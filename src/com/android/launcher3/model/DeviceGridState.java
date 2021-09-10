/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.model;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_2;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_3;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_4;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_5;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.util.IntSet;

import java.util.Locale;
import java.util.Objects;

/**
 * Utility class representing persisted grid properties.
 */
public class DeviceGridState {

    public static final String KEY_WORKSPACE_SIZE = "migration_src_workspace_size";
    public static final String KEY_HOTSEAT_COUNT = "migration_src_hotseat_count";
    public static final String KEY_DEVICE_TYPE = "migration_src_device_type";

    public static final int TYPE_PHONE = 0;
    public static final int TYPE_MULTI_DISPLAY = 1;
    public static final int TYPE_TABLET = 2;
    public static final IntSet COMPATIBLE_TYPES = IntSet.wrap(TYPE_PHONE, TYPE_MULTI_DISPLAY);

    private final String mGridSizeString;
    private final int mNumHotseat;
    private final int mDeviceType;

    public DeviceGridState(InvariantDeviceProfile idp) {
        mGridSizeString = String.format(Locale.ENGLISH, "%d,%d", idp.numColumns, idp.numRows);
        mNumHotseat = idp.numDatabaseHotseatIcons;
        mDeviceType = idp.supportedProfiles.size() > 2
                ? TYPE_MULTI_DISPLAY
                : idp.supportedProfiles.stream().allMatch(dp -> dp.isTablet)
                        ? TYPE_TABLET
                        : TYPE_PHONE;
    }

    public DeviceGridState(Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);
        mGridSizeString = prefs.getString(KEY_WORKSPACE_SIZE, "");
        mNumHotseat = prefs.getInt(KEY_HOTSEAT_COUNT, -1);
        mDeviceType = prefs.getInt(KEY_DEVICE_TYPE, TYPE_PHONE);
    }

    /**
     * Returns the device type for the grid
     */
    public int getDeviceType() {
        return mDeviceType;
    }

    /**
     * Stores the device state to shared preferences
     */
    public void writeToPrefs(Context context) {
        Utilities.getPrefs(context).edit()
                .putString(KEY_WORKSPACE_SIZE, mGridSizeString)
                .putInt(KEY_HOTSEAT_COUNT, mNumHotseat)
                .putInt(KEY_DEVICE_TYPE, mDeviceType)
                .apply();
    }

    /**
     * Returns the logging event corresponding to the grid state
     */
    public LauncherEvent getWorkspaceSizeEvent() {
        if (!TextUtils.isEmpty(mGridSizeString)) {
            switch (mGridSizeString.charAt(0)) {
                case '5':
                    return LAUNCHER_GRID_SIZE_5;
                case '4':
                    return LAUNCHER_GRID_SIZE_4;
                case '3':
                    return LAUNCHER_GRID_SIZE_3;
                case '2':
                    return LAUNCHER_GRID_SIZE_2;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceGridState that = (DeviceGridState) o;
        return mNumHotseat == that.mNumHotseat
                && (mDeviceType == that.mDeviceType
                    || (COMPATIBLE_TYPES.contains(mDeviceType)
                        && COMPATIBLE_TYPES.contains(that.mDeviceType)))
                && Objects.equals(mGridSizeString, that.mGridSizeString);
    }
}
