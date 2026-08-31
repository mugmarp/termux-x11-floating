package com.termux.x11.floating;

import com.termux.x11.LorieView;

/**
 * Callback fired when LorieView is ready for keyboard control.
 * Prevents race conditions between Service startup and MainActivity initialization.
 */
public interface FloatingButtonListener {
    void onLorieViewReady(LorieView view);
    void onLorieViewDestroyed();
}