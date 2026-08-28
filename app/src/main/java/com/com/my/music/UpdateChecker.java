package com.com.my.music;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

// Fire-and-forget update check, called once from MainActivity.onCreate.
// Uses plain HttpURLConnection (no extra library) on a background thread,
// then hops back to the main thread only if there's something to show.
public final class UpdateChecker {
    private static final String SERVER_URL = "https://zip-to-apk-ce53.onrender.com";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateChecker() {}

    public static void check(android.app.Activity activity) {
        if (SERVER_URL.isEmpty()) return; // no update server configured for this build

        final String packageName = activity.getPackageName();
        final int currentVersionCode;
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(packageName, 0);
            currentVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? (int) info.getLongVersionCode()
                : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                String urlStr = SERVER_URL.replaceAll("/+$", "")
                    + "/api/update-check?packageName=" + Uri.encode(packageName)
                    + "&versionCode=" + currentVersionCode;
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) { conn.disconnect(); return; }

                StringBuilder body = new StringBuilder();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(body.toString());
                if (!json.optBoolean("updateAvailable", false)) return;

                String latestVersionName = json.optString("latestVersionName", "");
                String apkUrl = json.optString("apkUrl", "");
                String notes = json.optString("notes", "");
                if (apkUrl.isEmpty()) return;

                new Handler(Looper.getMainLooper()).post(() ->
                    showUpdateDialog(activity, latestVersionName, apkUrl, notes));
            } catch (Exception e) {
                android.util.Log.d("UpdateChecker", "Update check failed: " + e.getMessage());
            }
        });
    }

    private static void showUpdateDialog(android.app.Activity activity, String versionName, String apkUrl, String notes) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        String message = notes.isEmpty()
            ? "Version " + versionName + " is available."
            : "Version " + versionName + " is available.\n\n" + notes;

        new AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("Update", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                activity.startActivity(intent);
            })
            .setNegativeButton("Later", null)
            .show();
    }
}
