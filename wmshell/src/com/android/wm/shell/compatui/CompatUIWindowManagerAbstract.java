/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;
import static com.android.internal.annotations.VisibleForTesting.Visibility.PRIVATE;
import static com.android.internal.annotations.VisibleForTesting.Visibility.PROTECTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.TaskInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.util.Log;
import android.view.IWindow;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceSession;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;

/**
 * A superclass for all Compat UI {@link WindowlessWindowManager}s that holds shared logic and
 * exposes general API for {@link CompatUIController}.
 *
 * <p>Holds view hierarchy of a root surface and helps to inflate and manage layout.
 */
public abstract class CompatUIWindowManagerAbstract extends WindowlessWindowManager {

    protected final int mTaskId;
    protected Context mContext;

    private final SyncTransactionQueue mSyncQueue;
    private final int mDisplayId;
    private Configuration mTaskConfig;
    private ShellTaskOrganizer.TaskListener mTaskListener;
    private DisplayLayout mDisplayLayout;
    private final Rect mStableBounds;

    @NonNull
    private TaskInfo mTaskInfo;

    /**
     * Utility class for adding and releasing a View hierarchy for this {@link
     * WindowlessWindowManager} to {@code mLeash}.
     */
    @Nullable
    protected SurfaceControlViewHost mViewHost;

    /**
     * A surface leash to position the layout relative to the task, since we can't set position for
     * the {@code mViewHost} directly.
     */
    @Nullable
    protected SurfaceControl mLeash;

    protected CompatUIWindowManagerAbstract(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue, ShellTaskOrganizer.TaskListener taskListener,
            DisplayLayout displayLayout) {
        super(taskInfo.configuration, null /* rootSurface */, null /* hostInputToken */);
        mTaskInfo = taskInfo;
        mContext = context;
        mSyncQueue = syncQueue;
        mTaskConfig = taskInfo.configuration;
        mDisplayId = mContext.getDisplayId();
        mTaskId = taskInfo.taskId;
        mTaskListener = taskListener;
        mDisplayLayout = displayLayout;
        mStableBounds = new Rect();
        mDisplayLayout.getStableBounds(mStableBounds);
    }

    /**
     * @return {@code true} if the instance of the specific {@link CompatUIWindowManagerAbstract}
     * for the current task id needs to be recreated loading the related resources. This happens
     * if the user switches between Light/Dark mode, if the device is docked/undocked or if the
     * user switches between multi-window mode to fullscreen where the
     * {@link ShellTaskOrganizer.TaskListener} implementation is different.
     */
    boolean needsToBeRecreated(TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener) {
        return hasUiModeChanged(mTaskInfo, taskInfo) || hasTaskListenerChanged(taskListener);
    }

    /**
     * Returns the z-order of this window which will be passed to the {@link SurfaceControl} once
     * {@link #attachToParentSurface} is called.
     *
     * <p>See {@link SurfaceControl.Transaction#setLayer}.
     */
    protected abstract int getZOrder();

    /** Returns the layout of this window manager. */
    protected abstract @Nullable View getLayout();

    /**
     * Inflates and inits the layout of this window manager on to the root surface if both {@code
     * canShow} and {@link #eligibleToShowLayout} are true.
     *
     * <p>Doesn't do anything if layout is not eligible to be shown.
     *
     * @param canShow whether the layout is allowed to be shown by the parent controller.
     * @return whether the layout is eligible to be shown.
     */
    @VisibleForTesting(visibility = PROTECTED)
    public boolean createLayout(boolean canShow) {
        if (!eligibleToShowLayout()) {
            return false;
        }
        if (!canShow || getLayout() != null) {
            // Wait until layout should be visible, or layout was already created.
            return true;
        }

        if (mViewHost != null) {
            throw new IllegalStateException(
                    "A UI has already been created with this window manager.");
        }

        // Construction extracted into separate methods to allow injection for tests.
        mViewHost = createSurfaceViewHost();
        mViewHost.setView(createLayout(), getWindowLayoutParams());

        updateSurfacePosition();

        return true;
    }

    /** Inflates and inits the layout of this window manager. */
    protected abstract View createLayout();

    protected abstract void removeLayout();

    /**
     * Whether the layout is eligible to be shown according to the internal state of the subclass.
     */
    protected abstract boolean eligibleToShowLayout();

