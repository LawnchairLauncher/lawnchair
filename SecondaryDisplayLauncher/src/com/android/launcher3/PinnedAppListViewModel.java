/**
 * Copyright (c) 2018 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.PinnedAppListViewModel.PINNED_APPS_KEY;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A view model that provides a list of activities that were pinned by user to always display on
 * home screen.
 * The pinned activities are stored in {@link SharedPreferences} to keep the sample simple :).
 */
public class PinnedAppListViewModel extends AndroidViewModel {

    final static String PINNED_APPS_KEY = "pinned_apps";

    private final PinnedAppListLiveData mLiveData;

    public PinnedAppListViewModel(Application application) {
        super(application);
        mLiveData = new PinnedAppListLiveData(application);
    }

    public LiveData<List<AppEntry>> getPinnedAppList() {
        return mLiveData;
    }
}

class PinnedAppListLiveData extends LiveData<List<AppEntry>> {

    private final Context mContext;
    private final PackageManager mPackageManager;
    // Store listener reference, so it won't be GC-ed.
    private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener;
    private int mCurrentDataVersion;

    public PinnedAppListLiveData(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();

        final SharedPreferences prefs = context.getSharedPreferences(PINNED_APPS_KEY, 0);
        mChangeListener = (preferences, key) -> {
            loadData();
        };
        prefs.registerOnSharedPreferenceChangeListener(mChangeListener);

        loadData();
    }

    private void loadData() {
        final int loadDataVersion = ++mCurrentDataVersion;

        new AsyncTask<Void, Void, List<AppEntry>>() {
            @Override
            protected List<AppEntry> doInBackground(Void... voids) {
                List<AppEntry> entries = new ArrayList<>();

                final SharedPreferences sp = mContext.getSharedPreferences(PINNED_APPS_KEY, 0);
                final Set<String> pinnedAppsComponents = sp.getStringSet(PINNED_APPS_KEY, null);
                if (pinnedAppsComponents == null) {
                    return null;
                }

                for (String componentString : pinnedAppsComponents) {
                    final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.setComponent(ComponentName.unflattenFromString(componentString));
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                    final List<ResolveInfo> apps = mPackageManager.queryIntentActivities(mainIntent,
                            PackageManager.GET_META_DATA);

                    if (apps != null) {
                        for (ResolveInfo app : apps) {
                            final AppEntry entry = new AppEntry(app, mPackageManager);
                            entries.add(entry);
                        }
                    }
                }

                return entries;
            }

            @Override
            protected void onPostExecute(List<AppEntry> data) {
                if (mCurrentDataVersion == loadDataVersion) {
                    setValue(data);
                }
            }
        }.execute();
    }
}