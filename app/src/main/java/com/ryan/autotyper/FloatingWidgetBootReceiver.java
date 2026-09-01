package com.ryan.autotyper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Khởi động lại FloatingWidgetService sau khi máy reboot nếu người dùng cho phép. */
public class FloatingWidgetBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        boolean enabled = context.getSharedPreferences(
                "floating_widget_preferences", Context.MODE_PRIVATE)
                .getBoolean(FloatingWidgetService.PREF_AUTO_START_BOOT, false);

        if (!enabled || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context))) {
            return;
        }

        Intent serviceIntent = new Intent(context, FloatingWidgetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
