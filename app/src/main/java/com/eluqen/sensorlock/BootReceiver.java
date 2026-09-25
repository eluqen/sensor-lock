package com.eluqen.sensorlock;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

public class BootReceiver extends BroadcastReceiver {
    private static final int RECOVERY_JOB_ID = 4101;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SensorController.beginBootRecovery(context);
        SensorController.cleanupNow(context);

        if (SensorController.isPairingVerified(context)
                && context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            SensorController.refreshConnectionHealthBackground(
                    context,
                    result -> SensorController.noteBackgroundRecoveryResult(
                            context, result.success));
        }

        JobScheduler scheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        NetworkRequest wifi = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        JobInfo job = new JobInfo.Builder(
                RECOVERY_JOB_ID,
                new ComponentName(context, RecoveryJobService.class))
                .setRequiredNetwork(wifi)
                .setBackoffCriteria(10000, JobInfo.BACKOFF_POLICY_LINEAR)
                .build();

        scheduler.schedule(job);
    }
}
