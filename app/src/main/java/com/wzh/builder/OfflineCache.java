package com.wzh.builder;

import android.content.Context;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class OfflineCache {
    private OfflineCache() {}

    // Backs refreshInBackground() below -- 'fast' cache mode returns an
    // instant response from tryCache() and uses this to quietly bring the
    // cache up to date afterward, off the thread shouldInterceptRequest
    // runs on. Small fixed pool: a page might fire off a couple dozen of
    // these on one load, and there's no reason to hand each one its own
    // throwaway Thread.
    private static final ExecutorService REFRESH_EXECUTOR = Executors.newFixedThreadPool(4);

    private static File dirFor(Context ctx) {
        File dir = new File(ctx.getCacheDir(), UrlObfuscator.decode(new int[] { 64, 40, 11, 224, 194, 164, 140, 87, 87, 39, 2, 225, 208 }, 47));
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // SHA-256 of the full URL (including query string) as a filesystem-safe
    // cache key -- two different URLs never collide, and the same URL
    // always maps back to the same files.
    private static String keyFor(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance(UrlObfuscator.decode(new int[] { 19, 23, 63, 176, 142, 238, 204 }, 64));
            byte[] digest = md.digest(url.getBytes(UrlObfuscator.decode(new int[] { 4, 36, 201, 131, 245 }, 81)));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format(UrlObfuscator.decode(new int[] { 71, 177, 146, 199 }, 98), b));
            return sb.toString();
        } catch (Exception e) {
            // SHA-256/UTF-8 are always available on Android in practice --
            // this is just a belt-and-suspenders fallback that's still
            // filesystem-safe if it's ever somehow reached.
            return "u" + Math.abs(url.hashCode());
        }
    }

    private static File bodyFile(Context ctx, String url) {
        return new File(dirFor(ctx), keyFor(url) + UrlObfuscator.decode(new int[] { 93, 240, 222, 180, 150 }, 115));
    }

    private static File metaFile(Context ctx, String url) {
        return new File(dirFor(ctx), keyFor(url) + UrlObfuscator.decode(new int[] { 170, 206, 167, 149, 97 }, 132));
    }

    // Fetches url fresh over the network and saves a successful response to
    // disk for next time. Short timeouts since this runs inline in
    // shouldInterceptRequest -- a hung connection shouldn't be able to stall
    // page load indefinitely. Returns null on any failure (caller falls
    // back to tryCache()).
    static WebResourceResponse tryNetwork(Context ctx, String url, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    String k = e.getKey();
                    if (k == null) continue;
                    String lk = k.toLowerCase();
                    // Set by HttpURLConnection itself from the real
                    // connection/body -- passing the WebView-side values
                    // through can conflict with them.
                    if (lk.equals(UrlObfuscator.decode(new int[] { 253, 219, 160, 134 }, 149)) || lk.equals(UrlObfuscator.decode(new int[] { 197, 170, 138, 119, 71, 47, 20, 82, 242, 216, 178, 156, 110, 81 }, 166))) continue;
                    try { conn.setRequestProperty(k, e.getValue()); } catch (Exception ignored) {}
                }
            }
            int code = conn.getResponseCode();
            String mime = conn.getContentType();
            if (mime != null && mime.indexOf(';') >= 0) mime = mime.substring(0, mime.indexOf(';')).trim();
            if (mime == null || mime.isEmpty()) mime = UrlObfuscator.decode(new int[] { 214, 166, 133, 120, 90, 49, 16, 228, 198, 161, 131, 35, 68, 41, 29, 237, 211, 235, 150, 112, 81, 39, 0, 237 }, 183);
            InputStream in = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) return null;
            byte[] data = readAll(in);
            if (code >= 200 && code < 300) {
                save(ctx, url, mime, data);
                return new WebResourceResponse(mime, UrlObfuscator.decode(new int[] { 157, 179, 64, 8, 124 }, 200), new ByteArrayInputStream(data));
            }
            // A real response from a reachable server, just not a 2xx one --
            // hand it back as-is rather than treating it as UrlObfuscator.decode(new int[] { 182, 158, 113, 90, 60, 26, 246 }, 217).
            String reason = code >= 500 ? UrlObfuscator.decode(new int[] { 185, 108, 90, 49, 3, 247, 132, 134, 144, 115, 79, 77 }, 234) : code == 404 ? UrlObfuscator.decode(new int[] { 181, 117, 77, 120, 49, 249, 192, 186, 151 }, 251) : UrlObfuscator.decode(new int[] { 79, 71, 35, 12, 230, 211, 230, 160, 118, 81, 45, 19 }, 268);
            return new WebResourceResponse(mime, UrlObfuscator.decode(new int[] { 72, 104, 29, 87, 161 }, 285), code, reason, null, new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // Fire-and-forget: the caller already handed the WebView a cached
    // response, so this just re-fetches url on a background thread and
    // (via tryNetwork's own save() call) updates what's on disk for next
    // time. Nothing waits on this and nothing reads its return value --
    // a failure here just means the cache stays as stale as it already was.
    static void refreshInBackground(Context ctx, String url, Map<String, String> headers) {
        REFRESH_EXECUTOR.execute(() -> tryNetwork(ctx, url, headers));
    }

    static WebResourceResponse tryCache(Context ctx, String url) {
        try {
            File body = bodyFile(ctx, url);
            File meta = metaFile(ctx, url);
            if (!body.exists() || !meta.exists()) return null;
            String mime = new String(readAll(new FileInputStream(meta)), UrlObfuscator.decode(new int[] { 102, 6, 55, 189, 151 }, 51)).trim();
            if (mime.isEmpty()) mime = UrlObfuscator.decode(new int[] { 37, 19, 242, 205, 169, 188, 159, 105, 85, 52, 20, 182, 215, 180, 130, 112, 64, 126, 1, 229, 194, 170, 143, 96 }, 68);
            byte[] data = readAll(new FileInputStream(body));
            return new WebResourceResponse(mime, UrlObfuscator.decode(new int[] { 0, 32, 213, 159, 233 }, 85), new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        }
    }

    private static void save(Context ctx, String url, String mime, byte[] data) {
        try {
            try (OutputStream out = new FileOutputStream(bodyFile(ctx, url))) {
                out.write(data);
            }
            try (OutputStream out = new FileOutputStream(metaFile(ctx, url))) {
                out.write(mime.getBytes(UrlObfuscator.decode(new int[] { 51, 209, 226, 238, 218 }, 102)));
            }
        } catch (IOException ignored) {
            // Best-effort -- a failed cache write only means this one
            // response won't be available offline later; nothing else about
            // the current page load is affected.
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
