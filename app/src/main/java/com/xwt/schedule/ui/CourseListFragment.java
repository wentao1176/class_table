package com.xwt.schedule.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.xwt.schedule.MainActivity;
import com.xwt.schedule.R;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.notify.AlarmScheduler;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.Palette;
import com.xwt.schedule.util.TimeTable;

import java.util.List;

public class CourseListFragment extends Fragment implements MainActivity.Refreshable {

    private CourseStore store;
    private RecyclerView rv;
    private TextView tvEmpty, tvCount, tvHolidayTip;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_courses, container, false);
        store = CourseStore.get(requireContext());
        rv = root.findViewById(R.id.rv_courses);
        tvEmpty = root.findViewById(R.id.tv_empty);
        tvCount = root.findViewById(R.id.tv_course_count);
        tvHolidayTip = root.findViewById(R.id.tv_holiday_tip);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        FloatingActionButton fab = root.findViewById(R.id.fab_add_course);
        fab.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CourseEditActivity.class)));
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
        if (rv == null) return;
        List<Course> list = store.sorted();
        tvCount.setText("共 " + list.size() + " 门课程安排 · 点击编辑，长按删除");
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);

        String tip = DayPlan.upcomingTip(store);
        tvHolidayTip.setText(tip);
        tvHolidayTip.setVisibility(tip.isEmpty() ? View.GONE : View.VISIBLE);

        rv.setAdapter(new Adapter(list));
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Course> data;

        Adapter(List<Course> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_course, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Course c = data.get(position);
            int fg = Palette.fg(c.color);
            h.name.setText(c.name);
            h.name.setTextColor(fg);
            GradientDrawable bar = new GradientDrawable();
            bar.setColor(fg);
            bar.setCornerRadii(new float[]{dp(10), dp(10), 0, 0, 0, 0, dp(10), dp(10)});
            h.bar.setBackground(bar);
            h.time.setText(c.weekdayText() + "  " + c.sectionText() + "  ·  " + c.weekText(store.getTotalWeeks()));
            String loc = c.location == null || c.location.isEmpty() ? "教室未填写" : "教室：" + c.location;
            String teacher = c.teacher == null || c.teacher.isEmpty() ? "" : "  ·  教师：" + c.teacher;
            h.info.setText(loc + teacher);

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), CourseEditActivity.class);
                i.putExtra(CourseEditActivity.EXTRA_COURSE_ID, c.id);
                startActivity(i);
            });
            h.itemView.setOnLongClickListener(v -> {
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
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            View bar;
            TextView name, time, info;

            VH(@NonNull View itemView) {
                super(itemView);
                bar = itemView.findViewById(R.id.view_color_bar);
                name = itemView.findViewById(R.id.tv_course_name);
                time = itemView.findViewById(R.id.tv_course_time);
                info = itemView.findViewById(R.id.tv_course_info);
            }
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
