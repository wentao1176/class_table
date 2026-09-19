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
 *
 * <h3>升级为什么不会丢课表</h3>
 * 数据存在本应用的 {@code SharedPreferences} 里，路径由<b>包名</b>决定。安装新版本 apk
 * 只是替换代码，不会碰这个目录，所以同包名、同签名的升级是原地覆盖，课表原样保留。
 * （唯一会丢数据的情况是签名不一致 —— 那 Android 会拒绝覆盖安装，必须先卸载，
 * 卸载才会清数据。所以发布用的签名密钥必须固定。）
 *
 * <p>另外示例课表只在<b>第一次安装</b>时写入一次，靠 {@code initialized} 标记守住：
 * 老用户升级后不会被示例数据覆盖。
 *
 * <h3>再加一层保险</h3>
 * 光靠上面的机制还不够 —— 万一某次写入异常或数据被写坏，用户打开就是空课表。
 * 所以这里做了三件事：
 * <ol>
 *   <li>每次落盘前把上一版留成备份（{@code courses_json_backup}）；</li>
 *   <li>启动时主数据解析失败就回退到备份，并把恢复出来的数据写回主存储；</li>
 *   <li>主数据读不出来时<b>绝不</b>把空列表覆盖上去，原始字节另行留档，
 *       免得把用户唯一的一份数据冲掉。</li>
 * </ol>
 */
public class CourseStore {

    private static final String PREF = "schedule_data";
    private static final String KEY_COURSES = "courses_json";
    /** 上一版课表数据，用于主数据损坏时回退。 */
    private static final String KEY_BACKUP = "courses_json_backup";
    /** 主数据解析失败时，把原始字节原样留档，便于人工找回。 */
    private static final String KEY_QUARANTINE = "courses_json_corrupt";
    /** 调休日自定义值：该调休日不上课。 */
    public static final int MAKEUP_OFF = -1;
    /** 调休日自定义值：未设置，使用官方数据推导出的默认星期。 */
    public static final int MAKEUP_AUTO = 0;

    private static CourseStore instance;

