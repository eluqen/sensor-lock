package com.eluqen.sensorlock;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SensorTileService extends TileService {
    private static final String UI_PREFS = "ui_preferences";
    private static final String KEY_TILE_ADDED = "quick_tile_added";

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
        setTileAdded(this, true);
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!DeviceCompatibility.isSupported(this) || !hasSecurePermission()) {
            openMainActivity();
            return;
        }

        SensorController.toggle(getApplicationContext(), result -> {
            refreshTile();
            if (!result.success && SensorController.isRepairRequired(this)) {
                openMainActivity();
            }
        });
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

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        Context localized = LanguageManager.wrap(this);
        tile.setLabel(localized.getString(R.string.app_name));

        if (!DeviceCompatibility.isSupported(this)) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(localized.getString(R.string.tile_unsupported));
        } else if (!hasSecurePermission()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.tile_setup_required));
        } else if (!SensorController.hasKnownState(this)) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(localized.getString(R.string.status_unknown));
        } else {
            boolean cam = SensorController.getCachedCameraBlocked(this);
            boolean mic = SensorController.getCachedMicrophoneBlocked(this);

            if (cam && mic) {
                tile.setState(Tile.STATE_ACTIVE);
                tile.setSubtitle(localized.getString(R.string.tile_protected));
            } else if (!cam && !mic) {
                tile.setState(Tile.STATE_INACTIVE);
                tile.setSubtitle(localized.getString(R.string.tile_available));
            } else {
                tile.setState(Tile.STATE_INACTIVE);
                tile.setSubtitle(localized.getString(R.string.tile_mixed));
            }
        }

        tile.updateTile();
    }

    private boolean hasSecurePermission() {
        return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }
}
