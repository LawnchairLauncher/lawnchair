/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.shared.recents.model;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.util.LruCache;

import com.android.internal.annotations.GuardedBy;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan.Options;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan.PreloadOptions;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.TaskKeyLruCache.EvictionCallback;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.io.PrintWriter;
import java.util.Map;


/**
 * Recents task loader
 */
public class RecentsTaskLoader {
    private static final String TAG = "RecentsTaskLoader";
    private static final boolean DEBUG = false;

    /** Levels of svelte in increasing severity/austerity. */
    // No svelting.
    public static final int SVELTE_NONE = 0;
    // Limit thumbnail cache to number of visible thumbnails when Recents was loaded, disable
    // caching thumbnails as you scroll.
    public static final int SVELTE_LIMIT_CACHE = 1;
    // Disable the thumbnail cache, load thumbnails asynchronously when the activity loads and
    // evict all thumbnails when hidden.
    public static final int SVELTE_DISABLE_CACHE = 2;
    // Disable all thumbnail loading.
    public static final int SVELTE_DISABLE_LOADING = 3;

    // This activity info LruCache is useful because it can be expensive to retrieve ActivityInfos
    // for many tasks, which we use to get the activity labels and icons.  Unlike the other caches
    // below, this is per-package so we can't invalidate the items in the cache based on the last
    // active time.  Instead, we rely on the PackageMonitor to keep us informed whenever a
    // package in the cache has been updated, so that we may remove it.
    private final LruCache<ComponentName, ActivityInfo> mActivityInfoCache;
    private final TaskKeyLruCache<Drawable> mIconCache;
    private final TaskKeyLruCache<String> mActivityLabelCache;
    private final TaskKeyLruCache<String> mContentDescriptionCache;
    private final TaskResourceLoadQueue mLoadQueue;
    private final IconLoader mIconLoader;
    private final BackgroundTaskLoader mLoader;
    private final HighResThumbnailLoader mHighResThumbnailLoader;
    @GuardedBy("this")
    private final TaskKeyStrongCache<ThumbnailData> mThumbnailCache = new TaskKeyStrongCache<>();
    @GuardedBy("this")
    private final TaskKeyStrongCache<ThumbnailData> mTempCache = new TaskKeyStrongCache<>();
    private final int mMaxThumbnailCacheSize;
    private final int mMaxIconCacheSize;
    private int mNumVisibleTasksLoaded;
    private int mSvelteLevel;

    private int mDefaultTaskBarBackgroundColor;
    private int mDefaultTaskViewBackgroundColor;

    private EvictionCallback mClearActivityInfoOnEviction = new EvictionCallback() {
        @Override
        public void onEntryEvicted(TaskKey key) {
            if (key != null) {
                mActivityInfoCache.remove(key.getComponent());
            }
        }
    };

    public RecentsTaskLoader(Context context, int maxThumbnailCacheSize, int maxIconCacheSize,
            int svelteLevel) {
        mMaxThumbnailCacheSize = maxThumbnailCacheSize;
        mMaxIconCacheSize = maxIconCacheSize;
        mSvelteLevel = svelteLevel;

        // Initialize the proxy, cache and loaders
        int numRecentTasks = ActivityManager.getMaxRecentTasksStatic();
        mHighResThumbnailLoader = new HighResThumbnailLoader(ActivityManagerWrapper.getInstance(),
                Looper.getMainLooper(), ActivityManager.isLowRamDeviceStatic());
        mLoadQueue = new TaskResourceLoadQueue();
        mIconCache = new TaskKeyLruCache<>(mMaxIconCacheSize, mClearActivityInfoOnEviction);
        mActivityLabelCache = new TaskKeyLruCache<>(numRecentTasks, mClearActivityInfoOnEviction);
        mContentDescriptionCache = new TaskKeyLruCache<>(numRecentTasks,
                mClearActivityInfoOnEviction);
        mActivityInfoCache = new LruCache<>(numRecentTasks);

        mIconLoader = createNewIconLoader(context, mIconCache, mActivityInfoCache);
        mLoader = new BackgroundTaskLoader(mLoadQueue, mIconLoader,
                mHighResThumbnailLoader::setTaskLoadQueueIdle);
    }

    protected IconLoader createNewIconLoader(Context context,TaskKeyLruCache<Drawable> iconCache,
            LruCache<ComponentName, ActivityInfo> activityInfoCache) {
        return new IconLoader.DefaultIconLoader(context, iconCache, activityInfoCache);
    }

    /**
     * Sets the default task bar/view colors if none are provided by the app.
     */
    public void setDefaultColors(int defaultTaskBarBackgroundColor,
            int defaultTaskViewBackgroundColor) {
        mDefaultTaskBarBackgroundColor = defaultTaskBarBackgroundColor;
        mDefaultTaskViewBackgroundColor = defaultTaskViewBackgroundColor;
    }

    /** Returns the size of the app icon cache. */
    public int getIconCacheSize() {
        return mMaxIconCacheSize;
    }

