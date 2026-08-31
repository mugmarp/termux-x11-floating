package com.termux.x11.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Helper for managing SYSTEM_ALERT_WINDOW permission.
 *
 * This permission is required to display system overlay windows (TYPE_APPLICATION_OVERLAY).
 * On Android 6+, it must be requested at runtime.
 */
public class OverlayPermissionHelper {

    /**
     * Check if the app has SYSTEM_ALERT_WINDOW permission.
     */
    public static boolean hasOverlayPermission(Activity activity) {
        return Settings.canDrawOverlays(activity);
    }

    /**
     * Request overlay permission from the user.
     * Call this from MainActivity when the permission is not granted.
     */
    public static void requestOverlayPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivityForResult(intent, requestCode);
        }
    }
}