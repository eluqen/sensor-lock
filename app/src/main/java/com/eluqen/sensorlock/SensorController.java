package com.eluqen.sensorlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;

public final class SensorController {
    public interface Callback { void onResult(Result result); }

    public static final class Result {
        public final boolean success;
        public final boolean blocked;
        public final boolean microphoneBlocked;
        public final boolean cameraBlocked;
        public final String message;
        public final String raw;

        Result(boolean success, boolean blocked, boolean microphoneBlocked,
               boolean cameraBlocked, String message, String raw) {
            this.success = success;
            this.blocked = blocked;
            this.microphoneBlocked = microphoneBlocked;
            this.cameraBlocked = cameraBlocked;
            this.message = message;
            this.raw = raw;
        }
    }

    public static final String ISSUE_NONE = "none";
    public static final String ISSUE_PAIRING_REVOKED = "pairing_revoked";
    public static final String ISSUE_NETWORK_REQUIRED = "network_required";
    public static final String ISSUE_SERVICE_START_FAILED = "service_start_failed";

    private static final String PREFS = "sensor_lock_state";
    private static final String HAS_STATE = "has_state";
    private static final String MIC_BLOCKED = "mic_blocked";
    private static final String CAMERA_BLOCKED = "camera_blocked";
    private static final String LAST_MESSAGE = "last_message";
    private static final String PAIRED_OK = "paired_ok";
    private static final String BRIDGE_READY = "bridge_ready";
    private static final String REPAIR_REQUIRED = "repair_required";
    private static final String CONNECTION_ISSUE = "connection_issue";
    private static final String HEALTH_CHECK_COMPLETED = "health_check_completed";
    private static final String DEBUG_SESSION_ACTIVE = "debug_session_active";
    private static final String BASE_DEV = "base_dev";
    private static final String BASE_WIFI = "base_wifi";
    private static final String BACKGROUND_RECOVERY_FAILURES = "background_recovery_failures";
    private static final String REPAIR_NOTIFICATION_POSTED = "repair_notification_posted";
    private static final String RECOVERY_GRACE_STARTED_AT = "recovery_grace_started_at";
    private static final int FAILURES_BEFORE_NOTIFY = 2;
    private static final long REPAIR_NOTIFICATION_GRACE_MS = 45_000L;
    private static final long BACKGROUND_RECOVERY_WINDOW_MS = 30_000L;
    private static final long BACKGROUND_RECOVERY_RETRY_MS = 1_200L;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    // Bridge reads, recovery and user mutations share one serial executor.
    // This prevents background recovery from racing a Quick Settings mutation.
    private static final ExecutorService BACKGROUND_RECOVERY_WORKER = WORKER;
    private static final ExecutorService ADB_IO = Executors.newCachedThreadPool();
    private static final AtomicBoolean BACKGROUND_HEALTH_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean TOGGLE_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile AbsAdbConnectionManager liveManager;

    private static final Pattern LOCAL_STATE =
            Pattern.compile("STATE\\s+mic=(\\d+)\\s+cam=(\\d+)");
    private static final int RECOVERY_CONNECT_ATTEMPTS = 4;
    private static final long RECOVERY_WARMUP_MS = 1400;
    private static final long RECOVERY_RETRY_DELAY_MS = 900;

    private SensorController() {}

    public static boolean hasKnownState(Context c) {
        return prefs(c).getBoolean(HAS_STATE, false);
    }

    public static boolean getCachedMicrophoneBlocked(Context c) {
        return prefs(c).getBoolean(MIC_BLOCKED, false);
    }

    public static boolean getCachedCameraBlocked(Context c) {
        return prefs(c).getBoolean(CAMERA_BLOCKED, false);
    }

    public static boolean getCachedBlocked(Context c) {
        return getCachedMicrophoneBlocked(c) && getCachedCameraBlocked(c);
    }

    public static String getLastMessage(Context c) {
        return prefs(c).getString(LAST_MESSAGE, "");
    }

    public static boolean isPairingVerified(Context c) {
        return prefs(c).getBoolean(PAIRED_OK, false);
    }

    public static boolean isBridgeReadyCached(Context c) {
        return prefs(c).getBoolean(BRIDGE_READY, false);
    }

    public static boolean isConnectionHealthyNow(Context c) {
        Context app = c.getApplicationContext();
        return isPairingVerified(app)
                && isBridgeReadyCached(app)
                && !isRepairRequired(app)
                && LocalShellClient.ping(app);
    }