    /** Returns the size of the thumbnail cache. */
    public int getThumbnailCacheSize() {
        return mMaxThumbnailCacheSize;
    }

    public HighResThumbnailLoader getHighResThumbnailLoader() {
        return mHighResThumbnailLoader;
    }

    /** Preloads recents tasks using the specified plan to store the output. */
    public synchronized void preloadTasks(RecentsTaskLoadPlan plan, int runningTaskId) {
        preloadTasks(plan, runningTaskId, ActivityManagerWrapper.getInstance().getCurrentUserId());
    }

    /** Preloads recents tasks using the specified plan to store the output. */
    public synchronized void preloadTasks(RecentsTaskLoadPlan plan, int runningTaskId,
            int currentUserId) {
        try {
            Trace.beginSection("preloadPlan");
            plan.preloadPlan(new PreloadOptions(), this, runningTaskId, currentUserId);
        } finally {
            Trace.endSection();
        }
    }

    /** Begins loading the heavy task data according to the specified options. */
    public synchronized void loadTasks(RecentsTaskLoadPlan plan, Options opts) {
        if (opts == null) {
            throw new RuntimeException("Requires load options");
        }
        if (opts.onlyLoadForCache && opts.loadThumbnails) {
            // If we are loading for the cache, we'd like to have the real cache only include the
            // visible thumbnails. However, we also don't want to reload already cached thumbnails.
            // Thus, we copy over the current entries into a second cache, and clear the real cache,
            // such that the real cache only contains visible thumbnails.
            mTempCache.copyEntries(mThumbnailCache);
            mThumbnailCache.evictAll();
        }
        plan.executePlan(opts, this);
        mTempCache.evictAll();
        if (!opts.onlyLoadForCache) {
            mNumVisibleTasksLoaded = opts.numVisibleTasks;
        }
    }

    /**
     * Acquires the task resource data directly from the cache, loading if necessary.
     */
    public void loadTaskData(Task t) {
        Drawable icon = mIconCache.getAndInvalidateIfModified(t.key);
        icon = icon != null ? icon : mIconLoader.getDefaultIcon(t.key.userId);
        mLoadQueue.addTask(t);
        t.notifyTaskDataLoaded(t.thumbnail, icon);
    }

    /** Releases the task resource data back into the pool. */
    public void unloadTaskData(Task t) {
        mLoadQueue.removeTask(t);
        t.notifyTaskDataUnloaded(mIconLoader.getDefaultIcon(t.key.userId));
    }

    /** Completely removes the resource data from the pool. */
    public void deleteTaskData(Task t, boolean notifyTaskDataUnloaded) {
        mLoadQueue.removeTask(t);
        mIconCache.remove(t.key);
        mActivityLabelCache.remove(t.key);
        mContentDescriptionCache.remove(t.key);
        if (notifyTaskDataUnloaded) {
            t.notifyTaskDataUnloaded(mIconLoader.getDefaultIcon(t.key.userId));
        }
    }

