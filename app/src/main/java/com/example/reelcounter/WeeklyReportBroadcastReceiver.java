package com.example.reelcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WeeklyReportBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && "com.example.reelcounter.WEEKLY_REPORT".equals(intent.getAction())) {
            WeeklyReportManager.sendWeeklyReport(context);
        }
    }
}