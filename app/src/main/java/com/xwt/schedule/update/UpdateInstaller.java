package com.xwt.schedule.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 下载新版本 apk 并拉起系统安装器。
 *
 * <p>apk 落在 {@code cacheDir/updates/}，由 AndroidManifest 中声明的 FileProvider 暴露给
 * 安装器——Android 7.0 起不允许直接传 {@code file://} URI。
 *
 * <p>下载完成后会做完整性校验（见 {@link #verify}）：坏包在这里就被拦下并自动换下一个
 * 镜像重试，而不是交给系统安装器去报一句用户看不懂的"解析软件包时出现问题"。
 */
public class UpdateInstaller {

    private static final String DIR_NAME = "updates";
    private static final int TIMEOUT_MS = 15000;

    public interface Progress {
        /** percent 为 0..100；总长度未知时回调 -1。 */
        void onProgress(int percent);
    }

    public interface Result {
        void onSuccess(File apk);

        void onFailed(String reason);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private UpdateInstaller() {
    }

    /** 见 UpdateChecker.main()：避免静态初始化时就去取主线程 Looper。 */
    private static Handler main() {
        return new Handler(Looper.getMainLooper());
    }

    /** FileProvider 的 authority，与 AndroidManifest 中保持一致。 */
    public static String authority(Context ctx) {
        return ctx.getPackageName() + ".fileprovider";
    }

    private static File updatesDir(Context ctx) {
        File d = new File(ctx.getCacheDir(), DIR_NAME);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 后台下载 apk，进度与结果都切回主线程。地址按 {@link UpdateInfo#apkUrls} 顺序重试。 */
    public static void download(Context ctx, final UpdateInfo info,
                                final Progress progress, final Result result) {
        final Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            final Handler ui = main();
            File tmp = null;
            String lastError = null;
            try {
                File dir = updatesDir(app);
                // 清掉上一次遗留的安装包，避免 cache 目录越滚越大
                File[] old = dir.listFiles();
                if (old != null) {
                    for (File f : old) f.delete();
                }
                File apk = new File(dir, "class_table_" + info.versionName + ".apk");
                tmp = new File(dir, apk.getName() + ".part");

                for (String url : info.apkUrls) {
                    try {
                        long declared = fetchTo(url, tmp, progress, ui);
                        // 坏包要在这里拦下来：否则会一路交给系统安装器，
                        // 用户只会看到一句"解析软件包时出现问题"，完全不知道发生了什么。
                        // 抛异常 → 落到 catch → 自动换下一个镜像重试。
                        String bad = verify(tmp, declared, info.size, info.sha256);
                        if (bad != null) throw new IllegalStateException(bad);
                        if (!tmp.renameTo(apk)) throw new IllegalStateException("无法保存安装包");
                        tmp = null;
                        final File f = apk;
                        ui.post(() -> result.onSuccess(f));
                        return;
                    } catch (Exception e) {
                        String m = e.getMessage();
                        lastError = e.getClass().getSimpleName() + (m == null ? "" : ": " + m);
                        if (tmp != null) tmp.delete();
                    }
                }
                throw new IllegalStateException(
                        lastError == null ? "没有可用的下载地址" : lastError);
            } catch (Exception e) {
                if (tmp != null) tmp.delete();
                String m = e.getMessage();
                final String reason = m == null ? e.getClass().getSimpleName() : m;
                ui.post(() -> result.onFailed(reason));
            }
        });
    }

    /**
     * 把单个地址的内容写到 dest，边写边回报进度。
     *
     * @return 服务器声明的 Content-Length；未知时返回 -1。
     */
    private static long fetchTo(String url, File dest, Progress progress, Handler ui)
            throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "ClassTable-Updater");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);

            long total = c.getContentLengthLong();
            long done = 0;
            int lastPercent = -2;
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int p = (int) (done * 100 / total);
                        if (p != lastPercent) {
                            lastPercent = p;
                            final int fp = p;
                            ui.post(() -> progress.onProgress(fp));
                        }
                    }
                }
                out.flush();
            }
            if (done <= 0) throw new IllegalStateException("下载内容为空");
            return total;
        } finally {
            c.disconnect();
        }
    }

    // ---------------- 完整性校验 ----------------

    /**
     * 校验下载下来的安装包是否完整可信。
     *
     * <p>返回 {@code null} 表示通过，否则返回一句可直接展示给用户的中文原因。
     * 抽成静态纯函数是为了能在单测里直接喂临时文件验证，不需要联网。
     *
     * <p>三层校验，从便宜到昂贵：
     * <ol>
     *   <li>字节数：HTTP 头声明了 Content-Length 就必须一字不差 —— 中途断流是最常见的
     *       下载失败，而它拿到的文件"非空"，光靠"字节数大于 0"是发现不了的；</li>
     *   <li>magic bytes：APK 本质是 zip，开头必须是 {@code PK}。用来挡住 200 状态下
     *       返回的 HTML 错误页 / 运营商劫持页；</li>
     *   <li>sha256：清单里给了就逐个字节核对，能挡下任何形式的静默损坏。</li>
     * </ol>
     *
     * @param declaredLength 服务器声明的长度，-1 或 0 表示未知
     * @param expectedSize   清单里的 size，0 表示未提供
     * @param expectedSha256 清单里的 sha256（大小写不敏感），空表示未提供
     */
    public static String verify(File apk, long declaredLength, long expectedSize, String expectedSha256) {
        if (apk == null || !apk.isFile()) return "安装包文件不存在";
        long len = apk.length();
        if (len <= 0) return "下载内容为空";
        if (declaredLength > 0 && len != declaredLength) {
            return "下载不完整（" + len + "/" + declaredLength + " 字节），请重试";
        }
        if (expectedSize > 0 && len != expectedSize) {
            return "安装包大小不符（" + len + "/" + expectedSize + " 字节），请重试";
        }
        if (!looksLikeZip(apk)) return "下载内容不是有效的安装包，请重试";
        if (expectedSha256 != null && !expectedSha256.isEmpty()) {
            String actual = sha256(apk);
            if (actual == null) return "无法校验安装包完整性";
            if (!actual.equalsIgnoreCase(expectedSha256.trim())) {
                return "安装包校验失败（sha256 不符），请重试";
            }
        }
        return null;
    }

    /** APK 是 zip 容器，开头固定为 PK（普通 zip 是 PK\x03\x04，空 zip 是 PK\x05\x06）。 */
    public static boolean looksLikeZip(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] head = new byte[4];
            int read = 0;
            while (read < head.length) {
                int n = in.read(head, read, head.length - read);
                if (n <= 0) break;
                read += n;
            }
            return read == head.length
                    && head[0] == 'P' && head[1] == 'K';
        } catch (Exception e) {
            return false;
        }
    }

    /** 文件的 SHA-256（小写 hex）；失败返回 null。 */
    public static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : md.digest()) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 拉起系统安装界面。 */
    public static boolean install(Context ctx, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, authority(ctx), apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Android 8.0 起需要用户显式允许"安装未知应用"。 */
    public static boolean canInstallPackages(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ctx.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /** 跳到本应用的"安装未知应用"授权页。 */
    public static void openInstallPermissionSettings(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }
}
