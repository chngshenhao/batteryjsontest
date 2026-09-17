package com.example.batteryjsontest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks an internal server for a newer APK and prompts the user to install it.
 *
 * Server layout (example):
 *   http://bkn1atm03s100/pda-agent/version.json
 *   http://bkn1atm03s100/pda-agent/pda-agent.apk
 */
public final class AppUpdateChecker {

    private static final String TAG = "AppUpdateChecker";

    /** Change this if your IIS / share path is different. */
    public static final String UPDATE_BASE_URL = "http://bkn1atm03s100/pda-agent/";
    public static final String VERSION_JSON_URL = UPDATE_BASE_URL + "version.json";

    private static final String PREF = "app_update_prefs";
    private static final String KEY_LAST_CHECK_MS = "last_check_ms";
    private static final String KEY_INSTALL_PROMPT_SHOWN = "install_prompt_shown";
    private static final long CHECK_INTERVAL_MS = 30 * 60_000L; // 30 minutes

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IN_PROGRESS = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppUpdateChecker() {}

    /**
     * On first launch (or whenever permission is still missing), ask the user
     * to allow "Install unknown apps" for PDA Agent.
     * Android does not show this automatically — the app must open Settings.
     */
    public static void ensureInstallPermission(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (activity.getPackageManager().canRequestPackageInstalls()) return;

        SharedPreferences sp = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        // Still re-prompt if permission was never granted (even after first dialog)
        boolean alreadyAsked = sp.getBoolean(KEY_INSTALL_PROMPT_SHOWN, false);

        Runnable openSettings = () -> {
            sp.edit().putBoolean(KEY_INSTALL_PROMPT_SHOWN, true).apply();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Cannot open unknown-apps settings: " + e.getMessage(), e);
                Toast.makeText(activity, R.string.update_allow_unknown, Toast.LENGTH_LONG).show();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.install_permission_title)
                .setMessage(R.string.install_permission_message)
                .setCancelable(alreadyAsked)
                .setPositiveButton(R.string.install_permission_open_settings, (d, w) -> openSettings.run());

        if (alreadyAsked) {
            builder.setNegativeButton(R.string.update_later, null);
        }

        builder.show();
    }

    /** Quiet check (throttled). Safe to call on every app open / Wi‑Fi connect. */
    public static void check(Activity activity) {
        check(activity, false);
    }

    /**
     * @param force if true, ignore throttle and toast when already up to date
     */
    public static void check(Activity activity, boolean force) {
        if (activity == null || activity.isFinishing()) return;

        if (!force) {
            SharedPreferences sp = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            long last = sp.getLong(KEY_LAST_CHECK_MS, 0L);
            if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) {
                Log.d(TAG, "Skip check (throttled)");
                return;
            }
        }

        if (!IN_PROGRESS.compareAndSet(false, true)) {
            if (force) {
                Toast.makeText(activity, R.string.update_in_progress, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (force) {
            Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }

        EXEC.execute(() -> {
            try {
                activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis())
                        .apply();

                RemoteVersion remote = fetchRemoteVersion();
                int localCode = localVersionCode(activity);

                Log.d(TAG, "Local versionCode=" + localCode + " remote=" + remote.versionCode);

                if (remote.versionCode <= localCode) {
                    if (force) {
                        MAIN.post(() -> Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                MAIN.post(() -> showUpdateDialog(activity, remote));
            } catch (Exception e) {
                Log.e(TAG, "Update check failed: " + e.getMessage(), e);
                if (force) {
                    MAIN.post(() -> Toast.makeText(activity,
                            activity.getString(R.string.update_check_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show());
                }
            } finally {
                IN_PROGRESS.set(false);
            }
        });
    }

    private static void showUpdateDialog(Activity activity, RemoteVersion remote) {
        if (activity.isFinishing()) return;

        String message = activity.getString(
                R.string.update_available_message, remote.versionName, remote.versionCode);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.update_install, (d, w) -> startDownloadAndInstall(activity, remote))
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    private static void startDownloadAndInstall(Activity activity, RemoteVersion remote) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_unknown, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(intent);
            return;
        }

        if (!IN_PROGRESS.compareAndSet(false, true)) {
            Toast.makeText(activity, R.string.update_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show();

        EXEC.execute(() -> {
            try {
                File apk = downloadApk(activity, remote.apkUrl);
                MAIN.post(() -> installApk(activity, apk));
            } catch (Exception e) {
                Log.e(TAG, "Download/install failed: " + e.getMessage(), e);
                MAIN.post(() -> Toast.makeText(activity,
                        activity.getString(R.string.update_download_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            } finally {
                IN_PROGRESS.set(false);
            }
        });
    }

    private static void installApk(Activity activity, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apk
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install intent failed: " + e.getMessage(), e);
            Toast.makeText(activity,
                    activity.getString(R.string.update_install_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static RemoteVersion fetchRemoteVersion() throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(VERSION_JSON_URL).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " for version.json");
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            JSONObject obj = new JSONObject(sb.toString());
            int versionCode = obj.getInt("versionCode");
            String versionName = obj.optString("versionName", String.valueOf(versionCode));

            String apkUrl;
            if (obj.has("apkUrl") && obj.getString("apkUrl").trim().length() > 0) {
                apkUrl = obj.getString("apkUrl").trim();
            } else {
                String apkFile = obj.optString("apkFile", "pda-agent.apk");
                apkUrl = UPDATE_BASE_URL + apkFile;
            }

            return new RemoteVersion(versionCode, versionName, apkUrl);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static File downloadApk(Context context, String apkUrl) throws Exception {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create updates cache folder");
        }

        File outFile = new File(dir, "pda-agent-update.apk");
        if (outFile.exists() && !outFile.delete()) {
            Log.w(TAG, "Could not delete old APK cache");
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apkUrl).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " downloading APK");
            }

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }

            if (outFile.length() < 1024) {
                throw new IllegalStateException("Downloaded APK looks too small");
            }

            Log.d(TAG, "APK downloaded: " + outFile.getAbsolutePath() + " (" + outFile.length() + " bytes)");
            return outFile;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static int localVersionCode(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0)
                        .getLongVersionCode();
            }
            //noinspection deprecation
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static final class RemoteVersion {
        final int versionCode;
        final String versionName;
        final String apkUrl;

        RemoteVersion(int versionCode, String versionName, String apkUrl) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
        }
    }
}
