package com.xwt.schedule.util;

/**
 * 课程卡片右上角「折角」的几何计算。
 *
 * <p>单独抽出来是为了能测：{@code CourseCardView} 继承自 {@code AppCompatTextView}，
 * 想在测试里验证它的绘制就得起 Robolectric（慢，而且要 Android 环境）。
 * 但折角的形状其实只是三个坐标点的算术，跟 Android 一点关系都没有 ——
 * 放到这里就能用纯 JVM 单测覆盖，画得对不对、有没有戳出卡片，一测便知。
 */
public final class FoldGeometry {

    /** 折角的默认名义边长（dp）。 */
    public static final float NOMINAL_SIZE_DP = 14f;

    private FoldGeometry() {
    }

    /**
     * 折角在具体卡片尺寸下的实际边长。
     *
     * <p>卡片很小的时候（比如只有一两行高的短课）要按比例缩小，
     * 否则折角会占掉整张卡片甚至戳出去。
     *
     * @param w             卡片宽度
     * @param h             卡片高度
     * @param nominalSizePx 名义边长（已换算成像素）
     */
    public static float sizeFor(float w, float h, float nominalSizePx) {
        return Math.min(nominalSizePx, Math.min(w, h) * 0.5f);
    }

    /**
     * 折角三角的三个顶点 {@code (x0,y0, x1,y1, x2,y2)}，相对于卡片左上角。
     *
     * <pre>
     *   (x0,y0) ──── (x1,y1)      直角顶点贴右上角
     *      \           │          两条直角边等长
     *       \          │
     *        \         (x2,y2)
     * </pre>
     *
     * @return 长度为 6 的数组；卡片尺寸非法时返回全 0
     */
    public static float[] triangle(float w, float h, float nominalSizePx) {
        if (w <= 0 || h <= 0) return new float[]{0, 0, 0, 0, 0, 0};
        float s = sizeFor(w, h, nominalSizePx);
        return new float[]{w - s, 0, w, 0, w, s};
    }

    /** 折角里那个门数标签的中心点，取三角的质心附近。 */
    public static float[] labelCenter(float w, float h, float nominalSizePx) {
        float s = sizeFor(w, h, nominalSizePx);
        return new float[]{w - s * 0.38f, s * 0.40f};
    }

    /** 折角（含描边）是否完整落在卡片内。 */
    public static boolean fitsInside(float w, float h, float nominalSizePx) {
        float[] t = triangle(w, h, nominalSizePx);
        for (float v : t) {
            if (v < 0) return false;
        }
        return t[4] <= w && t[5] <= h;
    }
}
