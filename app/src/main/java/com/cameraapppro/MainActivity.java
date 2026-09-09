package com.cameraapppro;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int REQ_CAMERA_AUDIO = 1001;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNeededPermissions();
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_CAMERA_AUDIO);
            }
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void saveMedia(String base64, String mime, String name) {
            try {
                byte[] data = Base64.getDecoder().decode(base64);
                String folder = mime != null && mime.startsWith("video") ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/Camera App Pro");
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    ContentResolver r = getContentResolver();
                    Uri uri = r.insert(mime != null && mime.startsWith("video") ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("MediaStore insert failed");
                    try (OutputStream out = r.openOutputStream(uri)) { out.write(data); }
                    v.clear(); v.put(MediaStore.MediaColumns.IS_PENDING, 0); r.update(uri, v, null, null);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(folder), "Camera App Pro");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, name);
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
                }
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر حفظ الملف", Toast.LENGTH_SHORT).show()); }
        }

        @JavascriptInterface public void shareMedia(String base64, String mime, String name) {
            try {
                byte[] data = Base64.getDecoder().decode(base64);
                File dir = new File(getCacheDir(), "shared"); if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name); try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
                Uri uri = androidx.core.content.FileProvider.getUriForFile(MainActivity.this, getPackageName()+".fileprovider", f);
                Intent i = new Intent(Intent.ACTION_SEND); i.setType(mime); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Share"));
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "المشاركة غير متاحة", Toast.LENGTH_SHORT).show()); }
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
