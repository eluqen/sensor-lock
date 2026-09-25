package com.eluqen.sensorlock;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageManager;

public class RecoveryJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                != PackageManager.PERMISSION_GRANTED) {
            jobFinished(params, false);
            return false;
        }

        boolean started = SensorController.refreshConnectionHealthBackground(
                getApplicationContext(),
                result -> {
                    SensorController.noteBackgroundRecoveryResult(
                            getApplicationContext(), result.success);

                    boolean retry =
                            !result.success
                            && SensorController.isPairingVerified(getApplicationContext());

                    jobFinished(params, retry);
                });

        if (!started) {
            jobFinished(params, true);
            return true;
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return SensorController.isPairingVerified(getApplicationContext());
    }
}
