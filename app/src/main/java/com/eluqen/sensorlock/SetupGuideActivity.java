package com.eluqen.sensorlock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SetupGuideActivity extends Activity {
    private Button startButton;
    private TextView connectedStatus;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(getColor(R.color.app_background));
        getWindow().setNavigationBarColor(getColor(R.color.app_background));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        root.setBackgroundColor(getColor(R.color.app_background));
        scroll.addView(root);

        TextView title = text(getString(R.string.guide_title), 28, true, R.color.text_primary);
        root.addView(title);

        TextView intro = text(getString(R.string.guide_intro), 14, false, R.color.text_secondary);
        intro.setPadding(0, dp(8), 0, dp(8));
        root.addView(intro);

        root.addView(stepCard(
                getString(R.string.guide_wifi_title),
                getString(R.string.guide_wifi_body),
                new String[]{
                        getString(R.string.guide_mock_wifi),
                        getString(R.string.guide_mock_wifi_on)},
                1));

        root.addView(stepCard(
                getString(R.string.guide_step1_title),
                getString(R.string.guide_step1_body),
                new String[]{
                        getString(R.string.guide_mock_settings),
                        getString(R.string.guide_mock_about_phone),
                        getString(R.string.guide_mock_software_info),
                        getString(R.string.guide_mock_build_number)},
                3));

        root.addView(stepCard(
                getString(R.string.guide_step2_title),
                getString(R.string.guide_step2_body),
                new String[]{
                        getString(R.string.guide_mock_developer_options),
                        getString(R.string.guide_mock_wireless)},
                1));

        root.addView(stepCard(
                getString(R.string.guide_step3_title),
                getString(R.string.guide_step3_body),
                new String[]{
                        getString(R.string.guide_mock_wireless_on),
                        getString(R.string.guide_mock_pair_device),
                        "123456"},
                1));

        root.addView(stepCard(
                getString(R.string.guide_step4_title),
                getString(R.string.guide_step4_body),
                new String[]{
                        getString(R.string.guide_mock_notification),
                        getString(R.string.pair_input_label),
                        getString(R.string.pair_finding_title)},
                1));

        TextView tip = text("✓  " + getString(R.string.guide_tip), 14, true, R.color.success);
        tip.setPadding(dp(6), dp(14), dp(6), dp(8));
        root.addView(tip);

        connectedStatus = text(
                "✓  " + getString(R.string.pair_not_needed_body),
                14, true, R.color.success);
        connectedStatus.setPadding(dp(6), dp(10), dp(6), dp(2));
        connectedStatus.setVisibility(View.GONE);
        root.addView(connectedStatus);

        startButton = primaryButton(getString(R.string.guide_start_pairing));
        startButton.setOnClickListener(v -> {
            // The initial onCreate status is not authoritative: bridge recovery
            // can finish while the guide stays open. Recheck on every tap.
            if (PairingReceiver.alreadyConnected(this)) {
                refreshConnectionAction();
                return;
            }
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("begin_pairing", true);
            startActivity(intent);
            finish();
        });
        root.addView(startButton);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnectionAction();
    }

    private void refreshConnectionAction() {
        if (startButton == null || connectedStatus == null) return;

        boolean connected = PairingReceiver.alreadyConnected(this);
        connectedStatus.setVisibility(connected ? View.VISIBLE : View.GONE);
        startButton.setText(getString(connected
                ? R.string.pair_not_needed_title
                : SensorController.isPairingVerified(this)
                  ? R.string.guide_check_connection
                  : R.string.guide_start_pairing));
        startButton.setEnabled(!connected);
        startButton.setAlpha(connected ? 0.55f : 1f);
    }

    private View stepCard(String title, String body, String[] rows, int highlightIndex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setColor(getColor(R.color.surface));
        background.setCornerRadius(dp(18));
        card.setBackground(background);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(14), 0, 0);
        card.setLayoutParams(lp);

        card.addView(text(title, 18, true, R.color.text_primary));

        TextView description = text(body, 14, false, R.color.text_secondary);
        description.setPadding(0, dp(6), 0, dp(12));
        card.addView(description);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable screenBg = new GradientDrawable();
        screenBg.setColor(0xffeef2f7);
        screenBg.setCornerRadius(dp(12));
        screen.setBackground(screenBg);

        for (int i = 0; i < rows.length; i++) {
            TextView row = text(rows[i], i == 0 ? 13 : 14,
                    i == highlightIndex, i == highlightIndex ? R.color.accent : R.color.text_primary);
            row.setPadding(dp(8), dp(9), dp(8), dp(9));

            if (i == highlightIndex) {
                GradientDrawable highlight = new GradientDrawable();
                highlight.setColor(0xffffffff);
                highlight.setStroke(dp(2), getColor(R.color.accent));
                highlight.setCornerRadius(dp(10));
                row.setBackground(highlight);
            }
            screen.addView(row);
        }

        card.addView(screen);
        return card;
    }

    private TextView text(String value, int size, boolean bold, int colorRes) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(colorRes));
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(0xffffffff);
        button.setAllCaps(false);
        button.setMinHeight(dp(56));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.accent));
        bg.setCornerRadius(dp(16));
        button.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(getColor(R.color.accent));
        button.setAllCaps(false);
        button.setMinHeight(dp(50));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.surface));
        bg.setStroke(dp(1), getColor(R.color.divider));
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
