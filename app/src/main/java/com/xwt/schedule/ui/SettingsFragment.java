package com.xwt.schedule.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.xwt.schedule.MainActivity;
import com.xwt.schedule.R;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.notify.AlarmScheduler;
import com.xwt.schedule.notify.NotificationHelper;
import com.xwt.schedule.util.ChinaHoliday;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.TimeTable;
import com.xwt.schedule.util.WeekUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class SettingsFragment extends Fragment implements MainActivity.Refreshable {

    private CourseStore store;
    private TextView tvSemesterName, tvStartDate, tvTotalWeeks, tvSemesterEnd, tvLead, tvPermission;
    private TextView tvHolidaySummary;
    private TextView tvAppVersion, tvUpdateStatus;
    private MaterialSwitch swReminder, swHolidaySkip, swMakeup, swAutoUpdate;

    private final ActivityResultLauncher<String> notifLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                updatePermissionText();
                if (granted) {
                    AlarmScheduler.rescheduleAsync(requireContext());
                    Toast.makeText(requireContext(), "通知权限已开启", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        store = CourseStore.get(requireContext());

        tvSemesterName = root.findViewById(R.id.tv_semester_name);
        tvStartDate = root.findViewById(R.id.tv_start_date);
        tvTotalWeeks = root.findViewById(R.id.tv_total_weeks);
        tvSemesterEnd = root.findViewById(R.id.tv_semester_end);
        tvLead = root.findViewById(R.id.tv_lead);
        tvPermission = root.findViewById(R.id.tv_permission);
        tvHolidaySummary = root.findViewById(R.id.tv_holiday_summary);
        tvAppVersion = root.findViewById(R.id.tv_app_version);
        tvUpdateStatus = root.findViewById(R.id.tv_update_status);
        swReminder = root.findViewById(R.id.sw_reminder);
        swHolidaySkip = root.findViewById(R.id.sw_holiday_skip);
        swMakeup = root.findViewById(R.id.sw_makeup);
        swAutoUpdate = root.findViewById(R.id.sw_auto_update);

        root.findViewById(R.id.row_semester_name).setOnClickListener(v -> editSemesterName());
        root.findViewById(R.id.row_start_date).setOnClickListener(v -> pickStartDate());
        root.findViewById(R.id.row_total_weeks).setOnClickListener(v -> pickTotalWeeks());
        root.findViewById(R.id.row_lead).setOnClickListener(v -> pickLead());
        root.findViewById(R.id.row_permission).setOnClickListener(v -> onPermissionRow());
        root.findViewById(R.id.row_holiday_list).setOnClickListener(v -> showHolidayDialog());
        root.findViewById(R.id.row_check_update).setOnClickListener(v -> checkUpdateManually());
        root.findViewById(R.id.row_test).setOnClickListener(v -> {
            NotificationHelper.showTest(requireContext());
            Toast.makeText(requireContext(), "已发送测试通知（若未出现请检查通知权限）",
                    Toast.LENGTH_LONG).show();
        });
        root.findViewById(R.id.row_restore).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("恢复示例课表")
                        .setMessage("将覆盖当前全部课程，确定继续吗？")
                        .setPositiveButton("恢复", (d, w) -> {
                            store.loadSamples();
                            AlarmScheduler.rescheduleAsync(requireContext());
                            Toast.makeText(requireContext(), "已恢复示例课表", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show());
        root.findViewById(R.id.row_clear).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("清空全部课程")
                        .setMessage("将删除所有课程安排，且无法恢复，确定继续吗？")
                        .setPositiveButton("清空", (d, w) -> {
                            store.clearAll();
                            AlarmScheduler.rescheduleAsync(requireContext());
                            Toast.makeText(requireContext(), "已清空", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show());

        swReminder.setOnCheckedChangeListener((b, checked) -> {
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
            store.setReminderEnabled(checked);
            AlarmScheduler.rescheduleAsync(requireContext());
        });

        swHolidaySkip.setOnCheckedChangeListener((b, checked) -> {
            store.setHolidaySkipEnabled(checked);
            AlarmScheduler.rescheduleAsync(requireContext());
            refresh();
        });

        swMakeup.setOnCheckedChangeListener((b, checked) -> {
            store.setMakeupEnabled(checked);
            AlarmScheduler.rescheduleAsync(requireContext());
            refresh();
        });

        swAutoUpdate.setOnCheckedChangeListener((b, checked) -> store.setAutoUpdateCheckEnabled(checked));

        refresh();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    private void refresh() {
        if (tvSemesterName == null) return;
        tvSemesterName.setText(store.getSemesterName());
        tvStartDate.setText(WeekUtil.format(store.getSemesterStart()));
        tvTotalWeeks.setText(store.getTotalWeeks() + " 周");
        tvSemesterEnd.setText(WeekUtil.format(store.getSemesterEnd()));
        tvLead.setText("提前 " + store.getReminderLeadMinutes() + " 分钟");
        swReminder.setChecked(store.isReminderEnabled());
        swHolidaySkip.setChecked(store.isHolidaySkipEnabled());
        swMakeup.setChecked(store.isMakeupEnabled());
        int holidayDays = 0, makeupDays = 0;
        for (ChinaHoliday.Day d : DayPlan.inSemester(store)) {
            if (d.isHoliday()) holidayDays++;
            else makeupDays++;
        }
        tvHolidaySummary.setText(holidayDays + " 天假期 · " + makeupDays + " 天调休");
        tvAppVersion.setText("v" + com.xwt.schedule.BuildConfig.VERSION_NAME);
        tvUpdateStatus.setText(updateStatusText());
        swAutoUpdate.setChecked(store.isAutoUpdateCheckEnabled());
        updatePermissionText();
    }

    /** 最近一次检查更新的结果，没检查过就显示当前版本。 */
    private String updateStatusText() {
        long t = store.getLastUpdateCheckTime();
        if (t <= 0) return "未检查";
        return "上次检查 " + android.text.format.DateFormat.format("MM-dd HH:mm", t);
    }

    /** 设置页手动检查更新：无论有没有新版本都给用户明确反馈。 */
    private void checkUpdateManually() {
        tvUpdateStatus.setText("检查中…");
        final int current = com.xwt.schedule.BuildConfig.VERSION_CODE;
        com.xwt.schedule.update.UpdateChecker.check(current,
                new com.xwt.schedule.update.UpdateChecker.Callback() {
                    @Override
                    public void onUpdateAvailable(com.xwt.schedule.update.UpdateInfo info) {
                        store.setLastUpdateCheckTime(System.currentTimeMillis());
                        tvUpdateStatus.setText(updateStatusText());
                        UpdateDialogs.showAvailable(requireContext(), info,
                                () -> store.setIgnoredVersionCode(info.versionCode));
                    }

                    @Override
                    public void onUpToDate(int currentVersionCode) {
                        store.setLastUpdateCheckTime(System.currentTimeMillis());
                        tvUpdateStatus.setText(updateStatusText());
                        UpdateDialogs.showUpToDate(requireContext());
                    }

                    @Override
                    public void onFailed(String reason) {
                        tvUpdateStatus.setText(updateStatusText());
                        UpdateDialogs.showCheckFailed(requireContext(), reason);
                    }
                });
    }

    private void updatePermissionText() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            tvPermission.setText("已开启");
            return;
        }
        boolean granted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        tvPermission.setText(granted ? "已开启" : "未开启，点击授权");
        tvPermission.setTextColor(granted ? 0xFF9AA0A8 : 0xFFE56B6B);
    }

    private void onPermissionRow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), "当前系统版本默认允许通知", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean granted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            // 跳到系统通知设置页
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            startActivity(i);
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("需要通知权限")
                    .setMessage("用于在每节课上课前推送提醒。请在系统设置中允许通知。")
                    .setPositiveButton("去设置", (d, w) -> openAppDetailSettings())
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void openAppDetailSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(i);
    }

    private void editSemesterName() {
        final EditText et = new EditText(requireContext());
        et.setText(store.getSemesterName());
        et.setSelection(et.getText().length());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        final android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(et);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("学期名称")
                .setView(container)
                .setPositiveButton("保存", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) {
                        store.setSemesterName(s);
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickStartDate() {
        Calendar c = WeekUtil.atStartOfDay(store.getSemesterStart());
        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month, day, 0, 0, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    store.setSemesterStart(cal.getTime());
                    AlarmScheduler.rescheduleAsync(requireContext());
                    refresh();
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTotalWeeks() {
        String[] items = {"16 周", "18 周", "20 周", "24 周"};
        int[] vals = {16, 18, 20, 24};
        int checked = -1;
        for (int i = 0; i < vals.length; i++) if (vals[i] == store.getTotalWeeks()) checked = i;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("学期总周数")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    store.setTotalWeeks(vals[which]);
                    AlarmScheduler.rescheduleAsync(requireContext());
                    refresh();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickLead() {
        int[] vals = {5, 10, 15, 20, 30, 60};
        String[] items = new String[vals.length];
        int checked = 0;
        for (int i = 0; i < vals.length; i++) {
            items[i] = "提前 " + vals[i] + " 分钟";
            if (vals[i] == store.getReminderLeadMinutes()) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("提前提醒时间")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    store.setReminderLeadMinutes(vals[which]);
                    AlarmScheduler.rescheduleAsync(requireContext());
                    refresh();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 法定节假日与调休 ----------------

    /** 列出本学期全部放假 / 调休安排，并提供入口修改调休日的上课星期。 */
    private void showHolidayDialog() {
        List<ChinaHoliday.Day> specials = DayPlan.inSemester(store);
        List<String> lines = DayPlan.describeSemester(store);

        LinearLayout column = new LinearLayout(requireContext());
        column.setOrientation(LinearLayout.VERTICAL);

        if (lines.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("本学期没有法定节假日或调休安排。");
            empty.setTextSize(14);
            empty.setTextColor(0xFF262A30);
            column.addView(empty);
        } else {
            for (String line : lines) {
                TextView tv = new TextView(requireContext());
                tv.setText("· " + line);
                tv.setTextSize(13.5f);
                tv.setTextColor(0xFF262A30);
                tv.setPadding(0, dp(6), 0, dp(6));
                column.addView(tv);
            }
        }

        TextView source = new TextView(requireContext());
        source.setText("数据来源：国务院办公厅关于本年度部分节假日安排的通知；"
                + "调休日按哪个星期的课表上课由各校自定，可点击下方按钮调整。");
        source.setTextSize(11.5f);
        source.setTextColor(0xFF9AA0A8);
        source.setPadding(0, dp(14), 0, dp(4));
        column.addView(source);

        ScrollView sv = new ScrollView(requireContext());
        sv.addView(column);
        int pad = dp(20);
        sv.setPadding(pad, dp(10), pad, dp(4));

        boolean hasMakeup = false;
        for (ChinaHoliday.Day d : specials) {
            if (d.isMakeup()) {
                hasMakeup = true;
                break;
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("本学期节假日与调休")
                .setView(sv)
                .setPositiveButton("关闭", null);
        if (hasMakeup) {
            builder.setNeutralButton("修改调休安排", (d, w) -> showMakeupDialog());
        }
        builder.show();
    }

    /** 逐个调休日设置「按星期几的课表上课」。 */
    private void showMakeupDialog() {
        final List<ChinaHoliday.Day> makeups = new ArrayList<>();
        for (ChinaHoliday.Day d : DayPlan.inSemester(store)) {
            if (d.isMakeup()) makeups.add(d);
        }
        if (makeups.isEmpty()) {
            Toast.makeText(requireContext(), "本学期没有调休安排", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[makeups.size()];
        for (int i = 0; i < makeups.size(); i++) {
            ChinaHoliday.Day d = makeups.get(i);
            items[i] = DayPlan.shortDate(d.date) + "  " + DayPlan.note(store, WeekUtil.parse(d.date));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("调休上课安排（点击修改）")
                .setItems(items, (dlg, which) -> pickMakeupWeekday(makeups.get(which)))
                .setNeutralButton("全部恢复默认", (d, w) -> {
                    store.clearMakeupOverrides();
                    AlarmScheduler.rescheduleAsync(requireContext());
                    refresh();
                    Toast.makeText(requireContext(), "已恢复官方默认", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickMakeupWeekday(ChinaHoliday.Day day) {
        String[] labels = new String[9];
        labels[0] = "不上课";
        for (int i = 0; i < TimeTable.WEEKDAY_SHORT.length; i++) {
            labels[i + 1] = "按" + TimeTable.WEEKDAY_SHORT[i] + "课表";
        }
        labels[8] = "恢复官方默认";
        int current = store.getMakeupOverride(day.date);
        int checked;
        if (current == CourseStore.MAKEUP_OFF) checked = 0;
        else if (current == CourseStore.MAKEUP_AUTO) checked = 8;
        else checked = current;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(DayPlan.shortDate(day.date) + " 调休上课安排")
                .setSingleChoiceItems(labels, checked, (dlg, which) -> {
                    if (which == 0) store.setMakeupOverride(day.date, CourseStore.MAKEUP_OFF);
                    else if (which == 8) store.setMakeupOverride(day.date, CourseStore.MAKEUP_AUTO);
                    else store.setMakeupOverride(day.date, which);
                    AlarmScheduler.rescheduleAsync(requireContext());
                    refresh();
                    dlg.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
