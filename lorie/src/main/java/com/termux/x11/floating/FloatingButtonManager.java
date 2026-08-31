package com.termux.x11.floating;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.termux.x11.LorieView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for floating keyboard button.
 *
 * Holds a WeakReference to LorieView to prevent memory leaks during Activity rotation.
 * Queries actual IME visibility from WindowInsets rather than tracking state manually.
 */
public class FloatingButtonManager {
    private static final String TAG = "FloatingButtonManager";
    private static FloatingButtonManager instance;

    private final Context context;
    private WeakReference<LorieView> lorieViewRef;  // Prevents memory leak
    private final List<FloatingButtonListener> listeners = new ArrayList<>();

    // Debounce rapid toggling
    private long lastToggleTime = 0;
    private static final long TOGGLE_DEBOUNCE_MS = 300;

    private FloatingButtonManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized FloatingButtonManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatingButtonManager(context);
        }
        return instance;
    }

    /**
     * Register a listener to be notified when LorieView is ready.
     * Called by FloatingButtonService before creating the button.
     */
    public void registerListener(FloatingButtonListener listener) {
        listeners.add(listener);
    }

    /**
     * Called by MainActivity when LorieView is fully initialized.
     * Notifies all listeners that the keyboard can now be controlled.
     */
    public void setLorieView(LorieView view) {
        this.lorieViewRef = new WeakReference<>(view);

        // Notify all listeners that LorieView is ready
        for (FloatingButtonListener listener : listeners) {
            listener.onLorieViewReady(view);
        }
    }

    /**
     * Called by MainActivity when LorieView is destroyed.
     * Notifies all listeners to disable the button.
     */
    public void clearLorieView() {
        this.lorieViewRef = null;

        // Notify all listeners that LorieView is destroyed
        for (FloatingButtonListener listener : listeners) {
            listener.onLorieViewDestroyed();
        }
    }

    /**
     * Get the current LorieView reference.
     * Returns null if the view was garbage collected (Activity destroyed).
     */
    public LorieView getLorieView() {
        return lorieViewRef != null ? lorieViewRef.get() : null;
    }

    /**
     * Toggle keyboard visibility based on actual IME state.
     *
     * Queries WindowInsets rather than tracking manually.
     * This prevents desynchronization when the user dismisses the keyboard via system back gesture.
     */
    public void toggleKeyboard() {
        // Debounce rapid taps
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < TOGGLE_DEBOUNCE_MS) {
            return;
        }
        lastToggleTime = now;

        LorieView view = getLorieView();
        if (view == null) {
            Log.w(TAG, "LorieView is null or was garbage collected");
            return;
        }

        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            Log.e(TAG, "InputMethodManager is null");
            return;
        }

        // Query ACTUAL keyboard visibility from WindowInsets
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
        boolean isKeyboardVisible = insets != null &&
                insets.isVisible(WindowInsetsCompat.Type.ime());

        if (isKeyboardVisible) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            Log.d(TAG, "Hiding keyboard");
        } else {
            // CRITICAL: Request focus before showing IME
            view.requestFocus();

            // Retry mechanism: if first attempt fails, retry with SHOW_FORCED
            boolean shown = imm.showSoftInput(view, 0);
            if (!shown) {
                Log.w(TAG, "showSoftInput returned false; retrying with SHOW_FORCED");
                view.postDelayed(() -> imm.showSoftInput(view, InputMethodManager.SHOW_FORCED), 100);
            } else {
                Log.d(TAG, "Showing keyboard");
            }
        }
    }
}