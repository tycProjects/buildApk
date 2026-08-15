package com.generated.wzh2apk;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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
// Downloads are handed off to Android's own DownloadManager (see
// startDownload below), which shows a real system notification for
// progress and completion and makes the file findable afterward -- the app
// itself never auto-opens or auto-installs anything; that's left to the
// user tapping the notification.
public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_REQUEST_CODE = 51426;
    private ValueCallback<Uri[]> filePathCallback;

    // Used to auto-dismiss the offline screen the instant the OS reports a
    // connection is back, instead of making the user tap "retry" themselves.
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Tiny custom-drawn glyph for the offline screen: a signal dot with two
    // fading arcs above it, struck through -- avoids needing a drawable
    // resource just for one icon.
    private static class SignalOffIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SignalOffIcon(Context context) {
            super(context);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h * 0.64f;
            float stroke = Math.max(w * 0.09f, 3f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#F2F2EE"));
            canvas.drawCircle(cx, cy, stroke * 1.05f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Color.parseColor("#F2F2EE"));
            float[] radii = {w * 0.24f, w * 0.38f};
            int[] alphas = {235, 130};
            for (int i = 0; i < radii.length; i++) {
                paint.setAlpha(alphas[i]);
                RectF arc = new RectF(cx - radii[i], cy - radii[i], cx + radii[i], cy + radii[i]);
                canvas.drawArc(arc, 208, 124, false, paint);
            }

            paint.setAlpha(255);
            paint.setColor(Color.parseColor("#FF6B57"));
            paint.setStrokeWidth(stroke * 1.1f);
            float pad = w * 0.14f;
            canvas.drawLine(pad, pad, w - pad, h - pad, paint);
        }
    }

    // Small helper the pulse loop below calls on each ring in turn: scales
    // a translucent circle up while fading it out, sonar-style. Kept as a
    // plain method (not a local lambda) since it doesn't need to capture
    // anything from onCreate.
    private void pulseOnce(View ring) {
        ring.animate().cancel();
        ring.setScaleX(0.5f);
        ring.setScaleY(0.5f);
        ring.setAlpha(0.5f);
        ring.animate()
            .scaleX(1.7f).scaleY(1.7f).alpha(0f)
            .setDuration(1400)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

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

        // Shown instead of the WebView's own built-in error page when the
        // main frame fails to load (see onReceivedError below) -- a bare
        // WebView renders Chromium's default "Webpage not available /
        // net::ERR_NAME_NOT_RESOLVED" page, which looks like a broken
        // browser rather than part of this app. Tapping it retries the load.
        // Also auto-retries the moment the OS reports connectivity back
        // (see the ConnectivityManager callback near the bottom of
        // onCreate), so on most devices the user never has to tap anything.
        final LinearLayout errorView = new LinearLayout(this);
        errorView.setOrientation(LinearLayout.VERTICAL);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(64, 0, 64, 0);
        errorView.setVisibility(View.GONE);

        // Icon stack: two looping "radar" pulse rings behind a static
        // no-signal glyph. The rings are plain circular Views animated with
        // ObjectAnimator (cheap, GPU-friendly); the glyph itself is a tiny
        // custom-drawn View (SignalOffIcon, defined below) so it doesn't
        // depend on any drawable resources.
        final FrameLayout iconStack = new FrameLayout(this);
        FrameLayout.LayoutParams iconStackParams = new FrameLayout.LayoutParams(140, 140);
        iconStackParams.gravity = Gravity.CENTER;
        iconStackParams.bottomMargin = 22;
        errorView.addView(iconStack, iconStackParams);

        final View pulseRingOuter = new View(this);
        GradientDrawable pulseDrawableOuter = new GradientDrawable();
        pulseDrawableOuter.setShape(GradientDrawable.OVAL);
        pulseDrawableOuter.setColor(Color.parseColor("#1AF2F2EE"));
        pulseRingOuter.setBackground(pulseDrawableOuter);
        FrameLayout.LayoutParams pulseParamsOuter = new FrameLayout.LayoutParams(140, 140);
        pulseParamsOuter.gravity = Gravity.CENTER;
        iconStack.addView(pulseRingOuter, pulseParamsOuter);

        final View pulseRingInner = new View(this);
        GradientDrawable pulseDrawableInner = new GradientDrawable();
        pulseDrawableInner.setShape(GradientDrawable.OVAL);
        pulseDrawableInner.setColor(Color.parseColor("#1AF2F2EE"));
        pulseRingInner.setBackground(pulseDrawableInner);
        FrameLayout.LayoutParams pulseParamsInner = new FrameLayout.LayoutParams(140, 140);
        pulseParamsInner.gravity = Gravity.CENTER;
        iconStack.addView(pulseRingInner, pulseParamsInner);

        final FrameLayout iconBadge = new FrameLayout(this);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(Color.parseColor("#16161A"));
        badgeBg.setStroke(2, Color.parseColor("#2A2A26"));
        iconBadge.setBackground(badgeBg);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(84, 84);
        badgeParams.gravity = Gravity.CENTER;
        iconStack.addView(iconBadge, badgeParams);

        final SignalOffIcon signalIcon = new SignalOffIcon(this);
        FrameLayout.LayoutParams signalIconParams = new FrameLayout.LayoutParams(48, 48);
        signalIconParams.gravity = Gravity.CENTER;
        iconBadge.addView(signalIcon, signalIconParams);

        // Two looping pulses, offset in time so a new ring kicks off partway
        // through the previous one's fade-out (the classic "sonar" look).
        final AnimatorSet[] pulseAnim = new AnimatorSet[1];
        final Runnable[] pulseInnerStarter = new Runnable[1];
        final Handler pulseHandler = new Handler(Looper.getMainLooper());

        final TextView errorTitle = new TextView(this);
        errorTitle.setText("You're offline");
        errorTitle.setTextColor(Color.parseColor("#F2F2EE"));
        errorTitle.setTextSize(20);
        errorTitle.setTypeface(errorTitle.getTypeface(), Typeface.BOLD);
        errorTitle.setGravity(Gravity.CENTER);
        errorView.addView(errorTitle);

        final TextView errorSubtitle = new TextView(this);
        errorSubtitle.setText("Turn on your internet to use this app");
        errorSubtitle.setTextColor(Color.parseColor("#9A9A94"));
        errorSubtitle.setTextSize(14);
        errorSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = 8;
        subtitleParams.bottomMargin = 30;
        errorView.addView(errorSubtitle, subtitleParams);

        // Styled as a pill so it reads as a real button rather than a plain
        // caption -- setOnTouchListener below scales + dims it slightly on
        // press since a bare TextView (unlike a Button) has no tap feedback
        // of its own.
        final TextView retryText = new TextView(this);
        retryText.setText("Tap to retry");
        retryText.setTextColor(Color.parseColor("#F2F2EE"));
        retryText.setTextSize(14);
        retryText.setTypeface(retryText.getTypeface(), Typeface.BOLD);
        retryText.setGravity(Gravity.CENTER);
        retryText.setPadding(56, 22, 56, 22);
        GradientDrawable retryPill = new GradientDrawable();
        retryPill.setColor(Color.parseColor("#1B1B1B"));
        retryPill.setStroke(2, Color.parseColor("#33332E"));
        retryPill.setCornerRadius(999f);
        retryText.setBackground(retryPill);
        errorView.addView(retryText);

        FrameLayout.LayoutParams errorParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        errorParams.gravity = Gravity.CENTER;
        root.addView(errorView, errorParams);

        setContentView(root);

        // Looping sonar pulse behind the icon, staggered so a new ring
        // starts partway through the previous one's fade. Declared before
        // the click/touch/webview callbacks below so all of them can call
        // start/stop on it.
        final Runnable[] pulseLoopOuter = new Runnable[1];
        final Runnable[] pulseLoopInner = new Runnable[1];
        pulseLoopOuter[0] = () -> {
            pulseOnce(pulseRingOuter);
            pulseHandler.postDelayed(pulseLoopOuter[0], 1500);
        };
        pulseLoopInner[0] = () -> {
            pulseOnce(pulseRingInner);
            pulseHandler.postDelayed(pulseLoopInner[0], 1500);
        };
        final Runnable startPulses = () -> {
            pulseHandler.removeCallbacks(pulseLoopOuter[0]);
            pulseHandler.removeCallbacks(pulseLoopInner[0]);
            pulseHandler.post(pulseLoopOuter[0]);
            pulseHandler.postDelayed(pulseLoopInner[0], 750);
        };
        final Runnable stopPulses = () -> {
            pulseHandler.removeCallbacks(pulseLoopOuter[0]);
            pulseHandler.removeCallbacks(pulseLoopInner[0]);
            pulseRingOuter.animate().cancel();
            pulseRingInner.animate().cancel();
            pulseRingOuter.setAlpha(0f);
            pulseRingInner.setAlpha(0f);
        };

        // Shared retry path for the tap target AND the auto-retry-on-
        // reconnect callback below -- plays a quick shrink-and-fade exit
        // before actually reloading, instead of just vanishing.
        final Runnable[] doRetry = new Runnable[1];
        doRetry[0] = () -> {
            stopPulses.run();
            loading.setVisibility(View.VISIBLE);
            webView.reload();
            errorView.animate()
                .alpha(0f).scaleX(0.94f).scaleY(0.94f)
                .setDuration(180)
                .withEndAction(() -> {
                    errorView.setVisibility(View.GONE);
                    errorView.setAlpha(1f);
                    errorView.setScaleX(1f);
                    errorView.setScaleY(1f);
                }).start();
        };

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

        // Auto-retry the moment the OS reports a usable connection again,
        // so the offline screen clears itself on most devices without
        // waiting for a tap. Falls back to manual "Tap to retry" if this
        // can't register (missing ACCESS_NETWORK_STATE on some OEM ROMs).
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    runOnUiThread(() -> {
                        if (errorView.getVisibility() == View.VISIBLE) {
                            doRetry[0].run();
                        }
                    });
                }
            };
            try {
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            } catch (SecurityException ignored) {
            }
        }

        errorView.setOnClickListener(v -> doRetry[0].run());
        errorView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    retryText.animate().cancel();
                    retryText.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.75f)
                        .setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    retryText.animate().cancel();
                    retryText.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(160).setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
            }
            return false;
        });

        webView.setWebViewClient(new WebViewClient() {
            // True once the current navigation has failed, so onPageFinished
            // (which WebView still calls after an error) knows not to reveal
            // the WebView underneath the error screen.
            private boolean hasError = false;

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                hasError = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (hasError) return;
                loading.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            // API 23+; covers essentially every device in real use. Not
            // calling super here on purpose -- the platform's default
            // implementation of this overload forwards main-frame errors
            // into the deprecated overload below, which would double-fire
            // showOffline().
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showOffline();
                }
            }

            // Fallback for minSdk 21-22, where the platform never calls the
            // overload above at all.
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                showOffline();
            }

            private void showOffline() {
                hasError = true;
                loading.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                errorView.setVisibility(View.VISIBLE);
                errorView.setAlpha(0f);
                errorView.setScaleX(0.88f);
                errorView.setScaleY(0.88f);
                errorView.setTranslationY(18f);
                errorView.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
                startPulses.run();
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
    }

    // Hands the download off to Android's own DownloadManager instead of
    // fetching it manually. This is what makes the file:
    //  - show a real system notification (progress while downloading, then
    //    "Download complete") instead of the app being the only place any
    //    progress is visible;
    //  - show up in the system Downloads app / any file manager afterward,
    //    so it's actually findable once the app that downloaded it is closed;
    //  - be openable straight from that notification (tapping it is the user
    //    choosing to open/install it -- the app itself never auto-opens or
    //    auto-installs anything).
    // setDestinationInExternalFilesDir needs no storage permission on any
    // API level (unlike the public Downloads directory, which needs a
    // runtime permission pre-API 29); setAllowedOverMetered/Roaming and
    // setVisibleInDownloadsUi make sure it isn't blocked on mobile data and
    // does still surface in the system Downloads listing despite living in
    // the app's own external-files folder.
    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        final String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
        final String cookie = CookieManager.getInstance().getCookie(url);

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.addRequestHeader("User-Agent", userAgent);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setVisibleInDownloadsUi(true);

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            downloadManager.enqueue(request);
            Toast.makeText(this, "Downloading " + filename + "…", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed -- check your connection and try again", Toast.LENGTH_LONG).show();
        }
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
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
    }
}
