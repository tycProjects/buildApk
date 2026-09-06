package com.novachat.bluetooth;

import android.app.*;
import android.content.*;
import android.os.Build;

public final class NotificationHelper {
    private static final String ID="novachat_messages";
    public static void create(Context c){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(ID,"رسائل NovaChat",NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("إشعارات الرسائل");
            ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    public static void show(Context c,String title,String text){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,ID):new Notification.Builder(c);
        b.setSmallIcon(com.novachat.bluetooth.R.drawable.ic_launcher).setContentTitle(title).setContentText(text).setAutoCancel(true);
        nm.notify((int)(System.currentTimeMillis()%100000),b.build());
    }
}