    @Override
    public void setConfiguration(Configuration configuration) {
        super.setConfiguration(configuration);
        mContext = mContext.createConfigurationContext(configuration);
    }

    @Override
    protected SurfaceControl getParentSurface(IWindow window, WindowManager.LayoutParams attrs) {
        String className = getClass().getSimpleName();
        final SurfaceControl.Builder builder = new SurfaceControl.Builder(new SurfaceSession())
                .setContainerLayer()
                .setName(className + "Leash")
                .setHidden(false)
                .setCallsite(className + "#attachToParentSurface");
        attachToParentSurface(builder);
        mLeash = builder.build();
        initSurface(mLeash);
        return mLeash;
    }

    protected ShellTaskOrganizer.TaskListener getTaskListener() {
        return mTaskListener;
    }

    /** Inits the z-order of the surface. */
    private void initSurface(SurfaceControl leash) {
        final int z = getZOrder();
        mSyncQueue.runInSync(t -> {
            if (leash == null || !leash.isValid()) {
                Log.w(getTag(), "The leash has been released.");
                return;
            }
            t.setLayer(leash, z);
        });
    }

    /**
     * Called when compat info changed.
     *
     * <p>The window manager is released if the layout is no longer eligible to be shown.
     *
     * @param canShow whether the layout is allowed to be shown by the parent controller.
     * @return whether the layout is eligible to be shown.
     */
    @VisibleForTesting(visibility = PROTECTED)
    public boolean updateCompatInfo(TaskInfo taskInfo,
            ShellTaskOrganizer.TaskListener taskListener, boolean canShow) {
        mTaskInfo = taskInfo;
        final Configuration prevTaskConfig = mTaskConfig;
        final ShellTaskOrganizer.TaskListener prevTaskListener = mTaskListener;
        mTaskConfig = taskInfo.configuration;
        mTaskListener = taskListener;

        // Update configuration.
        setConfiguration(mTaskConfig);

        if (!eligibleToShowLayout()) {
            release();
            return false;
        }

        View layout = getLayout();
        if (layout == null || prevTaskListener != taskListener
                || mTaskConfig.uiMode != prevTaskConfig.uiMode) {
            // Layout wasn't created yet or TaskListener changed, recreate the layout for new
            // surface parent.
            release();
            return createLayout(canShow);
        }

        boolean boundsUpdated = !mTaskConfig.windowConfiguration.getBounds().equals(
                prevTaskConfig.windowConfiguration.getBounds());
        boolean layoutDirectionUpdated =
                mTaskConfig.getLayoutDirection() != prevTaskConfig.getLayoutDirection();
        if (boundsUpdated || layoutDirectionUpdated) {
            onParentBoundsChanged();
        }

        if (layout != null && layoutDirectionUpdated) {
            // Update layout for RTL.
            layout.setLayoutDirection(mTaskConfig.getLayoutDirection());
        }

        return true;
    }

    /**
     * Updates the visibility of the layout.
     *
     * @param canShow whether the layout is allowed to be shown by the parent controller.
     */
    @VisibleForTesting(visibility = PACKAGE)
    public void updateVisibility(boolean canShow) {
        View layout = getLayout();
        if (layout == null) {
            // Layout may not have been created because it was hidden previously.
            createLayout(canShow);
            return;
        }

        final int newVisibility = canShow && eligibleToShowLayout() ? View.VISIBLE : View.GONE;
        if (layout.getVisibility() != newVisibility) {
            layout.setVisibility(newVisibility);
        }
    }

    /** Called when display layout changed. */
    @VisibleForTesting(visibility = PACKAGE)
    public void updateDisplayLayout(DisplayLayout displayLayout) {
        final Rect prevStableBounds = mStableBounds;
        final Rect curStableBounds = new Rect();
        displayLayout.getStableBounds(curStableBounds);
        mDisplayLayout = displayLayout;
        if (!prevStableBounds.equals(curStableBounds)) {
            // mStableBounds should be updated before we call onParentBoundsChanged.
            mStableBounds.set(curStableBounds);
            onParentBoundsChanged();
        }
    }

