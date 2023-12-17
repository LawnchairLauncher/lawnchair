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

import static com.android.launcher3.InvariantDeviceProfile.DeviceType;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE;
import static com.android.launcher3.LauncherPrefs.DB_FILE;
import static com.android.launcher3.LauncherPrefs.DEVICE_TYPE;
import static com.android.launcher3.LauncherPrefs.HOTSEAT_COUNT;
import static com.android.launcher3.LauncherPrefs.WORKSPACE_SIZE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_2;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_3;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_4;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_5;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_GRID_SIZE_6;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext;

import java.util.Locale;
import java.util.Objects;

import app.lawnchair.LawnchairProto;

/**
 * Utility class representing persisted grid properties.
 */
public class DeviceGridState implements Comparable<DeviceGridState> {

    public static final String KEY_WORKSPACE_SIZE = "migration_src_workspace_size";
    public static final String KEY_HOTSEAT_COUNT = "migration_src_hotseat_count";
    public static final String KEY_DEVICE_TYPE = "migration_src_device_type";
    public static final String KEY_DB_FILE = "migration_src_db_file";

    private final String mGridSizeString;
    private final int mNumHotseat;
    private final @DeviceType int mDeviceType;
    private String mDbFile;

    public DeviceGridState(InvariantDeviceProfile idp) {
        mGridSizeString = String.format(Locale.ENGLISH, "%d,%d", idp.numColumns, idp.numRows);
        mNumHotseat = idp.numDatabaseHotseatIcons;
        mDeviceType = idp.deviceType;
        mDbFile = idp.dbFile;
    }

    public DeviceGridState(Context context) {
        LauncherPrefs lp = LauncherPrefs.get(context);
        mGridSizeString = lp.get(WORKSPACE_SIZE);
        mNumHotseat = lp.get(HOTSEAT_COUNT);
        mDeviceType = lp.get(DEVICE_TYPE);
        mDbFile = lp.get(DB_FILE);
    }

    @SuppressLint("WrongConstant")
    public DeviceGridState(LawnchairProto.GridState protoGridState) {
        mGridSizeString = protoGridState.getGridSize();
        mNumHotseat = protoGridState.getHotseatCount();
        mDeviceType = protoGridState.getDeviceType();
    }

    /**
     * Returns the device type for the grid
     */
    public @DeviceType int getDeviceType() {
        return mDeviceType;
    }

    /**
     * Returns the databaseFile for the grid.
     */
    public String getDbFile() {
        return mDbFile;
    }

    /**
     * Returns the number of hotseat icons.
     */
    public int getNumHotseat() {
        return mNumHotseat;
    }

    /**
     * Stores the device state to shared preferences
     */
    public void writeToPrefs(Context context) {
        if (context instanceof SandboxContext) {
            return;
        }
        LauncherPrefs.get(context).put(
                WORKSPACE_SIZE.to(mGridSizeString),
                HOTSEAT_COUNT.to(mNumHotseat),
                DEVICE_TYPE.to(mDeviceType),
                DB_FILE.to(mDbFile));
    }


    public void writeToPrefs(Context context, boolean commit) {
        SharedPreferences.Editor editor = Utilities.getPrefs(context).edit()
                .putString(KEY_WORKSPACE_SIZE, mGridSizeString)
                .putInt(KEY_HOTSEAT_COUNT, mNumHotseat)
                .putInt(KEY_DEVICE_TYPE, mDeviceType);
        if (commit) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    public LawnchairProto.GridState toProtoMessage() {
        return LawnchairProto.GridState.newBuilder()
                .setGridSize(mGridSizeString)
                .setHotseatCount(mNumHotseat)
                .setDeviceType(mDeviceType)
                .build();
    }

    /**
     * Returns the logging event corresponding to the grid state
     */
    public LauncherEvent getWorkspaceSizeEvent() {
        if (!TextUtils.isEmpty(mGridSizeString)) {
            switch (getColumns()) {
                case 6:
                    return LAUNCHER_GRID_SIZE_6;
                case 5:
                    return LAUNCHER_GRID_SIZE_5;
                case 4:
                    return LAUNCHER_GRID_SIZE_4;
                case 3:
                    return LAUNCHER_GRID_SIZE_3;
                case 2:
                    return LAUNCHER_GRID_SIZE_2;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "DeviceGridState{"
                + "mGridSizeString='" + mGridSizeString + '\''
                + ", mNumHotseat=" + mNumHotseat
                + ", mDeviceType=" + mDeviceType
                + ", mDbFile=" + mDbFile
                + '}';
    }

    /**
     * Returns true if the database from another DeviceGridState can be loaded into
     * the current
     * DeviceGridState without migration, or false otherwise.
     */
    public boolean isCompatible(DeviceGridState other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        return Objects.equals(mDbFile, other.mDbFile);
    }

    public Integer getColumns() {
        return Integer.parseInt(String.valueOf(mGridSizeString.charAt(0)));
    }

    public Integer getRows() {
        return Integer.parseInt(String.valueOf(mGridSizeString.charAt(2)));
    }

    @Override
    public int compareTo(DeviceGridState other) {
        Integer size = getColumns() * getRows();
        Integer otherSize = other.getColumns() * other.getRows();

        return size.compareTo(otherSize);
    }

}
