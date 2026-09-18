package com.xwt.schedule.update;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GitHub 仓库根目录 update.json 的解析结果。
 *
 * <p>该文件随版本一起提交，形如：
 * <pre>
 * {
 *   "versionCode": 4,
 *   "versionName": "1.3",
 *   "apkUrls": [
 *     "https://cdn.jsdelivr.net/gh/wentao1176/class_table@main/apk/class_table_v1.3.apk",
 *     "https://raw.githubusercontent.com/wentao1176/class_table/main/apk/class_table_v1.3.apk"
 *   ],
 *   "notes": "修复……；新增……",
 *   "forceUpdate": false
 * }
 * </pre>
 *
 * <p>也接受单个 {@code "apkUrl": "..."} 字符串写法。多个下载地址会按顺序尝试，
 * 因为 GitHub 直链在国内时通时不通，留个镜像能显著提高更新成功率。
 *
 * <p>本类不依赖 Android API（org.json 除外），便于单测覆盖解析与版本比较逻辑。
 */
public class UpdateInfo {

    /** 远程清单里的 versionCode，与 app/build.gradle 中的 versionCode 对应。 */
    public final int versionCode;
    /** 展示用的版本名，如 "1.3"。 */
    public final String versionName;
    /** 新版本 apk 的下载地址，按顺序尝试。 */
    public final List<String> apkUrls;
    /** 更新说明，显示在更新对话框里。 */
    public final String notes;
    /** 为 true 时不提供"以后再说"，强制更新。 */
    public final boolean forceUpdate;

    public UpdateInfo(int versionCode, String versionName, List<String> apkUrls,
                      String notes, boolean forceUpdate) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrls = Collections.unmodifiableList(new ArrayList<>(apkUrls));
        this.notes = notes;
        this.forceUpdate = forceUpdate;
    }

    /** 首选下载地址；apkUrls 保证非空。 */
    public String primaryUrl() {
        return apkUrls.get(0);
    }

    /**
     * 解析版本清单。缺关键字段或 JSON 非法时返回 {@code null}，
     * 让调用方可以安全地忽略一个坏掉的清单，而不是崩溃。
     */
    public static UpdateInfo parse(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json.trim());
            int code = o.optInt("versionCode", 0);
            if (code <= 0) return null;

            List<String> urls = new ArrayList<>();
            JSONArray arr = o.optJSONArray("apkUrls");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String u = arr.optString(i, "").trim();
                    if (!u.isEmpty()) urls.add(u);
                }
            }
            if (urls.isEmpty()) {
                String single = o.optString("apkUrl", "").trim();
                if (!single.isEmpty()) urls.add(single);
            }
            if (urls.isEmpty()) return null;

            String name = o.optString("versionName", "").trim();
            if (name.isEmpty()) name = String.valueOf(code);

            return new UpdateInfo(code, name, urls,
                    o.optString("notes", "").trim(),
                    o.optBoolean("forceUpdate", false));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isNewerThan(int currentVersionCode) {
        return versionCode > currentVersionCode;
    }
}
