package com.zenlock.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Fires natively via AlarmManager at the real session end time.
 * This runs even if the screen is off and the WebView's JS timers
 * are throttled, so DND always gets restored on time.
 */
public class ZenLockAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "ZenLockAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Auto-unlock alarm fired, restoring DND");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error restoring DND from alarm", t);
        }
        // Screen pinning (startLockTask/stopLockTask) can only be released from
        // the foreground Activity itself, so MainActivity re-checks the real
        // end time as soon as it resumes and unpins then.
    }
}
