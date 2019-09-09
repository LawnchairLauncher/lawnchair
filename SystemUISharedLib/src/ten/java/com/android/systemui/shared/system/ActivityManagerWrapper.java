/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui.shared.system;

import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;
import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;
import static android.app.ActivityManager.LOCK_TASK_MODE_PINNED;
import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.AppGlobals;
import android.app.IAssistDataReceiver;
import android.app.WindowConfiguration.ActivityType;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;

import com.android.internal.app.IVoiceInteractionManagerService;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class ActivityManagerWrapper {

    private static final String TAG = "ActivityManagerWrapper";

    private static final ActivityManagerWrapper sInstance = new ActivityManagerWrapper();

    // Should match the values in PhoneWindowManager
    public static final String CLOSE_SYSTEM_WINDOWS_REASON_RECENTS = "recentapps";

    private final PackageManager mPackageManager;
    private final BackgroundExecutor mBackgroundExecutor;
    private final TaskStackChangeListeners mTaskStackChangeListeners;

    private ActivityManagerWrapper() {
        final Context context = AppGlobals.getInitialApplication();
        mPackageManager = context.getPackageManager();
        mBackgroundExecutor = BackgroundExecutor.get();
        mTaskStackChangeListeners = new TaskStackChangeListeners(Looper.getMainLooper());
    }

    public static ActivityManagerWrapper getInstance() {
        return sInstance;
    }

    /**
     * @return the current user's id.
     */
    public int getCurrentUserId() {
        UserInfo ui;
        try {
            ui = ActivityManager.getService().getCurrentUser();
            return ui != null ? ui.id : 0;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @return the top running task (can be {@code null}).
     */
    public ActivityManager.RunningTaskInfo getRunningTask() {
        return getRunningTask(ACTIVITY_TYPE_RECENTS /* ignoreActivityType */);
    }

    public ActivityManager.RunningTaskInfo getRunningTask(@ActivityType int ignoreActivityType) {
        // Note: The set of running tasks from the system is ordered by recency
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    ActivityTaskManager.getService().getFilteredTasks(1, ignoreActivityType,
                            WINDOWING_MODE_PINNED /* ignoreWindowingMode */);
            if (tasks.isEmpty()) {
                return null;
            }
            return tasks.get(0);
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * @return a list of the recents tasks.
     */
    public List<RecentTaskInfo> getRecentTasks(int numTasks, int userId) {
        try {
            return ActivityTaskManager.getService().getRecentTasks(numTasks,
                            RECENT_IGNORE_UNAVAILABLE, userId).getList();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get recent tasks", e);
            return new ArrayList<>();
        }
    }

    /**
     * @return the task snapshot for the given {@param taskId}.
     */
    public @NonNull ThumbnailData getTaskThumbnail(int taskId, boolean reducedResolution) {
        ActivityManager.TaskSnapshot snapshot = null;
        try {
            snapshot = ActivityTaskManager.getService().getTaskSnapshot(taskId, reducedResolution);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to retrieve task snapshot", e);
        }
        if (snapshot != null) {
            return new ThumbnailData(snapshot);
        } else {
            return new ThumbnailData();
        }
    }

    /**
     * @return the activity label, badging if necessary.
     */
    public String getBadgedActivityLabel(ActivityInfo info, int userId) {
        return getBadgedLabel(info.loadLabel(mPackageManager).toString(), userId);
    }

    /**
     * @return the application label, badging if necessary.
     */
    public String getBadgedApplicationLabel(ApplicationInfo appInfo, int userId) {
        return getBadgedLabel(appInfo.loadLabel(mPackageManager).toString(), userId);
    }

    /**
     * @return the content description for a given task, badging it if necessary.  The content
     * description joins the app and activity labels.
     */
    public String getBadgedContentDescription(ActivityInfo info, int userId,
            ActivityManager.TaskDescription td) {
        String activityLabel;
        if (td != null && td.getLabel() != null) {
            activityLabel = td.getLabel();
        } else {
            activityLabel = info.loadLabel(mPackageManager).toString();
        }
        String applicationLabel = info.applicationInfo.loadLabel(mPackageManager).toString();
        String badgedApplicationLabel = getBadgedLabel(applicationLabel, userId);
        return applicationLabel.equals(activityLabel)
                ? badgedApplicationLabel
                : badgedApplicationLabel + " " + activityLabel;
    }

    /**
     * @return the given label for a user, badging if necessary.
     */
    private String getBadgedLabel(String label, int userId) {
        if (userId != UserHandle.myUserId()) {
            label = mPackageManager.getUserBadgedLabel(label, new UserHandle(userId)).toString();
        }
        return label;
    }

    /**
     * Starts the recents activity. The caller should manage the thread on which this is called.
     */
    public void startRecentsActivity(Intent intent, final AssistDataReceiver assistDataReceiver,
            final RecentsAnimationListener animationHandler, final Consumer<Boolean> resultCallback,
            Handler resultCallbackHandler) {
        try {
            IAssistDataReceiver receiver = null;
            if (assistDataReceiver != null) {
                receiver = new IAssistDataReceiver.Stub() {
                    public void onHandleAssistData(Bundle resultData) {
                        assistDataReceiver.onHandleAssistData(resultData);
                    }
                    public void onHandleAssistScreenshot(Bitmap screenshot) {
                        assistDataReceiver.onHandleAssistScreenshot(screenshot);
                    }
                };
            }
            IRecentsAnimationRunner runner = null;
            if (animationHandler != null) {
                runner = new IRecentsAnimationRunner.Stub() {
                    @Override
                    public void onAnimationStart(IRecentsAnimationController controller,
                            RemoteAnimationTarget[] apps, Rect homeContentInsets,
                            Rect minimizedHomeBounds) {
                        final RecentsAnimationControllerCompat controllerCompat =
                                new RecentsAnimationControllerCompat(controller);
                        final RemoteAnimationTargetCompat[] appsCompat =
                                RemoteAnimationTargetCompat.wrap(apps);
                        animationHandler.onAnimationStart(controllerCompat, appsCompat,
                                homeContentInsets, minimizedHomeBounds);
                    }

                    @Override
                    public void onAnimationCanceled(boolean deferredWithScreenshot) {
                        animationHandler.onAnimationCanceled(deferredWithScreenshot);
                    }
                };
            }
            ActivityTaskManager.getService().startRecentsActivity(intent, receiver, runner);
            if (resultCallback != null) {
                resultCallbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        resultCallback.accept(true);
                    }
                });
            }
        } catch (Exception e) {
            if (resultCallback != null) {
                resultCallbackHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        resultCallback.accept(false);
                    }
                });
            }
        }
    }

    /**
     * Cancels the remote recents animation started from {@link #startRecentsActivity}.
     */
    public void cancelRecentsAnimation(boolean restoreHomeStackPosition) {
        try {
            ActivityTaskManager.getService().cancelRecentsAnimation(restoreHomeStackPosition);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to cancel recents animation", e);
        }
    }

    /**
     * Starts a task from Recents.
     *
     * @see {@link #startActivityFromRecentsAsync(TaskKey, ActivityOptions, int, int, Consumer, Handler)}
     */
    public void startActivityFromRecentsAsync(Task.TaskKey taskKey, ActivityOptions options,
            Consumer<Boolean> resultCallback, Handler resultCallbackHandler) {
        startActivityFromRecentsAsync(taskKey, options, WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_UNDEFINED, resultCallback, resultCallbackHandler);
    }

    /**
     * Starts a task from Recents.
     *
     * @param resultCallback The result success callback
     * @param resultCallbackHandler The handler to receive the result callback
     */
    public void startActivityFromRecentsAsync(final Task.TaskKey taskKey, ActivityOptions options,
            int windowingMode, int activityType, final Consumer<Boolean> resultCallback,
            final Handler resultCallbackHandler) {
        if (taskKey.windowingMode == WINDOWING_MODE_SPLIT_SCREEN_PRIMARY) {
            // We show non-visible docked tasks in Recents, but we always want to launch
            // them in the fullscreen stack.
            if (options == null) {
                options = ActivityOptions.makeBasic();
            }
            options.setLaunchWindowingMode(WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        } else if (windowingMode != WINDOWING_MODE_UNDEFINED
                || activityType != ACTIVITY_TYPE_UNDEFINED) {
            if (options == null) {
                options = ActivityOptions.makeBasic();
            }
            options.setLaunchWindowingMode(windowingMode);
            options.setLaunchActivityType(activityType);
        }
        final ActivityOptions finalOptions = options;

        // Execute this from another thread such that we can do other things (like caching the
        // bitmap for the thumbnail) while AM is busy starting our activity.
        mBackgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                boolean result = false;
                try {
                    result = startActivityFromRecents(taskKey.id, finalOptions);
                } catch (Exception e) {
                    // Fall through
                }
                final boolean finalResult = result;
                if (resultCallback != null) {
                    resultCallbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            resultCallback.accept(finalResult);
                        }
                    });
                }
            }
        });
    }

    /**
     * Starts a task from Recents synchronously.
     */
    public boolean startActivityFromRecents(int taskId, ActivityOptions options) {
        try {
            Bundle optsBundle = options == null ? null : options.toBundle();
            ActivityTaskManager.getService().startActivityFromRecents(taskId, optsBundle);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Moves an already resumed task to the side of the screen to initiate split screen.
     */
    public boolean setTaskWindowingModeSplitScreenPrimary(int taskId, int createMode,
            Rect initialBounds) {
        try {
            return ActivityTaskManager.getService().setTaskWindowingModeSplitScreenPrimary(taskId,
                    createMode, true /* onTop */, false /* animate */, initialBounds,
                    true /* showRecents */);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Registers a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void registerTaskStackListener(TaskStackChangeListener listener) {
        synchronized (mTaskStackChangeListeners) {
            mTaskStackChangeListeners.addListener(ActivityManager.getService(), listener);
        }
    }

    /**
     * Unregisters a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void unregisterTaskStackListener(TaskStackChangeListener listener) {
        synchronized (mTaskStackChangeListeners) {
            mTaskStackChangeListeners.removeListener(listener);
        }
    }

    /**
     * Requests that the system close any open system windows (including other SystemUI).
     */
    public Future<?> closeSystemWindows(final String reason) {
        return mBackgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ActivityManager.getService().closeSystemDialogs(reason);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to close system windows", e);
                }
            }
        });
    }

    /**
     * Removes a task by id.
     */
    public void removeTask(final int taskId) {
        mBackgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ActivityTaskManager.getService().removeTask(taskId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to remove task=" + taskId, e);
                }
            }
        });
    }

    /**
     * Removes all the recent tasks.
     */
    public void removeAllRecentTasks() {
        mBackgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ActivityTaskManager.getService().removeAllVisibleRecentTasks();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to remove all tasks", e);
                }
            }
        });
    }

    /**
     * Cancels the current window transtion to/from Recents for the given task id.
     */
    public void cancelWindowTransition(int taskId) {
        try {
            ActivityTaskManager.getService().cancelTaskWindowTransition(taskId);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to cancel window transition for task=" + taskId, e);
        }
    }

    /**
     * @return whether screen pinning is active.
     */
    public boolean isScreenPinningActive() {
        try {
            return ActivityTaskManager.getService().getLockTaskModeState() == LOCK_TASK_MODE_PINNED;
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * @return whether screen pinning is enabled.
     */
    public boolean isScreenPinningEnabled() {
        final ContentResolver cr = AppGlobals.getInitialApplication().getContentResolver();
        return Settings.System.getInt(cr, Settings.System.LOCK_TO_APP_ENABLED, 0) != 0;
    }

    /**
     * @return whether there is currently a locked task (ie. in screen pinning).
     */
    public boolean isLockToAppActive() {
        try {
            return ActivityTaskManager.getService().getLockTaskModeState() != LOCK_TASK_MODE_NONE;
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * @return whether lock task mode is active in kiosk-mode (not screen pinning).
     */
    public boolean isLockTaskKioskModeActive() {
        try {
            return ActivityTaskManager.getService().getLockTaskModeState() == LOCK_TASK_MODE_LOCKED;
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Shows a voice session identified by {@code token}
     * @return true if the session was shown, false otherwise
     */
    public boolean showVoiceSession(IBinder token, Bundle args, int flags) {
        IVoiceInteractionManagerService service = IVoiceInteractionManagerService.Stub.asInterface(
                ServiceManager.getService(Context.VOICE_INTERACTION_MANAGER_SERVICE));
        if (service == null) {
            return false;
        }
        try {
            return service.showSessionFromSession(token, args, flags);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Returns true if the system supports freeform multi-window.
     */
    public boolean supportsFreeformMultiWindow(Context context) {
        final boolean freeformDevOption = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0) != 0;
        return ActivityTaskManager.supportsMultiWindow(context)
                && (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                || freeformDevOption);
    }
}