    /**
     * Handles signals from the system, trimming memory when requested to prevent us from running
     * out of memory.
     */
    public synchronized void onTrimMemory(int level) {
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                // Stop the loader immediately when the UI is no longer visible
                stopLoader();
                mIconCache.trimToSize(Math.max(mNumVisibleTasksLoaded,
                        mMaxIconCacheSize / 2));
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
                // We are leaving recents, so trim the data a bit
                mIconCache.trimToSize(Math.max(1, mMaxIconCacheSize / 2));
                mActivityInfoCache.trimToSize(Math.max(1,
                        ActivityManager.getMaxRecentTasksStatic() / 2));
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
                // We are going to be low on memory
                mIconCache.trimToSize(Math.max(1, mMaxIconCacheSize / 4));
                mActivityInfoCache.trimToSize(Math.max(1,
                        ActivityManager.getMaxRecentTasksStatic() / 4));
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                // We are low on memory, so release everything
                mIconCache.evictAll();
                mActivityInfoCache.evictAll();
                // The cache is small, only clear the label cache when we are critical
                mActivityLabelCache.evictAll();
                mContentDescriptionCache.evictAll();
                mThumbnailCache.evictAll();
                break;
            default:
                break;
        }
    }

    public void onPackageChanged(String packageName) {
        // Remove all the cached activity infos for this package.  The other caches do not need to
        // be pruned at this time, as the TaskKey expiration checks will flush them next time their
        // cached contents are requested
        Map<ComponentName, ActivityInfo> activityInfoCache = mActivityInfoCache.snapshot();
        for (ComponentName cn : activityInfoCache.keySet()) {
            if (cn.getPackageName().equals(packageName)) {
                if (DEBUG) {
                    Log.d(TAG, "Removing activity info from cache: " + cn);
                }
                mActivityInfoCache.remove(cn);
            }
        }
    }

    /**
     * Returns the cached task label if the task key is not expired, updating the cache if it is.
     */
    String getAndUpdateActivityTitle(TaskKey taskKey, ActivityManager.TaskDescription td) {
        // Return the task description label if it exists
        if (td != null && td.getLabel() != null) {
            return td.getLabel();
        }
        // Return the cached activity label if it exists
        String label = mActivityLabelCache.getAndInvalidateIfModified(taskKey);
        if (label != null) {
            return label;
        }
        // All short paths failed, load the label from the activity info and cache it
        ActivityInfo activityInfo = getAndUpdateActivityInfo(taskKey);
        if (activityInfo != null) {
            label = ActivityManagerWrapper.getInstance().getBadgedActivityLabel(activityInfo,
                    taskKey.userId);
            mActivityLabelCache.put(taskKey, label);
            return label;
        }
        // If the activity info does not exist or fails to load, return an empty label for now,
        // but do not cache it
        return "";
    }

    /**
     * Returns the cached task content description if the task key is not expired, updating the
     * cache if it is.
     */
    String getAndUpdateContentDescription(TaskKey taskKey, ActivityManager.TaskDescription td) {
        // Return the cached content description if it exists
        String label = mContentDescriptionCache.getAndInvalidateIfModified(taskKey);
        if (label != null) {
            return label;
        }

        // All short paths failed, load the label from the activity info and cache it
        ActivityInfo activityInfo = getAndUpdateActivityInfo(taskKey);
        if (activityInfo != null) {
            label = ActivityManagerWrapper.getInstance().getBadgedContentDescription(
                    activityInfo, taskKey.userId, td);
            if (td == null) {
                // Only add to the cache if the task description is null, otherwise, it is possible
                // for the task description to change between calls without the last active time
                // changing (ie. between preloading and Overview starting) which would lead to stale
                // content descriptions
                // TODO: Investigate improving this
                mContentDescriptionCache.put(taskKey, label);
            }
            return label;
        }
        // If the content description does not exist, return an empty label for now, but do not
        // cache it
        return "";
    }

    /**
     * Returns the cached task icon if the task key is not expired, updating the cache if it is.
     */
    Drawable getAndUpdateActivityIcon(TaskKey taskKey, ActivityManager.TaskDescription td,
            boolean loadIfNotCached) {
        return mIconLoader.getAndInvalidateIfModified(taskKey, td, loadIfNotCached);
    }

    /**
     * Returns the cached thumbnail if the task key is not expired, updating the cache if it is.
     */
    synchronized ThumbnailData getAndUpdateThumbnail(TaskKey taskKey, boolean loadIfNotCached,
            boolean storeInCache) {
        ThumbnailData cached = mThumbnailCache.getAndInvalidateIfModified(taskKey);
        if (cached != null) {
            return cached;
        }

        cached = mTempCache.getAndInvalidateIfModified(taskKey);
        if (cached != null) {
            mThumbnailCache.put(taskKey, cached);
            return cached;
        }

        if (loadIfNotCached) {
            if (mSvelteLevel < SVELTE_DISABLE_LOADING) {
                // Load the thumbnail from the system
                ThumbnailData thumbnailData = ActivityManagerWrapper.getInstance().getTaskThumbnail(
                        taskKey.id, true /* reducedResolution */);
                if (thumbnailData.thumbnail != null) {
                    if (storeInCache) {
                        mThumbnailCache.put(taskKey, thumbnailData);
                    }
                    return thumbnailData;
                }
            }
        }

        // We couldn't load any thumbnail
        return null;
    }

    /**
     * Returns the task's primary color if possible, defaulting to the default color if there is
     * no specified primary color.
     */
    int getActivityPrimaryColor(ActivityManager.TaskDescription td) {
        if (td != null && td.getPrimaryColor() != 0) {
            return td.getPrimaryColor();
        }
        return mDefaultTaskBarBackgroundColor;
    }

    /**
     * Returns the task's background color if possible.
     */
    int getActivityBackgroundColor(ActivityManager.TaskDescription td) {
        if (td != null && td.getBackgroundColor() != 0) {
            return td.getBackgroundColor();
        }
        return mDefaultTaskViewBackgroundColor;
    }

    /**
     * Returns the activity info for the given task key, retrieving one from the system if the
     * task key is expired.
     */
    ActivityInfo getAndUpdateActivityInfo(TaskKey taskKey) {
        return mIconLoader.getAndUpdateActivityInfo(taskKey);
    }

    /**
     * Starts loading tasks.
     */
    public void startLoader(Context ctx) {
        mLoader.start(ctx);
    }

    /**
     * Stops the task loader and clears all queued, pending task loads.
     */
    private void stopLoader() {
        mLoader.stop();
        mLoadQueue.clearTasks();
    }

    public synchronized void dump(String prefix, PrintWriter writer) {
        String innerPrefix = prefix + "  ";

        writer.print(prefix); writer.println(TAG);
        writer.print(prefix); writer.println("Icon Cache");
        mIconCache.dump(innerPrefix, writer);
        writer.print(prefix); writer.println("Thumbnail Cache");
        mThumbnailCache.dump(innerPrefix, writer);
        writer.print(prefix); writer.println("Temp Thumbnail Cache");
        mTempCache.dump(innerPrefix, writer);
    }
}
