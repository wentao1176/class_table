package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xwt.schedule.update.UpdateChecker;
import com.xwt.schedule.update.UpdateInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/**
 * 更新清单的解析与版本比较。
 *
 * <p>用 Robolectric 跑而不是纯 JVM：{@code org.json} 属于 Android 框架的一部分，
 * 在普通本地单测里只有会抛 "not mocked" 的空壳实现，解析必然失败。
 * 不需要网络——坏清单、缺字段、非法 JSON 都靠纯字符串喂进去验证。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UpdateLogicTest {

    private static final String VALID = "{"
            + "\"versionCode\": 4,"
            + "\"versionName\": \"1.3\","
            + "\"apkUrl\": \"https://example.com/class_table_v1.3.apk\","
            + "\"notes\": \"修复周末灰色显示；学期改为16周\","
            + "\"forceUpdate\": true"
            + "}";

    @Test
    public void parsesAValidManifest() {
        UpdateInfo info = UpdateInfo.parse(VALID);
        assertNotNull(info);
        assertEquals(4, info.versionCode);
        assertEquals("1.3", info.versionName);
        assertEquals("https://example.com/class_table_v1.3.apk", info.primaryUrl());
        assertEquals(1, info.apkUrls.size());
        assertEquals("修复周末灰色显示；学期改为16周", info.notes);
        assertTrue(info.forceUpdate);
    }

    @Test
    public void parsesAnApkUrlsArrayInOrder() {
        UpdateInfo info = UpdateInfo.parse("{"
                + "\"versionCode\": 5,"
                + "\"apkUrls\": [\"https://cdn.example.com/a.apk\", \"\", \"https://raw.example.com/a.apk\"]"
                + "}");
        assertNotNull(info);
        // 空字符串会被丢掉，顺序保持不变，首个地址作为首选
        assertEquals(2, info.apkUrls.size());
        assertEquals("https://cdn.example.com/a.apk", info.primaryUrl());
        assertEquals("https://raw.example.com/a.apk", info.apkUrls.get(1));
    }

    @Test
    public void missingVersionCodeIsRejected() {
        assertNull(UpdateInfo.parse("{\"apkUrl\":\"https://example.com/a.apk\"}"));
    }

    @Test
    public void missingApkUrlIsRejected() {
        assertNull(UpdateInfo.parse("{\"versionCode\":4,\"versionName\":\"1.3\"}"));
    }

    @Test
    public void malformedJsonIsRejectedInsteadOfThrowing() {
        assertNull(UpdateInfo.parse("not json at all"));
        assertNull(UpdateInfo.parse("{\"versionCode\":"));
        assertNull(UpdateInfo.parse(""));
    }

    @Test
    public void nullInputIsRejected() {
        assertNull(UpdateInfo.parse(null));
    }

    @Test
    public void optionalFieldsFallBackToSaneDefaults() {
        UpdateInfo info = UpdateInfo.parse(
                "{\"versionCode\":7,\"apkUrl\":\"https://example.com/a.apk\"}");
        assertNotNull(info);
        assertEquals("7", info.versionName);   // 缺 versionName 时用 versionCode 兜底
        assertEquals("", info.notes);
        assertFalse(info.forceUpdate);         // 默认不强制更新
    }

    @Test
    public void versionComparisonUsesVersionCodeOnly() {
        UpdateInfo info = UpdateInfo.parse(VALID);   // versionCode = 4
        assertNotNull(info);
        assertTrue(info.isNewerThan(3));
        assertFalse(info.isNewerThan(4));
        assertFalse(info.isNewerThan(5));
    }

    @Test
    public void manifestUrlsAreMirrorsOfTheSameRepo() {
        String[] urls = UpdateChecker.manifestUrls();
        assertEquals(2, urls.length);
        for (String u : urls) {
            assertTrue("每个镜像地址都应指向同一个仓库: " + u,
                    u.contains(UpdateChecker.REPO) && u.endsWith("update.json"));
        }
        // jsDelivr 放在第一位：国内一般可直连，raw.githubusercontent 常被阻断
        assertTrue("首个地址应为 jsDelivr CDN", urls[0].contains("jsdelivr"));
    }

    // ---------------- 多镜像取最新 ----------------

    private static UpdateInfo manifest(int versionCode, String url) {
        UpdateInfo i = UpdateInfo.parse("{\"versionCode\":" + versionCode
                + ",\"apkUrl\":\"" + url + "\"}");
        assertNotNull(i);
        return i;
    }

    @Test
    public void newestManifestWinsWhenMirrorsDisagree() {
        // jsDelivr 命中旧缓存（v3），raw 已经是最新的（v4）—— 必须取 v4，
        // 否则用户会被告知"已是最新"而收不到更新。
        UpdateInfo stale = manifest(3, "https://cdn.example.com/v3.apk");
        UpdateInfo fresh = manifest(4, "https://raw.example.com/v4.apk");

        UpdateInfo best = UpdateChecker.pickNewest(Arrays.asList(stale, fresh));
        assertNotNull(best);
        assertEquals(4, best.versionCode);
        assertEquals("https://raw.example.com/v4.apk", best.primaryUrl());

        // 顺序反过来结果一样
        assertEquals(4, UpdateChecker.pickNewest(Arrays.asList(fresh, stale)).versionCode);
    }

    @Test
    public void newestManifestIsPickedRegardlessOfMirrorOrder() {
        assertEquals(9, UpdateChecker.pickNewest(Arrays.asList(
                manifest(7, "https://a/v7.apk"),
                manifest(9, "https://b/v9.apk"),
                manifest(8, "https://c/v8.apk"))).versionCode);
    }

    @Test
    public void pickNewestToleratesMissingMirrors() {
        assertNull(UpdateChecker.pickNewest(null));
        assertNull(UpdateChecker.pickNewest(Collections.emptyList()));
        // 只有一个镜像挂了（null）时，仍然要用活着的那份
        UpdateInfo only = manifest(6, "https://a/v6.apk");
        assertEquals(6, UpdateChecker.pickNewest(Arrays.asList(null, only, null)).versionCode);
    }

    @Test
    public void mirrorCachedOlderVersionStillReportsUpdate() {
        // 端到端语义：缓存到 v3 的镜像 + 拿到 v4 的镜像 → 对 v3 的用户应报有更新
        UpdateInfo best = UpdateChecker.pickNewest(Arrays.asList(
                manifest(3, "https://cdn.example.com/v3.apk"),
                manifest(4, "https://raw.example.com/v4.apk")));
        assertNotNull(best);
        assertTrue(best.isNewerThan(3));
    }
}
