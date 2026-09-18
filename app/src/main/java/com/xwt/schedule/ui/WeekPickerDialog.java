package com.xwt.schedule.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 周次选择器：网格列出第 1..N 周，标记“本周”。 */
public class WeekPickerDialog extends DialogFragment {

    public interface OnWeekPicked {
        void onPicked(int week);
    }

    private int totalWeeks;
    private int currentWeek;
    private int selectedWeek;
    private OnWeekPicked callback;

    public static WeekPickerDialog newInstance(int total, int current, int selected, OnWeekPicked cb) {
        WeekPickerDialog d = new WeekPickerDialog();
        d.totalWeeks = total;
        d.currentWeek = current;
        d.selectedWeek = selected;
        d.callback = cb;
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        root.setPadding(pad, pad, pad, (int) (8 * density));

        ScrollView sv = new ScrollView(requireContext());
        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(4);
        int cell = (int) ((getResources().getDisplayMetrics().widthPixels - 2 * pad - 3 * 10 * density) / 4);

        for (int w = 1; w <= totalWeeks; w++) {
            final int week = w;
            TextView tv = new TextView(requireContext());
            String label = "第" + w + "周";
            if (w == currentWeek) label += "\n本周";
            tv.setText(label);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setPadding(0, (int) (10 * density), 0, (int) (10 * density));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10 * density);
            if (w == selectedWeek) {
                bg.setColor(0xFF4A8FE7);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            } else if (w == currentWeek) {
                bg.setColor(0xFFEAF3FE);
                tv.setTextColor(0xFF4A8FE7);
            } else {
                bg.setColor(0xFFF2F3F5);
                tv.setTextColor(0xFF3A3F45);
            }
            tv.setBackground(bg);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            int m = (int) (4 * density);
            lp.setMargins(m, m, m, m);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> {
                if (callback != null) callback.onPicked(week);
                dismiss();
            });
            grid.addView(tv);
        }
        sv.addView(grid);
        root.addView(sv);

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择周次")
                .setView(root)
                .setNegativeButton("取消", null)
                .setNeutralButton("回到本周", (d, which) -> {
                    if (callback != null) callback.onPicked(currentWeek);
                });
        return b.create();
    }
}
