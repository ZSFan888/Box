package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.ReleaseAssetSelector;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class AboutDialog extends BaseDialog {
    private static final String RELEASE_API = "https://api.github.com/repos/ZSFan888/Box/releases/latest";
    private static final String UPDATE_TAG = "tvbox_update";
    private static final String EXPECTED_CERT_SHA256 =
            "98BC507C922EE5AEA70D4C92C18CEB027E371133D8C2B24869E09CB2193A30FC";

    private final Context context;
    private final TextView status;
    private final TextView action;
    private final ProgressBar progress;
    private String downloadUrl;
    private String downloadName;
    private File downloadedApk;

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        this.context = context;
        setContentView(R.layout.dialog_about);
        status = findViewById(R.id.updateStatus);
        action = findViewById(R.id.updateAction);
        progress = findViewById(R.id.updateProgress);
        TextView version = findViewById(R.id.updateVersion);
        version.setText(context.getString(R.string.update_current,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                BuildConfig.UPDATE_ABI, BuildConfig.UPDATE_BRAND, BuildConfig.UPDATE_MODE));
        findViewById(R.id.updateClose).setOnClickListener(v -> dismiss());
        action.setOnClickListener(v -> {
            if (downloadedApk != null && downloadedApk.isFile()) {
                install(downloadedApk);
            } else if (downloadUrl != null) {
                downloadUpdate();
            } else {
                checkUpdate();
            }
        });
        setOnDismissListener(dialog -> OkGo.getInstance().cancelTag(UPDATE_TAG));
    }

    private void checkUpdate() {
        setBusy(true);
        status.setText(R.string.update_checking);
        OkGo.<String>get(RELEASE_API)
                .tag(UPDATE_TAG)
                .headers("Accept", "application/vnd.github+json")
                .headers("User-Agent", "TVBox-update-check")
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
                            String tagName = release.get("tag_name").getAsString();
                            int releaseCode = ReleaseAssetSelector.releaseVersionCode(tagName);
                            if (releaseCode < 0) throw new IllegalStateException("invalid release tag");
                            if (releaseCode <= BuildConfig.VERSION_CODE) {
                                status.setText(R.string.update_latest);
                                action.setText(R.string.update_check);
                                setBusy(false);
                                return;
                            }
                            JsonArray assets = release.getAsJsonArray("assets");
                            List<String> names = new ArrayList<>();
                            for (JsonElement element : assets) {
                                names.add(element.getAsJsonObject().get("name").getAsString());
                            }
                            String selected = ReleaseAssetSelector.selectAssetName(names,
                                    BuildConfig.UPDATE_ABI, BuildConfig.UPDATE_BRAND, BuildConfig.UPDATE_MODE);
                            if (selected == null) {
                                status.setText(context.getString(R.string.update_asset_missing,
                                        ReleaseAssetSelector.expectedAssetName(BuildConfig.UPDATE_ABI,
                                                BuildConfig.UPDATE_BRAND, BuildConfig.UPDATE_MODE)));
                                setBusy(false);
                                return;
                            }
                            for (JsonElement element : assets) {
                                JsonObject asset = element.getAsJsonObject();
                                if (selected.equals(asset.get("name").getAsString())) {
                                    downloadUrl = asset.get("browser_download_url").getAsString();
                                    downloadName = selected;
                                    break;
                                }
                            }
                            if (!ReleaseAssetSelector.isTrustedDownloadUrl(downloadUrl)) {
                                throw new SecurityException("untrusted download URL");
                            }
                            status.setText(context.getString(R.string.update_available, tagName));
                            action.setText(R.string.update_download);
                        } catch (Exception error) {
                            showError(error);
                        }
                        setBusy(false);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        showError(response.getException());
                        setBusy(false);
                    }
                });
    }

    private void downloadUpdate() {
        if (!ReleaseAssetSelector.isTrustedDownloadUrl(downloadUrl) || downloadName == null) {
            showError(new SecurityException("untrusted download URL"));
            return;
        }
        File updateDir = new File(context.getCacheDir(), "updates");
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            showError(new IllegalStateException("cannot create update cache"));
            return;
        }
        File previousDownload = new File(updateDir, downloadName);
        if (previousDownload.isFile() && !previousDownload.delete()) {
            showError(new IllegalStateException("cannot replace cached update"));
            return;
        }
        setBusy(true);
        progress.setVisibility(View.VISIBLE);
        OkGo.<File>get(downloadUrl).tag(UPDATE_TAG)
                .execute(new FileCallback(updateDir.getAbsolutePath(), downloadName) {
                    @Override
                    public void onSuccess(Response<File> response) {
                        progress.setVisibility(View.GONE);
                        setBusy(false);
                        try {
                            verifyPackage(response.body());
                            downloadedApk = response.body();
                            action.setText(R.string.update_install);
                            install(downloadedApk);
                        } catch (Exception error) {
                            downloadedApk = null;
                            if (response.body() != null) response.body().delete();
                            status.setText(R.string.update_signature_error);
                        }
                    }

                    @Override
                    public void onError(Response<File> response) {
                        progress.setVisibility(View.GONE);
                        setBusy(false);
                        showError(response.getException());
                    }

                    @Override
                    public void downloadProgress(Progress value) {
                        int percent = (int) (value.fraction * 100);
                        progress.setProgress(percent);
                        status.setText(context.getString(R.string.update_downloading, percent));
                    }
                });
    }

    private void verifyPackage(File apk) throws Exception {
        if (apk == null || !apk.isFile() || !apk.getName().endsWith(".apk")) {
            throw new SecurityException("invalid APK");
        }
        PackageManager manager = context.getPackageManager();
        PackageInfo archive = getPackageInfo(manager, apk.getAbsolutePath(), true);
        PackageInfo installed = getPackageInfo(manager, context.getPackageName(), false);
        if (archive == null || installed == null || !context.getPackageName().equals(archive.packageName)) {
            throw new SecurityException("package name mismatch");
        }
        long archiveVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode() : archive.versionCode;
        long installedVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? installed.getLongVersionCode() : installed.versionCode;
        if (archiveVersion <= installedVersion) {
            throw new SecurityException("APK is not newer than installed version");
        }
        String archiveCert = certificateSha256(archive);
        String installedCert = certificateSha256(installed);
        if (!EXPECTED_CERT_SHA256.equals(archiveCert)
                || !EXPECTED_CERT_SHA256.equals(installedCert)
                || !archiveCert.equals(installedCert)) {
            throw new SecurityException("certificate mismatch");
        }
    }

    @SuppressWarnings("deprecation")
    private PackageInfo getPackageInfo(PackageManager manager, String pathOrPackage, boolean archive) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return archive ? manager.getPackageArchiveInfo(pathOrPackage, flags)
                : getInstalledPackageInfo(manager, pathOrPackage, flags);
    }

    private PackageInfo getInstalledPackageInfo(PackageManager manager, String packageName, int flags) {
        try {
            return manager.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException error) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private String certificateSha256(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length != 1) throw new SecurityException("missing certificate");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) result.append(String.format("%02X", value & 0xFF));
        return result.toString();
    }

    private void install(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            status.setText(R.string.update_allow_install);
            action.setText(R.string.update_install);
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            try {
                context.startActivity(settings);
            } catch (Exception firstError) {
                try {
                    context.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                } catch (Exception secondError) {
                    showError(secondError);
                }
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception error) {
            showError(error);
        }
    }

    private void setBusy(boolean busy) {
        action.setEnabled(!busy);
        action.setAlpha(busy ? 0.5f : 1f);
    }

    private void showError(Throwable error) {
        String message = error == null || error.getMessage() == null ? "unknown error" : error.getMessage();
        status.setText(context.getString(R.string.update_error, message));
    }
}
