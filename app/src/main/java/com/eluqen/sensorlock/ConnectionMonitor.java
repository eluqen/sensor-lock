package com.eluqen.sensorlock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConnectionMonitor {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static ScheduledExecutorService scheduler;

    private ConnectionMonitor() {}

    public static void start(Context context) {
        if (!STARTED.compareAndSet(false, true)) return;

        final Context app = context.getApplicationContext();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (app.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                if (!SensorController.isPairingVerified(app)
                        && !SensorController.isBridgeReadyCached(app)) {
                    return;
                }

                // Do not stop retrying merely because an early post-boot
                // attempt looked like revoked pairing. Only repeated complete
                // recovery failures are allowed to confirm that condition.
                if (!SensorController.isBridgeReadyCached(app) && !hasWifiTransport(app)) {
                    return;
                }

                SensorController.refreshConnectionHealthBackground(
                        app,
                        result -> SensorController.noteBackgroundRecoveryResult(
                                app, result.success));
            } catch (Throwable ignored) {
            }
        }, 2, 10, TimeUnit.SECONDS);
    }

    private static boolean hasWifiTransport(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        try {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
