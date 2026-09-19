package com.xwt.schedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.util.DayPlan;
import com.xwt.schedule.util.Palette;
import com.xwt.schedule.util.TimeTable;
import com.xwt.schedule.util.WeekUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 周课表网格：左侧节次时间列 + 周日~周六 7 列 + 课程卡片。
 *
 * <p>非本周课程置灰显示。同一时段有多门课时<b>不再左右分栏</b>，而是合并成一张整宽卡片，
 * 右上角画折角并标出门数，点一下把该时段的全部课程列出来 —— 一列只有 40 多 dp 宽，
 * 切成窄条之后课程名会被截断到失去意义。
 *
 * <p>表头按中国法定节假日适配：放假当天标注「休」并整列留空，调休上班日标注「补」
 * 且改为显示被调休星期的课表。
 */
public class ScheduleGridView extends ViewGroup {

    private final float density;
    private final int labelW;
    private final int headerH;
    private final int sectionH;
    private final int cardGap;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headDatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holidayWatermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int week = 1;
    private final List<Course> courses = new ArrayList<>();
    private final List<CardItem> cards = new ArrayList<>();
    private OnCourseClickListener listener;

    public interface OnCourseClickListener {
        /** 点到的时段只有一门课。 */
        void onCourseClick(Course c);

        /** 点到的时段挤了多门课，交给调用方把全部列出来。 */
        void onCoursesClick(List<Course> courses);
    }

    private static class CardItem {
        View view;
        RectF rect = new RectF();
        /** 这张卡片代表的一门或多门课（同一时段重叠时会有多门）。 */
        List<Course> courses = new ArrayList<>();
    }

    public ScheduleGridView(Context ctx) {
        this(ctx, null);
    }

    public ScheduleGridView(Context ctx, android.util.AttributeSet attrs) {
        super(ctx, attrs);
        density = getResources().getDisplayMetrics().density;
        labelW = dp(46);
        headerH = dp(50);
        sectionH = dp(58);
        cardGap = dp(3);

        linePaint.setColor(0xFFF1F2F4);
        linePaint.setStrokeWidth(1f);
        todayBgPaint.setColor(0xFFF4F9FF);
        numPaint.setColor(0xFF33383F);
        numPaint.setTextSize(sp(13));
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTypeface(Typeface.DEFAULT_BOLD);
        timePaint.setColor(0xFF9AA0A8);
        timePaint.setTextSize(sp(9));
        timePaint.setTextAlign(Paint.Align.CENTER);
        headTextPaint.setTextSize(sp(12.5f));
        headTextPaint.setTextAlign(Paint.Align.CENTER);
        headDatePaint.setTextSize(sp(9.5f));
        headDatePaint.setTextAlign(Paint.Align.CENTER);
        nowLinePaint.setColor(0xFFE56B6B);
        nowLinePaint.setStrokeWidth(dp(1.6f));
        holidayWatermarkPaint.setColor(0xFFE3E4E8);
        holidayWatermarkPaint.setTextSize(sp(11));
        holidayWatermarkPaint.setTextAlign(Paint.Align.CENTER);
        holidayWatermarkPaint.setTypeface(Typeface.DEFAULT_BOLD);
        setWillNotDraw(false);
    }

    public void setData(int week, List<Course> data) {
        this.week = week;
        this.courses.clear();
        this.courses.addAll(data);
        rebuildCards();
        invalidate();
    }

