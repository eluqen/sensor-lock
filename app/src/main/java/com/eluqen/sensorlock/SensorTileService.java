package com.eluqen.sensorlock;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicLong;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SensorTileService extends TileService {
    private static final String UI_PREFS = "ui_preferences";
    private static final String KEY_TILE_ADDED = "quick_tile_added";
    private static final AtomicLong LAST_LISTEN_PROBE_MS = new AtomicLong(0L);
    private static final long MIN_LISTEN_PROBE_INTERVAL_MS = 60_000L;

    private volatile boolean listening;
    private SharedPreferences statePrefs;
    private SharedPreferences tilePrefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener tileStateListener =
            (preferences, key) -> {
                if (!listening) return;
                if (preferences == tilePrefs || "mic_blocked".equals(key)
                        || "camera_blocked".equals(key) || "has_state".equals(key)
                        || "bridge_ready".equals(key) || "repair_required".equals(key)
                        || "connection_issue".equals(key)
                        || "health_check_completed".equals(key)) {
                    mainHandler.post(() -> {
                        if (listening) refreshTile();
                    });
                }
            };

    public static boolean isTileAdded(Context context) {
        return context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_TILE_ADDED, false);
    }

    public static void setTileAdded(Context context, boolean added) {
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TILE_ADDED, added).apply();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        setTileAdded(this, true);
        refreshTile();
    }

    @Override
    public void onTileRemoved() {
        setTileAdded(this, false);
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        RecoveryDiagnostics.event("tile_listening_started");
        setTileAdded(this, true);
        if (!listening) {
            statePrefs = getSharedPreferences("sensor_lock_state", MODE_PRIVATE);
            tilePrefs = getSharedPreferences("tile_preferences", MODE_PRIVATE);
            listening = true;
            statePrefs.registerOnSharedPreferenceChangeListener(tileStateListener);
            tilePrefs.registerOnSharedPreferenceChangeListener(tileStateListener);
        }
        refreshTile();
        // Do not trust pre-reboot cached protection to represent a live bridge.
        // This read/recovery is asynchronous and shares the serial operation
        // executor with tile mutations; opening the shade never blocks UI.
        long now = SystemClock.elapsedRealtime();
        long last = LAST_LISTEN_PROBE_MS.get();
        if (hasSecurePermission() && SensorController.isPairingVerified(this)
                && !SensorController.isToggleInFlight()
                && (last == 0L || now - last >= MIN_LISTEN_PROBE_INTERVAL_MS)
                && LAST_LISTEN_PROBE_MS.compareAndSet(last, now)) {
            boolean started = SensorController.refreshConnectionHealthBackground(
                    getApplicationContext(), result -> {
                        RecoveryDiagnostics.event(result.success
                                ? "tile_visibility_health_ok"
                                : "tile_visibility_health_unavailable");
                        if (listening) refreshTile();
                    });
            if (started) refreshTile();
        }
    }

    @Override
    public void onStopListening() {
        RecoveryDiagnostics.event("tile_listening_stopped");
        unregisterTileObservers();
        super.onStopListening();
    }

    @Override
    public void onDestroy() {
        unregisterTileObservers();
        super.onDestroy();
    }

    private void unregisterTileObservers() {
        if (!listening) return;
        listening = false;
        if (statePrefs != null) {
            statePrefs.unregisterOnSharedPreferenceChangeListener(tileStateListener);
            statePrefs = null;
        }
        if (tilePrefs != null) {
            tilePrefs.unregisterOnSharedPreferenceChangeListener(tileStateListener);
            tilePrefs = null;
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        RecoveryDiagnostics.event("tile_onclick_delivered");

        if (!DeviceCompatibility.isSupported(this) || !hasSecurePermission()) {
            openMainActivity();
            return;
        }

        if (SensorController.isToggleInFlight()) {
            RecoveryDiagnostics.event("tile_tap_ignored_duplicate");
            refreshTile();
            return;
        }

        final int mode = TilePreferences.getMode(this);
        RecoveryDiagnostics.event("tile_tap_accepted mode=" + mode
                + " backgroundRecovery=" + SensorController.isBackgroundRecoveryInFlight());
        refreshTile();

        boolean accepted = SensorController.toggle(
                getApplicationContext(), mode, result -> {
                    RecoveryDiagnostics.event(result.success
                            ? "tile_mutation_verified"
                            : "tile_mutation_failed");
                    // Distinguish an unsupported/old bridge from a real failed
                    // state verification; do not mask every selective failure
                    // behind the misleading "connect to Wi-Fi" subtitle.
                    String needsRefresh = LanguageManager.wrap(this).getString(
                            R.string.selective_control_service_refresh);
                    String otherChanged = LanguageManager.wrap(this).getString(
                            R.string.unselected_sensor_changed);
                    if (!result.success && needsRefresh.equals(result.message)) {
                        showSelectiveRefreshNeeded();
                        return;
                    }
                    if (!result.success && otherChanged.equals(result.message)) {
                        showUnselectedStateWarning();
                        return;
                    }

                    // Never open MainActivity as an unintended consequence of a
                    // Quick Settings tap. On failure display unavailable/reconnect
                    // explicitly; open the app ONLY when the user chooses to.
                    refreshTile();
                });

        if (!accepted) RecoveryDiagnostics.event("tile_mutation_not_queued");
        refreshTile();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 901, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void showSelectiveRefreshNeeded() {
        Tile tile = getQsTile();
        if (tile == null) return;
        Context localized = LanguageManager.wrap(this);
        tile.setState(Tile.STATE_INACTIVE);
        tile.setSubtitle(localized.getString(R.string.tile_service_refresh_needed));
        tile.updateTile();
    }

    private void showUnselectedStateWarning() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.setSubtitle(LanguageManager.wrap(this).getString(
                R.string.unselected_sensor_changed));
        tile.updateTile();
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        Context localized = LanguageManager.wrap(this);
        tile.setLabel(localized.getString(R.string.app_name));

        if (!DeviceCompatibility.isSupported(this)) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(localized.getString(R.string.tile_unsupported));
            tile.updateTile();
            return;
        }

        if (!hasSecurePermission()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_setup_required));
            tile.updateTile();
            return;
        }

        if (SensorController.isToggleInFlight()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_working));
            tile.updateTile();
            return;
        }

        if (SensorController.isBackgroundRecoveryInFlight()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_reconnecting));
            tile.updateTile();
            return;
        }

        if (!SensorController.isBridgeReadyCached(this)
                || SensorController.isRepairRequired(this)) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_connection_unavailable));
            tile.updateTile();
            return;
        }

        if (!SensorController.hasKnownState(this)) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.status_unknown));
            tile.updateTile();
            return;
        }

        boolean cam = SensorController.getCachedCameraBlocked(this);
        boolean mic = SensorController.getCachedMicrophoneBlocked(this);
        int mode = TilePreferences.getMode(this);

        if (mode == TilePreferences.MODE_CAMERA) {
            tile.setState(cam ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(
                    cam ? R.string.tile_camera_protected : R.string.tile_camera_available));
        } else if (mode == TilePreferences.MODE_MICROPHONE) {
            tile.setState(mic ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(
                    mic ? R.string.tile_microphone_protected
                            : R.string.tile_microphone_available));
        } else if (cam && mic) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_protected));
        } else if (!cam && !mic) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_available));
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_mixed));
        }

        tile.updateTile();
    }

    private boolean hasSecurePermission() {
        return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }
}
