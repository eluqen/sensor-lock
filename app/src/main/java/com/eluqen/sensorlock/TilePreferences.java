package com.eluqen.sensorlock;

import android.content.Context;

public final class TilePreferences {
    public static final int MODE_CAMERA = 1;
    public static final int MODE_MICROPHONE = 2;
    public static final int MODE_BOTH = MODE_CAMERA | MODE_MICROPHONE;

    private static final String PREFS = "tile_preferences";
    private static final String KEY_MODE = "sensor_mode";

    private TilePreferences() {}

    public static int getMode(Context context) {
        int mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, MODE_BOTH);
        return isValidMode(mode) ? mode : MODE_BOTH;
    }

    public static void setMode(Context context, int mode) {
        if (!isValidMode(mode)) mode = MODE_BOTH;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MODE, mode).apply();
    }

    public static boolean isValidMode(int mode) {
        return mode == MODE_CAMERA || mode == MODE_MICROPHONE || mode == MODE_BOTH;
    }
}
