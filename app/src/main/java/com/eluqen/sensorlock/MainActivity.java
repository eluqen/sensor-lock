package com.eluqen.sensorlock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 50;

    private TextView overallStatus;
    private Button cameraControlButton;
    private Button microphoneControlButton;
    private TextView compatibilityTitle;
    private TextView compatibilityBody;
    private TextView message;
    private Button checkButton;
    private Button quickButton;
    private Button tileModeButton;
    private LinearLayout compatibilityCard;
    private LinearLayout setupCard;
    private LinearLayout connectionCard;
    private TextView connectionBody;
    private Button reconnectButton;
    private LinearLayout quickCard;
    private boolean repairCheckInProgress;
    private boolean pendingSetup;
    private boolean setupCheckInProgress;

    private final SharedPreferences.OnSharedPreferenceChangeListener stateListener =
            (preferences, key) -> {
                if ("has_state".equals(key) ||
                        "mic_blocked".equals(key) ||
                        "camera_blocked".equals(key) ||
                        "last_message".equals(key) ||
                        "bridge_ready".equals(key) ||
                        "repair_required".equals(key) ||
                        "connection_issue".equals(key) ||
                        "health_check_completed".equals(key)) {
                    runOnUiThread(this::refreshUi);
                }
            };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.wrap(newBase));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(getColor(R.color.app_background));
        getWindow().setNavigationBarColor(getColor(R.color.app_background));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.setBackgroundColor(getColor(R.color.app_background));
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titles.setLayoutParams(titlesLp);

        TextView title = label(getString(R.string.app_name), 30, true, R.color.text_primary);
        titles.addView(title);
        TextView descriptor = label(getString(R.string.app_descriptor), 14, false, R.color.text_secondary);
        descriptor.setPadding(0, dp(2), 0, 0);
        titles.addView(descriptor);
        top.addView(titles);

        Button language = smallButton(languageBadge());
        language.setOnClickListener(v -> showLanguageMenu(language));
        top.addView(language);
        root.addView(top);

        compatibilityCard = card();
        compatibilityCard.setVisibility(View.GONE);
        compatibilityTitle = label("", 17, true, R.color.warning);
        compatibilityBody = label("", 14, false, R.color.text_secondary);
        compatibilityBody.setPadding(0, dp(6), 0, 0);
        compatibilityCard.addView(compatibilityTitle);
        compatibilityCard.addView(compatibilityBody);
        addCard(root, compatibilityCard, 18);

        LinearLayout protectionCard = card();
        TextView controlTitle = label(getString(R.string.main_controls_header),
                18, true, R.color.text_primary);
        controlTitle.setGravity(Gravity.CENTER);
        protectionCard.addView(controlTitle);
        TextView controlHint = label(getString(R.string.main_controls_subheading),
                13, false, R.color.text_secondary);
        controlHint.setGravity(Gravity.CENTER);
        controlHint.setPadding(0, dp(5), 0, dp(12));
        protectionCard.addView(controlHint);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        cameraControlButton = sensorControlButton(R.string.camera,
                TilePreferences.MODE_CAMERA);
        microphoneControlButton = sensorControlButton(R.string.microphone,
                TilePreferences.MODE_MICROPHONE);
        // A real inter-button spacer works in both LTR and RTL. Physical left/right
        // margins moved to the outer edges in Persian, making the buttons touch.
        LinearLayout.LayoutParams cameraLp = new LinearLayout.LayoutParams(0, dp(86), 1f);
        controls.addView(cameraControlButton, cameraLp);
        View sensorGap = new View(this);
        controls.addView(sensorGap, new LinearLayout.LayoutParams(dp(10), dp(1)));
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(0, dp(86), 1f);
        controls.addView(microphoneControlButton, micLp);
        protectionCard.addView(controls);

        overallStatus = label(getString(R.string.status_unknown),
                21, true, R.color.text_primary);
        overallStatus.setGravity(Gravity.CENTER);
        overallStatus.setPadding(0, dp(14), 0, dp(6));
        protectionCard.addView(overallStatus);
        addCard(root, protectionCard, 14);

        checkButton = secondaryButton(getString(R.string.check_protection));
        checkButton.setOnClickListener(v -> verifyProtection());
        root.addView(checkButton);

        message = label("", 13, false, R.color.text_secondary);
        message.setPadding(dp(4), dp(10), dp(4), 0);
        root.addView(message);

        setupCard = card();
        setupCard.addView(label(getString(R.string.setup_title), 18, true, R.color.text_primary));
        TextView setupText = label(getString(R.string.setup_body), 14, false, R.color.text_secondary);
        setupText.setPadding(0, dp(6), 0, dp(8));
        setupCard.addView(setupText);
        Button setupButton = secondaryButton(getString(R.string.setup_button));
        setupButton.setOnClickListener(v -> startSetup());
        setupCard.addView(setupButton);
        addCard(root, setupCard, 18);

        connectionCard = card();
        connectionCard.setVisibility(View.GONE);
        connectionCard.addView(label(getString(R.string.connection_card_title),
                18, true, R.color.warning));
        connectionBody = label("", 14, false, R.color.text_secondary);
        connectionBody.setPadding(0, dp(6), 0, dp(8));
        connectionCard.addView(connectionBody);
        reconnectButton = secondaryButton(getString(R.string.reconnect_button));
        reconnectButton.setOnClickListener(v -> repairConnection());
        connectionCard.addView(reconnectButton);
        addCard(root, connectionCard, 14);

        quickCard = card();
        quickCard.addView(label(getString(R.string.quick_title), 18, true, R.color.text_primary));
        TextView quickText = label(getString(R.string.quick_body), 14, false, R.color.text_secondary);
        quickText.setPadding(0, dp(6), 0, dp(8));
        quickCard.addView(quickText);

        TextView tileControlsLabel = label(
                getString(R.string.quick_controls_label),
                13, true, R.color.text_secondary);
        tileControlsLabel.setPadding(0, dp(8), 0, 0);
        quickCard.addView(tileControlsLabel);

        tileModeButton = secondaryButton(tileModeText());
        tileModeButton.setOnClickListener(v -> showTileModeDialog());
        quickCard.addView(tileModeButton);

        TextView quickModeNote = label(getString(R.string.quick_controls_scope_note),
                12, false, R.color.text_secondary);
        quickModeNote.setPadding(dp(3), dp(3), dp(3), dp(8));
        quickCard.addView(quickModeNote);

        quickButton = secondaryButton(getString(R.string.quick_button));
        quickButton.setOnClickListener(v -> requestTile());
        quickCard.addView(quickButton);
        addCard(root, quickCard, 14);

        Button guideButton = secondaryButton(getString(R.string.guide_button));
        guideButton.setOnClickListener(v ->
                startActivity(new Intent(this, SetupGuideActivity.class)));
        root.addView(guideButton);

        Button aboutButton = secondaryButton(getString(R.string.about_button));
        aboutButton.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        root.addView(aboutButton);

        Button more = smallButton(getString(R.string.more));
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(-2, -2);
        moreLp.gravity = Gravity.CENTER_HORIZONTAL;
        moreLp.setMargins(0, dp(14), 0, 0);
        more.setLayoutParams(moreLp);
        more.setOnClickListener(v -> showMore());
        root.addView(more);

        TextView support = label(getString(R.string.support_email), 13, true, R.color.accent);
        support.setGravity(Gravity.CENTER);
        support.setPadding(0, dp(18), 0, 0);
        support.setOnClickListener(v -> openSupportEmail());
        root.addView(support);

        TextView footer = label(
                getString(R.string.version_format, versionName()) + "  •  "
                        + getString(R.string.publisher_name),
                12, false, R.color.text_secondary);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, 0);
        root.addView(footer);

        setContentView(scroll);
        refreshUi();
        handleIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSharedPreferences("sensor_lock_state", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(stateListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (PairingReceiver.alreadyConnected(this)) {
            PairingReceiver.cancelPending(this);
        }

        refreshUi();

        if (hasSecurePermission()
                && DeviceCompatibility.isSupported(this)
                && !SensorController.isBackgroundRecoveryInFlight()
                && !SensorController.isToggleInFlight()
                && !setupCheckInProgress) {
            SensorController.refreshConnectionHealth(
                    getApplicationContext(),
                    result -> refreshUi());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("begin_pairing", false)) {
            intent.removeExtra("begin_pairing");
            continueSetup();
        }
    }

    @Override
    protected void onStop() {
        getSharedPreferences("sensor_lock_state", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(stateListener);
        super.onStop();
    }

    private Button sensorControlButton(int labelRes, int sensorMode) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(16);
        button.setMinHeight(dp(76));
        button.setOnClickListener(v -> toggleSensor(sensorMode));
        return button;
    }

    private void updateSensorControl(Button button, int nameRes, boolean blocked,
                                     boolean known, boolean verified, boolean enabled) {
        String state = !known ? getString(R.string.status_unknown)
                : verified ? getString(blocked ? R.string.blocked : R.string.available)
                : getString(R.string.sensor_last_known,
                        getString(blocked ? R.string.blocked : R.string.available));
        String name = getString(nameRes);
        button.setText(getString(R.string.sensor_control_label, name, state));
        button.setContentDescription(getString(R.string.sensor_control_accessibility, name,
                state));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(15));
        if (verified && blocked) {
            bg.setColor(getColor(R.color.accent));
            button.setTextColor(Color.WHITE);
        } else {
            bg.setColor(getColor(R.color.surface));
            bg.setStroke(dp(1), getColor(R.color.divider));
            button.setTextColor(getColor(R.color.text_primary));
        }
        button.setBackground(bg);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .55f);
    }

    private void refreshSensorControls(boolean ready, boolean busy, boolean known,
                                       boolean verified) {
        boolean enabled = ready && !busy && DeviceCompatibility.isSupported(this);
        updateSensorControl(cameraControlButton, R.string.camera,
                SensorController.getCachedCameraBlocked(this), known, verified, enabled);
        updateSensorControl(microphoneControlButton, R.string.microphone,
                SensorController.getCachedMicrophoneBlocked(this), known, verified, enabled);
    }

    private int protectionSummaryRes(boolean cameraBlocked, boolean microphoneBlocked) {
        switch (ProtectionSummary.from(cameraBlocked, microphoneBlocked)) {
            case FULL: return R.string.summary_fully_protected;
            case CAMERA_ONLY: return R.string.summary_camera_protected;
            case MICROPHONE_ONLY: return R.string.summary_microphone_protected;
            default: return R.string.status_off;
        }
    }

    private void showInlineError(String value) {
        message.setText(value == null ? "" : value);
        message.setVisibility(value == null || value.isEmpty()
                ? View.GONE : View.VISIBLE);
    }

    private void refreshUi() {
        boolean compatible = DeviceCompatibility.isSupported(this);
        if (!compatible) {
            compatibilityCard.setVisibility(View.VISIBLE);
            compatibilityTitle.setText(getString(R.string.unsupported_title));
            compatibilityBody.setText(getString(R.string.unsupported_android_body,
                    DeviceCompatibility.androidVersion()));
            overallStatus.setText(getString(R.string.unsupported_title));
            overallStatus.setTextColor(getColor(R.color.warning));
            refreshSensorControls(false, false, false, false);
            checkButton.setEnabled(false);
            quickButton.setEnabled(false);
            tileModeButton.setEnabled(false);
            setupCard.setVisibility(View.GONE);
            message.setText("");
            return;
        }

        compatibilityCard.setVisibility(View.GONE);
        boolean tileAdded = SensorTileService.isTileAdded(this);
        quickButton.setText(getString(tileAdded ? R.string.quick_added : R.string.quick_button));
        quickButton.setEnabled(!tileAdded);
        tileModeButton.setText(tileModeText());
        tileModeButton.setEnabled(hasSecurePermission());

        boolean ready = hasSecurePermission();
        boolean busy = SensorController.isToggleInFlight();
        setupCard.setVisibility(ready ? View.GONE : View.VISIBLE);
        checkButton.setEnabled(ready && !busy);
        boolean bridgeCachedReady = SensorController.isBridgeReadyCached(this);
        boolean repairMarked = SensorController.isRepairRequired(this);
        boolean pairingRevoked = SensorController.ISSUE_PAIRING_REVOKED.equals(
                SensorController.getConnectionIssue(this));
        boolean recovering = ConnectionDisplayState.isReconnecting(
                SensorController.isBackgroundRecoveryInFlight(),
                bridgeCachedReady, repairMarked && !pairingRevoked);
        boolean repairRequired = ready && repairMarked && !recovering;
        connectionCard.setVisibility(repairRequired ? View.VISIBLE : View.GONE);
        if (repairRequired) {
            String issue = SensorController.getConnectionIssue(this);
            int textRes = SensorController.ISSUE_PAIRING_REVOKED.equals(issue)
                    ? R.string.connection_pairing_revoked
                    : SensorController.ISSUE_NETWORK_REQUIRED.equals(issue)
                    ? R.string.connection_network_needed
                    : R.string.connection_repair_required;
            connectionBody.setText(getString(textRes));
        }

        boolean known = SensorController.hasKnownState(this);
        boolean verified = ConnectionDisplayState.isSensorStateVerified(
                ready, bridgeCachedReady, repairMarked, pairingRevoked);
        refreshSensorControls(ready, busy, known, verified);
        if (!known) {
            overallStatus.setText(recovering
                    ? getString(R.string.main_reconnecting)
                    : getString(R.string.status_unknown));
            overallStatus.setTextColor(getColor(recovering
                    ? R.color.warning : R.color.text_primary));
            overallStatus.setTextColor(getColor(R.color.warning));
        } else {
            boolean cam = SensorController.getCachedCameraBlocked(this);
            boolean mic = SensorController.getCachedMicrophoneBlocked(this);
            if (!verified) {
                overallStatus.setText(recovering
                        ? getString(R.string.main_reconnecting)
                        : getString(R.string.main_connection_unverified));
                overallStatus.setTextColor(getColor(R.color.warning));
            } else {
                overallStatus.setText(getString(protectionSummaryRes(cam, mic)));
                overallStatus.setTextColor(getColor(cam && mic
                        ? R.color.success : !cam && !mic
                        ? R.color.text_primary : R.color.warning));
            }
        }
        // Healthy protection already appears on the two sensor buttons and summary.
        // Show inline messages only for connection problems, not duplicate success copy.
        String last = SensorController.getLastMessage(this);
        if (!verified && last != null && !last.isEmpty()) {
            showInlineError(last);
        } else {
            showInlineError("");
        }
    }

    private void toggleSensor(int mode) {
        if (!hasSecurePermission()) {
            message.setText(getString(R.string.setup_required));
            return;
        }
        showInlineError(getString(R.string.working));
        cameraControlButton.setEnabled(false);
        microphoneControlButton.setEnabled(false);
        checkButton.setEnabled(false);
        // The single-sensor mode is captured at tap time; Quick Settings is independent.
        boolean accepted = SensorController.toggle(getApplicationContext(), mode, result -> {
            refreshUi();
            if (!result.success) message.setText(result.message);
        });
        if (!accepted) {
            refreshUi();
            message.setText(getString(R.string.operation_in_progress));
        }
    }

    private void verifyProtection() {
        if (!hasSecurePermission()) {
            message.setText(getString(R.string.setup_required));
            return;
        }

        showInlineError(getString(R.string.checking));
        cameraControlButton.setEnabled(false);
        microphoneControlButton.setEnabled(false);
        checkButton.setEnabled(false);

        SensorController.verify(getApplicationContext(), result -> {
            refreshUi();
            if (!result.success) showInlineError(result.message);
        });
    }

    private void startSetup() {
        if (!DeviceCompatibility.isSupported(this)) {
            refreshUi();
            return;
        }
        openGuide();
    }

    private void openGuide() {
        startActivity(new Intent(this, SetupGuideActivity.class));
    }

    private void continueSetup() {
        if (setupCheckInProgress) return;

        // A cached 'not connected' result is only a snapshot: app replacement
        // or boot recovery may be in progress while Help is open.
        boolean everConnected = SensorController.isPairingVerified(this)
                || SensorController.isBridgeReadyCached(this)
                || SensorController.isBackgroundRecoveryInFlight();
        if (hasSecurePermission() && everConnected) {
            repairConnection();
            return;
        }
        continueSetupAfterHealthCheck();
    }

    private void promptManualPairing() {
        // Never surprise the user by opening Developer options on a transient
        // negative result. A manual re-pair is an explicit second decision.
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.guide_repair_prompt_title))
                .setMessage(getString(R.string.guide_repair_prompt_body))
                .setPositiveButton(getString(R.string.guide_repair_open), (dialog, which) ->
                        continueSetupAfterHealthCheck())
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }

    private void showRepairResult(int textRes) {
        if (isFinishing() || isDestroyed()) return;
        refreshUi();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.repair_connection))
                .setMessage(getString(textRes))
                .setPositiveButton(getString(R.string.close), null)
                .show();
    }

    private void endRepairCheck() {
        setupCheckInProgress = false;
        reconnectButton.setEnabled(true);
    }
    private void showInconclusiveRepair() {
        refreshUi();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.repair_connection))
                .setMessage(getString(R.string.pair_verification_inconclusive))
                .setPositiveButton(getString(R.string.guide_repair_open),
                        (dialog, which) -> continueSetupAfterHealthCheck())
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }


    private void repairConnection() {
        if (!hasSecurePermission()) {
            openGuide();
            return;
        }
        if (setupCheckInProgress || SensorController.isToggleInFlight()) return;
        setupCheckInProgress = true;
        showInlineError(getString(R.string.pair_checking_existing));
        reconnectButton.setEnabled(false);

        // Try the actual local bridge and recovery first. This works even if Wi-Fi
        // was turned off after pairing; an ADB TLS probe alone cannot prove service health.
        SensorController.refreshConnectionHealth(getApplicationContext(), result -> {
            if (isFinishing() || isDestroyed()) return;
            boolean pairingRevoked = SensorController.ISSUE_PAIRING_REVOKED.equals(
                    SensorController.getConnectionIssue(this));
            if (result.success && !pairingRevoked && !SensorController.isRepairRequired(this)) {
                endRepairCheck();
                PairingReceiver.clearPairingNotifications(this);
                showRepairResult(R.string.repair_connection_verified);
                return;
            }

            // Once the local service fails, repairing saved ADB authorization may
            // require Wi-Fi. Never present lack of Wi-Fi as proof of revoked pairing.
            if (!ConnectionMonitor.hasWifiTransport(this)) {
                endRepairCheck();
                showRepairResult(result.success
                        ? R.string.repair_pairing_wifi_needed
                        : R.string.repair_wifi_needed);
                return;
            }

            if (pairingRevoked) {
                // This state was already established by the existing connection monitor.
                // Offer manual pairing without pretending the existing local bridge is broken.
                endRepairCheck();
                refreshUi();
                promptManualPairing();
                return;
            }

            PairingValidator.validateAsync(getApplicationContext(), true, outcome -> {
                if (isFinishing() || isDestroyed()) return;
                if (outcome == PairingValidator.Outcome.REVOKED) {
                    endRepairCheck();
                    refreshUi();
                    promptManualPairing();
                    return;
                }
                if (outcome == PairingValidator.Outcome.NO_WIFI) {
                    endRepairCheck();
                    showRepairResult(R.string.repair_wifi_needed);
                    return;
                }
                if (outcome != PairingValidator.Outcome.AUTHENTICATED) {
                    endRepairCheck();
                    // Unknown authentication is not invalid pairing. Manual pairing
                    // remains available as an explicit opt-in, never forced.
                    showInconclusiveRepair();
                    return;
                }

                SensorController.refreshConnectionHealth(getApplicationContext(), retry -> {
                    if (isFinishing() || isDestroyed()) return;
                    endRepairCheck();
                    if (retry.success && !SensorController.isRepairRequired(this)) {
                        PairingReceiver.clearPairingNotifications(this);
                        showRepairResult(R.string.repair_connection_restored);
                    } else {
                        showRepairResult(R.string.pair_verification_bridge_unavailable);
                    }
                });
            });
        });
    }

    private void continueSetupAfterHealthCheck() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingSetup = true;
            message.setText(getString(R.string.notifications_required));
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }

        beginPairing();
    }

    private void beginPairing() {
        try {
            if (PairingReceiver.alreadyConnected(this)) {
                PairingReceiver.cancelPairingNotifications(this);
                message.setText(getString(R.string.pair_not_needed));
                refreshUi();
                return;
            }

            boolean hasSecure = hasSecurePermission();

            if (hasSecure && !SensorController.prepareDebugForPairing(this)) {
                PairingReceiver.cancelPairingNotifications(this);
                message.setText(getString(R.string.operation_failed));
                refreshUi();
                return;
            }

            if (!PairingReceiver.start(this)) {
                SensorController.cleanupNow(getApplicationContext());
                message.setText(getString(R.string.pair_not_needed));
                refreshUi();
                return;
            }

            message.setText(getString(hasSecure
                    ? R.string.setup_open_pairing
                    : R.string.setup_open_pairing_first));

            Intent intent = new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS");
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException unavailable) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            }
        } catch (Throwable t) {
            PairingReceiver.cancelPairingNotifications(this);
            SensorController.cleanupNow(getApplicationContext());
            message.setText(getString(R.string.operation_failed));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_NOTIFICATIONS || !pendingSetup) return;
        pendingSetup = false;

        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            beginPairing();
        } else {
            message.setText(getString(R.string.notifications_denied));
        }
    }

    private String tileModeText() {
        int mode = TilePreferences.getMode(this);
        if (mode == TilePreferences.MODE_CAMERA) {
            return getString(R.string.quick_controls_camera);
        }
        if (mode == TilePreferences.MODE_MICROPHONE) {
            return getString(R.string.quick_controls_microphone);
        }
        return getString(R.string.quick_controls_both);
    }

    private void showTileModeDialog() {
        int current = TilePreferences.getMode(this);
        String[] items = {
                getString(R.string.quick_controls_both),
                getString(R.string.quick_controls_camera),
                getString(R.string.quick_controls_microphone)
        };
        int checked = current == TilePreferences.MODE_CAMERA ? 1
                : current == TilePreferences.MODE_MICROPHONE ? 2 : 0;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.quick_controls_title))
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    int mode = which == 1 ? TilePreferences.MODE_CAMERA
                            : which == 2 ? TilePreferences.MODE_MICROPHONE
                            : TilePreferences.MODE_BOTH;
                    TilePreferences.setMode(this, mode);
                    tileModeButton.setText(tileModeText());
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }

    private void requestTile() {
        if (!DeviceCompatibility.isSupported(this)) return;

        if (Build.VERSION.SDK_INT >= 33) {
            ComponentName component = new ComponentName(this, SensorTileService.class);
            Executor direct = command -> runOnUiThread(command);
            StatusBarManager manager = getSystemService(StatusBarManager.class);

            if (manager != null) {
                manager.requestAddTileService(
                        component,
                        getString(R.string.app_name),
                        Icon.createWithResource(this, R.drawable.ic_tile),
                        direct,
                        result -> {
                            boolean added =
                                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED;
                            if (added) {
                                SensorTileService.setTileAdded(this, true);
                                message.setText(getString(R.string.quick_added));
                            } else {
                                message.setText(getString(R.string.quick_add_failed));
                            }
                            refreshUi();
                        });
                return;
            }
        }

        message.setText(getString(R.string.tile_manual));
    }

    private void showLanguageMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "English");
        menu.getMenu().add(0, 2, 1, "فارسی");
        menu.getMenu().add(0, 3, 2, "Français");
        menu.setOnMenuItemClickListener(item -> {
            String code = item.getItemId() == 2 ? "fa" : item.getItemId() == 3 ? "fr" : "en";
            LanguageManager.setLanguage(this, code);
            recreate();
            return true;
        });
        menu.show();
    }
    private void showMore() {
        boolean temporarySettings = SensorController.hasTemporaryDebugSettings(this);
        String[] items = temporarySettings
                ? new String[]{getString(R.string.repair_connection),
                        getString(R.string.restore_setup_settings)}
                : new String[]{getString(R.string.repair_connection)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.more_title))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) repairConnection();
                    else confirmRestoreTemporarySettings();
                })
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }

    private void confirmRestoreTemporarySettings() {
        if (!SensorController.hasTemporaryDebugSettings(this)) {
            message.setText(getString(R.string.no_temporary_setup_settings));
            refreshUi();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.restore_setup_settings))
                .setMessage(getString(R.string.restore_setup_settings_body))
                .setPositiveButton(getString(R.string.restore_action), (dialog, which) -> {
                    SensorController.cleanupNow(getApplicationContext());
                    if (SensorController.hasTemporaryDebugSettings(this)) {
                        showRepairResult(R.string.operation_failed);
                    } else {
                        showRepairResult(R.string.setup_settings_restored);
                    }
                })
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }

    private void openSupportEmail() {
        Intent email = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + getString(R.string.support_email)));
        try {
            startActivity(email);
        } catch (ActivityNotFoundException ignored) {
            message.setText(getString(R.string.operation_failed));
        }
    }

    private boolean hasSecurePermission() {
        return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }
    private String languageBadge() {
        String language = LanguageManager.getLanguage(this);
        if ("fa".equals(language)) return "FA";
        if ("fr".equals(language)) return "FR";
        return "EN";
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "1.2.0";
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.surface));
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setElevation(dp(2));
        return card;
    }
    private void addCard(LinearLayout root, View card, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(topMargin), 0, 0);
        card.setLayoutParams(lp);
        root.addView(card);
    }

    private TextView statusRow(String name) {
        TextView view = label(name + "  •  " + getString(R.string.status_unknown),
                16, true, R.color.text_primary);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private TextView label(String text, int size, boolean bold, int colorRes) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getColor(colorRes));
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(getColor(R.color.accent));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.surface));
        bg.setStroke(dp(1), getColor(R.color.divider));
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(9), 0, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(getColor(R.color.text_secondary));
        button.setAllCaps(false);
        button.setMinHeight(dp(38));
        button.setMinimumWidth(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.surface));
        bg.setStroke(dp(1), getColor(R.color.divider));
        bg.setCornerRadius(dp(20));
        button.setBackground(bg);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