    public static boolean verifyLocalBridgeFunctional(Context c) {
        try {
            PrivacyState state = functionalBridgeCheck(c.getApplicationContext());
            return state.valid;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isRepairRequired(Context c) {
        return prefs(c).getBoolean(REPAIR_REQUIRED, false);
    }

    public static String getConnectionIssue(Context c) {
        return prefs(c).getString(CONNECTION_ISSUE, ISSUE_NONE);
    }

    public static void noteBridgeStopped(Context c) {
        prefs(c).edit().putBoolean(BRIDGE_READY, false).apply();
    }

    public static void beginBootRecovery(Context context) {
        Context c = context.getApplicationContext();
        prefs(c).edit()
                .putInt(BACKGROUND_RECOVERY_FAILURES, 0)
                .putBoolean(REPAIR_NOTIFICATION_POSTED, false)
                .putBoolean(REPAIR_REQUIRED, false)
                .putString(CONNECTION_ISSUE, ISSUE_NONE)
                .putString(LAST_MESSAGE, "")
                .putLong(RECOVERY_GRACE_STARTED_AT, System.currentTimeMillis())
                .apply();
        cancelRepairNotification(c);
        noteBridgeStopped(c);
    }

    public static void noteBackgroundRecoveryResult(Context context, boolean success) {
        Context c = context.getApplicationContext();

        if (success) {
            prefs(c).edit()
                    .putInt(BACKGROUND_RECOVERY_FAILURES, 0)
                    .putBoolean(REPAIR_NOTIFICATION_POSTED, false)
                    .remove(RECOVERY_GRACE_STARTED_AT)
                    .apply();
            cancelRepairNotification(c);
            return;
        }

        if (!isRepairRequired(c)) return;

        android.content.SharedPreferences sp = prefs(c);
        long started = sp.getLong(RECOVERY_GRACE_STARTED_AT, 0L);
        if (started <= 0L) {
            started = System.currentTimeMillis();
        }

        int failures = sp.getInt(BACKGROUND_RECOVERY_FAILURES, 0) + 1;
        sp.edit()
                .putInt(BACKGROUND_RECOVERY_FAILURES, failures)
                .putLong(RECOVERY_GRACE_STARTED_AT, started)
                .apply();

        if (failures < FAILURES_BEFORE_NOTIFY) return;
        if (System.currentTimeMillis() - started < REPAIR_NOTIFICATION_GRACE_MS) return;

        // Keep the saved pairing key marked as known-good. Boot-time ADB
        // discovery/auth failures are not reliable proof that Android deleted it.
        notifyRepairIfConfirmed(c);
    }

    public static boolean isBackgroundRecoveryInFlight() {
        return BACKGROUND_HEALTH_IN_FLIGHT.get();
    }

    public static boolean refreshConnectionHealthBackground(
            final Context context, final Callback cb) {
        if (!BACKGROUND_HEALTH_IN_FLIGHT.compareAndSet(false, true)) {
            return false;
        }

        final Context c = context.getApplicationContext();
        RecoveryDiagnostics.event("background_health_queued");
        BACKGROUND_RECOVERY_WORKER.submit(() -> {
            Result result;
            try {
                if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                        != PackageManager.PERMISSION_GRANTED) {
                    result = failure(c, R.string.setup_required);
                } else if (LocalShellClient.ping(c)) {
                    PrivacyState state = functionalBridgeCheck(c);
                    saveStateSilent(c, state.microphoneBlocked, state.cameraBlocked,
                            isPairingVerified(c));
                    markBridgeReady(c);
                    result = new Result(true,
                            state.microphoneBlocked && state.cameraBlocked,
                            state.microphoneBlocked, state.cameraBlocked,
                            text(c, R.string.connection_ready), state.raw);
                } else if (recoverBridgeBackgroundWindow(c, BACKGROUND_RECOVERY_WINDOW_MS)) {
                    PrivacyState state = localState(c);
                    saveStateSilent(c, state.microphoneBlocked, state.cameraBlocked,
                            isPairingVerified(c));
                    markBridgeReady(c);
                    result = new Result(true,
                            state.microphoneBlocked && state.cameraBlocked,
                            state.microphoneBlocked, state.cameraBlocked,
                            text(c, R.string.connection_recovered), state.raw);
                } else {
                    result = failure(c, repairMessageRes(c));
                }
            } catch (Throwable t) {
                RecoveryDiagnostics.error("background_health_exception", t);
                result = failure(c, repairMessageRes(c));
            } finally {
                BACKGROUND_HEALTH_IN_FLIGHT.set(false);
            }

            // A successful health check often writes exactly the same cached
            // state. SharedPreferences does not notify MainActivity in that
            // case; emit an explicit completion edge AFTER clearing in-flight
            // so a previously drawn "Reconnecting" can never stay latched.
            prefs(c).edit().putLong(HEALTH_CHECK_COMPLETED,
                    android.os.SystemClock.elapsedRealtimeNanos()).apply();
            RecoveryDiagnostics.event(result.success
                    ? "background_health_verified"
                    : "background_health_failed issue=" + getConnectionIssue(c));
            notifyTile(c);
            if (cb != null) deliver(cb, result);
        });
        return true;
    }

    public static void refreshConnectionHealth(final Context context, final Callback cb) {
        final Context c = context.getApplicationContext();
        WORKER.submit(() -> {
            if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                    != PackageManager.PERMISSION_GRANTED) {
                deliver(cb, failure(c, R.string.setup_required));
                return;
            }

            try {
                if (LocalShellClient.ping(c)) {
                    PrivacyState state;
                    if (!isBridgeReadyCached(c) || isRepairRequired(c)) {
                        state = functionalBridgeCheck(c);
                    } else {
                        state = localState(c);
                    }
                    if (!state.valid) {
                        throw new Exception("Local bridge returned an invalid privacy state");
                    }
                    saveStateSilent(c, state.microphoneBlocked, state.cameraBlocked,
                            isPairingVerified(c));
                    markBridgeReady(c);
                    // A functional local bridge is already verified. Do not
                    // disconnect/reprobe the shared ADB manager on every UI
                    // refresh: it cannot improve this proof and can disrupt an
                    // in-progress recovery/authorization sequence.
                    deliver(cb, new Result(true, state.microphoneBlocked && state.cameraBlocked,
                            state.microphoneBlocked, state.cameraBlocked,
                            text(c, R.string.connection_ready), state.raw));
                    return;
                }

                if (recoverBridge(c)) {
                    PrivacyState state = localState(c);
                    if (!state.valid) {
                        throw new Exception("Recovered bridge returned an invalid privacy state");
                    }
                    saveStateSilent(c, state.microphoneBlocked, state.cameraBlocked,
                            isPairingVerified(c));
                    deliver(cb, new Result(true, state.microphoneBlocked && state.cameraBlocked,
                            state.microphoneBlocked, state.cameraBlocked,
                            text(c, R.string.connection_recovered), state.raw));
                    return;
                }

                deliver(cb, failure(c, repairMessageRes(c)));
            } catch (Throwable ignored) {
                deliver(cb, failure(c, repairMessageRes(c)));
            }
        });
    }

