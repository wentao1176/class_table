package com.xwt.schedule.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 到点接收闹钟，弹出上课提醒通知。 */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String name = intent.getStringExtra(AlarmScheduler.EXTRA_NAME);
        String loc = intent.getStringExtra(AlarmScheduler.EXTRA_LOC);
        String start = intent.getStringExtra(AlarmScheduler.EXTRA_START);
        String section = intent.getStringExtra(AlarmScheduler.EXTRA_SECTION);
        int lead = intent.getIntExtra(AlarmScheduler.EXTRA_LEAD, 15);
        String note = intent.getStringExtra(AlarmScheduler.EXTRA_NOTE);
        int id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, (int) (System.currentTimeMillis() / 1000));
        if (name == null) return;
        NotificationHelper.showReminder(context, id, name, loc == null ? "" : loc,
                start == null ? "" : start, section == null ? "" : section, lead,
                note == null ? "" : note);
    }
}
