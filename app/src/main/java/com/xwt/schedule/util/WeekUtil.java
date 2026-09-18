package com.xwt.schedule.util;

import com.xwt.schedule.data.CourseStore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 学期 / 周次 / 日期换算。
 * 约定：开学日期为“第 1 周周日”，一周从周日开始（与课表列序一致）。
 */
public final class WeekUtil {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    private WeekUtil() {}

    public static synchronized String format(Date d) {
        return FMT.format(d);
    }

    public static synchronized Date parse(String s) {
        try {
            return FMT.parse(s);
        } catch (ParseException e) {
            return new Date();
        }
    }

    public static Calendar atStartOfDay(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    public static int daysBetween(Date a, Date b) {
        long ms = atStartOfDay(b).getTimeInMillis() - atStartOfDay(a).getTimeInMillis();
        return (int) Math.round(ms / 86400000.0);
    }

    /** 今天是学期第几周（1 起）；未开学返回 1，已放假返回总周数。 */
    public static int currentWeek(CourseStore store) {
        return weekOfDate(store, Calendar.getInstance().getTime());
    }

    public static int weekOfDate(CourseStore store, Date date) {
        int diff = daysBetween(store.getSemesterStart(), date);
        int week = diff / 7 + 1;
        return Math.max(1, Math.min(store.getTotalWeeks(), week));
    }

    /** 第 week 周、第 col 列（0=周日）的日期。 */
    public static Calendar dateOf(CourseStore store, int week, int col) {
        Calendar c = atStartOfDay(store.getSemesterStart());
        c.add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + col);
        return c;
    }

    /** 今天的列序（0=周日…6=周六）。 */
    public static int todayColumn() {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
    }

    public static String mdc(Calendar c) {
        return (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH);
    }

    public static String cnDate(Calendar c) {
        return c.get(Calendar.MONTH) + 1 + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
    }
}