    private final SharedPreferences sp;
    private final List<Course> courses = new ArrayList<>();
    private long nextId = 1;
    /** 本次启动时主数据没能解析出来（此时 courses 里的内容不可信）。 */
    private boolean loadFailed = false;

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
        // 用户明确要求清空：备份和留档一起丢掉。
        // 这里刻意不走 persist() —— persist 会把"清空前的那一版"存回备份槽，
        // 下次启动就又被恢复了，等于删不掉。
        sp.edit()
                .remove(KEY_BACKUP)
                .remove(KEY_QUARANTINE)
                .putString(KEY_COURSES, "[]")
                .putLong("next_id", nextId)
                .apply();
        loadFailed = false;
    }

    public synchronized void loadSamples() {
        courses.clear();
        nextId = 1;
        // 参照截图的课表：彩色为每周上课，灰色（双周）在第 1 周不上、置灰显示。
        // 注意：周六、周日没有任何课程——周末本来就没课。原截图中周末出现的
        // 「人工智能导论 / 模式识别 / 企业法律风险管理」是调休当周被误录的重复项
        // （课程名、教室、节次与周一/周三的课完全一一对应），现已由
        // DayPlan + ChinaHoliday 按国家法定节假日规范自动推导，不再写进课表。
        addSample("跨文化交际", "", "中109", Calendar.TUESDAY, 1, 2, Course.TYPE_ALL, 0);
        addSample("操作系统", "", "复303", Calendar.THURSDAY, 1, 2, Course.TYPE_ALL, 1);
        addSample("操作系统", "", "中103", Calendar.FRIDAY, 1, 2, Course.TYPE_EVEN, 1);

        addSample("人工智能导论", "", "复302", Calendar.MONDAY, 3, 2, Course.TYPE_ALL, 2);
        addSample("操作系统", "", "复304", Calendar.TUESDAY, 3, 2, Course.TYPE_ALL, 1);
        addSample("模式识别", "", "复302", Calendar.WEDNESDAY, 3, 2, Course.TYPE_ALL, 3);
        addSample("多元统计分析与SPSS应用", "", "中110", Calendar.THURSDAY, 3, 2, Course.TYPE_ALL, 4);
        addSample("模式识别", "", "中310", Calendar.FRIDAY, 3, 2, Course.TYPE_EVEN, 3);

        addSample("模式识别", "", "复302", Calendar.MONDAY, 5, 2, Course.TYPE_ALL, 3);
        addSample("人工智能导论", "", "复302", Calendar.WEDNESDAY, 5, 2, Course.TYPE_ALL, 2);
        addSample("物理性污染控制工程", "", "中102", Calendar.THURSDAY, 5, 2, Course.TYPE_ALL, 7);
        addSample("习近平新时代中国特色社会主义思想概论", "", "复503", Calendar.FRIDAY, 5, 2, Course.TYPE_ALL, 6);

        addSample("企业法律风险管理", "", "中119", Calendar.WEDNESDAY, 7, 2, Course.TYPE_ALL, 5);
        addSample("人工智能导论", "", "中320", Calendar.THURSDAY, 7, 2, Course.TYPE_EVEN, 2);
        addSample("组织行为学", "", "研B204", Calendar.FRIDAY, 7, 2, Course.TYPE_ALL, 4);
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
        c.weekEnd = DEFAULT_TOTAL_WEEKS;
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
        loadFailed = false;
        String json = sp.getString(KEY_COURSES, null);
        if (json == null) return;              // 首次安装，交给 loadSamples()
        if (parseInto(json)) return;

        // 主数据坏了。先别急着让用户看到空课表 —— 试试上一版备份。
        loadFailed = true;
        String backup = sp.getString(KEY_BACKUP, null);
        if (backup != null && parseInto(backup)) {
            // 这里**故意**不把 loadFailed 清掉：让 persist() 走"坏数据留档"那条路，
            // 把损坏的原始字节挪进留档槽，而不是覆盖掉刚刚救回我们的那份备份。
            persist();
        }
    }

    /**
     * 解析 JSON 并替换内存里的课程表。
     * <p>只有整份数据都解析成功才替换 —— 半截数据比空数据更危险，会被当成"用户的课表"
     * 原样写回去，等于悄悄删掉了没解析出来的那部分。
     *
     * @return 解析成功返回 true
     */
    private boolean parseInto(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            List<Course> parsed = new ArrayList<>(arr.length());
            long maxId = 0;
            for (int i = 0; i < arr.length(); i++) {
                Course c = Course.fromJson(arr.getJSONObject(i));
                parsed.add(c);
                maxId = Math.max(maxId, c.id);
            }
            courses.clear();
            courses.addAll(parsed);
            nextId = maxId + 1;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        try {
            for (Course c : courses) arr.put(c.toJson());
        } catch (JSONException ignored) {
        }
        String next = arr.toString();

        SharedPreferences.Editor e = sp.edit();
        String prev = sp.getString(KEY_COURSES, null);
        if (loadFailed) {
            // 主数据本来就读不出来，现在又要落盘：把原始字节单独留档，
            // 同时**不要**冲掉可能还有用的上一版备份。
            if (prev != null) e.putString(KEY_QUARANTINE, prev);
        } else if (prev != null && !prev.equals(next)) {
            e.putString(KEY_BACKUP, prev);
        }
        e.putString(KEY_COURSES, next).putLong("next_id", nextId).apply();
        loadFailed = false;   // 已经落盘，内存与存储重新自洽
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

    /** 默认学期周数：一学期 16 周。基准常量见 {@link Course#DEFAULT_WEEK_END}。 */
    public static final int DEFAULT_TOTAL_WEEKS = Course.DEFAULT_WEEK_END;

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
