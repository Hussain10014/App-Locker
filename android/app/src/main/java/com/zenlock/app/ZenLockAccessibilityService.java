package com.zenlock.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Watches for the foreground app changing away from ZenLock while a lock
 * session is active, and immediately relaunches ZenLock. This does NOT
 * disable the swipe-up-and-hold unpin gesture the way Device Owner mode
 * does -- but it means that even if that gesture (or Home, or Recents)
 * momentarily succeeds, the user lands right back in the locked screen
 * instead of actually reaching another app. MainActivity.onResume() then
 * re-engages Lock Task Mode if it was dropped.
 */
public class ZenLockAccessibilityService extends AccessibilityService {
    private static final String TAG = "ZenLockAccess";
    private static final String OWN_PACKAGE = "com.zenlock.app";

    // Toggled by WebAppInterface.startLockTask() / stopLockTask()
    public static volatile boolean lockActive = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!lockActive) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null || OWN_PACKAGE.contentEquals(pkg)) return;

        Log.d(TAG, "Foreground escape detected to: " + pkg + " -- relaunching lock");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
        // no-op
    }
}
