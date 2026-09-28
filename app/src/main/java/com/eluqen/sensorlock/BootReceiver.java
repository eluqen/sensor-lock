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
        if (intent == null) return;
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction());
        boolean updated = Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());
        if (!boot && !updated) return;
        RecoveryDiagnostics.event(boot ? "boot_recovery_received" : "package_update_recovery_received");

        // APK replacement can stop an app_process bridge even though the
        // pairing key and sensor-privacy state are still valid. Do not rotate
        // local bridge credentials, clear pairing, or restore setup settings.
        if (boot) {
            SensorController.beginBootRecovery(context);
            SensorController.cleanupNow(context);
        }

        boolean canRecover = SensorController.isPairingVerified(context)
                && context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (canRecover) {
            SensorController.refreshConnectionHealthBackground(
                    context,
                    result -> SensorController.noteBackgroundRecoveryResult(
                            context, result.success));
        }
        if (!boot && !canRecover) return;

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
