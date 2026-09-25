package com.eluqen.sensorlock;

import android.os.Build;

public final class DeviceCompatibility {
    private DeviceCompatibility() {}

    /**
     * Sensor Lock supports Android 11 (API 30) and newer.
     *
     * Do not use framework capability probes as a compatibility gate here.
     * The protection path uses local ADB + cmd sensor_privacy and verifies the
     * real resulting state after each operation. A ROM-specific framework
     * capability report must not disable an otherwise working device.
     */
    public static boolean isAndroidVersionSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static boolean isSupported(android.content.Context context) {
        return isAndroidVersionSupported();
    }

    public static String androidVersion() {
        return Build.VERSION.RELEASE == null ? "unknown" : Build.VERSION.RELEASE;
    }
}
