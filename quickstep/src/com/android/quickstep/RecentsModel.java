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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Looper;
import android.os.UserHandle;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan.PreloadOptions;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.TaskStackChangeListener;

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

    private final Context mContext;
    private final RecentsTaskLoader mRecentsTaskLoader;
    private final MainThreadExecutor mMainThreadExecutor;

    private RecentsTaskLoadPlan mLastLoadPlan;
    private int mLastLoadPlanId;
    private int mTaskChangeId;
    private ISystemUiProxy mSystemUiProxy;

    private RecentsModel(Context context) {
        mContext = context;

        Resources res = context.getResources();
        mRecentsTaskLoader = new RecentsTaskLoader(mContext,
                res.getInteger(R.integer.config_recentsMaxThumbnailCacheSize),
                res.getInteger(R.integer.config_recentsMaxIconCacheSize), 0);
        mRecentsTaskLoader.startLoader(mContext);

        mMainThreadExecutor = new MainThreadExecutor();
        ActivityManagerWrapper.getInstance().registerTaskStackListener(this);

        mTaskChangeId = 1;
        loadTasks(-1, null);
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
            opts.loadTitles = false;
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

    @Override
    public void onTaskStackChanged() {
        mTaskChangeId++;
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
}
