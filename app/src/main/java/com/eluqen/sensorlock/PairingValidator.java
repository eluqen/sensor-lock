package com.eluqen.sensorlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

/** Independent ADB credential check; never touches the active shell bridge or ADB singleton. */
final class PairingValidator {
    enum Outcome { AUTHENTICATED, REVOKED, UNDETERMINED, NO_WIFI, ALREADY_RUNNING }
    interface Callback { void complete(Outcome outcome); }

    private static final String STATE = "pairing_auth_state";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile long lastBackgroundAttemptMs;
    private static final long BACKGROUND_INTERVAL_MS = 40_000L;

    private PairingValidator() {}

    static ConnectionPhase.Pairing current(Context c) {
        int state = prefs(c).getInt(STATE, 0);
        if (state == 2) return ConnectionPhase.Pairing.REVOKED;
        if (state == 1) return ConnectionPhase.Pairing.VERIFIED;
        return ConnectionPhase.Pairing.LAST_KNOWN;
    }

    static void pairedSuccessfully(Context c) {
        GENERATION.incrementAndGet();
        prefs(c).edit().putInt(STATE, 1).apply();
    }

    static void validateAsync(Context context, boolean ownerRequested, Callback cb) {
        Context c = context.getApplicationContext();
        if (!ConnectionMonitor.hasWifiTransport(c)) {
            result(cb, Outcome.NO_WIFI);
            return;
        }
        if (!ownerRequested) {
            if (!SensorController.isPairingVerified(c)
                    || !SensorController.isBridgeReadyCached(c)
                    || SensorController.isToggleInFlight()
                    || SensorController.isBackgroundRecoveryInFlight()
                    || current(c) == ConnectionPhase.Pairing.REVOKED) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastBackgroundAttemptMs < BACKGROUND_INTERVAL_MS) return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            result(cb, Outcome.ALREADY_RUNNING);
            return;
        }
        lastBackgroundAttemptMs = SystemClock.elapsedRealtime();
        int generation = GENERATION.get();
        EXECUTOR.submit(() -> {
            Outcome outcome = Outcome.UNDETERMINED;
            try {
                ConnectionPhase.AuthenticationEvidence evidence =
                        new ConnectionPhase.AuthenticationEvidence();
                for (int attempt = 0; attempt < (ownerRequested ? 1 : 2); attempt++) {
                    if (!ConnectionMonitor.hasWifiTransport(c)) {
                        outcome = Outcome.NO_WIFI;
                        break;
                    }
                    Outcome one = probeOnce(c);
                    if (one == Outcome.AUTHENTICATED) {
                        evidence.authenticated();
                        outcome = one;
                        break;
                    }
                    if (one != Outcome.REVOKED) {
                        evidence.transportUnavailable();
                        outcome = Outcome.UNDETERMINED;
                        break;
                    }
                    evidence.authenticationRejected();
                    if (evidence.current() == ConnectionPhase.Pairing.REVOKED) {
                        outcome = Outcome.REVOKED;
                        break;
                    }
                    try { Thread.sleep(ownerRequested ? 0 : 750); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        outcome = Outcome.UNDETERMINED;
                        break;
                    }
                }
                // An owner-triggered pairing completion supersedes any older probe result.
                if (generation == GENERATION.get()) {
                    if (outcome == Outcome.AUTHENTICATED) {
                        prefs(c).edit().putInt(STATE, 1).apply();
                        SensorController.pairingVerified(c);
                        if (SensorController.isBridgeReadyCached(c)
                                && SensorController.verifyLocalBridgeFunctional(c)) {
                            SensorController.pairingCompleted(c);
                        }
                        RecoveryDiagnostics.event("pairing_tls_verified");
                    } else if (outcome == Outcome.REVOKED) {
                        prefs(c).edit().putInt(STATE, 2).apply();
                        SensorController.markPairingStale(c);
                        RecoveryDiagnostics.event("pairing_revocation_confirmed");
                        try { PairingReceiver.start(c); } catch (Throwable promptError) { RecoveryDiagnostics.error("pairing_prompt_unavailable", promptError); }
                    } else {
                        RecoveryDiagnostics.event("pairing_validation_inconclusive");
                    }
                } else {
                    outcome = Outcome.UNDETERMINED;
                }
            } catch (Throwable t) {
                RecoveryDiagnostics.error("pairing_validation_exception", t);
                outcome = Outcome.UNDETERMINED;
            } finally {
                RUNNING.set(false);
            }
            result(cb, outcome);
        });
    }

    private static Outcome probeOnce(Context c) {
        AbsAdbConnectionManager probe = null;
        try {
            probe = AdbConnectionManager.createValidationInstance(c);
            probe.setThrowOnUnauthorised(true);
            probe.connectTls(c, 3000);
            return probe.isConnected() ? Outcome.AUTHENTICATED : Outcome.UNDETERMINED;
        } catch (AdbAuthenticationFailedException | AdbPairingRequiredException e) {
            return Outcome.REVOKED;
        } catch (Throwable t) {
            return Outcome.UNDETERMINED;
        } finally {
            if (probe != null) {
                try { probe.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void result(Callback cb, Outcome outcome) {
        if (cb != null) new Handler(Looper.getMainLooper()).post(() -> cb.complete(outcome));
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sensor_lock_state", Context.MODE_PRIVATE);
    }
}
