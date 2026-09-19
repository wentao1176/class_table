package com.xwt.schedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xwt.schedule.MainActivity;
import com.xwt.schedule.R;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.notify.AlarmScheduler;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.TimeTable;
import com.xwt.schedule.util.WeekUtil;

import java.util.Calendar;

public class ScheduleFragment extends Fragment implements MainActivity.Refreshable {

    private CourseStore store;
    private int displayWeek;
    private ScheduleGridView grid;
    private TextView tvTitle, tvSemester;
    private ImageButton btnPrev, btnNext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_schedule, container, false);
        store = CourseStore.get(requireContext());
        if (displayWeek <= 0) displayWeek = WeekUtil.currentWeek(store);

        grid = root.findViewById(R.id.grid);
        tvTitle = root.findViewById(R.id.tv_week_title);
        tvSemester = root.findViewById(R.id.tv_semester);
        btnPrev = root.findViewById(R.id.btn_prev_week);
        btnNext = root.findViewById(R.id.btn_next_week);

        btnPrev.setOnClickListener(v -> changeWeek(displayWeek - 1));
        btnNext.setOnClickListener(v -> changeWeek(displayWeek + 1));
        root.findViewById(R.id.btn_week_title).setOnClickListener(v ->
                WeekPickerDialog.newInstance(store.getTotalWeeks(),
                        WeekUtil.currentWeek(store), displayWeek, w -> changeWeek(w))
                        .show(getChildFragmentManager(), "week"));

        grid.setOnCourseClickListener(new ScheduleGridView.OnCourseClickListener() {
            @Override
            public void onCourseClick(Course c) {
                showCourseDetail(c);
            }

            @Override
            public void onCoursesClick(java.util.List<Course> courses) {
                CourseDetailDialog.showSlot(requireContext(), store, courses, displayWeek, actions);
            }
        });

        refresh();
        return root;
    }

    private void changeWeek(int w) {
        if (w < 1 || w > store.getTotalWeeks()) {
            Toast.makeText(requireContext(),
                    w < 1 ? "已经是第 1 周了" : "已经是最后一周了", Toast.LENGTH_SHORT).show();
            return;
        }
        displayWeek = w;
        refresh();
    }

    /** 详情弹窗里的「编辑 / 删除」动作。 */
    private final CourseDetailDialog.Actions actions = new CourseDetailDialog.Actions() {
        @Override
        public void onEdit(Course c) {
            CourseDetailDialog.edit(requireContext(), c);
        }

        @Override
        public void onDelete(Course c) {
            confirmDelete(c);
        }
    };

    private void showCourseDetail(Course c) {
        CourseDetailDialog.showDetail(requireContext(), store, c, displayWeek, actions);
    }

    private void confirmDelete(Course c) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除课程")
                .setMessage("确定删除「" + c.name + "」吗？")
                .setPositiveButton("删除", (d, w) -> {
                    store.delete(c.id);
                    AlarmScheduler.rescheduleAsync(requireContext());
                    refresh();
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
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
        if (grid == null) return;
        int cur = WeekUtil.currentWeek(store);
        if (displayWeek < 1) displayWeek = cur;
        grid.setData(displayWeek, store.all());
        tvTitle.setText("第" + displayWeek + "周" + (displayWeek == cur ? "（本周）" : ""));

        // 本周节假日 / 调休概览
        int holidayDays = 0, makeupDays = 0;
        for (int col = 0; col < 7; col++) {
            Calendar d = WeekUtil.dateOf(store, displayWeek, col);
            int st = DayPlan.status(store, d.getTime());
            if (st == DayPlan.STATUS_HOLIDAY) holidayDays++;
            else if (st == DayPlan.STATUS_MAKEUP) makeupDays++;
        }
        StringBuilder sub = new StringBuilder(store.getSemesterName())
                .append(" · 共").append(store.getTotalWeeks()).append("周");
        if (holidayDays > 0) sub.append(" · 放假").append(holidayDays).append("天");
        if (makeupDays > 0) sub.append(" · 调休").append(makeupDays).append("天");
        tvSemester.setText(sub.toString());

        btnPrev.setAlpha(displayWeek <= 1 ? 0.35f : 1f);
        btnNext.setAlpha(displayWeek >= store.getTotalWeeks() ? 0.35f : 1f);
    }
}
