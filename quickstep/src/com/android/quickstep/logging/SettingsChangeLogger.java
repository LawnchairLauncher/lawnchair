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

package com.android.quickstep.logging;

import static com.android.launcher3.InvariantDeviceProfile.KEY_MIGRATION_SRC_HOTSEAT_COUNT;
import static com.android.launcher3.Utilities.getDevicePrefs;
import static com.android.launcher3.Utilities.getPrefs;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_2;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_3;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_4;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_5;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_SUGGESTIONS_DISABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DOT_DISABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DOT_ENABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_DISABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_ENABLED;
import static com.android.launcher3.model.QuickstepModelDelegate.LAST_PREDICTION_ENABLED_STATE;
import static com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI;
import static com.android.launcher3.util.Themes.KEY_THEMED_ICONS;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.TypedArray;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Xml;

import com.android.launcher3.AutoInstallsLayout;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.util.SettingsCache;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Utility class to log launcher settings changes
 */
public class SettingsChangeLogger implements
        NavigationModeChangeListener, OnSharedPreferenceChangeListener {

    private static final String TAG = "SettingsChangeLogger";
    private static final String ROOT_TAG = "androidx.preference.PreferenceScreen";
    private static final String BOOLEAN_PREF = "SwitchPreference";

    private final Context mContext;
    private final ArrayMap<String, LoggablePref> mLoggablePrefs;

    private Mode mNavMode;
    private boolean mNotificationDotsEnabled;

    public SettingsChangeLogger(Context context) {
        mContext = context;
        mLoggablePrefs = loadPrefKeys(context);
        mNavMode = SysUINavigationMode.INSTANCE.get(context).addModeChangeListener(this);

        getPrefs(context).registerOnSharedPreferenceChangeListener(this);
        getDevicePrefs(context).registerOnSharedPreferenceChangeListener(this);

        SettingsCache mSettingsCache = SettingsCache.INSTANCE.get(context);
        mSettingsCache.register(NOTIFICATION_BADGING_URI,
                this::onNotificationDotsChanged);
        onNotificationDotsChanged(mSettingsCache.getValue(NOTIFICATION_BADGING_URI));
    }

    private static ArrayMap<String, LoggablePref> loadPrefKeys(Context context) {
        XmlPullParser parser = context.getResources().getXml(R.xml.launcher_preferences);
        ArrayMap<String, LoggablePref> result = new ArrayMap<>();

        try {
            AutoInstallsLayout.beginDocument(parser, ROOT_TAG);
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                if (BOOLEAN_PREF.equals(parser.getName())) {
                    TypedArray a = context.obtainStyledAttributes(
                            Xml.asAttributeSet(parser), R.styleable.LoggablePref);
                    String key = a.getString(R.styleable.LoggablePref_android_key);
                    LoggablePref pref = new LoggablePref();
                    pref.defaultValue =
                            a.getBoolean(R.styleable.LoggablePref_android_defaultValue, true);
                    pref.eventIdOn = a.getInt(R.styleable.LoggablePref_logIdOn, 0);
                    pref.eventIdOff = a.getInt(R.styleable.LoggablePref_logIdOff, 0);
                    if (pref.eventIdOff > 0 && pref.eventIdOn > 0) {
                        result.put(key, pref);
                    }
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing preference xml", e);
        }
        return result;
    }

    private void onNotificationDotsChanged(boolean isDotsEnabled) {
        mNotificationDotsEnabled = isDotsEnabled;
        dispatchUserEvent();
    }

    @Override
    public void onNavigationModeChanged(Mode newMode) {
        mNavMode = newMode;
        dispatchUserEvent();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (LAST_PREDICTION_ENABLED_STATE.equals(key) || KEY_MIGRATION_SRC_HOTSEAT_COUNT.equals(key)
                || mLoggablePrefs.containsKey(key)) {
            dispatchUserEvent();
        }
    }

    private void dispatchUserEvent() {
        StatsLogger logger = StatsLogManager.newInstance(mContext).logger()
                .withInstanceId(new InstanceIdSequence().newInstanceId());

        logger.log(mNotificationDotsEnabled
                ? LAUNCHER_NOTIFICATION_DOT_ENABLED
                : LAUNCHER_NOTIFICATION_DOT_DISABLED);
        logger.log(mNavMode.launcherEvent);
        logger.log(getDevicePrefs(mContext).getBoolean(LAST_PREDICTION_ENABLED_STATE, true)
                ? LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED
                : LAUNCHER_HOME_SCREEN_SUGGESTIONS_DISABLED);

        SharedPreferences prefs = getPrefs(mContext);
        StatsLogManager.LauncherEvent gridSizeChangedEvent = null;
        // TODO(b/184981523): This doesn't work for 2-panel grid, which has 6 hotseat icons
        switch (prefs.getInt(KEY_MIGRATION_SRC_HOTSEAT_COUNT, -1)) {
            case 5:
                gridSizeChangedEvent = LAUNCHER_GRID_SIZE_5;
                break;
            case 4:
                gridSizeChangedEvent = LAUNCHER_GRID_SIZE_4;
                break;
            case 3:
                gridSizeChangedEvent = LAUNCHER_GRID_SIZE_3;
                break;
            case 2:
                gridSizeChangedEvent = LAUNCHER_GRID_SIZE_2;
                break;
            default:
                // Ignore illegal input.
                break;
        }
        if (gridSizeChangedEvent != null) {
            logger.log(gridSizeChangedEvent);
        }

        if (FeatureFlags.ENABLE_THEMED_ICONS.get()) {
            logger.log(prefs.getBoolean(KEY_THEMED_ICONS, false)
                    ? LAUNCHER_THEMED_ICON_ENABLED
                    : LAUNCHER_THEMED_ICON_DISABLED);
        }

        mLoggablePrefs.forEach((key, lp) -> logger.log(() ->
                prefs.getBoolean(key, lp.defaultValue) ? lp.eventIdOn : lp.eventIdOff));
    }

    private static class LoggablePref {
        public boolean defaultValue;
        public int eventIdOn;
        public int eventIdOff;
    }
}
