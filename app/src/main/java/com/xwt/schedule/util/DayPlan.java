package com.xwt.schedule.util;

import com.xwt.schedule.data.CourseStore;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 把「中国法定节假日 / 调休数据」与用户设置结合起来，回答两个问题：
 * <ol>
 *   <li>某一天到底上不上课（放假 / 调休上课 / 正常）；</li>
 *   <li>如果要上课，应该按星期几的课表上（调休补课日会换星期）。</li>
 * </ol>
 */
public final class DayPlan {

    /** 正常上课日。 */
    public static final int STATUS_NORMAL = 0;
    /** 法定节假日，不上课。 */
    public static final int STATUS_HOLIDAY = 1;
    /** 调休上班日，按指定星期的课表上课。 */
    public static final int STATUS_MAKEUP = 2;

    private DayPlan() {
    }

    /** 某天的状态。 */
    public static int status(CourseStore store, Date date) {
        ChinaHoliday.Day d = ChinaHoliday.of(date);
        if (d == null) return STATUS_NORMAL;
        if (d.isHoliday()) {
            return store.isHolidaySkipEnabled() ? STATUS_HOLIDAY : STATUS_NORMAL;
        }
        return store.isMakeupEnabled() ? STATUS_MAKEUP : STATUS_NORMAL;
    }

    /** 节日名称，非节假日返回 ""。 */
    public static String holidayName(Date date) {
        return ChinaHoliday.name(date);
    }

    /**
     * 某天实际上课采用的星期（{@link Calendar#DAY_OF_WEEK}，1=周日…7=周六）。
     * 当天不上课时返回 0。
     */
    public static int effectiveDayOfWeek(CourseStore store, Date date) {
        Calendar c = WeekUtil.atStartOfDay(date);
        int st = status(store, date);
        if (st == STATUS_HOLIDAY) return 0;
        if (st == STATUS_MAKEUP) {
            int dow = store.resolveMakeupDayOfWeek(WeekUtil.format(date));
            if (dow == CourseStore.MAKEUP_OFF) return 0;
            if (dow > 0) return dow;
        }
        return c.get(Calendar.DAY_OF_WEEK);
    }

    /** 网格表头 / 列表用的角标文字：「休」「补」或 ""。 */
    public static String badge(CourseStore store, Date date) {
        int st = status(store, date);
        if (st == STATUS_HOLIDAY) return "休";
        if (st == STATUS_MAKEUP) return "补";
        return "";
    }

    /** 一句话说明，如「国庆节 放假」「调休上课 · 按周二课表」；普通日返回 ""。 */
    public static String note(CourseStore store, Date date) {
        ChinaHoliday.Day d = ChinaHoliday.of(date);
        int st = status(store, date);
        if (st == STATUS_HOLIDAY) {
            String n = d == null ? "" : d.name;
            return n.isEmpty() ? "法定节假日放假" : n + " 放假";
        }
        if (st == STATUS_MAKEUP) {
            int dow = store.resolveMakeupDayOfWeek(WeekUtil.format(date));
            if (dow == CourseStore.MAKEUP_OFF) return "调休上班日（已设为不上课）";
            if (dow <= 0) dow = WeekUtil.atStartOfDay(date).get(Calendar.DAY_OF_WEEK);
            return "调休上课 · 按" + weekdayShort(dow) + "课表";
        }
        return "";
    }

    /** 某天是否要上课（用于「今日」页判断）。 */
    public static boolean hasClass(CourseStore store, Date date) {
        return effectiveDayOfWeek(store, date) != 0;
    }

    /** 学期内的全部节假日与调休日，按日期升序。 */
    public static List<ChinaHoliday.Day> inSemester(CourseStore store) {
        return ChinaHoliday.between(store.getSemesterStart(), store.getSemesterEnd());
    }

    /** 学期内「今天及以后」的节假日与调休日。 */
    public static List<ChinaHoliday.Day> upcoming(CourseStore store) {
        return ChinaHoliday.between(new Date(), store.getSemesterEnd());
    }

    public static String weekdayShort(int dayOfWeek) {
        int idx = dayOfWeek - 1;
        if (idx < 0 || idx >= TimeTable.WEEKDAY_SHORT.length) return "";
        return TimeTable.WEEKDAY_SHORT[idx];
    }

    /** 把 yyyy-MM-dd 转成「10月1日」。 */
    public static String shortDate(String date) {
        Calendar c = WeekUtil.atStartOfDay(WeekUtil.parse(date));
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
    }

    /** 带星期的日期，如「9月20日（周日）」。 */
    public static String shortDateWithWeek(String date) {
        Calendar c = WeekUtil.atStartOfDay(WeekUtil.parse(date));
        return shortDate(date) + "（" + weekdayShort(c.get(Calendar.DAY_OF_WEEK)) + "）";
    }

    /** 「课程」页顶部提示：最近一次放假 / 调休安排；没有则返回 ""。 */
    public static String upcomingTip(CourseStore store) {
        List<ChinaHoliday.Day> up = upcoming(store);
        if (up.isEmpty()) return "";
        ChinaHoliday.Day d = up.get(0);
        if (d.isHoliday()) {
            return "最近假期：" + shortDateWithWeek(d.date) + " " + d.name + "放假";
        }
        return "最近调休：" + shortDateWithWeek(d.date) + " " + note(store, WeekUtil.parse(d.date));
    }

    /**
     * 学期内节假日 / 调休的可读文本行，用于「我的」页展示与核对：
     * <pre>
     *   国庆节 10月1日–10月7日 放假 7 天
     *   9月20日（周日）调休上课 · 按周二课表
     * </pre>
     */
    public static List<String> describeSemester(CourseStore store) {
        List<String> out = new java.util.ArrayList<>();
        List<ChinaHoliday.Day> days = inSemester(store);
        int i = 0;
        while (i < days.size()) {
            ChinaHoliday.Day d = days.get(i);
            if (d.isHoliday()) {
                int j = i;
                while (j + 1 < days.size()) {
                    ChinaHoliday.Day nx = days.get(j + 1);
                    if (!nx.isHoliday() || !nx.name.equals(d.name)) break;
                    if (WeekUtil.daysBetween(WeekUtil.parse(days.get(j).date),
                            WeekUtil.parse(nx.date)) != 1) break;
                    j++;
                }
                String span = shortDate(d.date);
                int count = j - i + 1;
                if (count > 1) span = span + "–" + shortDate(days.get(j).date);
                out.add(d.name + " " + span + " 放假 " + count + " 天");
                i = j + 1;
            } else {
                Calendar c = WeekUtil.atStartOfDay(WeekUtil.parse(d.date));
                int dow = store.resolveMakeupDayOfWeek(d.date);
                String how;
                if (dow == CourseStore.MAKEUP_OFF) how = "不上课";
                else if (dow > 0) how = "按" + weekdayShort(dow) + "课表";
                else how = "按本日课表";
                out.add(shortDate(d.date) + "（" + weekdayShort(c.get(Calendar.DAY_OF_WEEK))
                        + "）调休上课 · " + how);
                i++;
            }
        }
        return out;
    }
}
