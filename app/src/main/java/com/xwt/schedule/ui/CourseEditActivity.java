package com.xwt.schedule.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.xwt.schedule.R;
import com.xwt.schedule.data.CourseStore;
import com.xwt.schedule.model.Course;
import com.xwt.schedule.notify.AlarmScheduler;
import com.xwt.schedule.util.Palette;
import com.xwt.schedule.util.TimeTable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CourseEditActivity extends AppCompatActivity {

    public static final String EXTRA_COURSE_ID = "course_id";

    private CourseStore store;
    private long editingId = -1L;

    private TextInputEditText etName, etTeacher, etLocation;
    private ChipGroup chipDay, chipStart, chipCount, chipWeekType;
    private AppCompatSpinner spWeekStart, spWeekEnd;
    private TextView tvTimePreview, tvCustomHint;
    private GridLayout gridCustomWeeks;
    private LinearLayout layoutColors;

    private final List<TextView> weekCells = new ArrayList<>();
    private int selectedColor = 0;

    private final int[] dayIds = new int[7];
    private final int[] startIds = new int[TimeTable.SECTIONS];
    private final int[] countIds = new int[4];
    private final int[] typeIds = new int[4];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_edit);
        store = CourseStore.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle("添加课程");

        etName = findViewById(R.id.et_name);
        etTeacher = findViewById(R.id.et_teacher);
        etLocation = findViewById(R.id.et_location);
        chipDay = findViewById(R.id.chip_day);
        chipStart = findViewById(R.id.chip_start);
        chipCount = findViewById(R.id.chip_count);
        chipWeekType = findViewById(R.id.chip_week_type);
        spWeekStart = findViewById(R.id.spinner_week_start);
        spWeekEnd = findViewById(R.id.spinner_week_end);
        tvTimePreview = findViewById(R.id.tv_time_preview);
        tvCustomHint = findViewById(R.id.tv_custom_hint);
        gridCustomWeeks = findViewById(R.id.grid_custom_weeks);
        layoutColors = findViewById(R.id.layout_colors);

        buildChips();
        buildSpinners();
        buildCustomWeekGrid();
        buildColors();

        MaterialButton btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> save());
        MaterialButton btnDelete = findViewById(R.id.btn_delete);
        btnDelete.setOnClickListener(v -> confirmDelete());

        editingId = getIntent().getLongExtra(EXTRA_COURSE_ID, -1L);
        if (editingId > 0) {
            Course c = store.get(editingId);
            if (c != null) bindCourse(c);
        } else {
            // 默认值：周一、第 1 节起、连排 2 节、每周、第 1 ~ 本学期周数（默认 16）周
            chipDay.check(dayIds[Calendar.MONDAY - 1]);
            chipStart.check(startIds[0]);
            chipCount.check(countIds[1]);
            chipWeekType.check(typeIds[0]);
            selectColor(0);
        }
        updateTimePreview();
    }

    // ---------------- 构建控件 ----------------

    private Chip makeChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.chip_bg));
        chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text));
        chip.setChipStrokeWidth(0);
        chip.setChipMinHeight(dp(32));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        return chip;
    }

    private void buildChips() {
        for (int i = 0; i < 7; i++) {
            Chip c = makeChip(TimeTable.WEEKDAY_SHORT[i]);
            dayIds[i] = View.generateViewId();
            c.setId(dayIds[i]);
            chipDay.addView(c);
        }
        for (int i = 0; i < TimeTable.SECTIONS; i++) {
            Chip c = makeChip("第" + (i + 1) + "节");
            startIds[i] = View.generateViewId();
            c.setId(startIds[i]);
            chipStart.addView(c);
        }
        for (int i = 0; i < 4; i++) {
            Chip c = makeChip((i + 1) + "节");
            countIds[i] = View.generateViewId();
            c.setId(countIds[i]);
            chipCount.addView(c);
        }
        String[] types = {"每周", "单周", "双周", "自定义"};
        for (int i = 0; i < 4; i++) {
            Chip c = makeChip(types[i]);
            typeIds[i] = View.generateViewId();
            c.setId(typeIds[i]);
            chipWeekType.addView(c);
        }

        chipStart.setOnCheckedStateChangeListener((group, checked) -> updateTimePreview());
        chipCount.setOnCheckedStateChangeListener((group, checked) -> updateTimePreview());
        chipWeekType.setOnCheckedStateChangeListener((group, checked) -> {
            int type = checkedType();
            boolean custom = type == Course.TYPE_CUSTOM;
            tvCustomHint.setVisibility(custom ? View.VISIBLE : View.GONE);
            gridCustomWeeks.setVisibility(custom ? View.VISIBLE : View.GONE);
        });
    }

    private void buildSpinners() {
        int total = store.getTotalWeeks();
        Integer[] weeks = new Integer[total];
        for (int i = 0; i < total; i++) weeks[i] = i + 1;
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, weeks);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spWeekStart.setAdapter(adapter);
        spWeekEnd.setAdapter(adapter);
        spWeekEnd.setSelection(total - 1);
        spWeekStart.setOnItemSelectedListener(new SimpleSelect());
        spWeekEnd.setOnItemSelectedListener(new SimpleSelect());
    }

    private static class SimpleSelect implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            // 保证起始周不晚于结束周
        }

        @Override
        public void onNothingSelected(AdapterView<?> p) {
        }
    }

    private void buildCustomWeekGrid() {
        gridCustomWeeks.setColumnCount(5);
        int total = store.getTotalWeeks();
        float density = getResources().getDisplayMetrics().density;
        int screen = getResources().getDisplayMetrics().widthPixels;
        int cell = (screen - dp(16) * 2 - dp(14) * 2 - dp(3) * 8) / 5;
        for (int w = 1; w <= total; w++) {
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(w));
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setTextColor(ContextCompat.getColorStateList(this, R.color.week_cell_text));
            tv.setBackgroundResource(R.drawable.bg_week_cell);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = dp(40);
            int m = dp(3);
            lp.setMargins(m, m, m, m);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> tv.setSelected(!tv.isSelected()));
            weekCells.add(tv);
            gridCustomWeeks.addView(tv);
        }
    }

    private void buildColors() {
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < Palette.BG.length; i++) {
            final int idx = i;
            TextView sw = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(8);
            sw.setLayoutParams(lp);
            sw.setTag(i);
            sw.setOnClickListener(v -> selectColor(idx));
            layoutColors.addView(sw);
            paintSwatch(sw, i, false);
        }
    }

    private void paintSwatch(TextView sw, int idx, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Palette.bg(idx));
        if (selected) g.setStroke(dp(2.5f), Palette.fg(idx));
        sw.setBackground(g);
        sw.setText(selected ? "✓" : "");
        sw.setGravity(Gravity.CENTER);
        sw.setTextColor(Palette.fg(idx));
        sw.setTypeface(Typeface.DEFAULT_BOLD);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    }

    private void selectColor(int idx) {
        selectedColor = idx;
        for (int i = 0; i < layoutColors.getChildCount(); i++) {
            View child = layoutColors.getChildAt(i);
            paintSwatch((TextView) child, i, i == idx);
        }
    }

    // ---------------- 回填 / 读取 ----------------

    private void bindCourse(Course c) {
        ((MaterialToolbar) findViewById(R.id.toolbar)).setTitle("编辑课程");
        etName.setText(c.name);
        etTeacher.setText(c.teacher);
        etLocation.setText(c.location);
        chipDay.check(dayIds[c.day - 1]);
        chipStart.check(startIds[c.startSection - 1]);
        chipCount.check(countIds[c.sectionCount - 1]);
        chipWeekType.check(typeIds[c.weekType]);
        spWeekStart.setSelection(c.weekStart - 1);
        spWeekEnd.setSelection(c.weekEnd - 1);
        for (int w : c.customWeeks) {
            if (w >= 1 && w <= weekCells.size()) weekCells.get(w - 1).setSelected(true);
        }
        selectColor(c.color);
        findViewById(R.id.btn_delete).setVisibility(View.VISIBLE);
        boolean custom = c.weekType == Course.TYPE_CUSTOM;
        tvCustomHint.setVisibility(custom ? View.VISIBLE : View.GONE);
        gridCustomWeeks.setVisibility(custom ? View.VISIBLE : View.GONE);
    }

    private int checkedIndex(ChipGroup group, int[] ids) {
        int checked = group.getCheckedChipId();
        for (int i = 0; i < ids.length; i++) if (ids[i] == checked) return i;
        return -1;
    }

    private int checkedType() {
        int idx = checkedIndex(chipWeekType, typeIds);
        return idx < 0 ? Course.TYPE_ALL : idx;
    }

    private void updateTimePreview() {
        int s = checkedIndex(chipStart, startIds);
        int ci = checkedIndex(chipCount, countIds);
        int start = s < 0 ? 0 : s;
        int count = ci < 0 ? 1 : ci + 1;
        if (start + count > TimeTable.SECTIONS) {
            count = TimeTable.SECTIONS - start;
            chipCount.check(countIds[count - 1]);
        }
        int end = Math.min(TimeTable.SECTIONS, start + count);
        tvTimePreview.setText("上课时间 " + TimeTable.START[start] + " - "
                + TimeTable.END[end - 1] + "（第" + (start + 1) + "-" + end + "节）");
    }

    private void save() {
        String name = text(etName);
        if (name.isEmpty()) {
            toast("请填写课程名称");
            return;
        }
        int dayIdx = checkedIndex(chipDay, dayIds);
        if (dayIdx < 0) {
            toast("请选择星期");
            return;
        }
        int startIdx = checkedIndex(chipStart, startIds);
        int countIdx = checkedIndex(chipCount, countIds);
        if (startIdx < 0 || countIdx < 0) {
            toast("请选择节次");
            return;
        }
        int count = countIdx + 1;
        if (startIdx + count > TimeTable.SECTIONS) {
            toast("节次超出一天 11 节范围");
            return;
        }
        int ws = spWeekStart.getSelectedItemPosition() + 1;
        int we = spWeekEnd.getSelectedItemPosition() + 1;
        if (ws > we) {
            toast("起始周不能晚于结束周");
            return;
        }
        int type = checkedType();
        List<Integer> custom = new ArrayList<>();
        if (type == Course.TYPE_CUSTOM) {
            for (int i = 0; i < weekCells.size(); i++) {
                if (weekCells.get(i).isSelected()) custom.add(i + 1);
            }
            if (custom.isEmpty()) {
                toast("自定义模式下请至少选择一个上课周");
                return;
            }
        }

        Course c = editingId > 0 && store.get(editingId) != null ? store.get(editingId) : new Course();
        c.name = name;
        c.teacher = text(etTeacher);
        c.location = text(etLocation);
        c.day = dayIdx + 1;
        c.startSection = startIdx + 1;
        c.sectionCount = count;
        c.weekType = type;
        c.weekStart = ws;
        c.weekEnd = we;
        c.customWeeks = custom;
        c.color = selectedColor;

        if (editingId > 0 && store.get(editingId) != null) {
            store.update(c);
        } else {
            store.add(c);
        }
        AlarmScheduler.reschedule(this);
        toast("已保存");
        finish();
    }

    private void confirmDelete() {
        if (editingId <= 0) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除课程")
                .setMessage("确定删除这门课吗？")
                .setPositiveButton("删除", (d, w) -> {
                    store.delete(editingId);
                    AlarmScheduler.reschedule(this);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
