package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        // 2026-09-18 处于第 1 周
        assertEquals(1, WeekUtil.currentWeek(store));
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
                    Course c = (Course) grid.getChildAt(i).getTag();
                    assertNotNull(c);
                    days.add(c.day);
                }
                assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
                                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                                Calendar.THURSDAY, Calendar.FRIDAY)),
                        days);
            });
        }
    }

    @Test
    public void todayTabShowsFridayWeekOneCourses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                nav.setSelectedItemId(R.id.nav_today);
                RecyclerView rv = activity.findViewById(R.id.rv_today);
                assertNotNull(rv.getAdapter());
                // 第1周周五实际上课：习概（5-6节）、组织行为学（7-8节），双周课本周不上
                assertEquals(2, rv.getAdapter().getItemCount());
                TextView name = activity.findViewById(R.id.tv_next_name);
                assertNotNull(name.getText());
                assertTrue(name.getText().length() > 0);
            });
        }
    }

    @Test
    public void weekTwoShowsEvenWeekCourses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
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
}
