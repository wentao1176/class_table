package com.xwt.schedule.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xwt.schedule.MainActivity;
import com.xwt.schedule.R;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.Palette;
import com.xwt.schedule.util.TimeTable;
import com.xwt.schedule.util.WeekUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 今日页：下一节课倒计时 + 今日课程时间线。
 * 已适配中国法定节假日：放假当天不显示课程、调休上班日改为显示被调休星期的课表。
 */
public class TodayFragment extends Fragment implements MainActivity.Refreshable {

    private CourseStore store;
    private TextView tvDate, tvSub, tvEmpty;
    private LinearLayout cardNext;
    private TextView tvNextLabel, tvNextName, tvNextTime, tvNextLoc, tvNextTeacher, tvNextCountdown;
    private RecyclerView rv;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_today, container, false);
        store = CourseStore.get(requireContext());
        tvDate = root.findViewById(R.id.tv_today_date);
        tvSub = root.findViewById(R.id.tv_today_sub);
        tvEmpty = root.findViewById(R.id.tv_today_empty);
        cardNext = root.findViewById(R.id.card_next);
        tvNextLabel = root.findViewById(R.id.tv_next_label);
        tvNextName = root.findViewById(R.id.tv_next_name);
        tvNextTime = root.findViewById(R.id.tv_next_time);
        tvNextLoc = root.findViewById(R.id.tv_next_loc);
        tvNextTeacher = root.findViewById(R.id.tv_next_teacher);
        tvNextCountdown = root.findViewById(R.id.tv_next_countdown);
        rv = root.findViewById(R.id.rv_today);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        refresh();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, 30_000);
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    private static class Occurrence {
        Course course;
        Calendar date;
    }

    private void refresh() {
        if (tvDate == null) return;
        Calendar now = Calendar.getInstance();
        int curWeek = WeekUtil.currentWeek(store);
        int todayCol = WeekUtil.todayColumn();
        Date today = now.getTime();

        int todayStatus = DayPlan.status(store, today);
        String note = DayPlan.note(store, today);

        tvDate.setText("今天 " + WeekUtil.cnDate(now) + " " + TimeTable.WEEKDAY_SHORT[todayCol]);
        String sub = store.getSemesterName() + " · 第 " + curWeek + " 周（共 " + store.getTotalWeeks() + " 周）";
        if (!note.isEmpty()) sub = sub + "\n" + note;
        tvSub.setText(sub);

        // 今天实际上课的星期：放假为 0，调休上班日为被调休的星期
        int effectiveDay = DayPlan.effectiveDayOfWeek(store, today);

        List<Course> todayList = new ArrayList<>();
        if (effectiveDay != 0) {
            for (Course c : store.all()) {
                if (c.day == effectiveDay && c.occursInWeek(curWeek)) todayList.add(c);
            }
            todayList.sort(Comparator.comparingInt(c -> c.startSection));
        }

        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        Occurrence next = findNext(now, nowMin, todayList);
        renderNext(next, now, nowMin, todayStatus, note);

        tvEmpty.setText(todayStatus == DayPlan.STATUS_HOLIDAY
                ? "今天放假，好好休息" : "今天没有课程安排，好好休息");
        tvEmpty.setVisibility(todayList.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setVisibility(todayList.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setAdapter(new TodayAdapter(todayList, nowMin));
    }

    private Occurrence findNext(Calendar now, int nowMin, List<Course> todayList) {
        for (Course c : todayList) {
            int endMin = TimeTable.toMinutes(TimeTable.END[c.endSection() - 1]);
            if (endMin > nowMin) {
                Occurrence o = new Occurrence();
                o.course = c;
                o.date = (Calendar) now.clone();
                return o;
            }
        }
        // 今天没课了：向后找最近一次课（跳过法定节假日，最多找 60 天）
        Calendar semesterEnd = WeekUtil.atStartOfDay(store.getSemesterEnd());
        for (int off = 1; off <= 60; off++) {
            Calendar cal = (Calendar) now.clone();
            cal.add(Calendar.DAY_OF_YEAR, off);
            if (WeekUtil.atStartOfDay(cal.getTime()).after(semesterEnd)) break;
            int eff = DayPlan.effectiveDayOfWeek(store, cal.getTime());
            if (eff == 0) continue;
            int w = WeekUtil.weekOfDate(store, cal.getTime());
            List<Course> dayList = new ArrayList<>();
            for (Course c : store.all()) {
                if (c.day == eff && c.occursInWeek(w)) dayList.add(c);
            }
            dayList.sort(Comparator.comparingInt(c -> c.startSection));
            if (!dayList.isEmpty()) {
                Occurrence o = new Occurrence();
                o.course = dayList.get(0);
                o.date = cal;
                return o;
            }
        }
        return null;
    }

    private void renderNext(@Nullable Occurrence next, Calendar now, int nowMin,
                            int todayStatus, String note) {
        if (todayStatus == DayPlan.STATUS_HOLIDAY) {
            renderHoliday(next, now, note);
            return;
        }
        if (next == null) {
            cardNext.setBackground(rounded(0xFF9AA0A8, dp(14)));
            tvNextLabel.setText("下一节课");
            tvNextName.setText("近期没有课程安排");
            tvNextTime.setText("");
            tvNextLoc.setText("");
            tvNextTeacher.setText("");
            tvNextCountdown.setText("享受假期吧");
            return;
        }
        Course c = next.course;
        cardNext.setBackground(rounded(Palette.fg(c.color), dp(14)));
        tvNextLabel.setText(todayStatus == DayPlan.STATUS_MAKEUP ? "下一节课 · 调休补课" : "下一节课");
        tvNextName.setText(c.name);
        tvNextTime.setText(c.weekdayText() + " " + c.sectionText());
        tvNextLoc.setText((c.location == null || c.location.isEmpty()) ? "教室未填写" : "教室：" + c.location);
        tvNextTeacher.setText((c.teacher == null || c.teacher.isEmpty()) ? "教师未填写" : "教师：" + c.teacher);

        int startMin = TimeTable.toMinutes(TimeTable.START[c.startSection - 1]);
        boolean sameDay = sameDay(next.date, now);
        if (sameDay) {
            int endMin = TimeTable.toMinutes(TimeTable.END[c.endSection() - 1]);
            if (nowMin >= startMin && nowMin <= endMin) {
                tvNextCountdown.setText("正在上课，" + TimeTable.END[c.endSection() - 1] + " 下课");
            } else {
                tvNextCountdown.setText("还有 " + gapText((startMin - nowMin) * 60_000L) + "上课");
            }
        } else {
            int dayDiff = daysTo(now, next.date);
            String when = dayDiff == 1 ? "明天" : TimeTable.WEEKDAY_SHORT[next.date.get(Calendar.DAY_OF_WEEK) - 1];
            tvNextTime.setText(when + " " + c.sectionText());
            long delta = nextDateMillis(next.date, c.startSection) - System.currentTimeMillis();
            tvNextCountdown.setText(delta > 0 ? "还有 " + dayDiff + " 天" : "");
        }
    }

    /** 今天放假：主卡片显示假期信息，并预告假期后的第一节课。 */
    private void renderHoliday(@Nullable Occurrence next, Calendar now, String note) {
        cardNext.setBackground(rounded(0xFFE56B6B, dp(14)));
        tvNextLabel.setText("法定节假日");
        tvNextName.setText(note.isEmpty() ? "今天放假" : note);
        if (next == null) {
            tvNextTime.setText("");
            tvNextLoc.setText("");
            tvNextTeacher.setText("");
            tvNextCountdown.setText("享受假期吧");
            return;
        }
        Course c = next.course;
        int dayDiff = Math.max(1, daysTo(now, next.date));
        String when = dayDiff == 1 ? "明天"
                : TimeTable.WEEKDAY_SHORT[next.date.get(Calendar.DAY_OF_WEEK) - 1];
        tvNextTime.setText("假期后第一节课：" + when + " " + c.sectionText());
        tvNextLoc.setText((c.location == null || c.location.isEmpty()) ? "教室未填写" : "教室：" + c.location);
        tvNextTeacher.setText((c.teacher == null || c.teacher.isEmpty()) ? "教师未填写" : "教师：" + c.teacher);
        tvNextCountdown.setText("还有 " + dayDiff + " 天");
    }

    private long nextDateMillis(Calendar date, int startSection) {
        Calendar c = (Calendar) date.clone();
        int[] hm = parseHm(TimeTable.START[startSection - 1]);
        c.set(Calendar.HOUR_OF_DAY, hm[0]);
        c.set(Calendar.MINUTE, hm[1]);
        c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }

    private int[] parseHm(String s) {
        String[] p = s.split(":");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private int daysTo(Calendar from, Calendar to) {
        Calendar a = WeekUtil.atStartOfDay(from.getTime());
        Calendar b = WeekUtil.atStartOfDay(to.getTime());
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
    }

    private String gapText(long ms) {
        long min = Math.max(0, ms / 60_000);
        if (min < 1) return "不到 1 分钟";
        if (min < 60) return min + " 分钟";
        return min / 60 + " 小时 " + min % 60 + " 分钟";
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(color);
        return g;
    }

    // ---------------- 今日课程列表 ----------------

    private class TodayAdapter extends RecyclerView.Adapter<TodayAdapter.VH> {
        private final List<Course> data;
        private final int nowMin;

        TodayAdapter(List<Course> data, int nowMin) {
            this.data = data;
            this.nowMin = nowMin;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_today_course, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Course c = data.get(position);
            h.section.setText("第" + c.startSection + (c.sectionCount > 1 ? "-" + c.endSection() : "") + "节");
            h.time.setText(TimeTable.START[c.startSection - 1] + "\n" + TimeTable.END[c.endSection() - 1]);
            h.name.setText(c.name);
            String info = (c.location == null || c.location.isEmpty() ? "教室未填写" : "@" + c.location)
                    + (c.teacher == null || c.teacher.isEmpty() ? "" : " · " + c.teacher);
            h.info.setText(info);
            h.name.setTextColor(Palette.fg(c.color));

            int startMin = TimeTable.toMinutes(TimeTable.START[c.startSection - 1]);
            int endMin = TimeTable.toMinutes(TimeTable.END[c.endSection() - 1]);
            String status;
            int bgColor;
            int textColor;
            if (nowMin > endMin) {
                status = "已结束";
                bgColor = 0xFFF0F1F3;
                textColor = 0xFF9AA0A8;
            } else if (nowMin >= startMin) {
                status = "进行中";
                bgColor = 0xFFEAF3FE;
                textColor = 0xFF4A8FE7;
            } else {
                status = "待上课";
                bgColor = 0xFFFFF3E2;
                textColor = 0xFFE08A3C;
            }
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(bgColor);
            h.status.setBackground(g);
            h.status.setTextColor(textColor);
            h.status.setText(status);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView section, time, name, info, status;

            VH(@NonNull View itemView) {
                super(itemView);
                section = itemView.findViewById(R.id.tv_item_section);
                time = itemView.findViewById(R.id.tv_item_time);
                name = itemView.findViewById(R.id.tv_item_name);
                info = itemView.findViewById(R.id.tv_item_info);
                status = itemView.findViewById(R.id.tv_item_status);
            }
        }
    }
}
