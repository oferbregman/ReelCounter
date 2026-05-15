package com.example.reelcounter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView todayCount, weekCount, monthCount, yearCount, statusText, debounceLabel;
    private SeekBar debounceSeek;
    private Switch diagnosticSwitch;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);

        todayCount      = findViewById(R.id.todayCount);
        weekCount       = findViewById(R.id.weekCount);
        monthCount      = findViewById(R.id.monthCount);
        yearCount       = findViewById(R.id.yearCount);
        statusText      = findViewById(R.id.statusText);
        debounceLabel   = findViewById(R.id.debounceLabel);
        debounceSeek    = findViewById(R.id.debounceSeek);
        diagnosticSwitch = findViewById(R.id.diagnosticSwitch);

        // Debounce seekbar: 0..19 -> 0.5s..10.0s in 0.5s steps
        long savedMs = prefs.getLong(Prefs.KEY_DEBOUNCE_MS, Prefs.DEFAULT_DEBOUNCE_MS);
        debounceSeek.setProgress(progressFromMs(savedMs));
        updateDebounceLabel(savedMs);

        debounceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                long ms = msFromProgress(progress);
                updateDebounceLabel(ms);
                if (fromUser) prefs.edit().putLong(Prefs.KEY_DEBOUNCE_MS, ms).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        // Diagnostic toggle
        diagnosticSwitch.setChecked(prefs.getBoolean(Prefs.KEY_DIAGNOSTIC, false));
        diagnosticSwitch.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(Prefs.KEY_DIAGNOSTIC, isChecked).apply());

        Button openSettings = findViewById(R.id.openSettingsBtn);
        openSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button refresh = findViewById(R.id.refreshBtn);
        refresh.setOnClickListener(v -> refreshCounts());
    }

    private int progressFromMs(long ms) {
        int p = (int) Math.round((ms / 1000.0 - 0.5) / 0.5);
        return Math.max(0, Math.min(19, p));
    }

    private long msFromProgress(int progress) {
        return Math.round((0.5 + progress * 0.5) * 1000);
    }

    private void updateDebounceLabel(long ms) {
        debounceLabel.setText(String.format(Locale.US, "Debounce: %.1fs", ms / 1000.0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusText.setText(isServiceEnabled()
                ? "Service: ENABLED. Open Instagram and scroll."
                : "Service: DISABLED. Tap below and enable Reel Counter under Accessibility.");
        refreshCounts();
    }

    private boolean isServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<android.accessibilityservice.AccessibilityServiceInfo> list =
                am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (android.accessibilityservice.AccessibilityServiceInfo info : list) {
            if (info.getId() != null && info.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    private void refreshCounts() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            int today = db.swipeDao().countSince(startOf(Calendar.DAY_OF_YEAR));
            int week  = db.swipeDao().countSince(startOfWeek());
            int month = db.swipeDao().countSince(startOf(Calendar.MONTH));
            int year  = db.swipeDao().countSince(startOf(Calendar.YEAR));
            ui.post(() -> {
                todayCount.setText(String.valueOf(today));
                weekCount.setText(String.valueOf(week));
                monthCount.setText(String.valueOf(month));
                yearCount.setText(String.valueOf(year));
            });
        }).start();
    }

    private long startOf(int field) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (field == Calendar.MONTH) c.set(Calendar.DAY_OF_MONTH, 1);
        else if (field == Calendar.YEAR) { c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.MONTH, 0); }
        return c.getTimeInMillis();
    }

    private long startOfWeek() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
        return c.getTimeInMillis();
    }
}
