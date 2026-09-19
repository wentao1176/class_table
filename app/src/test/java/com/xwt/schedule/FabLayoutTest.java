package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.view.View;

import androidx.test.core.app.ActivityScenario;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 右下角「加课」按钮的可见性。
 *
 * <p>回归背景：这个按钮原来放在 Fragment 的布局里，写成
 * {@code android:layout_margin="20dp"} + {@code android:layout_marginBottom="92dp"}。
 * Android 的 {@code MarginLayoutParams} <b>只在没有 {@code layout_margin} 时才去读单边
 * margin</b>，所以简写把 92dp 整个吃掉了，实际只剩 20dp ——
 * 实测按钮矩形 2264~2320，而底部导航栏从 2284 开始，按钮被盖住 64%。
 *
 * <p>修法是把按钮移到 Activity 层的 CoordinatorLayout，用
 * {@code layout_insetEdge="bottom"} + {@code layout_dodgeInsetEdges="bottom"}
 * 让布局系统自己把按钮顶到导航栏上方，不再依赖写死的数字。
 * 下面这几条断言就是防止它再被改回去。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class FabLayoutTest {

    private static final int W = 1080;
    private static final int H = 2340;

    /** 按固定尺寸测量 + 布局整棵视图树，让几何信息变成可断言的确定值。 */
    private static void layout(MainActivity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
    }

    private static void selectTab(MainActivity activity, int itemId) {
        BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(itemId);
        layout(activity);
    }

    @Test
    public void addButtonIsFullyOnScreenAndClearOfTheBottomNav() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                selectTab(activity, R.id.nav_schedule);

                FloatingActionButton fab = activity.findViewById(R.id.fab_add);
                BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                assertNotNull(fab);
                assertNotNull(nav);

                assertEquals("「课表」页应显示加课按钮", View.VISIBLE, fab.getVisibility());
                assertTrue("按钮没有被测量出尺寸", fab.getWidth() > 0 && fab.getHeight() > 0);

                // 完整落在屏幕内（不能跑出右边界或下边界）
                assertTrue("按钮超出屏幕: " + rect(fab),
                        fab.getLeft() >= 0 && fab.getTop() >= 0
                                && fab.getRight() <= W && fab.getBottom() <= H);

                // 关键断言：不能被底部导航栏压住
                assertTrue("加课按钮被底部导航栏遮住 —— fab=" + rect(fab)
                                + " nav.top=" + nav.getTop(),
                        fab.getBottom() <= nav.getTop());
            });
        }
    }

    @Test
    public void addButtonIsNotWhiteOnWhite() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FloatingActionButton fab = activity.findViewById(R.id.fab_add);
                assertNotNull(fab);
                assertNotNull("按钮底色未设置", fab.getBackgroundTintList());
                assertNotNull("按钮图标色未设置", fab.getImageTintList());

                int bg = fab.getBackgroundTintList().getDefaultColor();
                int fg = fab.getImageTintList().getDefaultColor();
                assertNotEquals("底色与图标同色，按钮会看不见", bg, fg);
                assertEquals("按钮底色应为主题蓝", Color.parseColor("#4A8FE7"), bg);
            });
        }
    }

    @Test
    public void addButtonOnlyAppearsOnTabsThatCanAddCourses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FloatingActionButton fab = activity.findViewById(R.id.fab_add);

                selectTab(activity, R.id.nav_schedule);
                assertEquals(View.VISIBLE, fab.getVisibility());

                selectTab(activity, R.id.nav_courses);
                assertEquals(View.VISIBLE, fab.getVisibility());

                selectTab(activity, R.id.nav_today);
                assertEquals("「今日」页不该出现加课按钮", View.GONE, fab.getVisibility());

                selectTab(activity, R.id.nav_me);
                assertEquals("「我的」页不该出现加课按钮", View.GONE, fab.getVisibility());

                // 切回来要恢复
                selectTab(activity, R.id.nav_schedule);
                assertEquals(View.VISIBLE, fab.getVisibility());
            });
        }
    }

    private static String rect(View v) {
        return "[" + v.getLeft() + "," + v.getTop() + " - " + v.getRight() + "," + v.getBottom() + "]";
    }
}
