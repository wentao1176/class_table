package com.xwt.schedule.update;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
 *   "forceUpdate": false,
 *   "size": 5594367,
 *   "sha256": "4fd8b61e...（小写 hex）"
 * }
 * </pre>
 *
 * <p>也接受单个 {@code "apkUrl": "..."} 字符串写法。多个下载地址会按顺序尝试，
 * 因为 GitHub 直链在国内时通时不通，留个镜像能显著提高更新成功率。
 *
 * <p>{@code size} 与 {@code sha256} 是可选的，但**强烈建议填**：下载中途断流时，
 * 只靠"字节数大于 0"是发现不了的，装到系统安装器那一步才会报
 * "解析软件包时出现问题"，用户根本不知道发生了什么。填了之后客户端能当场识别出
 * 坏包并自动换下一个镜像重试。
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
    /** apk 的期望字节数；0 表示清单未提供。 */
    public final long size;
    /** apk 的期望 SHA-256（小写 hex）；"" 表示清单未提供。 */
    public final String sha256;

    public UpdateInfo(int versionCode, String versionName, List<String> apkUrls,
                      String notes, boolean forceUpdate) {
        this(versionCode, versionName, apkUrls, notes, forceUpdate, 0L, "");
    }

    public UpdateInfo(int versionCode, String versionName, List<String> apkUrls,
                      String notes, boolean forceUpdate, long size, String sha256) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrls = Collections.unmodifiableList(new ArrayList<>(apkUrls));
        this.notes = notes;
        this.forceUpdate = forceUpdate;
        this.size = size;
        this.sha256 = sha256 == null ? "" : sha256;
    }

    /** 清单是否带了完整性信息（size / sha256 至少有一个）。 */
    public boolean hasIntegrity() {
        return size > 0 || !sha256.isEmpty();
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

            long size = o.optLong("size", 0L);
            if (size < 0) size = 0L;
            // 统一成小写，比较时才能用简单的 equals
            String sha = o.optString("sha256", "").trim().toLowerCase(Locale.ROOT);

            return new UpdateInfo(code, name, urls,
                    o.optString("notes", "").trim(),
                    o.optBoolean("forceUpdate", false),
                    size, sha);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isNewerThan(int currentVersionCode) {
        return versionCode > currentVersionCode;
    }
}
