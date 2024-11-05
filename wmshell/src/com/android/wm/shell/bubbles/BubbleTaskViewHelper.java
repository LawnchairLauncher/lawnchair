/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

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

import androidx.annotation.Nullable;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.taskview.TaskView;

/**
 * Handles creating and updating the {@link TaskView} associated with a {@link Bubble}.
 */
public class BubbleTaskViewHelper {

    private static final String TAG = BubbleTaskViewHelper.class.getSimpleName();

    /**
     * Listener for users of {@link BubbleTaskViewHelper} to use to be notified of events
     * on the task.
     */
    public interface Listener {

        /** Called when the task is first created. */
        void onTaskCreated();

        /** Called when the visibility of the task changes. */
        void onContentVisibilityChanged(boolean visible);

        /** Called when back is pressed on the task root. */
        void onBackPressed();
    }

    private final Context mContext;
    private final BubbleExpandedViewManager mExpandedViewManager;
    private final BubbleTaskViewHelper.Listener mListener;
    private final View mParentView;

    @Nullable
    private Bubble mBubble;
    @Nullable
    private PendingIntent mPendingIntent;
    @Nullable
    private TaskView mTaskView;
    private int mTaskId = INVALID_TASK_ID;

    private final TaskView.Listener mTaskViewListener = new TaskView.Listener() {
        private boolean mInitialized = false;
        private boolean mDestroyed = false;

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
            mParentView.post(() -> {
                ProtoLog.d(WM_SHELL_BUBBLES, "onInitialized: calling startActivity, bubble=%s",
                        getBubbleKey());
                try {
                    options.setTaskAlwaysOnTop(true);
                    options.setLaunchedFromBubble(true);
                    options.setPendingIntentBackgroundActivityStartMode(
                            MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    options.setPendingIntentBackgroundActivityLaunchAllowedByPermission(true);

                    Intent fillInIntent = new Intent();
                    // Apply flags to make behaviour match documentLaunchMode=always.
                    fillInIntent.addFlags(FLAG_ACTIVITY_NEW_DOCUMENT);
                    fillInIntent.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);

                    if (mBubble.isAppBubble()) {
                        Context context =
                                mContext.createContextAsUser(
                                        mBubble.getUser(), Context.CONTEXT_RESTRICTED);
                        PendingIntent pi = PendingIntent.getActivity(
                                context,
                                /* requestCode= */ 0,
                                mBubble.getAppBubbleIntent()
                                        .addFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
                                        .addFlags(FLAG_ACTIVITY_MULTIPLE_TASK),
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT,
                                /* options= */ null);
                        mTaskView.startActivity(pi, /* fillInIntent= */ null, options,
                                launchBounds);
                    } else if (mBubble.hasMetadataShortcutId()) {
                        options.setApplyActivityFlagsForBubbles(true);
                        mTaskView.startShortcutActivity(mBubble.getShortcutInfo(),
                                options, launchBounds);
                    } else {
                        if (mBubble != null) {
                            mBubble.setIntentActive();
                        }
                        mTaskView.startActivity(mPendingIntent, fillInIntent, options,
                                launchBounds);
                    }
                } catch (RuntimeException e) {
                    // If there's a runtime exception here then there's something
                    // wrong with the intent, we can't really recover / try to populate
                    // the bubble again so we'll just remove it.
                    Log.w(TAG, "Exception while displaying bubble: " + getBubbleKey()
                            + ", " + e.getMessage() + "; removing bubble");
                    mExpandedViewManager.removeBubble(
                            getBubbleKey(), Bubbles.DISMISS_INVALID_INTENT);
                }
                mInitialized = true;
            });
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

            if (mBubble != null && mBubble.isAppBubble()) {
                // Let the controller know sooner what the taskId is.
                mExpandedViewManager.setAppBubbleTaskId(mBubble.getKey(), mTaskId);
            }

            // With the task org, the taskAppeared callback will only happen once the task has
            // already drawn
            mListener.onTaskCreated();
        }

        @Override
        public void onTaskVisibilityChanged(int taskId, boolean visible) {
            mListener.onContentVisibilityChanged(visible);
        }

        @Override
        public void onTaskRemovalStarted(int taskId) {
            ProtoLog.d(WM_SHELL_BUBBLES, "onTaskRemovalStarted: taskId=%d bubble=%s",
                    taskId, getBubbleKey());
            if (mBubble != null) {
                mExpandedViewManager.removeBubble(mBubble.getKey(), Bubbles.DISMISS_TASK_FINISHED);
            }
            if (mTaskView != null) {
                mTaskView.release();
                ((ViewGroup) mParentView).removeView(mTaskView);
                mTaskView = null;
            }
        }

        @Override
        public void onBackPressedOnTaskRoot(int taskId) {
            if (mTaskId == taskId && mExpandedViewManager.isStackExpanded()) {
                mListener.onBackPressed();
            }
        }
    };

    public BubbleTaskViewHelper(Context context,
            BubbleExpandedViewManager expandedViewManager,
            BubbleTaskViewHelper.Listener listener,
            BubbleTaskView bubbleTaskView,
            View parent) {
        mContext = context;
        mExpandedViewManager = expandedViewManager;
        mListener = listener;
        mParentView = parent;
        mTaskView = bubbleTaskView.getTaskView();
        bubbleTaskView.setDelegateListener(mTaskViewListener);
        if (bubbleTaskView.isCreated()) {
            mTaskId = bubbleTaskView.getTaskId();
            mListener.onTaskCreated();
        }
    }

    /**
     * Sets the bubble or updates the bubble used to populate the view.
     *
     * @return true if the bubble is new, false if it was an update to the same bubble.
     */
    public boolean update(Bubble bubble) {
        boolean isNew = mBubble == null || didBackingContentChange(bubble);
        mBubble = bubble;
        if (isNew) {
            mPendingIntent = mBubble.getBubbleIntent();
            return true;
        }
        return false;
    }

    /** Returns the bubble key associated with this view. */
    @Nullable
    public String getBubbleKey() {
        return mBubble != null ? mBubble.getKey() : null;
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

    /** Returns whether the bubble set on the helper is valid to populate the task view. */
    public boolean isValidBubble() {
        return mBubble != null && (mPendingIntent != null || mBubble.hasMetadataShortcutId());
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
        boolean newIsIntentBased = newBubble.getBubbleIntent() != null;
        return prevWasIntentBased != newIsIntentBased;
    }
}
