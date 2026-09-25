package com.eluqen.sensorlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CleanupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SensorController.cleanupNow(context);
    }
}
