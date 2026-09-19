package com.xwt.schedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.appcompat.widget.AppCompatTextView;

import com.xwt.schedule.model.Course;
import com.xwt.schedule.util.FoldGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 课表里的一张课程卡片。
 *
 * <p>当同一时段挤了不止一门课时，卡片右上角会画一个「折角」（褶皱）并标上门数，
 * 用来暗示"这里还有东西，点开看看" —— 而不是把卡片左右切成几条窄条。
 * 课表一列本来就只有 40 多 dp 宽，切成两条后课程名会被截成两三个字，等于没显示。
 *
 * <p>折角画在 {@code onDraw} 里、{@code super.onDraw} 之后：TextView 的背景是它自己的
 * background，绘制在内容之前，所以这里画能盖在最上层。画之前先按圆角矩形裁剪，
 * 折角才会跟着卡片的圆角走，不会戳出圆角外面。
 *
 * <p>折角会占掉右上角一块，所以需要避让的顶部内边距由调用方（{@link ScheduleGridView}）
 * 在创建卡片时一并设好 —— 不在 {@code onMeasure} 里改 padding，
 * 那会触发额外的布局传递，甚至死循环。
 */
public class CourseCardView extends AppCompatTextView {

    private final Paint foldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF cardBounds = new RectF();

    private final float cornerRadius;
    private final float foldSize;

    /** 1 表示只有一门课，不画折角。 */
    private int stackCount = 1;

    /** 这张卡片代表的课程。同一时段重叠时会有多门，顺序与课表里的排序一致。 */
    private List<Course> courses = Collections.emptyList();

    public CourseCardView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        cornerRadius = 6 * density;
        foldSize = FoldGeometry.NOMINAL_SIZE_DP * density;

        foldPaint.setStyle(Paint.Style.FILL);
        creasePaint.setStyle(Paint.Style.STROKE);
        creasePaint.setStrokeWidth(Math.max(1f, density));
        countPaint.setTextAlign(Paint.Align.CENTER);
        countPaint.setTextSize(9 * getResources().getDisplayMetrics().scaledDensity);
        countPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /** 折角的名义边长（dp 转 px）。调用方据此给正文留出避让的顶部内边距。 */
    public float foldSize() {
        return foldSize;
    }

    /** 折角在具体尺寸下的实际边长；卡片太小时按比例缩，不撑破卡片。 */
    public float foldSizeFor(int w, int h) {
        return FoldGeometry.sizeFor(w, h, foldSize);
    }

    /**
     * 折角三角的三个顶点 {@code (x0,y0, x1,y1, x2,y2)}，相对卡片左上角。
     * 实际算法在 {@link FoldGeometry} 里，那边是纯函数，可以用普通 JVM 单测覆盖。
     */
    public float[] foldTriangle(int w, int h) {
        return FoldGeometry.triangle(w, h, foldSize);
    }

    /**
     * 这张卡片代表的课程。一门课时不画折角；多门课时右上角出现折角 + 门数。
     * 课程列表同时作为「点开看全部」的数据来源。
     */
    public void setCourses(List<Course> list) {
        List<Course> copy = list == null ? Collections.<Course>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
        courses = copy;
        int c = Math.max(1, copy.size());
        if (c == stackCount) return;
        stackCount = c;
        invalidate();
    }

    public List<Course> getCourses() {
        return courses;
    }

    /** 这一时段有几门课。大于 1 时右上角出现折角标记。 */
    public int getStackCount() {
        return stackCount;
    }

    /** 折角配色，一般传课程自己的前景色 + 白色数字。 */
    public void setFoldColors(int fill, int crease, int text) {
        foldPaint.setColor(fill);
        creasePaint.setColor(crease);
        countPaint.setColor(text);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (stackCount <= 1) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float s = foldSizeFor(w, h);
        if (s <= 2) return;

        // 折角要跟着卡片的圆角走，所以先裁到圆角矩形里
        cardBounds.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(cardBounds, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        // 右上角那一块"翻起来的纸角"
        float[] t = foldTriangle(w, h);
        Path corner = new Path();
        corner.moveTo(t[0], t[1]);
        corner.lineTo(t[2], t[3]);
        corner.lineTo(t[4], t[5]);
        corner.close();
        canvas.drawPath(corner, foldPaint);

        // 折痕：沿斜边描一条略深的线，平面三角形才有"折过来"的感觉
        canvas.drawLine(t[0], t[1], t[4], t[5], creasePaint);
        canvas.restore();

        // 门数画在三角内部
        String label = stackCount > 9 ? "9+" : String.valueOf(stackCount);
        float[] c = FoldGeometry.labelCenter(w, h, foldSize);
        float cy = c[1] - (countPaint.descent() + countPaint.ascent()) / 2f;
        canvas.drawText(label, c[0], cy, countPaint);
    }
}
