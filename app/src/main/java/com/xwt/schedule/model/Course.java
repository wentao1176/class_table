package com.xwt.schedule.model;

import com.xwt.schedule.util.TimeTable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * 一门课程。
 * day：上课星期，遵循 {@link Calendar#DAY_OF_WEEK}，1=周日 … 7=周六。
 * startSection：起始节次（1..11）；sectionCount：连排节数。
 * weekType：0=每周（区间内），1=单周，2=双周，3=自定义周次。
 */
public class Course {

    public static final int TYPE_ALL = 0;
    public static final int TYPE_ODD = 1;
    public static final int TYPE_EVEN = 2;
    public static final int TYPE_CUSTOM = 3;

    /** 默认学期周数（一学期 16 周）。这是全局唯一的周数基准，{@code CourseStore} 也引用它。 */
    public static final int DEFAULT_WEEK_END = 16;

    public long id;
    public String name = "";
    public String teacher = "";
    public String location = "";
    public int day = Calendar.MONDAY;
    public int startSection = 1;
    public int sectionCount = 2;
    public int weekType = TYPE_ALL;
    public int weekStart = 1;
    public int weekEnd = DEFAULT_WEEK_END;
    public List<Integer> customWeeks = new ArrayList<>();
    public int color = 0;

    public int endSection() {
        return Math.min(TimeTable.SECTIONS, startSection + sectionCount - 1);
    }

    /** 该课程在指定周次是否上课。 */
    public boolean occursInWeek(int week) {
        if (week < weekStart || week > weekEnd) return false;
        switch (weekType) {
            case TYPE_ODD:
                return week % 2 == 1;
            case TYPE_EVEN:
                return week % 2 == 0;
            case TYPE_CUSTOM:
                return customWeeks.contains(week);
            case TYPE_ALL:
            default:
                return true;
        }
    }

    public String weekdayText() {
        return TimeTable.WEEKDAY_SHORT[day - 1];
    }

    public String sectionText() {
        return TimeTable.rangeText(startSection, sectionCount);
    }

    /** 周次的人类可读描述，如 “1-16周”“单周 1-16”“第2,4,6周”。 */
    public String weekText(int totalWeeks) {
        String range;
        if (weekStart == 1 && weekEnd >= totalWeeks) {
            range = "";
        } else {
            range = weekStart + "-" + weekEnd + "周 ";
        }
        switch (weekType) {
            case TYPE_ODD:
                return range + "单周";
            case TYPE_EVEN:
                return range + "双周";
            case TYPE_CUSTOM: {
                List<Integer> sorted = new ArrayList<>(customWeeks);
                Collections.sort(sorted);
                StringBuilder sb = new StringBuilder("第");
                for (int i = 0; i < sorted.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(sorted.get(i));
                }
                sb.append("周");
                return sb.toString();
            }
            case TYPE_ALL:
            default:
                return range.isEmpty() ? "每周" : range.trim();
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("teacher", teacher);
        o.put("location", location);
        o.put("day", day);
        o.put("start", startSection);
        o.put("count", sectionCount);
        o.put("weekType", weekType);
        o.put("weekStart", weekStart);
        o.put("weekEnd", weekEnd);
        o.put("color", color);
        JSONArray arr = new JSONArray();
        for (int w : customWeeks) arr.put(w);
        o.put("customWeeks", arr);
        return o;
    }

    public static Course fromJson(JSONObject o) {
        Course c = new Course();
        c.id = o.optLong("id");
        c.name = o.optString("name", "");
        c.teacher = o.optString("teacher", "");
        c.location = o.optString("location", "");
        c.day = o.optInt("day", Calendar.MONDAY);
        c.startSection = o.optInt("start", 1);
        c.sectionCount = o.optInt("count", 2);
        c.weekType = o.optInt("weekType", TYPE_ALL);
        c.weekStart = o.optInt("weekStart", 1);
        c.weekEnd = o.optInt("weekEnd", DEFAULT_WEEK_END);
        c.color = o.optInt("color", 0);
        JSONArray arr = o.optJSONArray("customWeeks");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) c.customWeeks.add(arr.optInt(i));
        }
        return c;
    }
}
