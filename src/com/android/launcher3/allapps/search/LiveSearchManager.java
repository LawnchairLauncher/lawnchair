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
package com.android.launcher3.allapps.search;

import static com.android.launcher3.widget.WidgetHostViewLoader.getDefaultOptionsForWidget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.lifecycle.LiveData;
import androidx.slice.Slice;
import androidx.slice.widget.SliceLiveData;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import java.util.HashMap;

/**
 * Manages Lifecycle for Live search results
 */
public class LiveSearchManager {

    public static final int SEARCH_APPWIDGET_HOST_ID = 2048;

    private final Launcher mLauncher;
    private final AppWidgetManager mAppWidgetManger;
    private final HashMap<ComponentKey, SearchWidgetInfoContainer> mWidgetPlaceholders =
            new HashMap<>();
    private final HashMap<Uri, LiveData<Slice>> mUriSliceMap = new HashMap<>();
    private SearchWidgetHost mSearchWidgetHost;

    public LiveSearchManager(Launcher launcher) {
        mLauncher = launcher;
        mAppWidgetManger = AppWidgetManager.getInstance(launcher);
    }

    /**
     * Creates new {@link AppWidgetHostView} from {@link AppWidgetProviderInfo}. Caches views for
     * quicker result within the same search session
     */
    public SearchWidgetInfoContainer getPlaceHolderWidget(AppWidgetProviderInfo providerInfo) {
        if (mSearchWidgetHost == null) {
            throw new RuntimeException("AppWidgetHost has not been created yet");
        }

        ComponentName provider = providerInfo.provider;
        UserHandle userHandle = providerInfo.getProfile();

        ComponentKey key = new ComponentKey(provider, userHandle);
        SearchWidgetInfoContainer view = mWidgetPlaceholders.getOrDefault(key, null);
        if (mWidgetPlaceholders.containsKey(key)) {
            return mWidgetPlaceholders.get(key);
        }
        LauncherAppWidgetProviderInfo pinfo = LauncherAppWidgetProviderInfo.fromProviderInfo(
                mLauncher, providerInfo);
        PendingAddWidgetInfo pendingAddWidgetInfo = new PendingAddWidgetInfo(pinfo);

        Bundle options = getDefaultOptionsForWidget(mLauncher, pendingAddWidgetInfo);
        int appWidgetId = mSearchWidgetHost.allocateAppWidgetId();
        boolean success = mAppWidgetManger.bindAppWidgetIdIfAllowed(appWidgetId, userHandle,
                provider, options);
        if (!success) {
            mWidgetPlaceholders.put(key, null);
            return null;
        }

        view = (SearchWidgetInfoContainer) mSearchWidgetHost.createView(mLauncher, appWidgetId,
                providerInfo);
        view.setTag(pendingAddWidgetInfo);
        mWidgetPlaceholders.put(key, view);
        return view;
    }

    /**
     * Creates {@link LiveData<Slice>} from Slice Uri. Caches created live data to be reused
     * within the same search session. Removes previous observers when new SliceView request a
     * live data for observation.
     */
    public LiveData<Slice> getSliceForUri(Uri sliceUri) {
        LiveData<Slice> sliceLiveData = mUriSliceMap.getOrDefault(sliceUri, null);
        if (sliceLiveData == null) {
            sliceLiveData = SliceLiveData.fromUri(mLauncher, sliceUri);
            mUriSliceMap.put(sliceUri, sliceLiveData);
        }
        return sliceLiveData;
    }

    /**
     * Start search session
     */
    public void start() {
        stop();
        mSearchWidgetHost = new SearchWidgetHost(mLauncher);
        mSearchWidgetHost.startListening();
    }

    /**
     * Stop search session
     */
    public void stop() {
        if (mSearchWidgetHost != null) {
            mSearchWidgetHost.stopListening();
            mSearchWidgetHost.deleteHost();
            for (SearchWidgetInfoContainer placeholder : mWidgetPlaceholders.values()) {
                placeholder.clearListeners();
            }
            mWidgetPlaceholders.clear();
            mSearchWidgetHost = null;
        }
        for (LiveData<Slice> liveData : mUriSliceMap.values()) {
            liveData.removeObservers(mLauncher);
        }
        mUriSliceMap.clear();
    }

    static class SearchWidgetHost extends AppWidgetHost {
        SearchWidgetHost(Context context) {
            super(context, SEARCH_APPWIDGET_HOST_ID);
        }

        @Override
        protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                AppWidgetProviderInfo appWidget) {
            return new SearchWidgetInfoContainer(context);
        }
    }
}
