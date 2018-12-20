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

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * A view model that provides a list of activities that can be launched.
 */
public class AppListViewModel extends AndroidViewModel {

    private final AppListLiveData mLiveData;
    private final PackageIntentReceiver
            mPackageIntentReceiver;

    public AppListViewModel(Application application) {
        super(application);
        mLiveData = new AppListLiveData(application);
        mPackageIntentReceiver = new PackageIntentReceiver(mLiveData, application);
    }

    public LiveData<List<AppEntry>> getAppList() {
        return mLiveData;
    }

    protected void onCleared() {
        getApplication().unregisterReceiver(mPackageIntentReceiver);
    }
}

class AppListLiveData extends LiveData<List<AppEntry>> {

    private final PackageManager mPackageManager;
    private int mCurrentDataVersion;

    public AppListLiveData(Context context) {
        mPackageManager = context.getPackageManager();
        loadData();
    }

    void loadData() {
        final int loadDataVersion = ++mCurrentDataVersion;

        new AsyncTask<Void, Void, List<AppEntry>>() {
            @Override
            protected List<AppEntry> doInBackground(Void... voids) {
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> apps = mPackageManager.queryIntentActivities(mainIntent,
                        PackageManager.GET_META_DATA);

                List<AppEntry> entries = new ArrayList<>();
                if (apps != null) {
                    for (ResolveInfo app : apps) {
                        AppEntry entry = new AppEntry(app, mPackageManager);
                        entries.add(entry);
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

/**
 * Receiver used to notify live data about app list changes.
 */
class PackageIntentReceiver extends BroadcastReceiver {

    private final AppListLiveData mLiveData;

    public PackageIntentReceiver(AppListLiveData liveData, Context context) {
        mLiveData = liveData;
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        context.registerReceiver(this, filter);

        // Register for events related to sdcard installation.
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        context.registerReceiver(this, sdFilter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mLiveData.loadData();
    }
}