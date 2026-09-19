package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xwt.schedule.model.Course;
import com.xwt.schedule.util.FoldGeometry;
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

    // ---------------- 折角几何 ----------------
    // 折角是 CourseCardView 画的，但形状只是三个坐标点 —— 抽到 FoldGeometry 之后
    // 就能用普通 JVM 单测覆盖，不用起 Robolectric。

    @Test
    public void foldTriangleHugsTheTopRightCorner() {
        float[] t = FoldGeometry.triangle(140, 220, 14);
        assertEquals("直角顶点贴右上角 x", 140f, t[2], 0.001f);
        assertEquals("直角顶点贴右上角 y", 0f, t[3], 0.001f);
        assertTrue("应向左展开", t[0] < t[2]);
        assertTrue("应向下展开", t[5] > t[3]);
        assertEquals("两条直角边等长", t[2] - t[0], t[5] - t[1], 0.001f);
        assertTrue("应完整落在卡片内", FoldGeometry.fitsInside(140, 220, 14));
    }

    @Test
    public void foldShrinksOnSmallCardsInsteadOfOverflowing() {
        // 只有 12×12 的卡片：14 的折角必须缩到 6，否则会戳出去
        float s = FoldGeometry.sizeFor(12, 12, 14);
        assertEquals(6f, s, 0.001f);
        assertTrue(FoldGeometry.fitsInside(12, 12, 14));
        assertTrue(FoldGeometry.fitsInside(40, 20, 14));   // 很扁的卡片
        assertTrue(FoldGeometry.fitsInside(20, 40, 14));   // 很窄的卡片
    }

    @Test
    public void foldSizeIsCappedAtHalfTheCard() {
        // 卡片再大，折角也不会超过名义边长
        assertEquals(14f, FoldGeometry.sizeFor(200, 300, 14), 0.001f);
        // 卡片再小，也不会变成 0 或负数
        assertTrue(FoldGeometry.sizeFor(1, 1, 14) > 0);
    }

    @Test
    public void foldTriangleIsEmptyForInvalidCardSize() {
        for (float[] t : new float[][]{
                FoldGeometry.triangle(0, 100, 14),
                FoldGeometry.triangle(100, 0, 14),
                FoldGeometry.triangle(-5, 100, 14)}) {
            for (float v : t) assertEquals(0f, v, 0.001f);
        }
    }

    @Test
    public void foldLabelSitsInsideTheTriangle() {
        float[] c = FoldGeometry.labelCenter(140, 220, 14);
        // 三角内部满足 y < x - (w - s)，即 y - x + (w - s) < 0
        float w = 140, s = 14;
        assertTrue("门数标签跑出折角了",
                c[1] - c[0] + (w - s) < 0);
        assertTrue(c[0] > w - s && c[0] < w);
        assertTrue(c[1] > 0 && c[1] < s);
    }
}
