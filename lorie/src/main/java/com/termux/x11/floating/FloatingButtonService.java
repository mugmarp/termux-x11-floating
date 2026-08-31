package com.termux.x11.floating;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
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
 * - Button position persistence across rotation
 */
public class FloatingButtonService extends Service {
    private static final String TAG = "FloatingButtonService";
    private static final String CHANNEL_ID = "floating_button_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private FloatingButtonView floatingButton;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

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
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        layoutParams.gravity = Gravity.TOP | Gravity.START;

        // Restore saved position
        restoreButtonPosition();

        floatingButton.setLayoutParams(layoutParams);

        // Start disabled; enabled when LorieView is ready
        floatingButton.setEnabled(false);
        floatingButton.setAlpha(0.5f);

        try {
            windowManager.addView(floatingButton, layoutParams);
            Log.d(TAG, "FloatingButtonView added to WindowManager");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add FloatingButtonView", e);
        }
    }

    private void restoreButtonPosition() {
        SharedPreferences prefs = getSharedPreferences("floating_button", MODE_PRIVATE);
        layoutParams.x = Math.round(prefs.getFloat("button_x", 100));
        layoutParams.y = Math.round(prefs.getFloat("button_y", 200));
    }

    public void saveButtonPosition(float x, float y) {
        SharedPreferences prefs = getSharedPreferences("floating_button", MODE_PRIVATE);
        prefs.edit()
                .putFloat("button_x", x)
                .putFloat("button_y", y)
                .apply();
        Log.d(TAG, "Saved button position: " + x + ", " + y);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
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