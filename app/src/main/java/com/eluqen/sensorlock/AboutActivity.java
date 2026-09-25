package com.eluqen.sensorlock;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutActivity extends Activity {
    private static final String GITHUB_URL = "https://github.com/eluqen/sensor-lock";
    private static final String WEBSITE_EN = "https://eluqen.com";
    private static final String WEBSITE_FA = "https://eluqen.ir";
    private static final String WEBSITE_FR = "https://eluqen.com/fr/";
    private static final String BAZAAR_PACKAGE = "com.farsitel.bazaar";
    private static final String BAZAAR_WEB =
            "https://cafebazaar.ir/app/com.eluqen.sensorlock";
    private static final String PLAY_PACKAGE = "com.android.vending";
    private static final String PLAY_WEB =
            "https://play.google.com/store/apps/details?id=com.eluqen.sensorlock";

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

        root.addView(text(getString(R.string.about_title), 28, true, R.color.text_primary));

        addSection(root, R.string.about_what_title, R.string.about_what_body);
        addSection(root, R.string.about_why_title, R.string.about_why_body);
        addSection(root, R.string.about_how_title, R.string.about_how_body);
        addSection(root, R.string.about_mixed_title, R.string.about_mixed_body);
        addSection(root, R.string.about_setup_title, R.string.about_setup_body);
        addSection(root, R.string.about_compat_title, R.string.about_compat_body);
        addSection(root, R.string.about_permissions_title, R.string.about_permissions_body);
        addSection(root, R.string.about_reboot_title, R.string.about_reboot_body);
        addSection(root, R.string.about_privacy_title, R.string.about_privacy_body);
        addSection(root, R.string.about_limits_title, R.string.about_limits_body);
        addSection(root, R.string.about_source_title, R.string.about_source_body);
        addSection(root, R.string.about_company_title, R.string.about_company_body);

        TextView links = text(getString(R.string.about_links_title), 18, true, R.color.text_primary);
        links.setPadding(dp(4), dp(22), dp(4), dp(2));
        root.addView(links);

        Button github = actionButton(getString(R.string.github_button));
        github.setOnClickListener(v -> openUrl(GITHUB_URL));
        root.addView(github);

        final String language = LanguageManager.getLanguage(this);

        Button website = actionButton(getString(R.string.website_button));
        website.setOnClickListener(v -> openUrl(websiteForLanguage(language)));
        root.addView(website);

        if ("fa".equals(language)) {
            Button storeView = actionButton(getString(R.string.bazaar_view_button));
            storeView.setOnClickListener(v -> openBazaar(
                    Intent.ACTION_VIEW,
                    "bazaar://details?id=" + getPackageName()));
            root.addView(storeView);

            Button storeUpdate = actionButton(getString(R.string.bazaar_update_button));
            storeUpdate.setOnClickListener(v -> openBazaar(
                    Intent.ACTION_VIEW,
                    "bazaar://details/modal?id=" + getPackageName()));
            root.addView(storeUpdate);

            Button storeReview = actionButton(getString(R.string.bazaar_review_button));
            storeReview.setOnClickListener(v -> openBazaar(
                    Intent.ACTION_EDIT,
                    "bazaar://details?id=" + getPackageName()));
            root.addView(storeReview);
        } else {
            Button storeView = actionButton(getString(R.string.play_view_button));
            storeView.setOnClickListener(v -> openGooglePlay());
            root.addView(storeView);

            Button storeUpdate = actionButton(getString(R.string.play_update_button));
            storeUpdate.setOnClickListener(v -> openGooglePlay());
            root.addView(storeUpdate);

            Button storeReview = actionButton(getString(R.string.play_review_button));
            storeReview.setOnClickListener(v -> openGooglePlay());
            root.addView(storeReview);
        }

        Button support = actionButton(getString(R.string.support_button));
        support.setOnClickListener(v -> openSupport());
        root.addView(support);

        TextView email = text(getString(R.string.support_email), 13, true, R.color.accent);
        email.setGravity(Gravity.CENTER);
        email.setPadding(0, dp(18), 0, 0);
        email.setOnClickListener(v -> openSupport());
        root.addView(email);

        TextView version = text(
                getString(R.string.version_format, versionName()) + "  •  " +
                        getString(R.string.publisher_name),
                12, false, R.color.text_secondary);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(8), 0, 0);
        root.addView(version);

        setContentView(scroll);
    }

    private void addSection(LinearLayout root, int titleRes, int bodyRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.surface));
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(lp);

        card.addView(text(getString(titleRes), 17, true, R.color.text_primary));
        TextView body = text(getString(bodyRes), 14, false, R.color.text_secondary);
        body.setPadding(0, dp(6), 0, 0);
        card.addView(body);
        root.addView(card);
    }

    private Button actionButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(getColor(R.color.accent));
        button.setAllCaps(false);
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

    private void openBazaar(String action, String uri) {
        Intent intent = new Intent(action, Uri.parse(uri));
        intent.setPackage(BAZAAR_PACKAGE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            openUrl(BAZAAR_WEB);
        }
    }

    private String websiteForLanguage(String language) {
        if ("fa".equals(language)) return WEBSITE_FA;
        if ("fr".equals(language)) return WEBSITE_FR;
        return WEBSITE_EN;
    }

    private void openGooglePlay() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + getPackageName()));
        intent.setPackage(PLAY_PACKAGE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException unavailable) {
            openUrl(PLAY_WEB);
        }
    }

    private void openSupport() {
        Intent email = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + getString(R.string.support_email)));
        try {
            startActivity(email);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
        }
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

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "1.0.0";
        }
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
