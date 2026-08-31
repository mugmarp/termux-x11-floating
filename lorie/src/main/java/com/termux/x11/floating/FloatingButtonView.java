package com.termux.x11.floating;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import androidx.core.content.res.ResourcesCompat;
import com.termux.x11.R;

/**
 * Custom View for the draggable floating keyboard button.
 *
 * Features:
 * - Draggable with proper touch slop detection
 * - Material Design circular button with keyboard icon
 * - Accessibility support (ContentDescription)
 */
public class FloatingButtonView extends View {
    private static final String TAG = "FloatingButtonView";

    private final Paint circlePaint;
    private final Paint shadowPaint;
    private final RectF circleRect = new RectF();

    private float lastTouchX, lastTouchY;
    private boolean isDragging = false;
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

    public FloatingButtonView(Context context) {
        super(context);

        // Accessibility
        this.setContentDescription("Toggle software keyboard");
        this.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        // Get device-specific touch slop
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Circle paint (main button)
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.parseColor("#4CAF50"));  // Material Green
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
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - lastTouchX;
                float dy = event.getRawY() - lastTouchY;

                // Use system touch slop for drag detection
                if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    isDragging = true;
                }

                if (isDragging && params != null) {
                    params.x += dx;
                    params.y += dy;

                    // Clamp to screen bounds
                    WindowManager wm = (WindowManager) getContext()
                            .getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        wm.updateViewLayout(this, params);
                    }
                }

                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_UP:
                if (!isDragging) {
                    // Tap event: toggle keyboard
                    performClick();
                    FloatingButtonManager.getInstance(getContext()).toggleKeyboard();
                } else if (params != null && positionSaveCallback != null) {
                    // End of drag: save position via callback
                    positionSaveCallback.onPositionSave(params.x, params.y);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Set the WindowManager layout parameters for this view.
     * Called by FloatingButtonService during initialization.
     */
    public void setLayoutParams(WindowManager.LayoutParams params) {
        this.params = params;
    }

    // Removed getFloatingButtonService() - using callback instead
}