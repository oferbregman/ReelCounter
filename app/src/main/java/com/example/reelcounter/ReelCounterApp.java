package com.example.reelcounter;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class ReelCounterApp extends Application {

    public static final String CHANNEL_REPORTS = "reel_counter_weekly";
    public static final String CHANNEL_LIMITS  = "reel_counter_limits";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // Weekly summary — default importance (no pop-up, shows in shade)
        NotificationChannel reports = new NotificationChannel(
                CHANNEL_REPORTS,
                "Weekly Report",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        reports.setDescription("Your Sunday reel-count summary.");
        nm.createNotificationChannel(reports);

        // Limit alerts — high importance so the banner pops up immediately
        NotificationChannel limits = new NotificationChannel(
                CHANNEL_LIMITS,
                "Limit Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        limits.setDescription("Fires the moment you cross your daily or weekly reel limit.");
        nm.createNotificationChannel(limits);
    }
}