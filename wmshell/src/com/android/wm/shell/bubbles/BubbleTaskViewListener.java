/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;

import static com.android.wm.shell.bubbles.util.BubbleUtils.getEnterBubbleTransaction;
import static com.android.wm.shell.bubbles.util.BubbleUtils.getExitBubbleTransaction;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.taskview.TaskView;
import com.android.wm.shell.taskview.TaskViewTaskController;

/**
 * A listener that works with task views for bubbles, manages launching the appropriate
 * content into the task view from the bubble and sends updates of task view events back to
 * the parent view via {@link BubbleTaskViewListener.Callback}.
 */
public class BubbleTaskViewListener implements TaskView.Listener {
    private static final String TAG = BubbleTaskViewListener.class.getSimpleName();

    /**
     * Callback to let the view parent of TaskView to be notified of different events.
     */
    public interface Callback {

        /** Called when the task is first created. */
        void onTaskCreated();

        /** Called when the visibility of the task changes. */
        void onContentVisibilityChanged(boolean visible);

        /** Called when back is pressed on the task root. */
        void onBackPressed();

        /** Called when task removal has started. */
        void onTaskRemovalStarted();

        /** Called when the task's info has changed. */
        void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo);
    }

    private final Context mContext;
    private final BubbleExpandedViewManager mExpandedViewManager;
    private final BubbleTaskViewListener.Callback mCallback;
    private final View mParentView;

    private Bubble mBubble;
    @Nullable
    private PendingIntent mPendingIntent;
    private int mTaskId = INVALID_TASK_ID;
    private TaskView mTaskView;

    private boolean mInitialized = false;
    private boolean mDestroyed = false;

    public BubbleTaskViewListener(Context context, BubbleTaskView bubbleTaskView, View parentView,
            BubbleExpandedViewManager manager, BubbleTaskViewListener.Callback callback) {
        mContext = context;
        mTaskView = bubbleTaskView.getTaskView();
        mParentView = parentView;
        mExpandedViewManager = manager;
        mCallback = callback;
        bubbleTaskView.setDelegateListener(this);
        if (bubbleTaskView.isCreated()) {
            mTaskId = bubbleTaskView.getTaskId();
            callback.onTaskCreated();
        }
    }

    @Override
    public void onInitialized() {
        ProtoLog.d(WM_SHELL_BUBBLES, "onInitialized: destroyed=%b initialized=%b bubble=%s",
                mDestroyed, mInitialized, getBubbleKey());

        if (mDestroyed || mInitialized) {
            return;
        }

        // Custom options so there is no activity transition animation
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext,
                0 /* enterResId */, 0 /* exitResId */);

        Rect launchBounds = new Rect();
        mTaskView.getBoundsOnScreen(launchBounds);

        // TODO: I notice inconsistencies in lifecycle
        // Post to keep the lifecycle normal
        // TODO - currently based on type, really it's what the "launch item" is.
        mParentView.post(() -> {
            ProtoLog.d(WM_SHELL_BUBBLES,
                    "onInitialized: calling startActivity, bubble=%s hasPreparingTransition=%b",
                    getBubbleKey(), mBubble.getPreparingTransition() != null);
            try {
                options.setTaskAlwaysOnTop(true /* alwaysOnTop */);
                options.setPendingIntentBackgroundActivityStartMode(
                        MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
                final boolean isShortcutBubble = (mBubble.hasMetadataShortcutId()
                        || (mBubble.isShortcut()
                        && BubbleAnythingFlagHelper.enableCreateAnyBubble()));
                if (mBubble.getPreparingTransition() != null) {
                    mBubble.getPreparingTransition().surfaceCreated();
                } else if (mBubble.isApp() || mBubble.isNote()) {
                    Context context =
                            mContext.createContextAsUser(
                                    mBubble.getUser(), Context.CONTEXT_RESTRICTED);
                    Intent fillInIntent = new Intent();
                    // First try get pending intent from the bubble
                    PendingIntent pi = mBubble.getPendingIntent();
                    if (pi == null) {
                        // If null - create new one based on the bubble intent
                        pi = PendingIntent.getActivity(
                                context,
                                /* requestCode= */ 0,
                                mBubble.getIntent(),
                                // Needs to be mutable for the fillInIntent
                                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT,
                                /* options= */ null);
                    }
                    final WindowContainerToken rootToken =
                            mExpandedViewManager.getAppBubbleRootTaskToken();
                    if (rootToken != null) {
                        options.setLaunchRootTask(rootToken);
                    } else {
                        options.setLaunchNextToBubble(true /* launchNextToBubble */);
                    }
                    mTaskView.startActivity(pi, fillInIntent, options, launchBounds);
                } else if (isShortcutBubble) {
                    if (mBubble.isChat()) {
                        options.setLaunchedFromBubble(true);
                        options.setApplyActivityFlagsForBubbles(true);
                    } else {
                        final WindowContainerToken rootToken =
                                mExpandedViewManager.getAppBubbleRootTaskToken();
                        if (rootToken != null) {
                            options.setLaunchRootTask(rootToken);
                        } else {
                            options.setLaunchNextToBubble(true /* launchNextToBubble */);
                        }
                        options.setApplyMultipleTaskFlagForShortcut(true);
                    }
                    mTaskView.startShortcutActivity(mBubble.getShortcutInfo(),
                            options, launchBounds);
                } else {
                    options.setLaunchedFromBubble(true);
                    if (mBubble != null) {
                        mBubble.setPendingIntentActive();
                    }
                    final Intent fillInIntent = new Intent();
                    // Apply flags to make behaviour match documentLaunchMode=always.
                    fillInIntent.addFlags(FLAG_ACTIVITY_NEW_DOCUMENT);
                    fillInIntent.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
                    mTaskView.startActivity(mPendingIntent, fillInIntent, options,
                            launchBounds);
                }
            } catch (RuntimeException e) {
                // If there's a runtime exception here then there's something
                // wrong with the intent, we can't really recover / try to populate
                // the bubble again so we'll just remove it.
                Log.e(TAG, "Exception while displaying bubble: " + getBubbleKey()
                        + "; removing bubble", e);
                mExpandedViewManager.removeBubble(
                        getBubbleKey(), Bubbles.DISMISS_INVALID_INTENT);
            }
            mInitialized = true;
        });
    }

    @Override
    public void onSurfaceAlreadyCreated() {
        ProtoLog.d(WM_SHELL_BUBBLES, "onSurfaceCreated: bubble=%s", getBubbleKey());
        if (mBubble.getPreparingTransition() != null) {
            mBubble.getPreparingTransition().surfaceCreated();
        }
    }

    @Override
    public void onReleased() {
        mDestroyed = true;
    }

    @Override
    public void onTaskCreated(int taskId, ComponentName name) {
        ProtoLog.d(WM_SHELL_BUBBLES, "onTaskCreated: taskId=%d bubble=%s",
                taskId, getBubbleKey());
        // The taskId is saved to use for removeTask, preventing appearance in recent tasks.
        mTaskId = taskId;

        if (mBubble != null && mBubble.isNote()) {
            // Let the controller know sooner what the taskId is.
            mExpandedViewManager.setNoteBubbleTaskId(mBubble.getKey(), mTaskId);
        }

        final TaskViewTaskController tvc = mTaskView.getController();
        final boolean isAppBubble = mBubble != null && (mBubble.isApp() || mBubble.isShortcut());
        final WindowContainerTransaction wct = getEnterBubbleTransaction(
                tvc.getTaskToken(), isAppBubble);
        tvc.getTaskOrganizer().applyTransaction(wct);

        // With the task org, the taskAppeared callback will only happen once the task has
        // already drawn
        mCallback.onTaskCreated();
    }

    @Override
    public void onTaskVisibilityChanged(int taskId, boolean visible) {
        mCallback.onContentVisibilityChanged(visible);
    }

    @Override
    public void onTaskRemovalStarted(int taskId) {
        ProtoLog.d(WM_SHELL_BUBBLES, "onTaskRemovalStarted: taskId=%d bubble=%s",
                taskId, getBubbleKey());
        if (mBubble != null) {
            mExpandedViewManager.removeBubble(mBubble.getKey(), Bubbles.DISMISS_TASK_FINISHED);
        }
        if (mTaskView != null) {
            final TaskViewTaskController tvc = mTaskView.getController();
            final ActivityManager.RunningTaskInfo taskInfo = tvc.getTaskInfo();
            if (taskInfo != null && taskInfo.isRunning
                    && mExpandedViewManager.shouldBeAppBubble(taskInfo)) {
                final WindowContainerTransaction wct = getExitBubbleTransaction(taskInfo.token,
                        mTaskView.getCaptionInsetsOwner());
                tvc.getTaskOrganizer().applyTransaction(wct);
            }
            mTaskView.release();
            ((ViewGroup) mParentView).removeView(mTaskView);
            mTaskView = null;
        }
        mCallback.onTaskRemovalStarted();
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            mCallback.onTaskInfoChanged(taskInfo);
        }
    }

    @Override
    public void onBackPressedOnTaskRoot(int taskId) {
        if (mTaskId == taskId && mExpandedViewManager.isStackExpanded()) {
            mCallback.onBackPressed();
        }
    }

    /**
     * Sets the bubble or updates the bubble used to populate the view.
     *
     * @return true if the bubble is new or if the launch content of the bubble changed from the
     * previous bubble.
     */
    public boolean setBubble(Bubble bubble) {
        boolean isNew = mBubble == null || didBackingContentChange(bubble);
        mBubble = bubble;
        if (isNew) {
            mPendingIntent = mBubble.getPendingIntent();
        }
        return isNew;
    }

    /** Returns the TaskView associated with this view. */
    @Nullable
    public TaskView getTaskView() {
        return mTaskView;
    }

    /**
     * Returns the task id associated with the task in this view. If the task doesn't exist then
     * {@link ActivityTaskManager#INVALID_TASK_ID}.
     */
    public int getTaskId() {
        return mTaskId;
    }

    private String getBubbleKey() {
        return mBubble != null ? mBubble.getKey() : "";
    }

    // TODO (b/274980695): Is this still relevant?
    /**
     * Bubbles are backed by a pending intent or a shortcut, once the activity is
     * started we never change it / restart it on notification updates -- unless the bubble's
     * backing data switches.
     *
     * This indicates if the new bubble is backed by a different data source than what was
     * previously shown here (e.g. previously a pending intent & now a shortcut).
     *
     * @param newBubble the bubble this view is being updated with.
     * @return true if the backing content has changed.
     */
    private boolean didBackingContentChange(Bubble newBubble) {
        boolean prevWasIntentBased = mBubble != null && mPendingIntent != null;
        boolean newIsIntentBased = newBubble.getPendingIntent() != null;
        return prevWasIntentBased != newIsIntentBased;
    }
}