    public static boolean isToggleInFlight() {
        return TOGGLE_IN_FLIGHT.get();
    }

    public static boolean toggle(final Context context, final Callback cb) {
        return toggle(context, TilePreferences.MODE_BOTH, cb);
    }

    public static boolean toggle(final Context context, final int requestedMode,
                                 final Callback cb) {
        final Context c = context.getApplicationContext();
        final int mode = TilePreferences.isValidMode(requestedMode)
                ? requestedMode : TilePreferences.MODE_BOTH;

        // A tap while a previous mutation is still running is intentionally dropped.
        // This prevents delayed taps from becoming a burst of BLOCK/ALLOW commands.
        if (!TOGGLE_IN_FLIGHT.compareAndSet(false, true)) {
            RecoveryDiagnostics.event("mutation_duplicate_ignored");
            notifyTile(c);
            return false;
        }

        RecoveryDiagnostics.event("mutation_queued mode=" + mode
                + " bg=" + BACKGROUND_HEALTH_IN_FLIGHT.get());
        notifyTile(c);
        WORKER.submit(() -> {
            Boolean requestedTarget = null;
            try {
                if (!DeviceCompatibility.isSupported(c)) {
                    deliver(cb, failure(c, R.string.unsupported_title));
                    return;
                }
                if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                        != PackageManager.PERMISSION_GRANTED) {
                    deliver(cb, failure(c, R.string.setup_required));
                    return;
                }

                if (!ensureBridge(c)) {
                    deliver(cb, failure(c, repairMessageRes(c)));
                    return;
                }

                // Per-sensor commands are capability-gated. An older bridge left
                // alive across an app update is never sent a command it cannot
                // understand or a fallback that could alter the wrong sensor.
                if (mode != TilePreferences.MODE_BOTH && !ensureSelectiveBridge(c)) {
                    int messageRes = isRepairRequired(c)
                            ? repairMessageRes(c)
                            : R.string.selective_control_service_refresh;
                    deliver(cb, failure(c, messageRes));
                    return;
                }

                PrivacyState current = localState(c);
                if (!current.valid) throw new Exception("Invalid local bridge state");

                boolean target = !selectedSensorsBlocked(current, mode);
                requestedTarget = target;
                String raw = LocalShellClient.request(
                        c, commandForMode(mode, target), 5000);
                PrivacyState verified = parseLocalState(raw);

                // Selected-only mode must NEVER claim success if the OTHER sensor
                // also changed. This is a state-verification failure, not an
                // invitation to retry the command and risk a second mutation.
                if (verified.valid && !unselectedSensorsUnchanged(current, verified, mode)) {
                    RecoveryDiagnostics.event("unselected_sensor_changed_during_mutation");
                    markBridgeReady(c);
                    saveState(c, verified.microphoneBlocked, verified.cameraBlocked,
                            text(c, R.string.unselected_sensor_changed), isPairingVerified(c));
                    deliver(cb, new Result(false, false, verified.microphoneBlocked,
                            verified.cameraBlocked,
                            text(c, R.string.unselected_sensor_changed), raw));
                    return;
                }
                if (!verified.valid || !selectedSensorsMatch(verified, mode, target)) {
                    throw new Exception("Local bridge verification failed");
                }

                String message = text(c, successMessageRes(mode, target));
                markBridgeReady(c);
                saveState(c, verified.microphoneBlocked, verified.cameraBlocked,
                        message, isPairingVerified(c));
                deliver(cb, new Result(true, target, verified.microphoneBlocked,
                        verified.cameraBlocked, message, raw));
            } catch (Throwable first) {
                noteBridgeStopped(c);
                if (!recoverBridge(c)) {
                    deliver(cb, failure(c, repairMessageRes(c)));
                    return;
                }

                try {
                    if (mode != TilePreferences.MODE_BOTH && !ensureSelectiveBridge(c)) {
                        int messageRes = isRepairRequired(c)
                                ? repairMessageRes(c)
                                : R.string.selective_control_service_refresh;
                        deliver(cb, failure(c, messageRes));
                        return;
                    }

                    PrivacyState current = localState(c);
                    if (!current.valid) throw new Exception("Invalid recovered state");

                    boolean target = requestedTarget != null
                            ? requestedTarget.booleanValue()
                            : !selectedSensorsBlocked(current, mode);
                    String raw = LocalShellClient.request(
                            c, commandForMode(mode, target), 5000);
                    PrivacyState verified = parseLocalState(raw);
                    if (verified.valid && !unselectedSensorsUnchanged(current, verified, mode)) {
                        RecoveryDiagnostics.event("unselected_sensor_changed_during_recovery");
                        markBridgeReady(c);
                        saveState(c, verified.microphoneBlocked, verified.cameraBlocked,
                                text(c, R.string.unselected_sensor_changed), isPairingVerified(c));
                        deliver(cb, new Result(false, false, verified.microphoneBlocked,
                                verified.cameraBlocked,
                                text(c, R.string.unselected_sensor_changed), raw));
                        return;
                    }
                    if (!verified.valid || !selectedSensorsMatch(verified, mode, target)) {
                        throw new Exception("Invalid recovered state");
                    }

                    String message = text(c, successMessageRes(mode, target));
                    markBridgeReady(c);
                    saveState(c, verified.microphoneBlocked, verified.cameraBlocked,
                            message, isPairingVerified(c));
                    deliver(cb, new Result(true, target, verified.microphoneBlocked,
                            verified.cameraBlocked, message, raw));
                } catch (Throwable second) {
                    markRepair(c, ISSUE_SERVICE_START_FAILED);
                    deliver(cb, failure(c, R.string.connection_repair_required));
                }
            } finally {
                TOGGLE_IN_FLIGHT.set(false);
                notifyTile(c);
                RecoveryDiagnostics.event("mutation_worker_finished issue=" + getConnectionIssue(c));
            }
        });
        return true;
    }

    private static boolean selectedSensorsBlocked(PrivacyState state, int mode) {
        boolean cameraOk = (mode & TilePreferences.MODE_CAMERA) == 0
                || state.cameraBlocked;
        boolean microphoneOk = (mode & TilePreferences.MODE_MICROPHONE) == 0
                || state.microphoneBlocked;
        return cameraOk && microphoneOk;
    }

    private static boolean selectedSensorsMatch(PrivacyState state, int mode,
                                                boolean blocked) {
        if ((mode & TilePreferences.MODE_CAMERA) != 0
                && state.cameraBlocked != blocked) return false;
        if ((mode & TilePreferences.MODE_MICROPHONE) != 0
                && state.microphoneBlocked != blocked) return false;
        return true;
    }

    private static boolean unselectedSensorsUnchanged(PrivacyState before,
                                                     PrivacyState after, int mode) {
        if ((mode & TilePreferences.MODE_CAMERA) == 0
                && before.cameraBlocked != after.cameraBlocked) return false;
        if ((mode & TilePreferences.MODE_MICROPHONE) == 0
                && before.microphoneBlocked != after.microphoneBlocked) return false;
        return true;
    }

    private static String commandForMode(int mode, boolean blocked) {
        if (mode == TilePreferences.MODE_CAMERA) {
            return blocked ? "BLOCK_CAMERA" : "ALLOW_CAMERA";
        }
        if (mode == TilePreferences.MODE_MICROPHONE) {
            return blocked ? "BLOCK_MICROPHONE" : "ALLOW_MICROPHONE";
        }
        return blocked ? "BLOCK" : "ALLOW";
    }

    private static int successMessageRes(int mode, boolean blocked) {
        if (mode == TilePreferences.MODE_CAMERA) {
            return blocked ? R.string.success_camera_blocked
                    : R.string.success_camera_allowed;
        }
        if (mode == TilePreferences.MODE_MICROPHONE) {
            return blocked ? R.string.success_microphone_blocked
                    : R.string.success_microphone_allowed;
        }
        return blocked ? R.string.success_blocked : R.string.success_allowed;
    }

    public static void verify(final Context context, final Callback cb) {
        final Context c = context.getApplicationContext();
        WORKER.submit(() -> {
            if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                    != PackageManager.PERMISSION_GRANTED) {
                deliver(cb, failure(c, R.string.setup_required));
                return;
            }

            try {
                if (!ensureBridge(c)) {
                    deliver(cb, failure(c, repairMessageRes(c)));
                    return;
                }

                PrivacyState state = localState(c);
                if (!state.valid) throw new Exception("Invalid state");

                String message;
                if (state.microphoneBlocked && state.cameraBlocked) {
                    message = text(c, R.string.verify_blocked);
                } else if (!state.microphoneBlocked && !state.cameraBlocked) {
                    message = text(c, R.string.verify_allowed);
                } else {
                    message = text(c, R.string.verify_mixed);
                }

                saveState(c, state.microphoneBlocked, state.cameraBlocked, message, isPairingVerified(c));
                markBridgeReady(c);
                deliver(cb, new Result(true,
                        state.microphoneBlocked && state.cameraBlocked,
                        state.microphoneBlocked, state.cameraBlocked, message, state.raw));
                notifyTile(c);
            } catch (Throwable t) {
                noteBridgeStopped(c);
                if (!recoverBridge(c)) {
                    deliver(cb, failure(c, repairMessageRes(c)));
                } else {
                    try {
                        PrivacyState state = localState(c);
                        String message = state.microphoneBlocked && state.cameraBlocked
                                ? text(c, R.string.verify_blocked)
                                : (!state.microphoneBlocked && !state.cameraBlocked
                                ? text(c, R.string.verify_allowed)
                                : text(c, R.string.verify_mixed));
                        saveState(c, state.microphoneBlocked, state.cameraBlocked, message, isPairingVerified(c));
                        markBridgeReady(c);
                        deliver(cb, new Result(true,
                                state.microphoneBlocked && state.cameraBlocked,
                                state.microphoneBlocked, state.cameraBlocked, message, state.raw));
                    } catch (Throwable second) {
                        markRepair(c, ISSUE_SERVICE_START_FAILED);
                        deliver(cb, failure(c, R.string.connection_repair_required));
                    }
                }
                notifyTile(c);
            }
        });
    }

    private static boolean bridgeSupportsSelective(Context c) {
        try {
            return "CAPS SELECTIVE_V1".equals(
                    LocalShellClient.request(c, "CAPS", 800));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean ensureSelectiveBridge(Context c) {
        if (bridgeSupportsSelective(c)) return true;

        // If the old bridge is still healthy, keep it running until a fresh ADB
        // session has actually connected. This preserves existing protection if
        // Wi-Fi is unavailable during an app update.
        boolean oldBridgeHealthy = false;
        try {
            oldBridgeHealthy = LocalShellClient.ping(c)
                    && functionalBridgeCheck(c).valid;
        } catch (Throwable ignored) {
        }

        if (!oldBridgeHealthy) {
            return recoverBridge(c) && bridgeSupportsSelective(c);
        }

        AbsAdbConnectionManager manager = null;
        boolean adbConnected = false;
        try {
            recordDebugBaseline(c);
            enableDebug(c);

            manager = AdbConnectionManager.freshInstance(c);
            manager.setThrowOnUnauthorised(true);
            liveManager = manager;
            connectExistingPairingAfterWarmup(c, manager);
            if (!manager.isConnected()) return false;

            adbConnected = true;
            startLocalBridgeViaManager(c, manager, true);
            if (!bridgeSupportsSelective(c)) {
                throw new Exception("Updated bridge capability check failed");
            }

            PrivacyState state = localState(c);
            if (!state.valid) {
                throw new Exception("Updated bridge returned invalid state");
            }
            saveStateSilent(c, state.microphoneBlocked, state.cameraBlocked,
                    isPairingVerified(c));
            markBridgeReady(c);
            return true;
        } catch (AdbAuthenticationFailedException | AdbPairingRequiredException e) {
            if (LocalShellClient.ping(c)) {
                markBridgeReady(c);
            } else {
                markRepair(c, ISSUE_PAIRING_REVOKED);
            }
            return false;
        } catch (Throwable e) {
            if (LocalShellClient.ping(c)) {
                // The v1.0 bridge is still healthy. Keep core protection usable
                // and report only that selective mode needs a service refresh.
                markBridgeReady(c);
            } else if (adbConnected) {
                markRepair(c, ISSUE_SERVICE_START_FAILED);
            }
            return false;
        } finally {
            if (manager != null) {
                try { manager.disconnect(); } catch (Throwable ignored) {}
            }
            AdbConnectionManager.resetInstance();
            liveManager = null;
            restoreDebugBaseline(c);
        }
    }

    private static boolean ensureBridge(Context c) {
        if (LocalShellClient.ping(c)) {
            try {
                if (isBridgeReadyCached(c) && !isRepairRequired(c)) {
                    return true;
                }
                PrivacyState state = functionalBridgeCheck(c);
                if (state.valid) {
                    saveStateSilent(c, state.microphoneBlocked, state.cameraBlocked,
                            isPairingVerified(c));
                    markBridgeReady(c);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        noteBridgeStopped(c);
        return recoverBridge(c);
    }

    private static boolean recoverBridgeBackgroundWindow(Context c, long windowMs) {
        if (!isPairingVerified(c)) {
            markRepair(c, ISSUE_PAIRING_REVOKED);
            return false;
        }

        AbsAdbConnectionManager manager = null;
        boolean adbConnected = false;
        String lastIssue = ISSUE_NETWORK_REQUIRED;
        long deadline = android.os.SystemClock.elapsedRealtime() + windowMs;

        try {
            recordDebugBaseline(c);
            enableDebug(c);

            // Keep Wireless debugging enabled for the entire recovery window.
            // Android often exposes the setting before the TLS/mDNS endpoint is
            // actually ready after boot, so toggling it off between attempts only
            // delays recovery further.
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (LocalShellClient.ping(c)) {
                    try {
                        PrivacyState functional = functionalBridgeCheck(c);
                        if (functional.valid) {
                            saveStateSilent(c, functional.microphoneBlocked,
                                    functional.cameraBlocked, isPairingVerified(c));
                            markBridgeReady(c);
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }

                try {
                    manager = AdbConnectionManager.freshInstance(c);
                    manager.setThrowOnUnauthorised(true);
                    liveManager = manager;

                    try {
                        manager.connectTls(c, 2500);
                    } catch (AdbAuthenticationFailedException | AdbPairingRequiredException auth) {
                        lastIssue = ISSUE_PAIRING_REVOKED;
                        throw auth;
                    } catch (Throwable transientFailure) {
                        lastIssue = ISSUE_NETWORK_REQUIRED;
                        throw transientFailure;
                    }

                    if (!manager.isConnected()) {
                        lastIssue = ISSUE_NETWORK_REQUIRED;
                    } else {
                        adbConnected = true;
                        startLocalBridgeViaManager(c, manager);

                        if (LocalShellClient.ping(c)) {
                            PrivacyState functional = functionalBridgeCheck(c);
                            if (functional.valid) {
                                saveStateSilent(c, functional.microphoneBlocked,
                                        functional.cameraBlocked, isPairingVerified(c));
                                markBridgeReady(c);
                                return true;
                            }
                        }
                        lastIssue = ISSUE_SERVICE_START_FAILED;
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (manager != null) {
                        try { manager.disconnect(); } catch (Throwable ignored) {}
                    }
                    AdbConnectionManager.resetInstance();
                    liveManager = null;
                    adbConnected = false;
                }

                long remaining = deadline - android.os.SystemClock.elapsedRealtime();
                if (remaining <= 0) break;
                try {
                    Thread.sleep(Math.min(BACKGROUND_RECOVERY_RETRY_MS, remaining));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            markRepair(c, lastIssue);
            return false;
        } catch (Throwable ignored) {
            markRepair(c, adbConnected ? ISSUE_SERVICE_START_FAILED : lastIssue);
            return false;
        } finally {
            if (manager != null) {
                try { manager.disconnect(); } catch (Throwable ignored) {}
            }
            AdbConnectionManager.resetInstance();
            liveManager = null;
            restoreDebugBaseline(c);
        }
    }

    private static boolean recoverBridge(Context c) {
        if (!isPairingVerified(c)) {
            markRepair(c, ISSUE_PAIRING_REVOKED);
            return false;
        }

        AbsAdbConnectionManager manager = null;
        boolean adbConnected = false;
        try {
            recordDebugBaseline(c);
            enableDebug(c);
            manager = AdbConnectionManager.getInstance(c);
            manager.setThrowOnUnauthorised(true);
            liveManager = manager;

            if (!manager.isConnected()) {
                connectExistingPairingAfterWarmup(c, manager);
            }

            if (!manager.isConnected()) {
                markRepair(c, ISSUE_NETWORK_REQUIRED);
                return false;
            }

            adbConnected = true;
            startLocalBridgeViaManager(c, manager);
            if (!LocalShellClient.ping(c)) {
                markRepair(c, ISSUE_SERVICE_START_FAILED);
                return false;
            }

            PrivacyState functional = functionalBridgeCheck(c);
            if (!functional.valid) {
                markRepair(c, ISSUE_SERVICE_START_FAILED);
                return false;
            }
            saveStateSilent(c, functional.microphoneBlocked, functional.cameraBlocked,
                    isPairingVerified(c));
            markBridgeReady(c);
            return true;
        } catch (AdbAuthenticationFailedException | AdbPairingRequiredException e) {
            markRepair(c, ISSUE_PAIRING_REVOKED);
            return false;
        } catch (Throwable e) {
            markRepair(c, adbConnected ? ISSUE_SERVICE_START_FAILED : ISSUE_NETWORK_REQUIRED);
            return false;
        } finally {
            if (manager != null) {
                try { manager.disconnect(); } catch (Throwable ignored) {}
            }
            liveManager = null;
            restoreDebugBaseline(c);
        }
    }

    private static void connectExistingPairingAfterWarmup(
            Context c, AbsAdbConnectionManager manager) throws Exception {
        // Enabling Wireless debugging immediately after boot does not mean the
        // ADB TLS/mDNS endpoint is ready in the same instant. The old UI repair
        // path appeared to fix this simply because opening Settings gave Android
        // enough time to bring the daemon up. Do that wait/retry here instead.
        Thread.sleep(RECOVERY_WARMUP_MS);

        Throwable last = null;
        int authFailures = 0;

        for (int attempt = 0; attempt < RECOVERY_CONNECT_ATTEMPTS; attempt++) {
            try {
                if (manager.isConnected()) return;
                manager.connectTls(c, 3200);
                if (manager.isConnected()) return;
            } catch (AdbAuthenticationFailedException | AdbPairingRequiredException auth) {
                last = auth;
                authFailures++;
            } catch (Throwable transientFailure) {
                last = transientFailure;
            }

            try { manager.disconnect(); } catch (Throwable ignored) {}

            if (attempt + 1 < RECOVERY_CONNECT_ATTEMPTS) {
                Thread.sleep(RECOVERY_RETRY_DELAY_MS);
            }
        }

        if (authFailures == RECOVERY_CONNECT_ATTEMPTS && last != null) {
            if (last instanceof AdbAuthenticationFailedException) {
                throw (AdbAuthenticationFailedException) last;
            }
            if (last instanceof AdbPairingRequiredException) {
                throw (AdbPairingRequiredException) last;
            }
        }

        if (last instanceof Exception) throw (Exception) last;
        throw new Exception("Wireless debugging did not become ready in time");
    }

    public static void startLocalBridgeViaManager(Context c,
                                                   AbsAdbConnectionManager manager) throws Exception {
        startLocalBridgeViaManager(c, manager, false);
    }

    private static void startLocalBridgeViaManager(Context c,
                                                   AbsAdbConnectionManager manager,
                                                   boolean requireCurrentProtocol) throws Exception {
        if (LocalShellClient.ping(c) && verifyLocalBridgeFunctional(c)) {
            if (!requireCurrentProtocol || bridgeSupportsSelective(c)) {
                return;
            }
        }

        String pkg = c.getPackageName();

        // A stale shell bridge may survive an app process restart or an interrupted
        // upgrade. Remove only our own named shell process before creating a new one.
        try {
            runAdbShell(manager,
                    "shell:for P in $(pidof sensorlock_bridge 2>/dev/null); " +
                    "do kill \"$P\" 2>/dev/null; done; echo SENSORLOCK_OLD_BRIDGE_CLEARED");
            Thread.sleep(120);
        } catch (Throwable ignored) {}

        for (int attempt = 0; attempt < 3; attempt++) {
            LocalShellClient.rotateIdentity(c);
            int port = LocalShellClient.getPort(c);
            String token = LocalShellClient.getToken(c);

            String shell = "shell:APK=$(pm path " + pkg +
                    " | head -n 1 | cut -d: -f2); " +
                    "if [ -z \"$APK\" ]; then echo ERR_NO_APK; else " +
                    "if command -v setsid >/dev/null 2>&1; then " +
                    "setsid app_process -Djava.class.path=\"$APK\" /system/bin " +
                    "--nice-name=sensorlock_bridge com.eluqen.sensorlock.ShellBridge " +
                    port + " " + token +
                    " >/dev/null 2>&1 </dev/null & " +
                    "else (trap '' HUP; exec app_process -Djava.class.path=\"$APK\" " +
                    "/system/bin --nice-name=sensorlock_bridge " +
                    "com.eluqen.sensorlock.ShellBridge " + port + " " + token +
                    " >/dev/null 2>&1 </dev/null) & fi; " +
                    "echo SENSORLOCK_BRIDGE_STARTED; fi";

            runAdbShell(manager, shell);

            for (int i = 0; i < 35; i++) {
                if (LocalShellClient.ping(c)) {
                    return;
                }
                Thread.sleep(100);
            }
        }
        throw new Exception("Local shell bridge did not start after retries");
    }

    private static PrivacyState localState(Context c) throws Exception {
        return parseLocalState(LocalShellClient.request(c, "STATE", 3000));
    }

    private static PrivacyState functionalBridgeCheck(Context c) throws Exception {
        String raw = LocalShellClient.request(c, "SELFTEST", 8000);
        PrivacyState state = parseLocalState(raw);
        if (!state.valid) {
            throw new Exception("Local bridge functional self-test failed: " + raw);
        }
        return state;
    }

    private static PrivacyState parseLocalState(String raw) {
        Matcher matcher = LOCAL_STATE.matcher(raw == null ? "" : raw);
        if (!matcher.find()) return new PrivacyState(false, false, false, raw);
        try {
            boolean mic = Integer.parseInt(matcher.group(1)) == 1;
            boolean cam = Integer.parseInt(matcher.group(2)) == 1;
            return new PrivacyState(true, mic, cam, raw);
        } catch (Throwable ignored) {
            return new PrivacyState(false, false, false, raw);
        }
    }

    public static synchronized void recordDebugBaseline(Context c) {
        android.content.SharedPreferences sp = prefs(c);
        if (sp.getBoolean(DEBUG_SESSION_ACTIVE, false)) return;

        int dev = Settings.Global.getInt(
                c.getContentResolver(), "development_settings_enabled", 0);
        int wifi = Settings.Global.getInt(
                c.getContentResolver(), "adb_wifi_enabled", 0);

        sp.edit()
                .putInt(BASE_DEV, dev)
                .putInt(BASE_WIFI, wifi)
                .putBoolean(DEBUG_SESSION_ACTIVE, true)
                .commit();
    }

    public static boolean hasTemporaryDebugSettings(Context c) {
        return prefs(c.getApplicationContext()).getBoolean(DEBUG_SESSION_ACTIVE, false);
    }

    public static boolean prepareDebugForPairing(Context c) {
        try {
            enableDebug(c.getApplicationContext());
            return true;
        } catch (Throwable ignored) {
            cleanupNow(c.getApplicationContext());
            return false;
        }
    }

    private static void enableDebug(Context c) throws Exception {
        recordDebugBaseline(c);
        boolean dev = Settings.Global.putInt(
                c.getContentResolver(), "development_settings_enabled", 1);
        boolean wifi = Settings.Global.putInt(
                c.getContentResolver(), "adb_wifi_enabled", 1);
        if (!dev || !wifi) throw new Exception("Could not enable temporary ADB settings");
    }

    private static synchronized void restoreDebugBaseline(Context c) {
        android.content.SharedPreferences sp = prefs(c);
        if (!sp.getBoolean(DEBUG_SESSION_ACTIVE, false)) return;

        int baseWifi = sp.getInt(BASE_WIFI, 0);
        int baseDev = sp.getInt(BASE_DEV, 0);

        try {
            Settings.Global.putInt(c.getContentResolver(), "adb_wifi_enabled", baseWifi);
        } catch (Throwable ignored) {}
        try {
            Settings.Global.putInt(c.getContentResolver(), "development_settings_enabled", baseDev);
        } catch (Throwable ignored) {}

        sp.edit().putBoolean(DEBUG_SESSION_ACTIVE, false).apply();
    }

    public static synchronized void cleanupNow(Context c) {
        AbsAdbConnectionManager manager = liveManager;
        if (manager != null) {
            try { manager.disconnect(); } catch (Throwable ignored) {}
        }
        liveManager = null;
        restoreDebugBaseline(c);
    }

    public static void pairingVerified(Context c) {
        prefs(c).edit()
                .putBoolean(PAIRED_OK, true)
                .apply();
    }

    public static void pairingCompleted(Context c) {
        pairingVerified(c);
        prefs(c).edit()
                .putBoolean(REPAIR_REQUIRED, false)
                .putString(CONNECTION_ISSUE, ISSUE_NONE)
                .apply();
        markBridgeReady(c);
    }

    public static void bridgeStartFailedAfterPairing(Context c) {
        noteBridgeStopped(c);
        markRepair(c, ISSUE_SERVICE_START_FAILED);
    }

    private static void markBridgeReady(Context c) {
        android.content.SharedPreferences sp = prefs(c);
        boolean pairingValid = sp.getBoolean(PAIRED_OK, false);

        android.content.SharedPreferences.Editor editor = sp.edit()
                .putBoolean(BRIDGE_READY, true)
                .putInt(BACKGROUND_RECOVERY_FAILURES, 0)
                .putBoolean(REPAIR_NOTIFICATION_POSTED, false)
                .remove(RECOVERY_GRACE_STARTED_AT);

        // A working bridge means communication is currently healthy, so any
        // previous reconnect notification is stale regardless of pairing metadata.
        cancelRepairNotification(c);

        if (pairingValid) {
            editor.putBoolean(REPAIR_REQUIRED, false)
                    .putString(CONNECTION_ISSUE, ISSUE_NONE)
                    .putString(LAST_MESSAGE, "")
                    .apply();
            PairingReceiver.clearPairingNotifications(c);
        } else {
            editor.putBoolean(REPAIR_REQUIRED, true)
                    .putString(CONNECTION_ISSUE, ISSUE_PAIRING_REVOKED)
                    .apply();
        }
        notifyTile(c);
    }

    private static void probePairingIfPossible(Context c) {
        int wirelessDebugging = Settings.Global.getInt(
                c.getContentResolver(), "adb_wifi_enabled", 0);
        if (wirelessDebugging != 1) return;

        AbsAdbConnectionManager manager = null;
        try {
            manager = AdbConnectionManager.getInstance(c);
            manager.setThrowOnUnauthorised(true);
            try { manager.disconnect(); } catch (Throwable ignored) {}

            boolean connected = manager.connectTls(c, 1800);
            if (connected || manager.isConnected()) {
                prefs(c).edit().putBoolean(PAIRED_OK, true).apply();
                markBridgeReady(c);
            }
        } catch (AdbAuthenticationFailedException | AdbPairingRequiredException ignored) {
            // A single probe immediately after boot is not enough evidence that
            // the saved pairing key was revoked. Full recovery retries and only
            // then classifies persistent authentication failure as stale pairing.
        } catch (Throwable ignored) {
            // No network / no mDNS result is not proof that pairing was revoked.
        } finally {
            if (manager != null) {
                try { manager.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void markPairingStale(Context c) {
        boolean bridgeStillReady = LocalShellClient.ping(c);
        prefs(c).edit()
                .putBoolean(PAIRED_OK, false)
                .putBoolean(BRIDGE_READY, bridgeStillReady)
                .putBoolean(REPAIR_REQUIRED, true)
                .putString(CONNECTION_ISSUE, ISSUE_PAIRING_REVOKED)
                .putString(LAST_MESSAGE, text(c, R.string.connection_pairing_revoked))
                .apply();
    }

    private static void markRepair(Context c, String issue) {
        prefs(c).edit()
                .putBoolean(BRIDGE_READY, false)
                .putBoolean(REPAIR_REQUIRED, true)
                .putString(CONNECTION_ISSUE, issue)
                .putString(LAST_MESSAGE, text(c, issueMessageRes(issue)))
                .apply();
        RecoveryDiagnostics.event("bridge_repair_required issue=" + issue);
        notifyTile(c);
    }

    private static int issueMessageRes(String issue) {
        if (ISSUE_PAIRING_REVOKED.equals(issue)) return R.string.connection_pairing_revoked;
        if (ISSUE_NETWORK_REQUIRED.equals(issue)) return R.string.connection_network_needed;
        return R.string.connection_repair_required;
    }

    private static int repairMessageRes(Context c) {
        return issueMessageRes(getConnectionIssue(c));
    }

    public static void notifyRepairIfConfirmed(Context context) {
        Context c = context.getApplicationContext();

        if (!isRepairRequired(c)) {
            cancelRepairNotification(c);
            return;
        }

        // Never leave or create a reconnect notification while the local bridge
        // is actually reachable. A successful recovery clears it immediately.
        if (LocalShellClient.ping(c)) {
            cancelRepairNotification(c);
            return;
        }

        if (prefs(c).getBoolean(REPAIR_NOTIFICATION_POSTED, false)) return;

        if (showRepairNotification(c, getConnectionIssue(c))) {
            prefs(c).edit().putBoolean(REPAIR_NOTIFICATION_POSTED, true).apply();
        }
    }

    private static boolean showRepairNotification(Context c, String issue) {
        if (Build.VERSION.SDK_INT >= 33 &&
                c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        final String channelId = "connection_repair_v1";
        NotificationManager nm =
                (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    channelId,
                    text(c, R.string.connection_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                c, 811, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = text(c, R.string.connection_lost_simple);
        Notification notification = new Notification.Builder(c, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(text(c, R.string.connection_repair_title))
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();

        nm.notify(811, notification);
        return true;
    }

    private static void cancelRepairNotification(Context c) {
        NotificationManager nm =
                (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(811);
        prefs(c).edit().putBoolean(REPAIR_NOTIFICATION_POSTED, false).apply();
    }

    private static void saveState(Context c, boolean microphoneBlocked,
                                  boolean cameraBlocked, String msg, boolean pairedOk) {
        prefs(c).edit()
                .putBoolean(HAS_STATE, true)
                .putBoolean(MIC_BLOCKED, microphoneBlocked)
                .putBoolean(CAMERA_BLOCKED, cameraBlocked)
                .putString(LAST_MESSAGE, msg)
                .putBoolean(PAIRED_OK, pairedOk)
                .apply();
    }

    private static void saveStateSilent(Context c, boolean microphoneBlocked,
                                        boolean cameraBlocked, boolean pairedOk) {
        prefs(c).edit()
                .putBoolean(HAS_STATE, true)
                .putBoolean(MIC_BLOCKED, microphoneBlocked)
                .putBoolean(CAMERA_BLOCKED, cameraBlocked)
                .putString(LAST_MESSAGE, "")
                .putBoolean(PAIRED_OK, pairedOk)
                .apply();
    }

    private static Result failure(Context c, int messageRes) {
        return new Result(false, getCachedBlocked(c),
                getCachedMicrophoneBlocked(c), getCachedCameraBlocked(c),
                text(c, messageRes), "");
    }

    private static void deliver(Callback cb, Result result) {
        if (cb == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> cb.onResult(result));
    }

    private static void notifyTile(Context c) {
        // Standard TileService lifecycle: SystemUI binds on shade
        // visibility. While it is listening, SharedPreferences observers
        // redraw state on mutation and health completion. Active-mode
        // requestListeningState would be ignored by Android here.
    }

    private static String runAdbShell(AbsAdbConnectionManager manager,
                                      String destination) throws Exception {
        AtomicReference<AdbStream> streamRef = new AtomicReference<>();

        Future<String> future = ADB_IO.submit(() -> {
            AdbStream stream = manager.openStream(destination);
            streamRef.set(stream);

            InputStream input = stream.openInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            try {
                while (true) {
                    int count = input.read(buffer);
                    if (count > 0) output.write(buffer, 0, count);
                    else break;
                    if (stream.isClosed()) break;
                }
                return output.toString("UTF-8");
            } finally {
                try { stream.close(); } catch (Throwable ignored) {}
            }
        });

        try {
            return future.get(6500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            AdbStream stream = streamRef.get();
            if (stream != null) {
                try { stream.close(); } catch (Throwable ignored) {}
            }
            future.cancel(true);
            throw new Exception("ADB shell command timed out", timeout);
        } catch (java.util.concurrent.ExecutionException execution) {
            Throwable cause = execution.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new Exception("ADB shell command failed", cause);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new Exception("ADB shell command interrupted", interrupted);
        }
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String text(Context c, int resId) {
        return LanguageManager.wrap(c).getString(resId);
    }

    private static final class PrivacyState {
        final boolean valid;
        final boolean microphoneBlocked;
        final boolean cameraBlocked;
        final String raw;

        PrivacyState(boolean valid, boolean microphoneBlocked,
                     boolean cameraBlocked, String raw) {
            this.valid = valid;
            this.microphoneBlocked = microphoneBlocked;
            this.cameraBlocked = cameraBlocked;
            this.raw = raw;
        }
    }
}
