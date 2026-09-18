package com.xwt.schedule.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.xwt.schedule.model.Course;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 课程与设置的统一存储（SharedPreferences + JSON，无需额外依赖）。
 */
public class CourseStore {

    private static final String PREF = "schedule_data";
    /** 调休日自定义值：该调休日不上课。 */
    public static final int MAKEUP_OFF = -1;
    /** 调休日自定义值：未设置，使用官方数据推导出的默认星期。 */
    public static final int MAKEUP_AUTO = 0;

    private static CourseStore instance;

    private final SharedPreferences sp;
    private final List<Course> courses = new ArrayList<>();
    private long nextId = 1;

    public static synchronized CourseStore get(Context ctx) {
        if (instance == null) instance = new CourseStore(ctx.getApplicationContext());
        return instance;
    }

    private CourseStore(Context app) {
        sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
        if (!sp.getBoolean("initialized", false)) {
            loadSamples();
            sp.edit().putBoolean("initialized", true).apply();
        }
    }

    // ---------------- 课程增删改查 ----------------

    public synchronized List<Course> all() {
        return new ArrayList<>(courses);
    }

    public synchronized Course get(long id) {
        for (Course c : courses) if (c.id == id) return c;
        return null;
    }

    public synchronized void add(Course c) {
        c.id = nextId++;
        courses.add(c);
        persist();
    }

