package com.termux.x11.floating;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import androidx.core.content.res.ResourcesCompat;
import com.termux.x11.LoriePreferences;
import com.termux.x11.R;

/**
 * Custom View for the draggable floating keyboard button.
 *
 * Features:
 * - Tap: Toggle soft keyboard visibility
 * - Long Press: Open Termux:X11 Preferences
 * - Drag: Move button around screen with edge-snapping
 * - Material Design circular button with keyboard icon
 * - Accessibility support (ContentDescription)
 */
public class FloatingButtonView extends View {
    private static final String TAG = "FloatingButtonView";

    // Gesture timing constants
    private static final long LONG_PRESS_TIMEOUT = 500; // ms
    private static final long TAP_TIMEOUT = 200; // ms

    private final Paint circlePaint;
    private final Paint shadowPaint;
    private final RectF circleRect = new RectF();
    private final Handler handler;

    private float lastTouchX, lastTouchY;
    private boolean isDragging = false;
    private boolean isLongPress = false;
    private long downTime;
    private WindowManager.LayoutParams params;

    // Touch sensitivity from system configuration
    private final int touchSlop;

    // Callback for position saving
    private PositionSaveCallback positionSaveCallback;

    public interface PositionSaveCallback {
        void onPositionSave(float x, float y);
    }

    public void setPositionSaveCallback(PositionSaveCallback callback) {
        this.positionSaveCallback = callback;
    }

    // Long press runnable
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isDragging) {
                isLongPress = true;
                performLongClick();
                openPreferences();
            }
        }
    };

    public FloatingButtonView(Context context) {
        super(context);

        // Accessibility
        this.setContentDescription("Toggle software keyboard. Long press for settings. Drag to move.");
        this.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        // Get device-specific touch slop
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Handler for long press detection
        handler = new Handler(Looper.getMainLooper());

        // Circle paint (main button) - Material Design Green
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.parseColor("#4CAF50"));
        circlePaint.setStyle(Paint.Style.FILL);

        // Shadow paint
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setShadowLayer(10, 0, 4, Color.parseColor("#40000000"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f - 4;

        // Draw shadow + circle
        circleRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawRoundRect(circleRect, radius, radius, shadowPaint);
        canvas.drawRoundRect(circleRect, radius, radius, circlePaint);

        // Draw keyboard icon (VectorDrawable)
        Drawable icon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_keyboard, null);
        if (icon != null) {
            int iconSize = (int) radius;
            icon.setBounds(
                    (int) (cx - iconSize / 2), (int) (cy - iconSize / 2),
                    (int) (cx + iconSize / 2), (int) (cy + iconSize / 2)
            );
            icon.draw(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Reset state
                isDragging = false;
                isLongPress = false;
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                downTime = System.currentTimeMillis();
                
                // Start long press timer
                handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - lastTouchX;
                float dy = event.getRawY() - lastTouchY;

                // Cancel long press if user starts dragging
                if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    isDragging = true;
                    handler.removeCallbacks(longPressRunnable);
                }

                if (isDragging && params != null) {
                    params.x += Math.round(dx);
                    params.y += Math.round(dy);

                    // Clamp to screen bounds
                    clampToScreen();

                    // Update view position
                    WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        try {
                            wm.updateViewLayout(this, params);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to update view layout during drag", e);
                        }
                    }
                }

                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                
                long pressDuration = System.currentTimeMillis() - downTime;
                
                if (isLongPress) {
                    // Long press already handled by runnable
                    Log.d(TAG, "Long press handled");
                } else if (isDragging) {
                    // End of drag: edge-snap and save position
                    snapToEdge();
                    if (positionSaveCallback != null) {
                        positionSaveCallback.onPositionSave(params.x, params.y);
                    }
                } else if (pressDuration < TAP_TIMEOUT) {
                    // Quick tap: toggle keyboard
                    performClick();
                    FloatingButtonManager.getInstance(getContext()).toggleKeyboard();
                }
                
                // Reset state
                isDragging = false;
                isLongPress = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                isDragging = false;
                isLongPress = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Open Termux:X11 Preferences activity.
     */
    private void openPreferences() {
        try {
            Context context = getContext();
            Intent intent = new Intent(context, LoriePreferences.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "Opened Termux:X11 Preferences");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open preferences", e);
        }
    }

    /**
     * Clamp the button position within current screen bounds.
     */
    private void clampToScreen() {
        if (params == null) return;

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        android.graphics.Point screenSize = new android.graphics.Point();
        wm.getDefaultDisplay().getSize(screenSize);

        int maxX = Math.max(0, screenSize.x - getWidth());
        int maxY = Math.max(0, screenSize.y - getHeight());

        if (params.x < 0) params.x = 0;
        if (params.y < 0) params.y = 0;
        if (params.x > maxX) params.x = maxX;
        if (params.y > maxY) params.y = maxY;
    }

    /**
     * Snap the button to the nearest screen edge (left or right).
     */
    private void snapToEdge() {
        if (params == null) return;

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        android.graphics.Point screenSize = new android.graphics.Point();
        wm.getDefaultDisplay().getSize(screenSize);

        int midpoint = screenSize.x / 2;
        int targetX = (params.x < midpoint) ? 0 : screenSize.x - getWidth();

        params.x = targetX;

        try {
            wm.updateViewLayout(this, params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to snap to edge", e);
        }
    }

    /**
     * Set the WindowManager layout parameters for this view.
     * Called by FloatingButtonService during initialization.
     */
    public void setLayoutParams(WindowManager.LayoutParams params) {
        this.params = params;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(longPressRunnable);
    }
}