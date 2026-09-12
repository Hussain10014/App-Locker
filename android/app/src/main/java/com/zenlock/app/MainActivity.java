package com.zenlock.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "ZenLock";
    private WebView webView;
    private WebAppInterface webAppInterface;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Global crash handler to catch any unexpected crash and display it gracefully
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception in " + thread.getName(), throwable);
            runOnUiThread(() -> {
                try {
                    TextView errorView = new TextView(this);
                    errorView.setPadding(40, 60, 40, 60);
                    errorView.setText("ZenLock Startup Error:\n\n" + Log.getStackTraceString(throwable));
                    errorView.setTextColor(0xFFFF5555);
                    errorView.setBackgroundColor(0xFF07090E);
                    setContentView(errorView);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to render error view", t);
                }
            });
        });

        try {
            // Keep screen awake while app is active
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // Show over lock screen if phone sleeps during lock session
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(true);
                    setTurnScreenOn(true);
                } else {
                    getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    );
                }
            } catch (Throwable t) {
                Log.w(TAG, "Could not set showWhenLocked flags", t);
            }

            // Create and attach WebView first
            webView = new WebView(this);
            webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            webView.setBackgroundColor(0xFF07090E);
            setContentView(webView);

            // Configure WebSettings safely
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                settings.setMediaPlaybackRequiresUserGesture(false);
            }

            // Bind JavaScript Native Bridge
            webAppInterface = new WebAppInterface(this);
            webView.addJavascriptInterface(webAppInterface, "AndroidLocker");

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    Log.e(TAG, "WebView error: " + description + " (" + failingUrl + ")");
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    Log.d(TAG, "JS Console: " + consoleMessage.message() + " -- From line "
                            + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                    return true;
                }
            });

            // Load local offline assets
            webView.loadUrl("file:///android_asset/index.html");

            // Post hideSystemBars to run safely after decor view is attached
            getWindow().getDecorView().post(this::hideSystemBars);

        } catch (Throwable t) {
            Log.e(TAG, "Fatal error initializing MainActivity", t);
            TextView tv = new TextView(this);
            tv.setPadding(40, 60, 40, 60);
            tv.setText("Initialization Error:\n\n" + Log.getStackTraceString(t));
            tv.setTextColor(0xFFFF5555);
            tv.setBackgroundColor(0xFF07090E);
            setContentView(tv);
        }
    }

    private void hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error hiding system bars", t);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    /**
     * Whenever the Activity comes back to the foreground -- e.g. the screen
     * was turned off and back on, or ZenLockAccessibilityService just bounced
     * the app back here after an unpin/escape attempt -- do two things:
     *   1. Force the WebView to re-run its JS resync logic immediately
     *      instead of waiting on a possibly-throttled setInterval tick.
     *   2. If a lock session is supposed to be active but Lock Task Mode is
     *      no longer engaged (i.e. the user just did swipe-up-and-hold to
     *      unpin), re-engage it immediately so the pin itself is restored,
     *      not just the app.
     */
    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (webView != null) {
                webView.onResume();
                webView.evaluateJavascript(
                    "if (window.zenLockResync) { window.zenLockResync(); }",
                    null
                );
            }

            if (webAppInterface != null && webAppInterface.isLockActive()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                boolean inLockTask = false;
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        inLockTask = am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
                    } else {
                        inLockTask = am.isInLockTaskMode();
                    }
                }
                if (!inLockTask) {
                    Log.d(TAG, "Lock session active but not pinned -- re-engaging Lock Task Mode");
                    startLockTask();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error resyncing on resume", t);
        }
    }

    @Override
    public void onBackPressed() {
        if (webAppInterface != null && webAppInterface.isLockActive()) {
            Toast.makeText(this, "ZenLock is engaged. Quitting is disabled!", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }
}
