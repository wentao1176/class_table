package com.xwt.schedule.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机、应用更新、系统时间/时区变化后重建全部提醒。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmScheduler.reschedule(context);
    }
}
