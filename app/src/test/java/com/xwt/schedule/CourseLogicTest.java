package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xwt.schedule.model.Course;
import com.xwt.schedule.util.TimeTable;

import org.junit.Test;

import java.util.Arrays;

/** 课程周次逻辑纯 JVM 单测。 */
public class CourseLogicTest {

    @Test
    public void weeklyCourseOccursEveryWeekInRange() {
        Course c = new Course();
        c.weekType = Course.TYPE_ALL;
        c.weekStart = 1;
        c.weekEnd = 16;
        assertTrue(c.occursInWeek(1));
        assertTrue(c.occursInWeek(8));
        assertTrue(c.occursInWeek(16));
        assertFalse(c.occursInWeek(17));
    }

    @Test
    public void oddEvenWeeks() {
        Course odd = new Course();
        odd.weekType = Course.TYPE_ODD;
        odd.weekStart = 1;
        odd.weekEnd = 20;
        assertTrue(odd.occursInWeek(1));
        assertFalse(odd.occursInWeek(2));
        assertTrue(odd.occursInWeek(19));

        Course even = new Course();
        even.weekType = Course.TYPE_EVEN;
        even.weekStart = 1;
        even.weekEnd = 20;
        assertFalse(even.occursInWeek(1));
        assertTrue(even.occursInWeek(2));
        assertTrue(even.occursInWeek(20));
    }

    @Test
    public void customWeeks() {
        Course c = new Course();
        c.weekType = Course.TYPE_CUSTOM;
        c.weekStart = 1;
        c.weekEnd = 20;
        c.customWeeks = Arrays.asList(2, 5, 9);
        assertTrue(c.occursInWeek(2));
        assertTrue(c.occursInWeek(9));
        assertFalse(c.occursInWeek(3));
    }

    @Test
    public void weekTextReadable() {
        Course c = new Course();
        c.weekType = Course.TYPE_ALL;
        c.weekStart = 1;
        c.weekEnd = 20;
        assertEquals("每周", c.weekText(20));
        c.weekEnd = 16;
        assertEquals("1-16周", c.weekText(20));
        c.weekType = Course.TYPE_ODD;
        c.weekEnd = 20;
        assertEquals("单周", c.weekText(20).trim());
    }

    @Test
    public void timeTableHasElevenSections() {
        assertEquals(11, TimeTable.SECTIONS);
        assertEquals(11, TimeTable.START.length);
        assertEquals(11, TimeTable.END.length);
        assertEquals("08:00", TimeTable.START[0]);
        assertEquals("20:50", TimeTable.START[10]);
        assertEquals("21:35", TimeTable.END[10]);
        assertEquals("第1-2节 08:00-09:40", TimeTable.rangeText(1, 2));
    }
}
