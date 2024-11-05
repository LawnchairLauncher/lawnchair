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

package com.android.wm.shell.taskview;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Handler;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * A {@link SurfaceView} that can display a task. This is a concrete implementation for
 * {@link TaskViewBase} which interacts {@link TaskViewTaskController}.
 */
public class TaskView extends SurfaceView implements SurfaceHolder.Callback,
        ViewTreeObserver.OnComputeInternalInsetsListener, TaskViewBase {
    /** Callback for listening task state. */
    public interface Listener {
        /**
         * Only called once when the surface has been created & the container is ready for
         * launching activities.
         */
        default void onInitialized() {}

        /** Called when the container can no longer launch activities. */
        default void onReleased() {}

        /** Called when a task is created inside the container. */
        default void onTaskCreated(int taskId, ComponentName name) {}

        /** Called when a task visibility changes. */
        default void onTaskVisibilityChanged(int taskId, boolean visible) {}

        /** Called when a task is about to be removed from the stack inside the container. */
        default void onTaskRemovalStarted(int taskId) {}

        /** Called when a task is created inside the container. */
        default void onBackPressedOnTaskRoot(int taskId) {}
    }

    private final Rect mTmpRect = new Rect();
    private final Rect mTmpRootRect = new Rect();
    private final int[] mTmpLocation = new int[2];
    private final Rect mBoundsOnScreen = new Rect();
    private final TaskViewTaskController mTaskViewTaskController;
    private Region mObscuredTouchRegion;
    private Insets mCaptionInsets;
    private Handler mHandler;

    public TaskView(Context context, TaskViewTaskController taskViewTaskController) {
        super(context, null, 0, 0, true /* disableBackgroundLayer */);
        mTaskViewTaskController = taskViewTaskController;
        // TODO(b/266736992): Think about a better way to set the TaskViewBase on the
        //  TaskViewTaskController and vice-versa
        mTaskViewTaskController.setTaskViewBase(this);
        mHandler = Handler.getMain();
        getHolder().addCallback(this);
    }

    /**
     * Launch a new activity.
     *
     * @param pendingIntent Intent used to launch an activity.
     * @param fillInIntent Additional Intent data, see {@link Intent#fillIn Intent.fillIn()}
     * @param options options for the activity.
     * @param launchBounds the bounds (window size and position) that the activity should be
     *                     launched in, in pixels and in screen coordinates.
     */
    public void startActivity(@NonNull PendingIntent pendingIntent, @Nullable Intent fillInIntent,
            @NonNull ActivityOptions options, @Nullable Rect launchBounds) {
        mTaskViewTaskController.startActivity(pendingIntent, fillInIntent, options, launchBounds);
    }

    /**
     * Launch an activity represented by {@link ShortcutInfo}.
     * <p>The owner of this container must be allowed to access the shortcut information,
     * as defined in {@link LauncherApps#hasShortcutHostPermission()} to use this method.
     *
     * @param shortcut the shortcut used to launch the activity.
     * @param options options for the activity.
     * @param launchBounds the bounds (window size and position) that the activity should be
     *                     launched in, in pixels and in screen coordinates.
     */
    public void startShortcutActivity(@NonNull ShortcutInfo shortcut,
            @NonNull ActivityOptions options, @Nullable Rect launchBounds) {
        mTaskViewTaskController.startShortcutActivity(shortcut, options, launchBounds);
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (mTaskViewTaskController.isUsingShellTransitions()) {
            // No need for additional work as it is already taken care of during
            // prepareOpenAnimation().
            return;
        }
        onLocationChanged();
        if (taskInfo.taskDescription != null) {
            final int bgColor = taskInfo.taskDescription.getBackgroundColor();
            runOnViewThread(() -> setResizeBackgroundColor(bgColor));
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo.taskDescription != null) {
            final int bgColor = taskInfo.taskDescription.getBackgroundColor();
            runOnViewThread(() -> setResizeBackgroundColor(bgColor));
        }
    }

    /**
     * @return {@code True} when the TaskView's surface has been created, {@code False} otherwise.
     */
    public boolean isInitialized() {
        return mTaskViewTaskController.isInitialized();
    }

    @Override
    public Rect getCurrentBoundsOnScreen() {
        getBoundsOnScreen(mTmpRect);
        return mTmpRect;
    }

    @Override
    public void setResizeBgColor(SurfaceControl.Transaction t, int bgColor) {
        if (mHandler.getLooper().isCurrentThread()) {
            // We can only use the transaction if it can updated synchronously, otherwise the tx
            // will be applied immediately after but also used/updated on the view thread which
            // will lead to a race and/or crash
            runOnViewThread(() -> setResizeBackgroundColor(t, bgColor));
        } else {
            runOnViewThread(() -> setResizeBackgroundColor(bgColor));
        }
    }

    /**
     * Only one listener may be set on the view, throws an exception otherwise.
     */
    public void setListener(@NonNull Executor executor, TaskView.Listener listener) {
        mTaskViewTaskController.setListener(executor, listener);
    }

    /**
     * Indicates a region of the view that is not touchable.
     *
     * @param obscuredRect the obscured region of the view.
     */
    public void setObscuredTouchRect(Rect obscuredRect) {
        mObscuredTouchRegion = obscuredRect != null ? new Region(obscuredRect) : null;
    }

    /**
     * Indicates a region of the view that is not touchable.
     *
     * @param obscuredRegion the obscured region of the view.
     */
    public void setObscuredTouchRegion(Region obscuredRegion) {
        mObscuredTouchRegion = obscuredRegion;
    }

    /**
     * Sets a region of the task to inset to allow for a caption bar. Currently only top insets
     * are supported.
     * <p>
     * This region will be factored in as an area of taskview that is not touchable activity
     * content (i.e. you don't need to additionally set {@link #setObscuredTouchRect(Rect)} for
     * the caption area).
     *
     * @param captionInsets the insets to apply to task view.
     */
    public void setCaptionInsets(Insets captionInsets) {
        mCaptionInsets = captionInsets;
        if (captionInsets == null) {
            // If captions are null we can set them now; otherwise they'll get set in
            // onComputeInternalInsets.
            mTaskViewTaskController.setCaptionInsets(null);
        }
    }

    /**
     * Call when view position or size has changed. Do not call when animating.
     */
    public void onLocationChanged() {
        getBoundsOnScreen(mTmpRect);
        mTaskViewTaskController.setWindowBounds(mTmpRect);
    }

    /**
     * Call to remove the task from window manager. This task will not appear in recents.
     */
    public void removeTask() {
        mTaskViewTaskController.removeTask();
    }

    /**
     * Release this container if it is initialized.
     */
    public void release() {
        getHolder().removeCallback(this);
        mTaskViewTaskController.release();
    }

    @Override
    public String toString() {
        return mTaskViewTaskController.toString();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mTaskViewTaskController.surfaceCreated(getSurfaceControl());
    }

    @Override
    public void surfaceChanged(@androidx.annotation.NonNull SurfaceHolder holder, int format,
            int width, int height) {
        getBoundsOnScreen(mTmpRect);
        mTaskViewTaskController.setWindowBounds(mTmpRect);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mTaskViewTaskController.surfaceDestroyed();
    }

    @Override
    public void onComputeInternalInsets(ViewTreeObserver.InternalInsetsInfo inoutInfo) {
        // TODO(b/176854108): Consider to move the logic into gatherTransparentRegions since this
        //   is dependent on the order of listener.
        // If there are multiple TaskViews, we'll set the touchable area as the root-view, then
        // subtract each TaskView from it.
        if (inoutInfo.touchableRegion.isEmpty()) {
            inoutInfo.setTouchableInsets(
                    ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
            View root = getRootView();
            root.getLocationInWindow(mTmpLocation);
            mTmpRootRect.set(mTmpLocation[0], mTmpLocation[1], root.getWidth(), root.getHeight());
            inoutInfo.touchableRegion.set(mTmpRootRect);
        }
        getLocationInWindow(mTmpLocation);
        mTmpRect.set(mTmpLocation[0], mTmpLocation[1],
                mTmpLocation[0] + getWidth(), mTmpLocation[1] + getHeight());
        if (mCaptionInsets != null) {
            mTmpRect.inset(mCaptionInsets);
            getBoundsOnScreen(mBoundsOnScreen);
            mTaskViewTaskController.setCaptionInsets(new Rect(
                    mBoundsOnScreen.left,
                    mBoundsOnScreen.top,
                    mBoundsOnScreen.right + getWidth(),
                    mBoundsOnScreen.top + mCaptionInsets.top));
        }
        inoutInfo.touchableRegion.op(mTmpRect, Region.Op.DIFFERENCE);

        if (mObscuredTouchRegion != null) {
            inoutInfo.touchableRegion.op(mObscuredTouchRegion, Region.Op.UNION);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnComputeInternalInsetsListener(this);
        mHandler = getHandler();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnComputeInternalInsetsListener(this);
        mHandler = Handler.getMain();
    }

    /** Returns the task info for the task in the TaskView. */
    @Nullable
    public ActivityManager.RunningTaskInfo getTaskInfo() {
        return mTaskViewTaskController.getTaskInfo();
    }

    /**
     * Sets the handler, only for testing.
     */
    @VisibleForTesting
    void setHandler(Handler viewHandler) {
        mHandler = viewHandler;
    }

    /**
     * Ensures that the given runnable runs on the view's thread.
     */
    private void runOnViewThread(Runnable r) {
        if (mHandler.getLooper().isCurrentThread()) {
            r.run();
        } else {
            // If this call is not from the same thread as the view, then post it
            mHandler.post(r);
        }
    }
}
