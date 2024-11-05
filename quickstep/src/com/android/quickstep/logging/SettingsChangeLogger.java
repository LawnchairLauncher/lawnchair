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

import static com.android.launcher3.LauncherPrefs.THEMED_ICONS;
import static com.android.launcher3.LauncherPrefs.getDevicePrefs;
import static com.android.launcher3.LauncherPrefs.getPrefs;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_SUGGESTIONS_DISABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DOT_DISABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DOT_ENABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_DISABLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_ENABLED;
import static com.android.launcher3.model.DeviceGridState.KEY_WORKSPACE_SIZE;
import static com.android.launcher3.model.PredictionUpdateTask.LAST_PREDICTION_ENABLED;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
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
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.DeviceGridState;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SettingsCache;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Optional;

/**
 * Utility class to log launcher settings changes
 */
public class SettingsChangeLogger implements
        DisplayController.DisplayInfoChangeListener, OnSharedPreferenceChangeListener,
        SafeCloseable {

    /**
     * Singleton instance
     */
    public static MainThreadInitializedObject<SettingsChangeLogger> INSTANCE =
            new MainThreadInitializedObject<>(SettingsChangeLogger::new);

    private static final String TAG = "SettingsChangeLogger";
    private static final String ROOT_TAG = "androidx.preference.PreferenceScreen";
    private static final String BOOLEAN_PREF = "SwitchPreference";

    private final Context mContext;
    private final ArrayMap<String, LoggablePref> mLoggablePrefs;
    private final StatsLogManager mStatsLogManager;

    private NavigationMode mNavMode;
    private StatsLogManager.LauncherEvent mNotificationDotsEvent;
    private StatsLogManager.LauncherEvent mHomeScreenSuggestionEvent;

    private SettingsChangeLogger(Context context) {
        mContext = context;
        mStatsLogManager = StatsLogManager.newInstance(mContext);
        mLoggablePrefs = loadPrefKeys(context);
        DisplayController.INSTANCE.get(context).addChangeListener(this);
        mNavMode = DisplayController.getNavigationMode(context);

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
        StatsLogManager.LauncherEvent mEvent =
                isDotsEnabled ? LAUNCHER_NOTIFICATION_DOT_ENABLED
                        : LAUNCHER_NOTIFICATION_DOT_DISABLED;

        // Log only when the setting is actually changed and not during initialization.
        if (mNotificationDotsEvent != null && mNotificationDotsEvent != mEvent) {
            mStatsLogManager.logger().log(mNotificationDotsEvent);
        }
        mNotificationDotsEvent = mEvent;
    }

    @Override
    public void onDisplayInfoChanged(Context context, Info info, int flags) {
        if ((flags & CHANGE_NAVIGATION_MODE) != 0) {
            mNavMode = info.getNavigationMode();
            mStatsLogManager.logger().log(mNavMode.launcherEvent);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (LAST_PREDICTION_ENABLED.getSharedPrefKey().equals(key)
                || KEY_WORKSPACE_SIZE.equals(key)
                || KEY_THEMED_ICONS.equals(key)
                || mLoggablePrefs.containsKey(key)) {

            mHomeScreenSuggestionEvent = LauncherPrefs.get(mContext).get(LAST_PREDICTION_ENABLED)
                    ? LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED
                    : LAUNCHER_HOME_SCREEN_SUGGESTIONS_DISABLED;

            mStatsLogManager.logger().log(mHomeScreenSuggestionEvent);
        }
    }

    /**
     * Takes snapshot of all eligible launcher settings and log them with the provided instance ID.
     */
    public void logSnapshot(InstanceId snapshotInstanceId) {
        StatsLogger logger = mStatsLogManager.logger().withInstanceId(snapshotInstanceId);

        Optional.ofNullable(mNotificationDotsEvent).ifPresent(logger::log);
        Optional.ofNullable(mNavMode).map(mode -> mode.launcherEvent).ifPresent(logger::log);
        Optional.ofNullable(mHomeScreenSuggestionEvent).ifPresent(logger::log);
        Optional.ofNullable(new DeviceGridState(mContext).getWorkspaceSizeEvent()).ifPresent(
                logger::log);

        SharedPreferences prefs = getPrefs(mContext);
        logger.log(LauncherPrefs.get(mContext).get(THEMED_ICONS)
                ? LAUNCHER_THEMED_ICON_ENABLED
                : LAUNCHER_THEMED_ICON_DISABLED);

        mLoggablePrefs.forEach((key, lp) -> logger.log(() ->
                prefs.getBoolean(key, lp.defaultValue) ? lp.eventIdOn : lp.eventIdOff));
    }

    @Override
    public void close() {
        getPrefs(mContext).unregisterOnSharedPreferenceChangeListener(this);
        getDevicePrefs(mContext).unregisterOnSharedPreferenceChangeListener(this);
    }

    private static class LoggablePref {
        public boolean defaultValue;
        public int eventIdOn;
        public int eventIdOff;
    }
}
