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

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserHandle;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.ArrayList;
import java.util.List;

/**
 * Model Helper for app predictions
 */
public class PredictionModel implements ResourceBasedOverride {

    private static final String CACHED_ITEMS_KEY = "predicted_item_keys";
    private static final int MAX_CACHE_ITEMS = 5;

    protected Context mContext;
    private SharedPreferences mDevicePrefs;
    private UserCache mUserCache;


    /**
     * Retrieve instance of this object that can be overridden in runtime based on the build
     * variant of the application.
     */
    public static PredictionModel newInstance(Context context) {
        PredictionModel model = Overrides.getObject(PredictionModel.class, context,
                R.string.prediction_model_class);
        model.init(context);
        return model;
    }

    protected void init(Context context) {
        mContext = context;
        mDevicePrefs = Utilities.getDevicePrefs(mContext);
        mUserCache = UserCache.INSTANCE.get(mContext);

    }

    /**
     * Formats and stores a list of component key in device preferences.
     */
    @AnyThread
    public void cachePredictionComponentKeys(List<ComponentKey> componentKeys) {
        MODEL_EXECUTOR.execute(() -> {
            LauncherAppState appState = LauncherAppState.getInstance(mContext);
            StringBuilder builder = new StringBuilder();
            int count = Math.min(componentKeys.size(), MAX_CACHE_ITEMS);
            for (int i = 0; i < count; i++) {
                builder.append(serializeComponentKeyToString(componentKeys.get(i)));
                builder.append("\n");
            }
            if (componentKeys.isEmpty() /* should invalidate loader items */) {
                appState.getModel().enqueueModelUpdateTask(new BaseModelUpdateTask() {
                    @Override
                    public void execute(LauncherAppState app, BgDataModel model, AllAppsList apps) {
                        model.cachedPredictedItems.clear();
                    }
                });
            }
            mDevicePrefs.edit().putString(CACHED_ITEMS_KEY, builder.toString()).apply();
        });
    }

    /**
     * parses and returns ComponentKeys saved by
     * {@link PredictionModel#cachePredictionComponentKeys(List)}
     */
    @WorkerThread
    public List<ComponentKey> getPredictionComponentKeys() {
        Preconditions.assertWorkerThread();
        ArrayList<ComponentKey> items = new ArrayList<>();
        String cachedBlob = mDevicePrefs.getString(CACHED_ITEMS_KEY, "");
        for (String line : cachedBlob.split("\n")) {
            ComponentKey key = getComponentKeyFromSerializedString(line);
            if (key != null) {
                items.add(key);
            }

        }
        return items;
    }

    private String serializeComponentKeyToString(ComponentKey componentKey) {
        long userSerialNumber = mUserCache.getSerialNumberForUser(componentKey.user);
        return componentKey.componentName.flattenToString() + "#" + userSerialNumber;
    }

    private ComponentKey getComponentKeyFromSerializedString(String str) {
        int sep = str.indexOf('#');
        if (sep < 0 || (sep + 1) >= str.length()) {
            return null;
        }
        ComponentName componentName = ComponentName.unflattenFromString(str.substring(0, sep));
        if (componentName == null) {
            return null;
        }
        try {
            long serialNumber = Long.parseLong(str.substring(sep + 1));
            UserHandle userHandle = mUserCache.getUserForSerialNumber(serialNumber);
            return userHandle != null ? new ComponentKey(componentName, userHandle) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
