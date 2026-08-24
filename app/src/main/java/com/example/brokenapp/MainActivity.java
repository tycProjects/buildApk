package com.example.brokenapp;

import android.app.Activity;
import android.os.Bundle;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ForegroundInfo;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WorkManager.getInstance(this).enqueue((androidx.work.WorkRequest) null);
    }

    // Triggers usesAnyForeground AND usesDataSync in the source scan.
    public static class SyncWorker extends Worker {
        public SyncWorker(android.content.Context context, WorkerParameters params) {
            super(context, params);
        }
        @Override
        public androidx.work.ListenableWorker.Result doWork() {
            ForegroundInfo info = new ForegroundInfo(1, null);
            // FOREGROUND_SERVICE_TYPE_DATA_SYNC referenced in source so the
            // usesDataSync regex matches.
            int type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            return androidx.work.ListenableWorker.Result.success();
        }
    }
}
