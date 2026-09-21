package com.wzh.builder;

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
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateChecker() {}

    public static void check(android.app.Activity activity) {
        final String serverUrl = ServerConfig.getUpdateServerUrl();
        if (serverUrl.isEmpty()) return; // no update server configured for this build

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
                String urlStr = serverUrl.replaceAll(UrlObfuscator.decode(new int[] { 0, 101, 73 }, 47), "")
                    + UrlObfuscator.decode(new int[] { 111, 62, 14, 244, 147, 174, 138, 125, 89, 35, 19, 184, 215, 187, 151, 114, 91, 112, 30, 236, 207, 160, 139, 110, 77, 9, 7, 232, 193, 254 }, 64) + Uri.encode(packageName)
                    + UrlObfuscator.decode(new int[] { 119, 6, 234, 220, 190, 133, 100, 68, 10, 7, 227, 195, 248 }, 81) + currentVersionCode;
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod(UrlObfuscator.decode(new int[] { 37, 196, 244 }, 98));

                if (conn.getResponseCode() != 200) { conn.disconnect(); return; }

                StringBuilder body = new StringBuilder();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(body.toString());
                if (!json.optBoolean(UrlObfuscator.decode(new int[] { 6, 226, 213, 177, 155, 107, 108, 58, 10, 227, 197, 169, 133, 106, 64 }, 115), false)) return;

                String latestVersionName = json.optString(UrlObfuscator.decode(new int[] { 232, 194, 182, 132, 115, 107, 104, 56, 14, 232, 211, 182, 150, 89, 87, 56, 17 }, 132), "");
                String apkUrl = json.optString(UrlObfuscator.decode(new int[] { 244, 196, 184, 167, 99, 92 }, 149), "");
                String notes = json.optString(UrlObfuscator.decode(new int[] { 200, 170, 144, 102, 81 }, 166), "");
                if (apkUrl.isEmpty()) return;

                new Handler(Looper.getMainLooper()).post(() ->
                    showUpdateDialog(activity, latestVersionName, apkUrl, notes));
            } catch (Exception e) {
                android.util.Log.d(UrlObfuscator.decode(new int[] { 226, 166, 145, 117, 71, 55, 50, 248, 202, 173, 134, 105, 89 }, 183), UrlObfuscator.decode(new int[] { 157, 151, 98, 68, 48, 6, 162, 194, 168, 186, 157, 118, 28, 61, 27, 240, 212, 178, 146, 47, 20 }, 200) + e.getMessage());
            }
        });
    }

    private static void showUpdateDialog(android.app.Activity activity, String versionName, String apkUrl, String notes) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        String message = notes.isEmpty()
            ? UrlObfuscator.decode(new int[] { 143, 157, 101, 69, 60, 27, 253, 146 }, 217) + versionName + UrlObfuscator.decode(new int[] { 202, 96, 91, 103, 7, 243, 197, 170, 142, 96, 66, 83, 59, 83 }, 234)
            : UrlObfuscator.decode(new int[] { 173, 127, 75, 43, 30, 249, 219, 244 }, 251) + versionName + " is available.\n\n" + notes;

        new AlertDialog.Builder(activity)
            .setTitle(UrlObfuscator.decode(new int[] { 89, 91, 46, 8, 252, 194, 230, 132, 114, 66, 43, 13, 225, 253, 210, 184 }, 268))
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(UrlObfuscator.decode(new int[] { 72, 76, 63, 27, 237, 221 }, 285), (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                activity.startActivity(intent);
            })
            .setNegativeButton(UrlObfuscator.decode(new int[] { 127, 51, 5, 245, 221 }, 51), null)
            .show();
    }
}
