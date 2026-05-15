package com.example.reelcounter;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class ReelAccessibilityService extends AccessibilityService {

    private static final String IG_PACKAGE = "com.instagram.android";
    private static final String TAG = "ReelCounter";

    /**
     * DIAGNOSTIC MODE (Prefs.KEY_DIAGNOSTIC = true):
     *   Logs every scroll event from Instagram so you can see exactly what
     *   className / dx / dy / scrollX / scrollY values arrive when you swipe
     *   a Reel. Run: adb logcat -s ReelCounter
     *   Then disable diagnostic mode and fill in REEL_CLASS_HINT below.
     *
     * NORMAL MODE:
     *   Uses the className observed in diagnostic mode to identify the Reels
     *   ViewPager, then counts one swipe per debounce window.
     *   No delta-value filtering — Instagram's reported deltas vary too much
     *   across versions to rely on.
     */

    // Set this to the class name fragment you observe in diagnostic logs,
    // e.g. "viewpager", "pager", "X3c", etc.  Empty string = accept all classes.
    private static final String REEL_CLASS_HINT = "";

    private long lastCountedAt = 0L;
    private SharedPreferences prefs;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        prefs = getApplicationContext()
                .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        Log.i(TAG, "Service connected. Diagnostic=" + isDiagnostic());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null || !IG_PACKAGE.contentEquals(pkg)) return;

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
            // In diagnostic mode still try to count so you can cross-check.
        }

        // --- Class filter ---
        // Hard-exclude known non-Reels views.
        String clsLower = cls.toLowerCase();
        if (clsLower.contains("recycler")) return;  // Feed / Explore
        if (clsLower.contains("listview")) return;
        if (clsLower.contains("horizontalscroll")) return;
        if (clsLower.contains("nestedscroll")) return;

        // If we have a known hint from diagnostic logs, require it.
        if (!REEL_CLASS_HINT.isEmpty() && !clsLower.contains(REEL_CLASS_HINT.toLowerCase())) {
            return;
        }

        // --- Direction filter: must be a vertical scroll ---
        boolean hasDeltas = (dx != 0 || dy != 0);
        if (hasDeltas && Math.abs(dx) > Math.abs(dy)) return; // horizontal

        // --- Debounce ---
        long debounceMs = prefs != null
                ? prefs.getLong(Prefs.KEY_DEBOUNCE_MS, Prefs.DEFAULT_DEBOUNCE_MS)
                : Prefs.DEFAULT_DEBOUNCE_MS;

        long now = System.currentTimeMillis();
        if (now - lastCountedAt < debounceMs) return;
        lastCountedAt = now;

        if (isDiagnostic()) Log.i(TAG, "COUNTED  cls=" + cls + "  dy=" + dy);

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        new Thread(() -> db.swipeDao().insert(new SwipeEvent(now))).start();
    }

    private boolean isDiagnostic() {
        return prefs != null && prefs.getBoolean(Prefs.KEY_DIAGNOSTIC, false);
    }

    @Override
    public void onInterrupt() { }
}
