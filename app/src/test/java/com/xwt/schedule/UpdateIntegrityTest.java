package com.xwt.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.xwt.schedule.update.UpdateInfo;
import com.xwt.schedule.update.UpdateInstaller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 更新包下载的完整性校验。
 *
 * <p>动机：原先下载只检查"字节数大于 0"。中途断流拿到的半个 apk 是非空的，
 * 于是会被当成成功，一路交给系统安装器，用户只看到一句
 * "解析软件包时出现问题" —— 既看不懂也无法自行恢复。
 * 现在这类坏包要在应用内就被识别出来，并自动换下一个镜像重试。
 *
 * <p>用 Robolectric 跑是因为 {@code UpdateInfo.parse} 依赖 {@code org.json}
 * （纯 JVM 下是 "not mocked" 空壳）；文件校验部分用临时文件，不需要网络。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UpdateIntegrityTest {

    // ---------------- 清单里的 size / sha256 ----------------

    @Test
    public void parsesSizeAndSha256() {
        UpdateInfo info = UpdateInfo.parse("{"
                + "\"versionCode\": 6,"
                + "\"versionName\": \"1.5\","
                + "\"apkUrl\": \"https://example.com/a.apk\","
                + "\"size\": 5594367,"
                + "\"sha256\": \"4fd8b61ec6c37676be94abddec96964f\""
                + "}");
        assertNotNull(info);
        assertEquals(5594367L, info.size);
        assertEquals("4fd8b61ec6c37676be94abddec96964f", info.sha256);
        assertTrue(info.hasIntegrity());
    }

    @Test
    public void sha256IsNormalisedToLowerCase() {
        // 发布时手抖写成大写也要能对上
        UpdateInfo info = UpdateInfo.parse("{"
                + "\"versionCode\": 6,\"apkUrl\": \"https://example.com/a.apk\","
                + "\"sha256\": \"4FD8B61EC6C37676BE94ABDDEC96964F\""
                + "}");
        assertNotNull(info);
        assertEquals("4fd8b61ec6c37676be94abddec96964f", info.sha256);
    }

    @Test
    public void integrityFieldsAreOptionalAndDefaultToAbsent() {
        UpdateInfo info = UpdateInfo.parse(
                "{\"versionCode\":6,\"apkUrl\":\"https://example.com/a.apk\"}");
        assertNotNull(info);
        assertEquals(0L, info.size);
        assertEquals("", info.sha256);
        assertFalse(info.hasIntegrity());
    }

    @Test
    public void negativeSizeIsTreatedAsAbsent() {
        UpdateInfo info = UpdateInfo.parse("{"
                + "\"versionCode\": 6,\"apkUrl\": \"https://example.com/a.apk\","
                + "\"size\": -5"
                + "}");
        assertNotNull(info);
        assertEquals(0L, info.size);
        assertFalse(info.hasIntegrity());
    }

    // ---------------- 下载后的完整性校验 ----------------

    /** 造一个以 zip 魔数开头、长度可控的假 apk。 */
    private static byte[] fakeApk(int length) {
        byte[] b = new byte[Math.max(4, length)];
        b[0] = 'P';
        b[1] = 'K';
        b[2] = 3;
        b[3] = 4;
        for (int i = 4; i < b.length; i++) b[i] = (byte) (i & 0xFF);
        return b;
    }

    private static File writeTemp(byte[] content) throws Exception {
        File f = File.createTempFile("ct-test", ".apk");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content);
        }
        return f;
    }

    @Test
    public void acceptsACompleteApk() throws Exception {
        byte[] data = fakeApk(4096);
        File f = writeTemp(data);
        String sha = UpdateInstaller.sha256(f);
        assertNotNull(sha);

        assertNull(UpdateInstaller.verify(f, data.length, data.length, sha));
    }

    @Test
    public void rejectsATruncatedDownload() throws Exception {
        // 服务器说 5MB，实际只落了 1KB —— 断流的典型形态。
        // 注意这个文件是"非空"的，旧逻辑发现不了。
        File f = writeTemp(fakeApk(1024));
        String why = UpdateInstaller.verify(f, 5_594_367L, 0L, "");
        assertNotNull("截断的下载必须被拒绝", why);
        assertTrue(why, why.contains("不完整"));
    }

    @Test
    public void rejectsASizeMismatchAgainstTheManifest() throws Exception {
        File f = writeTemp(fakeApk(2048));
        String why = UpdateInstaller.verify(f, -1L, 4096L, "");
        assertNotNull(why);
        assertTrue(why, why.contains("大小不符"));
    }

    @Test
    public void rejectsAnHtmlErrorPageServedWithHttp200() throws Exception {
        // 运营商/门户劫持、CDN 出错时常见的形态：HTTP 200 但正文是 HTML
        File f = writeTemp("<html><body>404 Not Found</body></html>".getBytes("UTF-8"));
        String why = UpdateInstaller.verify(f, -1L, 0L, "");
        assertNotNull(why);
        assertTrue(why, why.contains("不是有效的安装包"));
    }

    @Test
    public void rejectsAContentMismatchCaughtBySha256() throws Exception {
        byte[] good = fakeApk(4096);
        File goodFile = writeTemp(good);
        String goodSha = UpdateInstaller.sha256(goodFile);

        // 长度对得上，但内容被改动过（静默损坏 / 中间人替换）
        byte[] tampered = fakeApk(4096);
        tampered[100] ^= 0x7F;
        File tamperedFile = writeTemp(tampered);

        assertNull("对照组应通过", UpdateInstaller.verify(goodFile, 4096, 4096, goodSha));
        String why = UpdateInstaller.verify(tamperedFile, 4096, 4096, goodSha);
        assertNotNull(why);
        assertTrue(why, why.contains("sha256"));
    }

    @Test
    public void rejectsAnEmptyOrMissingFile() throws Exception {
        File empty = writeTemp(new byte[0]);
        assertEquals("下载内容为空", UpdateInstaller.verify(empty, -1L, 0L, ""));
        assertEquals("安装包文件不存在",
                UpdateInstaller.verify(new File(empty.getParent(), "no-such-file.apk"),
                        -1L, 0L, ""));
        assertNotNull(UpdateInstaller.verify(null, -1L, 0L, ""));
    }

    @Test
    public void passesOnMagicBytesAloneWhenNothingElseIsKnown() throws Exception {
        // 清单没给 size/sha256，服务器也没给 Content-Length —— 只能靠魔数，
        // 这仍然挡得住 HTML 错误页，比旧逻辑强。
        File f = writeTemp(fakeApk(777));
        assertNull(UpdateInstaller.verify(f, -1L, 0L, ""));
    }

    // ---------------- sha256 实现本身 ----------------

    @Test
    public void sha256MatchesTheKnownDigestOfAbc() throws Exception {
        File f = File.createTempFile("ct-abc", ".bin");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("abc".getBytes("UTF-8"));
        }
        // 标准测试向量；同时验证是 64 位小写 hex（补零正确）
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                UpdateInstaller.sha256(f));
    }
}
