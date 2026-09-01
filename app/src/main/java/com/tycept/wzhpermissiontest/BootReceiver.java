package com.tycept.wzhpermissiontest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Declared only to test the background/boot capability.
        // No background work is started automatically.
    }
}
