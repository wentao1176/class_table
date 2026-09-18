package com.xwt.schedule.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中国国家法定节假日与调休（补班 / 补课）数据。
 *
 * <p>数据来源为国务院办公厅官方通知：
 * <ul>
 *   <li>《国务院办公厅关于2025年部分节假日安排的通知》国办发明电〔2024〕12号</li>
 *   <li>《国务院办公厅关于2026年部分节假日安排的通知》国办发明电〔2025〕7号</li>
 * </ul>
 *
 * <p>说明：官方通知只规定「哪几天放假、哪几天上班」，并不规定补班日按星期几的课表上课，
 * 各高校教务处的补课安排并不统一。因此这里按「补班日补足假期中被占用的工作日」的原则
 * 给出一套默认映射（见 {@link #defaultWorkAs(String)}），App 内可由用户逐日修改。
 *
 * <p>未收录年份回退为「法定节日当天放假、不做调休」的简化规则，见 {@link #FALLBACK}。
 */
public final class ChinaHoliday {

    /** 普通日期（既不是法定假日，也不是调休上班日）。 */
    public static final int TYPE_NORMAL = 0;
    /** 法定放假日：不上课。 */
    public static final int TYPE_HOLIDAY = 1;
    /** 调休上班日：本来是周末，但按工作日上课。 */
    public static final int TYPE_MAKEUP = 2;

    /** 节日放假区间：起|止|法定假日天数|名称。 */
    private static final String[] SPANS = {
            // ---------------- 2025 年 ----------------
            "2025-01-01|2025-01-01|1|元旦",
            "2025-01-28|2025-02-04|4|春节",
            "2025-04-04|2025-04-06|1|清明节",
            "2025-05-01|2025-05-05|2|劳动节",
            "2025-05-31|2025-06-02|1|端午节",
            "2025-10-01|2025-10-08|4|国庆节·中秋节",
            // ---------------- 2026 年 ----------------
            "2026-01-01|2026-01-03|1|元旦",
            "2026-02-15|2026-02-23|4|春节",
            "2026-04-04|2026-04-06|1|清明节",
            "2026-05-01|2026-05-05|2|劳动节",
            "2026-06-19|2026-06-21|1|端午节",
            "2026-09-25|2026-09-27|1|中秋节",
            "2026-10-01|2026-10-07|3|国庆节",
    };

    /** 调休上班（补班 / 补课）日。 */
    private static final String[] MAKEUP_DAYS = {
            // 2025：1月26日、2月8日、4月27日、9月28日、10月11日上班
            "2025-01-26", "2025-02-08", "2025-04-27", "2025-09-28", "2025-10-11",
            // 2026：1月4日、2月14日、2月28日、5月9日、9月20日、10月10日上班
            "2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09",
            "2026-09-20", "2026-10-10",
    };

    /** 未收录年份的兜底法定假日：元旦 1/1、劳动节 5/1、国庆节 10/1~10/3。 */
    private static final int[][] FALLBACK = {
            {Calendar.JANUARY, 1}, {Calendar.MAY, 1},
            {Calendar.OCTOBER, 1}, {Calendar.OCTOBER, 2}, {Calendar.OCTOBER, 3},
    };

    /** 一个特殊日期。 */
    public static final class Day {
        /** 日期，yyyy-MM-dd。 */
        public final String date;
        /** {@link #TYPE_HOLIDAY} 或 {@link #TYPE_MAKEUP}。 */
        public final int type;
        /** 节日名称；调休日没有名称时为 ""。 */
        public final String name;

        Day(String date, int type, String name) {
            this.date = date;
            this.type = type;
            this.name = name;
        }

        public boolean isHoliday() {
            return type == TYPE_HOLIDAY;
        }

        public boolean isMakeup() {
            return type == TYPE_MAKEUP;
        }

        /** 「休」/「补」角标文字。 */
        public String badge() {
            return isHoliday() ? "休" : "补";
        }
    }

    private static final Map<String, Day> INDEX = new LinkedHashMap<>();
    private static final Map<String, Integer> DEFAULT_WORK_AS = new LinkedHashMap<>();
    private static final List<String> MAKEUP_ORDER = new ArrayList<>();

    static {
        build();
    }

    private ChinaHoliday() {
    }

    private static void build() {
        for (String span : SPANS) {
            String[] p = span.split("\\|");
            String from = p[0];
            String to = p[1];
            String name = p[3];
            Calendar c = WeekUtil.atStartOfDay(WeekUtil.parse(from));
            Calendar end = WeekUtil.atStartOfDay(WeekUtil.parse(to));
            while (!c.after(end)) {
                String key = WeekUtil.format(c.getTime());
                INDEX.put(key, new Day(key, TYPE_HOLIDAY, name));
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        for (String d : MAKEUP_DAYS) {
            INDEX.put(d, new Day(d, TYPE_MAKEUP, ""));
            MAKEUP_ORDER.add(d);
        }
        Collections.sort(MAKEUP_ORDER);
        buildDefaultWorkAs();
    }

    /**
     * 默认补课映射：把每个假期中「被占用的工作日」（周一~周五、且不在法定假日范围内）
     * 与对应的调休上班日配对，得到「调休日按周几上课」。
     */
    private static void buildDefaultWorkAs() {
        for (String span : SPANS) {
            String[] p = span.split("\\|");
            String from = p[0];
            String to = p[1];
            int statutory = Integer.parseInt(p[2]);

            // 假期内的工作日（跳过区间开头的法定假日）
            List<String> borrowed = new ArrayList<>();
            Calendar c = WeekUtil.atStartOfDay(WeekUtil.parse(from));
            Calendar end = WeekUtil.atStartOfDay(WeekUtil.parse(to));
            int offset = 0;
            while (!c.after(end)) {
                int dow = c.get(Calendar.DAY_OF_WEEK);
                if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY && offset >= statutory) {
                    borrowed.add(WeekUtil.format(c.getTime()));
                }
                offset++;
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            if (borrowed.isEmpty()) continue;

            // 与该假期相邻（前后 14 天内）的调休上班日
            List<String> makeups = new ArrayList<>();
            for (String m : MAKEUP_ORDER) {
                if (near(m, from, to, 14)) makeups.add(m);
            }
            if (makeups.isEmpty()) continue;

            int n = Math.min(makeups.size(), borrowed.size());
            // 取假期末尾被占用的工作日与调休日依次配对
            List<String> tail = borrowed.subList(borrowed.size() - n, borrowed.size());
            for (int i = 0; i < n; i++) {
                int dow = WeekUtil.atStartOfDay(WeekUtil.parse(tail.get(i))).get(Calendar.DAY_OF_WEEK);
                DEFAULT_WORK_AS.put(makeups.get(i), dow);
            }
        }
    }

    private static boolean near(String date, String from, String to, int days) {
        long d = WeekUtil.atStartOfDay(WeekUtil.parse(date)).getTimeInMillis();
        long f = WeekUtil.atStartOfDay(WeekUtil.parse(from)).getTimeInMillis();
        long t = WeekUtil.atStartOfDay(WeekUtil.parse(to)).getTimeInMillis();
        long span = (long) days * 86400000L;
        return d >= f - span && d <= t + span;
    }

    // ---------------- 查询 ----------------

    /** 某天的节假日信息；普通日期返回 null。 */
    public static Day of(Date date) {
        if (date == null) return null;
        String key = WeekUtil.format(date);
        Day d = INDEX.get(key);
        if (d != null) return d;
        if (!covered(key.substring(0, 4))) return fallback(date);
        return null;
    }

    public static boolean isHoliday(Date date) {
        Day d = of(date);
        return d != null && d.isHoliday();
    }

    public static boolean isMakeup(Date date) {
        Day d = of(date);
        return d != null && d.isMakeup();
    }

    /** 节日名称，普通日期返回 ""。 */
    public static String name(Date date) {
        Day d = of(date);
        return d == null ? "" : d.name;
    }

    /**
     * 调休上班日的默认补课星期（{@link Calendar#DAY_OF_WEEK}）；未收录返回 0。
     */
    public static int defaultWorkAs(String date) {
        Integer v = DEFAULT_WORK_AS.get(date);
        return v == null ? 0 : v;
    }

    /** 数据中是否收录了该年份。 */
    public static boolean covered(String year) {
        for (String span : SPANS) {
            if (span.startsWith(year + "-")) return true;
        }
        return false;
    }

    /** 收录的全部特殊日期（按日期升序）。 */
    public static List<Day> all() {
        List<Day> list = new ArrayList<>(INDEX.values());
        Collections.sort(list, (a, b) -> a.date.compareTo(b.date));
        return list;
    }

    /** 收录的调休上班日（按日期升序）。 */
    public static List<Day> allMakeupDays() {
        List<Day> list = new ArrayList<>();
        for (String d : MAKEUP_ORDER) list.add(INDEX.get(d));
        return list;
    }

    /** 指定日期区间内（含端点）的特殊日期，按日期升序。 */
    public static List<Day> between(Date from, Date to) {
        List<Day> list = new ArrayList<>();
        Calendar c = WeekUtil.atStartOfDay(from);
        Calendar end = WeekUtil.atStartOfDay(to);
        while (!c.after(end)) {
            Day d = of(c.getTime());
            if (d != null) list.add(d);
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return list;
    }

    private static Day fallback(Date date) {
        Calendar c = WeekUtil.atStartOfDay(date);
        for (int[] md : FALLBACK) {
            if (c.get(Calendar.MONTH) == md[0] && c.get(Calendar.DAY_OF_MONTH) == md[1]) {
                String name;
                if (md[0] == Calendar.JANUARY) name = "元旦";
                else if (md[0] == Calendar.MAY) name = "劳动节";
                else name = "国庆节";
                return new Day(WeekUtil.format(date), TYPE_HOLIDAY, name);
            }
        }
        return null;
    }
}
