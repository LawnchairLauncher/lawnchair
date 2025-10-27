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

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

import static com.android.launcher3.Flags.enableGridOnlyOverview;
import static com.android.launcher3.Flags.enableRefactorTaskThumbnail;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.graphics.ThemeManager.ThemeChangeListener;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors.SimpleThreadFactory;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.SafeCloseable;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;
import com.android.quickstep.recents.data.RecentTasksDataSource;
import com.android.quickstep.recents.data.TaskVisualsChangeNotifier;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.TaskVisualsChangeListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import dagger.Lazy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.inject.Inject;

import app.lawnchair.LawnchairApp;
import app.lawnchair.compat.LawnchairQuickstepCompat;

/**
 * Singleton class to load and manage recents model.
 */
@TargetApi(Build.VERSION_CODES.O)
@LauncherAppSingleton
public class RecentsModel implements RecentTasksDataSource, TaskStackChangeListener,
        TaskVisualsChangeListener, TaskVisualsChangeNotifier,
        ThemeChangeListener {

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final DaggerSingletonObject<RecentsModel> INSTANCE =
            new DaggerSingletonObject<>(QuickstepBaseAppComponent::getRecentsModel);

    private static final Executor RECENTS_MODEL_EXECUTOR = Executors.newSingleThreadExecutor(
            new SimpleThreadFactory("TaskThumbnailIconCache-", THREAD_PRIORITY_BACKGROUND));

    private final ConcurrentLinkedQueue<TaskVisualsChangeListener> mThumbnailChangeListeners =
            new ConcurrentLinkedQueue<>();
    private final Context mContext;
    private final RecentTasksList mTaskList;
    private final TaskIconCache mIconCache;
    private final TaskThumbnailCache mThumbnailCache;

    @Inject
     public RecentsModel(@ApplicationContext Context context,
            SystemUiProxy systemUiProxy,
            TopTaskTracker topTaskTracker,
            DisplayController displayController,
            LockedUserState lockedUserState,
            Lazy<ThemeManager> themeManagerLazy,
            DesktopVisibilityController desktopVisibilityController,
            DaggerSingletonTracker tracker
            ) {
        // Lazily inject the ThemeManager and access themeManager once the device is
        // unlocked. See b/393248495 for details.
        this(context, new IconProvider(context), systemUiProxy, topTaskTracker,
                displayController, lockedUserState, themeManagerLazy, desktopVisibilityController,
                tracker);
    }

    @SuppressLint("VisibleForTests")
    private RecentsModel(@ApplicationContext Context context,
            IconProvider iconProvider,
            SystemUiProxy systemUiProxy,
            TopTaskTracker topTaskTracker,
            DisplayController displayController,
            LockedUserState lockedUserState,
            Lazy<ThemeManager> themeManagerLazy,
            DesktopVisibilityController desktopVisibilityController,
            DaggerSingletonTracker tracker) {
        this(context,
                new RecentTasksList(
                        context,
                        MAIN_EXECUTOR,
                        context.getSystemService(KeyguardManager.class),
                        systemUiProxy,
                        topTaskTracker, desktopVisibilityController, tracker),
                new TaskIconCache(context, RECENTS_MODEL_EXECUTOR, iconProvider, displayController),
                new TaskThumbnailCache(context, RECENTS_MODEL_EXECUTOR),
                iconProvider,
                TaskStackChangeListeners.getInstance(),
                lockedUserState,
                themeManagerLazy,
                tracker);
    }

    @VisibleForTesting
    RecentsModel(@ApplicationContext Context context,
            RecentTasksList taskList,
            TaskIconCache iconCache,
            TaskThumbnailCache thumbnailCache,
            IconProvider iconProvider,
            TaskStackChangeListeners taskStackChangeListeners,
            LockedUserState lockedUserState,
            Lazy<ThemeManager> themeManagerLazy,
            DaggerSingletonTracker tracker) {
        mContext = context;
        mTaskList = taskList;
        mIconCache = iconCache;
        mIconCache.registerTaskVisualsChangeListener(this);
        mThumbnailCache = thumbnailCache;
        if (isCachePreloadingEnabled()) {
            ComponentCallbacks componentCallbacks = new ComponentCallbacks() {
                @Override
                public void onConfigurationChanged(Configuration configuration) {
                    updateCacheSizeAndPreloadIfNeeded();
                }

                @Override
                public void onLowMemory() {
                }
            };
            context.registerComponentCallbacks(componentCallbacks);
            tracker.addCloseable(() -> context.unregisterComponentCallbacks(componentCallbacks));
        }

        if (LawnchairApp.isRecentsEnabled()) {
            TaskStackChangeListeners.getInstance().registerTaskStackListener(this);
        }
        SafeCloseable iconChangeCloseable = iconProvider.registerIconChangeListener(
                this::onAppIconChanged, MAIN_EXECUTOR.getHandler());

        Runnable unlockCallback = () -> themeManagerLazy.get().addChangeListener(this);
        lockedUserState.runOnUserUnlocked(unlockCallback);

        // Lawnchair-TODO-Merge-High: taskStackChangeListeners run when isRecentsEnabled
        tracker.addCloseable(() -> {
            if (LawnchairApp.isRecentsEnabled()) {
                TaskStackChangeListeners.getInstance().unregisterTaskStackListener(this);
            }
            iconChangeCloseable.close();
            mIconCache.removeTaskVisualsChangeListener();
            if (lockedUserState.isUserUnlocked()) {
                themeManagerLazy.get().removeChangeListener(this);
            } else {
                lockedUserState.removeOnUserUnlockedRunnable(unlockCallback);
            }
        });
    }

    public TaskIconCache getIconCache() {
        return mIconCache;
    }

    public TaskThumbnailCache getThumbnailCache() {
        return mThumbnailCache;
    }

    /**
     * Fetches the list of recent tasks. Tasks are ordered by recency, with the latest active tasks
     * at the end of the list. Filters out desktop tasks that contain no non-minimized tasks.
     *
     * @param callback The callback to receive the task plan once its complete or null. This is
     *                always called on the UI thread.
     * @return the request id associated with this call.
     */
    @Override
    public int getTasks(@Nullable Consumer<List<GroupTask>> callback) {
        return mTaskList.getTasks(false /* loadKeysOnly */, callback,
                RecentsFilterState.getDesktopTaskFilter());
    }

    /**
     * Fetches the list of recent tasks, based on a filter
     *
     * @param callback The callback to receive the task plan once its complete or null. This is
     *                always called on the UI thread.
     * @param filter  Returns true if a GroupTask should be included into the list passed into
     *                callback.
     * @return the request id associated with this call.
     */
    public int getTasks(@Nullable Consumer<List<GroupTask>> callback, Predicate<GroupTask> filter) {
        return mTaskList.getTasks(false /* loadKeysOnly */, callback, filter);
    }

    /**
     * @return Whether the provided {@param changeId} is the latest recent tasks list id.
     */
    public boolean isTaskListValid(int changeId) {
        return mTaskList.isTaskListValid(changeId);
    }

    /**
     * @return Whether the task list is currently updating in the background
     */
    @VisibleForTesting
    public boolean isLoadingTasksInBackground() {
        return mTaskList.isLoadingTasksInBackground();
    }

    /**
     * Checks if a task has been removed or not.
     *
     * @param callback Receives true if task is removed, false otherwise
     * @param filter Returns true if GroupTask should be in the list of considerations
     */
    public void isTaskRemoved(int taskId, Consumer<Boolean> callback, Predicate<GroupTask> filter) {
        // Invalidate the existing list before checking to ensure this reflects the current state in
        // the system
        mTaskList.onRecentTasksChanged();
        mTaskList.getTasks(true /* loadKeysOnly */, (taskGroups) -> {
            for (GroupTask group : taskGroups) {
                if (group.containsTask(taskId)) {
                    callback.accept(false);
                    return;
                }
            }
            callback.accept(true);
        }, filter);
    }

    @Override
    public void onTaskStackChangedBackground() {
        if (!mThumbnailCache.isPreloadingEnabled()) {
            // Skip if we aren't preloading
            return;
        }

        int currentUserId = Process.myUserHandle().getIdentifier();
        if (!checkCurrentOrManagedUserId(currentUserId, mContext)) {
            // Skip if we are not the current user
            return;
        }

        // Keep the cache up to date with the latest thumbnails
        ActivityManager.RunningTaskInfo runningTask =
                LawnchairQuickstepCompat.getActivityManagerCompat().getRunningTask(true);
        int runningTaskId = runningTask != null ? runningTask.id : -1;
        mTaskList.getTaskKeys(mThumbnailCache.getCacheSize(), taskGroups -> {
            for (GroupTask group : taskGroups) {
                if (group.containsTask(runningTaskId)) {
                    // Skip the running task, it's not going to have an up-to-date snapshot by the
                    // time the user next enters overview
                    continue;
                }
                group.getTasks().forEach(
                        t -> mThumbnailCache.updateThumbnailInCache(t, /* lowResolution= */ true));
            }
        });
    }

    @Override
    public boolean onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
        mThumbnailCache.updateTaskSnapShot(taskId, snapshot);

        for (TaskVisualsChangeListener listener : mThumbnailChangeListeners) {
            Task task = listener.onTaskThumbnailChanged(taskId, snapshot);
            if (task != null) {
                task.thumbnail = snapshot;
            }
        }
        return true;
    }

    @Override
    public void onTaskRemoved(int taskId) {
        Task.TaskKey stubKey = new Task.TaskKey(taskId, 0, new Intent(), null, 0, 0);
        mThumbnailCache.remove(stubKey);
        mIconCache.onTaskRemoved(stubKey);
    }

    public void onTrimMemory(int level) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            mThumbnailCache.getHighResLoadingState().setVisible(false);
        }
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            // Clear everything once we reach a low-mem situation
            mThumbnailCache.clear();
            mIconCache.clearCache();
        }
    }

    private void onAppIconChanged(String packageName, UserHandle user) {
        mIconCache.invalidateCacheEntries(packageName, user);
        for (TaskVisualsChangeListener listener : mThumbnailChangeListeners) {
            listener.onTaskIconChanged(packageName, user);
        }
    }

    @Override
    public void onTaskIconChanged(int taskId) {
        for (TaskVisualsChangeListener listener : mThumbnailChangeListeners) {
            listener.onTaskIconChanged(taskId);
        }
    }

    @Override
    public void onThemeChanged() {
        mIconCache.clearCache();
    }

    /**
     * Adds a listener for visuals changes
     */
    @Override
    public void addThumbnailChangeListener(TaskVisualsChangeListener listener) {
        mThumbnailChangeListeners.add(listener);
    }

    /**
     * Removes a previously added listener
     */
    @Override
    public void removeThumbnailChangeListener(TaskVisualsChangeListener listener) {
        mThumbnailChangeListeners.remove(listener);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "RecentsModel:");
        mTaskList.dump("  ", writer);
    }

    /**
     * Registers a listener for running tasks
     * TODO(b/343292503): Should we remove RunningTasksListener entirely if it's not needed?
     *  (Note that Desktop mode gets the running tasks by checking {@link DesktopTask#tasks}
     */
    public void registerRunningTasksListener(RunningTasksListener listener) {
        mTaskList.registerRunningTasksListener(listener);
    }

    /**
     * Removes the previously registered running tasks listener
     */
    public void unregisterRunningTasksListener() {
        mTaskList.unregisterRunningTasksListener();
    }

    /**
     * Registers a listener for recent tasks
     */
    public void registerRecentTasksChangedListener(RecentTasksChangedListener listener) {
        mTaskList.registerRecentTasksChangedListener(listener);
    }

    /**
     * Removes the previously registered running tasks listener
     */
    public void unregisterRecentTasksChangedListener() {
        mTaskList.unregisterRecentTasksChangedListener();
    }

    /**
     * Gets the set of running tasks.
     */
    public ArrayList<ActivityManager.RunningTaskInfo> getRunningTasks() {
        return mTaskList.getRunningTasks();
    }

    /**
     * Preloads cache if enableGridOnlyOverview is true, preloading is enabled and
     * highResLoadingState is enabled
     */
    public void preloadCacheIfNeeded() {
        if (!isCachePreloadingEnabled()) {
            return;
        }

        if (!mThumbnailCache.isPreloadingEnabled()) {
            // Skip if we aren't preloading.
            return;
        }

        if (!mThumbnailCache.getHighResLoadingState().isEnabled()) {
            // Skip if high-res loading state is disabled.
            return;
        }

        mTaskList.getTaskKeys(mThumbnailCache.getCacheSize(), taskGroups -> {
            for (GroupTask group : taskGroups) {
                group.getTasks().forEach(
                        t -> mThumbnailCache.updateThumbnailInCache(t, /* lowResolution= */ false));
            }
        });
    }

    /**
     * Updates cache size and preloads more tasks if cache size increases
     */
    public void updateCacheSizeAndPreloadIfNeeded() {
        if (!isCachePreloadingEnabled()) {
            return;
        }

        // If new size is larger than original size, preload more cache to fill the gap
        if (mThumbnailCache.updateCacheSizeAndRemoveExcess()) {
            preloadCacheIfNeeded();
        }
    }

    private boolean isCachePreloadingEnabled() {
        return enableGridOnlyOverview() || enableRefactorTaskThumbnail();
    }

    /**
     * Listener for receiving running tasks changes
     */
    public interface RunningTasksListener {
        /**
         * Called when there's a change to running tasks
         */
        void onRunningTasksChanged();
    }

    /**
     * Listener for receiving recent tasks changes
     */
    public interface RecentTasksChangedListener {
        /**
         * Called when there's a change to recent tasks
         */
        void onRecentTasksChanged();
    }
}
