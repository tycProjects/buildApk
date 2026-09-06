package com.pvn.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class ProxyService extends VpnService {

    private static final String TAG = "ProxyService";
    private static final String CHANNEL_ID = "pvn_channel";
    private ParcelFileDescriptor vpnInterface;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, getNotification("Đang kết nối proxy..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String proxyIp = intent.getStringExtra("proxy_ip");
            int proxyPort = intent.getIntExtra("proxy_port", 8080);

            Log.i(TAG, "Starting VPN with proxy: " + proxyIp + ":" + proxyPort);

            try {
                vpnInterface = new Builder()
                        .setAddress("10.0.0.2", 32)
                        .addRoute("0.0.0.0", 0)
                        .setSession("PVN Proxy")
                        .establish();
            } catch (Exception e) {
                Log.e(TAG, "Error establishing VPN: " + e.getMessage());
                stopSelf();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface: " + e.getMessage());
            }
        }
        Log.i(TAG, "Proxy stopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "PVN Proxy", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification getNotification(String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PVN Proxy")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        return builder.build();
    }
}
