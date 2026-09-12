package com.zenlock.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class WebAppInterface {
    private static final String TAG = "ZenLockBridge";
    private static final int ALARM_REQUEST_CODE = 4201;

    private final Activity activity;
    private final NotificationManager notificationManager;
    private int previousFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
    private boolean isLockActive = false;

    // CPU-level wake lock so ticks/timers keep a chance to run with screen off
    private PowerManager.WakeLock cpuWakeLock;

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

    // ========================================================================
    // Do Not Disturb
    // ========================================================================
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

    // ========================================================================
    // Exact Alarm (reliable native auto-unlock, independent of WebView JS)
    // ========================================================================
    @JavascriptInterface
    public boolean isExactAlarmGranted() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager am = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
                return am != null && am.canScheduleExactAlarms();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error checking exact alarm permission", t);
        }
        return true; // not required below API 31
    }

    @JavascriptInterface
    public void requestExactAlarmPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                Toast.makeText(activity, "Enable 'Allow setting alarms and reminders' for ZenLock", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error opening exact alarm settings", t);
        }
    }

    /**
     * Schedule the native auto-unlock alarm. This is the source of truth for
     * when the session actually ends -- it fires via AlarmManager even if the
     * screen is off and the WebView's own JS timers are throttled.
     * @param delayMillis milliseconds from now until the session should end
     */
    @JavascriptInterface
    public void scheduleAutoUnlock(long delayMillis) {
        try {
            AlarmManager am = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(activity, ZenLockAlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                activity,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            long triggerAt = System.currentTimeMillis() + delayMillis;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
            Log.d(TAG, "Auto-unlock alarm scheduled for +" + delayMillis + "ms");
        } catch (Throwable t) {
            Log.e(TAG, "Error scheduling auto-unlock alarm", t);
        }
    }

    @JavascriptInterface
    public void cancelAutoUnlock() {
        try {
            AlarmManager am = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(activity, ZenLockAlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                activity,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            am.cancel(pi);
            Log.d(TAG, "Auto-unlock alarm cancelled");
        } catch (Throwable t) {
            Log.e(TAG, "Error cancelling auto-unlock alarm", t);
        }
    }

    // ========================================================================
    // Accessibility Service (blocks escape attempts by relaunching ZenLock)
    // ========================================================================
    @JavascriptInterface
    public boolean isAccessibilityGranted() {
        try {
            String enabled = Settings.Secure.getString(activity.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(
                activity.getPackageName() + "/" + activity.getPackageName() + ".ZenLockAccessibilityService");
        } catch (Throwable t) {
            Log.w(TAG, "Error checking accessibility permission", t);
            return false;
        }
    }

    @JavascriptInterface
    public void requestAccessibilityPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            activity.startActivity(intent);
            Toast.makeText(activity, "Find 'ZenLock' in the list and turn it ON", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Log.e(TAG, "Error opening accessibility settings", t);
        }
    }

    // ========================================================================
    // Lock Task Mode (Screen Pinning) + Wake Lock + Accessibility toggle
    // ========================================================================
    @JavascriptInterface
    public void startLockTask() {
        isLockActive = true;
        activity.runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.startLockTask();
                }

                // Partial wake lock: keeps CPU alive with screen off so the
                // WebView gets *some* chance to tick; the AlarmManager alarm
                // is what guarantees correctness regardless.
                PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                if (pm != null && (cpuWakeLock == null || !cpuWakeLock.isHeld())) {
                    cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZenLock::CountdownWakeLock");
                    cpuWakeLock.acquire(24 * 60 * 60 * 1000L); // 24h safety timeout
                }

                // Arm the escape-detection accessibility service, if enabled.
                ZenLockAccessibilityService.lockActive = true;

                Toast.makeText(activity, "ZenLock Engaged. Screen is pinned.", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Log.w(TAG, "startLockTask warning (App Pinning must be enabled in Settings)", t);
            }
        });
    }

    @JavascriptInterface
    public void stopLockTask() {
        isLockActive = false;
        activity.runOnUiThread(() -> {
            try {
                ZenLockAccessibilityService.lockActive = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.stopLockTask();
                }
                if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
                    cpuWakeLock.release();
                    cpuWakeLock = null;
                }
                Toast.makeText(activity, "Session Complete. Screen Unlocked.", Toast.LENGTH_SHORT).show();
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
