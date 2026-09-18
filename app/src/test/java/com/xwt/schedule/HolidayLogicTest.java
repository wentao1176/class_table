package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xwt.schedule.util.ChinaHoliday;
import com.xwt.schedule.util.WeekUtil;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 中国法定节假日 / 调休数据的纯 JVM 单测。
 * 数据以国务院办公厅 2025 / 2026 年节假日安排通知为准。
 */
public class HolidayLogicTest {

    private static Date d(String s) {
        return WeekUtil.parse(s);
    }

    @Test
    public void nationalDay2026IsHoliday() {
        for (int day = 1; day <= 7; day++) {
            String key = String.format("2026-10-%02d", day);
            assertTrue(key + " 应为法定假日", ChinaHoliday.isHoliday(d(key)));
            assertEquals("国庆节", ChinaHoliday.name(d(key)));
        }
        assertFalse(ChinaHoliday.isHoliday(d("2026-09-30")));
        assertFalse(ChinaHoliday.isHoliday(d("2026-10-08")));
    }

    @Test
    public void midAutumn2026IsHoliday() {
        assertTrue(ChinaHoliday.isHoliday(d("2026-09-25")));
        assertTrue(ChinaHoliday.isHoliday(d("2026-09-26")));
        assertTrue(ChinaHoliday.isHoliday(d("2026-09-27")));
        assertEquals("中秋节", ChinaHoliday.name(d("2026-09-25")));
        assertFalse(ChinaHoliday.isHoliday(d("2026-09-24")));
        assertFalse(ChinaHoliday.isHoliday(d("2026-09-28")));
    }

    @Test
    public void makeupWorkdays2026() {
        String[] days = {"2026-01-04", "2026-02-14", "2026-02-28",
                "2026-05-09", "2026-09-20", "2026-10-10"};
        for (String key : days) {
            assertTrue(key + " 应为调休上班日", ChinaHoliday.isMakeup(d(key)));
            assertFalse(key + " 不应同时是假日", ChinaHoliday.isHoliday(d(key)));
        }
    }

    @Test
    public void makeupDaysFallOnWeekend() {
        // 2026-09-20 是周日、2026-10-10 是周六，正因调休才需要上课
        assertEquals(Calendar.SUNDAY, WeekUtil.atStartOfDay(d("2026-09-20")).get(Calendar.DAY_OF_WEEK));
        assertEquals(Calendar.SATURDAY, WeekUtil.atStartOfDay(d("2026-10-10")).get(Calendar.DAY_OF_WEEK));
    }

    @Test
    public void defaultMakeupMappingIsDerivedFromBorrowedWorkdays() {
        // 国庆占用 10/6（周二）、10/7（周三），由 9/20、10/10 依次补齐
        assertEquals(Calendar.TUESDAY, ChinaHoliday.defaultWorkAs("2026-09-20"));
        assertEquals(Calendar.WEDNESDAY, ChinaHoliday.defaultWorkAs("2026-10-10"));
        // 春节占用 2/20（周五）、2/23（周一）
        assertEquals(Calendar.FRIDAY, ChinaHoliday.defaultWorkAs("2026-02-14"));
        assertEquals(Calendar.MONDAY, ChinaHoliday.defaultWorkAs("2026-02-28"));
        // 劳动节占用 5/5（周二）
        assertEquals(Calendar.TUESDAY, ChinaHoliday.defaultWorkAs("2026-05-09"));
        // 元旦占用 1/2（周五）
        assertEquals(Calendar.FRIDAY, ChinaHoliday.defaultWorkAs("2026-01-04"));
    }

    @Test
    public void ordinaryDaysAreNormal() {
        String[] days = {"2026-09-13", "2026-09-18", "2026-09-21", "2026-11-11"};
        for (String key : days) {
            assertNull(key + " 应为普通日期", ChinaHoliday.of(d(key)));
        }
    }

    @Test
    public void unknownYearFallsBackToStatutoryDays() {
        // 2027 年安排尚未公布，至少保证元旦 / 劳动节 / 国庆法定当天放假
        assertTrue(ChinaHoliday.isHoliday(d("2027-01-01")));
        assertTrue(ChinaHoliday.isHoliday(d("2027-05-01")));
        assertTrue(ChinaHoliday.isHoliday(d("2027-10-01")));
        assertTrue(ChinaHoliday.isHoliday(d("2027-10-03")));
        assertFalse(ChinaHoliday.isHoliday(d("2027-10-04")));
        assertFalse(ChinaHoliday.isHoliday(d("2027-03-08")));
    }

    @Test
    public void semesterRangeContainsAutumnFestivalAndNationalDay() {
        Date start = d("2026-09-13");
        Date end = d("2027-01-30");
        List<ChinaHoliday.Day> list = ChinaHoliday.between(start, end);
        int holidays = 0, makeups = 0;
        for (ChinaHoliday.Day day : list) {
            if (day.isHoliday()) holidays++;
            else makeups++;
        }
        // 中秋 3 天 + 国庆 7 天 + 元旦（2027-01-01 兜底）1 天
        assertEquals(11, holidays);
        // 9/20、10/10 两个调休日
        assertEquals(2, makeups);
        assertNotNull(list.get(0).date);
        assertEquals("2026-09-20", list.get(0).date);
    }

    @Test
    public void consecutiveHolidaysAreMergedInText() {
        // 春节 2026-02-15 ~ 2026-02-23 共 9 天
        List<ChinaHoliday.Day> list = ChinaHoliday.between(d("2026-02-15"), d("2026-02-23"));
        assertEquals(9, list.size());
        boolean allSpringFestival = true;
        for (ChinaHoliday.Day day : list) {
            if (!"春节".equals(day.name)) allSpringFestival = false;
        }
        assertTrue(allSpringFestival);
    }

    @Test
    public void allDatesAreSortedAndUnique() {
        List<ChinaHoliday.Day> all = ChinaHoliday.all();
        assertTrue(all.size() >= 40);
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).date.compareTo(all.get(i).date) < 0);
        }
    }
}
