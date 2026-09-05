package com.sxtools

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private EditText urlInput, outputArea;
    private Button fetchBtn, copyBtn;
    private TextView toastView;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, String> cache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Background #0b0e14 - TETAP SAMA
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0b0e14"));
        root.setPadding(40, 40, 40, 40);
        root.setGravity(Gravity.CENTER);

        // Container #151e28 - TETAP SAMA
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#151e28"));
        container.setPadding(48, 48, 48, 48);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header SXtools
        TextView title = new TextView(this);
        title.setText("⬡ SXTOOLS"); // GANTI NAMA
        title.setTextColor(Color.parseColor("#5fc9ff"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView status = new TextView(this);
        status.setText("● ONLINE");
        status.setTextColor(Color.parseColor("#7ddfa0"));
        status.setPadding(24, 8, 24, 8);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.SPACE_BETWEEN);
        header.addView(title);
        header.addView(status);
        container.addView(header);

        // META DIUBAH
        TextView meta = new TextView(this);
        meta.setText("v1.0.0  |  DEV: SAN  |  SDK: Android 14+"); // VERSI + DEV
        meta.setTextColor(Color.parseColor("#8a9cb0"));
        meta.setTextSize(12);
        meta.setPadding(0, 12, 0, 20);
        container.addView(meta);

        // Input + Button
        urlInput = new EditText(this);
        urlInput.setHint("Enter full URL (https://...)");
        urlInput.setHintTextColor(Color.parseColor("#8a9cb0"));
        urlInput.setTextColor(Color.parseColor("#d4e2f0"));
        urlInput.setBackgroundColor(Color.parseColor("#0f171f"));
        urlInput.setPadding(32, 24, 32, 24);
        urlInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);

        fetchBtn = new Button(this);
        fetchBtn.setText("▶ CONVERT");
        fetchBtn.setTextColor(Color.WHITE);
        fetchBtn.setBackgroundColor(Color.parseColor("#2a5a7a"));

        LinearLayout inputGroup = new LinearLayout(this);
        inputGroup.setOrientation(LinearLayout.VERTICAL);
        inputGroup.addView(urlInput);
        inputGroup.addView(fetchBtn);
        container.addView(inputGroup);

        // Result Area
        LinearLayout resultArea = new LinearLayout(this);
        resultArea.setOrientation(LinearLayout.VERTICAL);
        resultArea.setBackgroundColor(Color.parseColor("#0b1117"));
        resultArea.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 700);
        resultParams.topMargin = 24;
        resultArea.setLayoutParams(resultParams);

        copyBtn = new Button(this);
        copyBtn.setText("📋 COPY HTML");
        copyBtn.setTextColor(Color.WHITE);
        copyBtn.setBackgroundColor(Color.parseColor("#2a5a4a"));
        copyBtn.setTextSize(12);

        outputArea = new EditText(this);
        outputArea.setHint("Hasil HTML akan muncul di sini...");
        outputArea.setHintTextColor(Color.parseColor("#4a607a"));
        outputArea.setTextColor(Color.parseColor("#c8d8e8"));
        outputArea.setBackground(null);
        outputArea.setPadding(24, 24, 24, 24);
        outputArea.setMinLines(15);
        outputArea.setMaxLines(20);
        outputArea.setFocusable(false);

        resultArea.addView(copyBtn);
        resultArea.addView(outputArea);
        container.addView(resultArea);

        // Footer SXtools
        TextView footer = new TextView(this);
        footer.setText("⚡ SXTOOLS · RAW HTML EXTRACTOR · CORS-PROXY ENABLED"); // GANTI NAMA
        footer.setTextColor(Color.parseColor("#4a607a"));
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 20, 0, 0);
        container.addView(footer);

        root.addView(container);
        setContentView(root);

        // Toast
        toastView = new TextView(this);
        toastView.setBackgroundColor(Color.parseColor("#2a4a3a"));
        toastView.setTextColor(Color.parseColor("#b0e0c0"));
        toastView.setPadding(32, 16, 32, 16);
        toastView.setVisibility(View.GONE);
        root.addView(toastView);

        // Events
        fetchBtn.setOnClickListener(v -> fetchAndConvert(urlInput.getText().toString()));
        copyBtn.setOnClickListener(v -> copyText(outputArea.getText().toString()));
    }

    private void showToast(String msg) {
        mainHandler.post(() -> {
            toastView.setText(msg);
            toastView.setVisibility(View.VISIBLE);
            mainHandler.postDelayed(() -> toastView.setVisibility(View.GONE), 2000);
        });
    }

    private void copyText(String text) {
        if (text == null || text.trim().isEmpty()) {
            showToast("⚠ No content to copy");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("HTML", text);
        clipboard.setPrimaryClip(clip);
        showToast("✓ HTML copied");
    }

    private void fetchAndConvert(String url) {
        if (url == null || url.trim().isEmpty()) {
            outputArea.setText("ERROR: URL cannot be empty.");
            return;
        }
        String fullUrl = url.trim();
        if (!fullUrl.startsWith("http")) fullUrl = "https://" + fullUrl;
        if (cache.containsKey(fullUrl)) {
            outputArea.setText(cache.get(fullUrl));
            showToast("✓ Loaded from cache");
            return;
        }

        fetchBtn.setEnabled(false);
        fetchBtn.setText("⏳ FETCHING");
        outputArea.setText("⏳ Fetching " + fullUrl + " ...\n");

        String finalFullUrl = fullUrl;
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String proxyUrl = "https://api.allorigins.win/raw?url=" + URLEncoder.encode(finalFullUrl, "UTF-8");
                URL urlObj = new URL(proxyUrl);
                conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line).append("\n");
                in.close();
                String html = response.toString();
                cache.put(finalFullUrl, html);
                mainHandler.post(() -> {
                    outputArea.setText(html);
                    fetchBtn.setEnabled(true);
                    fetchBtn.setText("▶ CONVERT");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    outputArea.setText("ERROR: " + e.getMessage() + "\n\nPossible reasons:\n- Proxy down\n- Invalid URL");
                    fetchBtn.setEnabled(true);
                    fetchBtn.setText("▶ CONVERT");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}