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

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.widget.WidgetHostViewLoader.getDefaultOptionsForWidget;

import android.app.Activity;
import android.app.Application.ActivityLifecycleCallbacks;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.Observer;
import androidx.slice.Slice;
import androidx.slice.SliceViewManager;
import androidx.slice.SliceViewManager.SliceCallback;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Manages Lifecycle for Live search results
 */
public class LiveSearchManager implements StateListener<LauncherState> {

    private static final String TAG = "LiveSearchManager";

    public static final int SEARCH_APPWIDGET_HOST_ID = 2048;

    private final Launcher mLauncher;
    private final HashMap<Uri, SliceLifeCycle> mUriSliceMap = new HashMap<>();

    private final HashMap<ComponentKey, SearchWidgetInfoContainer> mWidgetPlaceholders =
            new HashMap<>();
    private SearchWidgetHost mSearchWidgetHost;

    public LiveSearchManager(Launcher launcher) {
        mLauncher = launcher;
        mLauncher.getStateManager().addStateListener(this);
    }

    /**
     * Creates new {@link AppWidgetHostView} from {@link AppWidgetProviderInfo}. Caches views for
     * quicker result within the same search session
     */
    public SearchWidgetInfoContainer getPlaceHolderWidget(AppWidgetProviderInfo providerInfo) {
        if (mSearchWidgetHost == null) {
            mSearchWidgetHost = new SearchWidgetHost(mLauncher);
            mSearchWidgetHost.startListening();
        }

        ComponentName provider = providerInfo.provider;
        UserHandle userHandle = providerInfo.getProfile();

        ComponentKey key = new ComponentKey(provider, userHandle);
        if (mWidgetPlaceholders.containsKey(key)) {
            return mWidgetPlaceholders.get(key);
        }

        LauncherAppWidgetProviderInfo pinfo = LauncherAppWidgetProviderInfo.fromProviderInfo(
                mLauncher, providerInfo);
        PendingAddWidgetInfo pendingAddWidgetInfo = new PendingAddWidgetInfo(pinfo);

        Bundle options = getDefaultOptionsForWidget(mLauncher, pendingAddWidgetInfo);
        int appWidgetId = mSearchWidgetHost.allocateAppWidgetId();
        boolean success = AppWidgetManager.getInstance(mLauncher)
                .bindAppWidgetIdIfAllowed(appWidgetId, userHandle, provider, options);
        if (!success) {
            mSearchWidgetHost.deleteAppWidgetId(appWidgetId);
            mWidgetPlaceholders.put(key, null);
            return null;
        }

        SearchWidgetInfoContainer view = (SearchWidgetInfoContainer) mSearchWidgetHost.createView(
                mLauncher, appWidgetId, providerInfo);
        view.setTag(pendingAddWidgetInfo);
        mWidgetPlaceholders.put(key, view);
        return view;
    }

    /**
     * Stop search session
     */
    public void stop() {
        clearWidgetHost();
    }

    private void clearWidgetHost() {
        if (mSearchWidgetHost != null) {
            mSearchWidgetHost.stopListening();
            mSearchWidgetHost.clearViews();
            mSearchWidgetHost.deleteHost();
            mWidgetPlaceholders.clear();
            mSearchWidgetHost = null;
        }
    }

    @Override
    public void onStateTransitionComplete(LauncherState finalState) {
        if (finalState != ALL_APPS) {
            // Clear all search session related objects
            mUriSliceMap.values().forEach(SliceLifeCycle::destroy);
            mUriSliceMap.clear();

            clearWidgetHost();
        }
    }

    /**
     * Adds a new observer for the provided uri and returns a callback to cancel this observer
     */
    public SafeCloseable addObserver(Uri uri, Observer<Slice> listener) {
        SliceLifeCycle slc = mUriSliceMap.get(uri);
        if (slc == null) {
            slc = new SliceLifeCycle(uri, mLauncher);
            mUriSliceMap.put(uri, slc);
        }
        slc.addListener(listener);

        final SliceLifeCycle sliceLifeCycle = slc;
        return () -> sliceLifeCycle.removeListener(listener);
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

        @Override
        public void clearViews() {
            super.clearViews();
        }
    }

    private static class SliceLifeCycle
            implements ActivityLifecycleCallbacks, SliceCallback {

        private final Uri mUri;
        private final Launcher mLauncher;
        private final SliceViewManager mSliceViewManager;
        private final ArrayList<Observer<Slice>> mListeners = new ArrayList<>();

        private boolean mDestroyed = false;
        private boolean mWasListening = false;

        SliceLifeCycle(Uri uri, Launcher launcher) {
            mUri = uri;
            mLauncher = launcher;
            mSliceViewManager = SliceViewManager.getInstance(launcher);
            launcher.registerActivityLifecycleCallbacks(this);

            if (launcher.isDestroyed()) {
                onActivityDestroyed(launcher);
            } else if (launcher.isStarted()) {
                onActivityStarted(launcher);
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            destroy();
        }

        @Override
        public void onActivityStarted(Activity activity) {
            updateListening();
        }

        @Override
        public void onActivityStopped(Activity activity) {
            updateListening();
        }

        private void updateListening() {
            boolean isListening = mDestroyed
                    ? false
                    : (mLauncher.isStarted() && !mListeners.isEmpty());
            UI_HELPER_EXECUTOR.execute(() -> uploadListeningBg(isListening));
        }

        @WorkerThread
        private void uploadListeningBg(boolean isListening) {
            if (mWasListening != isListening) {
                mWasListening = isListening;
                if (isListening) {
                    mSliceViewManager.registerSliceCallback(mUri, MAIN_EXECUTOR, this);
                    // Update slice one-time on the different thread so that we can display
                    // multiple slices in parallel
                    THREAD_POOL_EXECUTOR.execute(this::updateSlice);
                } else {
                    mSliceViewManager.unregisterSliceCallback(mUri, this);
                }
            }
        }

        @UiThread
        private void addListener(Observer<Slice> listener) {
            mListeners.add(listener);
            updateListening();
        }

        @UiThread
        private void removeListener(Observer<Slice> listener) {
            mListeners.remove(listener);
            updateListening();
        }

        @WorkerThread
        private void updateSlice() {
            try {
                Slice s = mSliceViewManager.bindSlice(mUri);
                MAIN_EXECUTOR.execute(() -> onSliceUpdated(s));
            } catch (Exception e) {
                Log.d(TAG, "Error fetching slice", e);
            }
        }

        @UiThread
        @Override
        public void onSliceUpdated(@Nullable Slice s) {
            mListeners.forEach(l -> l.onChanged(s));
        }

        private void destroy() {
            if (mDestroyed) {
                return;
            }
            mDestroyed = true;
            mLauncher.unregisterActivityLifecycleCallbacks(this);
            mListeners.clear();
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) { }

        @Override
        public void onActivityPaused(Activity activity) { }

        @Override
        public void onActivityResumed(Activity activity) { }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }
    }
}
