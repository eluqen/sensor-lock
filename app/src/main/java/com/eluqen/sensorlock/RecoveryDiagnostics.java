package com.eluqen.sensorlock;

import android.util.Log;

/** Deliberately non-sensitive local logcat diagnostics. No keys, tokens, pairing
 * codes, addresses, sensor operation payloads or identifiers are logged. */
final class RecoveryDiagnostics {
    static final String TAG = "SensorLockRecovery";
    private RecoveryDiagnostics() {}

    static void event(String event) {
        Log.i(TAG, event);
    }

    static void error(String event, Throwable error) {
        // The exception CLASS only; exception messages can contain sensitive
        // mDNS, port or connection information and must not be dumped to logs.
        Log.w(TAG, event + " reasonClass=" +
                (error == null ? "unknown" : error.getClass().getSimpleName()));
    }
}
