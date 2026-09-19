package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.notify.AlarmScheduler;
import com.xwt.schedule.notify.NotificationHelper;
import com.xwt.schedule.ui.CourseCardView;
import com.xwt.schedule.ui.CourseEditActivity;
import com.xwt.schedule.ui.ScheduleGridView;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.TimeTable;
import com.xwt.schedule.util.WeekUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppRobolectricTest {

    /**
     * <b>本类里的断言一律不许依赖"今天"。</b>
     *
     * <p>踩过的坑：{@code todayTabShowsFridayWeekOneCourses} 写死了「第 1 周周五有 2 节课」，
     * 2026-09-18 那天是绿的，第二天到了周六就自己变成 0 挂掉了 —— 测试跟真实日期耦合，
     * 等于给自己埋了一颗定时炸弹。
     *
     * <p>试过冻结时钟：Robolectric 4.10 默认的 PAUSED looper 模式下，
     * {@code SystemClock.setCurrentTimeMillis()} <b>返回 true 但根本不改时钟</b>
     * （实测 {@code new Date()} 前后完全一样），{@code ShadowSystemClock} 与
     * {@code ShadowSystem} 也没有可用的 setter。所以不能指望冻结时钟。
     *
     * <p>正确做法：要么用 {@link WeekUtil#weekOfDate} 这类纯函数按显式日期验证，
     * 要么按"今天实际是什么"来造数据 —— 见
     * {@link #todayTabListsTheCoursesOfTheEffectiveDay()}。
     */
    private Context ctx;
    private CourseStore store;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        store = CourseStore.get(ctx);
        store.clearAll();
        store.loadSamples();
        // 每个用例从「默认节假日设置」开始
        store.setHolidaySkipEnabled(true);
        store.setMakeupEnabled(true);
        store.clearMakeupOverrides();
        // 关掉启动时的自动更新检查：MainActivity 一创建就会去联网拉 update.json，
        // 而网络一慢整套测试就跟着慢（实测每个用例卡几分钟）。
        // 单测不该依赖网络，这条链路由 UpdateLogicTest / UpdateIntegrityTest 单独覆盖。
        store.setAutoUpdateCheckEnabled(false);
    }

    @Test
    public void samplesLoadedAndWeekOneDateMatchesReference() {
        assertEquals(15, store.all().size());
        // 示例课表不含任何周六 / 周日课程：周末本来就没课，
        // 截图中周末出现的课是调休当周的重复项，现由节假日引擎自动推导。
        for (Course c : store.all()) {
            assertTrue("示例课表不应包含周末课程：" + c.name,
                    c.day != Calendar.SATURDAY && c.day != Calendar.SUNDAY);
        }
        // 默认学期 16 周，示例课程周次应与之对齐
        assertEquals(CourseStore.DEFAULT_TOTAL_WEEKS, store.getTotalWeeks());
        for (Course c : store.all()) {
            assertEquals(CourseStore.DEFAULT_TOTAL_WEEKS, c.weekEnd);
        }
        // 默认开学日 2026-09-13（周日），第 1 周周五应为 2026-09-18
        Calendar fri = WeekUtil.dateOf(store, 1, TimeTable.WEEKDAY_SHORT.length - 2); // 索引5=周五
        assertEquals(2026, fri.get(Calendar.YEAR));
        assertEquals(Calendar.SEPTEMBER, fri.get(Calendar.MONTH));
        assertEquals(18, fri.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.FRIDAY, fri.get(Calendar.DAY_OF_WEEK));
        // 日期→周次用显式日期验证（weekOfDate 是纯函数）。
        // 这里原来写的是 WeekUtil.currentWeek(store)，那是在断言"今天恰好是第 1 周"，
        // 学期一开始往前走就会挂 —— 日期相关的断言一律不许依赖"今天"。
        assertEquals(1, WeekUtil.weekOfDate(store, WeekUtil.parse("2026-09-18")));
        assertEquals(1, WeekUtil.weekOfDate(store, WeekUtil.parse("2026-09-13")));
        assertEquals(2, WeekUtil.weekOfDate(store, WeekUtil.parse("2026-09-20")));
        assertEquals(16, WeekUtil.weekOfDate(store, WeekUtil.parse("2027-01-02")));
        assertEquals("学期结束后应夹在最后一周", 16,
                WeekUtil.weekOfDate(store, WeekUtil.parse("2027-06-01")));
        assertEquals("开学前应夹在第 1 周", 1,
                WeekUtil.weekOfDate(store, WeekUtil.parse("2026-01-01")));
    }

    @Test
    public void mainActivityScheduleRendersAllCourseCards() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ScheduleGridView grid = activity.findViewById(R.id.grid);
                assertNotNull(grid);
                // 所有课程（含本周不上、置灰的课）都要渲染；示例课表 15 门
                assertEquals(15, grid.getChildCount());
            });
        }
    }

    @Test
    public void weekendsNeverShowCourses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ScheduleGridView grid = activity.findViewById(R.id.grid);
                // 第 1 周（2026-09-13 ~ 09-19）既没有节假日也没有调休：
                // 15 门课全部落在周一~周五，周六 / 周日两列必须一张卡片都没有。
                assertEquals(15, grid.getChildCount());
                java.util.Set<Integer> days = new java.util.HashSet<>();
                for (int i = 0; i < grid.getChildCount(); i++) {
                    CourseCardView card = (CourseCardView) grid.getChildAt(i);
                    for (Course c : card.getCourses()) days.add(c.day);
                }
                assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
                                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                                Calendar.THURSDAY, Calendar.FRIDAY)),
                        days);
            });
        }
    }

    /**
     * 今日页只列「今天生效的星期」的课。
     *
     * <p>刻意不假设今天是周几 —— 旧版本写死「第 1 周周五 2 节课」，2026-09-19 一到周六
     * 就自己挂了。这里改成按 {@link DayPlan#effectiveDayOfWeek} 的实际结果造数据：
     * 在"今天生效的星期"上放两门课，再在别的星期放一门做干扰，看页面有没有真的按星期过滤。
     */
    @Test
    public void todayTabListsTheCoursesOfTheEffectiveDay() {
        int effective = DayPlan.effectiveDayOfWeek(store, new Date());

        store.clearAll();
        if (effective != 0) {
            newCourse("今天的课A", "中101", effective, 1, 2, 0);
            newCourse("今天的课B", "中102", effective, 5, 2, 1);
            // 干扰项：确保页面是按星期过滤，而不是把课表全列出来
            int other = effective == Calendar.MONDAY ? Calendar.TUESDAY : Calendar.MONDAY;
            newCourse("别的天的课", "中103", other, 1, 2, 2);
        }
        final int expected = effective == 0 ? 0 : 2;

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                nav.setSelectedItemId(R.id.nav_today);
                RecyclerView rv = activity.findViewById(R.id.rv_today);
                assertNotNull(rv.getAdapter());
                assertEquals("今日页应只列今天生效星期的课（今天生效星期=" + effective + "）",
                        expected, rv.getAdapter().getItemCount());
                TextView name = activity.findViewById(R.id.tv_next_name);
                assertNotNull(name.getText());
            });
        }
    }

    @Test
    public void weekTwoShowsEvenWeekCourses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                gotoWeekOne(activity);
                activity.findViewById(R.id.btn_next_week).performClick();
                ScheduleGridView grid = activity.findViewById(R.id.grid);
                // 第 2 周 15 门课全部处于上课周（含放假当周被隐藏的课）
                long onCount = store.all().stream().filter(c -> c.occursInWeek(2)).count();
                assertEquals(15, onCount);
                assertTrue(grid.getChildCount() > 0);
            });
        }
    }

    @Test
    public void addCourseFlowSavesAndReschedules() {
        int before = store.all().size();
        try (ActivityScenario<CourseEditActivity> scenario =
                     ActivityScenario.launch(CourseEditActivity.class)) {
            scenario.onActivity(activity -> {
                ((TextInputEditText) activity.findViewById(R.id.et_name)).setText("测试新增课");
                ((TextInputEditText) activity.findViewById(R.id.et_location)).setText("中999");
                // 默认已选 周一/第1节/2节/每周/第1-20周/颜色0，直接保存
                activity.findViewById(R.id.btn_save).performClick();
            });
        }
        assertEquals(before + 1, store.all().size());
        Course added = store.all().get(store.all().size() - 1);
        assertEquals("测试新增课", added.name);
        assertEquals("中999", added.location);
        // 重排闹钟不应崩溃
        AlarmScheduler.reschedule(ctx);
    }

    @Test
    public void emptyNameIsRejected() {
        int before = store.all().size();
        try (ActivityScenario<CourseEditActivity> scenario =
                     ActivityScenario.launch(CourseEditActivity.class)) {
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.btn_save).performClick());
        }
        assertEquals(before, store.all().size());
    }

    @Test
    public void weekTwoAppliesHolidayAndMakeupRules() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                gotoWeekOne(activity);
                activity.findViewById(R.id.btn_next_week).performClick();
                ScheduleGridView grid = activity.findViewById(R.id.grid);
                // 第 2 周 = 2026-09-20 ~ 09-26：
                //   9/20（周日）调休上课 → 整列改显示周二课表（跨文化交际、操作系统）2 张
                //   9/25、9/26 中秋放假 → 整列不排课
                // 其余 4 天（周一 2、周二 2、周三 3、周四 4）共 11 门次课，合计 13 张卡片
                assertEquals(13, grid.getChildCount());
                // 课程本身仍然全部存在，只是放假当周不显示
                long onCount = store.all().stream().filter(c -> c.occursInWeek(2)).count();
                assertEquals(15, onCount);
            });
        }
    }

    @Test
    public void meTabOpensSettingsPage() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                nav.setSelectedItemId(R.id.nav_me);

                Fragment me = activity.getSupportFragmentManager().findFragmentByTag("me");
                assertNotNull("「我的」页应已加入", me);
                assertNotNull(me.getView());
                assertEquals(View.VISIBLE, me.getView().getVisibility());
                assertTrue("「我的」页应可见", me.getView().isShown());

                Fragment schedule = activity.getSupportFragmentManager().findFragmentByTag("schedule");
                assertNotNull(schedule);
                assertEquals(View.GONE, schedule.getView().getVisibility());

                // 设置项已填充
                TextView name = activity.findViewById(R.id.tv_semester_name);
                assertTrue(name.getText().length() > 0);

                // 回归防护：设置页首次可见时必须能完成 measure/layout。
                // 曾经因为 MaterialSwitch 没有 textOn/textOff，SwitchCompat.makeLayout()
                // 拿到 null 抛 NPE，导致点「我的」时页面绘制失败、看起来“没有反应”。
                View meView = me.getView();
                meView.measure(
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST));
                assertTrue(meView.getMeasuredHeight() > 0);
                meView.layout(0, 0, meView.getMeasuredWidth(), meView.getMeasuredHeight());
            });
        }
    }

    @Test
    public void everyBottomTabShowsItsPage() {
        int[] ids = {R.id.nav_today, R.id.nav_schedule, R.id.nav_courses, R.id.nav_me};
        String[] tags = {"today", "schedule", "courses", "me"};
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                for (int i = 0; i < ids.length; i++) {
                    nav.setSelectedItemId(ids[i]);
                    for (int j = 0; j < tags.length; j++) {
                        Fragment f = activity.getSupportFragmentManager().findFragmentByTag(tags[j]);
                        assertNotNull(f);
                        assertNotNull(f.getView());
                        int expected = i == j ? View.VISIBLE : View.GONE;
                        assertEquals("切换 " + tags[i] + " 后 " + tags[j] + " 的可见性",
                                expected, f.getView().getVisibility());
                    }
                }
            });
        }
    }

    @Test
    public void holidaySettingsAffectEffectiveWeekday() {
        Date nationalDay = WeekUtil.parse("2026-10-01");
        Date makeupSunday = WeekUtil.parse("2026-09-20");
        Date normalDay = WeekUtil.parse("2026-09-21");

        // 默认：国庆放假、9/20 按周二课表
        assertEquals(DayPlan.STATUS_HOLIDAY, DayPlan.status(store, nationalDay));
        assertEquals(0, DayPlan.effectiveDayOfWeek(store, nationalDay));
        assertEquals(DayPlan.STATUS_MAKEUP, DayPlan.status(store, makeupSunday));
        assertEquals(Calendar.TUESDAY, DayPlan.effectiveDayOfWeek(store, makeupSunday));
        assertEquals(DayPlan.STATUS_NORMAL, DayPlan.status(store, normalDay));
        assertEquals(Calendar.MONDAY, DayPlan.effectiveDayOfWeek(store, normalDay));

        // 关掉「节假日自动停课」后国庆当天照常按周四上课
        store.setHolidaySkipEnabled(false);
        assertEquals(DayPlan.STATUS_NORMAL, DayPlan.status(store, nationalDay));
        assertEquals(Calendar.THURSDAY, DayPlan.effectiveDayOfWeek(store, nationalDay));
        store.setHolidaySkipEnabled(true);

        // 关掉「调休上班日按课表上课」后不再换星期，9/20 就按周日自己的课表上课
        store.setMakeupEnabled(false);
        assertEquals(DayPlan.STATUS_NORMAL, DayPlan.status(store, makeupSunday));
        assertEquals(Calendar.SUNDAY, DayPlan.effectiveDayOfWeek(store, makeupSunday));
        store.setMakeupEnabled(true);
        assertEquals(Calendar.TUESDAY, DayPlan.effectiveDayOfWeek(store, makeupSunday));

        // 用户自定义：9/20 改为按周五课表，10/10 改为不上课
        store.setMakeupOverride("2026-09-20", Calendar.FRIDAY);
        assertEquals(Calendar.FRIDAY, DayPlan.effectiveDayOfWeek(store, makeupSunday));
        store.setMakeupOverride("2026-10-10", CourseStore.MAKEUP_OFF);
        assertEquals(0, DayPlan.effectiveDayOfWeek(store, WeekUtil.parse("2026-10-10")));
        // 恢复默认
        store.clearMakeupOverrides();
        assertEquals(Calendar.TUESDAY, DayPlan.effectiveDayOfWeek(store, makeupSunday));
    }

    @Test
    public void semesterHolidaySummaryIsReadable() {
        List<String> lines = DayPlan.describeSemester(store);
        assertFalse(lines.isEmpty());
        boolean hasNationalDay = false, hasMidAutumn = false, hasMakeup = false;
        for (String line : lines) {
            if (line.startsWith("国庆节") && line.contains("放假 7 天")) hasNationalDay = true;
            if (line.startsWith("中秋节") && line.contains("放假 3 天")) hasMidAutumn = true;
            if (line.contains("调休上课")) hasMakeup = true;
        }
        assertTrue(lines.toString(), hasNationalDay);
        assertTrue(lines.toString(), hasMidAutumn);
        assertTrue(lines.toString(), hasMakeup);
    }

    @Test
    public void reminderNotificationPosts() {
        NotificationHelper.showTest(ctx);
        ShadowNotificationManager shadow =
                shadowOf((android.app.NotificationManager)
                        ctx.getSystemService(Context.NOTIFICATION_SERVICE));
        assertEquals(1, shadow.getAllNotifications().size());
        Notification n = shadow.getAllNotifications().get(0);
        assertEquals("class_reminder", n.getChannelId());
        assertNotNull(n.contentIntent);
    }

    /**
     * 把课表页确定性地退回到第 1 周。
     *
     * <p>页面初始显示的是 {@code WeekUtil.currentWeek()}，也就是"今天在第几周" ——
     * 直接点一次「下一周」并不能保证到第 2 周。连点「上一周」会停在第 1 周
     * （再往前会提示"已经是第 1 周了"），于是下一步一定是第 2 周，与今天无关。
     */
    private static void gotoWeekOne(MainActivity activity) {
        View prev = activity.findViewById(R.id.btn_prev_week);
        for (int i = 0; i < 20; i++) prev.performClick();
    }

    // ---------------- 同一时段多门课 ----------------

    private Course newCourse(String name, String room, int day, int start, int count, int color) {
        Course c = new Course();
        c.name = name;
        c.location = room;
        c.day = day;
        c.startSection = start;
        c.sectionCount = count;
        c.weekType = Course.TYPE_ALL;
        c.weekStart = 1;
        c.weekEnd = store.getTotalWeeks();
        c.color = color;
        store.add(c);
        return c;
    }

    @Test
    public void overlappingCoursesShareOneCardInsteadOfBeingSplit() {
        store.clearAll();
        newCourse("数据库系统", "复201", Calendar.MONDAY, 3, 2, 0);
        newCourse("编译原理", "复202", Calendar.MONDAY, 3, 2, 1);   // 与上一门完全重叠
        newCourse("大学英语", "中101", Calendar.MONDAY, 7, 2, 2);   // 不重叠

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ScheduleGridView grid = activity.findViewById(R.id.grid);
                // 重叠的两门合成一张卡 + 单独一门 = 2 张（旧实现会切成 3 张窄卡）
                assertEquals(2, grid.getChildCount());

                CourseCardView stacked = null, single = null;
                for (int i = 0; i < grid.getChildCount(); i++) {
                    CourseCardView card = (CourseCardView) grid.getChildAt(i);
                    if (card.getStackCount() > 1) stacked = card;
                    else single = card;
                }
                assertNotNull("重叠的时段应该出现一张带折角的卡片", stacked);
                assertNotNull(single);
                assertEquals(2, stacked.getStackCount());
                assertEquals(2, stacked.getCourses().size());
                assertEquals(1, single.getStackCount());

                // 关键：合并后的卡片要占满整列，不能再被切成一半
                assertEquals("重叠卡片应与普通卡片同宽（占满整列）",
                        single.getWidth(), stacked.getWidth());
                assertTrue("卡片宽度应大于 0", stacked.getWidth() > 0);

                // 折角必须真的落在右上角，并且没有戳出卡片
                float[] tri = stacked.foldTriangle(stacked.getWidth(), stacked.getHeight());
                assertEquals("折角的直角顶点应贴右上角", stacked.getWidth(), tri[2], 0.01f);
                assertEquals(0f, tri[3], 0.01f);
                assertEquals("折角应是等边直角三角", tri[2] - tri[0], tri[5] - tri[1], 0.01f);
                for (float v : tri) {
                    assertTrue("折角顶点跑出卡片了: " + v, v >= 0);
                }
                assertTrue(tri[4] <= stacked.getWidth() && tri[5] <= stacked.getHeight());

                // 折角会占掉右上角，正文必须让开，否则课程名会被压在折角下面
                assertTrue("多门课的卡片没有给折角留出顶部内边距：paddingTop="
                                + stacked.getPaddingTop() + " foldSize=" + stacked.foldSize(),
                        stacked.getPaddingTop() >= stacked.foldSize());
                assertTrue("单门课的卡片不该白留内边距",
                        single.getPaddingTop() < single.foldSize());

                // 点带折角的卡片 -> 把整组课程交出去；点普通卡片 -> 单门课
                final List<Course>[] slot = new List[]{null};
                final Course[] one = new Course[]{null};
                grid.setOnCourseClickListener(new ScheduleGridView.OnCourseClickListener() {
                    @Override
                    public void onCourseClick(Course c) {
                        one[0] = c;
                    }

                    @Override
                    public void onCoursesClick(List<Course> courses) {
                        slot[0] = courses;
                    }
                });

                stacked.performClick();
                assertNotNull("点重叠卡片应触发 onCoursesClick", slot[0]);
                assertEquals(2, slot[0].size());
                assertNull("重叠卡片不该走单门课回调", one[0]);

                single.performClick();
                assertNotNull(one[0]);
                assertEquals("大学英语", one[0].name);
            });
        }
    }
}