    /** Called when the surface is ready to be placed under the task surface. */
    @VisibleForTesting(visibility = PRIVATE)
    void attachToParentSurface(SurfaceControl.Builder b) {
        mTaskListener.attachChildSurfaceToTask(mTaskId, b);
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    public int getTaskId() {
        return mTaskId;
    }

    /** Releases the surface control and tears down the view hierarchy. */
    public void release() {
        // Hiding before releasing to avoid flickering when transitioning to the Home screen.
        View layout = getLayout();
        if (layout != null) {
            layout.setVisibility(View.GONE);
        }
        removeLayout();

        if (mViewHost != null) {
            mViewHost.release();
            mViewHost = null;
        }

        if (mLeash != null) {
            final SurfaceControl leash = mLeash;
            mSyncQueue.runInSync(t -> t.remove(leash));
            mLeash = null;
        }
    }

    /** Re-layouts the view host and updates the surface position. */
    void relayout() {
        relayout(getWindowLayoutParams());
    }

    protected void relayout(WindowManager.LayoutParams windowLayoutParams) {
        if (mViewHost == null) {
            return;
        }
        mViewHost.relayout(windowLayoutParams);
        updateSurfacePosition();
    }

    @NonNull
    protected TaskInfo getLastTaskInfo() {
        return mTaskInfo;
    }

    /**
     * Called following a change in the task bounds, display layout stable bounds, or the layout
     * direction.
     */
    protected void onParentBoundsChanged() {
        updateSurfacePosition();
    }

    /**
     * Updates the position of the surface with respect to the parent bounds.
     */
    protected abstract void updateSurfacePosition();

    /**
     * Updates the position of the surface with respect to the given {@code positionX} and {@code
     * positionY}.
     */
    protected void updateSurfacePosition(int positionX, int positionY) {
        if (mLeash == null) {
            return;
        }
        mSyncQueue.runInSync(t -> {
            if (mLeash == null || !mLeash.isValid()) {
                Log.w(getTag(), "The leash has been released.");
                return;
            }
            t.setPosition(mLeash, positionX, positionY);
        });
    }

    protected int getLayoutDirection() {
        return mContext.getResources().getConfiguration().getLayoutDirection();
    }

    protected Rect getTaskBounds() {
        return mTaskConfig.windowConfiguration.getBounds();
    }

    /** Returns the intersection between the task bounds and the display layout stable bounds. */
    protected Rect getTaskStableBounds() {
        final Rect result = new Rect(mStableBounds);
        result.intersect(getTaskBounds());
        return result;
    }

    /** Creates a {@link SurfaceControlViewHost} for this window manager. */
    @VisibleForTesting(visibility = PRIVATE)
    public SurfaceControlViewHost createSurfaceViewHost() {
        return new SurfaceControlViewHost(mContext, mContext.getDisplay(), this,
                getClass().getSimpleName());
    }

    /** Gets the layout params. */
    protected WindowManager.LayoutParams getWindowLayoutParams() {
        View layout = getLayout();
        if (layout == null) {
            return new WindowManager.LayoutParams();
        }
        // Measure how big the hint is since its size depends on the text size.
        layout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return getWindowLayoutParams(layout.getMeasuredWidth(), layout.getMeasuredHeight());
    }

    /** Gets the layout params given the width and height of the layout. */
    protected WindowManager.LayoutParams getWindowLayoutParams(int width, int height) {
        final WindowManager.LayoutParams winParams = new WindowManager.LayoutParams(
                // Cannot be wrap_content as this determines the actual window size
                width, height,
                TYPE_APPLICATION_OVERLAY,
                getWindowManagerLayoutParamsFlags(),
                PixelFormat.TRANSLUCENT);
        winParams.token = new Binder();
        winParams.setTitle(getClass().getSimpleName() + mTaskId);
        winParams.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION | PRIVATE_FLAG_TRUSTED_OVERLAY;
        return winParams;
    }

    /**
     * @return Flags to use for the {@link WindowManager} layout
     */
    protected int getWindowManagerLayoutParamsFlags() {
        return FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL;
    }

    protected final String getTag() {
        return getClass().getSimpleName();
    }

    protected boolean hasTaskListenerChanged(ShellTaskOrganizer.TaskListener newTaskListener) {
        return !mTaskListener.equals(newTaskListener);
    }

    protected static boolean hasUiModeChanged(TaskInfo currentTaskInfo, TaskInfo newTaskInfo) {
        return currentTaskInfo.configuration.uiMode != newTaskInfo.configuration.uiMode;
    }
}
