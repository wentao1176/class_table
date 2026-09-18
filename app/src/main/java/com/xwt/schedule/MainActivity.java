package com.xwt.schedule;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.notify.AlarmScheduler;
import com.xwt.schedule.ui.CourseListFragment;
import com.xwt.schedule.ui.ScheduleFragment;
import com.xwt.schedule.ui.SettingsFragment;
import com.xwt.schedule.ui.TodayFragment;
import com.xwt.schedule.ui.UpdateDialogs;
import com.xwt.schedule.update.UpdateChecker;
import com.xwt.schedule.update.UpdateInfo;

/**
 * 主界面：底部四个 Tab（今日 / 课表 / 课程 / 我的）。
 *
 * <p>四个页面一次性 add 到同一个容器，通过 hide / show 切换；非当前页面限制为 STARTED
 * 状态，避免隐藏页面仍在后台跑刷新逻辑；切换使用 {@code commitNow()} 同步提交，
 * 点击 Tab 后立即生效。页面实例用 tag 保存，配置变更（旋转、深色模式、进程恢复）后
 * 按 tag 复用，不会重复 add 出多份页面导致点击无反应。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 1001;

    private static final String TAG_TODAY = "today";
    private static final String TAG_SCHEDULE = "schedule";
    private static final String TAG_COURSES = "courses";
    private static final String TAG_ME = "me";

    private static final String STATE_TAB = "state_current_tab";

    private TodayFragment todayFragment;
    private ScheduleFragment scheduleFragment;
    private CourseListFragment courseListFragment;
    private SettingsFragment settingsFragment;

    private Fragment current;
    private String currentTag = TAG_SCHEDULE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fm = getSupportFragmentManager();
        todayFragment = (TodayFragment) fm.findFragmentByTag(TAG_TODAY);
        scheduleFragment = (ScheduleFragment) fm.findFragmentByTag(TAG_SCHEDULE);
        courseListFragment = (CourseListFragment) fm.findFragmentByTag(TAG_COURSES);
        settingsFragment = (SettingsFragment) fm.findFragmentByTag(TAG_ME);

        boolean restored = todayFragment != null && scheduleFragment != null
                && courseListFragment != null && settingsFragment != null;

        if (restored) {
            currentTag = savedInstanceState == null
                    ? TAG_SCHEDULE : savedInstanceState.getString(STATE_TAB, TAG_SCHEDULE);
        } else {
            // 首次进入，或状态不完整（FragmentManager 没有恢复出全部页面）
            todayFragment = new TodayFragment();
            scheduleFragment = new ScheduleFragment();
            courseListFragment = new CourseListFragment();
            settingsFragment = new SettingsFragment();
            fm.beginTransaction()
                    .add(R.id.container, scheduleFragment, TAG_SCHEDULE)
                    .add(R.id.container, todayFragment, TAG_TODAY)
                    .hide(todayFragment)
                    .add(R.id.container, courseListFragment, TAG_COURSES)
                    .hide(courseListFragment)
                    .add(R.id.container, settingsFragment, TAG_ME)
                    .hide(settingsFragment)
                    .setMaxLifecycle(todayFragment, Lifecycle.State.STARTED)
                    .setMaxLifecycle(courseListFragment, Lifecycle.State.STARTED)
                    .setMaxLifecycle(settingsFragment, Lifecycle.State.STARTED)
                    .commitNow();
            current = scheduleFragment;
            currentTag = TAG_SCHEDULE;
        }

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            switchTo(fragmentOf(item.getItemId()));
            return true;
        });
        // 重复点击已选中的 Tab 也保证页面可见
        nav.setOnItemReselectedListener(item -> switchTo(fragmentOf(item.getItemId())));

        // 先让选中项与当前页一致（触发一次切换），再兜底同步一次可见性
        nav.setSelectedItemId(idOf(currentTag));
        switchTo(fragmentOf(idOf(currentTag)));

        requestNotificationPermissionIfNeeded();
        autoCheckUpdateIfDue();
    }

    private boolean autoUpdateChecked = false;

    /**
     * 启动时自动检查更新。默认开启，24 小时内最多检查一次；
     * 用户选过"忽略此版本"就不再弹同一个版本；网络失败保持静默。
     */
    private void autoCheckUpdateIfDue() {
        if (autoUpdateChecked) return;
        autoUpdateChecked = true;

        final CourseStore store = CourseStore.get(this);
        if (!store.isAutoUpdateCheckEnabled()) return;

        final long DAY_MS = 24L * 60 * 60 * 1000;
        if (System.currentTimeMillis() - store.getLastUpdateCheckTime() < DAY_MS) return;

        UpdateChecker.check(BuildConfig.VERSION_CODE, new UpdateChecker.Callback() {
            @Override
            public void onUpdateAvailable(UpdateInfo info) {
                store.setLastUpdateCheckTime(System.currentTimeMillis());
                if (info.versionCode == store.getIgnoredVersionCode()) return;
                if (isFinishing() || isDestroyed()) return;
                UpdateDialogs.showAvailable(MainActivity.this, info,
                        () -> store.setIgnoredVersionCode(info.versionCode));
            }

            @Override
            public void onUpToDate(int currentVersionCode) {
                store.setLastUpdateCheckTime(System.currentTimeMillis());
            }

            @Override
            public void onFailed(String reason) {
                // 自动检查失败不打扰用户，设置页里可手动重试
            }
        });
    }

    @Nullable
    private Fragment fragmentOf(int itemId) {
        if (itemId == R.id.nav_today) return todayFragment;
        if (itemId == R.id.nav_courses) return courseListFragment;
        if (itemId == R.id.nav_me) return settingsFragment;
        return scheduleFragment;
    }

    private int idOf(String tag) {
        if (TAG_TODAY.equals(tag)) return R.id.nav_today;
        if (TAG_COURSES.equals(tag)) return R.id.nav_courses;
        if (TAG_ME.equals(tag)) return R.id.nav_me;
        return R.id.nav_schedule;
    }

    private String tagOf(Fragment f) {
        if (f == todayFragment) return TAG_TODAY;
        if (f == courseListFragment) return TAG_COURSES;
        if (f == settingsFragment) return TAG_ME;
        return TAG_SCHEDULE;
    }

    private void switchTo(@Nullable Fragment target) {
        if (target == null || target == current) return;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if (current != null && current.isAdded()) {
            ft.hide(current);
            ft.setMaxLifecycle(current, Lifecycle.State.STARTED);
        } else {
            // 配置变更恢复后 current 为空：把其余页面统一隐藏，保证只显示目标页
            for (Fragment f : new Fragment[]{todayFragment, scheduleFragment,
                    courseListFragment, settingsFragment}) {
                if (f != null && f != target && f.isAdded() && !f.isHidden()) {
                    ft.hide(f);
                    ft.setMaxLifecycle(f, Lifecycle.State.STARTED);
                }
            }
        }
        if (target.isAdded()) {
            ft.show(target);
        } else {
            ft.add(R.id.container, target, tagOf(target));
        }
        ft.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        // 同步提交：点击 Tab 后立即生效，避免异步提交期间的“无响应”观感
        ft.commitNow();
        current = target;
        currentTag = tagOf(target);
        if (target instanceof Refreshable) ((Refreshable) target).onRefresh();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TAB, currentTag);
    }

    /** 数据变化后供子页面刷新。 */
    public interface Refreshable {
        void onRefresh();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            AlarmScheduler.reschedule(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重建提醒放到后台线程，避免整学期闹钟重排阻塞主线程
        AlarmScheduler.rescheduleAsync(this);
        if (current instanceof Refreshable) ((Refreshable) current).onRefresh();
    }
}
