package com.xwt.schedule.notify;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.xwt.schedule.App;
import com.xwt.schedule.MainActivity;
import com.xwt.schedule.R;

/** 通知构建与发送。 */
public final class NotificationHelper {

    private NotificationHelper() {}

    public static void showReminder(Context ctx, int notifId, String name,
                                    String location, String startTime,
                                    String sectionText, int leadMinutes) {
        showReminder(ctx, notifId, name, location, startTime, sectionText, leadMinutes, "");
    }

    public static void showReminder(Context ctx, int notifId, String name,
                                    String location, String startTime,
                                    String sectionText, int leadMinutes, String note) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlag = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent contentPi = PendingIntent.getActivity(ctx, notifId, open, piFlag);

        String loc = (location == null || location.isEmpty()) ? "" : "，地点 " + location;
        String tail = (note == null || note.isEmpty()) ? "" : "（" + note + "）";
        String text = startTime + " 上" + name + loc + tail
                + "，还有 " + leadMinutes + " 分钟上课，别迟到啦";

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, App.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle("上课提醒：" + name)
                .setContentText(startTime + " 上课" + loc + "（提前" + leadMinutes + "分钟）")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        try {
            NotificationManagerCompat.from(ctx).notify(notifId, b.build());
        } catch (SecurityException ignored) {
            // Android 13+ 未授予通知权限时静默失败
        }
    }

    public static void showTest(Context ctx) {
        showReminder(ctx, Integer.MAX_VALUE - 1, "示例课程", "中103",
                "08:00", "第1-2节", 15);
    }
}
