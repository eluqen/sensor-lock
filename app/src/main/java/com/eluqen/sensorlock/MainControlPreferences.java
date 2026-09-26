package com.eluqen.sensorlock;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Independently persisted MainActivity button selection.  This intentionally
 * does NOT use tile_preferences: editing either selector never changes the
 * other selector or the actual system privacy state.
 */
public final class MainControlPreferences {
    private static final String PREFS = "main_control_preferences";
    private static final String KEY_MODE = "main_sensor_mode";

    private MainControlPreferences() {}

    public static int getMode(Context context) {
        int saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, TilePreferences.MODE_BOTH);
        return TilePreferences.isValidMode(saved) ? saved : TilePreferences.MODE_BOTH;
    }

    public static void setMode(Context context, int mode) {
        if (!TilePreferences.isValidMode(mode)) {
            throw new IllegalArgumentException("At least one sensor is required");
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MODE, mode).apply();
    }

    /** Pure bit-mask transition; the last deselection automatically selects the other sensor. */
    public static int nextMode(int current, int sensor, boolean selected) {
        if (!TilePreferences.isValidMode(current)) current = TilePreferences.MODE_BOTH;
        if (sensor != TilePreferences.MODE_CAMERA
                && sensor != TilePreferences.MODE_MICROPHONE) {
            throw new IllegalArgumentException("Unknown sensor");
        }
        int next = selected ? (current | sensor) : (current & ~sensor);
        if (next == 0) {
            next = sensor == TilePreferences.MODE_CAMERA
                    ? TilePreferences.MODE_MICROPHONE : TilePreferences.MODE_CAMERA;
        }
        return next;
    }
}
