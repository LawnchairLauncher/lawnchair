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

import static com.android.launcher3.uioverrides.RecentsUiFactory.GO_LOW_RAM_RECENTS_ENABLED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.accessibility.AccessibilityManager;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.util.Preconditions;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.TaskKeyLruCache;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages the caching of task icons and related data.
 * TODO(b/138944598): This class should later be merged into IconCache.
 */
public class TaskIconCache {

    private final Handler mBackgroundHandler;
    private final AccessibilityManager mAccessibilityManager;

    private final NormalizedIconLoader mIconLoader;

    private final TaskKeyLruCache<Drawable> mIconCache;
    private final TaskKeyLruCache<String> mContentDescriptionCache;
    private final LruCache<ComponentName, ActivityInfo> mActivityInfoCache;

    private TaskKeyLruCache.EvictionCallback mClearActivityInfoOnEviction =
            new TaskKeyLruCache.EvictionCallback() {
        @Override
        public void onEntryEvicted(Task.TaskKey key) {
            if (key != null) {
                mActivityInfoCache.remove(key.getComponent());
            }
        }
    };

    public TaskIconCache(Context context, Looper backgroundLooper) {
        mBackgroundHandler = new Handler(backgroundLooper);
        mAccessibilityManager = context.getSystemService(AccessibilityManager.class);

        Resources res = context.getResources();
        int cacheSize = res.getInteger(R.integer.recentsIconCacheSize);
        mIconCache = new TaskKeyLruCache<>(cacheSize, mClearActivityInfoOnEviction);
        mContentDescriptionCache = new TaskKeyLruCache<>(cacheSize, mClearActivityInfoOnEviction);
        mActivityInfoCache = new LruCache<>(cacheSize);
        mIconLoader = new NormalizedIconLoader(context, mIconCache, mActivityInfoCache,
                true /* disableColorExtraction */);
    }

    /**
     * Asynchronously fetches the icon and other task data.
     *
     * @param task The task to fetch the data for
     * @param callback The callback to receive the task after its data has been populated.
     * @return A cancelable handle to the request
     */
    public IconLoadRequest updateIconInBackground(Task task, Consumer<Task> callback) {
        Preconditions.assertUIThread();
        if (task.icon != null) {
            // Nothing to load, the icon is already loaded
            callback.accept(task);
            return null;
        }

        IconLoadRequest request = new IconLoadRequest(mBackgroundHandler) {
            @Override
            public void run() {
                Drawable icon = mIconLoader.getIcon(task);
                String contentDescription = loadContentDescriptionInBackground(task);
                if (isCanceled()) {
                    // We don't call back to the provided callback in this case
                    return;
                }
                MAIN_EXECUTOR.execute(() -> {
                    task.icon = icon;
                    task.titleDescription = contentDescription;
                    callback.accept(task);
                    onEnd();
                });
            }
        };
        Utilities.postAsyncCallback(mBackgroundHandler, request);
        return request;
    }

    public void clear() {
        mIconCache.evictAll();
        mContentDescriptionCache.evictAll();
    }

    /**
     * Loads the content description for the given {@param task}.
     */
    private String loadContentDescriptionInBackground(Task task) {
        // Return the cached content description if it exists
        String label = mContentDescriptionCache.getAndInvalidateIfModified(task.key);
        if (label != null) {
            return label;
        }

        // Skip loading content descriptions if accessibility is disabled unless low RAM recents
        // is enabled.
        if (!GO_LOW_RAM_RECENTS_ENABLED && !mAccessibilityManager.isEnabled()) {
            return "";
        }

        // Skip loading the content description if the activity no longer exists
        ActivityInfo activityInfo = mIconLoader.getAndUpdateActivityInfo(task.key);
        if (activityInfo == null) {
            return "";
        }

        // Load the label otherwise
        label = ActivityManagerWrapper.getInstance().getBadgedContentDescription(activityInfo,
                task.key.userId, task.taskDescription);
        mContentDescriptionCache.put(task.key, label);
        return label;
    }


    void onTaskRemoved(TaskKey taskKey) {
        mIconCache.remove(taskKey);
    }

    void invalidatePackage(String packageName) {
        // TODO(b/138944598): Merge this class into IconCache so we can do this at the base level
        Map<ComponentName, ActivityInfo> activityInfoCache = mActivityInfoCache.snapshot();
        for (ComponentName cn : activityInfoCache.keySet()) {
            if (cn.getPackageName().equals(packageName)) {
                mActivityInfoCache.remove(cn);
            }
        }
    }

    public static abstract class IconLoadRequest extends HandlerRunnable {
        IconLoadRequest(Handler handler) {
            super(handler, null);
        }
    }
}
