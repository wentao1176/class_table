package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Calendar;

/**
 * 已保存的课表必须活过升级 / 重启。
 *
 * <p>「升级会不会丢数据」这件事的答案分两层：
 * <ol>
 *   <li>正常的覆盖安装（同包名 + 同签名）<b>不会</b>碰 SharedPreferences，
 *       数据本来就在；而且示例课表只在第一次安装时写入一次，
 *       老用户升级后不会被示例数据盖掉。{@link #samplesAreSeededOnlyOnce()} 与
 *       {@link #coursesSurviveARestart()} 守的就是这一层。</li>
 *   <li>万一数据被写坏，也不能让用户直接面对一张空课表 ——
 *       自动回退到上一版备份，并且绝不用空列表去覆盖原始数据。</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DataPersistenceTest {

    private static final String PREF = "schedule_data";
    private static final String KEY_COURSES = "courses_json";
    private static final String KEY_BACKUP = "courses_json_backup";
    private static final String KEY_QUARANTINE = "courses_json_corrupt";

    private Context ctx;
    private SharedPreferences sp;

    @Before
    public void setUp() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        restartApp();
    }

    /** 丢掉单例、重新读一次存储 —— 等价于杀进程后重新打开 App / 装完新版本首次启动。 */
    private void restartApp() throws Exception {
        Field f = CourseStore.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static Course course(String name) {
        Course c = new Course();
        c.name = name;
        c.location = "复301";
        c.day = Calendar.MONDAY;
        c.startSection = 1;
        c.sectionCount = 2;
        c.weekType = Course.TYPE_ALL;
        c.weekStart = 1;
        c.weekEnd = CourseStore.DEFAULT_TOTAL_WEEKS;
        return c;
    }

    private void assertNames(CourseStore store, String... expected) {
        assertEquals(expected.length, store.all().size());
        for (String n : expected) {
            boolean found = false;
            for (Course c : store.all()) if (n.equals(c.name)) found = true;
            assertTrue("重启后丢了课程：" + n + "，实际=" + names(store), found);
        }
    }

    private static String names(CourseStore store) {
        StringBuilder sb = new StringBuilder("[");
        for (Course c : store.all()) sb.append(c.name).append(" ");
        return sb.append("]").toString();
    }

    // ---------------- 正常路径 ----------------

    @Test
    public void coursesSurviveARestart() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.clearAll();
        store.add(course("数据结构"));
        store.add(course("计算机网络"));
        assertEquals(2, store.all().size());

        restartApp();
        assertNames(CourseStore.get(ctx), "数据结构", "计算机网络");
    }

    @Test
    public void editsAndDeletesSurviveARestart() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.clearAll();
        Course a = course("编译原理");
        store.add(a);
        Course b = course("离散数学");
        store.add(b);

        b.location = "中208";
        b.day = Calendar.FRIDAY;
        store.update(b);
        store.delete(a.id);

        restartApp();
        CourseStore after = CourseStore.get(ctx);
        assertEquals(1, after.all().size());
        Course loaded = after.all().get(0);
        assertEquals("离散数学", loaded.name);
        assertEquals("中208", loaded.location);
        assertEquals(Calendar.FRIDAY, loaded.day);
    }

    /** 老用户升级后不能被示例课表覆盖 —— 示例只在第一次安装时写一次。 */
    @Test
    public void samplesAreSeededOnlyOnce() throws Exception {
        CourseStore first = CourseStore.get(ctx);
        assertTrue("首次安装应写入示例课表", first.all().size() > 0);

        // 用户改成自己的课表
        first.clearAll();
        first.add(course("我的课"));

        restartApp();
        assertNames(CourseStore.get(ctx), "我的课");

        restartApp();
        assertNames(CourseStore.get(ctx), "我的课");
    }

    @Test
    public void settingsSurviveARestart() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.setTotalWeeks(18);
        store.setHolidaySkipEnabled(false);
        store.setMakeupEnabled(false);
        store.setSemesterName("2026-2027春");

        restartApp();
        CourseStore after = CourseStore.get(ctx);
        assertEquals(18, after.getTotalWeeks());
        assertFalse(after.isHolidaySkipEnabled());
        assertFalse(after.isMakeupEnabled());
        assertEquals("2026-2027春", after.getSemesterName());
    }

    // ---------------- 数据被写坏时的兜底 ----------------

    @Test
    public void corruptDataFallsBackToThePreviousBackup() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.clearAll();
        store.add(course("备份里的课"));
        // 再写一次，让上一版落进备份槽
        store.add(course("后来的课"));

        assertNotNull("应留有备份", sp.getString(KEY_BACKUP, null));

        // 主数据被写坏
        sp.edit().putString(KEY_COURSES, "{ 这不是合法 JSON").commit();

        restartApp();
        CourseStore recovered = CourseStore.get(ctx);
        assertTrue("主数据损坏时应从备份恢复，而不是给用户一张空课表",
                recovered.all().size() > 0);
        assertNames(recovered, "备份里的课");
    }

    @Test
    public void corruptDataIsNeverOverwrittenWithAnEmptyList() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.clearAll();
        store.add(course("唯一的课"));

        // 主数据 + 备份都坏掉
        sp.edit()
                .putString(KEY_COURSES, "坏掉的字节@@@")
                .putString(KEY_BACKUP, "也坏掉了###")
                .commit();

        restartApp();
        CourseStore after = CourseStore.get(ctx);
        assertEquals("两份都读不出来时只能先空着", 0, after.all().size());
        assertTrue("两份都读不出来时不该把示例课表塞进来",
                sp.getBoolean("initialized", false));

        // 此时用户新增一门课：原始字节必须被留档，不能被悄悄冲掉
        after.add(course("新加的课"));
        assertEquals("原始坏数据应被留档以便人工找回",
                "坏掉的字节@@@", sp.getString(KEY_QUARANTINE, null));
    }

    @Test
    public void clearingAllDoesNotComeBackFromTheBackup() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.clearAll();
        store.add(course("会被删掉的课"));

        // 用户明确清空：备份也要一起丢，否则下次启动会"诈尸"
        store.clearAll();
        assertEquals(0, store.all().size());
        assertNull("清空后不该还留着备份", sp.getString(KEY_BACKUP, null));

        restartApp();
        assertEquals("清空后重启不该又冒出课程", 0, CourseStore.get(ctx).all().size());
    }

    @Test
    public void malformedJsonIsRejectedWholesaleAndQuarantined() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.clearAll();
        store.add(course("好课"));

        // 截断的 JSON（写到一半断电、写入被中断都会长这样）。
        // 关键是**不能**把能解析出来的那一门当成完整课表写回去 ——
        // 那等于悄悄删掉了没解析出来的部分，比报错更难查。
        String corrupt = "[{\"id\":1,\"name\":\"好课\",\"day\":2,\"start\":1,\"count\":2},{\"id\":2,";
        sp.edit().putString(KEY_COURSES, corrupt).remove(KEY_BACKUP).commit();

        restartApp();
        CourseStore after = CourseStore.get(ctx);
        assertEquals("解析失败又无备份时只能先空着", 0, after.all().size());

        // 用户接着加课 -> 触发落盘 -> 原始坏字节必须被原样留档
        after.add(course("新加的课"));
        String quarantined = sp.getString(KEY_QUARANTINE, null);
        assertNotNull("坏数据必须留档，否则用户那唯一一份数据就彻底没了", quarantined);
        assertTrue("留档应是原始字节", quarantined.contains("好课"));
        assertEquals(1, after.all().size());
    }

    /**
     * 字段级别的容错：某个字段类型不对时 {@code optInt} 会退回默认值而不是整份失败。
     * 这是刻意的 —— 宁可让一门课的某个字段用默认值，也不要把整张课表丢掉。
     */
    @Test
    public void unknownFieldValuesFallBackToDefaultsInsteadOfLosingEverything() throws Exception {
        CourseStore store = CourseStore.get(ctx);
        store.clearAll();
        store.add(course("好课"));

        sp.edit().putString(KEY_COURSES,
                "[{\"id\":1,\"name\":\"字段怪但能用\",\"day\":\"不是数字\",\"count\":null}]").commit();

        restartApp();
        CourseStore after = CourseStore.get(ctx);
        assertEquals("整份数据仍然可用", 1, after.all().size());
        assertEquals("字段怪但能用", after.all().get(0).name);
    }
}
