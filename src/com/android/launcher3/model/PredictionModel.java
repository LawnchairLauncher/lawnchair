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
package com.android.launcher3.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserHandle;

import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Model helper for app predictions in workspace
 */
public class PredictionModel {
    private static final String CACHED_ITEMS_KEY = "predicted_item_keys";
    private static final int MAX_CACHE_ITEMS = 5;

    private final Context mContext;
    private final SharedPreferences mDevicePrefs;
    private ArrayList<ComponentKey> mCachedComponentKeys;

    public PredictionModel(Context context) {
        mContext = context;
        mDevicePrefs = Utilities.getDevicePrefs(mContext);
    }

    /**
     * Formats and stores a list of component key in device preferences.
     */
    public void cachePredictionComponentKeys(List<ComponentKey> componentKeys) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(componentKeys.size(), MAX_CACHE_ITEMS);
        for (int i = 0; i < count; i++) {
            builder.append(componentKeys.get(i));
            builder.append("\n");
        }
        mDevicePrefs.edit().putString(CACHED_ITEMS_KEY, builder.toString()).apply();
        mCachedComponentKeys = null;
    }

    /**
     * parses and returns ComponentKeys saved by
     * {@link PredictionModel#cachePredictionComponentKeys(List)}
     */
    public List<ComponentKey> getPredictionComponentKeys() {
        if (mCachedComponentKeys == null) {
            mCachedComponentKeys = new ArrayList<>();

            String cachedBlob = mDevicePrefs.getString(CACHED_ITEMS_KEY, "");
            for (String line : cachedBlob.split("\n")) {
                ComponentKey key = ComponentKey.fromString(line);
                if (key != null) {
                    mCachedComponentKeys.add(key);
                }
            }
        }
        return mCachedComponentKeys;
    }

    /**
     * Remove uninstalled applications from model
     */
    public void removePackage(String pkgName, UserHandle user, ArrayList<AppInfo> ids) {
        for (int i = ids.size() - 1; i >= 0; i--) {
            AppInfo info = ids.get(i);
            if (info.user.equals(user) && pkgName.equals(info.componentName.getPackageName())) {
                ids.remove(i);
            }
        }
        cachePredictionComponentKeys(getPredictionComponentKeys().stream()
                .filter(cn -> !(cn.user.equals(user) && cn.componentName.getPackageName().equals(
                        pkgName))).collect(Collectors.toList()));
    }
}