    public synchronized void update(Course c) {
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id == c.id) {
                courses.set(i, c);
                break;
            }
        }
        persist();
    }

    public synchronized void delete(long id) {
        courses.removeIf(c -> c.id == id);
        persist();
    }

    public synchronized void clearAll() {
        courses.clear();
        persist();
    }

    public synchronized void loadSamples() {
        courses.clear();
        nextId = 1;
        // 参照截图的课表：彩色为每周上课，灰色（双周）在第 1 周不上、置灰显示
        addSample("跨文化交际", "", "中109", Calendar.TUESDAY, 1, 2, Course.TYPE_ALL, 0);
        addSample("操作系统", "", "复303", Calendar.THURSDAY, 1, 2, Course.TYPE_ALL, 1);
        addSample("操作系统", "", "中103", Calendar.FRIDAY, 1, 2, Course.TYPE_EVEN, 1);

        addSample("人工智能导论", "", "复302", Calendar.SUNDAY, 3, 2, Course.TYPE_EVEN, 2);
        addSample("人工智能导论", "", "复302", Calendar.MONDAY, 3, 2, Course.TYPE_ALL, 2);
        addSample("操作系统", "", "复304", Calendar.TUESDAY, 3, 2, Course.TYPE_ALL, 1);
        addSample("模式识别", "", "复302", Calendar.WEDNESDAY, 3, 2, Course.TYPE_ALL, 3);
        addSample("多元统计分析与SPSS应用", "", "中110", Calendar.THURSDAY, 3, 2, Course.TYPE_ALL, 4);
        addSample("模式识别", "", "中310", Calendar.FRIDAY, 3, 2, Course.TYPE_EVEN, 3);
        addSample("模式识别", "", "复302", Calendar.SATURDAY, 3, 2, Course.TYPE_EVEN, 3);

        addSample("模式识别", "", "复302", Calendar.SUNDAY, 5, 2, Course.TYPE_EVEN, 3);
        addSample("模式识别", "", "复302", Calendar.MONDAY, 5, 2, Course.TYPE_ALL, 3);
        addSample("人工智能导论", "", "复302", Calendar.WEDNESDAY, 5, 2, Course.TYPE_ALL, 2);
        addSample("物理性污染控制工程", "", "中102", Calendar.THURSDAY, 5, 2, Course.TYPE_ALL, 7);
        addSample("习近平新时代中国特色社会主义思想概论", "", "复503", Calendar.FRIDAY, 5, 2, Course.TYPE_ALL, 6);
        addSample("人工智能导论", "", "复302", Calendar.SATURDAY, 5, 2, Course.TYPE_EVEN, 2);

        addSample("企业法律风险管理", "", "中119", Calendar.WEDNESDAY, 7, 2, Course.TYPE_ALL, 5);
        addSample("人工智能导论", "", "中320", Calendar.THURSDAY, 7, 2, Course.TYPE_EVEN, 2);
        addSample("组织行为学", "", "研B204", Calendar.FRIDAY, 7, 2, Course.TYPE_ALL, 4);
        addSample("企业法律风险管理", "", "中119", Calendar.SATURDAY, 7, 2, Course.TYPE_EVEN, 5);
        persist();
    }

    private void addSample(String name, String teacher, String loc, int day,
                           int start, int count, int weekType, int color) {
        Course c = new Course();
        c.id = nextId++;
        c.name = name;
        c.teacher = teacher;
        c.location = loc;
        c.day = day;
        c.startSection = start;
        c.sectionCount = count;
        c.weekType = weekType;
        c.weekStart = 1;
        c.weekEnd = 20;
        c.color = color;
        courses.add(c);
    }

    /** 按星期、节次排序的副本。 */
    public List<Course> sorted() {
        List<Course> list = all();
        list.sort(Comparator.comparingInt((Course c) -> c.day)
                .thenComparingInt(c -> c.startSection));
        return list;
    }

    // ---------------- 持久化 ----------------

    private void load() {
        courses.clear();
        String json = sp.getString("courses_json", null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    Course c = Course.fromJson(arr.getJSONObject(i));
                    courses.add(c);
                    nextId = Math.max(nextId, c.id + 1);
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        try {
            for (Course c : courses) arr.put(c.toJson());
        } catch (JSONException ignored) {
        }
        sp.edit().putString("courses_json", arr.toString()).putLong("next_id", nextId).apply();
    }

    // ---------------- 设置项 ----------------

    public Date getSemesterStart() {
        return com.xwt.schedule.util.WeekUtil.parse(sp.getString("semester_start", "2026-09-13"));
    }

    public void setSemesterStart(Date d) {
        sp.edit().putString("semester_start", com.xwt.schedule.util.WeekUtil.format(d)).apply();
    }

    /** 学期最后一天（第 totalWeeks 周的周六）。 */
    public Date getSemesterEnd() {
        Calendar c = com.xwt.schedule.util.WeekUtil.dateOf(this, getTotalWeeks(), 6);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    public String getSemesterName() {
        return sp.getString("semester_name", "2026-2027秋");
    }

    public void setSemesterName(String name) {
        sp.edit().putString("semester_name", name).apply();
    }

    /** 默认学期周数：一学期 16 周。 */
    public static final int DEFAULT_TOTAL_WEEKS = 16;

    public int getTotalWeeks() {
        return sp.getInt("total_weeks", DEFAULT_TOTAL_WEEKS);
    }

    public void setTotalWeeks(int w) {
        sp.edit().putInt("total_weeks", w).apply();
    }

    public boolean isReminderEnabled() {
        return sp.getBoolean("reminder_enabled", true);
    }

    public void setReminderEnabled(boolean b) {
        sp.edit().putBoolean("reminder_enabled", b).apply();
    }

    /** 提前多少分钟提醒。 */
    public int getReminderLeadMinutes() {
        return sp.getInt("reminder_lead", 15);
    }

    public void setReminderLeadMinutes(int m) {
        sp.edit().putInt("reminder_lead", m).apply();
    }

    // ---------------- 法定节假日 / 调休 ----------------

    /** 法定节假日是否自动停课（默认开启）。 */
    public boolean isHolidaySkipEnabled() {
        return sp.getBoolean("holiday_skip", true);
    }

    public void setHolidaySkipEnabled(boolean b) {
        sp.edit().putBoolean("holiday_skip", b).apply();
    }

    /** 调休上班日是否按课表上课（默认开启）。 */
    public boolean isMakeupEnabled() {
        return sp.getBoolean("makeup_enabled", true);
    }

    public void setMakeupEnabled(boolean b) {
        sp.edit().putBoolean("makeup_enabled", b).apply();
    }

    /** 用户对某个调休日的自定义值：{@link #MAKEUP_AUTO} / {@link #MAKEUP_OFF} / Calendar.DAY_OF_WEEK。 */
    public int getMakeupOverride(String date) {
        return sp.getInt("makeup_" + date, MAKEUP_AUTO);
    }

    public void setMakeupOverride(String date, int dayOfWeek) {
        if (dayOfWeek == MAKEUP_AUTO) {
            sp.edit().remove("makeup_" + date).apply();
        } else {
            sp.edit().putInt("makeup_" + date, dayOfWeek).apply();
        }
    }

    /** 清空全部调休自定义，恢复官方默认推导。 */
    public void clearMakeupOverrides() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) {
            if (k.startsWith("makeup_")) e.remove(k);
        }
        e.apply();
    }

    /**
     * 某个调休上班日实际按哪个星期上课：
     * 用户自定义优先，其次用官方数据推导的默认值，最后退回该日原本的星期。
     */
    public int resolveMakeupDayOfWeek(String date) {
        int v = getMakeupOverride(date);
        if (v != MAKEUP_AUTO) return v;
        return com.xwt.schedule.util.ChinaHoliday.defaultWorkAs(date);
    }

    // ---------------- 自动更新 ----------------

    /** 是否自动检查更新（默认开启，启动后每 24 小时最多检查一次）。 */
    public boolean isAutoUpdateCheckEnabled() {
        return sp.getBoolean("auto_update_check", true);
    }

    public void setAutoUpdateCheckEnabled(boolean b) {
        sp.edit().putBoolean("auto_update_check", b).apply();
    }

    /** 上次检查更新的时间戳，用于限制自动检查频率。 */
    public long getLastUpdateCheckTime() {
        return sp.getLong("last_update_check", 0L);
    }

    public void setLastUpdateCheckTime(long t) {
        sp.edit().putLong("last_update_check", t).apply();
    }

    /** 用户选择"忽略此版本"后记下的 versionCode，避免反复弹同一个提示。 */
    public int getIgnoredVersionCode() {
        return sp.getInt("ignored_version", 0);
    }

    public void setIgnoredVersionCode(int code) {
        sp.edit().putInt("ignored_version", code).apply();
    }
}
