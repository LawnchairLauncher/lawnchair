/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.onehanded;

import static com.android.internal.accessibility.AccessibilityShortcutController.ONE_HANDED_COMPONENT_NAME;

import android.annotation.IntDef;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.wm.shell.R;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * APIs for querying or updating one handed settings.
 */
public final class OneHandedSettingsUtil {
    private static final String TAG = "OneHandedSettingsUtil";
    private static final String ONE_HANDED_MODE_TARGET_NAME =
            ONE_HANDED_COMPONENT_NAME.getShortClassName();

    @IntDef(prefix = {"ONE_HANDED_TIMEOUT_"}, value = {
            ONE_HANDED_TIMEOUT_NEVER,
            ONE_HANDED_TIMEOUT_SHORT_IN_SECONDS,
            ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS,
            ONE_HANDED_TIMEOUT_LONG_IN_SECONDS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OneHandedTimeout {
    }

    /**
     * Never stop one handed automatically
     */
    public static final int ONE_HANDED_TIMEOUT_NEVER = 0;
    /**
     * Auto stop one handed in {@link OneHandedSettingsUtil#ONE_HANDED_TIMEOUT_SHORT_IN_SECONDS}
     */
    public static final int ONE_HANDED_TIMEOUT_SHORT_IN_SECONDS = 4;
    /**
     * Auto stop one handed in {@link OneHandedSettingsUtil#ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS}
     */
    public static final int ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS = 8;
    /**
     * Auto stop one handed in {@link OneHandedSettingsUtil#ONE_HANDED_TIMEOUT_LONG_IN_SECONDS}
     */
    public static final int ONE_HANDED_TIMEOUT_LONG_IN_SECONDS = 12;

    /**
     * Registers one handed preference settings observer
     *
     * @param key       Setting key to monitor in observer
     * @param resolver  ContentResolver of context
     * @param observer  Observer from caller
     * @param newUserId New user id to be registered
     * @return uri key for observing
     */
    @Nullable
    public Uri registerSettingsKeyObserver(String key, ContentResolver resolver,
            ContentObserver observer, int newUserId) {
        Uri uriKey = null;
        uriKey = Settings.Secure.getUriFor(key);
        if (resolver != null && uriKey != null) {
            resolver.registerContentObserver(uriKey, false, observer, newUserId);
        }
        return uriKey;
    }

    /**
     * Unregisters one handed preference settings observer.
     *
     * @param resolver  ContentResolver of context
     * @param observer  preference key change observer
     */
    public void unregisterSettingsKeyObserver(ContentResolver resolver,
            ContentObserver observer) {
        if (resolver != null) {
            resolver.unregisterContentObserver(observer);
        }
    }

    /**
     * Queries one handed enable or disable flag from Settings provider.
     *
     * @return enable or disable one handed mode flag.
     */
    public boolean getSettingsOneHandedModeEnabled(ContentResolver resolver, int userId) {
        return Settings.Secure.getIntForUser(resolver,
                Settings.Secure.ONE_HANDED_MODE_ENABLED, 0 /* Disabled */, userId) == 1;
    }

    /**
     * Sets one handed enable or disable flag from Settings provider.
     *
     * @return true if the value was set, false on database errors
     */
    public boolean setOneHandedModeEnabled(ContentResolver resolver, int enabled, int userId) {
        return Settings.Secure.putIntForUser(resolver,
                Settings.Secure.ONE_HANDED_MODE_ENABLED, enabled, userId);
    }


    /**
     * Queries taps app to exit config from Settings provider.
     *
     * @return enable or disable taps app exit.
     */
    public boolean getSettingsTapsAppToExit(ContentResolver resolver, int userId) {
        return Settings.Secure.getIntForUser(resolver,
                Settings.Secure.TAPS_APP_TO_EXIT, 1, userId) == 1;
    }

    /**
     * Queries timeout value from Settings provider. Default is.
     * {@link OneHandedSettingsUtil#ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS}
     *
     * @return timeout value in seconds.
     */
    public @OneHandedTimeout int getSettingsOneHandedModeTimeout(ContentResolver resolver,
            int userId) {
        return Settings.Secure.getIntForUser(resolver,
                Settings.Secure.ONE_HANDED_MODE_TIMEOUT, ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS,
                userId);
    }

    /**
     * Returns whether swipe bottom to notification gesture enabled or not.
     */
    public boolean getSettingsSwipeToNotificationEnabled(ContentResolver resolver, int userId) {
        return Settings.Secure.getIntForUser(resolver,
                Settings.Secure.SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED, 0, userId) == 1;
    }


    /**
     * Queries tutorial shown counts from Settings provider. Default is 0.
     *
     * @return counts tutorial shown counts.
     */
    public int getTutorialShownCounts(ContentResolver resolver, int userId) {
        return Settings.Secure.getIntForUser(resolver,
                Settings.Secure.ONE_HANDED_TUTORIAL_SHOW_COUNT, 0, userId);
    }

    /**
     * Queries one-handed mode shortcut enabled in settings or not.
     *
     * @return true if user enabled one-handed shortcut in settings, false otherwise.
     */
    public boolean getShortcutEnabled(ContentResolver resolver, int userId) {
        // Checks SOFTWARE_SHORTCUT_KEY
        final String targetsSwKey = Settings.Secure.getStringForUser(resolver,
                Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS, userId);
        if (!TextUtils.isEmpty(targetsSwKey) && targetsSwKey.contains(
                ONE_HANDED_MODE_TARGET_NAME)) {
            return true;
        }

        // Checks HARDWARE_SHORTCUT_KEY
        final String targetsHwKey = Settings.Secure.getStringForUser(resolver,
                Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, userId);
        if (!TextUtils.isEmpty(targetsHwKey) && targetsHwKey.contains(
                ONE_HANDED_MODE_TARGET_NAME)) {
            return true;
        }
        return false;
    }

    /**
     * Sets tutorial shown counts.
     *
     * @return true if the value was set, false on database errors.
     */
    public boolean setTutorialShownCounts(ContentResolver resolver, int shownCounts, int userId) {
        return Settings.Secure.putIntForUser(resolver,
                Settings.Secure.ONE_HANDED_TUTORIAL_SHOW_COUNT, shownCounts, userId);
    }

    /**
     * Sets one handed activated or not to notify state for shortcut.
     *
     * @return true if one handed mode is activated.
     */
    public boolean getOneHandedModeActivated(ContentResolver resolver, int userId) {
        return Settings.Secure.getIntForUser(resolver,
                Settings.Secure.ONE_HANDED_MODE_ACTIVATED, 0, userId) == 1;
    }

    /**
     * Sets one handed activated or not to notify state for shortcut.
     *
     * @return true if the value was set, false on database errors.
     */
    public boolean setOneHandedModeActivated(ContentResolver resolver, int state, int userId) {
        return Settings.Secure.putIntForUser(resolver,
                Settings.Secure.ONE_HANDED_MODE_ACTIVATED, state, userId);
    }

    /**
     * Obtains one-handed mode transition duration from resource config.
     *
     * @return durationMs The duration in milli-seconds
     */
    public int getTransitionDuration(Context context) {
        return context.getResources().getInteger(
                R.integer.config_one_handed_translate_animation_duration);
    }

    /**
     * Obtains one-handed mode offset fraction from resource config.
     *
     * @return fraction The fraction of offset of the whole screen.
     */
    public float getTranslationFraction(Context context) {
        return context.getResources().getFraction(R.fraction.config_one_handed_offset, 1, 1);
    }

    void dump(PrintWriter pw, String prefix, ContentResolver resolver,
            int userId) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        pw.print(innerPrefix + "isOneHandedModeEnable=");
        pw.println(getSettingsOneHandedModeEnabled(resolver, userId));
        pw.print(innerPrefix + "isSwipeToNotificationEnabled=");
        pw.println(getSettingsSwipeToNotificationEnabled(resolver, userId));
        pw.print(innerPrefix + "oneHandedTimeOut=");
        pw.println(getSettingsOneHandedModeTimeout(resolver, userId));
        pw.print(innerPrefix + "tapsAppToExit=");
        pw.println(getSettingsTapsAppToExit(resolver, userId));
        pw.print(innerPrefix + "shortcutActivated=");
        pw.println(getOneHandedModeActivated(resolver, userId));
        pw.print(innerPrefix + "tutorialShownCounts=");
        pw.println(getTutorialShownCounts(resolver, userId));
    }

    public OneHandedSettingsUtil() {
    }
}
