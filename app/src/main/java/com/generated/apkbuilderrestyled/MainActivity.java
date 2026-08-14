package com.generated.apkbuilderrestyled;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

// Shows a themed loading screen (matching the app's dark background) the
// moment the app opens, and swaps it out for the WebView content only once
// the page has actually finished loading -- so opening the app never shows
// a blank white flash while the WebView engine spins up.
//
// Also wires up onShowFileChooser: a plain WebView does NOT respond to
// <input type="file"> clicks out of the box -- without this override,
// tapping a file-upload control silently does nothing, which is the most
// common "the button doesn't work" complaint for wrapped web apps that
// let the user pick a file.
//
// And a DownloadListener: a plain WebView also does NOT know what to do
// with a link to a downloadable file (an APK, a zip, etc) -- without this,
// tapping a "Download" link just fails to navigate anywhere and the app
// appears to do nothing / falls back to showing the page underneath it.
// This hands the download off to Android's real DownloadManager so it
// saves properly to the device's Downloads folder with a system
// notification, the way a normal download is expected to behave.
public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_REQUEST_CODE = 51426;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 51427;
    private ValueCallback<Uri[]> filePathCallback;

    // Baked in at build time from the builder server's PUBLIC_URL. Left
    // empty (and the update check silently skipped) if PUBLIC_URL wasn't
    // set when this app was built.
    private static final String UPDATE_CHECK_URL = "https://zip-to-apk-ce53.onrender.com/api/app-update";
    // Accent used to theme the "Update available" dialog -- matches this
    // app's own theme-color when it declared one at build time.
    private static final String ACCENT_COLOR = "#5EEAD4";
    private static final String ACCENT_TEXT_COLOR = "#0B1210";
    private long pendingUpdateDownloadId = -1;
    private BroadcastReceiver updateDownloadReceiver;

    // Stashed so we can retry the download once a requested permission is granted.
    private String pendingUrl, pendingUserAgent, pendingContentDisposition, pendingMimeType;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#050505"));

        final WebView webView = new WebView(this);
        webView.setVisibility(View.GONE);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(webView, webParams);

        final ProgressBar loading = new ProgressBar(this);
        loading.getIndeterminateDrawable().setColorFilter(
            Color.parseColor("#F2F2EE"), PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        loadingParams.gravity = Gravity.CENTER;
        root.addView(loading, loadingParams);

        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        // Needed for Google/Firebase-style "sign in with popup" flows: that JS
        // calls window.open() on the auth provider's URL, and Chrome/Firebase
        // then closes that popup itself once sign-in finishes. Without these
        // two, WebView either can't open the popup at all or opens it detached
        // from the parent page's session, so the auth handler gets a request
        // it can't reconcile and shows "The requested action is invalid".
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loading.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            // Handles window.open() calls, which is how Google/Firebase-style
            // "sign in with popup" flows work. A bare WebView has nowhere to put
            // that second window, so without this override the popup silently
            // fails (or opens detached from the parent page) and the auth
            // handler comes back with "The requested action is invalid".
            //
            // We give it a real WebView hosted in a full-screen Dialog, and rely
            // on the provider's own page calling window.close() when the flow
            // finishes (which Firebase's auth handler does) to dismiss it.
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popupWebView = new WebView(MainActivity.this);
                WebSettings popupSettings = popupWebView.getSettings();
                popupSettings.setJavaScriptEnabled(true);
                popupSettings.setDomStorageEnabled(true);

                final Dialog popupDialog = new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                popupDialog.setContentView(popupWebView);
                popupDialog.setOnDismissListener(d -> popupWebView.destroy());
                popupDialog.show();

                popupWebView.setWebViewClient(new WebViewClient());
                popupWebView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        popupDialog.dismiss();
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
            startDownload(url, userAgent, contentDisposition, mimeType));

        webView.loadUrl("file:///android_asset/index.html");

        checkForUpdate();
    }

    // ---- In-app update dialog ------------------------------------------
    // Pings the builder server on every launch. If it comes back with
    // updateAvailable=true (flipped server-side -- see UPDATE_AVAILABLE in
    // server.js), shows a native dialog offering to download and install
    // the new APK directly, no Play Store involved. Runs on a plain
    // background thread and fails silently on any error -- a broken or
    // unreachable update check should never block or crash a normal launch.
    private void checkForUpdate() {
        if (UPDATE_CHECK_URL == null || UPDATE_CHECK_URL.isEmpty()) return;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(UPDATE_CHECK_URL).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() != 200) return;

                Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                String body = scanner.hasNext() ? scanner.next() : "";
                JSONObject json = new JSONObject(body);

                boolean updateAvailable = json.optBoolean("updateAvailable", false);
                String apkUrl = json.optString("apkUrl", "");
                String message = json.optString("message", "A new version of this app is available.");
                JSONArray notes = json.optJSONArray("notes");

                if (updateAvailable && !apkUrl.isEmpty()) {
                    runOnUiThread(() -> showUpdateDialog(message, notes, apkUrl));
                }
            } catch (Exception e) {
                // No connectivity, bad response, etc -- just skip silently.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // Builds the "Update available" prompt as one custom-styled, animated
    // card instead of a plain system AlertDialog -- the changelog (if any)
    // is attached inside this same card, in its own scroll area, so a long
    // list of notes scrolls internally instead of pushing the Later/Update
    // Now buttons off the bottom of the screen.
    private void showUpdateDialog(String message, JSONArray notes, String apkUrl) {
        if (isFinishing()) return;

        final float d = getResources().getDisplayMetrics().density;
        final int accent = Color.parseColor(ACCENT_COLOR);
        final int accentText = Color.parseColor(ACCENT_TEXT_COLOR);
        final int accentDim = Color.argb(41, Color.red(accent), Color.green(accent), Color.blue(accent));
        final int surfaceColor = Color.parseColor("#14161A");
        final int textColor = Color.parseColor("#F4F3EF");
        final int mutedColor = Color.parseColor("#9A978D");
        final int lineColor = Color.parseColor("#26FFFFFF");

        final Dialog dialog = new Dialog(MainActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * d);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(surfaceColor);
        cardBg.setCornerRadius(18 * d);
        cardBg.setStroke((int) (1 * d), accentDim);
        card.setBackground(cardBg);

        // header: icon badge + title
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout badge = new FrameLayout(this);
        int badgeSize = (int) (38 * d);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(accentDim);
        badge.setBackground(badgeBg);
        TextView badgeIcon = new TextView(this);
        badgeIcon.setText("\u2B07");
        badgeIcon.setTextColor(accent);
        badgeIcon.setTextSize(15);
        badge.addView(badgeIcon, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.setMarginEnd((int) (12 * d));

        TextView title = new TextView(this);
        title.setText("Update available");
        title.setTextColor(textColor);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextSize(17);

        header.addView(badge, badgeLp);
        header.addView(title);
        card.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText(message);
        subtitle.setTextColor(mutedColor);
        subtitle.setTextSize(13.5f);
        subtitle.setLineSpacing(2 * d, 1f);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = (int) (10 * d);
        card.addView(subtitle, subtitleLp);

        // changelog, attached inside this same card -- capped height with
        // its own scroll so it never pushes the buttons off-screen
        if (notes != null && notes.length() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(lineColor);
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * d));
            dividerLp.topMargin = (int) (14 * d);
            dividerLp.bottomMargin = (int) (14 * d);
            card.addView(divider, dividerLp);

            LinearLayout notesList = new LinearLayout(this);
            notesList.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < notes.length(); i++) {
                String note = notes.optString(i, "");
                if (note.isEmpty()) continue;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowLp.topMargin = (int) (12 * d);

                FrameLayout num = new FrameLayout(this);
                int numSize = (int) (20 * d);
                GradientDrawable numBg = new GradientDrawable();
                numBg.setShape(GradientDrawable.OVAL);
                numBg.setColor(accentDim);
                num.setBackground(numBg);
                TextView numText = new TextView(this);
                numText.setText(String.valueOf(i + 1));
                numText.setTextColor(accent);
                numText.setTextSize(10.5f);
                num.addView(numText, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(numSize, numSize);
                numLp.setMarginEnd((int) (10 * d));
                numLp.topMargin = (int) (1 * d);

                TextView noteText = new TextView(this);
                noteText.setText(note);
                noteText.setTextColor(mutedColor);
                noteText.setTextSize(13);
                noteText.setLineSpacing(2 * d, 1f);
                LinearLayout.LayoutParams noteTextLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

                row.addView(num, numLp);
                row.addView(noteText, noteTextLp);
                notesList.addView(row, rowLp);
            }

            final ScrollView notesScroll = new ScrollView(this);
            notesScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            notesScroll.addView(notesList);
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            card.addView(notesScroll, scrollLp);

            final int maxNotesHeight = (int) (260 * d);
            notesScroll.post(() -> {
                if (notesScroll.getHeight() > maxNotesHeight) {
                    ViewGroup.LayoutParams lp = notesScroll.getLayoutParams();
                    lp.height = maxNotesHeight;
                    notesScroll.setLayoutParams(lp);
                }
            });
        }

        // footer: Later / Update now, always visible below whatever's above
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);

        Button laterBtn = new Button(this);
        laterBtn.setText("Later");
        laterBtn.setAllCaps(true);
        laterBtn.setTextColor(mutedColor);
        laterBtn.setTextSize(12.5f);
        laterBtn.setMinWidth(0);
        laterBtn.setMinHeight(0);
        laterBtn.setMinimumWidth(0);
        laterBtn.setMinimumHeight(0);
        laterBtn.setStateListAnimator(null);
        laterBtn.setPadding(0, (int) (13 * d), 0, (int) (13 * d));
        GradientDrawable laterBg = new GradientDrawable();
        laterBg.setColor(Color.TRANSPARENT);
        laterBg.setStroke((int) (1.5f * d), Color.parseColor("#6BFFFFFF"));
        laterBg.setCornerRadius(10 * d);
        laterBtn.setBackground(laterBg);
        laterBtn.setOnClickListener(v -> dialog.dismiss());

        Button updateBtn = new Button(this);
        updateBtn.setText("Update now");
        updateBtn.setAllCaps(true);
        updateBtn.setTextColor(accentText);
        updateBtn.setTextSize(12.5f);
        updateBtn.setMinWidth(0);
        updateBtn.setMinHeight(0);
        updateBtn.setMinimumWidth(0);
        updateBtn.setMinimumHeight(0);
        updateBtn.setStateListAnimator(null);
        updateBtn.setPadding(0, (int) (13 * d), 0, (int) (13 * d));
        GradientDrawable updateBg = new GradientDrawable();
        updateBg.setColor(accent);
        updateBg.setCornerRadius(10 * d);
        updateBtn.setBackground(updateBg);
        updateBtn.setOnClickListener(v -> { dialog.dismiss(); downloadUpdateApk(apkUrl); });

        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        laterLp.setMarginEnd((int) (10 * d));
        LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        footer.addView(laterBtn, laterLp);
        footer.addView(updateBtn, updateLp);

        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = (int) (18 * d);
        card.addView(footer, footerLp);

        FrameLayout outer = new FrameLayout(this);
        int sideMargin = (int) (24 * d);
        outer.setPadding(sideMargin, 0, sideMargin, 0);
        outer.addView(card, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.setContentView(outer);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        // pop-in entrance animation
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        dialog.setOnShowListener(d2 -> card.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(260)
            .setInterpolator(new OvershootInterpolator(1.15f))
            .start());

        dialog.show();
    }

    private void downloadUpdateApk(String apkUrl) {
        Toast.makeText(this, "Downloading update\u2026", Toast.LENGTH_SHORT).show();

        final DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setTitle("App update");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "update.apk");
        pendingUpdateDownloadId = dm.enqueue(request);

        if (updateDownloadReceiver != null) {
            try { unregisterReceiver(updateDownloadReceiver); } catch (Exception ignored) {}
        }
        updateDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != pendingUpdateDownloadId) return;
                try { unregisterReceiver(this); } catch (Exception ignored) {}
                updateDownloadReceiver = null;
                promptInstall(dm, id);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateDownloadReceiver, filter);
        }
    }

    private void promptInstall(DownloadManager dm, long downloadId) {
        try {
            Uri apkUri = dm.getUriForDownloadedFile(downloadId);
            if (apkUri == null) {
                Toast.makeText(this, "Update download failed.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "Allow this app to install updates, then tap Update Now again.",
                    Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
                return;
            }
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't start the install.", Toast.LENGTH_LONG).show();
        }
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        // On API < 29, writing to the public Downloads folder needs the
        // runtime WRITE_EXTERNAL_STORAGE permission. API 29+ (scoped
        // storage) doesn't need it for DownloadManager's public downloads.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingUrl = url;
            pendingUserAgent = userAgent;
            pendingContentDisposition = contentDisposition;
            pendingMimeType = mimeType;
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
                STORAGE_PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.addRequestHeader("User-Agent", userAgent);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            request.setMimeType(mimeType);
            request.setTitle(filename);
            request.setDescription("Downloading " + filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, "Downloading " + filename + "\u2026", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't start the download", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != STORAGE_PERMISSION_REQUEST_CODE) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && pendingUrl != null) {
            startDownload(pendingUrl, pendingUserAgent, pendingContentDisposition, pendingMimeType);
        } else {
            Toast.makeText(this, "Storage permission is needed to save the download", Toast.LENGTH_LONG).show();
        }
        pendingUrl = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                results = new Uri[]{ data.getData() };
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    @Override
    protected void onDestroy() {
        if (updateDownloadReceiver != null) {
            try { unregisterReceiver(updateDownloadReceiver); } catch (Exception ignored) {}
            updateDownloadReceiver = null;
        }
        super.onDestroy();
    }
}
