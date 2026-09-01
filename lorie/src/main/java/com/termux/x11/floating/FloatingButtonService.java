package com.termux.x11.floating;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.termux.x11.R;

/**
 * Foreground service managing the floating keyboard button overlay.
 *
 * Lifecycle:
 * - Service starts when MainActivity.onStart() fires
 * - Service stops when MainActivity.onStop() fires (app backgrounded)
 *
 * Handles:
 * - Permission verification at runtime
 * - WindowManager overlay creation and management
 * - Button position persistence across rotation (normalized 0-1 coordinates)
 * - Edge-snapping and screen-bound clamping on orientation change
 */
public class FloatingButtonService extends Service {
    private static final String TAG = "FloatingButtonService";
    private static final String CHANNEL_ID = "floating_button_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private FloatingButtonView floatingButton;
    private WindowManager.LayoutParams layoutParams;
    private DisplayManager displayManager;
    private int lastDisplayRotation = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

        // Register for LorieView ready callback
        FloatingButtonManager.getInstance(this)
                .registerListener(new FloatingButtonListener() {
                    @Override
                    public void onLorieViewReady(com.termux.x11.LorieView view) {
                        Log.d(TAG, "LorieView is ready; enabling button");
                        if (floatingButton != null) {
                            floatingButton.setEnabled(true);
                            floatingButton.setAlpha(1.0f);
                        }
                    }

                    @Override
                    public void onLorieViewDestroyed() {
                        Log.d(TAG, "LorieView destroyed");
                        if (floatingButton != null) {
                            floatingButton.setEnabled(false);
                            floatingButton.setAlpha(0.5f);
                        }
                    }
                });

        // Listen for display changes (rotation)
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        handleDisplayRotation();
                    }
                }
            }, null);
        }

        createFloatingButton();
    }

    private void createFloatingButton() {
        // CRITICAL: Verify overlay permission before calling addView
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW permission denied; cannot create overlay");
            notifyPermissionDenied();
            return;
        }

        floatingButton = new FloatingButtonView(this);

        // Set up position save callback
        floatingButton.setPositionSaveCallback(new FloatingButtonView.PositionSaveCallback() {
            @Override
            public void onPositionSave(float x, float y) {
                saveButtonPosition(x, y);
            }
        });

        int size = (int) (56 * getResources().getDisplayMetrics().density);

        layoutParams = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
        );

        layoutParams.gravity = Gravity.TOP | Gravity.START;

        // Restore saved normalized position and apply to current screen
        restoreButtonPosition();

        floatingButton.setLayoutParams(layoutParams);

        // Start disabled; enabled when LorieView is ready
        floatingButton.setEnabled(false);
        floatingButton.setAlpha(0.5f);

        try {
            windowManager.addView(floatingButton, layoutParams);
            Log.d(TAG, "FloatingButtonView added to WindowManager at (" + layoutParams.x + ", " + layoutParams.y + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add FloatingButtonView", e);
        }
    }

    /**
     * Handle display rotation by remapping normalized position to new screen bounds.
     */
    private void handleDisplayRotation() {
        if (floatingButton == null || windowManager == null || layoutParams == null) return;

        int currentRotation = getCurrentDisplayRotation();
        if (currentRotation == lastDisplayRotation) return;
        
        Log.d(TAG, "Display rotation changed from " + lastDisplayRotation + " to " + currentRotation);
        lastDisplayRotation = currentRotation;

        // Get current screen dimensions
        android.graphics.Point screenSize = new android.graphics.Point();
        Display display = windowManager.getDefaultDisplay();
        display.getSize(screenSize);

        // Clamp position to new screen bounds
        int maxX = Math.max(0, screenSize.x - layoutParams.width);
        int maxY = Math.max(0, screenSize.y - layoutParams.height);
        layoutParams.x = Math.min(Math.max(0, layoutParams.x), maxX);
        layoutParams.y = Math.min(Math.max(0, layoutParams.y), maxY);

        try {
            windowManager.updateViewLayout(floatingButton, layoutParams);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update layout on rotation", e);
        }
    }

    private int getCurrentDisplayRotation() {
        if (windowManager == null) return 0;
        Display display = windowManager.getDefaultDisplay();
        return display != null ? display.getRotation() : 0;
    }

    /**
     * Save button position as normalized (0-1) relative to screen dimensions.
     * This ensures the button position persists correctly across rotation.
     */
    public void saveButtonPosition(float x, float y) {
        if (windowManager == null) return;

        android.graphics.Point screenSize = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(screenSize);

        // Normalize to 0-1 range
        float normX = screenSize.x > 0 ? x / screenSize.x : 0;
        float normY = screenSize.y > 0 ? y / screenSize.y : 0;

        // Clamp to valid range
        normX = Math.max(0, Math.min(1, normX));
        normY = Math.max(0, Math.min(1, normY));

        SharedPreferences prefs = getSharedPreferences("floating_button", MODE_PRIVATE);
        prefs.edit()
                .putFloat("button_norm_x", normX)
                .putFloat("button_norm_y", normY)
                .putInt("button_screen_width", screenSize.x)
                .putInt("button_screen_height", screenSize.y)
                .apply();
        Log.d(TAG, "Saved normalized button position: (" + normX + ", " + normY + ") on screen " + screenSize.x + "x" + screenSize.y);
    }

    /**
     * Restore button position from normalized (0-1) coordinates.
     * Maps the saved position to current screen dimensions.
     */
    private void restoreButtonPosition() {
        if (windowManager == null) return;

        android.graphics.Point screenSize = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(screenSize);

        SharedPreferences prefs = getSharedPreferences("floating_button", MODE_PRIVATE);

        // Try normalized position first
        if (prefs.contains("button_norm_x")) {
            float normX = prefs.getFloat("button_norm_x", 0.5f);
            float normY = prefs.getFloat("button_norm_y", 0.5f);

            layoutParams.x = Math.round(normX * screenSize.x);
            layoutParams.y = Math.round(normY * screenSize.y);
            Log.d(TAG, "Restored normalized position: (" + normX + ", " + normY + ") -> (" + layoutParams.x + ", " + layoutParams.y + ")");
        } else {
            // Fallback to raw pixel position (legacy)
            layoutParams.x = Math.round(prefs.getFloat("button_x", 100));
            layoutParams.y = Math.round(prefs.getFloat("button_y", 200));
            Log.d(TAG, "Restored legacy position: (" + layoutParams.x + ", " + layoutParams.y + ")");
        }

        // Clamp to screen bounds
        int maxX = Math.max(0, screenSize.x - layoutParams.width);
        int maxY = Math.max(0, screenSize.y - layoutParams.height);
        layoutParams.x = Math.min(Math.max(0, layoutParams.x), maxX);
        layoutParams.y = Math.min(Math.max(0, layoutParams.y), maxY);

        lastDisplayRotation = getCurrentDisplayRotation();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(null);
        }
        if (floatingButton != null && windowManager != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (Exception e) {
                Log.e(TAG, "Error removing FloatingButtonView", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- Notification helpers ----

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Keyboard Button",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the floating keyboard button active");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Termux:X11")
                .setContentText("Floating keyboard button active")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void notifyPermissionDenied() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Floating Button Disabled")
                    .setContentText("Grant overlay permission to enable")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build();
            nm.notify(NOTIFICATION_ID, n);
        }
    }
}