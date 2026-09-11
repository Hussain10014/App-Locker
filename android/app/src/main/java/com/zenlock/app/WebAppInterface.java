package com.zenlock.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class WebAppInterface {
    private static final String TAG = "ZenLockBridge";
    private final Activity activity;
    private final NotificationManager notificationManager;
    private int previousFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
    private boolean isLockActive = false;

    public WebAppInterface(Activity activity) {
        this.activity = activity;
        NotificationManager nm = null;
        try {
            nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to get NotificationManager", t);
        }
        this.notificationManager = nm;
    }

    public boolean isLockActive() {
        return isLockActive;
    }

    /**
     * Check if app has permission to manage Do Not Disturb policy.
     */
    @JavascriptInterface
    public boolean isDNDGranted() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
                return notificationManager.isNotificationPolicyAccessGranted();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error checking DND permission", t);
        }
        return false;
    }

    /**
     * Open Android Settings to grant DND access.
     */
    @JavascriptInterface
    public void requestDNDPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isDNDGranted()) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    activity.startActivity(intent);
                    Toast.makeText(activity, "Find 'ZenLock' and switch ON to allow hiding notifications", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error opening DND settings", t);
        }
    }

    /**
     * Turn Do Not Disturb ON (hide notifications) or OFF (restore normal notifications).
     */
    @JavascriptInterface
    public void setDND(boolean enable) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
                if (isDNDGranted()) {
                    if (enable) {
                        previousFilter = notificationManager.getCurrentInterruptionFilter();
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                    } else {
                        notificationManager.setInterruptionFilter(previousFilter);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error toggling DND", t);
        }
    }

    /**
     * Android App Pinning / Lock Task Mode.
     * Prevents exiting app, locks navigation bar, and hides status bar pull-down.
     */
    @JavascriptInterface
    public void startLockTask() {
        isLockActive = true;
        activity.runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.startLockTask();
                    Toast.makeText(activity, "ZenLock Engaged. Screen is pinned.", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                Log.w(TAG, "startLockTask warning (App Pinning must be enabled in Settings)", t);
            }
        });
    }

    /**
     * Release Screen Pinning when timer ends.
     */
    @JavascriptInterface
    public void stopLockTask() {
        isLockActive = false;
        activity.runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.stopLockTask();
                    Toast.makeText(activity, "Session Complete. Screen Unlocked.", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                Log.w(TAG, "stopLockTask error", t);
            }
        });
    }

    @JavascriptInterface
    public void showToast(String message) {
        activity.runOnUiThread(() -> {
            try {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        });
    }
}
