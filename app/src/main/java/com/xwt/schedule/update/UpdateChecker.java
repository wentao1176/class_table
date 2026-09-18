package com.xwt.schedule.update;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 读取 GitHub 仓库上的版本清单，判断是否有新版本。
 *
 * <p>清单只有几 KB，所以依次尝试若干镜像地址，任一成功即结束：
 * <ol>
 *   <li>jsDelivr CDN —— 国内一般可直连；</li>
 *   <li>raw.githubusercontent.com —— 兜底，但国内常被阻断。</li>
 * </ol>
 *
 * <p>注意 jsDelivr 对分支引用有缓存（约数小时），刚推送的新版本可能延迟可见。
 */
public class UpdateChecker {

    /** 版本清单所在仓库，与 git remote 保持一致。 */
    public static final String REPO = "wentao1176/class_table";
    public static final String BRANCH = "main";
    public static final String MANIFEST_PATH = "update.json";

    private static final String[] MANIFEST_URLS = {
            "https://cdn.jsdelivr.net/gh/" + REPO + "@" + BRANCH + "/" + MANIFEST_PATH,
            "https://raw.githubusercontent.com/" + REPO + "/" + BRANCH + "/" + MANIFEST_PATH,
    };

    private static final int TIMEOUT_MS = 8000;

    public interface Callback {
        void onUpdateAvailable(UpdateInfo info);

        void onUpToDate(int currentVersionCode);

        /** 所有镜像都失败时回调，reason 用于给用户一个可读的提示。 */
        void onFailed(String reason);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    /**
     * 主线程 Handler。刻意做成方法而不是静态字段：静态字段会在类第一次被加载时就调用
     * {@code Looper.getMainLooper()}，在纯 JVM 单测里会拿到 null 而抛异常。
     */
    private static Handler main() {
        return new Handler(Looper.getMainLooper());
    }

    private UpdateChecker() {
    }

    /** 依次尝试的清单地址，暴露出来便于单测校验。 */
    public static String[] manifestUrls() {
        return MANIFEST_URLS.clone();
    }

    /** 后台检查更新，回调统一切回主线程。 */
    public static void check(final int currentVersionCode, final Callback cb) {
        POOL.execute(() -> {
            final Handler ui = main();
            String lastError = null;
            for (String url : MANIFEST_URLS) {
                try {
                    UpdateInfo info = UpdateInfo.parse(httpGet(url));
                    if (info == null) {
                        lastError = "版本清单格式不正确";
                        continue;
                    }
                    final UpdateInfo found = info;
                    ui.post(() -> {
                        if (found.isNewerThan(currentVersionCode)) cb.onUpdateAvailable(found);
                        else cb.onUpToDate(currentVersionCode);
                    });
                    return;
                } catch (Exception e) {
                    String m = e.getMessage();
                    lastError = e.getClass().getSimpleName() + (m == null ? "" : ": " + m);
                }
            }
            final String reason = lastError == null ? "无法获取版本清单" : lastError;
            ui.post(() -> cb.onFailed(reason));
        });
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "ClassTable-Updater");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
