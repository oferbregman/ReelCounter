package com.example.reelcounter;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class ReelAccessibilityService extends AccessibilityService {

    private static final String IG_PACKAGE = "com.instagram.android";
    private static final String TAG = "ReelCounter";
    private static final String REEL_CLASS_HINT = "";
    private static final long SESSION_TIMEOUT_MS = 500L;

    private long lastCountedAt = 0L;
    private long currentSessionId = 0L;
    private SharedPreferences prefs;
    private String lastActiveTab = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        prefs = getApplicationContext()
                .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        currentSessionId = System.currentTimeMillis();
        Log.i(TAG, "Service connected. Diagnostic=" + isDiagnostic());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null || !IG_PACKAGE.contentEquals(pkg)) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            CharSequence desc = event.getContentDescription();
            String descStr = desc != null ? desc.toString().toLowerCase() : "";
            if (descStr.contains("reel") && !lastActiveTab.equals("reels")) {
                lastActiveTab = "reels";
                long now = System.currentTimeMillis();
                long debounceMs = prefs != null
                        ? prefs.getLong(Prefs.KEY_DEBOUNCE_MS, Prefs.DEFAULT_DEBOUNCE_MS)
                        : Prefs.DEFAULT_DEBOUNCE_MS;
                if (now - lastCountedAt >= debounceMs) {
                    lastCountedAt = now;
                    currentSessionId = now;
                    AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                    final long sessionId = currentSessionId;
                    new Thread(() -> {
                        db.swipeDao().insert(new SwipeEvent(now, 1, sessionId));
                        WeeklyReportManager.checkLimitsAndNotify(getApplicationContext());
                    }).start();
                }
            } else if (!descStr.contains("reel")) {
                lastActiveTab = "";
            }
            return;
        }

        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;

        String cls = event.getClassName() != null
                ? event.getClassName().toString() : "(null)";
        int dx  = event.getScrollDeltaX();
        int dy  = event.getScrollDeltaY();
        int sx  = event.getScrollX();
        int sy  = event.getScrollY();
        int max = event.getMaxScrollY();

        if (isDiagnostic()) {
            Log.d(TAG, "SCROLL  cls=" + cls
                    + "  dx=" + dx + " dy=" + dy
                    + "  sx=" + sx + " sy=" + sy + " maxY=" + max);
        }

        String clsLower = cls.toLowerCase();
        if (clsLower.contains("recycler")) return;
        if (clsLower.contains("listview")) return;
        if (clsLower.contains("horizontalscroll")) return;
        if (clsLower.contains("nestedscroll")) return;

        if (!REEL_CLASS_HINT.isEmpty() && !clsLower.contains(REEL_CLASS_HINT.toLowerCase())) {
            return;
        }

        boolean hasDeltas = (dx != 0);
        if (hasDeltas && Math.abs(dx) > Math.abs(dy)) return;

        if (dy < 0) {
            if (isDiagnostic()) Log.d(TAG, "IGNORED negative dy=" + dy);
            return;
        }

        long now = System.currentTimeMillis();
        long debounceMs = prefs != null
                ? prefs.getLong(Prefs.KEY_DEBOUNCE_MS, Prefs.DEFAULT_DEBOUNCE_MS)
                : Prefs.DEFAULT_DEBOUNCE_MS;

        if (now - lastCountedAt < SESSION_TIMEOUT_MS) {
            if (isDiagnostic()) Log.d(TAG, "IGNORED same session (within " + SESSION_TIMEOUT_MS + "ms)");
            return;
        }

        if (now - lastCountedAt < debounceMs) {
            if (isDiagnostic()) Log.d(TAG, "IGNORED debounce window (" + debounceMs + "ms)");
            return;
        }

        lastCountedAt = now;
        currentSessionId = now;

        if (isDiagnostic()) {
            Log.i(TAG, "COUNTED  cls=" + cls + "  dy=" + dy + "  session=" + currentSessionId);
        }

        // Insert first, then check limits — order matters.
        // checkLimitsAndNotify queries the DB, so the new row must already be committed.
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        final long sessionId = currentSessionId;
        new Thread(() -> {
            db.swipeDao().insert(new SwipeEvent(now, dy, sessionId));
            // Now the row is in the DB — limit check will see the correct count.
            WeeklyReportManager.checkLimitsAndNotify(getApplicationContext());
        }).start();
    }

    private boolean isDiagnostic() {
        return prefs != null && prefs.getBoolean(Prefs.KEY_DIAGNOSTIC, false);
    }

    @Override
    public void onInterrupt() { }
}