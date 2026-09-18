package com.xwt.schedule.util;

import android.graphics.Color;

/**
 * 课程卡片马卡龙配色：浅色底 + 同色系深色文字，参照小程序截图。
 */
public final class Palette {

    public static final int[] BG = {
            0xFFFFF4C4, // 0 鹅黄
            0xFFFDE3E1, // 1 樱粉
            0xFFFCE6D6, // 2 蜜桃橙
            0xFFE2EEFD, // 3 天蓝
            0xFFEDE7FB, // 4 薰衣紫
            0xFFDBF3EC, // 5 薄荷绿
            0xFFE0F2F8, // 6 青碧
            0xFFFBE2EF, // 7 玫粉
            0xFFE7F4DC, // 8 嫩绿
            0xFFE9E9ED, // 9 浅灰紫
    };

    public static final int[] FG = {
            0xFFC7951F,
            0xFFD9635C,
            0xFFDF8548,
            0xFF4A86E8,
            0xFF8A6FD6,
            0xFF2FA186,
            0xFF3792B5,
            0xFFD15A97,
            0xFF679E4C,
            0xFF6B7077,
    };

    public static final String[] NAME = {
            "鹅黄", "樱粉", "蜜桃", "天蓝", "薰衣紫",
            "薄荷", "青碧", "玫粉", "嫩绿", "浅灰"
    };

    public static final int DIM_BG = 0xFFECEDEF;
    public static final int DIM_FG = 0xFFB4B8BE;

    private Palette() {}

    public static int bg(int index) {
        return BG[((index % BG.length) + BG.length) % BG.length];
    }

    public static int fg(int index) {
        return FG[((index % FG.length) + FG.length) % FG.length];
    }

    /** 把背景色加深一点，用于“下一节课”大卡片等。 */
    public static int strong(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.92f;
        return Color.HSVToColor(hsv);
    }
}
