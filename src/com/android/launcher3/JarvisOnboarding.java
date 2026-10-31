/*
 * JARVIS OS first-run welcome and built-in guide.
 *
 * Copyright (C) 2026 JARVIS OS contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.launcher3;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shows the JARVIS OS welcome flow once and provides a concise launcher guide. */
public final class JarvisOnboarding {
    private static final String PREFS = "jarvis_os_onboarding";
    private static final String KEY_WELCOME_COMPLETE = "welcome_complete";

    private static final String[] GUIDE_TITLES = {
            "Home", "Applications", "JARVIS Assistant", "Quick Settings", "Personalization"
    };

    private static final String[] GUIDE_TEXT = {
            "Your Home screen is the starting point for JARVIS OS. Place your favourite apps "
                    + "and widgets here, swipe between pages, and use the dock for apps you "
                    + "open most often. Long-press an empty area to access home-screen options.",
            "Swipe up from the Home screen to open Applications. Scroll to browse installed "
                    + "apps, use search to find one quickly, and touch an app icon to launch it. "
                    + "Long-press an icon to see the actions available for that app.",
            "JARVIS Assistant is the assistant experience included with your JARVIS OS setup. "
                    + "Open its app or shortcut when available. The launcher guide does not "
                    + "grant permissions or enable assistant features automatically.",
            "Open Android Quick Settings by swiping down from the top edge of the screen. "
                    + "Use the tiles provided by Android to control Wi-Fi, Bluetooth, battery "
                    + "features and other device controls. Available tiles depend on your device.",
            "Open launcher Settings to adjust the Home screen, app drawer, icons, gestures, "
                    + "search and other supported options. You can return to these settings "
                    + "whenever you want to change how your launcher looks or behaves."
    };

    private JarvisOnboarding() {}

    /** Displays the welcome screen on the first launcher start only. */
    public static void maybeShowWelcome(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_WELCOME_COMPLETE, false)) {
            return;
        }

        new AlertDialog.Builder(context)
                .setTitle("Welcome to JARVIS OS")
                .setMessage("A smarter home for your Android device. Get familiar with your "
                        + "Home screen, applications, assistant and personalization options.")
                .setPositiveButton("Explore guide", (dialog, which) -> {
                    prefs.edit().putBoolean(KEY_WELCOME_COMPLETE, true).apply();
                    showGuide(context);
                })
                .setNegativeButton("Get started", (dialog, which) ->
                        prefs.edit().putBoolean(KEY_WELCOME_COMPLETE, true).apply())
                .setCancelable(false)
                .show();
    }

    /** Opens the built-in guide. Kept public so launcher help entry points can reuse it. */
    public static void showGuide(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("?  JARVIS OS Guide")
                .setItems(GUIDE_TITLES, (dialog, which) -> showGuideSection(context, which))
                .setNegativeButton("Close", null)
                .show();
    }

    private static void showGuideSection(Context context, int index) {
        if (index < 0 || index >= GUIDE_TEXT.length) {
            return;
        }
        TextView body = new TextView(context);
        body.setText(GUIDE_TEXT[index]);
        body.setTextColor(Color.WHITE);
        body.setTextSize(16);
        body.setLineSpacing(6f, 1f);
        body.setPadding(28, 24, 28, 24);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(GUIDE_TITLES[index])
                .setView(scroll)
                .setNegativeButton("Back to guide", (d, w) -> showGuide(context))
                .setPositiveButton("Done", null)
                .create();
        dialog.show();
    }
}
