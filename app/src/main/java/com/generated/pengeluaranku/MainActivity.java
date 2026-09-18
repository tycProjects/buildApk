package com.generated.pengeluaranku;

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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.io.ByteArrayInputStream;
import java.io.File;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaPlayer;
import android.view.TextureView;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.graphics.Matrix;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.webkit.JavascriptInterface;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;

// Shows a themed loading screen (matching the app's dark background) the
// moment the app opens, and swaps it out for the WebView content only once
// the page has actually finished loading -- so opening the app never shows
// a blank white flash while the WebView engine spins up.
//
// Also wires up onShowFileChooser: a plain WebView does NOT respond to
// <input type=UrlObfuscator.decode(new int[] { 73, 39, 1, 233 }, 47)> clicks out of the box -- without this override,
// tapping a file-upload control silently does nothing, which is the most
// common UrlObfuscator.decode(new int[] { 52, 55, 27, 189, 222, 174, 142, 109, 87, 57, 86, 241, 219, 182, 129, 127, 23, 59, 78, 250, 195, 185, 129 }, 64) complaint for wrapped web apps that
// let the user pick a file.
//
// And a DownloadListener: a plain WebView also does NOT know what to do
// with a link to a downloadable file (an APK, a zip, etc) -- without this,
// tapping a UrlObfuscator.decode(new int[] { 21, 31, 248, 192, 161, 131, 106, 78 }, 81) link just fails to navigate anywhere and the app
// appears to do nothing / falls back to showing the page underneath it.
// Downloads are handed off to Android's own DownloadManager (see
// startDownload below), which shows a real system notification for
// progress and completion and makes the file findable afterward. If what
// finished downloading is itself a .apk, this also offers the system
// installer straight away (see requestInstall/launchInstall) instead of
// making the user go find the file themselves -- every other kind of
// download still just lands in the Downloads folder, same as before.
public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_REQUEST_CODE = 51426;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 51427;
    // Covers both getUserMedia() resources a page can ask for -- camera and
    // mic -- since a page can (and video-chat widgets often do) ask for
    // both in the same PermissionRequest; see onPermissionRequest below.
    private static final int WEB_MEDIA_PERMISSION_REQUEST_CODE = 51428;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 51429;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 51430;
    // Handled the same way STORAGE_PERMISSION_REQUEST_CODE/pendingDownload
    // is above: Android 8+ requires the user to explicitly allow this app
    // to install packages before ACTION_VIEW on an APK does anything, so if
    // that's not granted yet this is parked here and resumed from
    // onActivityResult once the user comes back from that settings screen.
    private static final int INSTALL_PERMISSION_REQUEST_CODE = 51431;
    private Uri pendingInstallUri;
    // Synthetic https origin standing in for file:///android_asset/ now that
    // the wrapped site's files are compiled into EmbeddedAssets rather than
    // sitting in a real assets/ folder -- shouldInterceptRequest below
    // resolves anything under this origin by decoding the matching
    // EmbeddedAssets entry instead of the OS resolving it against disk.
    private static final String EMBED_HOST = UrlObfuscator.decode(new int[] { 10, 245, 212, 207, 173, 199, 51, 20, 63, 20, 250, 210, 178, 145, 113, 87, 124, 29, 255, 204, 175, 129, 35 }, 98);
    private ValueCallback<Uri[]> filePathCallback;
    // See the WebViewCompat.addDocumentStartJavaScript call in onCreate and
    // the onPageStarted fallback below for why this exists in two places.
    private boolean documentStartScriptSupported = false;
    // Best-effort polyfill for the File System Access API
    // (window.showOpenFilePicker), shared by both injection paths below.
    // Plain android.webkit.WebView has never implemented this API (it's
    // Chrome-desktop/modern-mobile-Chrome only -- see caniuse/MDN), so any
    // site written against it -- rather than the older
    // <input type=UrlObfuscator.decode(new int[] { 21, 251, 221, 181 }, 115)> + .click() pattern -- just throws
    // UrlObfuscator.decode(new int[] { 247, 203, 173, 150, 79, 111, 91, 51, 58, 242, 214, 188, 168, 126, 85, 62, 17, 225, 146, 184, 131, 47, 64, 34, 24, 171, 203, 233, 142, 114, 72, 38, 16, 234, 205, 175 }, 132) the moment its own upload
    // button is tapped, which looks to the user exactly like the button
    // silently doing nothing. This reroutes that call through a real
    // hidden <input type=file> instead, which
    // WebChromeClient.onShowFileChooser below already knows how to handle.
    private static final String SHOW_OPEN_FILE_PICKER_POLYFILL =
        UrlObfuscator.decode(new int[] { 189, 210, 166, 156, 114, 68, 38, 1, 227, 132, 226, 145 }, 149) +
        UrlObfuscator.decode(new int[] { 207, 163, 204, 116, 75, 47, 4, 16, 233, 147, 175, 147, 117, 78, 23, 7, 243, 219, 146, 154, 126, 84, 0, 6, 237, 198, 169, 153, 35, 91, 45, 19, 243, 215, 170, 216 }, 166) +
        UrlObfuscator.decode(new int[] { 192, 191, 155, 112, 92, 37, 95, 227, 199, 161, 154, 67, 91, 47, 7, 206, 206, 170, 128, 84, 74, 33, 10, 229, 237, 131, 187, 137, 117, 89, 45, 17, 248, 216, 253, 155, 99, 70, 34, 89, 244 }, 183) +
        UrlObfuscator.decode(new int[] { 167, 151, 114, 86, 121, 12, 242, 213, 179, 163, 130, 102, 65, 96 }, 200) +
        UrlObfuscator.decode(new int[] { 171, 157, 99, 67, 39, 26, 179, 220, 180, 135, 47, 126, 63, 3, 230, 195, 186, 141, 47, 64, 48, 10, 224, 214, 168, 143, 145, 54, 79, 57, 8, 245, 213, 174, 146, 58, 71, 49, 25, 247, 210, 164, 198, 117 }, 217) +
        UrlObfuscator.decode(new int[] { 156, 104, 90, 103, 15, 235, 212, 182, 150, 60, 68, 80, 61, 8, 241, 222, 180, 141, 54, 84, 36, 16, 245, 199, 183, 180, 124, 74, 35, 8, 226, 223, 226, 206, 97, 73, 54, 16, 240, 132, 235, 218 }, 234) +
        UrlObfuscator.decode(new int[] { 146, 116, 73, 45, 3, 184, 193, 173, 131, 119, 12, 119, 9, 231, 193, 169, 204, 49 }, 251) +
        UrlObfuscator.decode(new int[] { 101, 77, 98, 6, 248, 211, 181, 203, 105, 86, 46, 21, 233, 239, 210, 184, 213, 114, 84, 41, 13, 227, 152, 184, 129, 127, 70, 56, 0, 227, 203, 240, 152, 121, 95, 44, 83 }, 268) +
        UrlObfuscator.decode(new int[] { 105, 78, 34, 1, 239, 217, 165, 214, 112, 76, 39, 1, 172, 235, 146, 213, 37, 67, 59, 30, 250, 134, 179, 159, 117, 65, 48, 30, 253, 251, 226, 247, 211, 122, 84, 40, 60, 249, 212, 190, 221, 114, 70, 60, 18, 228, 198, 161, 131, 36, 95, 99, 18 }, 285) +
        UrlObfuscator.decode(new int[] { 69, 51, 3, 176, 206, 243, 153, 42, 13, 62, 71, 233, 196, 165, 128, 116, 87, 121, 8, 230, 183, 159, 188, 213, 105, 95, 45, 13, 229, 216, 238 }, 51) +
        UrlObfuscator.decode(new int[] { 11, 1, 232, 196, 163, 171, 208, 118, 89, 34, 9, 177, 217, 254, 216, 115, 91, 33, 55, 240, 211, 167, 198, 107, 89, 37, 9, 253, 193, 168, 136, 45, 73, 42, 15, 228, 137, 196, 187, 133, 104, 72, 116, 9, 237, 196, 190, 221, 121, 90, 63, 20, 185, 148 }, 68) +
        UrlObfuscator.decode(new int[] { 125, 21, 200, 223, 184, 157, 106, 115, 49, 16, 208, 247, 224, 198, 97, 73, 55, 33, 226, 193, 169, 200, 153, 107, 83, 63, 15, 243, 214, 182, 223, 115, 77, 32, 90, 233, 212, 168, 155, 125, 3, 60, 30, 249, 193, 224, 130, 126, 81, 109, 88, 255, 136, 251, 162, 215, 38, 65, 114, 65 }, 85) +
        UrlObfuscator.decode(new int[] { 15, 227, 140, 166, 154, 117, 83, 17, 50, 24, 242, 220, 174, 145, 49, 94, 56, 5, 225, 199, 252, 144, 115, 76, 43, 29, 248, 150, 175, 145, 124, 84, 104, 15, 235, 202, 172, 201, 39, 51, 25, 116, 71, 230, 217, 184, 140, 116, 94, 125, 17, 186, 201, 172 }, 102) +
        UrlObfuscator.decode(new int[] { 30, 248, 197, 161, 135, 60, 66, 36, 22, 226, 200, 226, 143, 99, 90, 56, 11, 231, 220, 249, 196, 108, 78, 46, 58, 89, 166 }, 119) +
        UrlObfuscator.decode(new int[] { 236, 200, 165, 144, 105, 70, 44, 21, 174, 253, 209, 185, 133, 53, 91, 41, 8, 242, 216, 177, 183, 123, 91, 61, 20, 167, 199, 163, 156, 126, 94, 96, 83 }, 136) +
        UrlObfuscator.decode(new int[] { 240, 214, 167, 131, 97, 26, 50, 22, 245, 245, 185, 139, 99, 88, 7, 3, 250, 220, 162, 136, 96, 86, 107, 69, 226, 200, 222, 176, 154, 121, 28, 118, 31, 237, 217, 181, 129, 125, 92, 60, 89, 185, 212 }, 153) +
        UrlObfuscator.decode(new int[] { 222, 187, 145, 124, 66, 42, 7, 246, 207, 164, 142, 139, 48, 95, 51, 31, 227, 151, 170, 146, 123, 90, 34, 22, 209, 217, 185, 131, 106, 5, 37, 5, 250, 220, 188, 206, 61, 88, 39, 2, 246, 194, 168, 247, 155, 52, 71, 38 }, 170) +
        UrlObfuscator.decode(new int[] { 210, 188, 209, 57, 94, 56, 5, 225, 199, 252, 151, 121, 67, 43, 30, 240, 215, 235, 128, 102, 87, 51, 17, 170, 197, 171, 141, 101, 108, 16, 49, 25, 245, 221, 173, 144, 62, 77 }, 187) +
        UrlObfuscator.decode(new int[] { 186, 138, 120, 9, 45, 21, 244, 158, 176, 145, 123, 90, 37, 45, 12, 160, 210, 190, 141, 57, 124, 24, 59, 208, 204, 176, 151, 97, 68, 38, 1, 227, 132, 236, 190, 97, 77, 103, 19, 246, 193, 177, 194, 96, 66, 80, 44, 9, 249, 223, 250, 152, 56, 69, 51, 4, 225, 214, 161, 133, 62, 8, 98, 74, 205, 201, 165, 155, 124, 98, 52, 23, 235, 209, 229, 200, 59, 98 }, 204) +
        UrlObfuscator.decode(new int[] { 190, 157, 111, 89, 49, 80, 242, 159, 174, 145, 97, 64, 108, 30, 234, 217, 237, 169, 121, 88, 38, 26, 175, 129, 132, 134, 108, 80, 53, 37, 13, 236, 210, 174, 220, 51, 2, 37 }, 221) +
        UrlObfuscator.decode(new int[] { 156, 104, 70, 46, 9, 253, 128, 162, 148, 119, 13, 120, 16, 228, 212, 202, 172, 147, 39, 70 }, 238) +
        UrlObfuscator.decode(new int[] { 137, 127, 79, 124, 19, 251, 215, 188, 155, 115, 70, 105, 50, 224, 195, 177, 150, 32, 93, 62, 4, 254, 198, 188, 158, 118, 64, 106, 14, 227, 209, 238, 188, 159, 113, 80, 115, 19, 247, 200, 162, 130, 59, 82, 58, 30, 244, 195, 227, 136, 120, 66, 40, 30, 224, 199, 169, 206, 99, 77, 47, 7, 168, 219 }, 255) +
        UrlObfuscator.decode(new int[] { 98, 74, 58, 24, 254, 197, 177, 130, 97, 73, 34, 95, 163, 197, 171, 141, 101, 56, 18, 51, 29, 246, 223, 227, 158, 126, 90, 48, 90, 253, 211, 188, 149, 35, 73, 40, 24, 205, 195, 165, 141, 61, 64, 48, 10, 224, 214, 168, 143, 145, 54, 20, 39, 9, 255, 205, 173, 133, 120, 21, 4, 1, 253, 220, 185, 156, 107, 3, 62, 14, 249, 198, 164, 145, 99, 13, 34, 10, 238, 196, 233, 228, 131, 96, 7, 38, 83, 162 }, 272) +
        UrlObfuscator.decode(new int[] { 83, 37, 44, 17, 241, 202, 190, 210, 118, 72, 35, 5, 187, 217, 166, 158, 101, 89, 63, 2, 232, 147, 163, 139, 103, 76, 43, 3, 246, 158, 152, 138, 96, 78, 91, 50, 24, 239, 224, 234, 164, 69, 30, 109, 8, 189, 136 }, 289) +
        UrlObfuscator.decode(new int[] { 94, 56, 5, 225, 199, 252, 146, 124, 70, 45, 6, 164, 130, 241, 148, 33, 28, 59, 94 }, 55) +
        UrlObfuscator.decode(new int[] { 53, 79, 175, 140 }, 72);
    private String[] pendingDownload;
    // Field (not a local in onCreate) so onNewIntent -- fired for
    // shortcut taps / deep links / shares while the app's already running,
    // via the singleTask launch mode set in the manifest -- can act on the
    // same WebView instance instead of only being able to touch it from
    // inside onCreate.
    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    // Updated by AndroidBridge.reportScrollTop() below, driven by a
    // capture-phase JS scroll listener -- see the SwipeRefreshLayout
    // override above for why this exists instead of using the WebView's
    // own getScrollY().
    private volatile int lastKnownScrollTop = 0;
    // Whether the CURRENT drag started within the top UrlObfuscator.decode(new int[] { 41, 13, 251, 218, 245, 142, 124, 92, 52 }, 89) (see
    // canChildScrollUp() override below). scrollTop alone can't tell a
    // page genuinely at its top apart from a dialog/sheet that just
    // opened and also happens to read scrollTop 0 -- a drag anywhere
    // inside that dialog would otherwise get read as UrlObfuscator.decode(new int[] { 11, 253, 136, 179, 142, 96, 4, 55, 13, 241, 140, 159, 173, 146, 22, 27, 122, 89, 184, 152, 249, 213, 96, 91, 59, 2, 176, 194, 187, 158, 120, 11, 40, 12, 168, 198, 230, 151, 97, 69, 48, 4, 243, 247, 158, 173, 137, 119, 86 }, 106) even though it's nowhere near the
    // actual top of the screen. Gating on where the gesture *started*
    // fixes that without needing the page to know anything about it.
    private volatile boolean lastTouchInPullZone = true;
    private Vibrator vibrator;

    // The WebView's own in-progress camera/mic request (e.g. a QR scanner
    // or a video-chat widget using getUserMedia) while we go ask Android
    // for the runtime CAMERA/RECORD_AUDIO permission(s) -- resumed in
    // onRequestPermissionsResult once that answer comes back, see
    // onPermissionRequest below.
    private PermissionRequest pendingWebPermissionRequest;
    // Same idea for a page calling navigator.geolocation.getCurrentPosition/
    // watchPosition -- WebView surfaces that as
    // onGeolocationPermissionsShowPrompt rather than onPermissionRequest,
    // with its own callback type, so it needs its own pending pair instead
    // of reusing pendingWebPermissionRequest above.
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    // Used to auto-dismiss the offline screen the instant the OS reports a
    // connection is back, instead of making the user tap UrlObfuscator.decode(new int[] { 9, 255, 205, 170, 142 }, 123) themselves.
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    // Explicitly re-triggers a media scan on every completed download (see
    // handleDownloadComplete below) -- MIUI/HyperOS (Xiaomi/POCO/Redmi) in
    // particular is known not to index files that a third-party app saved
    // to the shared Downloads folder via DownloadManager: the file is
    // genuinely on disk, it just never appears in their own Downloads/file
    // manager UI until something explicitly asks the OS to scan it.
    private BroadcastReceiver downloadCompleteReceiver;
    // The ID of the download THIS app most recently started, or -1 if none
    // is in flight. DownloadManager.ACTION_DOWNLOAD_COMPLETE is a system-
    // wide broadcast -- it fires for every download completing anywhere on
    // the device through the shared DownloadManager service, not just this
    // app's own. Without checking the completed download's ID against this
    // field, downloadCompleteReceiver would call notifyDownloadResult(true)
    // for ANY finished download (another app's, or a stray leftover from an
    // earlier tap), instantly marking the page's button UrlObfuscator.decode(new int[] { 200, 196, 189, 135, 100, 72, 39, 1, 225, 199 }, 140) while
    // the actual file the user just requested was still genuinely
    // downloading in the system Download Manager -- this is what was fixed.
    private long pendingDownloadId = -1;

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
            paint.setColor(Color.parseColor(UrlObfuscator.decode(new int[] { 190, 250, 233, 188, 43, 125, 18 }, 157)));
            canvas.drawCircle(cx, cy, stroke * 1.05f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Color.parseColor(UrlObfuscator.decode(new int[] { 141, 248, 137, 110, 75, 45, 92 }, 174)));
            float[] radii = {w * 0.24f, w * 0.38f};
            int[] alphas = {235, 130};
            for (int i = 0; i < radii.length; i++) {
                paint.setAlpha(alphas[i]);
                RectF arc = new RectF(cx - radii[i], cy - radii[i], cx + radii[i], cy + radii[i]);
                canvas.drawArc(arc, 208, 124, false, paint);
            }

            paint.setAlpha(255);
            paint.setColor(Color.parseColor(UrlObfuscator.decode(new int[] { 156, 152, 187, 42, 121, 111, 78 }, 191)));
            paint.setStrokeWidth(stroke * 1.1f);
            float pad = w * 0.14f;
            canvas.drawLine(pad, pad, w - pad, h - pad, paint);
        }
    }

    // Compact three-dot UrlObfuscator.decode(new int[] { 178, 128, 123, 67, 47, 2, 228, 206 }, 208) loading indicator for the nav-loading
    // overlay (see navOverlay in onCreate) -- each dot bounces up and back
    // down on a loop, staggered so they ripple left-to-right rather than
    // moving in lockstep. Sized to sit centered as a small indicator
    // (rather than filling the screen) over the overlay's background.
    private static class BouncingDotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] dotLift = new float[3];
        private ValueAnimator anim;

        BouncingDotsView(Context context, int dotColor) {
            super(context);
            paint.setColor(dotColor);
            paint.setStyle(Paint.Style.FILL);
            setWillNotDraw(false);
        }

        // Ties the bounce loop to actual on-screen visibility rather than
        // running it for the app's whole lifetime -- navOverlay (this
        // view's parent) sits GONE between navigations, so without this
        // the animator would keep ticking indefinitely in the background
        // for no visible benefit.
        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            if (visibility == View.VISIBLE) {
                startAnim();
            } else {
                stopAnim();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stopAnim();
        }

        private void startAnim() {
            if (anim != null) return;
            anim = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
            anim.setDuration(1000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                for (int i = 0; i < 3; i++) {
                    // Each dot's phase is offset from the last so they
                    // bounce in a left-to-right ripple instead of together.
                    // Clamped at 0 so a dot rests on the baseline instead
                    // of dipping below it between bounces.
                    float phase = t - i * 0.55f;
                    dotLift[i] = (float) Math.max(0, Math.sin(phase));
                }
                invalidate();
            });
            anim.start();
        }

        private void stopAnim() {
            if (anim != null) {
                anim.cancel();
                anim = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float radius = Math.min(w, h) * 0.14f;
            float spacing = radius * 3.2f;
            float baseline = h * 0.68f;
            float bounceHeight = h * 0.38f;
            float startX = w / 2f - spacing;
            for (int i = 0; i < 3; i++) {
                float cy = baseline - dotLift[i] * bounceHeight;
                canvas.drawCircle(startX + i * spacing, cy, radius, paint);
            }
        }
    }

    // Common contract for whatever's sitting in the splash slot -- either
    // the built-in text-animation view below (LoadingSplashView) or a
    // user-uploaded video/image (CustomSplashView, further down). Lets
    // onCreate hold either one behind a single 'loading' variable and just
    // call show()/hide() without caring which kind it actually got.
    private interface SplashController {
        void show();
        void hide();
        // Used only when the load has failed and the offline screen is
        // about to be shown in its place -- skips whatever grace period
        // (minimum display time / UrlObfuscator.decode(new int[] { 141, 101, 107, 30, 41, 20, 254, 154, 176, 150, 99, 68, 58, 84, 229, 219, 181, 149, 96, 14, 43, 5, 229, 195, 186, 128 }, 225)) hide()
        // normally respects for a successful load, so the splash can't
        // still be fading/playing underneath the offline UI.
        void hideImmediate();
    }

    // Startup loading screen -- five selectable ways the app's name
    // assembles itself on a near-black backdrop while the WebView loads
    // behind it: TUMBLE (letters drop, spin and spring into place), FADE
    // (the whole wordmark rises gently while it fades in), TYPEWRITER
    // (letters type in left-to-right behind a blinking cursor), PULSE
    // (expanding rings ping outward behind the name) and SLIDE (letters
    // glide in from alternating sides). Whichever style is picked, the
    // wordmark keeps a gentle breathing pulse once it's landed so a slow
    // connection still reads as UrlObfuscator.decode(new int[] { 133, 126, 66, 36, 7, 227, 203 }, 242) instead of stuck. Text size
    // scales up for short names so they don't look lost in the middle of
    // the screen.
    private static class LoadingSplashView extends FrameLayout implements SplashController {
        static final int STYLE_TUMBLE = 0;
        static final int STYLE_FADE = 1;
        static final int STYLE_TYPEWRITER = 2;
        static final int STYLE_PULSE = 3;
        static final int STYLE_SLIDE = 4;
        static final int STYLE_NONE = 5;

        // Flat speed multiplier baked in from the Options tab's Slow /
        // Normal / Fast choice (1.6 / 1.0 / 0.6) -- every entrance
        // duration and delay below is passed through sd()/sdi() so the
        // whole animation plays slower or faster without changing what
        // it actually does.
        static final float SPEED_MULT = 1f;

        private static long sd(long ms) { return Math.round(ms * SPEED_MULT); }
        private static int sdi(int ms) { return (int) Math.round(ms * SPEED_MULT); }

        private final int style;
        private final View[] letters;
        private final LinearLayout row;
        private final View cursor;
        private final View[] rings;
        private final View barTrack;
        private final View barFill;
        private View barWrap;
        private int barFillWidth;
        private int barTrackWidthPx;
        private ValueAnimator idlePulse;
        private ValueAnimator cursorBlink;
        private ValueAnimator barAnim;
        private final Handler ringHandler = new Handler(Looper.getMainLooper());
        private final Runnable ringLoop = this::runRingLoop;
        private final Handler hideHandler = new Handler(Looper.getMainLooper());
        // Wall-clock time show() was called, and how long the entrance
        // animation needs to fully play out (the per-style value returned by
        // showTumble/showFade/etc, in ms). The page behind this view -- often
        // a local file:///android_asset/ asset -- can finish loading in just
        // a few milliseconds, well before a multi-letter entrance (staggered
        // tumble/typewriter/slide) has visually completed. Without tracking
        // this, hide() would cancel those in-flight per-letter animations
        // immediately, so the name appears to snap or cut off mid-motion
        // instead of finishing. hide() uses these two fields to wait out
        // whatever's left of the entrance before it starts fading out.
        private long showStartTime;
        private long minDisplayMs;

        LoadingSplashView(Context context, int bgColor, String appName, int style) {
            super(context);
            this.style = style;
            // Flat black/gray only -- fixed near-black navy (#10151C, see
            // splashBgColor), no color tint from the site's own accent.
            setBackgroundColor(bgColor);
            setVisibility(View.INVISIBLE);
            setAlpha(0f);

            float density = context.getResources().getDisplayMetrics().density;
            String name = (appName == null || appName.trim().isEmpty()) ? UrlObfuscator.decode(new int[] { 66, 82, 49 }, 259) : appName.trim().toUpperCase();

            // Pulse-ring style gets a few concentric ring outlines behind
            // everything else, pinging outward on a loop -- every other
            // style skips this entirely (empty array, loop never starts).
            if (style == STYLE_PULSE) {
                rings = new View[3];
                for (int i = 0; i < rings.length; i++) {
                    View ring = new View(context);
                    GradientDrawable ringBg = new GradientDrawable();
                    ringBg.setShape(GradientDrawable.OVAL);
                    ringBg.setColor(Color.TRANSPARENT);
                    ringBg.setStroke((int) (1.6f * density), withAlpha(Color.WHITE, 110));
                    ring.setBackground(ringBg);
                    int ringSize = (int) (120 * density);
                    FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(ringSize, ringSize);
                    ringParams.gravity = Gravity.CENTER;
                    ring.setAlpha(0f);
                    addView(ring, ringParams);
                    rings[i] = ring;
                }
            } else {
                rings = new View[0];
            }

            // Everything else stacks vertically -- the wordmark, then a
            // slim loading bar underneath it -- centered as one unit.
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            columnParams.gravity = Gravity.CENTER;
            addView(column, columnParams);

            // The wordmark row that holds each letter -- see
            // buildLetterView() below for how each one is actually styled
            // (light system weight, wide tracking, soft glow -- a clean,
            // minimal, UrlObfuscator.decode(new int[] { 101, 70, 59, 18, 251, 143, 162, 130, 109, 79, 35, 7, 239 }, 276) look rather than a heavy one).
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            column.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            float baseSize = letterSizeFor(name.length());
            int glowColor = Color.WHITE;
            letters = new View[name.length()];
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                boolean isFirst = i == 0;
                View letterView = buildLetterView(context, c, isFirst, baseSize, density, glowColor);
                letterView.setAlpha(0f);
                row.addView(letterView);
                letters[i] = letterView;
            }

            // A slim indeterminate loading bar under the wordmark -- a
            // faint track with a brighter segment that slides back and
            // forth the whole time the page is loading, instead of the
            // soft glow the splash used to sit on.
            int barTrackWidth = (int) (108 * density);
            int barHeight = (int) (3 * density);
            barFillWidth = (int) (38 * density);

            barTrack = new View(context);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setShape(GradientDrawable.RECTANGLE);
            trackBg.setCornerRadius(barHeight / 2f);
            trackBg.setColor(withAlpha(Color.WHITE, 32));
            barTrack.setBackground(trackBg);

            barFill = new View(context);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setShape(GradientDrawable.RECTANGLE);
            fillBg.setCornerRadius(barHeight / 2f);
            fillBg.setColor(Color.WHITE);
            barFill.setBackground(fillBg);

            FrameLayout barWrap = new FrameLayout(context);
            FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(barTrackWidth, barHeight);
            trackLp.gravity = Gravity.CENTER;
            barWrap.addView(barTrack, trackLp);
            FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(barFillWidth, barHeight);
            fillLp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            barWrap.addView(barFill, fillLp);
            barWrap.setAlpha(0f);

            LinearLayout.LayoutParams barWrapLp = new LinearLayout.LayoutParams(barTrackWidth, barHeight);
            barWrapLp.topMargin = (int) (24 * density);
            column.addView(barWrap, barWrapLp);
            this.barWrap = barWrap;
            barTrackWidthPx = barTrackWidth;

            // Typewriter style gets a thin blinking cursor bar right after
            // the last letter -- every other style never adds it to the row.
            if (style == STYLE_TYPEWRITER) {
                cursor = new View(context);
                cursor.setBackgroundColor(Color.WHITE);
                int cursorWidth = (int) (3 * density);
                int cursorHeight = (int) (baseSize * density * 0.95f);
                LinearLayout.LayoutParams cursorParams = new LinearLayout.LayoutParams(cursorWidth, cursorHeight);
                cursorParams.leftMargin = (int) (4 * density);
                cursor.setAlpha(0f);
                row.addView(cursor, cursorParams);
            } else {
                cursor = null;
            }
        }

        // Builds one letter as a single, lightweight TextView -- the
        // system's medium Roboto weight rather than a heavy bold, wide
        // letter-spacing, and just a soft white glow (no color tint, no
        // hard outline) -- the clean, minimal, quick-loading wordmark
        // feel of something like Facebook Lite's splash rather than a
        // bold caption-style treatment. Every letter matches the size,
        // weight and brightness the first letter used to have alone, so
        // the whole name reads as one consistently bold wordmark instead
        // of one emphasized letter followed by smaller ones.
        private static View buildLetterView(Context context, char c, boolean isFirst, float baseSize, float density, int glowColor) {
            String txt = c == ' ' ? " " : String.valueOf(c);
            TextView letter = new TextView(context);
            letter.setText(txt);
            letter.setTypeface(Typeface.create(UrlObfuscator.decode(new int[] { 86, 37, 13, 241, 140, 179, 186, 140, 116, 90, 118, 23, 252, 220, 190, 131, 120 }, 293), Typeface.NORMAL));
            letter.setTextSize(baseSize * 1.3f);
            letter.setLetterSpacing(0.14f);
            letter.setTextColor(Color.WHITE);
            letter.setShadowLayer(16f, 0f, 0f, withAlpha(glowColor, 110));
            return letter;
        }

        // Shorter names get noticeably bigger text -- a 3-4 letter name at
        // the same size as a long one would look lost in the middle of the
        // screen, so scale it up as the name gets shorter.
        private static float letterSizeFor(int nameLength) {
            if (nameLength <= 4) return 54f;
            if (nameLength <= 6) return 44f;
            if (nameLength <= 9) return 35f;
            if (nameLength <= 13) return 27f;
            return 22f;
        }

        private static int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | (alpha << 24);
        }

        // Fades in the backdrop + loading bar, then hands off to whichever
        // entrance the chosen style uses for the letters. Safe to call
        // again after hide() -- resets every child first.
        public void show() {
            stopIdlePulse();
            hideHandler.removeCallbacksAndMessages(null);
            animate().cancel();
            barWrap.animate().cancel();
            setVisibility(View.VISIBLE);
            setAlpha(0f);
            barWrap.setAlpha(0f);

            animate().alpha(1f).setDuration(sd(360)).start();
            barWrap.animate().alpha(1f).setStartDelay(sd(160)).setDuration(sd(500)).start();
            startBarAnim();

            long idleStart;
            switch (style) {
                case STYLE_FADE:
                    idleStart = showFade();
                    break;
                case STYLE_TYPEWRITER:
                    idleStart = showTypewriter();
                    break;
                case STYLE_PULSE:
                    idleStart = showPulse();
                    break;
                case STYLE_SLIDE:
                    idleStart = showSlide();
                    break;
                case STYLE_NONE:
                    idleStart = showNone();
                    break;
                case STYLE_TUMBLE:
                default:
                    idleStart = showTumble();
                    break;
            }

            // Pulsing kicks in once the entrance has landed, and keeps
            // going -- it's only ever stopped by hide(), i.e. it runs for
            // as long as the page is still loading, however long that
            // ends up taking.
            postDelayed(this::startIdlePulse, idleStart);

            showStartTime = SystemClock.uptimeMillis();
            minDisplayMs = idleStart;
        }

        // Slides the bright segment of the loading bar back and forth
        // across the track on an infinite loop -- purely indeterminate
        // (not tied to real page-load percentage), just something visibly
        // UrlObfuscator.decode(new int[] { 76, 53, 11, 243, 222, 184, 146 }, 59) under the wordmark the whole time it's showing.
        private void startBarAnim() {
            if (barAnim != null) barAnim.cancel();
            float maxTranslation = barTrackWidthPx - barFillWidth;
            barAnim = ValueAnimator.ofFloat(0f, maxTranslation);
            barAnim.setDuration(sd(950));
            barAnim.setRepeatMode(ValueAnimator.REVERSE);
            barAnim.setRepeatCount(ValueAnimator.INFINITE);
            barAnim.addUpdateListener(a -> barFill.setTranslationX((float) a.getAnimatedValue()));
            barAnim.start();
        }

        private float density() {
            return getResources().getDisplayMetrics().density;
        }

        // TUMBLE: each letter drops, spins slightly off its axis and
        // springs back with a little scale overshoot as it lands -- more
        // like it's physically tumbling into place than just sliding on
        // one axis, staggered left to right so the name reads as being
        // assembled.
        private long showTumble() {
            float density = density();
            int letterStagger = sdi(80);
            for (int i = 0; i < letters.length; i++) {
                View letter = letters[i];
                letter.animate().cancel();
                letter.setAlpha(0f);
                letter.setTranslationX(0f);
                letter.setTranslationY(-56 * density);
                letter.setScaleX(0.3f);
                letter.setScaleY(0.3f);
                // Alternating tilt direction per letter, growing slightly
                // toward the middle letters, so the row doesn't read as a
                // mechanically identical repeat of the same motion.
                float tilt = (i % 2 == 0 ? -1f : 1f) * (16f + (i * 5f) % 14f);
                letter.setRotation(tilt);

                long delay = sd(200) + (long) i * letterStagger;

                ObjectAnimator fall = ObjectAnimator.ofFloat(letter, View.TRANSLATION_Y, -56 * density, 0f);
                fall.setDuration(sd(560));
                // Smoother single-settle landing instead of the multi-bounce
                // BounceInterpolator used to give -- still a snappy pop, but
                // one clean overshoot-and-settle reads as UrlObfuscator.decode(new int[] { 63, 6, 229, 198, 188, 143 }, 76) rather
                // than jittery, closer to a slick caption-style entrance.
                fall.setInterpolator(new OvershootInterpolator(1.8f));

                ObjectAnimator spin = ObjectAnimator.ofFloat(letter, View.ROTATION, tilt, 0f);
                spin.setDuration(sd(520));
                spin.setInterpolator(new OvershootInterpolator(2.2f));

                ObjectAnimator growX = ObjectAnimator.ofFloat(letter, View.SCALE_X, 0.3f, 1f);
                ObjectAnimator growY = ObjectAnimator.ofFloat(letter, View.SCALE_Y, 0.3f, 1f);
                growX.setDuration(sd(480));
                growY.setDuration(sd(480));
                growX.setInterpolator(new OvershootInterpolator(3.4f));
                growY.setInterpolator(new OvershootInterpolator(3.4f));

                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(letter, View.ALPHA, 0f, 1f);
                fadeIn.setDuration(sd(220));

                AnimatorSet letterIn = new AnimatorSet();
                letterIn.playTogether(fall, spin, growX, growY, fadeIn);
                letterIn.setStartDelay(delay);
                letterIn.start();
            }
            return sd(200) + (long) letters.length * letterStagger + sd(700);
        }

        // FADE & RISE: no per-letter stagger at all -- the whole wordmark
        // rises gently out of the backdrop as one block while it fades in,
        // the calmest of the five.
        private long showFade() {
            float density = density();
            for (View letter : letters) {
                letter.animate().cancel();
                letter.setAlpha(1f);
                letter.setScaleX(1f);
                letter.setScaleY(1f);
                letter.setTranslationX(0f);
                letter.setTranslationY(0f);
                letter.setRotation(0f);
            }
            row.animate().cancel();
            row.setAlpha(0f);
            row.setTranslationY(28 * density);
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(sd(160))
                .setDuration(sd(700))
                .start();
            return sd(160) + sd(700);
        }

        // NONE: no per-letter animation at all. The name is placed in its
        // final resting state immediately -- the only motion on screen is
        // the plain backdrop fade-in that show() already does for every
        // style. Used when the user just wants a static loading screen.
        private long showNone() {
            for (View letter : letters) {
                letter.animate().cancel();
                letter.setAlpha(1f);
                letter.setScaleX(1f);
                letter.setScaleY(1f);
                letter.setTranslationX(0f);
                letter.setTranslationY(0f);
                letter.setRotation(0f);
            }
            row.animate().cancel();
            row.setAlpha(1f);
            row.setTranslationY(0f);
            if (cursor != null) {
                cursor.setAlpha(0f);
            }
            // Still matches the container's own 360ms fade-in so the splash
            // can't be hidden before it's even fully visible.
            return sd(360);
        }

        // TYPEWRITER: letters appear left to right with a quick fade + tiny
        // grow, no bounce or spin, behind a cursor bar that only appears
        // and starts blinking once typing finishes -- reads as the name
        // being typed out.
        private long showTypewriter() {
            if (cursor != null) {
                cursor.animate().cancel();
                cursor.setAlpha(0f);
            }
            int letterStagger = sdi(90);
            for (int i = 0; i < letters.length; i++) {
                View letter = letters[i];
                letter.animate().cancel();
                letter.setAlpha(0f);
                letter.setTranslationX(0f);
                letter.setTranslationY(0f);
                letter.setRotation(0f);
                letter.setScaleX(0.9f);
                letter.setScaleY(0.9f);

                long delay = sd(250) + (long) i * letterStagger;
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(letter, View.ALPHA, 0f, 1f);
                ObjectAnimator growX = ObjectAnimator.ofFloat(letter, View.SCALE_X, 0.9f, 1f);
                ObjectAnimator growY = ObjectAnimator.ofFloat(letter, View.SCALE_Y, 0.9f, 1f);
                fadeIn.setDuration(sd(160));
                growX.setDuration(sd(160));
                growY.setDuration(sd(160));
                AnimatorSet letterIn = new AnimatorSet();
                letterIn.playTogether(fadeIn, growX, growY);
                letterIn.setStartDelay(delay);
                letterIn.start();
            }
            long typedDone = sd(250) + (long) letters.length * letterStagger + sd(160);
            if (cursor != null) {
                postDelayed(() -> {
                    cursor.setAlpha(1f);
                    startCursorBlink();
                }, typedDone);
            }
            return typedDone + sd(300);
        }

        // PULSE RINGS: concentric ring outlines ping outward from center on
        // a repeating loop, starting immediately, while the wordmark fades
        // in as one block on top of them a beat later.
        private long showPulse() {
            for (View letter : letters) {
                letter.animate().cancel();
                letter.setAlpha(1f);
                letter.setScaleX(1f);
                letter.setScaleY(1f);
                letter.setTranslationX(0f);
                letter.setTranslationY(0f);
                letter.setRotation(0f);
            }
            row.animate().cancel();
            row.setAlpha(0f);
            row.animate().alpha(1f).setStartDelay(sd(360)).setDuration(sd(500)).start();

            for (View ring : rings) {
                ring.animate().cancel();
                ring.setScaleX(0.4f);
                ring.setScaleY(0.4f);
                ring.setAlpha(0f);
            }
            ringHandler.removeCallbacks(ringLoop);
            ringHandler.post(ringLoop);
            return sd(360) + sd(500);
        }

        // SLIDE IN: letters glide in horizontally from alternating sides
        // (odd from the left, even from the right) and settle with a small
        // overshoot -- a sideways counterpart to the vertical tumble, no
        // rotation or bounce.
        private long showSlide() {
            float density = density();
            int letterStagger = sdi(70);
            for (int i = 0; i < letters.length; i++) {
                View letter = letters[i];
                letter.animate().cancel();
                letter.setAlpha(0f);
                letter.setTranslationY(0f);
                letter.setRotation(0f);
                letter.setScaleX(1f);
                letter.setScaleY(1f);
                float startX = (i % 2 == 0 ? -1f : 1f) * 90 * density;
                letter.setTranslationX(startX);

                long delay = sd(180) + (long) i * letterStagger;
                ObjectAnimator slide = ObjectAnimator.ofFloat(letter, View.TRANSLATION_X, startX, 0f);
                slide.setDuration(sd(520));
                slide.setInterpolator(new OvershootInterpolator(1.6f));
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(letter, View.ALPHA, 0f, 1f);
                fadeIn.setDuration(sd(320));
                AnimatorSet letterIn = new AnimatorSet();
                letterIn.playTogether(slide, fadeIn);
                letterIn.setStartDelay(delay);
                letterIn.start();
            }
            return sd(180) + (long) letters.length * letterStagger + sd(520);
        }

        // One ping outward per ring, staggered, then reposts itself so the
        // sonar effect keeps going for as long as the splash is showing.
        private void runRingLoop() {
            for (int i = 0; i < rings.length; i++) {
                final View ring = rings[i];
                ring.animate().cancel();
                ring.setScaleX(0.4f);
                ring.setScaleY(0.4f);
                ring.setAlpha(0.8f);
                ring.animate()
                    .scaleX(1.6f).scaleY(1.6f).alpha(0f)
                    .setStartDelay((long) i * sd(260))
                    .setDuration(sd(1400))
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            }
            ringHandler.postDelayed(ringLoop, sd(1600));
        }

        // Fades the view out and sets it GONE so the WebView underneath
        // takes over. Callers (onPageFinished, error handling, etc.) can
        // call this the instant the page is ready, which for a local
        // file:///android_asset/ page is often only a handful of
        // milliseconds after show() -- long before a staggered entrance
        // (tumble/typewriter/slide) has actually finished playing. Rather
        // than cutting that animation off mid-flight, wait out whatever's
        // left of minDisplayMs before starting the actual fade-out.
        public void hide() {
            hideHandler.removeCallbacksAndMessages(null);
            long elapsed = SystemClock.uptimeMillis() - showStartTime;
            long remaining = minDisplayMs - elapsed;
            if (remaining > 0) {
                hideHandler.postDelayed(this::doHide, remaining);
            } else {
                doHide();
            }
        }

        @Override
        public void hideImmediate() {
            hideHandler.removeCallbacksAndMessages(null);
            doHide();
        }

        private void doHide() {
            stopIdlePulse();
            ringHandler.removeCallbacks(ringLoop);
            if (barAnim != null) {
                barAnim.cancel();
                barAnim = null;
            }
            animate().cancel();
            animate()
                .alpha(0f)
                .setDuration(sd(320))
                .withEndAction(() -> setVisibility(View.GONE))
                .start();
        }

        // Gentle breathing on the wordmark -- a soft alpha + scale pulse --
        // runs continuously until hide() is called, so as long as the
        // WebView is still loading the name keeps visibly UrlObfuscator.decode(new int[] { 60, 16, 242, 204, 188 }, 93) instead
        // of sitting static. Skipped for typewriter, which already has its
        // own blinking cursor doing that job.
        private void startIdlePulse() {
            if (idlePulse != null || style == STYLE_TYPEWRITER || style == STYLE_NONE) return;
            idlePulse = ValueAnimator.ofFloat(0f, 1f);
            idlePulse.setDuration(sd(1300));
            idlePulse.setRepeatMode(ValueAnimator.REVERSE);
            idlePulse.setRepeatCount(ValueAnimator.INFINITE);
            idlePulse.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float pulseAlpha = 0.6f + 0.4f * t;
                float pulseScale = 1f + 0.035f * t;
                for (View letter : letters) {
                    letter.setAlpha(pulseAlpha);
                    letter.setScaleX(pulseScale);
                    letter.setScaleY(pulseScale);
                }
            });
            idlePulse.start();
        }

        // Blink loop for the typewriter cursor -- a plain alpha square-wave
        // rather than a smooth pulse, so it reads as a real text cursor.
        private void startCursorBlink() {
            if (cursorBlink != null || cursor == null) return;
            cursorBlink = ValueAnimator.ofFloat(0f, 1f);
            cursorBlink.setDuration(sd(530));
            cursorBlink.setRepeatMode(ValueAnimator.RESTART);
            cursorBlink.setRepeatCount(ValueAnimator.INFINITE);
            cursorBlink.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                cursor.setAlpha(t < 0.5f ? 1f : 0f);
            });
            cursorBlink.start();
        }

        private void stopIdlePulse() {
            if (idlePulse != null) {
                idlePulse.cancel();
                idlePulse = null;
            }
            if (cursorBlink != null) {
                cursorBlink.cancel();
                cursorBlink = null;
            }
        }
    }

    // A full-bleed, center-cropped video surface for the splash screen.
    //
    // This deliberately does NOT use android.widget.VideoView. VideoView
    // (backed by a SurfaceView) relies on
    // MediaPlayer.setVideoScalingMode(VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
    // to preserve aspect ratio while filling the screen -- and on a lot of
    // real devices that mode is silently ignored, so the video just gets
    // stretched to exactly fill the view (non-uniform scale), distorting
    // anything that isn't already the same aspect ratio as the screen.
    // That's a widely-reported VideoView/MediaPlayer quirk, not something
    // fixable by picking a different scaling-mode constant.
    //
    // TextureView sidesteps it: by default it *also* stretches its content
    // to exactly fill the view, but since we render into it manually we can
    // apply our own Matrix that compensates for that stretch and restores a
    // true uniform-scale-and-crop (the same visual result as ImageView's
    // CENTER_CROP), independent of device/OEM MediaPlayer behavior.
    private static class CropTextureView extends TextureView implements TextureView.SurfaceTextureListener {
        private int videoWidth = 0;
        private int videoHeight = 0;

        CropTextureView(Context context) {
            super(context);
            setSurfaceTextureListener(this);
        }

        void setVideoSize(int width, int height) {
            videoWidth = width;
            videoHeight = height;
            applyCropTransform();
        }

        private void applyCropTransform() {
            int viewWidth = getWidth();
            int viewHeight = getHeight();
            if (viewWidth == 0 || viewHeight == 0 || videoWidth == 0 || videoHeight == 0) return;

            // TextureView's default transform already stretches the buffer
            // non-uniformly to exactly fill (viewWidth x viewHeight). To turn
            // that into a center-crop, scale further around the center by
            // however much the video's aspect ratio differs from the view's.
            float viewRatio = viewWidth / (float) viewHeight;
            float videoRatio = videoWidth / (float) videoHeight;
            float scaleX = 1f, scaleY = 1f;
            if (videoRatio > viewRatio) {
                scaleX = videoRatio / viewRatio;
            } else {
                scaleY = viewRatio / videoRatio;
            }
            Matrix matrix = new Matrix();
            matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
            setTransform(matrix);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            applyCropTransform();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            applyCropTransform();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    }

    // Startup screen built from a user-uploaded file instead of one of the
    // built-in text animations: either a single-play video (custom_splash
    // in res/raw) or a still image (custom_splash in res/drawable).
    // Looked up by resource name at runtime via getIdentifier rather than a
    // generated R reference, since whether that resource even exists
    // depends entirely on whether this specific build actually shipped one.
    private static class CustomSplashView extends FrameLayout implements SplashController {
        private final boolean isVideo;
        private final CropTextureView textureView;
        private MediaPlayer mediaPlayer;
        private Surface playerSurface;
        // False only while an uploaded video still has playback left --
        // there's nothing to wait on for a still image, or when no video
        // resource actually got shipped, so those start out already UrlObfuscator.decode(new int[] { 10, 226, 194, 174 }, 110).
        private boolean videoDone = true;
        private final boolean hasVideoSource;
        private final int videoResId;
        // hide() can be asked to close before the video's finished (the
        // WebView is done loading first, which is the common case) --
        // remember that it was asked, and actually close once
        // onCompletion/onError fires instead of cutting the clip off.
        private boolean pendingHide = false;
        // show() can be called before the SurfaceTexture is ready yet
        // (first launch, cold start) -- remember that playback was
        // requested and start it as soon as the surface actually shows up.
        private boolean pendingPlay = false;
        // Three-dot pulse shown ONLY while the clip has finished playing
        // but the page hasn't -- i.e. exactly the frozen-frame wait. Never
        // shown during normal playback, so it doesn't compete visually
        // with the video itself.
        private final LinearLayout waitDots;
        private final View[] waitDotViews = new View[3];
        private ValueAnimator waitDotsAnim;

        CustomSplashView(Context context, int bgColor, boolean isVideo) {
            super(context);
            this.isVideo = isVideo;
            setBackgroundColor(bgColor);
            setVisibility(View.INVISIBLE);
            setAlpha(0f);

            String pkg = context.getPackageName();
            FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

            if (isVideo) {
                videoResId = context.getResources().getIdentifier(UrlObfuscator.decode(new int[] { 28, 235, 206, 168, 148, 119, 102, 43, 7, 250, 212, 167, 155 }, 127), UrlObfuscator.decode(new int[] { 226, 206, 185 }, 144), pkg);
                hasVideoSource = videoResId != 0;
                CropTextureView tv = new CropTextureView(context);
                if (hasVideoSource) {
                    tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                        @Override
                        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                            tv.onSurfaceTextureAvailable(surface, width, height);
                            playerSurface = new Surface(surface);
                            preparePlayer(context);
                        }
                        @Override
                        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                            tv.onSurfaceTextureSizeChanged(surface, width, height);
                        }
                        @Override
                        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                            if (mediaPlayer != null) {
                                mediaPlayer.release();
                                mediaPlayer = null;
                            }
                            if (playerSurface != null) {
                                playerSurface.release();
                                playerSurface = null;
                            }
                            return tv.onSurfaceTextureDestroyed(surface);
                        }
                        @Override
                        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                            tv.onSurfaceTextureUpdated(surface);
                        }
                    });
                }
                textureView = tv;
                addView(textureView, fill);
            } else {
                textureView = null;
                hasVideoSource = false;
                videoResId = 0;
                ImageView iv = new ImageView(context);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                int resId = context.getResources().getIdentifier(UrlObfuscator.decode(new int[] { 194, 181, 172, 138, 114, 81, 4, 9, 233, 212, 182, 133, 125 }, 161), UrlObfuscator.decode(new int[] { 214, 163, 145, 120, 79, 47, 0, 238 }, 178), pkg);
                if (resId != 0) {
                    iv.setImageURI(Uri.parse(UrlObfuscator.decode(new int[] { 162, 140, 101, 82, 80, 55, 25, 178, 201, 191, 138, 119, 66, 36, 22, 241, 137, 253, 222 }, 195) + pkg + "/" + resId));
                }
                addView(iv, fill);
            }

            // Built once regardless of image/video -- only ever made
            // visible from the video branch's frozen-frame wait (see
            // onCompletion below), but harmless (and unused) for images.
            float density = context.getResources().getDisplayMetrics().density;
            int dotSize = Math.round(8 * density);
            int dotGap = Math.round(10 * density);
            waitDots = new LinearLayout(context);
            waitDots.setOrientation(LinearLayout.HORIZONTAL);
            waitDots.setAlpha(0f);
            waitDots.setVisibility(View.INVISIBLE);
            for (int i = 0; i < waitDotViews.length; i++) {
                View dot = new View(context);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(Color.WHITE);
                dot.setBackground(dotBg);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
                if (i > 0) dotParams.leftMargin = dotGap;
                waitDots.addView(dot, dotParams);
                waitDotViews[i] = dot;
            }
            FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            dotsParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            dotsParams.bottomMargin = Math.round(64 * density);
            addView(waitDots, dotsParams);
        }

        // Starts (or is a no-op if already running) the staggered
        // pulse -- each dot fades/scales up and back down in its own
        // phase, looping until stopWaitDots() is called.
        private void startWaitDots() {
            if (waitDotsAnim != null) return;
            waitDots.setVisibility(View.VISIBLE);
            waitDots.animate().alpha(1f).setDuration(220).start();
            waitDotsAnim = ValueAnimator.ofFloat(0f, 1f);
            waitDotsAnim.setDuration(1000);
            waitDotsAnim.setRepeatCount(ValueAnimator.INFINITE);
            waitDotsAnim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                for (int i = 0; i < waitDotViews.length; i++) {
                    // Each dot's own phase is offset by a third of the
                    // cycle so the pulse visibly travels left-to-right
                    // rather than all three dots moving in lockstep.
                    float phase = (t + (i / (float) waitDotViews.length)) % 1f;
                    float bump = (float) Math.sin(phase * Math.PI);
                    float scale = 0.7f + 0.3f * bump;
                    waitDotViews[i].setScaleX(scale);
                    waitDotViews[i].setScaleY(scale);
                    waitDotViews[i].setAlpha(0.4f + 0.6f * bump);
                }
            });
            waitDotsAnim.start();
        }

        private void stopWaitDots() {
            if (waitDotsAnim != null) {
                waitDotsAnim.cancel();
                waitDotsAnim = null;
            }
            waitDots.animate().cancel();
            waitDots.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> waitDots.setVisibility(View.INVISIBLE))
                .start();
        }

        // Sets up the MediaPlayer once the TextureView's SurfaceTexture is
        // actually available -- can't render into it any earlier than that.
        private void preparePlayer(Context context) {
            if (mediaPlayer != null || playerSurface == null) return;
            String pkg = context.getPackageName();
            MediaPlayer mp = new MediaPlayer();
            mediaPlayer = mp;
            try {
                mp.setDataSource(context, Uri.parse(UrlObfuscator.decode(new int[] { 181, 157, 118, 67, 63, 6, 234, 131, 190, 142, 121, 70, 61, 21, 229, 192, 254, 204, 45 }, 212) + pkg + "/" + videoResId));
                mp.setSurface(playerSurface);
                // Single play, not looped -- see onCompletion below for
                // how the UrlObfuscator.decode(new int[] { 147, 109, 71, 39, 14, 160, 250, 208, 185, 143, 59, 88, 60, 30, 248, 196, 176, 212, 103, 90, 52, 80, 255, 207, 170, 137, 43, 67, 58, 72, 245, 195, 164, 128, 122 }, 229) case is
                // actually handled (freeze on the last frame, not repeat).
                mp.setLooping(false);
                mp.setOnPreparedListener(p -> {
                    textureView.setVideoSize(p.getVideoWidth(), p.getVideoHeight());
                    if (pendingPlay) {
                        pendingPlay = false;
                        videoDone = false;
                        p.seekTo(0);
                        p.start();
                    }
                });
                mp.setOnCompletionListener(p -> {
                    videoDone = true;
                    // If the page isn't ready yet, freeze on the clip's
                    // last frame instead of looping it (annoying to watch
                    // repeat) or leaving it black. Some devices clear the
                    // TextureView's buffer once MediaPlayer hits its
                    // PlaybackCompleted state -- re-seeking to just before
                    // the end forces a redraw so that last frame actually
                    // stays visible while we wait. The pulsing dots make
                    // it visually clear the app hasn't stalled.
                    if (pendingHide) {
                        doHide();
                    } else {
                        p.seekTo(Math.max(0, p.getDuration() - 33));
                        startWaitDots();
                    }
                });
                // A clip that can't actually play (bad codec, corrupt
                // upload, whatever) shouldn't leave the app stuck behind a
                // black screen forever -- treat a playback error the same
                // as having finished.
                mp.setOnErrorListener((p, what, extra) -> {
                    videoDone = true;
                    if (pendingHide) doHide();
                    return true;
                });
                mp.prepareAsync();
            } catch (Exception e) {
                videoDone = true;
                if (pendingHide) doHide();
            }
        }

        public void show() {
            animate().cancel();
            setVisibility(View.VISIBLE);
            setAlpha(0f);
            animate().alpha(1f).setDuration(280).start();
            pendingHide = false;
            stopWaitDots();
            if (hasVideoSource) {
                if (mediaPlayer != null) {
                    videoDone = false;
                    mediaPlayer.seekTo(0);
                    mediaPlayer.start();
                } else {
                    // Surface (and therefore the player) isn't ready yet --
                    // preparePlayer()'s onPrepared will start it instead.
                    pendingPlay = true;
                }
            }
        }

        public void hide() {
            if (hasVideoSource && !videoDone) {
                // The page is done loading, but the intro clip isn't --
                // let it play out before the WebView actually appears
                // instead of cutting it off mid-frame.
                pendingHide = true;
                return;
            }
            doHide();
        }

        @Override
        public void hideImmediate() {
            pendingHide = false;
            doHide();
        }

        private void doHide() {
            pendingHide = false;
            stopWaitDots();
            animate().cancel();
            animate()
                .alpha(0f)
                .setDuration(280)
                .withEndAction(() -> {
                    setVisibility(View.GONE);
                    if (isVideo && mediaPlayer != null) mediaPlayer.pause();
                })
                .start();
        }
    }

    // Startup loading screen for when the Options tab's animation picker is
    // set to UrlObfuscator.decode(new int[] { 153, 115, 82 }, 246) -- rather than showing nothing while the page loads
    // (which used to mean the WebView appeared the instant it was created,
    // flashing black before its own background even painted), this shows
    // the app's own launcher icon centered on the splash background,
    // completely static, and holds it until the page is ready. UrlObfuscator.decode(new int[] { 72, 64, 35 }, 263) means
    // no animated entrance, not no splash.
    private static class StaticIconSplashView extends FrameLayout implements SplashController {
        StaticIconSplashView(Context context, int bgColor) {
            super(context);
            setBackgroundColor(bgColor);
            setVisibility(View.INVISIBLE);
            setAlpha(0f);

            float density = context.getResources().getDisplayMetrics().density;
            int iconSize = Math.round(96 * density);
            ImageView iv = new ImageView(context);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // Looked up by name (same pattern as CustomSplashView's
            // custom_splash asset above) rather than referenced as
            // R.mipmap.ic_launcher, since this generated source doesn't
            // otherwise depend on the built R class. ic_launcher is always
            // present -- either the uploaded logo or the generated default
            // -- so this should never come back 0.
            int iconResId = context.getResources().getIdentifier(UrlObfuscator.decode(new int[] { 113, 84, 9, 25, 245, 198, 188, 146, 120, 74, 60 }, 280), UrlObfuscator.decode(new int[] { 68, 33, 23, 235, 196, 180 }, 297), context.getPackageName());
            if (iconResId != 0) {
                iv.setImageResource(iconResId);
            }
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER;
            addView(iv, iconParams);
        }

        public void show() {
            animate().cancel();
            setVisibility(View.VISIBLE);
            setAlpha(0f);
            animate().alpha(1f).setDuration(220).start();
        }

        public void hide() {
            animate().cancel();
            animate().alpha(0f).setDuration(220)
                .withEndAction(() -> setVisibility(View.GONE))
                .start();
        }

        @Override
        public void hideImmediate() {
            animate().cancel();
            setAlpha(0f);
            setVisibility(View.GONE);
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
        UpdateChecker.check(this);
        // Channel + runtime permission are needed for ANY notification --
        // local (AndroidBridge.showNotification, always available) as well
        // as Firebase push (only if google-services.json was provided) --
        // so both now run unconditionally rather than only when FCM is on.
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(UrlObfuscator.decode(new int[] { 28, 110, 72, 172, 142, 234, 204 }, 63)));

        webView = new WebView(this);
        // A freshly created WebView has no rendered frame yet -- its
        // underlying Surface starts out blank/black, and that shows through
        // for a beat even with setBackgroundColor(WHITE) set, because that
        // color is drawn BY the WebView, but the Surface itself hasn't
        // produced a first frame to draw it onto yet. Making the WebView
        // visible immediately -- which this used to do whenever startup
        // loading was set to UrlObfuscator.decode(new int[] { 63, 9, 232 }, 80) -- is exactly what let that black flash
        // reach the screen, often right before a second flash of the
        // WebView's own white background as the real page started painting.
        // Two mismatched flashes back to back is the UrlObfuscator.decode(new int[] { 3, 236, 254, 221, 182, 220, 111, 82, 60, 22, 183, 193, 189, 157, 103, 87 }, 97)
        // effect.
        //
        // The fix: never show the WebView until it already has real content
        // to show. It now loads fully hidden behind this root FrameLayout's
        // solid near-black background (#050505 above) and only gets
        // revealed in revealWebView() (see onPageFinished below), which
        // runs unconditionally regardless of the splash setting. With
        // startup loading on, that reveal is the existing animated hand-off
        // from the splash overlay; with it set to UrlObfuscator.decode(new int[] { 29, 247, 214 }, 114), it's the same
        // reveal minus the overlay -- a plain near-black hold, then the
        // site, with nothing flashing in between.
        webView.setBackgroundColor(Color.WHITE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        webView.setVisibility(View.GONE);
        // Starts slightly shrunk/faded/dropped so the reveal in
        // onPageFinished below has something to animate from -- otherwise
        // it'd just pop in at full size the instant it's set VISIBLE.
        webView.setAlpha(0f);
        webView.setScaleX(0.94f);
        webView.setScaleY(0.94f);
        webView.setTranslationY(14f);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        // Wrapping the WebView in a SwipeRefreshLayout gets a native
        // swipe-down-to-reload gesture almost for free -- BUT its default
        // canChildScrollUp() check only looks at the WebView's own
        // top-level document scroll offset. Plenty of real sites (chat
        // apps, feeds, anything with an inner UrlObfuscator.decode(new int[] { 236, 212, 164, 146, 153, 114, 82, 43, 86, 227, 131, 248, 150, 99, 65, 59 }, 131) panel)
        // never scroll the document itself -- an inner <div> scrolls
        // instead, so the WebView's own scrollY sits at 0 forever. That
        // makes SwipeRefreshLayout think the page is always UrlObfuscator.decode(new int[] { 245, 199, 242, 133, 120, 74, 110, 25, 227, 219, 230 }, 148)
        // so it hijacks every downward drag anywhere on screen -- including
        // ones meant to scroll that inner panel -- as a refresh gesture,
        // which blocks the real scroll entirely. The capture-phase JS
        // listener registered in onPageFinished below reports the
        // scrollTop of whatever element actually just scrolled (scroll
        // events don't bubble, but they ARE observable via a capture
        // listener on window), and reportScrollTop() uses that -- not
        // webView.getScrollY() -- to decide whether a pull should refresh.
        //
        // That alone still isn't enough for dialogs/bottom sheets: one
        // that just opened (or was never scrolled) reads scrollTop 0,
        // identical to UrlObfuscator.decode(new int[] { 194, 161, 141, 119, 72, 46, 58, 18, 228, 156, 186, 142, 57, 76, 63, 19, 181, 192, 188, 130, 49, 95, 41, 78, 249, 196, 174, 202, 121, 73, 32, 3 }, 165) -- so dragging
        // anywhere inside it still reads as a refresh pull. Fixed below
        // by also gating on where the drag physically started: only a
        // drag beginning within a small band under the status bar (where
        // the real page content actually starts) is allowed to become a
        // refresh at all. A dialog sitting lower on screen -- which is
        // how virtually every bottom sheet / centered modal is laid out
        // -- never has its drags reach that band in the first place, so
        // it's excluded regardless of its own internal scroll state.
        swipeRefresh = new SwipeRefreshLayout(this) {
            @Override
            public boolean canChildScrollUp() {
                return lastKnownScrollTop > 0 || !lastTouchInPullZone;
            }
        };
        final int pullZonePx = (int) (72 * getResources().getDisplayMetrics().density);
        swipeRefresh.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastTouchInPullZone = event.getY() <= pullZonePx;
            }
            return false; // never consume -- this only samples the gesture's start point
        });
        swipeRefresh.setOnRefreshListener(() -> {
            // A pull-to-refresh is the user explicitly asking for the latest
            // version of the page -- a plain webView.reload() could just
            // serve back a cached copy (with 'fast' caching on) and make the
            // refresh gesture feel like it did nothing. Force one real
            // network round trip here, then restore whichever cache mode
            // this build was configured with for normal navigation.
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.reload();
            webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        });
        // Off by default -- the page's own JS can still turn it back on
        // at any point via AndroidBridge.setPullToRefreshEnabled(true).
        swipeRefresh.setEnabled(false);
        swipeRefresh.setColorSchemeColors(Color.parseColor(UrlObfuscator.decode(new int[] { 149, 231, 176, 37, 113, 21, 54 }, 182)));
        swipeRefresh.addView(webView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(swipeRefresh, webParams);

        final SplashController loading = new LoadingSplashView(this, Color.parseColor(UrlObfuscator.decode(new int[] { 228, 214, 53, 20, 115, 82, 177 }, 199)), UrlObfuscator.decode(new int[] { 136, 146, 120, 82, 49, 31, 231, 208, 162, 142, 96, 70, 57 }, 216), 5);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView((View) loading, loadingParams);
        // Always shown, animated styles and UrlObfuscator.decode(new int[] { 134, 110, 65 }, 233) alike -- UrlObfuscator.decode(new int[] { 149, 127, 94 }, 250) just means
        // the static-icon splash above instead of an animated wordmark, not
        // no splash at all. Either way it holds the screen until
        // revealWebView (see onPageFinished below) swaps it for the site.
        loading.show();

        // Shown instead of the WebView's own built-in error page when the
        // main frame fails to load (see onReceivedError below) -- a bare
        // WebView renders Chromium's default UrlObfuscator.decode(new int[] { 92, 79, 43, 24, 230, 193, 160, 196, 109, 77, 53, 64, 30, 232, 220, 181, 151, 123, 91, 52, 18, 182, 154, 222, 211, 50, 17, 112, 79, 174, 141, 236, 196, 37, 9, 38, 2, 242, 159, 254, 166, 80, 115, 31, 17, 63, 208, 249, 132, 180, 86, 108, 8, 36, 208, 231, 156, 190, 71, 117, 11 }, 267) page, which looks like a broken
        // browser rather than part of this app. Tapping it retries the load.
        // Also auto-retries the moment the OS reports connectivity back
        // (see the ConnectivityManager callback near the bottom of
        // onCreate), so on most devices the user never has to tap anything.
        //
        // Styled as a native system-alert card (title / message / full-width
        // action) rather than a full-bleed branded screen. Colors are pulled
        // at runtime from the phone's own light/dark setting -- not baked
        // into the build -- so this always matches whatever mode the rest of
        // the OS is in. The one exception is the action label, which is
        // tinted with this app's own accent color (see offlineAccentColor)
        // the same way iOS/Android tint a dialog's default action with the
        // app's brand color, so the alert still reads as part of THIS app.
        final boolean offlineIsNightMode =
            (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        final String offlineScrimColor = offlineIsNightMode ? UrlObfuscator.decode(new int[] { 63, 2, 99, 73, 168, 135, 230, 197, 36 }, 284) : UrlObfuscator.decode(new int[] { 17, 103, 70, 191, 158, 253, 220, 59, 26 }, 50);
        // Slightly translucent (not fully opaque) card/button fills, so a
        // hint of the dimmed scrim behind still shows through -- a cheap
        // stand-in for a true frosted-glass blur, which would need
        // RenderEffect (API 31+) and a snapshot of whatever's behind the
        // card to do for real.
        final String offlineCardBg = offlineIsNightMode ? UrlObfuscator.decode(new int[] { 96, 39, 185, 146, 252, 236, 184, 47, 11 }, 67) : UrlObfuscator.decode(new int[] { 119, 49, 170, 247, 226, 169, 60, 107, 126 }, 84);
        final String offlineTitleColor = offlineIsNightMode ? UrlObfuscator.decode(new int[] { 70, 194, 229, 132, 167, 70, 89 }, 101) : UrlObfuscator.decode(new int[] { 85, 165, 132, 227, 194, 33, 0 }, 118);
        final String offlineSubtitleColor = offlineIsNightMode ? UrlObfuscator.decode(new int[] { 164, 228, 253, 166, 59, 96, 5 }, 135) : UrlObfuscator.decode(new int[] { 187, 130, 148, 192, 86, 6, 23 }, 152);
        final String offlineButtonBg = offlineIsNightMode ? UrlObfuscator.decode(new int[] { 138, 240, 164, 50, 29, 112, 91, 182, 224 }, 169) : UrlObfuscator.decode(new int[] { 153, 225, 187, 83, 0, 17, 66, 215, 243 }, 186);
        final float offlineDensity = getResources().getDisplayMetrics().density;

        // Outer full-screen scrim so the card reads as a dialog sitting on
        // top of the app, matching how a native UrlObfuscator.decode(new int[] { 165, 133, 41, 75, 40, 8, 235, 193, 160, 150, 104, 79, 81 }, 203) alert
        // dims everything behind it.
        final FrameLayout errorView = new FrameLayout(this);
        errorView.setBackgroundColor(Color.parseColor(offlineScrimColor));
        errorView.setVisibility(View.GONE);

        // The actual rounded card. Entrance/exit animations below target
        // this (not the full-screen scrim) so only the card itself pops
        // in/out while the dim layer just fades. Corner radius and padding
        // are tuned to match a real iOS-style alert card's proportions
        // (roughly 14dp radius, not an exaggerated UrlObfuscator.decode(new int[] { 175, 138, 111, 80, 42, 20, 250, 208 }, 220)) rather than
        // the more rounded, more padded first pass.
        final LinearLayout errorCard = new LinearLayout(this);
        errorCard.setOrientation(LinearLayout.VERTICAL);
        int cardPadH = (int) (18 * offlineDensity);
        int cardPadTop = (int) (14 * offlineDensity);
        int cardPadBottom = (int) (12 * offlineDensity);
        errorCard.setPadding(cardPadH, cardPadTop, cardPadH, cardPadBottom);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor(offlineCardBg));
        cardBg.setCornerRadius(16 * offlineDensity);
        errorCard.setBackground(cardBg);
        errorCard.setElevation(6f * offlineDensity);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            (int) (270 * offlineDensity), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        errorView.addView(errorCard, cardParams);

        final TextView errorTitle = new TextView(this);
        errorTitle.setText(UrlObfuscator.decode(new int[] { 163, 99, 11, 36, 12, 252, 208, 169, 151, 111 }, 237));
        errorTitle.setTextColor(Color.parseColor(offlineTitleColor));
        errorTitle.setTextSize(16);
        errorTitle.setTypeface(errorTitle.getTypeface(), Typeface.BOLD);
        errorTitle.setGravity(Gravity.START);
        errorCard.addView(errorTitle);

        final TextView errorSubtitle = new TextView(this);
        errorSubtitle.setText(UrlObfuscator.decode(new int[] { 174, 113, 89, 58, 9, 252, 152, 165, 147, 97, 70, 42, 82, 230, 216, 170, 128, 45, 79, 36, 4, 231, 205, 164, 146, 96, 64, 99, 22, 238, 128, 214, 176, 137, 121, 73, 52, 28, 236 }, 254));
        errorSubtitle.setTextColor(Color.parseColor(offlineSubtitleColor));
        errorSubtitle.setTextSize(13);
        errorSubtitle.setGravity(Gravity.START);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = (int) (4 * offlineDensity);
        subtitleParams.bottomMargin = (int) (13 * offlineDensity);
        errorCard.addView(errorSubtitle, subtitleParams);

        // Full-width pill button, the one actionable element on the card --
        // setOnTouchListener below scales + dims it slightly on press since
        // a bare TextView (unlike a Button) has no tap feedback of its own.
        // Label is plain (not accent-tinted) to match the reference, which
        // uses the same neutral title/button color throughout.
        final TextView retryText = new TextView(this);
        retryText.setText(UrlObfuscator.decode(new int[] { 93, 75, 57, 30, 242 }, 271));
        retryText.setTextColor(Color.parseColor(offlineTitleColor));
        retryText.setTextSize(14);
        retryText.setTypeface(retryText.getTypeface(), Typeface.BOLD);
        retryText.setGravity(Gravity.CENTER);
        int retryPadV = (int) (11 * offlineDensity);
        retryText.setPadding(0, retryPadV, 0, retryPadV);
        GradientDrawable retryPill = new GradientDrawable();
        retryPill.setColor(Color.parseColor(offlineButtonBg));
        retryPill.setCornerRadius(999f);
        retryText.setBackground(retryPill);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorCard.addView(retryText, retryParams);

        FrameLayout.LayoutParams errorParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(errorView, errorParams);

        // Lightweight cover for every navigation AFTER the first one --
        // e.g. tapping a link/button that loads a brand-new page. Without
        // this, the WebView briefly shows its own blank white/black frame
        // between the old page unloading and the new one's first paint.
        // The very first load doesn't need this: the full splash above is
        // already covering that gap.
        final FrameLayout navOverlay = new FrameLayout(this);
        navOverlay.setBackgroundColor(Color.parseColor(UrlObfuscator.decode(new int[] { 3, 15, 110, 77, 172, 139, 234 }, 288)));
        navOverlay.setVisibility(View.GONE);
        final BouncingDotsView navDots = new BouncingDotsView(this, Color.parseColor(UrlObfuscator.decode(new int[] { 21, 19, 70, 213, 128, 148, 181 }, 54)));
        int navDotsWidth = (int) (84 * getResources().getDisplayMetrics().density);
        int navDotsHeight = (int) (36 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams navDotsParams = new FrameLayout.LayoutParams(navDotsWidth, navDotsHeight);
        navDotsParams.gravity = Gravity.CENTER;
        navOverlay.addView(navDots, navDotsParams);
        FrameLayout.LayoutParams navOverlayParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(navOverlay, navOverlayParams);
        // Flips true right after the first successful page load -- used to
        // skip navOverlay on that first load only (the splash already has
        // it covered) and show it on every navigation after that.
        final boolean[] hasLoadedOnce = { false };

        setContentView(root);

        // Makes the system back button/gesture navigate the WebView's own
        // history first (like a real browser back) instead of immediately
        // closing the Activity/exiting the app. Only falls through to the
        // default UrlObfuscator.decode(new int[] { 34, 30, 236, 208 }, 71) behavior once there's no more WebView history to
        // go back to. Implemented via OnBackPressedCallback (not the older
        // onBackPressed() override) so it also plays nicely with Android
        // 13+'s predictive-back swipe gesture, not just a hardware/nav-bar
        // back button press.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Shared retry path for the tap target AND the auto-retry-on-
        // reconnect callback below -- plays a quick shrink-and-fade exit
        // before actually reloading, instead of just vanishing. Only the
        // card shrinks; the scrim behind it just fades out with it.
        final Runnable[] doRetry = new Runnable[1];
        doRetry[0] = () -> {
            loading.show();
            // A retry (whether tapped manually or fired automatically when
            // connectivity comes back) means the previous attempt failed --
            // there's no good cached success response to speed this up with,
            // so this should always be a genuine network attempt rather than
            // risking a cache hit on a stale/incomplete response.
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.reload();
            webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            errorCard.animate().scaleX(0.94f).scaleY(0.94f).setDuration(180).start();
            errorView.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> {
                    errorView.setVisibility(View.GONE);
                    errorView.setAlpha(1f);
                    errorCard.setScaleX(1f);
                    errorCard.setScaleY(1f);
                }).start();
        };

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        // Off by default in WebView -- without this, a page calling
        // navigator.geolocation never even reaches
        // onGeolocationPermissionsShowPrompt below, it just fails silently.
        settings.setGeolocationEnabled(true);
        // Chromium needs a hardware-composited layer to decode and paint a
        // video's first frame -- without this, <video> elements still play
        // fine on tap (audio/duration both work), but the thumbnail/poster
        // frame never renders and falls back to a generic placeholder icon
        // instead of an actual preview. android:hardwareAccelerated=UrlObfuscator.decode(new int[] { 44, 5, 227, 208 }, 88)
        // on <application> (see AndroidManifest.xml) covers the window as a
        // whole, but WebView's own layer type can still default to
        // software on some OEM builds, so it's set explicitly here too.
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // Matches how a normal mobile browser tab handles inline video
        // (autoplay muted, no tap-to-start-decoding required) -- without
        // this, WebView can leave a video's decoder totally uninitialized
        // until playback is explicitly requested, which is the other half
        // of why the poster frame never showed up.
        settings.setMediaPlaybackRequiresUserGesture(false);
        // Without these two, WebView ignores the page's own
        // <meta name=UrlObfuscator.decode(new int[] { 31, 225, 194, 177, 149, 107, 81, 54 }, 105)> tag and lays it out at a fixed desktop
        // width (980px) instead, then scales the result to fit -- which is
        // exactly what produces oversized icons/buttons and title text
        // that overflows off the right edge instead of wrapping, since the
        // page's responsive CSS never actually saw a phone-width viewport.
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
        // Google's own sign-in pages detect the UrlObfuscator.decode(new int[] { 65, 185, 207, 161, 223 }, 122) token (and the
        // UrlObfuscator.decode(new int[] { 221, 207, 187, 155, 110, 73, 43, 75, 219, 140, 153, 192 }, 139) segment) that stock WebView adds to its user-agent
        // and use it to silently block OAuth inside embedded WebViews -- the
        // page loads fine but the sign-in form's JS just no-ops, so tapping
        // UrlObfuscator.decode(new int[] { 210, 222, 162, 141 }, 156) appears to do nothing. Stripping those two markers from an
        // otherwise-real device UA (rather than hardcoding a fake one) is
        // the standard workaround: it still looks like a legitimate mobile
        // Chrome UA, just without the tell.
        //
        // Heads up: this is not a real fix, it's evading a detection Google
        // runs specifically to prevent embedded WebViews from harvesting
        // Google credentials. It can stop working with no warning on any
        // Chrome/WebView update, and if Google's backend flags the traffic
        // anyway, the consequence isn't just this failing again -- it's the
        // app's OAuth client getting throttled or suspended. The only path
        // Google actually guarantees is native sign-in via Credential
        // Manager (see googleSignInEnabled above), which needs nothing more
        // than a free Web Client ID.
        String defaultUA = settings.getUserAgentString();
        String spoofedUA = defaultUA.replace(UrlObfuscator.decode(new int[] { 150, 236, 156, 124 }, 173), "").replaceAll("Version/[0-9.]+\s", "");
        settings.setUserAgentString(spoofedUA);
        // Needed for Google/Firebase-style UrlObfuscator.decode(new int[] { 205, 180, 155, 117, 26, 48, 22, 183, 193, 188, 128, 123, 18, 33, 31, 255, 219, 189 }, 190) flows: that JS
        // calls window.open() on the auth provider's URL, and Chrome/Firebase
        // then closes that popup itself once sign-in finishes. Without these
        // two, WebView either can't open the popup at all or opens it detached
        // from the parent page's session, so the auth handler gets a request
        // it can't reconcile and shows UrlObfuscator.decode(new int[] { 155, 134, 104, 12, 57, 15, 248, 221, 162, 149, 113, 65, 39, 66, 224, 195, 203, 183, 146, 114, 27, 51, 10, 184, 222, 184, 131, 117, 95, 59, 21 }, 207).
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        // A raw WebView lets the user pinch-zoom and shows on-screen zoom
        // controls like a browser tab -- fine for browsing a random site,
        // but it's the biggest tell that UrlObfuscator.decode(new int[] { 148, 151, 119, 78, 124, 18, 233, 153, 178, 130, 101, 65, 116, 18, 178, 198, 181, 141, 46, 93, 45, 12, 239 }, 224) for an
        // app that's supposed to read as native. The page's own
        // <meta name=UrlObfuscator.decode(new int[] { 135, 121, 74, 57, 29, 227, 217, 190 }, 241)> (handled above) is what actually controls
        // layout sizing; this only turns off the *manual* pinch/zoom-button
        // affordance on top of that.
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        // A long-press on selectable page text opens Android's native
        // text-selection action mode (the Translate / Copy / Share / more
        // popup) -- another dead giveaway it's a WebView, and it can get
        // stuck sitting on top of the page since nothing in a normal wrapped
        // app ever dismisses it for the user. setLongClickable(false) alone
        // isn't enough on its own to stop Chromium from starting selection
        // (it still owns long-press internally), so this also swallows the
        // long-click event at the View level and returns true to mark it
        // consumed -- taps, scrolling, links, and buttons on the page are
        // untouched since those aren't long-clicks.
        //
        // EDIT_TEXT_TYPE is the one exception: that's the hit-test result
        // for a focused/editable field, and the exact same ActionMode this
        // is built to suppress is also what draws the text-insertion handle
        // and the UrlObfuscator.decode(new int[] { 114, 64, 51, 43, 27, 189, 218, 169, 149, 116, 24, 52, 26, 252, 196, 177, 157, 112, 66, 43 }, 258) bubble over a text input. Blanket-
        // consuming every long-click was taking that down too, so a form
        // field's own clipboard tray/suggestion would highlight on tap but
        // never actually insert anything -- returning false here for editable
        // hits lets Chromium handle those long-clicks normally while every
        // other long-click on the page (the actual giveaway) stays swallowed.
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = webView.getHitTestResult();
            return result == null || result.getType() != WebView.HitTestResult.EDIT_TEXT_TYPE;
        });
        webView.setHapticFeedbackEnabled(false);
        // Chrome's blue overscroll glow at the top/bottom edges is another
        // dead giveaway it's a WebView -- native views don't do that.
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Same idea for the thin scrollbar indicator that flashes on the
        // right edge while scrolling -- that's Android's default browser
        // chrome, not something a native screen shows. Content still
        // scrolls completely normally; this only hides the indicator
        // itself, not the scrolling behavior.
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        // Local assets don't need HTTP cache tuning (they load straight off
        // disk already), but any fetch()/XHR calls the page makes to a real
        // backend do. This build uses 'fast': serves a cached response instantly, skipping the network check -- quicker, but can go stale until evicted or refreshed
        // (see the SwipeRefreshLayout listener above, which forces a real
        // network reload either way). setDatabaseEnabled covers older
        // WebSQL-based storage some libraries still fall back to.
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setDatabaseEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Exposes vibrate() (used for a light haptic tap on buttons/links,
        // see the injected click-listener script in onPageFinished below)
        // and applyThemeColor() (reads the page's own
        // <meta name=UrlObfuscator.decode(new int[] { 103, 90, 52, 29, 234, 131, 174, 131, 103, 69, 59 }, 275)>, if it has one, and recolors the
        // status/nav bars to match instead of leaving them a fixed color
        // that may clash with the page). Safe to expose here specifically
        // because this WebView only ever loads this app's own bundled
        // local assets, never arbitrary third-party pages.
        webView.addJavascriptInterface(new AndroidBridge(), UrlObfuscator.decode(new int[] { 101, 45, 6, 243, 207, 214, 186, 191, 110, 82, 62, 30, 253 }, 292));

        // Auto-retry the moment the OS reports a usable connection again,
        // so the offline screen clears itself on most devices without
        // waiting for a tap. Falls back to manual UrlObfuscator.decode(new int[] { 110, 56, 8, 183, 194, 186, 212, 97, 87, 37, 2, 246 }, 58) if this
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

        // Brings the WebView in with a little life instead of just
        // flipping it to VISIBLE at full size the instant the splash
        // clears -- a quick scale/fade/settle so the site's first frame
        // feels like it's arriving, not just appearing.
        final Runnable revealWebView = () -> {
            loading.hide();
            webView.setVisibility(View.VISIBLE);
            webView.animate().cancel();
            webView.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(460)
                .setInterpolator(new OvershootInterpolator(0.9f))
                .start();
        };

        // Guaranteed-first-script injection where the installed WebView
        // supports it (Chromium ~M94+, i.e. the large majority of real
        // devices) -- runs before ANY of the page's own scripts, on every
        // navigation, automatically. Falls back to the best-effort
        // onPageStarted injection below on older WebView versions that
        // don't support this feature at all.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentStartScriptSupported = true;
            WebViewCompat.addDocumentStartJavaScript(
                webView, SHOW_OPEN_FILE_PICKER_POLYFILL, java.util.Collections.singleton("*"));
        }

        webView.setWebViewClient(new WebViewClient() {
            // True once the current navigation has failed, so onPageFinished
            // (which WebView still calls after an error) knows not to reveal
            // the WebView underneath the error screen.
            private boolean hasError = false;

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                hasError = false;
                // Only needed as a fallback here: if
                // WebViewCompat.addDocumentStartJavaScript (see onCreate)
                // was supported on this device, the polyfill is already
                // guaranteed to run before the page's own scripts and
                // doesn't need re-injecting on every navigation. Where it
                // isn't supported (older WebView versions), this
                // evaluateJavascript call is best-effort -- it usually wins
                // the race against the page's own inline scripts, but
                // isn't a hard guarantee the way the document-start path
                // is.
                if (!documentStartScriptSupported) {
                    view.evaluateJavascript(SHOW_OPEN_FILE_PICKER_POLYFILL, null);
                }
                if (hasLoadedOnce[0]) {
                    navOverlay.animate().cancel();
                    navOverlay.setAlpha(1f);
                    navOverlay.setVisibility(View.VISIBLE);
                }
            }


            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                navOverlay.animate().cancel();
                navOverlay.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> navOverlay.setVisibility(View.GONE)).start();
                if (hasError) return;
                revealWebView.run();
                hasLoadedOnce[0] = true;
                // Picks up the page's <meta name=UrlObfuscator.decode(new int[] { 63, 2, 236, 197, 162, 203, 102, 75, 47, 13, 243 }, 75)> (if it has
                // one) to recolor the system bars, and wires up a light
                // haptic tap on buttons/links -- both no-ops wrapped in
                // try/catch so a page that doesn't have a theme-color tag,
                // or that runs somewhere AndroidBridge isn't defined (the
                // popup WebView below doesn't get it), just silently skips
                // rather than throwing a JS error.
                view.evaluateJavascript(
                    UrlObfuscator.decode(new int[] { 116, 29, 239, 215, 187, 131, 127, 90, 58, 91, 187, 202 }, 92) +
                    // Forces correct mobile scaling regardless of what the
                    // wrapped site itself declares. setUseWideViewPort/
                    // setLoadWithOverviewMode (see WebSettings above) handle
                    // the common case of a page with NO viewport tag at all,
                    // but a page whose own tag is present yet wrong (a
                    // leftover desktop width, a bad initial-scale, or CSS
                    // that just ignores the viewport and sets a fixed pixel
                    // width somewhere) can still render oversized and
                    // overflow off both edges -- exactly what makes a
                    // wrapped site read as UrlObfuscator.decode(new int[] { 12, 172, 220, 175, 139, 123, 78, 50, 0, 168, 131, 172, 142, 116, 63, 95, 125, 18, 250, 206, 176, 142, 114, 22, 52, 4, 227 }, 109).
                    // Rewriting the tag to a known-good mobile value, and
                    // clamping the root elements so nothing can force page
                    // width past the viewport, covers those cases too.
                    UrlObfuscator.decode(new int[] { 10, 239, 197, 160 }, 126) +
                    "var vp=document.querySelector('meta[name=\"viewport\"]');" +
                    UrlObfuscator.decode(new int[] { 230, 200, 229, 205, 125, 90, 96, 19, 241, 214, 248, 128, 108, 65, 52, 13, 26, 240, 201, 242, 152, 104, 92, 57, 3, 243, 240, 184, 150, 127, 84, 62, 27, 166, 138, 161, 142, 126, 72, 111, 78, 189, 211, 180, 205, 113, 68, 52, 30, 10, 233, 206, 178, 152, 108, 76, 50, 94, 178, 218, 178, 159, 116, 23, 99, 73, 251, 197, 174, 157, 121, 71, 53, 18, 162, 141, 248, 134, 110, 67, 74, 51, 24, 242, 207, 244, 145, 125, 86, 50, 91, 245, 195, 162, 148, 126, 75, 13, 5, 229, 199, 174, 193, 126, 87, 111, 94, 249 }, 143) +
                    UrlObfuscator.decode(new int[] { 214, 207, 240, 142, 121, 79, 27, 13, 236, 197, 191, 151, 97, 71, 55, 89, 183, 204, 161, 131, 120, 78, 36, 29, 175, 139, 225, 146, 109, 71, 54, 9, 189, 251, 219, 171, 149, 120, 95, 116, 15, 254, 210, 161, 156, 63, 18, 56, 30, 230, 218, 164, 141, 103, 7, 58, 11, 230, 202, 160, 217, 50, 12, 113, 76, 95, 243, 220, 164, 146, 119, 76, 53, 90, 229, 214, 181, 159, 119, 12, 97, 65, 190, 129, 236, 158, 121, 76, 58, 74, 245, 198, 165, 143, 99, 67, 44, 58, 67, 243, 211, 252, 211, 34 }, 160) +
                    UrlObfuscator.decode(new int[] { 216, 182, 199, 47, 73, 35, 8, 255, 196, 173, 137, 114, 11, 35, 6, 246, 228, 172, 186, 147, 120, 82, 47, 56, 224, 241, 179, 222, 50, 107, 12, 19, 255, 212, 189, 129, 100, 72, 29, 3, 236, 223, 183, 137, 119, 80, 5, 11, 249, 135, 150, 247, 134 }, 177) +
                    UrlObfuscator.decode(new int[] { 180, 128, 114, 63, 77, 41, 65, 255, 213, 186, 141, 122, 83, 59, 0, 189, 209, 163, 149, 110, 90, 40, 41, 231, 207, 164, 141, 105, 82, 109, 67, 240, 214, 184, 140, 154, 57, 20, 103, 8, 238, 151, 177, 147, 43, 18, 11, 44, 243, 223, 180, 157, 97, 68, 40, 61, 227, 204, 191, 151, 105, 87, 48, 37, 235, 217, 231, 228 }, 194) +
                    UrlObfuscator.decode(new int[] { 160, 134, 63, 68, 42, 22, 249, 239, 164, 132, 125, 77, 41, 18, 184, 131, 171, 150, 108, 76, 19, 60, 18, 248, 194, 161, 148, 121, 79, 123, 2, 253, 215, 166, 153, 42, 30, 126, 93, 250, 220, 235, 128, 101, 87, 41, 23, 240, 194, 172, 149, 59, 112, 72, 56, 14, 253, 214, 182, 143, 58, 78, 111, 28, 250, 214, 181, 149, 97, 15, 36, 1, 251, 197, 187, 156, 102, 72, 49, 95, 174, 213, 164, 130, 148, 119, 73, 113, 15, 255, 193, 172, 218, 101, 92, 46, 22, 191, 208, 180, 133, 123, 94, 56, 81, 187, 153, 248, 194, 39, 76, 41, 19, 237, 211, 180, 190, 144, 105, 7, 47, 31, 225, 204, 250, 133, 124, 78, 54, 95, 240, 212, 165, 155, 126, 88, 113, 91, 185, 152, 226, 199, 108, 73, 51, 13, 243, 212, 222, 176, 137, 39, 70, 125, 66 }, 211) +
                    UrlObfuscator.decode(new int[] { 128, 108, 65, 52, 13, 26, 240, 201, 242, 147, 127, 88, 60, 89, 247, 197, 164, 150, 124, 85, 19, 7, 231, 193, 168, 195, 121, 93, 97, 92, 251 }, 228) +
                    UrlObfuscator.decode(new int[] { 136, 119, 82, 38, 18, 248, 135, 171, 196, 119, 86 }, 245) +
                    "try{var m=document.querySelector('meta[name=\"theme-color\"]');" +
                    UrlObfuscator.decode(new int[] { 111, 67, 108, 14, 164, 135, 183, 182, 144, 121, 83, 44, 84, 216, 214, 179, 132, 122, 93, 55, 48, 227, 217, 171, 137, 104, 10, 109, 43, 231, 204, 181, 137, 108, 64, 1, 16, 232, 196, 216, 187, 211, 125, 75, 42, 21, 225, 227, 190, 144, 121, 86, 17, 30, 252, 192, 188, 196, 119, 106, 36, 13, 250, 200, 175, 129, 70, 81, 43, 5, 231, 250, 144, 188, 140, 107, 86, 32, 44, 255, 211, 184, 145, 80, 93, 61, 31, 253, 134, 160, 194, 108, 79, 61, 41, 243, 210, 183, 141, 97, 87, 53, 5, 87, 185, 222, 179, 149, 110, 92, 54, 3, 177, 156, 168, 143, 53, 22, 121, 84, 243 }, 262) +
                    UrlObfuscator.decode(new int[] { 106, 85, 52, 0, 240, 218, 249, 149, 38, 85, 48 }, 279) +
                    UrlObfuscator.decode(new int[] { 92, 53, 31, 254, 205, 165, 202, 32, 87, 86, 48, 25, 243, 204, 244, 166, 71, 86, 56, 17, 230, 220, 187, 149, 88, 78, 62, 25, 229, 200, 185, 171, 103, 82, 40, 1, 173, 216, 181, 136, 110, 123, 81, 42, 82, 196, 229, 184, 150, 115, 68, 58, 29, 247, 250, 176, 128, 123, 71, 46, 31, 201, 197, 188, 134, 99, 27, 49, 22, 246, 199, 250 }, 296) +
                    UrlObfuscator.decode(new int[] { 90, 50, 31, 238, 215, 188, 150, 99, 24, 52, 16, 247, 247, 167, 149, 97, 90, 1, 5, 248, 222, 172, 134, 98, 84, 109, 67, 224, 206, 168, 131, 148, 57, 17, 58, 14, 244, 218, 172, 158, 121, 91, 124, 22, 187, 202 }, 62) +
                    "var t=e.target;var el=t&&t.closest?t.closest('button,a,[role=\"button\"],input[type=\"button\"],input[type=\"submit\"]'):null;" +
                    UrlObfuscator.decode(new int[] { 38, 8, 165, 201, 167, 204, 47, 95, 46, 8, 225, 203, 180, 204, 64, 78, 91, 44, 18, 245, 223, 152, 139, 113, 83, 49, 16, 178, 149, 147, 159, 116, 93, 33, 4, 232, 233, 184, 128, 108, 64, 35, 75, 242, 202, 160, 147, 97, 107, 91, 116, 7, 218, 212, 189, 138, 120, 95, 49, 54, 225, 219, 181, 151, 106, 0, 59, 5, 233, 216, 168, 156, 98, 14, 108, 95, 254 }, 79) +
                    UrlObfuscator.decode(new int[] { 29, 83, 234, 207, 169, 158, 51, 2, 37, 10, 245, 212, 160, 144, 122, 25, 53, 70, 245, 208 }, 96) +
                    // Polyfills the Web Share API on top of the real Android
                    // share sheet -- most WebView builds don't implement
                    // navigator.share at all, so a page's own UrlObfuscator.decode(new int[] { 34, 248, 206, 188, 136 }, 113) button
                    // either does nothing or falls back to a hand-rolled
                    // copy-link menu instead of the native chooser. Only
                    // installed when the page doesn't already have a working
                    // navigator.share (some newer WebView versions do).
                    UrlObfuscator.decode(new int[] { 246, 211, 185, 164, 151, 123, 20, 44, 19, 247, 220, 184, 129, 59, 117, 61, 22, 227, 223, 166, 138, 79, 94, 34, 14, 238, 205, 225, 192, 68, 74, 39, 16, 238, 201, 219, 156, 143, 117, 95, 61, 28, 182, 196, 190, 148, 102, 86, 116, 87, 177, 193, 175, 155, 101, 76, 43, 29, 231, 213, 232, 150, 108, 66, 48, 4, 169, 228 }, 130) +
                    UrlObfuscator.decode(new int[] { 253, 211, 167, 153, 104, 79, 57, 3, 249, 132, 186, 128, 102, 84, 32, 89, 229, 215, 175, 131, 139, 119, 82, 50, 83, 254, 216, 172, 150, 63, 78 }, 147) +
                    UrlObfuscator.decode(new int[] { 192, 162, 150, 96, 29, 91, 63, 9, 253, 199, 166, 130, 101, 12 }, 164) +
                    UrlObfuscator.decode(new int[] { 193, 166, 138, 105, 112, 62, 11, 252, 194, 165, 143, 72, 91, 33, 3, 225, 192, 234, 144, 106, 64, 50, 58, 86, 206, 200, 169, 147, 119, 95, 127, 18, 244, 192, 178, 220, 101, 89, 59, 2, 232, 208, 183, 205, 46, 1, 107, 53, 241, 214, 170, 140, 102, 8, 91, 63, 9, 253, 149, 174, 156, 96, 67, 42, 9, 179, 148, 251, 221, 67, 91, 60, 4, 226, 204, 226, 141, 105, 83, 39, 75, 241, 209, 174, 157, 124, 56, 25, 116, 85, 160 }, 181) +
                    UrlObfuscator.decode(new int[] { 180, 128, 112, 86, 48, 15, 160, 207, 204, 178, 145, 114, 73, 60, 86, 229, 211, 166, 155, 127, 68, 52, 88, 166, 149, 176, 143, 106, 94, 42, 0, 175, 195, 236, 159, 113, 71, 53, 21, 13, 240, 157, 140, 137, 117, 84, 49, 4, 243, 155, 166, 150, 120, 84, 51, 27, 166, 200, 229, 208, 119, 84, 115 }, 198) +
                    UrlObfuscator.decode(new int[] { 185, 151, 99, 93, 52, 19, 229, 223, 189, 192, 110, 77, 37, 57, 225, 201, 181, 131, 56, 66, 54, 12, 226, 212, 214, 177, 147, 52, 18, 33, 11, 253, 195, 163, 135, 122, 19, 38, 3, 229, 202, 245, 144, 55 }, 215) +
                    UrlObfuscator.decode(new int[] { 149, 122, 69, 36, 16, 224, 202, 233, 133, 214, 101, 64 }, 232) +
                    // Reports the scrollTop of whichever element just
                    // scrolled -- document or any nested panel -- so native
                    // knows whether a pull-to-refresh gesture is actually
                    // safe (see SwipeRefreshLayout override above). Scroll
                    // events don't bubble, so this has to be a capture
                    // listener on window to see scrolling from any
                    // descendant, not just the document itself. Throttled
                    // with a trailing rAF flag so a fast scroll doesn't
                    // spam the JS bridge with a call per pixel.
                    UrlObfuscator.decode(new int[] { 141, 106, 78, 45, 28, 242, 155, 243, 134, 121, 65, 42, 2, 251, 133, 149, 182, 105, 73, 34, 23, 235, 202, 166, 178, 99, 109, 81, 49, 16, 217, 213, 172, 150, 115, 31, 46, 3, 250, 220, 181, 159, 120, 0, 18, 51, 234, 196, 173, 154, 104, 79, 33, 55, 224, 208, 174, 140, 147, 92, 82, 41, 21, 254, 132, 172, 133, 99, 80, 111 }, 249) +
                    UrlObfuscator.decode(new int[] { 124, 72, 58, 71, 246, 192, 170, 135, 107, 79, 39, 98, 24, 252, 208, 168, 159, 34 }, 266) +
                    UrlObfuscator.decode(new int[] { 108, 83, 55, 28, 248, 193, 251, 149, 119, 86, 20, 6, 234, 192, 185, 160, 98, 89, 61, 13, 233, 195, 183, 204, 36, 81, 34, 18, 16, 242, 209, 251, 215, 124, 76, 54, 20, 226, 220, 187, 157, 58, 84, 121, 20 }, 283) +
                    UrlObfuscator.decode(new int[] { 88, 54, 71, 254, 200, 162, 143, 99, 71, 47, 78, 244, 192, 176, 150, 112, 79, 123, 47, 27, 243, 216, 178, 148, 126, 5, 35, 4, 224, 209, 232 }, 49) +
                    UrlObfuscator.decode(new int[] { 48, 4, 241, 234, 219, 174, 136, 90, 84, 48, 21, 246, 194, 188, 155, 125, 116, 35, 17, 226, 203, 229, 138, 126, 68, 42, 28, 238, 201, 171, 204, 42, 89, 49, 5, 17, 250, 212, 178, 156, 39, 95, 57, 27, 229, 208, 239 }, 66) +
                    UrlObfuscator.decode(new int[] { 37, 19, 227, 144, 170, 130, 48, 4, 46, 68, 253, 201, 181, 129, 96, 80, 101, 68, 228, 142, 203, 191, 143, 123, 94, 46, 87, 246, 216, 178, 144, 64, 74, 34, 20, 173, 146, 243, 220, 37, 20, 47, 71, 252, 198, 180, 130, 97, 87, 120, 5, 239, 252, 203, 176, 153, 117, 78, 119, 11, 244, 196, 186, 152, 127, 91, 63, 23, 202, 194, 168, 129, 110, 68, 61, 83 }, 83) +
                    UrlObfuscator.decode(new int[] { 18, 226, 208, 225, 148, 144, 110, 0, 57, 23, 165, 220, 180, 217, 101, 86, 38, 28, 254, 221, 132, 128, 126, 23, 124, 80 }, 100) +
                    UrlObfuscator.decode(new int[] { 28, 242, 155, 165, 152, 126, 75, 33, 26, 162, 234, 164, 141, 122, 72, 47, 1, 198, 209, 171, 133, 103, 122, 24, 123, 61, 245, 222, 171, 151, 126, 82, 23, 6, 250, 214, 182, 149, 33, 92, 40, 28, 228, 216, 189, 187, 100, 84, 42, 8, 239, 246, 174, 144, 214, 101, 124, 50, 31, 232, 214, 177, 147, 84, 71, 61, 23, 245, 212, 254, 157, 107, 93, 35, 25, 254, 250, 171, 149, 105, 73, 40, 55, 237, 209, 232, 171, 145, 109, 64, 107, 83, 162, 197 }, 117) +
                    UrlObfuscator.decode(new int[] { 251, 140, 255, 158, 46, 85, 50, 42, 27, 180, 135, 166, 135, 122, 89, 35, 21, 253, 156, 182, 219, 106, 77 }, 134) +
                    UrlObfuscator.decode(new int[] { 234, 159, 253, 221, 40 }, 151),
                    null);
            }

            // UrlObfuscator.decode(new int[] { 255, 162, 132, 118, 77, 55, 7, 161, 204, 214, 176, 150 }, 168) mode: every GET request the page makes (the main
            // document, its scripts/styles/images, its own fetch()/XHR calls
            // -- shouldInterceptRequest sees all of it) goes through
            // OfflineCache. First-ever launch with no connection and nothing
            // cached yet still falls through to onReceivedError/showOffline()
            // below, same as before.
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!UrlObfuscator.decode(new int[] { 254, 157, 163 }, 185).equalsIgnoreCase(request.getMethod())) {
                    return super.shouldInterceptRequest(view, request);
                }
                String url = request.getUrl().toString();
                // 'fast': serve whatever's already cached immediately -- the page
                // never blocks on a network round trip it doesn't have to --
                // and kick off a background refresh so the cache doesn't go
                // stale forever. Only blocks on the network when nothing's
                // cached yet for this exact URL.
                WebResourceResponse cachedFirst = OfflineCache.tryCache(getApplicationContext(), url);
                if (cachedFirst != null) {
                    if (isNetworkAvailable()) {
                        OfflineCache.refreshInBackground(getApplicationContext(), url, request.getRequestHeaders());
                    }
                    return cachedFirst;
                }
                if (isNetworkAvailable()) {
                    WebResourceResponse fresh = OfflineCache.tryNetwork(getApplicationContext(), url, request.getRequestHeaders());
                    if (fresh != null) return fresh;
                }
                return super.shouldInterceptRequest(view, request);
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
                navOverlay.animate().cancel();
                navOverlay.setVisibility(View.GONE);
                loading.hideImmediate();
                webView.setVisibility(View.GONE);
                // Reset back to the pre-reveal state so a retry that
                // succeeds gets the same entrance animation again, instead
                // of popping straight in at full size (its alpha/scale are
                // already 1 from the reveal that just got hidden here).
                webView.setAlpha(0f);
                webView.setScaleX(0.94f);
                webView.setScaleY(0.94f);
                webView.setTranslationY(14f);
                errorView.setVisibility(View.VISIBLE);
                errorView.setAlpha(0f);
                errorView.animate().alpha(1f).setDuration(220).start();
                errorCard.setScaleX(0.88f);
                errorCard.setScaleY(0.88f);
                errorCard.setTranslationY(18f);
                errorCard.animate()
                    .scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
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

            // Handles the site itself asking for camera/mic access -- a
            // UrlObfuscator.decode(new int[] { 153, 138, 105, 73, 102, 52, 214 }, 202) feature or a video-chat widget using getUserMedia().
            // Without this override the WebView auto-denies every such
            // request, which is what was showing as UrlObfuscator.decode(new int[] { 152, 155, 116, 93, 37, 23, 181, 196, 182, 128, 124, 89, 60, 29, 228, 195, 165, 224, 41, 8, 103, 70, 165, 132, 227, 194, 33, 0, 31, 126, 82, 179, 155, 190, 156, 118, 94, 51, 17, 180, 220, 160, 209, 101, 65, 47, 27, 237, 194, 166, 136, 106, 75, 35 }, 219) -- the app never even asked Android for
            // the underlying runtime permission, regardless of whether the
            // person would have said yes. Camera and mic are requested
            // independently of each other: a page that only asked for one
            // only gets asked (and only ends up granted) for that one, even
            // if it later asks for the other too.
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                java.util.List<String> requested = java.util.Arrays.asList(request.getResources());
                java.util.List<String> neededAndroidPerms = new java.util.ArrayList<>();
                if (requested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    neededAndroidPerms.add(Manifest.permission.CAMERA);
                }
                if (requested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    neededAndroidPerms.add(Manifest.permission.RECORD_AUDIO);
                }
                if (neededAndroidPerms.isEmpty()) {
                    // Nothing else in a PermissionRequest is backed by a
                    // declared runtime permission here -- deny rather than
                    // silently hang.
                    request.deny();
                    return;
                }

                java.util.List<String> stillMissing = new java.util.ArrayList<>();
                for (String perm : neededAndroidPerms) {
                    if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) stillMissing.add(perm);
                }
                if (stillMissing.isEmpty()) {
                    request.grant(request.getResources());
                    return;
                }

                // Ask Android for whichever runtime permission(s) are still
                // missing and hold onto the WebView's request until that
                // answer comes back.
                pendingWebPermissionRequest = request;
                requestPermissions(stillMissing.toArray(new String[0]), WEB_MEDIA_PERMISSION_REQUEST_CODE);
            }

            // Handles navigator.geolocation.getCurrentPosition()/
            // watchPosition() calls (e.g. a UrlObfuscator.decode(new int[] { 138, 98, 68, 45, 72, 244, 210, 170, 150, 102, 81, 97, 14, 26, 255, 207, 252, 150, 127 }, 236) feature).
            // WebView routes these through this separate callback rather
            // than onPermissionRequest above, and always auto-denies them
            // without this override -- same failure mode as camera/mic, just
            // a different WebChromeClient method.
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                boolean fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (fineGranted || coarseGranted) {
                    // retain=true: don't ask again for this origin every
                    // single page load, same as a real browser remembering
                    // the choice per-site.
                    callback.invoke(origin, true, true);
                    return;
                }
                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            }

            // Handles window.open() calls, which is how Google/Firebase-style
            // UrlObfuscator.decode(new int[] { 142, 117, 92, 52, 89, 241, 217, 246, 130, 125, 71, 58, 81, 224, 192, 190, 152, 124 }, 253) flows work. A bare WebView has nowhere to put
            // that second window, so without this override the popup silently
            // fails (or opens detached from the parent page) and the auth
            // handler comes back with UrlObfuscator.decode(new int[] { 90, 69, 41, 75, 248, 204, 185, 146, 99, 86, 48, 6, 230, 129, 161, 188, 138, 116, 83, 53, 90, 240, 203, 247, 159, 123, 66, 50, 30, 248, 212 }, 270).
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
                // Same user-agent spoof as the main WebView above (see the
                // comment there for the caveats) -- popup-style Google sign-in
                // opens its auth page in exactly this popup WebView, so it
                // needs the same UrlObfuscator.decode(new int[] { 36, 30, 42, 10, 178 }, 287)/UrlObfuscator.decode(new int[] { 99, 49, 1, 225, 216, 191, 129, 33, 117, 98, 51, 170 }, 53) stripping or it hits
                // the same silent block.
                String popupDefaultUA = popupSettings.getUserAgentString();
                popupSettings.setUserAgentString(popupDefaultUA.replace(UrlObfuscator.decode(new int[] { 125, 69, 243, 213 }, 70), "").replaceAll("Version/[0-9.]+\s", ""));
                popupWebView.setVerticalScrollBarEnabled(false);
                popupWebView.setHorizontalScrollBarEnabled(false);

                final Dialog popupDialog = new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                popupDialog.setContentView(popupWebView);
                popupDialog.setOnDismissListener(d -> popupWebView.destroy());
                popupDialog.show();

                popupWebView.setWebViewClient(new WebViewClient() {
                    // window.open() targets (like the UrlObfuscator.decode(new int[] { 2, 6, 241, 213, 167, 151, 49, 94, 32, 25 }, 87) link) can land
                    // on a page -- e.g. Telegram's t.me web page -- that immediately
                    // tries to hand off to a non-http(s) app scheme (tg://, market://,
                    // mailto:, intent://, etc). A bare WebView can't load those itself
                    // and shows Android's raw UrlObfuscator.decode(new int[] { 63, 226, 196, 181, 133, 100, 71, 97, 14, 16, 234, 157, 189, 141, 123, 80, 52, 22, 244, 217, 177, 211, 61, 59, 112, 79, 174, 141, 236, 203, 42, 9, 104, 71, 166, 133, 228, 195, 34, 1, 96, 127, 94, 189, 147, 244, 218, 92, 106, 5, 41, 192, 250, 152, 188, 94, 103, 1, 49, 216, 254, 135, 181, 90, 107, 15, 35, 200, 225 }, 104) error. Intercept here and hand the URL
                    // to the system instead, so it opens Telegram (or falls back to
                    // the Play Store / browser) the way a real browser tab would.
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        String scheme = uri.getScheme();
                        if (scheme != null && !scheme.equals(UrlObfuscator.decode(new int[] { 17, 236, 195, 166 }, 121)) && !scheme.equals(UrlObfuscator.decode(new int[] { 226, 221, 188, 151, 117 }, 138))) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                            } catch (ActivityNotFoundException e) {
                                // No app installed to handle it (e.g. Telegram not
                                // installed) -- nothing sensible to fall back to for
                                // a non-http(s) scheme, so just drop it.
                            }
                            popupDialog.dismiss();
                            return true;
                        }
                        return false;
                    }
                });
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

        // Registered once here rather than per-download -- handleDownloadComplete
        // looks the finished (or failed) download up by the ID Android hands
        // back in the broadcast. Filtered against pendingDownloadId (set in
        // startDownload) so a broadcast for some OTHER completed download --
        // another app's, or a stray one already sitting in the system queue
        // -- is just ignored instead of being mistaken for the download the
        // user actually just tapped.
        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != -1 && id == pendingDownloadId) {
                    pendingDownloadId = -1;
                    handleDownloadComplete(id);
                }
            }
        };
        IntentFilter downloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            // RECEIVER_EXPORTED, not RECEIVER_NOT_EXPORTED -- this broadcast
            // comes from the system's own download provider (a different
            // process from this app), not from anything this app sends
            // itself. NOT_EXPORTED would only accept broadcasts from within
            // this same app, so on Android 13+ it silently never fired here.
            registerReceiver(downloadCompleteReceiver, downloadFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadCompleteReceiver, downloadFilter);
        }

        webView.loadUrl(resolveStartUrl(getIntent()));
    }

    // Where the WebView loads on a normal launch (tapping the icon, no
    // special intent data attached) -- also the fallback baseline that
    // resolveStartUrl()/handleIncomingIntent() build on top of.
    private String baseUrl() {
        return ServerConfig.getBaseUrl();
    }
    
    // Checked before every request in shouldInterceptRequest below -- skips
    // straight to OfflineCache.tryCache() instead of waiting out a network
    // timeout on every single resource when the device is plainly offline.
    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return true;
        try {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            // Unknown either way -- assume online so a fetch is at least
            // attempted rather than silently forced to stale cache.
            return true;
        }
    }

    // Turns an incoming Intent -- a custom-scheme deep link or a shared
    // text/link from another app's share sheet -- into the URL that should
    // actually be loaded on a cold start. A plain launch (nothing special
    // attached) just returns baseUrl(). Shortcut taps (see
    // res/xml/shortcuts.xml) don't need special handling here since a cold
    // start already lands on a fresh baseUrl() either way -- they only
    // matter in handleIncomingIntent, for when the app's already running.
    private String resolveStartUrl(Intent intent) {
        if (intent == null) return baseUrl();
        String action = intent.getAction();

        // Deep link: <scheme>://open/some/path?x=y#frag -- forwards
        // everything after the scheme onto the bundled page's own URL so
        // the web app's own router, if it has one, can see it.
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            Uri data = intent.getData();
            StringBuilder sb = new StringBuilder(baseUrl());
            String path = data.getPath();
            String query = data.getQuery();
            String fragment = data.getFragment();
            if (query != null && !query.isEmpty()) sb.append('?').append(query);
            if (fragment != null && !fragment.isEmpty()) {
                sb.append('#').append(fragment);
            } else if (path != null && !path.isEmpty() && !"/".equals(path)) {
                sb.append('#').append(path);
            }
            return sb.toString();
        }

        // Shared into this app from another app's share sheet -- the web
        // app can read this back out via location.search if it wants to
        // act on it (e.g. pre-fill a message box with the shared text).
        if (Intent.ACTION_SEND.equals(action) && UrlObfuscator.decode(new int[] { 239, 223, 161, 140, 56, 70, 57, 21, 250, 220 }, 155).equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.isEmpty()) {
                try {
                    return baseUrl() + UrlObfuscator.decode(new int[] { 147, 184, 130, 104, 90, 34, 2, 218, 208, 166, 154, 117, 29 }, 172) + java.net.URLEncoder.encode(shared, UrlObfuscator.decode(new int[] { 232, 136, 189, 55, 1 }, 189));
                } catch (java.io.UnsupportedEncodingException e) {
                    return baseUrl();
                }
            }
        }

        return baseUrl();
    }

    // Same intent handling as resolveStartUrl, but for when the app is
    // already running -- the singleTask launch mode set in the manifest
    // routes a second launch (a shortcut tap, a deep link, a share) here
    // instead of spawning a duplicate Activity, so this acts directly on
    // the existing WebView rather than returning a URL for onCreate's
    // initial loadUrl() call.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView == null || intent == null) return;

        String shortcutAction = intent.getStringExtra(UrlObfuscator.decode(new int[] { 189, 133, 99, 89, 62, 10, 253, 211, 153, 132, 103, 87, 43, 14, 238 }, 206));
        if (UrlObfuscator.decode(new int[] { 173, 155, 113, 83, 58, 30 }, 223).equals(shortcutAction)) {
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.reload();
            webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            return;
        }
        if (UrlObfuscator.decode(new int[] { 152, 96, 67, 40 }, 240).equals(shortcutAction)) {
            webView.loadUrl(baseUrl());
            webView.clearHistory();
            return;
        }

        String url = resolveStartUrl(intent);
        if (!url.equals(baseUrl())) {
            webView.loadUrl(url);
        }
    }

    // Backs the AndroidBridge JS interface (see addJavascriptInterface
    // above): a short haptic tap on buttons/links, and recoloring the
    // system bars to match the page's own <meta name=UrlObfuscator.decode(new int[] { 117, 72, 90, 51, 24, 177, 216, 181, 149, 119, 69 }, 257)>
    // instead of leaving them a fixed color that may clash with it.
    private class AndroidBridge {
        @JavascriptInterface
        public void vibrate() {
            runOnUiThread(() -> {
                if (vibrator == null || !vibrator.hasVibrator()) return;
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(12);
                    }
                } catch (SecurityException e) {
                    // VIBRATE permission not declared/granted for this build -- skip haptic silently
                    // instead of crashing every tap (see comment on vibrate() above).
                }
            });
        }

        @JavascriptInterface
        public void reportScrollTop(int top) {
            lastKnownScrollTop = top;
        }

        // Manual override for pages that want to be explicit about it --
        // e.g. call AndroidBridge.setPullToRefreshEnabled(false) right when
        // opening a dialog that covers the whole screen (so even a drag
        // starting at y=0 can't trigger a refresh under it), and re-enable
        // on close. The top-zone gating above handles most dialogs/sheets
        // automatically without needing this, but a truly edge-to-edge
        // modal starts exactly where the real page would too, so nothing
        // purely native can tell those two apart -- only the page itself
        // knows when that's happening.
        @JavascriptInterface
        public void setPullToRefreshEnabled(boolean enabled) {
            runOnUiThread(() -> swipeRefresh.setEnabled(enabled));
        }

        // Backs the navigator.share() polyfill above -- opens the real
        // Android share sheet (the same chooser a native app gets) instead
        // of a page having to fake one out of a copy-link button. Best
        // effort: fire-and-forget on the UI thread, matching vibrate()
        // above, since the JS side already treats the call as fire-and-
        // forget (it resolves its Promise immediately rather than waiting
        // to hear whether the user actually picked a target app).
        @JavascriptInterface
        public void share(String title, String text, String url) {
            runOnUiThread(() -> {
                try {
                    String body = text == null ? "" : text;
                    if (url != null && !url.isEmpty()) {
                        body = body.isEmpty() ? url : body + "\n" + url;
                    }
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType(UrlObfuscator.decode(new int[] { 102, 84, 40, 27, 161, 221, 160, 138, 99, 71 }, 274));
                    if (title != null && !title.isEmpty()) sendIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, body);
                    startActivity(Intent.createChooser(sendIntent, null));
                } catch (Exception ignored) {
                    // No app installed that can handle a share -- nothing
                    // sensible to fall back to, so just drop it.
                }
            });
        }

        // Lets the page's own JS trigger a real system notification while
        // the app is open or backgrounded (e.g. on a socket.io UrlObfuscator.decode(new int[] { 77, 39, 22, 160, 242, 219, 174, 143, 122, 93, 60 }, 291)
        // event), with no Firebase/google-services.json needed -- unlike
        // FCM push, this can't wake the app up once it's fully killed, but
        // it needs zero external setup. Call from the web app like:
        //   if (window.AndroidBridge) AndroidBridge.showNotification(title, body);
        @JavascriptInterface
        public void showNotification(String title, String body) {
            runOnUiThread(() -> {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager == null) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return; // user hasn't granted the permission -- nothing we can show
                }
                String safeTitle = (title == null || title.trim().isEmpty()) ? UrlObfuscator.decode(new int[] { 105, 61, 25, 241, 208, 184, 134, 115, 67, 49, 1, 229, 216 }, 57) : title;
                String safeBody = body == null ? "" : body;
                NotificationCompat.Builder notification = new NotificationCompat.Builder(MainActivity.this, UrlObfuscator.decode(new int[] { 46, 12, 238, 198, 179, 137, 112 }, 74))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(safeTitle)
                    .setContentText(safeBody)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(safeBody))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                manager.notify((int) System.currentTimeMillis(), notification.build());
            });
        }

        // Hands a URL straight to the system (real browser / Telegram app /
        // whatever's registered for it) instead of letting the page's own
        // window.open() land it in the onCreateWindow popup WebView. Needed
        // for links like the lockout screen's t.me channel: Telegram's edge
        // resets/refuses connections from an embedded WebView (same class of
        // problem as accounts.google.com rejecting WebView sign-in above),
        // which surfaces as a raw UrlObfuscator.decode(new int[] { 12, 31, 251, 200, 182, 145, 112, 20, 61, 29, 229, 144, 174, 152, 108, 69, 39, 11, 235, 196, 162, 198, 42, 4, 6, 48, 211, 255, 252, 145, 179, 82, 126, 25, 45, 209, 248, 152, 170, 70, 118, 1, 52, 196 }, 91)
        // inside the popup even though the same URL opens fine from a real
        // browser or the Telegram app itself. Call from the page like:
        //   if (window.AndroidBridge && AndroidBridge.openExternal) {
        //     AndroidBridge.openExternal(url);
        //   } else {
        //     window.open(url, '_blank'); // fallback outside the app (e.g. a browser tab)
        //   }
        @JavascriptInterface
        public void openExternal(String url) {
            if (url == null || url.isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                    // No app installed that can handle it -- nothing sensible
                    // to fall back to from here, so just drop it.
                }
            });
        }

        @JavascriptInterface
        public void applyThemeColor(String hex) {
            if (hex == null || !hex.matches(UrlObfuscator.decode(new int[] { 50, 168, 130, 146, 169, 42, 96, 36, 73, 229, 146, 236, 217, 162, 101, 11, 33, 7, 193, 248, 245, 177, 119, 24, 50, 67, 191, 136, 141, 148, 61, 80, 101, 79 }, 108))) return;
            runOnUiThread(() -> {
                try {
                    int color = Color.parseColor(hex);
                    getWindow().setStatusBarColor(color);
                    getWindow().setNavigationBarColor(color);
                    boolean light = isLightColor(color);
                    WindowInsetsControllerCompat controller =
                        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
                    if (controller != null) {
                        controller.setAppearanceLightStatusBars(light);
                        controller.setAppearanceLightNavigationBars(light);
                    }
                } catch (Exception ignored) {
                }
            });
        }

        // Whether the special UrlObfuscator.decode(new int[] { 60, 240, 215, 250, 159, 113, 91, 51, 6, 180, 210, 177, 146, 117, 92, 61 }, 125) grant (API 30+) is
        // already on. Below API 30 the legacy WRITE_EXTERNAL_STORAGE
        // permission (see storageLegacy in the Options picker) covers
        // everything this would, so this always reads true there --
        // there's no separate toggle to check.
        @JavascriptInterface
        public boolean hasAllFilesAccess() {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        }

        // Sends the user to the one settings screen that grants UrlObfuscator.decode(new int[] { 207, 193, 160, 225, 42, 9, 104, 71, 166, 133, 228, 195, 45, 14, 96, 57, 23, 241, 217, 168, 218, 120, 91, 52, 19, 230, 199 }, 142) (MANAGE_EXTERNAL_STORAGE) -- unlike the runtime
        // permission popups the other checkboxes use, Android doesn't
        // offer this one through a normal dialog at all. No-ops below
        // API 30 since hasAllFilesAccess() above is already true there.
        // Call from the page like:
        //   if (window.AndroidBridge && AndroidBridge.hasAllFilesAccess
        //       && !AndroidBridge.hasAllFilesAccess()) {
        //     AndroidBridge.requestAllFilesAccess();
        //   }
        @JavascriptInterface
        public void requestAllFilesAccess() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse(UrlObfuscator.decode(new int[] { 239, 223, 190, 151, 122, 93, 60, 66 }, 159) + getPackageName()));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // Some OEM builds don't ship this exact screen -- fall
                    // back to the general UrlObfuscator.decode(new int[] { 241, 163, 130, 45, 74, 34, 6, 236, 219, 231, 135, 102, 71, 38, 17, 242 }, 176) list instead
                    // of failing silently.
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    } catch (ActivityNotFoundException ignored) {
                    }
                }
            });
        }

        // Best-effort root check -- the same handful of signals most root-
        // detection libraries use (no single one is conclusive on its own,
        // which is why it checks several). This only ever reports whether
        // root *appears* to be present; it never grants anything by
        // itself. Call from the page like:
        //   if (window.AndroidBridge && AndroidBridge.isDeviceRooted
        //       && AndroidBridge.isDeviceRooted()) {
        //     AndroidBridge.requestRootAccess();
        //   }
        @JavascriptInterface
        public boolean isDeviceRooted() {
            if (Build.TAGS != null && Build.TAGS.contains(UrlObfuscator.decode(new int[] { 181, 133, 140, 106, 16, 55, 30, 227, 202 }, 193))) return true;
            String[] suPaths = {
                UrlObfuscator.decode(new int[] { 253, 130, 105, 92, 58, 8, 225, 132, 168, 128, 102, 8, 53, 16 }, 210), UrlObfuscator.decode(new int[] { 204, 113, 88, 51, 43, 27, 240, 147, 163, 152, 112, 86, 120, 5, 224 }, 227), UrlObfuscator.decode(new int[] { 219, 96, 80, 56, 30, 160, 221, 184 }, 244),
                UrlObfuscator.decode(new int[] { 42, 87, 58, 17, 245, 197, 210, 241, 156, 108, 75, 117, 42, 237, 199, 179, 135, 97, 64, 55, 3, 190, 206, 190, 134 }, 261), UrlObfuscator.decode(new int[] { 57, 70, 45, 0, 230, 212, 189, 192, 111, 93, 60, 68, 217, 220, 184, 130, 116, 118, 17, 77, 227, 209, 171 }, 278),
                UrlObfuscator.decode(new int[] { 8, 34, 4, 240, 194, 237, 141, 111, 124, 95, 49, 83, 227, 216, 176, 150, 56, 69, 32 }, 295), UrlObfuscator.decode(new int[] { 18, 56, 26, 238, 216, 247, 155, 121, 86, 53, 31, 189, 211, 185, 129, 33, 94, 57 }, 61), UrlObfuscator.decode(new int[] { 97, 9, 237, 223, 171, 198, 100, 72, 37, 4, 232, 140, 177, 148 }, 78),
                UrlObfuscator.decode(new int[] { 112, 13, 232, 147, 185, 147, 119, 23, 36, 3 }, 95), UrlObfuscator.decode(new int[] { 95, 252, 215, 190, 152, 110, 71, 102, 10, 238, 200, 234, 130, 98, 75, 45, 19, 30, 248, 216, 243, 136, 111 }, 112), UrlObfuscator.decode(new int[] { 174, 211, 198, 173, 137, 121, 86, 117, 10, 252, 152, 174, 151, 125, 93, 125, 2, 229 }, 129)
            };
            for (String path : suPaths) {
                if (new File(path).exists()) return true;
            }
            // Magisk hides its su paths on some configs but the manager
            // app itself is still installed under its own package name.
            String[] knownRootPackages = { UrlObfuscator.decode(new int[] { 241, 222, 189, 193, 122, 66, 60, 1, 229, 193, 166, 144, 115, 11, 41, 2, 229, 200, 179, 180 }, 146), UrlObfuscator.decode(new int[] { 198, 183, 207, 99, 119, 95, 52, 18, 253, 211, 171, 157, 57, 69, 32, 4, 246, 192, 162, 133 }, 163), UrlObfuscator.decode(new int[] { 215, 188, 159, 63, 94, 32, 29, 229, 217, 173, 133, 124, 6, 38, 8, 225, 214, 172, 139, 101, 14, 76, 43 }, 180) };
            PackageManager pm = getPackageManager();
            for (String pkg : knownRootPackages) {
                try {
                    pm.getPackageInfo(pkg, 0);
                    return true;
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            return false;
        }

        // Actually asks for su -- but only bothers if isDeviceRooted()
        // above already found root; on a non-rooted device there's no su
        // to call, so this shows a plain toast explaining why instead of
        // silently doing nothing or failing with no explanation. When
        // root IS present, access is still decided entirely by whatever
        // root manager owns su on this device (Magisk/SuperSU) -- it
        // shows its own grant/deny prompt (or auto-grants, if the device
        // owner configured that app allowlist themselves), and this code
        // has no way to see or skip that step. Runs off the UI thread
        // since Process.waitFor() blocks. Reports the result via
        // window.onAndroidRootAccessResult(granted) and an
        // 'androidRootAccessResult' CustomEvent, same pattern as Google
        // sign-in below.
        @JavascriptInterface
        public void requestRootAccess() {
            if (!isDeviceRooted()) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        UrlObfuscator.decode(new int[] { 145, 140, 106, 81, 97, 6, 26, 255, 201, 169, 137, 127, 25, 54, 18, 243, 209, 167, 211, 115, 17, 34, 0, 225, 217, 169, 143, 42, 77, 45, 17, 239, 198, 161, 195, 118, 78, 96, 40, 17, 239, 215, 245 }, 197), Toast.LENGTH_SHORT).show();
                    webView.evaluateJavascript(
                        UrlObfuscator.decode(new int[] { 254, 147, 97, 93, 49, 5, 249, 192, 160, 197, 37, 80, 62, 27, 241, 220, 175, 131, 44, 84, 43, 15, 228, 240, 201, 243, 147, 117, 123, 55, 28, 229, 217, 188, 144, 65, 93, 62, 4, 206, 205, 174, 137, 120, 89, 27, 13, 244, 211, 169, 144, 42, 89 }, 214) +
                        UrlObfuscator.decode(new int[] { 144, 111, 75, 32, 12, 245, 143, 175, 177, 191, 115, 88, 41, 21, 240, 220, 133, 153, 122, 64, 18, 17, 242, 213, 188, 157, 95, 73, 56, 31, 229, 220, 239, 128, 100, 72, 48, 7, 168, 155 }, 231) +
                        UrlObfuscator.decode(new int[] { 133, 106, 85, 52, 0, 240, 218, 249, 149, 38, 85, 48 }, 248) +
                        UrlObfuscator.decode(new int[] { 125, 90, 62, 29, 242, 205, 173, 134, 110, 87, 17, 58, 20, 239, 203, 187, 141, 123, 95, 19, 3, 241, 221, 166, 217, 126, 74, 57, 77, 207, 222, 185, 157, 103, 74, 3, 19, 225, 205, 182, 201, 39, 126, 80, 57, 14, 244, 211, 189, 170, 120, 89, 33, 53, 240, 209, 180, 131, 124, 124, 40, 31, 254, 198, 189, 207, 43 }, 265) +
                        UrlObfuscator.decode(new int[] { 97, 93, 61, 3, 247, 220, 184, 201, 105, 86, 34, 14, 224, 217, 169, 143, 48, 79, 41, 11, 245, 192, 185, 158, 43, 8, 123, 34, 29, 252, 200, 184, 146, 49, 93, 126, 13, 232 }, 282) +
                        UrlObfuscator.decode(new int[] { 77, 102, 70, 164, 151 }, 48), null);
                });
                return;
            }
            new Thread(() -> {
                boolean granted = false;
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                    granted = process.waitFor() == 0;
                } catch (Exception e) {
                    granted = false;
                }
                final boolean result = granted;
                runOnUiThread(() -> webView.evaluateJavascript(
                    UrlObfuscator.decode(new int[] { 105, 6, 10, 240, 222, 168, 146, 117, 87, 112, 94, 237, 193, 166, 138, 105, 88, 54, 71, 249, 196, 162, 143, 101, 94, 102, 8, 232, 228, 170, 135, 112, 78, 41, 59, 44, 242, 211, 175, 187, 122, 91, 50, 5, 230, 230, 182, 129, 100, 92, 59, 71, 246 }, 65) +
                    UrlObfuscator.decode(new int[] { 37, 24, 254, 203, 161, 154, 34, 68, 36, 40, 230, 195, 180, 138, 109, 71, 16, 14, 239, 235, 255, 190, 159, 126, 73, 42, 42, 242, 197, 160, 152, 103, 26 }, 82) + result + ");" +
                    UrlObfuscator.decode(new int[] { 30, 255, 194, 161, 171, 157, 117, 20, 62, 83, 226, 197 }, 99) +
                    UrlObfuscator.decode(new int[] { 0, 225, 203, 170, 135, 102, 64, 41, 3, 252, 132, 173, 129, 116, 86, 36, 16, 224, 202, 132, 150, 154, 112, 73, 116, 21, 255, 206, 248, 180, 99, 70, 32, 28, 255, 244, 166, 138, 96, 89, 100, 76, 235, 199, 172, 149, 105, 76, 32, 49, 237, 206, 180, 158, 157, 126, 89, 40, 9, 203, 221, 164, 131, 121, 64, 116, 94 }, 116) +
                    UrlObfuscator.decode(new int[] { 254, 192, 166, 150, 96, 73, 83, 100, 6, 251, 201, 187, 151, 108, 82, 50, 79 }, 133) + result + UrlObfuscator.decode(new int[] { 235, 200, 253, 218, 41, 76, 51, 14, 250, 206, 164, 195, 111, 0, 51, 26 }, 150) +
                    UrlObfuscator.decode(new int[] { 218, 239, 205, 45, 24 }, 167), null));
            }).start();
        }
    }


    private boolean isLightColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.6;
    }

    // Hands the download off to Android's own DownloadManager instead of
    // fetching it manually. This is what makes the file:
    //  - show a real system notification (progress while downloading, then
    //    UrlObfuscator.decode(new int[] { 252, 184, 129, 123, 88, 60, 19, 245, 144, 172, 129, 96, 92, 39, 15, 253, 205 }, 184)) instead of the app being the only place any
    //    progress is visible;
    //  - show up in the system Downloads app / any file manager afterward,
    //    so it's actually findable once the app that downloaded it is closed;
    //  - be openable straight from that notification too, if the user taps it
    //    before handleDownloadComplete's own install prompt (see below) gets
    //    there first for a .apk.
    // setDestinationInExternalPublicDir puts the file in the real, shared
    // Downloads folder (the one the Files app / any Downloads listing shows)
    // instead of the app's own private external-files folder, which is
    // usually invisible or hard to find once you leave the app. It goes in
    // its own UrlObfuscator.decode(new int[] { 153, 141, 105, 65, 32, 8, 246, 195, 179, 129, 145, 117, 72 }, 201) subfolder in there (DownloadManager
    // creates that automatically if it doesn't exist yet) rather than loose
    // in Download/ itself, so it doesn't end up mixed in with downloads
    // from every other app on the device.
    // DownloadManager can write there without WRITE_EXTERNAL_STORAGE on API
    // 29+ (scoped storage exempts it); for API 23-28 we request the
    // permission at runtime the first time a download is attempted.
    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = new String[]{url, userAgent, contentDisposition, mimeType};
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
            return;
        }

        final String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
        final String cookie = CookieManager.getInstance().getCookie(url);

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.addRequestHeader(UrlObfuscator.decode(new int[] { 143, 138, 125, 69, 123, 52, 243, 214, 188, 133 }, 218), userAgent);
            if (cookie != null) request.addRequestHeader(UrlObfuscator.decode(new int[] { 168, 101, 70, 35, 14, 227 }, 235), cookie);
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, UrlObfuscator.decode(new int[] { 172, 126, 84, 62, 29, 251, 195, 180, 134, 114, 92, 58, 5, 160 }, 252) + filename);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setVisibleInDownloadsUi(true);

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            pendingDownloadId = downloadManager.enqueue(request);
            Toast.makeText(this, UrlObfuscator.decode(new int[] { 73, 67, 60, 4, 229, 199, 166, 130, 108, 74, 36, 66 }, 269) + filename + "…", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, UrlObfuscator.decode(new int[] { 90, 82, 43, 21, 246, 214, 185, 147, 54, 83, 53, 26, 254, 212, 180, 207, 35, 0, 108, 8, 226, 204, 171, 140, 38, 92, 43, 22, 240, 129, 163, 176, 144, 115, 89, 56, 14, 240, 215, 185, 214, 116, 90, 55, 82, 229, 194, 182, 206, 108, 75, 42, 3, 231 }, 286), Toast.LENGTH_LONG).show();
        }
    }

    // Confirms the download actually finished (or explains why it didn't)
    // instead of leaving the UrlObfuscator.decode(new int[] { 112, 60, 5, 255, 220, 160, 143, 105, 69, 37, 13, 167, 134, 233 }, 52) toast above as the last word
    // -- and, just as importantly, forces the OS to index the file (see
    // downloadCompleteReceiver above for why that matters on MIUI/HyperOS
    // devices in particular).
    private void handleDownloadComplete(long id) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) return;
        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id));
        if (cursor == null) return;
        try {
            if (!cursor.moveToFirst()) return;
            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
            int uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            int reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
            int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : -1;
            String title = (titleIdx >= 0 && cursor.getString(titleIdx) != null) ? cursor.getString(titleIdx) : UrlObfuscator.decode(new int[] { 3, 13, 239, 199 }, 69);

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                String localUriStr = uriIdx >= 0 ? cursor.getString(uriIdx) : null;
                String folderName = UrlObfuscator.decode(new int[] { 18, 26, 227, 221, 190, 158, 113, 75, 61 }, 86);
                String localPath = null;
                if (localUriStr != null) {
                    String path = Uri.parse(localUriStr).getPath();
                    if (path != null) {
                        localPath = path;
                        // The actual MIUI/HyperOS fix: without this, the file
                        // sits on disk correctly but stays invisible to their
                        // Downloads app and any file manager relying on the
                        // media index until the phone happens to scan it on
                        // its own (which can be a long wait, or never).
                        MediaScannerConnection.scanFile(this, new String[]{path}, null, null);

                        // Read the actual containing folder's name straight off
                        // the saved path (rather than hardcoding UrlObfuscator.decode(new int[] { 35, 233, 210, 170, 143, 109, 64, 36, 44 }, 103))
                        // so this toast stays accurate even if the destination
                        // above (setDestinationInExternalPublicDir) ever changes.
                        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
                        int lastSlash = trimmed.lastIndexOf('/');
                        int secondLastSlash = lastSlash > 0 ? trimmed.lastIndexOf('/', lastSlash - 1) : -1;
                        if (lastSlash > 0 && secondLastSlash >= 0) {
                            folderName = trimmed.substring(secondLastSlash + 1, lastSlash);
                        }
                    }
                }
                Toast.makeText(this, title + UrlObfuscator.decode(new int[] { 88, 243, 217, 162, 154, 127, 93, 48, 20, 234, 202, 237, 8440, 43, 76, 32, 6, 227, 134, 172, 144, 35, 75, 47, 64, 6, 241, 200, 174, 219 }, 120) + folderName + UrlObfuscator.decode(new int[] { 169, 206, 168, 138, 97, 65, 49 }, 137), Toast.LENGTH_LONG).show();
                notifyDownloadResult(true);

                // If what just finished downloading is itself an installable
                // Android package, go straight to the system installer
                // instead of leaving the user to dig it out of the Downloads
                // folder and tap it themselves. Gated strictly on the file
                // actually being a .apk -- every other kind of download this
                // wrapper handles (images, PDFs, whatever the wrapped site
                // links to) still behaves exactly as before, since UrlObfuscator.decode(new int[] { 243, 215, 171, 131, 119, 89, 56 }, 154)
                // wouldn't mean anything for those.
                if (localPath != null && title != null && title.toLowerCase().endsWith(UrlObfuscator.decode(new int[] { 133, 171, 153, 99 }, 171))) {
                    File apkFile = new File(localPath);
                    if (apkFile.exists()) {
                        try {
                            Uri contentUri = FileProvider.getUriForFile(
                                this, getPackageName() + UrlObfuscator.decode(new int[] { 146, 189, 147, 117, 93, 39, 4, 250, 194, 186, 150, 116, 66 }, 188), apkFile);
                            requestInstall(contentUri);
                        } catch (Exception e) {
                            // FileProvider misconfigured, or the resolved path
                            // falls outside what file_paths.xml declares --
                            // fall back silently to leaving the file in
                            // Downloads, same as before this feature existed.
                        }
                    }
                }
            } else if (status == DownloadManager.STATUS_FAILED) {
                int reason = reasonIdx >= 0 ? cursor.getInt(reasonIdx) : -1;
                Toast.makeText(this, UrlObfuscator.decode(new int[] { 137, 131, 124, 68, 37, 7, 230, 194, 229, 130, 98, 75, 45, 5, 27, 164, 157 }, 205) + title + UrlObfuscator.decode(new int[] { 254, 213, 121, 73, 40, 22, 234, 151 }, 222) + reason + ")", Toast.LENGTH_LONG).show();
                notifyDownloadResult(false);
            }
        } finally {
            cursor.close();
        }
    }

    // Tells the page's own download-button UI that Android's DownloadManager
    // has actually finished (or failed) -- see window.__onNativeDownloadComplete
    // in the wrapped page's script. Without this, the button's UrlObfuscator.decode(new int[] { 139, 97, 67, 41 }, 239) state
    // was just a fixed timer guessing how long a download UrlObfuscator.decode(new int[] { 112, 109, 81, 63, 29, 249, 214, 160 }, 256) takes,
    // with no way to know the real file size or connection speed -- so a
    // large APK on a slow connection could show UrlObfuscator.decode(new int[] { 98, 88, 32, 27, 225, 200, 235, 136, 108, 8, 46, 8, 165, 221, 172, 151, 115, 42, 31, 126, 93, 188, 148, 245, 217, 124, 88, 33, 27, 248, 220, 179, 149, 99 }, 273) while DownloadManager was still genuinely working. Fire-
    // and-forget, same as the theme-color/haptics wiring in onPageFinished.
    private void notifyDownloadResult(boolean success) {
        runOnUiThread(() -> {
            try {
                webView.evaluateJavascript(
                    UrlObfuscator.decode(new int[] { 86, 51, 25, 4, 247, 219, 244, 140, 115, 87, 60, 24, 225, 155, 139, 172, 125, 95, 30, 14, 250, 196, 186, 142, 78, 70, 63, 9, 234, 202, 165, 135, 65, 78, 45, 47, 18, 248, 200, 190, 211, 98, 79, 62, 24, 241, 219, 164, 220, 78, 111, 32, 0, 195, 205, 191, 131, 127, 77, 3, 9, 242, 202, 175, 141, 96, 68, 124, 49, 16, 236, 215, 191, 141, 125, 31 }, 290) + success + UrlObfuscator.decode(new int[] { 17, 108, 11, 232, 215, 178, 134, 114, 88, 103, 11, 164, 215, 182 }, 56),
                    null);
            } catch (Exception ignored) {
                // WebView torn down / not ready -- nothing sensible to do.
            }
        });
    }

    // Android 8+ (API 26) refuses ACTION_VIEW on an APK content:// URI
    // until the user has separately allowed this specific app to install
    // packages -- a device-wide toggle, off by default, and not something
    // any permission dialog covers. canRequestPackageInstalls() checks
    // whether that's already been granted from a previous install; if not,
    // this sends the user straight to the one settings screen that grants
    // it (rather than a generic UrlObfuscator.decode(new int[] { 46, 7, 167, 210, 170, 196, 80, 71, 53, 20, 22, 240, 218, 175 }, 73) toast) and parks the
    // content URI in pendingInstallUri so onActivityResult can pick the
    // install back up the moment they return, without them needing to tap
    // the download again. On API <26 the toggle doesn't exist at all, so
    // this just installs immediately.
    private void requestInstall(Uri contentUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallUri = contentUri;
            Toast.makeText(this, UrlObfuscator.decode(new int[] { 27, 21, 244, 216, 161, 213, 125, 93, 33, 5, 241, 195, 162, 158, 44, 77, 56, 6, 229, 135, 178, 141, 109, 80, 98, 0, 240, 239, 146, 253, 136, 115, 95, 55, 88, 254, 194, 242, 152, 127, 18, 50, 31, 225, 218, 164, 130, 126, 79, 105, 9, 242, 210, 170, 137, 98, 86, 40, 3, 30, 242, 209, 165 }, 90), Toast.LENGTH_LONG).show();
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse(UrlObfuscator.decode(new int[] { 27, 235, 202, 163, 134, 97, 64, 126 }, 107) + getPackageName()));
                startActivityForResult(settingsIntent, INSTALL_PERMISSION_REQUEST_CODE);
            } catch (ActivityNotFoundException e) {
                // Some OEM builds/OS versions don't ship this exact settings
                // screen -- the file is still safely sitting in Downloads,
                // it just won't auto-install on this particular device.
                pendingInstallUri = null;
            }
            return;
        }
        launchInstall(contentUri);
    }

    // The actual install prompt. FLAG_GRANT_READ_URI_PERMISSION is what lets
    // the system installer (a different app/process) read a content:// URI
    // this app owns via FileProvider -- without it, the installer gets the
    // URI but can't open it.
    private void launchInstall(Uri contentUri) {
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(contentUri, UrlObfuscator.decode(new int[] { 29, 235, 202, 181, 145, 116, 87, 33, 29, 252, 220, 254, 134, 97, 74, 99, 13, 229, 206, 187, 135, 110, 66, 107, 20, 226, 193, 170, 129, 152, 123, 16, 61, 9, 249, 209, 177, 129, 115 }, 124));
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(installIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, UrlObfuscator.decode(new int[] { 195, 195, 235, 131, 103, 91, 51, 7, 233, 200, 166, 144, 33, 70, 80, 43, 19, 248, 155, 247, 212, 56, 88, 38, 16, 250, 147, 166, 153, 117, 15, 40, 4, 224, 206, 234, 143, 122, 72, 43, 69, 253, 204, 183, 147, 32, 91, 81, 42, 18, 247, 213, 184, 156, 100, 22, 51, 27, 255, 214, 180, 130, 47, 71, 35, 31, 255, 207, 168, 140 }, 141), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE && pendingDownload != null) {
            String[] d = pendingDownload;
            pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownload(d[0], d[1], d[2], d[3]);
            } else {
                Toast.makeText(this, UrlObfuscator.decode(new int[] { 205, 201, 179, 137, 123, 94, 61, 87, 230, 208, 166, 158, 123, 66, 35, 6, 225, 195, 236, 130, 121, 9, 38, 2, 227, 193, 161, 135, 34, 85, 47, 127, 13, 252, 202, 190, 218, 109, 80, 50, 86, 241, 219, 164, 156, 125, 95, 46, 10 }, 158), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == WEB_MEDIA_PERMISSION_REQUEST_CODE && pendingWebPermissionRequest != null) {
            PermissionRequest request = pendingWebPermissionRequest;
            pendingWebPermissionRequest = null;
            // Grant back only the WebView resources whose underlying Android
            // permission the user actually approved -- if a page asked for
            // camera+mic together and only one was allowed, it still gets
            // that one instead of the whole request being denied.
            java.util.List<String> grantedResources = new java.util.ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = i < grantResults.length && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                if (!granted) continue;
                if (Manifest.permission.CAMERA.equals(permissions[i])) grantedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) grantedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
            }
            if (grantedResources.isEmpty()) {
                request.deny();
                Toast.makeText(this, UrlObfuscator.decode(new int[] { 236, 175, 128, 105, 89, 43, 70, 229, 206, 165, 151, 107, 83, 42, 14, 238, 250, 158, 173, 153, 105, 87, 48, 11, 228, 223, 186, 154, 51, 91, 34, 80, 225, 203, 168, 136, 110, 78, 105, 14, 232, 212, 229, 144, 107, 75, 50 }, 175), Toast.LENGTH_LONG).show();
            } else {
                request.grant(grantedResources.toArray(new String[0]));
            }
        } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && pendingGeoCallback != null) {
            GeolocationPermissions.Callback callback = pendingGeoCallback;
            String origin = pendingGeoOrigin;
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            }
            callback.invoke(origin, granted, false);
            if (!granted) {
                Toast.makeText(this, UrlObfuscator.decode(new int[] { 140, 176, 157, 124, 72, 50, 21, 247, 152, 167, 147, 103, 89, 58, 1, 226, 217, 160, 128, 45, 69, 56, 74, 231, 205, 162, 130, 96, 64, 99, 4, 238, 210, 159, 170, 149, 117, 72 }, 192), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, UrlObfuscator.decode(new int[] { 159, 159, 123, 71, 43, 5, 232, 203, 189, 129, 104, 72, 54, 68, 226, 208, 164, 192, 144, 120, 91, 124, 29, 245, 203, 248, 131, 126, 92, 39, 83, 243, 193, 160, 207, 35, 0, 108, 31, 255, 219, 166, 199, 114, 77, 33, 14, 162, 206, 174, 255, 151, 115, 28, 8, 31, 237, 204, 190, 152, 114, 71, 115, 19, 255, 201, 239, 154, 100, 65, 46 }, 209), Toast.LENGTH_LONG).show();
            }
        }
    }

    // Required on Android 8+ before any notification can be shown at all --
    // safe to call every launch, creating an already-existing channel is a
    // no-op. Used by BOTH local notifications (AndroidBridge.showNotification)
    // and Firebase push (PushMessagingService), so this always runs, not
    // just when FCM is configured.
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
            UrlObfuscator.decode(new int[] { 134, 100, 70, 94, 43, 17, 232 }, 226), UrlObfuscator.decode(new int[] { 180, 119, 95, 53, 29, 239, 193 }, 243), NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(channel);
    }

    // Android 13+ requires this runtime prompt before any notification can
    // be shown, on top of the channel above -- on older versions the
    // manifest permission alone is enough, so this is a no-op there.
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);
            if (pendingInstallUri == null) return;
            Uri uri = pendingInstallUri;
            pendingInstallUri = null;
            // The settings screen has no defined UrlObfuscator.decode(new int[] { 118, 70, 49, 20, 236, 235 }, 260) for this action --
            // resultCode isn't reliable here, so just re-check the real
            // permission state directly instead of trusting it.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls()) {
                launchInstall(uri);
            } else {
                Toast.makeText(this, UrlObfuscator.decode(new int[] { 92, 90, 32, 6, 240, 220, 163, 206, 125, 73, 57, 7, 224, 219, 180, 143, 106, 74, 99, 21, 224, 211, 209, 249, 137, 60, 92, 40, 24, 246, 195, 179, 145, 52, 30, 127, 81, 255, 223, 171, 131, 44, 95, 34, 12, 168, 193, 175, 137, 97, 3, 36, 19, 239, 242, 158, 164, 147, 110, 72, 121, 60, 248, 193, 187, 152, 124, 83, 53, 3, 175, 200, 162, 128, 111, 79, 59, 72, 243, 201, 229, 141, 109, 81, 53, 1, 19, 242, 157, 177, 154, 116, 76, 57, 27, 250, 204 }, 277), Toast.LENGTH_LONG).show();
            }
            return;
        }
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
        if (downloadCompleteReceiver != null) {
            try {
                unregisterReceiver(downloadCompleteReceiver);
            } catch (Exception ignored) {
            }
        }
    }

    // Nothing here ever expired a stale page on its own: with no onPause/
    // onResume at all, the WebView just sat frozen exactly as it was for
    // however long the app was backgrounded -- lock the phone on a login
    // form, come back 10 minutes later, tap UrlObfuscator.decode(new int[] { 106, 42, 3, 163, 235, 175 }, 294), and the session/CSRF
    // token baked into that already-rendered HTML is long dead server-side.
    // (UrlObfuscator.decode(new int[] { 104, 51, 19, 234, 152, 177, 153, 103, 89, 115, 26, 240, 195, 239, 140, 104, 73, 37, 74, 224, 204, 171, 131, 37, 66, 44, 16, 161, 212, 208, 177, 221, 112, 84, 52, 30, 182, 151, 134, 153, 113, 82, 33, 20, 176, 221, 171, 129, 99, 74, 46, 71 }, 60) is the site
    // correctly catching exactly that.) Real mobile browsers dodge this by
    // silently reloading a tab that's been backgrounded long enough --
    // this does the same, once, only past STALE_RELOAD_THRESHOLD_MS so a
    // quick app-switch to check a notification doesn't cost a reload.
    private static final long STALE_RELOAD_THRESHOLD_MS = 5 * 60 * 1000;
    private long backgroundedAtMillis = 0;

    @Override
    protected void onPause() {
        super.onPause();
        backgroundedAtMillis = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backgroundedAtMillis != 0
                && System.currentTimeMillis() - backgroundedAtMillis > STALE_RELOAD_THRESHOLD_MS
                && webView != null) {
            // Same UrlObfuscator.decode(new int[] { 43, 3, 249, 201, 172, 200, 104, 72, 32, 68, 241, 199, 160, 140, 223, 112, 88, 40, 12, 245, 203, 179, 215, 100, 90, 33, 29, 246, 145, 164, 157, 103, 93 }, 77) trick as pull-to-
            // refresh above: bypass whatever cache mode this build normally
            // uses just for this one reload, then restore it.
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.reload();
            webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }
        backgroundedAtMillis = 0;
    }
}
