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
    private TextView cameraStatus;
    private TextView microphoneStatus;
    private TextView compatibilityTitle;
    private TextView compatibilityBody;
    private TextView message;
    private Button primaryButton;
    private Button checkButton;
    private Button quickButton;
    private LinearLayout compatibilityCard;
    private LinearLayout setupCard;
    private LinearLayout connectionCard;
    private TextView connectionBody;
    private Button reconnectButton;
    private LinearLayout quickCard;
    private boolean pendingSetup;

    private final SharedPreferences.OnSharedPreferenceChangeListener stateListener =
            (preferences, key) -> {
                if ("has_state".equals(key) ||
                        "mic_blocked".equals(key) ||
                        "camera_blocked".equals(key) ||
                        "last_message".equals(key) ||
                        "bridge_ready".equals(key) ||
                        "repair_required".equals(key) ||
                        "connection_issue".equals(key)) {
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
        TextView protectionLabel = label(getString(R.string.app_descriptor), 13, true, R.color.text_secondary);
        protectionCard.addView(protectionLabel);

        overallStatus = label(getString(R.string.status_unknown), 26, true, R.color.text_primary);
        overallStatus.setPadding(0, dp(6), 0, dp(14));
        protectionCard.addView(overallStatus);

        cameraStatus = statusRow(getString(R.string.camera));
        protectionCard.addView(cameraStatus);
        microphoneStatus = statusRow(getString(R.string.microphone));
        microphoneStatus.setPadding(0, dp(10), 0, 0);
        protectionCard.addView(microphoneStatus);
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
                && !SensorController.isBackgroundRecoveryInFlight()) {
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

    private void refreshUi() {
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
            primaryButton.setEnabled(false);
            checkButton.setEnabled(false);
            quickButton.setEnabled(false);
            setupCard.setVisibility(View.GONE);
            message.setText("");
            return;
        }

        compatibilityCard.setVisibility(View.GONE);

        boolean tileAdded = SensorTileService.isTileAdded(this);
        quickButton.setText(getString(tileAdded ? R.string.quick_added : R.string.quick_button));
        quickButton.setEnabled(!tileAdded);

        boolean ready = hasSecurePermission();
        setupCard.setVisibility(ready ? View.GONE : View.VISIBLE);
        primaryButton.setEnabled(ready);
        checkButton.setEnabled(ready);

        boolean repairRequired = ready
                && SensorController.isRepairRequired(this)
                && !SensorController.isBackgroundRecoveryInFlight();
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

        if (!SensorController.hasKnownState(this)) {
            overallStatus.setText(getString(R.string.status_unknown));
            overallStatus.setTextColor(getColor(R.color.text_primary));
            cameraStatus.setText(getString(R.string.camera) + "  •  " + getString(R.string.status_unknown));
            microphoneStatus.setText(getString(R.string.microphone) + "  •  " + getString(R.string.status_unknown));
            primaryButton.setText(getString(R.string.protect_action));
        } else {
            boolean cam = SensorController.getCachedCameraBlocked(this);
            boolean mic = SensorController.getCachedMicrophoneBlocked(this);
            cameraStatus.setText(getString(R.string.camera) + "  •  " + getString(cam ? R.string.blocked : R.string.available));
            microphoneStatus.setText(getString(R.string.microphone) + "  •  " + getString(mic ? R.string.blocked : R.string.available));

            if (cam && mic) {
                overallStatus.setText(getString(R.string.status_protected));
                overallStatus.setTextColor(getColor(R.color.success));
                primaryButton.setText(getString(R.string.allow_action));
            } else if (!cam && !mic) {
                overallStatus.setText(getString(R.string.status_off));
                overallStatus.setTextColor(getColor(R.color.text_primary));
                primaryButton.setText(getString(R.string.protect_action));
            } else {
                overallStatus.setText(getString(R.string.mixed));
                overallStatus.setTextColor(getColor(R.color.warning));
                primaryButton.setText(getString(R.string.protect_action));
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

        SensorController.toggle(getApplicationContext(), result -> {
            message.setText(result.message);
            refreshUi();
        });
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
        if (SensorController.isPairingVerified(this) && hasSecurePermission()) {
            message.setText(getString(R.string.pair_checking_existing));
            SensorController.refreshConnectionHealth(getApplicationContext(), result -> {
                if (result.success && PairingReceiver.alreadyConnected(this)) {
                    PairingReceiver.clearPairingNotifications(this);
                    message.setText(getString(R.string.pair_not_needed));
                    refreshUi();
                    return;
                }

                // The user explicitly asked to connect/repair. If silent recovery
                // could not restore a healthy connection, start the real pairing flow.
                continueSetupAfterHealthCheck();
            });
            return;
        }

        continueSetupAfterHealthCheck();
    }

    private void repairConnection() {
        if (!hasSecurePermission()) {
            openGuide();
            return;
        }

        message.setText(getString(R.string.pair_checking_existing));
        reconnectButton.setEnabled(false);

        SensorController.refreshConnectionHealth(getApplicationContext(), result -> {
            reconnectButton.setEnabled(true);
            if (result.success && PairingReceiver.alreadyConnected(this)) {
                PairingReceiver.clearPairingNotifications(this);
                message.setText(getString(R.string.connection_ready));
                refreshUi();
                return;
            }

            // Do not leave the user on a dead "Reconnect" button. A failed
            // automatic repair immediately enters the pairing flow.
            continueSetupAfterHealthCheck();
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
                getString(R.string.repair_setup),
                getString(R.string.restore_setup_settings)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startSetup();
                    } else if (which == 1) {
                        SensorController.cleanupNow(getApplicationContext());
                        message.setText(getString(R.string.setup_settings_restored));
                        refreshUi();
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
            return "1.0.0";
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