    public void setOnCourseClickListener(OnCourseClickListener l) {
        this.listener = l;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    private int colWidth() {
        return (getWidth() - labelW) / 7;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = headerH + sectionH * TimeTable.SECTIONS;
        setMeasuredDimension(w, h);
        for (CardItem item : cards) {
            View v = item.view;
            int mw = MeasureSpec.makeMeasureSpec((int) item.rect.width(), MeasureSpec.EXACTLY);
            int mh = MeasureSpec.makeMeasureSpec((int) item.rect.height(), MeasureSpec.EXACTLY);
            v.measure(mw, mh);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (CardItem item : cards) {
            View v = item.view;
            v.layout((int) item.rect.left, (int) item.rect.top,
                    (int) item.rect.right, (int) item.rect.bottom);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 首次测量前 getWidth() 为 0，卡片是按屏幕宽度估算的；宽度真正确定（含旋转）后重算一次
        if (w != oldw && !cards.isEmpty()) {
            post(this::rebuildCards);
        }
    }

    // ---------------- 卡片构建 ----------------

    private void rebuildCards() {
        removeAllViews();
        cards.clear();
        int colW = getColWidthRaw();
        CourseStore store = CourseStore.get(getContext());

        // 按天分组并对重叠课程做连通分簇
        for (int col = 0; col < 7; col++) {
            Calendar date = WeekUtil.dateOf(store, week, col);
            // 法定节假日返回 0（当天不排课）；调休上班日返回被调休的星期
            int effectiveDay = DayPlan.effectiveDayOfWeek(store, date.getTime());
            if (effectiveDay == 0) continue;

            List<Course> dayCourses = new ArrayList<>();
            for (Course c : courses) {
                if (c.day == effectiveDay) dayCourses.add(c);
            }
            if (dayCourses.isEmpty()) continue;

            int n = dayCourses.size();
            int[] cluster = new int[n];
            for (int i = 0; i < n; i++) cluster[i] = i;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Course a = dayCourses.get(i), b = dayCourses.get(j);
                    if (a.startSection <= b.endSection() && b.startSection <= a.endSection()) {
                        int ra = root(cluster, i), rb = root(cluster, j);
                        if (ra != rb) cluster[Math.max(ra, rb)] = Math.min(ra, rb);
                    }
                }
            }
            Map<Integer, List<Integer>> groups = new HashMap<>();
            for (int i = 0; i < n; i++) {
                int r = root(cluster, i);
                groups.computeIfAbsent(r, k -> new ArrayList<>()).add(i);
            }
            for (List<Integer> g : groups.values()) {
                g.sort((x, y) -> {
                    int sx = Integer.compare(dayCourses.get(x).startSection, dayCourses.get(y).startSection);
                    return sx != 0 ? sx : Long.compare(dayCourses.get(x).id, dayCourses.get(y).id);
                });
                List<Course> group = new ArrayList<>(g.size());
                for (int idx : g) group.add(dayCourses.get(idx));
                addCard(group, col, colW);
            }
        }
        requestLayout();
    }

    private int root(int[] cluster, int i) {
        while (cluster[i] != i) {
            cluster[i] = cluster[cluster[i]];
            i = cluster[i];
        }
        return i;
    }

    /** measure 前 getWidth 可能为 0，用屏幕宽度兜底计算列宽。 */
    private int getColWidthRaw() {
        int w = getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        return (w - labelW) / 7;
    }

    /**
     * 给一个「时段分组」画一张整宽卡片。
     *
     * <p>分组里有多门课时不再左右分栏，而是只显示第一门，并在右上角画折角 + 门数；
     * 点一下由调用方把这一时段的全部课程列出来。
     * 一列只有 40 多 dp 宽，切成两条之后课程名只剩两三个字，比不显示还糟。
     */
    private void addCard(List<Course> group, int col, int colW) {
        Course c = group.get(0);
        int stackCount = group.size();
        boolean on = false;
        for (Course g : group) {
            if (g.occursInWeek(week)) {
                on = true;
                break;
            }
        }

        CourseCardView tv = new CourseCardView(getContext());
        tv.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        int pad = dp(3);
        // 有折角时把正文往下压一点，免得课程名钻到折角下面
        int extraTop = stackCount > 1 ? (int) Math.ceil(tv.foldSize()) : 0;
        tv.setPadding(pad, dp(4) + extraTop, pad, pad);
        tv.setIncludeFontPadding(false);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, c.sectionCount >= 2 ? 10.5f : 10f);
        tv.setMaxLines(c.sectionCount >= 2 ? 4 : 2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setLineSpacing(0, 1.05f);

        String loc = c.location == null ? "" : c.location;
        StringBuilder sb = new StringBuilder(c.name);
        if (!loc.isEmpty()) sb.append("\n@").append(loc);
        tv.setText(sb.toString());

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        if (on) {
            bg.setColor(Palette.bg(c.color));
            tv.setTextColor(Palette.fg(c.color));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            bg.setColor(Palette.DIM_BG);
            tv.setTextColor(Palette.DIM_FG);
            tv.setTypeface(Typeface.DEFAULT);
        }
        tv.setBackground(bg);

        // 把整组课程挂到卡片上：一门课不画折角，多门课右上角出现折角 + 门数
        tv.setCourses(group);
        if (stackCount > 1) {
            int accent = on ? Palette.fg(c.color) : Palette.DIM_FG;
            tv.setFoldColors(accent, Palette.strong(accent), 0xFFFFFFFF);
        }
        final List<Course> slot = group;
        tv.setOnClickListener(v -> {
            if (listener == null) return;
            if (slot.size() == 1) listener.onCourseClick(slot.get(0));
            else listener.onCoursesClick(slot);
        });

        float left = labelW + col * colW + cardGap;
        float top = headerH + (c.startSection - 1) * sectionH + cardGap;
        CardItem item = new CardItem();
        item.view = tv;
        item.courses = slot;
        item.rect.set(left, top, left + colW - 2 * cardGap,
                top + c.sectionCount * sectionH - 2 * cardGap);
        cards.add(item);
        addView(tv);
    }

    // ---------------- 网格与文字绘制 ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int colW = colWidth();
        CourseStore store = CourseStore.get(getContext());
        int curWeek = WeekUtil.currentWeek(store);
        int todayCol = WeekUtil.todayColumn();

        // 注意：周六、周日以及法定节假日没有课是常态，不做灰底等特殊底色处理，
        // 避免把"本来就没课"渲染成"异常状态"。节假日/调休信息只在表头以角标体现。
        // 今天所在列高亮
        if (week == curWeek) {
            canvas.drawRect(labelW + todayCol * colW, 0,
                    labelW + (todayCol + 1) * colW, getHeight(), todayBgPaint);
        }

        // 表头：星期 + 日期 + 休/补 角标
        for (int col = 0; col < 7; col++) {
            float cx = labelW + col * colW + colW / 2f;
            Calendar d = WeekUtil.dateOf(store, week, col);
            boolean isToday = week == curWeek && col == todayCol;
            int status = DayPlan.status(store, d.getTime());
            String badge = DayPlan.badge(store, d.getTime());

            headTextPaint.setColor(isToday ? 0xFF4A8FE7 : 0xFF555B63);
            headTextPaint.setTypeface(isToday ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            canvas.drawText(TimeTable.WEEKDAY_SHORT[col], cx, dp(20), headTextPaint);

            int dateColor;
            if (status == DayPlan.STATUS_HOLIDAY) dateColor = 0xFFE56B6B;
            else if (status == DayPlan.STATUS_MAKEUP) dateColor = 0xFF4A8FE7;
            else if (isToday) dateColor = 0xFF4A8FE7;
            else dateColor = 0xFF9AA0A8;
            headDatePaint.setColor(dateColor);
            String dateText = WeekUtil.mdc(d);
            if (!badge.isEmpty()) dateText = dateText + " " + badge;
            canvas.drawText(dateText, cx, dp(38), headDatePaint);
        }

        // 横线 + 左侧节次/时间
        for (int s = 0; s <= TimeTable.SECTIONS; s++) {
            float y = headerH + s * sectionH;
            canvas.drawLine(0, y, w, y, linePaint);
        }
        for (int s = 0; s < TimeTable.SECTIONS; s++) {
            float top = headerH + s * sectionH;
            canvas.drawText(String.valueOf(s + 1), labelW / 2f, top + dp(17), numPaint);
            canvas.drawText(TimeTable.START[s], labelW / 2f, top + dp(33), timePaint);
            canvas.drawText(TimeTable.END[s], labelW / 2f, top + dp(47), timePaint);
        }
        // 竖线
        for (int i = 0; i <= 7; i++) {
            float x = labelW + i * colW;
            canvas.drawLine(x, 0, x, getHeight(), linePaint);
        }
        canvas.drawLine(0, headerH, w, headerH, linePaint);

        // 放假列的水印
        for (int col = 0; col < 7; col++) {
            Calendar d = WeekUtil.dateOf(store, week, col);
            if (DayPlan.status(store, d.getTime()) != DayPlan.STATUS_HOLIDAY) continue;
            float cx = labelW + col * colW + colW / 2f;
            float cy = headerH + sectionH * 2.2f;
            String name = DayPlan.holidayName(d.getTime());
            if (name.isEmpty()) name = "法定节假日";
            canvas.drawText(name, cx, cy, holidayWatermarkPaint);
            canvas.drawText("放假", cx, cy + dp(18), holidayWatermarkPaint);
        }

        // 当前时间红线（仅当前周、且处于有课时段内）
        if (week == curWeek) drawNowLine(canvas, colW);
    }

    private void drawNowLine(Canvas canvas, int colW) {
        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int s = 0; s < TimeTable.SECTIONS; s++) {
            int st = TimeTable.toMinutes(TimeTable.START[s]);
            int en = TimeTable.toMinutes(TimeTable.END[s]);
            if (nowMin >= st && nowMin <= en) {
                float frac = (float) (nowMin - st) / Math.max(1, en - st);
                float y = headerH + s * sectionH + frac * sectionH;
                canvas.drawLine(labelW, y, getWidth(), y, nowLinePaint);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0xFFE56B6B);
                canvas.drawCircle(labelW + dp(3), y, dp(3), p);
                break;
            }
        }
    }
}
