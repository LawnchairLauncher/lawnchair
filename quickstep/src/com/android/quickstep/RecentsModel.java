/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.util.LruCache;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityManager;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.util.Preconditions;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.IconLoader;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan.PreloadOptions;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.recents.model.TaskKeyLruCache;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Singleton class to load and manage recents model.
 */
@TargetApi(Build.VERSION_CODES.O)
public class RecentsModel extends TaskStackChangeListener {
    // We do not need any synchronization for this variable as its only written on UI thread.
    private static RecentsModel INSTANCE;

    public static RecentsModel getInstance(final Context context) {
        if (INSTANCE == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                INSTANCE = new RecentsModel(context.getApplicationContext());
            } else {
                try {
                    return new MainThreadExecutor().submit(
                            () -> RecentsModel.getInstance(context)).get();
                } catch (InterruptedException|ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return INSTANCE;
    }

    private final SparseArray<Bundle> mCachedAssistData = new SparseArray<>(1);
    private final ArrayList<AssistDataListener> mAssistDataListeners = new ArrayList<>();

    private final Context mContext;
    private final RecentsTaskLoader mRecentsTaskLoader;
    private final MainThreadExecutor mMainThreadExecutor;

    private RecentsTaskLoadPlan mLastLoadPlan;
    private int mLastLoadPlanId;
    private int mTaskChangeId;
    private ISystemUiProxy mSystemUiProxy;
    private boolean mClearAssistCacheOnStackChange = true;
    private final boolean mIsLowRamDevice;
    private boolean mPreloadTasksInBackground;
    private final AccessibilityManager mAccessibilityManager;

    private RecentsModel(Context context) {
        mContext = context;

        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mIsLowRamDevice = activityManager.isLowRamDevice();
        mMainThreadExecutor = new MainThreadExecutor();

        Resources res = context.getResources();
        mRecentsTaskLoader = new RecentsTaskLoader(mContext,
                res.getInteger(R.integer.config_recentsMaxThumbnailCacheSize),
                res.getInteger(R.integer.config_recentsMaxIconCacheSize), 0) {

            @Override
            protected IconLoader createNewIconLoader(Context context,
                    TaskKeyLruCache<Drawable> iconCache,
                    LruCache<ComponentName, ActivityInfo> activityInfoCache) {
                return new NormalizedIconLoader(context, iconCache, activityInfoCache);
            }
        };
        mRecentsTaskLoader.startLoader(mContext);
        ActivityManagerWrapper.getInstance().registerTaskStackListener(this);

        mTaskChangeId = 1;
        loadTasks(-1, null);
        mAccessibilityManager = context.getSystemService(AccessibilityManager.class);
    }

    public RecentsTaskLoader getRecentsTaskLoader() {
        return mRecentsTaskLoader;
    }

    /**
     * Preloads the task plan
     * @param taskId The running task id or -1
     * @param callback The callback to receive the task plan once its complete or null. This is
     *                always called on the UI thread.
     * @return the request id associated with this call.
     */
    public int loadTasks(int taskId, Consumer<RecentsTaskLoadPlan> callback) {
        final int requestId = mTaskChangeId;

        // Fail fast if nothing has changed.
        if (mLastLoadPlanId == mTaskChangeId) {
            if (callback != null) {
                final RecentsTaskLoadPlan plan = mLastLoadPlan;
                mMainThreadExecutor.execute(() -> callback.accept(plan));
            }
            return requestId;
        }

        BackgroundExecutor.get().submit(() -> {
            // Preload the plan
            RecentsTaskLoadPlan loadPlan = new RecentsTaskLoadPlan(mContext);
            PreloadOptions opts = new PreloadOptions();
            opts.loadTitles = mAccessibilityManager.isEnabled();
            loadPlan.preloadPlan(opts, mRecentsTaskLoader, taskId, UserHandle.myUserId());
            // Set the load plan on UI thread
            mMainThreadExecutor.execute(() -> {
                mLastLoadPlan = loadPlan;
                mLastLoadPlanId = requestId;

                if (callback != null) {
                    callback.accept(loadPlan);
                }
            });
        });
        return requestId;
    }

    public void setPreloadTasksInBackground(boolean preloadTasksInBackground) {
        mPreloadTasksInBackground = preloadTasksInBackground && !mIsLowRamDevice;
    }

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
        mTaskChangeId++;
    }

    @Override
    public void onActivityUnpinned() {
        mTaskChangeId++;
    }

    @Override
    public void onTaskStackChanged() {
        mTaskChangeId++;

        Preconditions.assertUIThread();
        if (mClearAssistCacheOnStackChange) {
            mCachedAssistData.clear();
        } else {
            mClearAssistCacheOnStackChange = true;
        }
    }

    @Override
    public void onTaskStackChangedBackground() {
        int userId = UserHandle.myUserId();
        if (!mPreloadTasksInBackground || !checkCurrentOrManagedUserId(userId, mContext)) {
            // TODO: Only register this for the current user
            return;
        }

        // Preload a fixed number of task icons/thumbnails in the background
        ActivityManager.RunningTaskInfo runningTaskInfo =
                ActivityManagerWrapper.getInstance().getRunningTask();
        RecentsTaskLoadPlan plan = new RecentsTaskLoadPlan(mContext);
        RecentsTaskLoadPlan.Options launchOpts = new RecentsTaskLoadPlan.Options();
        launchOpts.runningTaskId = runningTaskInfo != null ? runningTaskInfo.id : -1;
        launchOpts.numVisibleTasks = 2;
        launchOpts.numVisibleTaskThumbnails = 2;
        launchOpts.onlyLoadForCache = true;
        launchOpts.onlyLoadPausedActivities = true;
        launchOpts.loadThumbnails = true;
        PreloadOptions preloadOpts = new PreloadOptions();
        preloadOpts.loadTitles = mAccessibilityManager.isEnabled();
        plan.preloadPlan(preloadOpts, mRecentsTaskLoader, -1, userId);
        mRecentsTaskLoader.loadTasks(plan, launchOpts);
    }

    public boolean isLoadPlanValid(int resultId) {
        return mTaskChangeId == resultId;
    }

    public RecentsTaskLoadPlan getLastLoadPlan() {
        return mLastLoadPlan;
    }

    public void setSystemUiProxy(ISystemUiProxy systemUiProxy) {
        mSystemUiProxy = systemUiProxy;
    }

    public ISystemUiProxy getSystemUiProxy() {
        return mSystemUiProxy;
    }

    public void onStart() {
        mRecentsTaskLoader.startLoader(mContext);
        mRecentsTaskLoader.getHighResThumbnailLoader().setVisible(true);
    }

    public void onTrimMemory(int level) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // We already stop the loader in UI_HIDDEN, so stop the high res loader as well
            mRecentsTaskLoader.getHighResThumbnailLoader().setVisible(false);
        }
        mRecentsTaskLoader.onTrimMemory(level);
    }

    public void onOverviewShown(boolean fromHome, String tag) {
        if (mSystemUiProxy == null) {
            return;
        }
        try {
            mSystemUiProxy.onOverviewShown(fromHome);
        } catch (RemoteException e) {
            Log.w(tag,
                    "Failed to notify SysUI of overview shown from " + (fromHome ? "home" : "app")
                            + ": ", e);
        }
    }

    public void resetAssistCache() {
        mCachedAssistData.clear();
    }

    @WorkerThread
    public void preloadAssistData(int taskId, Bundle data) {
        mMainThreadExecutor.execute(() -> {
            mCachedAssistData.put(taskId, data);
            // We expect a stack change callback after the assist data is set. So ignore the
            // very next stack change callback.
            mClearAssistCacheOnStackChange = false;

            int count = mAssistDataListeners.size();
            for (int i = 0; i < count; i++) {
                mAssistDataListeners.get(i).onAssistDataReceived(taskId);
            }
        });
    }

    public Bundle getAssistData(int taskId) {
        Preconditions.assertUIThread();
        return mCachedAssistData.get(taskId);
    }

    public void addAssistDataListener(AssistDataListener listener) {
        mAssistDataListeners.add(listener);
    }

    public void removeAssistDataListener(AssistDataListener listener) {
        mAssistDataListeners.remove(listener);
    }

    /**
     * Callback for receiving assist data
     */
    public interface AssistDataListener {

        void onAssistDataReceived(int taskId);
    }
}
