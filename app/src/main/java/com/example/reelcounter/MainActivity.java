package com.example.reelcounter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TabHost tabHost;
    private TextView todayCount, todayDistance, todayRPM, statusText, debounceLabel;
    private TextView weeklyCount, weeklyDistance, weeklyRPM;
    private TextView lifetimeCount, lifetimeDistance, lifetimeRPM;
    private SeekBar debounceSeek;
    private Switch diagnosticSwitch;
    private Button screenLengthBtn, resetLifetimeBtn, weeklyLimitBtn, dailyLimitBtn;
    private SharedPreferences prefs;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    // Android 13+ notification permission
    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this,
                            "Notifications disabled — limit alerts won't appear.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);

        initializeViews();
        setupDebounceSeekbar();
        setupDiagnosticSwitch();
        setupScreenLengthButton();
        setupResetLifetimeButton();
        setupDailyLimitButton();
        setupWeeklyLimitButton();
        setupOpenSettingsButton();
        setupRefreshButton();
        setupTabs();

        requestNotificationPermission();
        WeeklyReportManager.scheduleWeeklyReport(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void initializeViews() {
        todayCount    = findViewById(R.id.todayCount);
        todayDistance = findViewById(R.id.todayDistance);
        todayRPM      = findViewById(R.id.todayRPM);

        weeklyCount    = findViewById(R.id.weeklyCount);
        weeklyDistance = findViewById(R.id.weeklyDistance);
        weeklyRPM      = findViewById(R.id.weeklyRPM);

        lifetimeCount    = findViewById(R.id.lifetimeCount);
        lifetimeDistance = findViewById(R.id.lifetimeDistance);
        lifetimeRPM      = findViewById(R.id.lifetimeRPM);

        statusText     = findViewById(R.id.statusText);
        debounceLabel  = findViewById(R.id.debounceLabel);
        debounceSeek   = findViewById(R.id.debounceSeek);
        diagnosticSwitch = findViewById(R.id.diagnosticSwitch);
        screenLengthBtn  = findViewById(R.id.screenLengthBtn);
        resetLifetimeBtn = findViewById(R.id.resetLifetimeBtn);
        weeklyLimitBtn   = findViewById(R.id.weeklyLimitBtn);
        dailyLimitBtn    = findViewById(R.id.dailyLimitBtn);
        tabHost          = findViewById(R.id.tabHost);
    }

    private void setupDebounceSeekbar() {
        long savedMs = prefs.getLong(Prefs.KEY_DEBOUNCE_MS, Prefs.DEFAULT_DEBOUNCE_MS);
        debounceSeek.setMax(100);
        debounceSeek.setProgress(msToProgress(savedMs));
        updateDebounceLabel(savedMs);

        debounceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                long ms = progressToMs(progress);
                updateDebounceLabel(ms);
                if (fromUser) prefs.edit().putLong(Prefs.KEY_DEBOUNCE_MS, ms).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private int msToProgress(long ms) { return (int) (ms / 100); }
    private long progressToMs(int progress) { return (long) progress * 100; }
    private void updateDebounceLabel(long ms) {
        debounceLabel.setText(String.format(Locale.US, "%.1fs", ms / 1000.0));
    }

    private void setupDiagnosticSwitch() {
        diagnosticSwitch.setChecked(prefs.getBoolean(Prefs.KEY_DIAGNOSTIC, false));
        diagnosticSwitch.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean(Prefs.KEY_DIAGNOSTIC, isChecked).apply());
    }

    private void setupScreenLengthButton() {
        updateScreenLengthButton(prefs.getFloat(Prefs.KEY_SCREEN_LENGTH_CM, 0f));
        screenLengthBtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter Screen Length");
            builder.setMessage("Enter your device's screen length in cm:");
            EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            float current = prefs.getFloat(Prefs.KEY_SCREEN_LENGTH_CM, 0f);
            if (current > 0) input.setText(String.valueOf(current));
            builder.setView(input);
            builder.setPositiveButton("Save", (dialog, which) -> {
                String text = input.getText().toString();
                if (!text.isEmpty()) {
                    try {
                        float length = Float.parseFloat(text);
                        prefs.edit().putFloat(Prefs.KEY_SCREEN_LENGTH_CM, length).apply();
                        updateScreenLengthButton(length);
                        refreshCounts();
                        Toast.makeText(this, "Screen length saved!", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });
    }

    private void updateScreenLengthButton(float lengthCm) {
        screenLengthBtn.setText(lengthCm > 0
                ? String.format(Locale.US, "Screen: %.1f cm", lengthCm)
                : "Set Screen Length");
    }

    // ── Daily limit ───────────────────────────────────────────────────────────

    private void setupDailyLimitButton() {
        updateDailyLimitButton(prefs.getInt(Prefs.KEY_DAILY_LIMIT, Prefs.DEFAULT_DAILY_LIMIT), -1, -1);
        dailyLimitBtn.setOnClickListener(v -> showLimitDialog(
                "Set Daily Reel Limit",
                "Enter a maximum number of reels per day (leave empty to disable):",
                prefs.getInt(Prefs.KEY_DAILY_LIMIT, Prefs.DEFAULT_DAILY_LIMIT),
                Prefs.KEY_DAILY_LIMIT, "day"));
    }

    private void updateDailyLimitButton(int limit, int current, int remaining) {
        if (limit <= 0) {
            dailyLimitBtn.setText("Set Daily Limit");
            dailyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF252525));
        } else if (current >= 0 && current > limit) {
            dailyLimitBtn.setText(String.format(Locale.US, "⚠ Over daily limit: %d / %d reels", current, limit));
            dailyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF8B0000));
        } else if (current >= 0) {
            dailyLimitBtn.setText(String.format(Locale.US, "Daily limit: %d reels (%d left)", limit, remaining));
            dailyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF252525));
        } else {
            dailyLimitBtn.setText(String.format(Locale.US, "Daily limit: %d reels", limit));
            dailyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF252525));
        }
    }

    // ── Weekly limit ──────────────────────────────────────────────────────────

    private void setupWeeklyLimitButton() {
        updateWeeklyLimitButton(prefs.getInt(Prefs.KEY_WEEKLY_LIMIT, Prefs.DEFAULT_WEEKLY_LIMIT), -1, -1);
        weeklyLimitBtn.setOnClickListener(v -> showLimitDialog(
                "Set Weekly Reel Limit",
                "Enter a maximum number of reels per week (leave empty to disable):",
                prefs.getInt(Prefs.KEY_WEEKLY_LIMIT, Prefs.DEFAULT_WEEKLY_LIMIT),
                Prefs.KEY_WEEKLY_LIMIT, "week"));
    }

    private void updateWeeklyLimitButton(int limit, int current, int remaining) {
        if (limit <= 0) {
            weeklyLimitBtn.setText("Set Weekly Limit");
            weeklyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF252525));
        } else if (current >= 0 && current > limit) {
            weeklyLimitBtn.setText(String.format(Locale.US, "⚠ Over weekly limit: %d / %d reels", current, limit));
            weeklyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF8B0000));
        } else if (current >= 0) {
            weeklyLimitBtn.setText(String.format(Locale.US, "Weekly limit: %d reels (%d left)", limit, remaining));
            weeklyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF252525));
        } else {
            weeklyLimitBtn.setText(String.format(Locale.US, "Weekly limit: %d reels", limit));
            weeklyLimitBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF252525));
        }
    }

    // ── Shared limit dialog ───────────────────────────────────────────────────

    private void showLimitDialog(String title, String message, int currentLimit,
                                 String prefKey, String periodLabel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (currentLimit > 0) input.setText(String.valueOf(currentLimit));
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                prefs.edit().putInt(prefKey, -1).apply();
                Toast.makeText(this, capitalize(periodLabel) + " limit removed.", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    int limit = Integer.parseInt(text);
                    prefs.edit().putInt(prefKey, limit).apply();
                    Toast.makeText(this, "Limit set to " + limit + " reels/" + periodLabel + ".", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
                }
            }
            refreshCounts();
        });
        builder.setNegativeButton("Clear Limit", (dialog, which) -> {
            prefs.edit().putInt(prefKey, -1).apply();
            refreshCounts();
            Toast.makeText(this, capitalize(periodLabel) + " limit removed.", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    private void setupResetLifetimeButton() {
        resetLifetimeBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset Everything?")
                    .setMessage("This will permanently delete all reel counts, distances, " +
                            "session history, and reset all settings to defaults. This cannot be undone.")
                    .setPositiveButton("Reset All", (dialog, which) -> {
                        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                        new Thread(() -> {
                            db.swipeDao().deleteAll();
                            prefs.edit().clear().apply();
                            uiHandler.post(() -> {
                                updateScreenLengthButton(0f);
                                updateDebounceLabel(Prefs.DEFAULT_DEBOUNCE_MS);
                                debounceSeek.setProgress(msToProgress(Prefs.DEFAULT_DEBOUNCE_MS));
                                diagnosticSwitch.setChecked(false);
                                updateDailyLimitButton(-1, -1, -1);
                                updateWeeklyLimitButton(-1, -1, -1);
                                refreshCounts();
                                Toast.makeText(MainActivity.this, "All data reset.", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupOpenSettingsButton() {
        findViewById(R.id.openSettingsBtn).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void setupRefreshButton() {
        findViewById(R.id.refreshBtn).setOnClickListener(v -> refreshCounts());
    }

    private void setupTabs() {
        tabHost.setup();
        TabHost.TabSpec s1 = tabHost.newTabSpec("Today");
        s1.setContent(R.id.todayTab); s1.setIndicator("Today"); tabHost.addTab(s1);
        TabHost.TabSpec s2 = tabHost.newTabSpec("Weekly");
        s2.setContent(R.id.weeklyTab); s2.setIndicator("Weekly"); tabHost.addTab(s2);
        TabHost.TabSpec s3 = tabHost.newTabSpec("Lifetime");
        s3.setContent(R.id.lifetimeTab); s3.setIndicator("Lifetime"); tabHost.addTab(s3);
    }

    @Override
    protected void onResume() {
        super.onResume();
//        WeeklyReportManager.sendWeeklyReport(this); //sends the weekly report immediately after opening the app
        statusText.setText(isServiceEnabled()
                ? "Service: ENABLED ✓"
                : "Service: DISABLED. Tap Settings and enable Reel Counter.");
        refreshCounts();
    }

    private boolean isServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<android.accessibilityservice.AccessibilityServiceInfo> list =
                am.getEnabledAccessibilityServiceList(
                        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (android.accessibilityservice.AccessibilityServiceInfo info : list) {
            if (info.getId() != null && info.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    private void refreshCounts() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        float screenLengthCm = prefs.getFloat(Prefs.KEY_SCREEN_LENGTH_CM, 0f);

        new Thread(() -> {
            long todayStart  = getStartOf(Calendar.DAY_OF_YEAR);
            long weekStart   = getStartOfWeek();
            long monthStart  = getStartOf(Calendar.MONTH);
            long yearStart   = getStartOf(Calendar.YEAR);

            int today    = db.swipeDao().countSessionsSince(todayStart);
            int week     = db.swipeDao().countSessionsSince(weekStart);
            int month    = db.swipeDao().countSessionsSince(monthStart);
            int year     = db.swipeDao().countSessionsSince(yearStart);
            int lifetime = db.swipeDao().countAllSessions();

            float todayDist    = calculateDistance(today,    screenLengthCm);
            float weekDist     = calculateDistance(week,     screenLengthCm);
            float lifetimeDist = calculateDistance(lifetime, screenLengthCm);

            Long todayUsageMs    = db.swipeDao().getUsageMillisSince(todayStart);
            Long weekUsageMs     = db.swipeDao().getUsageMillisSince(weekStart);
            Long lifetimeUsageMs = db.swipeDao().getUsageMillisSince(0);

            float todayRpm    = calculateRPM(today,    todayUsageMs);
            float weekRpm     = calculateRPM(week,     weekUsageMs);
            float lifetimeRpm = calculateRPM(lifetime, lifetimeUsageMs);

            int dailyLimit  = prefs.getInt(Prefs.KEY_DAILY_LIMIT,  Prefs.DEFAULT_DAILY_LIMIT);
            int weeklyLimit = prefs.getInt(Prefs.KEY_WEEKLY_LIMIT, Prefs.DEFAULT_WEEKLY_LIMIT);

            uiHandler.post(() -> {
                todayCount.setText(String.valueOf(today));
                todayDistance.setText(String.format(Locale.US, "%.2f km", todayDist));
                todayRPM.setText(String.format(Locale.US, "%.1f rpm", todayRpm));
                updateDailyLimitButton(dailyLimit, today,
                        dailyLimit > 0 ? Math.max(0, dailyLimit - today) : -1);

                weeklyCount.setText(String.valueOf(week));
                weeklyDistance.setText(String.format(Locale.US, "%.2f km", weekDist));
                weeklyRPM.setText(String.format(Locale.US, "%.1f rpm", weekRpm));
                updateWeeklyLimitButton(weeklyLimit, week,
                        weeklyLimit > 0 ? Math.max(0, weeklyLimit - week) : -1);

                lifetimeCount.setText(String.valueOf(lifetime));
                lifetimeDistance.setText(String.format(Locale.US, "%.2f km", lifetimeDist));
                lifetimeRPM.setText(String.format(Locale.US, "%.1f rpm", lifetimeRpm));
            });
        }).start();
    }

    private float calculateDistance(int scrollCount, float screenLengthCm) {
        if (screenLengthCm <= 0) return 0f;
        return (scrollCount * screenLengthCm) / 100000f;
    }


    private float calculateRPM(int scrollCount, Long usageMs) {
        if (usageMs == null || usageMs <= 0) return 0f;
        long minutes = usageMs / (60 * 10000);
        if (minutes <= 0) return 0f;
        Log.d("rpm", "scrollCount=" + scrollCount + "  minutes=" + minutes + "  rpm=" + (float) scrollCount / minutes);
        return scrollCount / (float) minutes;
    }

    private long getStartOf(int field) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0);
        if (field == Calendar.MONTH) c.set(Calendar.DAY_OF_MONTH, 1);
        else if (field == Calendar.YEAR) { c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.MONTH, 0); }
        return c.getTimeInMillis();
    }

    private long getStartOfWeek() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
        return c.getTimeInMillis();
    }
}