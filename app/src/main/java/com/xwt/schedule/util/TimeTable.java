package com.xwt.schedule.util;

/**
 * 大学作息时间表：一天 11 节课。
 * 时间参照湖南大学秋季作息（与参考小程序一致）。
 */
public final class TimeTable {

    public static final int SECTIONS = 11;

    public static final String[] START = {
            "08:00", "08:55", "10:00", "10:55", "14:30",
            "15:15", "16:10", "16:55", "19:00", "19:55", "20:50"
    };

    public static final String[] END = {
            "08:45", "09:40", "10:45", "11:40", "15:15",
            "16:00", "16:55", "17:40", "19:45", "20:40", "21:35"
    };

    /** 列序：0=周日 ... 6=周六（与参考小程序一致，周日为一周第一列） */
    public static final String[] WEEKDAY_SHORT = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private TimeTable() {}

    public static String rangeText(int startSection, int sectionCount) {
        int s = startSection;
        int e = Math.min(SECTIONS, startSection + sectionCount - 1);
        return "第" + s + "-" + e + "节 " + START[s - 1] + "-" + END[e - 1];
    }

    public static int toMinutes(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
}
