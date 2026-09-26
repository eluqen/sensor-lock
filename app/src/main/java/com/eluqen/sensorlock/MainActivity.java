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
import android.widget.Switch;
import android.widget.Toast;
import android.widget.TextView;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 50;

    private TextView overallStatus;
    private TextView actualGlobalStatus;
    private TextView mainScope;
    private Switch mainCameraSwitch;
    private Switch mainMicrophoneSwitch;
    private TextView cameraStatus;
    private TextView microphoneStatus;
    private TextView compatibilityTitle;
    private TextView compatibilityBody;
    private TextView message;
    private Button primaryButton;
    private Button checkButton;
    private Button quickButton;
    private Button tileModeButton;
    private LinearLayout compatibilityCard;
    private LinearLayout setupCard;
    private LinearLayout connectionCard;
    private TextView connectionBody;
    private Button reconnectButton;
    private LinearLayout quickCard;
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

        // A compact, non-dropdown main-action selector in the top-right of
        // the protection card. It is independent of Quick Settings selection.
        LinearLayout mainHeader = new LinearLayout(this);
        mainHeader.setOrientation(LinearLayout.HORIZONTAL);
        // Keep the selector physically on the right in EN/FA/FR; text inside
        // each group still follows the selected language's text direction.
        mainHeader.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mainHeader.setGravity(Gravity.TOP);
        LinearLayout mainHeaderText = new LinearLayout(this);
        mainHeaderText.setOrientation(LinearLayout.VERTICAL);
        mainHeaderText.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        mainHeaderText.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        mainHeaderText.addView(label(getString(R.string.main_controls_header),
                14, true, R.color.text_primary));
        mainScope = label(getString(R.string.main_controls_subheading),
                12, false, R.color.text_secondary);
        mainScope.setPadding(0, dp(5), dp(5), 0);
        mainHeaderText.addView(mainScope);
        mainHeader.addView(mainHeaderText);

        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.VERTICAL);
        selector.setGravity(Gravity.END);
        mainCameraSwitch = mainSensorSwitch(R.string.camera, TilePreferences.MODE_CAMERA);
        mainMicrophoneSwitch = mainSensorSwitch(R.string.microphone, TilePreferences.MODE_MICROPHONE);
        selector.addView(mainCameraSwitch);
        selector.addView(mainMicrophoneSwitch);
        mainHeader.addView(selector);
        protectionCard.addView(mainHeader);

        overallStatus = label(getString(R.string.status_unknown), 24, true, R.color.text_primary);
        overallStatus.setPadding(0, dp(10), 0, dp(8));
        protectionCard.addView(overallStatus);

        cameraStatus = statusRow(getString(R.string.camera));
        protectionCard.addView(cameraStatus);
        microphoneStatus = statusRow(getString(R.string.microphone));
        microphoneStatus.setPadding(0, dp(10), 0, 0);
        protectionCard.addView(microphoneStatus);
        actualGlobalStatus = label("", 12, false, R.color.text_secondary);
        actualGlobalStatus.setPadding(0, dp(9), 0, 0);
        protectionCard.addView(actualGlobalStatus);
        addCard(root, protectionCard, 14);

        primaryButton = primaryButton(getString(R.string.protect_action));
        primaryButton.setOnClickListener(v -> toggleProtection());
        root.addView(primaryButton);

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
                getString(R.string.version_format, versionName()) + "  •  " + getString(R.string.publisher_name),
                12, false, R.color.text_secondary);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, 0);
        root.addView(footer);

        // Initialize selector state once, before this view is first drawn.
        // Background health/operation UI refreshes must never re-bind switches.
        syncMainSelectionUi();
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

    private Switch mainSensorSwitch(int labelRes, int sensor) {
        Switch control = new Switch(this);
        control.setText(getString(labelRes));
        control.setTextSize(13);
        control.setTextColor(getColor(R.color.text_primary));
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setMinHeight(dp(48));
        control.setPadding(dp(2), 0, 0, 0);
        control.setOnClickListener(v -> {
            int previous = MainControlPreferences.getMode(this);
            boolean requested = control.isChecked();
            int next = MainControlPreferences.nextMode(previous, sensor, requested);
            MainControlPreferences.setMode(this, next);
            if (previous == sensor && !requested) {
                int other = sensor == TilePreferences.MODE_CAMERA
                        ? R.string.microphone : R.string.camera;
                Toast.makeText(this, getString(R.string.main_last_selection_switched,
                        getString(other)), Toast.LENGTH_SHORT).show();
            }
            // Only a genuine user selection change can re-bind the switches.
            // A running action already captured its target mode at tap time;
            // changing this preference only affects the NEXT main action.
            syncMainSelectionUi();
            refreshUi();
        });
        return control;
    }

    private void syncMainSelectionUi() {
        int mode = MainControlPreferences.getMode(this);
        boolean cameraSelected = (mode & TilePreferences.MODE_CAMERA) != 0;
        boolean microphoneSelected = (mode & TilePreferences.MODE_MICROPHONE) != 0;
        // setChecked on an unchanged Switch triggers redundant drawable/state
        // work and can make it look as though pressing Protect reset selection.
        if (mainCameraSwitch.isChecked() != cameraSelected) {
            mainCameraSwitch.setChecked(cameraSelected);
        }
        if (mainMicrophoneSwitch.isChecked() != microphoneSelected) {
            mainMicrophoneSwitch.setChecked(microphoneSelected);
        }
        mainScope.setText(getString(mode == TilePreferences.MODE_BOTH
                ? R.string.main_controls_subheading
                : R.string.main_controls_subheading_single));
        boolean supported = DeviceCompatibility.isSupported(this);
        mainCameraSwitch.setEnabled(supported);
        mainMicrophoneSwitch.setEnabled(supported);
    }

    private String actualOverallText(boolean cameraBlocked, boolean microphoneBlocked) {
        if (cameraBlocked && microphoneBlocked) return getString(R.string.status_protected);
        if (!cameraBlocked && !microphoneBlocked) return getString(R.string.status_off);
        return getString(R.string.mixed);
    }

    private void refreshUi() {
        // Read the selected preference to derive labels and command state.
        // Do NOT write to or redraw the sensor-selection switches here.
        boolean compatible = DeviceCompatibility.isSupported(this);
        if (!compatible) {
            compatibilityCard.setVisibility(View.VISIBLE);
            compatibilityTitle.setText(getString(R.string.unsupported_title));
            compatibilityBody.setText(
                    getString(R.string.unsupported_android_body, DeviceCompatibility.androidVersion()));
            overallStatus.setText(getString(R.string.unsupported_title));
            overallStatus.setTextColor(getColor(R.color.warning));
            cameraStatus.setText(getString(R.string.camera) + "  —");
            microphoneStatus.setText(getString(R.string.microphone) + "  —");
            actualGlobalStatus.setText("");
            primaryButton.setEnabled(false);
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
        primaryButton.setEnabled(ready && !busy);
        checkButton.setEnabled(ready && !busy);

        boolean bridgeCachedReady = SensorController.isBridgeReadyCached(this);
        boolean repairMarked = SensorController.isRepairRequired(this);
        // A routine 10-second check of an already healthy bridge is NOT
        // reconnecting. Only a genuinely unready/repair-marked bridge can
        // enter that UI state.
        boolean recovering = ConnectionDisplayState.isReconnecting(
                SensorController.isBackgroundRecoveryInFlight(),
                bridgeCachedReady, repairMarked);
        boolean repairRequired = ready && repairMarked && !recovering;
        connectionCard.setVisibility(repairRequired ? View.VISIBLE : View.GONE);
        if (repairRequired) {
            String issue = SensorController.getConnectionIssue(this);
            int textRes;
            if (SensorController.ISSUE_PAIRING_REVOKED.equals(issue)) {
                textRes = R.string.connection_pairing_revoked;
            } else if (SensorController.ISSUE_NETWORK_REQUIRED.equals(issue)) {
                textRes = R.string.connection_network_needed;
            } else {
                textRes = R.string.connection_repair_required;
            }
            connectionBody.setText(getString(textRes));
        }

        int mode = MainControlPreferences.getMode(this);
        boolean hasState = SensorController.hasKnownState(this);
        boolean verifiedConnection =
                ConnectionDisplayState.isVerified(ready, bridgeCachedReady, repairMarked);

        if (!hasState) {
            overallStatus.setText(recovering
                    ? getString(R.string.main_reconnecting)
                    : getString(R.string.status_unknown));
            overallStatus.setTextColor(getColor(recovering
                    ? R.color.warning : R.color.text_primary));
            cameraStatus.setText(getString(R.string.camera) + "  •  "
                    + getString(R.string.status_unknown));
            microphoneStatus.setText(getString(R.string.microphone) + "  •  "
                    + getString(R.string.status_unknown));
            actualGlobalStatus.setText(getString(R.string.main_actual_state_unknown));
            primaryButton.setText(getString(mode == TilePreferences.MODE_BOTH
                    ? R.string.main_connect_and_change
                    : R.string.main_connect_and_change_single));
        } else {
            boolean cam = SensorController.getCachedCameraBlocked(this);
            boolean mic = SensorController.getCachedMicrophoneBlocked(this);
            int stateWord = verifiedConnection ? R.string.main_actual_state
                    : R.string.main_last_verified_state;
            actualGlobalStatus.setText(getString(stateWord, actualOverallText(cam, mic)));
            int prefix = verifiedConnection ? R.string.main_current_value
                    : R.string.main_last_verified_value;
            cameraStatus.setText(getString(R.string.camera) + "  •  "
                    + getString(prefix, getString(cam ? R.string.blocked : R.string.available)));
            microphoneStatus.setText(getString(R.string.microphone) + "  •  "
                    + getString(prefix, getString(mic ? R.string.blocked : R.string.available)));

            if (!verifiedConnection) {
                overallStatus.setText(recovering
                        ? getString(R.string.main_reconnecting)
                        : getString(R.string.main_connection_unverified));
                overallStatus.setTextColor(getColor(R.color.warning));
                primaryButton.setText(getString(mode == TilePreferences.MODE_BOTH
                    ? R.string.main_connect_and_change
                    : R.string.main_connect_and_change_single));
            } else {
                boolean allSelectedBlocked =
                        ((mode & TilePreferences.MODE_CAMERA) == 0 || cam)
                        && ((mode & TilePreferences.MODE_MICROPHONE) == 0 || mic);
                boolean anySelectedBlocked =
                        ((mode & TilePreferences.MODE_CAMERA) != 0 && cam)
                        || ((mode & TilePreferences.MODE_MICROPHONE) != 0 && mic);
                if (allSelectedBlocked) {
                    overallStatus.setText(getString(mode == TilePreferences.MODE_BOTH
                            ? R.string.status_protected : R.string.main_selected_protected_single));
                    overallStatus.setTextColor(getColor(R.color.success));
                    primaryButton.setText(getString(mode == TilePreferences.MODE_BOTH
                            ? R.string.allow_action : R.string.main_allow_selected_single));
                } else if (anySelectedBlocked) {
                    // Partial state requires both sensors to be selected:
                    // a single selected sensor has a binary on/off state.
                    overallStatus.setText(getString(R.string.main_selected_partial));
                    overallStatus.setTextColor(getColor(R.color.warning));
                    primaryButton.setText(getString(mode == TilePreferences.MODE_BOTH
                            ? R.string.main_protect_selected
                            : R.string.main_protect_selected_single));
                } else {
                    overallStatus.setText(getString(mode == TilePreferences.MODE_BOTH
                            ? R.string.status_off : R.string.main_selected_available_single));
                    overallStatus.setTextColor(getColor(R.color.text_primary));
                    primaryButton.setText(getString(mode == TilePreferences.MODE_BOTH
                            ? R.string.protect_action : R.string.main_protect_selected_single));
                }
            }
        }

        String last = SensorController.getLastMessage(this);
        message.setText(last == null ? "" : last);
    }

    private void toggleProtection() {
        if (!hasSecurePermission()) {
            message.setText(getString(R.string.setup_required));
            return;
        }

        message.setText(getString(R.string.working));
        primaryButton.setEnabled(false);
        checkButton.setEnabled(false);

        // Capture this main-control mode at tap time. Quick Settings has a
        // separate preference and is never changed by the main switches.
        final int mode = MainControlPreferences.getMode(this);
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

        message.setText(getString(R.string.checking));
        primaryButton.setEnabled(false);
        checkButton.setEnabled(false);

        SensorController.verify(getApplicationContext(), result -> {
            message.setText(result.message);
            refreshUi();
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
            setupCheckInProgress = true;
            message.setText(getString(R.string.pair_checking_existing));
            SensorController.refreshConnectionHealth(getApplicationContext(), result -> {
                setupCheckInProgress = false;
                if (result.success && PairingReceiver.alreadyConnected(this)) {
                    PairingReceiver.clearPairingNotifications(this);
                    message.setText(getString(R.string.connection_ready));
                    refreshUi();
                    return;
                }
                promptManualPairing();
            });
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

    private void repairConnection() {
        if (!hasSecurePermission()) {
            openGuide();
            return;
        }
        if (setupCheckInProgress) return;

        setupCheckInProgress = true;
        message.setText(getString(R.string.pair_checking_existing));
        reconnectButton.setEnabled(false);

        SensorController.refreshConnectionHealth(getApplicationContext(), result -> {
            setupCheckInProgress = false;
            reconnectButton.setEnabled(true);
            if (result.success && PairingReceiver.alreadyConnected(this)) {
                PairingReceiver.clearPairingNotifications(this);
                message.setText(getString(R.string.connection_ready));
                refreshUi();
                return;
            }
            promptManualPairing();
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
        String[] items = {
                getString(R.string.repair_connection),
                getString(R.string.restore_setup_settings)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.more_title))
                .setMessage(getString(R.string.more_body))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        repairConnection();
                    } else {
                        confirmRestoreTemporarySettings();
                    }
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
                    message.setText(getString(R.string.setup_settings_restored));
                    refreshUi();
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
            return "1.1.0";
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

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(58));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.accent));
        bg.setCornerRadius(dp(16));
        button.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(getColor(R.color.accent));
        button.setAllCaps(false);        button.setGravity(Gravity.CENTER);
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
        button.setPadding(dp(14), 0, dp(14), 0);        GradientDrawable bg = new GradientDrawable();
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
