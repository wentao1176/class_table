package com.xwt.schedule.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.TimeTable;
import com.xwt.schedule.util.WeekUtil;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 为每节课排定“课前提醒”闹钟。
 * requestCode = courseId * 100 + 周次，保证同一门课同一周的闹钟稳定可取消。
 *
 * <p>排定按「日期」遍历而不是按「课程星期」遍历，因此天然支持法定节假日与调休：
 * 放假当天不排提醒；调休上班日改用被调休星期的课表排提醒。
 */
public final class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LOC = "loc";
    public static final String EXTRA_START = "start";
    public static final String EXTRA_SECTION = "section";
    public static final String EXTRA_LEAD = "lead";
    public static final String EXTRA_ID = "notifId";
    public static final String EXTRA_NOTE = "note";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private AlarmScheduler() {
    }

    private static int code(Course c, int week) {
        return (int) (c.id * 100L + week);
    }

    private static PendingIntent buildPi(Context ctx, Course c, int week, String note, int flags) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra(EXTRA_NAME, c.name);
        i.putExtra(EXTRA_LOC, c.location);
        i.putExtra(EXTRA_START, TimeTable.START[c.startSection - 1]);
        i.putExtra(EXTRA_SECTION, "第" + c.startSection + "节");
        i.putExtra(EXTRA_LEAD, Math.max(1, CourseStore.get(ctx).getReminderLeadMinutes()));
        i.putExtra(EXTRA_NOTE, note == null ? "" : note);
        int rc = code(c, week);
        i.putExtra(EXTRA_ID, rc);
        int piFlag = flags | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, rc, i, piFlag);
    }

    /** 在后台线程重建提醒，避免整学期闹钟重排阻塞主线程。 */
    public static void rescheduleAsync(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            try {
                reschedule(app);
            } catch (Throwable t) {
                Log.w(TAG, "reschedule failed", t);
            }
        });
    }

    /** 全量重排：先取消旧闹钟，再按课表排定未来的提醒。 */
    public static void reschedule(Context ctx) {
        Context app = ctx.getApplicationContext();
        CourseStore store = CourseStore.get(app);
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        List<Course> list = store.all();
        int totalWeeks = store.getTotalWeeks();

        // 1) 取消旧闹钟（遍历整学期所有可能的 requestCode）
        for (Course c : list) {
            for (int w = 1; w <= totalWeeks; w++) {
                PendingIntent pi = buildPi(app, c, w, "", PendingIntent.FLAG_NO_CREATE);
                if (pi != null && am != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        }

        if (!store.isReminderEnabled() || am == null) return;

        // 2) 排定未来的提醒
        int lead = Math.max(1, store.getReminderLeadMinutes());
        long now = System.currentTimeMillis();
        int scheduled = 0;

        for (int w = 1; w <= totalWeeks; w++) {
            for (int col = 0; col < 7; col++) {
                Calendar day = WeekUtil.dateOf(store, w, col);
                // 法定节假日返回 0（不上课）；调休上班日返回被调休的星期
                int effectiveDay = DayPlan.effectiveDayOfWeek(store, day.getTime());
                if (effectiveDay == 0) continue;
                String note = DayPlan.status(store, day.getTime()) == DayPlan.STATUS_MAKEUP
                        ? DayPlan.note(store, day.getTime()) : "";

                for (Course c : list) {
                    if (c.day != effectiveDay || !c.occursInWeek(w)) continue;
                    Calendar cal = (Calendar) day.clone();
                    String[] hm = TimeTable.START[c.startSection - 1].split(":");
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long triggerAt = cal.getTimeInMillis() - lead * 60_000L;
                    if (triggerAt <= now) continue; // 已过的提醒不排

                    PendingIntent pi = buildPi(app, c, w, note, PendingIntent.FLAG_UPDATE_CURRENT);
                    setAlarm(am, triggerAt, pi);
                    scheduled++;
                }
            }
        }
        Log.i(TAG, "rescheduled " + scheduled + " reminders, lead=" + lead + "min");
    }

    private static void setAlarm(AlarmManager am, long triggerAt, PendingIntent pi) {
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exact = am.canScheduleExactAlarms();
        }
        if (exact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                return;
            } catch (SecurityException e) {
                exact = false;
            }
        }
        // 无精准闹钟权限时退化为允许 Doze 唤醒的非精准闹钟（仍会在分钟级触发）
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
    }
}
