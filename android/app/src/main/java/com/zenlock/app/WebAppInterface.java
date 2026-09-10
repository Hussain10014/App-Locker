package com.zenlock.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class WebAppInterface {
    private final Activity activity;
    private final NotificationManager notificationManager;
    private int previousFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
    private boolean isLockActive = false;

    public WebAppInterface(Activity activity) {
        this.activity = activity;
        this.notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public boolean isLockActive() {
        return isLockActive;
    }

    /**
     * Check if app has permission to manage Do Not Disturb policy.
     */
    @JavascriptInterface
    public boolean isDNDGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
        }
        return true;
    }

    /**
     * Open Android Settings to grant DND access.
     */
    @JavascriptInterface
    public void requestDNDPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isDNDGranted()) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                activity.startActivity(intent);
                Toast.makeText(activity, "Find 'ZenLock' and toggle ON to allow hiding notifications", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Turn Do Not Disturb ON (hide notifications) or OFF (restore normal notifications).
     */
    @JavascriptInterface
    public void setDND(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager != null) {
            if (isDNDGranted()) {
                try {
                    if (enable) {
                        previousFilter = notificationManager.getCurrentInterruptionFilter();
                        // Silence & hide all notifications, peek banners, and alerts
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                    } else {
                        // Restore previous setting
                        notificationManager.setInterruptionFilter(previousFilter);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
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
            } catch (Exception e) {
                e.printStackTrace();
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @JavascriptInterface
    public void showToast(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }
}
