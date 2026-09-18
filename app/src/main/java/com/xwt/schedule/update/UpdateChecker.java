package com.xwt.schedule.update;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 读取 GitHub 仓库上的版本清单，判断是否有新版本。
 *
 * <p>清单只有几百字节，所以<b>并行</b>拉取全部镜像地址，取其中 {@code versionCode} 最大的一份：
 * <ol>
 *   <li>jsDelivr CDN —— 国内一般可直连；</li>
 *   <li>raw.githubusercontent.com —— 兜底，但国内常被阻断。</li>
 * </ol>
 *
 * <p><b>为什么要取最大值而不是"第一个成功的"</b>：jsDelivr 对分支引用有缓存（最长约 12 小时），
 * 刚推送的新版本可能延迟可见；而 raw 是近实时的但可能被墙。只认第一个成功的，就会在
 * 「jsDelivr 命中旧缓存」时误报"已是最新"。取最大值可以让两个镜像互相纠正。
 * 发布新版本时另外调用一次 jsDelivr purge 接口，把缓存立刻刷掉，见 README。
 *
 * <p>两个镜像并行拉取，整体耗时约等于单个镜像的超时时间，不会翻倍。
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

    /**
     * 从若干份清单里挑出 {@code versionCode} 最大的那份，全部为 null 时返回 null。
     * 抽成纯函数是为了能在单测里直接验证「镜像缓存不一致时取最新」这条规则。
     */
    public static UpdateInfo pickNewest(List<UpdateInfo> candidates) {
        UpdateInfo best = null;
        if (candidates == null) return null;
        for (UpdateInfo info : candidates) {
            if (info == null) continue;
            if (best == null || info.versionCode > best.versionCode) best = info;
        }
        return best;
    }

    /** 后台检查更新，回调统一切回主线程。 */
    public static void check(final int currentVersionCode, final Callback cb) {
        POOL.execute(() -> {
            final Handler ui = main();

            // 并行拉取所有镜像
            List<Future<UpdateInfo>> futures = new ArrayList<>();
            ExecutorService fan = Executors.newFixedThreadPool(MANIFEST_URLS.length);
            try {
                for (final String url : MANIFEST_URLS) {
                    futures.add(fan.submit(() -> UpdateInfo.parse(httpGet(url))));
                }
            } catch (Exception ignored) {
                // 提交失败不应影响已提交的任务
            }

            List<UpdateInfo> got = new ArrayList<>();
            String lastError = null;
            for (Future<UpdateInfo> f : futures) {
                try {
                    UpdateInfo info = f.get(TIMEOUT_MS + 2000L, TimeUnit.MILLISECONDS);
                    if (info == null) {
                        lastError = "版本清单格式不正确";
                    } else {
                        got.add(info);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    String m = cause.getMessage();
                    lastError = cause.getClass().getSimpleName() + (m == null ? "" : ": " + m);
                }
            }
            fan.shutdownNow();

            final UpdateInfo found = pickNewest(got);
            if (found == null) {
                final String reason = lastError == null ? "无法获取版本清单" : lastError;
                ui.post(() -> cb.onFailed(reason));
                return;
            }
            ui.post(() -> {
                if (found.isNewerThan(currentVersionCode)) cb.onUpdateAvailable(found);
                else cb.onUpToDate(currentVersionCode);
            });
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
