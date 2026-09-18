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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 下载新版本 apk 并拉起系统安装器。
 *
 * <p>apk 落在 {@code cacheDir/updates/}，由 AndroidManifest 中声明的 FileProvider 暴露给
 * 安装器——Android 7.0 起不允许直接传 {@code file://} URI。
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
                        fetchTo(url, tmp, progress, ui);
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

    /** 把单个地址的内容写到 dest，边写边回报进度。 */
    private static void fetchTo(String url, File dest, Progress progress, Handler ui)
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
        } finally {
            c.disconnect();
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
