package com.example.reelcounter;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;
import java.util.Locale;

public class WeeklyReportManager {

    private static final String WEEKLY_REPORT_ACTION = "com.example.reelcounter.WEEKLY_REPORT";

    private static final int WEEKLY_REPORT_ID     = 100;
    private static final int OVER_WEEKLY_LIMIT_ID = 101;
    private static final int OVER_DAILY_LIMIT_ID  = 102;

    private static final String KEY_DAILY_LIMIT_NOTIFIED_DAY   = "daily_limit_notified_day";
    private static final String KEY_WEEKLY_LIMIT_NOTIFIED_WEEK = "weekly_limit_notified_week";

    // ── Scheduling ────────────────────────────────────────────────────────────

    public static void scheduleWeeklyReport(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, WeeklyReportBroadcastReceiver.class);
        intent.setAction(WEEKLY_REPORT_ACTION);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                WEEKLY_REPORT_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY * 7,
                    pendingIntent
            );
        }
    }

    // ── Sunday report ─────────────────────────────────────────────────────────

    public static void sendWeeklyReport(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        SharedPreferences prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        float screenLengthCm = prefs.getFloat(Prefs.KEY_SCREEN_LENGTH_CM, 0f);
        int weeklyLimit = prefs.getInt(Prefs.KEY_WEEKLY_LIMIT, Prefs.DEFAULT_WEEKLY_LIMIT);
        int dailyLimit  = prefs.getInt(Prefs.KEY_DAILY_LIMIT,  Prefs.DEFAULT_DAILY_LIMIT);

        new Thread(() -> {
            // Time boundaries
            long thisWeekStart = getStartOfWeek();
            long lastWeekStart = thisWeekStart - (7L * 24 * 60 * 60 * 1000);
            long lastWeekEnd   = thisWeekStart; // exclusive
            long todayStart    = getStartOfDay();

            // This week
            int thisWeekCount = db.swipeDao().countSessionsSince(thisWeekStart);
            Long thisWeekUsageMs = db.swipeDao().getUsageMillisSince(thisWeekStart);
            float thisWeekDist = calculateDistance(thisWeekCount, screenLengthCm);
            float thisWeekRpm  = calculateRPM(thisWeekCount, thisWeekUsageMs);

            // Last week — count sessions that fall strictly within last week's window.
            // countSessionsBetween queries timestamp >= lastWeekStart AND timestamp < lastWeekEnd.
            int lastWeekCount = db.swipeDao().countSessionsBetween(lastWeekStart, lastWeekEnd);
            Long lastWeekUsageMs = db.swipeDao().getUsageMillisBetween(lastWeekStart, lastWeekEnd);
            float lastWeekDist = calculateDistance(lastWeekCount, screenLengthCm);
            float lastWeekRpm  = calculateRPM(lastWeekCount, lastWeekUsageMs);

            // Today (for daily limit line)
            int todayCount = db.swipeDao().countSessionsSince(todayStart);

            // ── Build notification body ───────────────────────────────────────

            StringBuilder body = new StringBuilder();

            // This week section
            body.append("📅 THIS WEEK\n");
            body.append(thisWeekCount).append(" reels");
            if (screenLengthCm > 0) {
                body.append(" · ").append(String.format(Locale.US, "%.2f km", thisWeekDist));
            }
            if (thisWeekRpm > 0) {
                body.append(" · ").append(String.format(Locale.US, "%.1f rpm", thisWeekRpm));
            }
            body.append("\n");

            // Weekly limit status
            if (weeklyLimit > 0) {
                if (thisWeekCount > weeklyLimit) {
                    body.append("⚠️ Weekly limit of ").append(weeklyLimit)
                            .append(" exceeded by ").append(thisWeekCount - weeklyLimit).append(" reels!\n");
                } else {
                    body.append("Limit: ").append(thisWeekCount).append(" / ")
                            .append(weeklyLimit).append(" reels used.\n");
                }
            }

            // Daily limit status (today = Sunday of the report)
            if (dailyLimit > 0) {
                if (todayCount > dailyLimit) {
                    body.append("⚠️ Today's limit of ").append(dailyLimit)
                            .append(" exceeded by ").append(todayCount - dailyLimit).append(" reels!\n");
                } else {
                    body.append("Today: ").append(todayCount).append(" / ")
                            .append(dailyLimit).append(" reels.\n");
                }
            }

            // Last week section
            body.append("\n📅 LAST WEEK\n");
            if (lastWeekCount <= 0) {
                body.append("No data recorded.");
            } else {
                body.append(lastWeekCount).append(" reels");
                if (screenLengthCm > 0) {
                    body.append(" · ").append(String.format(Locale.US, "%.2f km", lastWeekDist));
                }
                if (lastWeekRpm > 0) {
                    body.append(" · ").append(String.format(Locale.US, "%.1f rpm", lastWeekRpm));
                }
                body.append("\n");

                // Trend vs last week
                if (thisWeekCount > 0 && lastWeekCount > 0) {
                    int pct = (int) (((thisWeekCount - lastWeekCount) / (float) lastWeekCount) * 100);
                    if (pct > 0) {
                        body.append("↑ ").append(pct).append("% more than last week. 📈");
                    } else if (pct < 0) {
                        body.append("↓ ").append(Math.abs(pct)).append("% less than last week. 📉");
                    } else {
                        body.append("Same as last week. 😐");
                    }
                }
            }

            // Send main weekly summary
            sendNotification(context,
                    "Your Weekly Reels Report 🎬",
                    body.toString(),
                    WEEKLY_REPORT_ID,
                    ReelCounterApp.CHANNEL_REPORTS);

            // Extra over-limit alerts
            if (weeklyLimit > 0 && thisWeekCount > weeklyLimit) {
                sendNotification(context,
                        "Weekly reel limit exceeded 🚨",
                        "You've watched " + thisWeekCount + " reels this week — "
                                + (thisWeekCount - weeklyLimit) + " over your limit of " + weeklyLimit + ".",
                        OVER_WEEKLY_LIMIT_ID,
                        ReelCounterApp.CHANNEL_LIMITS);
            }

            if (dailyLimit > 0 && todayCount > dailyLimit) {
                sendNotification(context,
                        "Daily reel limit exceeded 🚨",
                        "You've watched " + todayCount + " reels today — "
                                + (todayCount - dailyLimit) + " over your daily limit of " + dailyLimit + ".",
                        OVER_DAILY_LIMIT_ID,
                        ReelCounterApp.CHANNEL_LIMITS);
            }

            // Reset "already notified" flags so next week starts clean
            prefs.edit()
                    .putLong(Prefs.KEY_LAST_WEEKLY_REPORT, System.currentTimeMillis())
                    .remove(KEY_DAILY_LIMIT_NOTIFIED_DAY)
                    .remove(KEY_WEEKLY_LIMIT_NOTIFIED_WEEK)
                    .apply();
        }).start();
    }

    // ── Real-time limit check ─────────────────────────────────────────────────

    public static void checkLimitsAndNotify(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        int dailyLimit  = prefs.getInt(Prefs.KEY_DAILY_LIMIT,  Prefs.DEFAULT_DAILY_LIMIT);
        int weeklyLimit = prefs.getInt(Prefs.KEY_WEEKLY_LIMIT, Prefs.DEFAULT_WEEKLY_LIMIT);

        if (dailyLimit <= 0 && weeklyLimit <= 0) return;

        AppDatabase db = AppDatabase.getInstance(context);

        long todayStart = getStartOfDay();
        long weekStart  = getStartOfWeek();

        String todayKey = KEY_DAILY_LIMIT_NOTIFIED_DAY + "_" + todayStart;
        String weekKey  = KEY_WEEKLY_LIMIT_NOTIFIED_WEEK + "_" + weekStart;

        if (dailyLimit > 0 && !prefs.getBoolean(todayKey, false)) {
            int todayCount = db.swipeDao().countSessionsSince(todayStart);
            if (todayCount > dailyLimit) {
                sendNotification(context,
                        "Daily reel limit reached 🚨",
                        "You've watched " + todayCount + " reels today — "
                                + (todayCount - dailyLimit) + " over your daily limit of " + dailyLimit + ".",
                        OVER_DAILY_LIMIT_ID,
                        ReelCounterApp.CHANNEL_LIMITS);
                prefs.edit().putBoolean(todayKey, true).apply();
            }
        }

        if (weeklyLimit > 0 && !prefs.getBoolean(weekKey, false)) {
            int weekCount = db.swipeDao().countSessionsSince(weekStart);
            if (weekCount > weeklyLimit) {
                sendNotification(context,
                        "Weekly reel limit reached 🚨",
                        "You've watched " + weekCount + " reels this week — "
                                + (weekCount - weeklyLimit) + " over your weekly limit of " + weeklyLimit + ".",
                        OVER_WEEKLY_LIMIT_ID,
                        ReelCounterApp.CHANNEL_LIMITS);
                prefs.edit().putBoolean(weekKey, true).apply();
            }
        }
    }

    // ── Notification helper ───────────────────────────────────────────────────

    public static void sendNotification(Context context, String title, String message,
                                        int notificationId, String channelId) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message)
                        .setBigContentTitle(title))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        nm.notify(notificationId, builder.build());
    }

    // ── Time helpers ──────────────────────────────────────────────────────────

    static long getStartOfDay() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long getStartOfWeek() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
        return c.getTimeInMillis();
    }

    private static float calculateDistance(int scrollCount, float screenLengthCm) {
        if (screenLengthCm <= 0) return 0f;
        return (scrollCount * screenLengthCm) / 100000f;
    }

    private static float calculateRPM(int scrollCount, Long usageMs) {
        if (usageMs == null || usageMs <= 0) return 0f;
        long minutes = usageMs / (60 * 1000);
        if (minutes <= 0) return 0f;
        return scrollCount / (float) minutes;
    }
}