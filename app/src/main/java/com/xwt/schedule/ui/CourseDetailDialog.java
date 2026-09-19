package com.xwt.schedule.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.xwt.schedule.R;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.Palette;
import com.xwt.schedule.util.WeekUtil;

import java.util.Calendar;
import java.util.List;

/**
 * 课程详情的弹窗。
 *
 * <p>刻意不用 {@code MaterialAlertDialogBuilder.setMessage()} —— 那样只有一片白底加一坨
 * 黑字，既看不出是哪门课，信息也没有层次。这里用自定义 {@link Dialog}：
 * 顶部一条课程配色的色带放课程名，下面按「时间 / 周次 / 教室 / 教师」分行，
 * 调休补课这类特殊情况单独一条浅蓝提示条。
 *
 * <p>用自定义 Dialog 而不是 AlertDialog 的自定义视图，是因为 AlertDialog 的按钮栏属于
 * 它自己的布局，把窗口背景设成透明后按钮栏会一起变透明，没法做出干净的圆角卡片。
 */
public final class CourseDetailDialog {

    private CourseDetailDialog() {
    }

    /** 详情弹窗里的动作回调。 */
    public interface Actions {
        void onEdit(Course c);

        void onDelete(Course c);
    }

    /**
     * 显示一门课的详情。
     *
     * @param displayWeek 当前查看的周次，用于说明"本周不上"与调休补课日期
     */
    public static void showDetail(final Context ctx, final CourseStore store, final Course c,
                                  final int displayWeek, final Actions actions) {
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_course_detail, null, false);

        int accent = Palette.fg(c.color);
        paintHeader(root.findViewById(R.id.header), accent);

        TextView name = root.findViewById(R.id.tv_detail_name);
        name.setText(c.name);

        boolean on = c.occursInWeek(displayWeek);
        TextView sub = root.findViewById(R.id.tv_detail_sub);
        sub.setText(c.weekdayText() + " " + c.sectionText() + (on ? "" : " · 本周不上"));

        setRow(root, R.id.tv_detail_time,
                c.weekdayText() + " " + c.sectionText() + "（" + c.sectionCount + " 节）");
        setRow(root, R.id.tv_detail_weeks, c.weekText(store.getTotalWeeks()) + " · 第"
                + displayWeek + "周" + (on ? "上课" : "不上"));
        setRow(root, R.id.tv_detail_room, blankAs(c.location, "未填写"));
        setRow(root, R.id.tv_detail_teacher, blankAs(c.teacher, "未填写"));

        // 调休补课：说明这门课为什么出现在别的星期
        TextView note = root.findViewById(R.id.tv_detail_note);
        String special = specialNote(store, c, displayWeek);
        if (special.isEmpty()) {
            note.setVisibility(View.GONE);
        } else {
            note.setVisibility(View.VISIBLE);
            note.setText(special);
        }

        final Dialog dlg = buildSheetDialog(ctx, root);

        root.findViewById(R.id.btn_detail_close).setOnClickListener(v -> dlg.dismiss());
        root.findViewById(R.id.btn_detail_edit).setOnClickListener(v -> {
            dlg.dismiss();
            if (actions != null) actions.onEdit(c);
        });
        root.findViewById(R.id.btn_detail_delete).setOnClickListener(v -> {
            dlg.dismiss();
            if (actions != null) actions.onDelete(c);
        });

        dlg.show();
    }

    /**
     * 同一时段有多门课时，把全部课程列出来让用户挑。
     * 点某一行关掉列表、打开该门课的详情。
     */
    public static void showSlot(final Context ctx, final CourseStore store,
                                final List<Course> courses, final int displayWeek,
                                final Actions actions) {
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_slot_list, null, false);

        // 用第一门课的颜色做头部，列表里每行再各自带自己的色条
        int accent = Palette.fg(courses.get(0).color);
        paintHeader(root.findViewById(R.id.slot_header), accent);

        TextView title = root.findViewById(R.id.tv_slot_title);
        title.setText("同一时段 " + courses.size() + " 门课");

        TextView subtitle = root.findViewById(R.id.tv_slot_subtitle);
        subtitle.setText(courses.get(0).weekdayText() + " " + courses.get(0).sectionText()
                + " · 第" + displayWeek + "周");

        final Dialog dlg = buildSheetDialog(ctx, root);

        LinearLayout container = root.findViewById(R.id.slot_container);
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (final Course c : courses) {
            View row = inflater.inflate(R.layout.item_slot_course, container, false);
            int fg = Palette.fg(c.color);

            GradientDrawable barBg = new GradientDrawable();
            barBg.setColor(fg);
            barBg.setCornerRadius(dp(ctx, 2));
            row.findViewById(R.id.v_slot_bar).setBackground(barBg);

            TextView rowName = row.findViewById(R.id.tv_slot_name);
            rowName.setText(c.name);
            rowName.setTextColor(fg);

            StringBuilder sb = new StringBuilder(c.weekText(store.getTotalWeeks()));
            if (!c.occursInWeek(displayWeek)) sb.append(" · 本周不上");
            String loc = blankAs(c.location, "");
            if (!loc.isEmpty()) sb.append(" · ").append(loc);
            String teacher = blankAs(c.teacher, "");
            if (!teacher.isEmpty()) sb.append(" · ").append(teacher);
            ((TextView) row.findViewById(R.id.tv_slot_info)).setText(sb.toString());

            row.setOnClickListener(v -> {
                dlg.dismiss();   // 先关掉列表，避免两个弹窗叠在一起
                showDetail(ctx, store, c, displayWeek, actions);
            });
            container.addView(row);
        }

        root.findViewById(R.id.btn_slot_close).setOnClickListener(v -> dlg.dismiss());
        dlg.show();
    }

    // ---------------- 内部工具 ----------------

    /**
     * 造一个底部带圆角的白色卡片弹窗，窗口背景透明，
     * 让圆角完全由我们自己的 {@code bg_dialog_sheet} 决定。
     */
    private static Dialog buildSheetDialog(Context ctx, View content) {
        Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(content);

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int margin = (int) dp(ctx, 24);
            int width = ctx.getResources().getDisplayMetrics().widthPixels - margin * 2;
            w.setLayout(Math.max(width, (int) dp(ctx, 240)), WindowManager.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        return dlg;
    }

    /** 头部色带：用课程配色，只有上面两个角是圆的，跟卡片圆角对齐。 */
    private static void paintHeader(View header, int color) {
        if (header == null) return;
        float r = dp(header.getContext(), 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        header.setBackground(bg);
    }

    private static void setRow(View root, int id, String text) {
        TextView tv = root.findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private static String blankAs(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }

    /** 调休补课、放假当周等需要额外说明的情况。 */
    private static String specialNote(CourseStore store, Course c, int displayWeek) {
        for (int col = 0; col < 7; col++) {
            Calendar d = WeekUtil.dateOf(store, displayWeek, col);
            if (DayPlan.effectiveDayOfWeek(store, d.getTime()) != c.day) continue;
            String note = DayPlan.note(store, d.getTime());
            if (note.isEmpty()) return "";
            return WeekUtil.format(d.getTime()) + "（" + note + "）";
        }
        return "";
    }

    private static float dp(Context ctx, float v) {
        return v * ctx.getResources().getDisplayMetrics().density;
    }

    /** 便于调用方从 Activity/Fragment 直接跳编辑页。 */
    public static void edit(Context ctx, Course c) {
        Intent i = new Intent(ctx, CourseEditActivity.class);
        i.putExtra(CourseEditActivity.EXTRA_COURSE_ID, c.id);
        ctx.startActivity(i);
    }
}
