package com.xwt.schedule.ui;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xwt.schedule.update.UpdateInfo;
import com.xwt.schedule.update.UpdateInstaller;

import java.io.File;

/**
 * 更新相关的对话框。自动检查（启动时）和手动检查（设置页）共用同一套 UI，
 * 避免两处逻辑各写一遍导致行为不一致。
 */
public final class UpdateDialogs {

    private UpdateDialogs() {
    }

    private static int dp(Context ctx, float v) {
        return (int) (ctx.getResources().getDisplayMetrics().density * v);
    }

    /** 有可用更新。onIgnore 在用户选择"忽略此版本"时回调。 */
    public static void showAvailable(final Context ctx, final UpdateInfo info, final Runnable onIgnore) {
        StringBuilder msg = new StringBuilder();
        msg.append("发现新版本 v").append(info.versionName).append("，当前版本 v")
                .append(com.xwt.schedule.BuildConfig.VERSION_NAME).append("。");
        if (!info.notes.isEmpty()) {
            msg.append("\n\n").append(info.notes);
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx)
                .setTitle("发现新版本")
                .setMessage(msg.toString())
                .setPositiveButton("立即更新", (d, w) -> {
                    if (ctx instanceof Activity) {
                        startDownloadAndInstall((Activity) ctx, info);
                    }
                });

        if (!info.forceUpdate) {
            b.setNegativeButton("以后再说", null);
            b.setNeutralButton("忽略此版本", (d, w) -> {
                if (onIgnore != null) onIgnore.run();
            });
        }
        b.show();
    }

    public static void showUpToDate(Context ctx) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("检查更新")
                .setMessage("当前已是最新版本 v" + com.xwt.schedule.BuildConfig.VERSION_NAME + "。")
                .setPositiveButton("好", null)
                .show();
    }

    public static void showCheckFailed(Context ctx, String reason) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("检查更新失败")
                .setMessage("无法获取版本信息，请检查网络后重试。\n\n" + reason)
                .setPositiveButton("好", null)
                .show();
    }

    /** 带进度条地下载 apk，完成后拉起系统安装器。 */
    public static void startDownloadAndInstall(final Activity act, final UpdateInfo info) {
        final ProgressBar bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);

        int pad = dp(act, 24);
        FrameLayout wrap = new FrameLayout(act);
        wrap.setPadding(pad, pad / 2, pad, pad / 2);
        wrap.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog dlg = new MaterialAlertDialogBuilder(act)
                .setTitle("正在下载 v" + info.versionName)
                .setCancelable(false)
                .setView(wrap)
                .create();
        dlg.show();

        UpdateInstaller.download(act, info,
                percent -> {
                    if (percent >= 0) bar.setProgress(percent);
                },
                new UpdateInstaller.Result() {
                    @Override
                    public void onSuccess(File apk) {
                        dlg.dismiss();
                        afterDownload(act, apk);
                    }

                    @Override
                    public void onFailed(String reason) {
                        dlg.dismiss();
                        new MaterialAlertDialogBuilder(act)
                                .setTitle("下载失败")
                                .setMessage("更新包下载失败，请稍后重试。\n\n" + reason)
                                .setPositiveButton("好", null)
                                .show();
                    }
                });
    }

    private static void afterDownload(final Activity act, final File apk) {
        if (!UpdateInstaller.canInstallPackages(act)) {
            new MaterialAlertDialogBuilder(act)
                    .setTitle("需要安装权限")
                    .setMessage("系统默认禁止安装来自未知来源的应用。请在本应用的设置里允许"
                            + "\"安装未知应用\"，然后重新点击更新。")
                    .setPositiveButton("去授权", (d, w) -> UpdateInstaller.openInstallPermissionSettings(act))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        if (!UpdateInstaller.install(act, apk)) {
            new MaterialAlertDialogBuilder(act)
                    .setTitle("无法安装")
                    .setMessage("安装包已下载完成，但无法拉起系统安装器。\n\n位置：" + apk.getAbsolutePath())
                    .setPositiveButton("好", null)
                    .show();
        }
    }
}
