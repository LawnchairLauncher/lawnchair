/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.quickstep.views;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.widget.Toast.LENGTH_SHORT;

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.IdRes;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.launcher3.util.ViewPool.Reusable;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.TaskIconCache;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.util.CancellableTask;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.quickstep.util.TransformParams;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;

import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements Reusable {

    private static final String TAG = TaskView.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final RectF EMPTY_RECT_F = new RectF();

    public static final int FLAG_UPDATE_ICON = 1;
    public static final int FLAG_UPDATE_THUMBNAIL = FLAG_UPDATE_ICON << 1;

    public static final int FLAG_UPDATE_ALL = FLAG_UPDATE_ICON | FLAG_UPDATE_THUMBNAIL;

    /**
     * Used in conjunction with {@link #onTaskListVisibilityChanged(boolean, int)}, providing more
     * granularity on which components of this task require an update
     */
    @Retention(SOURCE)
    @IntDef({FLAG_UPDATE_ALL, FLAG_UPDATE_ICON, FLAG_UPDATE_THUMBNAIL})
    public @interface TaskDataChanges {}

    /**
     * Type of task view
     */
    @Retention(SOURCE)
    @IntDef({Type.SINGLE, Type.GROUPED, Type.DESKTOP})
    public @interface Type {
        int SINGLE = 1;
        int GROUPED = 2;
        int DESKTOP = 3;
    }

    /** The maximum amount that a task view can be scrimmed, dimmed or tinted. */
    public static final float MAX_PAGE_SCRIM_ALPHA = 0.4f;

    private static final float EDGE_SCALE_DOWN_FACTOR_CAROUSEL = 0.03f;
    private static final float EDGE_SCALE_DOWN_FACTOR_GRID = 0.00f;

    public static final long SCALE_ICON_DURATION = 120;
    private static final long DIM_ANIM_DURATION = 700;

    private static final Interpolator GRID_INTERPOLATOR = ACCEL_DEACCEL;

    /**
     * This technically can be a vanilla {@link TouchDelegate} class, however that class requires
     * setting the touch bounds at construction, so we'd repeatedly be created many instances
     * unnecessarily as scrolling occurs, whereas {@link TransformingTouchDelegate} allows touch
     * delegated bounds only to be updated.
     */
    private TransformingTouchDelegate mIconTouchDelegate;

    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT =
            Collections.singletonList(new Rect());

    public static final FloatProperty<TaskView> FOCUS_TRANSITION =
            new FloatProperty<TaskView>("focusTransition") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setIconsAndBannersTransitionProgress(v, false /* invert */);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mFocusTransitionProgress;
                }
            };

    private static final FloatProperty<TaskView> SPLIT_SELECT_TRANSLATION_X =
            new FloatProperty<TaskView>("splitSelectTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setSplitSelectTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mSplitSelectTranslationX;
                }
            };

    private static final FloatProperty<TaskView> SPLIT_SELECT_TRANSLATION_Y =
            new FloatProperty<TaskView>("splitSelectTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setSplitSelectTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mSplitSelectTranslationY;
                }
            };

    private static final FloatProperty<TaskView> DISMISS_TRANSLATION_X =
            new FloatProperty<TaskView>("dismissTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setDismissTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mDismissTranslationX;
                }
            };

    private static final FloatProperty<TaskView> DISMISS_TRANSLATION_Y =
            new FloatProperty<TaskView>("dismissTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setDismissTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mDismissTranslationY;
                }
            };

    private static final FloatProperty<TaskView> TASK_OFFSET_TRANSLATION_X =
            new FloatProperty<TaskView>("taskOffsetTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskOffsetTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskOffsetTranslationX;
                }
            };

    private static final FloatProperty<TaskView> TASK_OFFSET_TRANSLATION_Y =
            new FloatProperty<TaskView>("taskOffsetTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskOffsetTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskOffsetTranslationY;
                }
            };

    private static final FloatProperty<TaskView> TASK_RESISTANCE_TRANSLATION_X =
            new FloatProperty<TaskView>("taskResistanceTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskResistanceTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskResistanceTranslationX;
                }
            };

    private static final FloatProperty<TaskView> TASK_RESISTANCE_TRANSLATION_Y =
            new FloatProperty<TaskView>("taskResistanceTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskResistanceTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskResistanceTranslationY;
                }
            };

    private static final FloatProperty<TaskView> NON_GRID_TRANSLATION_X =
            new FloatProperty<TaskView>("nonGridTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setNonGridTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mNonGridTranslationX;
                }
            };

    private static final FloatProperty<TaskView> NON_GRID_TRANSLATION_Y =
            new FloatProperty<TaskView>("nonGridTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setNonGridTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mNonGridTranslationY;
                }
            };

    public static final FloatProperty<TaskView> GRID_END_TRANSLATION_X =
            new FloatProperty<TaskView>("gridEndTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setGridEndTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mGridEndTranslationX;
                }
            };

    public static final FloatProperty<TaskView> SNAPSHOT_SCALE =
            new FloatProperty<TaskView>("snapshotScale") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setSnapshotScale(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mSnapshotView.getScaleX();
                }
            };

    @Nullable
    protected Task mTask;
    protected TaskThumbnailView mSnapshotView;
    protected IconView mIconView;
    protected final DigitalWellBeingToast mDigitalWellBeingToast;
    protected float mFullscreenProgress;
    private float mGridProgress;
    protected float mTaskThumbnailSplashAlpha;
    private float mNonGridScale = 1;
    private float mDismissScale = 1;
    protected final FullscreenDrawParams mCurrentFullscreenParams;
    protected final StatefulActivity mActivity;

    // Various causes of changing primary translation, which we aggregate to setTranslationX/Y().
    private float mDismissTranslationX;
    private float mDismissTranslationY;
    private float mTaskOffsetTranslationX;
    private float mTaskOffsetTranslationY;
    private float mTaskResistanceTranslationX;
    private float mTaskResistanceTranslationY;
    // The following translation variables should only be used in the same orientation as Launcher.
    private float mBoxTranslationY;
    // The following grid translations scales with mGridProgress.
    private float mGridTranslationX;
    private float mGridTranslationY;
    // The following grid translation is used to animate closing the gap between grid and clear all.
    private float mGridEndTranslationX;
    // Applied as a complement to gridTranslation, for adjusting the carousel overview and quick
    // switch.
    private float mNonGridTranslationX;
    private float mNonGridTranslationY;
    // Used when in SplitScreenSelectState
    private float mSplitSelectTranslationY;
    private float mSplitSelectTranslationX;
    private float mSplitSelectScrollOffsetPrimary;

    @Nullable
    private ObjectAnimator mIconAndDimAnimator;
    private float mIconScaleAnimStartProgress = 0;
    private float mFocusTransitionProgress = 1;
    private float mModalness = 0;
    private float mStableAlpha = 1;

    private int mTaskViewId = -1;
    /**
     * Index 0 will contain taskID of left/top task, index 1 will contain taskId of bottom/right
     */
    protected int[] mTaskIdContainer = new int[]{-1, -1};
    protected TaskIdAttributeContainer[] mTaskIdAttributeContainer =
            new TaskIdAttributeContainer[2];

    private boolean mShowScreenshot;

    // The current background requests to load the task thumbnail and icon
    @Nullable
    private CancellableTask mThumbnailLoadRequest;
    @Nullable
    private CancellableTask mIconLoadRequest;

    private boolean mEndQuickswitchCuj;

    private final float[] mIconCenterCoords = new float[2];

    protected final PointF mLastTouchDownPosition = new PointF();

    private boolean mIsClickableAsLiveTile = true;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivity = StatefulActivity.fromContext(context);
        setOnClickListener(this::onClick);

        mCurrentFullscreenParams = new FullscreenDrawParams(context);
        mDigitalWellBeingToast = new DigitalWellBeingToast(mActivity, this);
    }

    public void setTaskViewId(int id) {
        this.mTaskViewId = id;
    }

    public int getTaskViewId() {
        return mTaskViewId;
    }

    /**
     * Builds proto for logging
     */
    public WorkspaceItemInfo getItemInfo() {
        return getItemInfo(mTask);
    }

    protected WorkspaceItemInfo getItemInfo(@Nullable Task task) {
        WorkspaceItemInfo stubInfo = new WorkspaceItemInfo();
        stubInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_TASK;
        stubInfo.container = LauncherSettings.Favorites.CONTAINER_TASKSWITCHER;
        if (task == null) {
            return stubInfo;
        }

        ComponentKey componentKey = TaskUtils.getLaunchComponentKeyForTask(task.key);
        stubInfo.user = componentKey.user;
        stubInfo.intent = new Intent().setComponent(componentKey.componentName);
        stubInfo.title = task.title;
        if (getRecentsView() != null) {
            stubInfo.screenId = getRecentsView().indexOfChild(this);
        }
        return stubInfo;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView = findViewById(R.id.snapshot);
        mIconView = findViewById(R.id.icon);
        mIconTouchDelegate = new TransformingTouchDelegate(mIconView);
    }

    /**
     * Whether the taskview should take the touch event from parent. Events passed to children
     * that might require special handling.
     */
    public boolean offerTouchToChildren(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            computeAndSetIconTouchDelegate(mIconView, mIconCenterCoords, mIconTouchDelegate);
        }
        if (mIconTouchDelegate != null && mIconTouchDelegate.onTouchEvent(event)) {
            return true;
        }
        return false;
    }

    protected void computeAndSetIconTouchDelegate(IconView iconView, float[] tempCenterCoords,
            TransformingTouchDelegate transformingTouchDelegate) {
        float iconHalfSize = iconView.getWidth() / 2f;
        tempCenterCoords[0] = tempCenterCoords[1] = iconHalfSize;
        getDescendantCoordRelativeToAncestor(iconView, mActivity.getDragLayer(), tempCenterCoords,
                false);
        transformingTouchDelegate.setBounds(
                (int) (tempCenterCoords[0] - iconHalfSize),
                (int) (tempCenterCoords[1] - iconHalfSize),
                (int) (tempCenterCoords[0] + iconHalfSize),
                (int) (tempCenterCoords[1] + iconHalfSize));
    }

    /**
     * The modalness of this view is how it should be displayed when it is shown on its own in the
     * modal state of overview.
     *
     * @param modalness [0, 1] 0 being in context with other tasks, 1 being shown on its own.
     */
    public void setModalness(float modalness) {
        if (mModalness == modalness) {
            return;
        }
        mModalness = modalness;
        mIconView.setAlpha(1 - modalness);
        mDigitalWellBeingToast.updateBannerOffset(modalness,
                mCurrentFullscreenParams.mCurrentDrawnInsets.top
                        + mCurrentFullscreenParams.mCurrentDrawnInsets.bottom);
    }

    public DigitalWellBeingToast getDigitalWellBeingToast() {
        return mDigitalWellBeingToast;
    }

    /**
     * Updates this task view to the given {@param task}.
     *
     * TODO(b/142282126) Re-evaluate if we need to pass in isMultiWindowMode after
     *   that issue is fixed
     */
    public void bind(Task task, RecentsOrientedState orientedState) {
        cancelPendingLoadTasks();
        mTask = task;
        mTaskIdContainer[0] = mTask.key.id;
        mTaskIdAttributeContainer[0] = new TaskIdAttributeContainer(task, mSnapshotView,
                mIconView, STAGE_POSITION_UNDEFINED);
        mSnapshotView.bind(task);
        setOrientationState(orientedState);
    }

    /**
     * Sets up an on-click listener and the visibility for show_windows icon on top of the task.
     */
    public void setUpShowAllInstancesListener() {
        String taskPackageName = mTaskIdAttributeContainer[0].mTask.key.getPackageName();

        // icon of the top/left task
        View showWindowsView = findViewById(R.id.show_windows);
        updateFilterCallback(showWindowsView, getFilterUpdateCallback(taskPackageName));
    }

    /**
     * Returns a callback that updates the state of the filter and the recents overview
     *
     * @param taskPackageName package name of the task to filter by
     */
    @Nullable
    protected View.OnClickListener getFilterUpdateCallback(String taskPackageName) {
        View.OnClickListener cb = (view) -> {
            // update and apply a new filter
            getRecentsView().setAndApplyFilter(taskPackageName);
        };

        if (!getRecentsView().getFilterState().shouldShowFilterUI(taskPackageName)) {
            cb = null;
        }
        return cb;
    }

    /**
     * Sets the correct visibility and callback on the provided filterView based on whether
     * the callback is null or not
     */
    protected void updateFilterCallback(@NonNull View filterView,
            @Nullable View.OnClickListener callback) {
        // Filtering changes alpha instead of the visibility since visibility
        // can be altered separately through RecentsView#resetFromSplitSelectionState()
        if (callback == null) {
            filterView.setAlpha(0);
        } else {
            filterView.setAlpha(1);
        }

        filterView.setOnClickListener(callback);
    }

    public TaskIdAttributeContainer[] getTaskIdAttributeContainers() {
        return mTaskIdAttributeContainer;
    }

    @Nullable
    public Task getTask() {
        return mTask;
    }

    /**
     * Check if given {@code taskId} is tracked in this view
     */
    public boolean containsTaskId(int taskId) {
        return mTask != null && mTask.key.id == taskId;
    }

    /**
     * @return integer array of two elements to be size consistent with max number of tasks possible
     *         index 0 will contain the taskId, index 1 will be -1 indicating a null taskID value
     */
    public int[] getTaskIds() {
        return mTaskIdContainer;
    }

    public boolean containsMultipleTasks() {
        return mTaskIdContainer[1] != -1;
    }

    public TaskThumbnailView getThumbnail() {
        return mSnapshotView;
    }

    void refreshThumbnails(@Nullable HashMap<Integer, ThumbnailData> thumbnailDatas) {
        if (mTask != null && thumbnailDatas != null) {
            final ThumbnailData thumbnailData = thumbnailDatas.get(mTask.key.id);
            if (thumbnailData != null) {
                mSnapshotView.setThumbnail(mTask, thumbnailData);
                return;
            }
        }

        mSnapshotView.refresh();
    }

    /** TODO(b/197033698) Remove all usages of above method and migrate to this one */
    public TaskThumbnailView[] getThumbnails() {
        return new TaskThumbnailView[]{mSnapshotView};
    }

    public IconView getIconView() {
        return mIconView;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        RecentsView recentsView = getRecentsView();
        if (recentsView == null || getTask() == null) {
            return false;
        }
        SplitSelectStateController splitSelectStateController =
                recentsView.getSplitSelectController();
        if (splitSelectStateController.isSplitSelectActive() &&
                splitSelectStateController.getInitialTaskId() == getTask().key.id) {
            // Prevent taps on the this taskview if it's being animated into split select state
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mLastTouchDownPosition.set(ev.getX(), ev.getY());
        }
        return super.dispatchTouchEvent(ev);
    }

    private void onClick(View view) {
        if (getTask() == null) {
            return;
        }
        if (confirmSecondSplitSelectApp()) {
            return;
        }
        launchTasks();
        mActivity.getStatsLogManager().logger().withItemInfo(getItemInfo())
                .log(LAUNCHER_TASK_LAUNCH_TAP);
    }

    /**
     * @return {@code true} if user is already in split select mode and this tap was to choose the
     *         second app. {@code false} otherwise
     */
    protected boolean confirmSecondSplitSelectApp() {
        int index = getLastSelectedChildTaskIndex();
        TaskIdAttributeContainer container = mTaskIdAttributeContainer[index];
        if (container != null) {
            return getRecentsView().confirmSplitSelect(this, container.getTask(),
                    container.getIconView().getDrawable(), container.getThumbnailView(),
                    container.getThumbnailView().getThumbnail(), /* intent */ null);
        }
        return false;
    }

    /**
     * Returns the task index of the last selected child task (0 or 1).
     */
    protected int getLastSelectedChildTaskIndex() {
        return 0;
    }

    /**
     * Starts the task associated with this view and animates the startup.
     * @return CompletionStage to indicate the animation completion or null if the launch failed.
     */
    @Nullable
    public RunnableList launchTaskAnimated() {
        if (mTask != null) {
            TestLogging.recordEvent(
                    TestProtocol.SEQUENCE_MAIN, "startActivityFromRecentsAsync", mTask);
            ActivityOptionsWrapper opts =  mActivity.getActivityLaunchOptions(this, null);
            opts.options.setLaunchDisplayId(
                    getDisplay() == null ? DEFAULT_DISPLAY : getDisplay().getDisplayId());
            if (ActivityManagerWrapper.getInstance()
                    .startActivityFromRecents(mTask.key, opts.options)) {
                RecentsView recentsView = getRecentsView();
                if (recentsView.getRunningTaskViewId() != -1) {
                    recentsView.onTaskLaunchedInLiveTileMode();

                    // Return a fresh callback in the live tile case, so that it's not accidentally
                    // triggered by QuickstepTransitionManager.AppLaunchAnimationRunner.
                    RunnableList callbackList = new RunnableList();
                    recentsView.addSideTaskLaunchCallback(callbackList);
                    return callbackList;
                }
                return opts.onEndCallback;
            } else {
                notifyTaskLaunchFailed(TAG);
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Starts the task associated with this view without any animation
     */
    public void launchTask(@NonNull Consumer<Boolean> callback) {
        launchTask(callback, false /* freezeTaskList */);
    }

    /**
     * Starts the task associated with this view without any animation
     */
    public void launchTask(@NonNull Consumer<Boolean> callback, boolean freezeTaskList) {
        if (mTask != null) {
            TestLogging.recordEvent(
                    TestProtocol.SEQUENCE_MAIN, "startActivityFromRecentsAsync", mTask);

            // Indicate success once the system has indicated that the transition has started
            ActivityOptions opts = makeCustomAnimation(getContext(), 0, 0,
                    () -> callback.accept(true), MAIN_EXECUTOR.getHandler());
            opts.setLaunchDisplayId(
                    getDisplay() == null ? DEFAULT_DISPLAY : getDisplay().getDisplayId());
            if (freezeTaskList) {
                opts.setFreezeRecentTasksReordering();
            }
            opts.setDisableStartingWindow(mSnapshotView.shouldShowSplashView());
            Task.TaskKey key = mTask.key;
            UI_HELPER_EXECUTOR.execute(() -> {
                if (!ActivityManagerWrapper.getInstance().startActivityFromRecents(key, opts)) {
                    // If the call to start activity failed, then post the result immediately,
                    // otherwise, wait for the animation start callback from the activity options
                    // above
                    MAIN_EXECUTOR.post(() -> {
                        notifyTaskLaunchFailed(TAG);
                        callback.accept(false);
                    });
                }
            });
        } else {
            callback.accept(false);
        }
    }

    /**
     * Returns ActivityOptions for overriding task transition animation.
     */
    private ActivityOptions makeCustomAnimation(Context context, int enterResId,
            int exitResId, final Runnable callback, final Handler callbackHandler) {
        return ActivityOptions.makeCustomTaskAnimation(context, enterResId, exitResId,
                callbackHandler,
                elapsedRealTime -> {
                    if (callback != null) {
                        callbackHandler.post(callback);
                    }
                }, null /* finishedListener */);
    }

    /**
     * Launch of the current task (both live and inactive tasks) with an animation.
     */
    @Nullable
    public RunnableList launchTasks() {
        RecentsView recentsView = getRecentsView();
        RemoteTargetHandle[] remoteTargetHandles = recentsView.mRemoteTargetHandles;
        if (isRunningTask() && remoteTargetHandles != null) {
            if (!mIsClickableAsLiveTile) {
                Log.e(TAG, "TaskView is not clickable as a live tile; returning to home.");
                return null;
            }

            mIsClickableAsLiveTile = false;
            RemoteAnimationTargets targets;
            if (remoteTargetHandles.length == 1) {
                targets = remoteTargetHandles[0].getTransformParams().getTargetSet();
            } else {
                TransformParams topLeftParams = remoteTargetHandles[0].getTransformParams();
                TransformParams rightBottomParams = remoteTargetHandles[1].getTransformParams();
                RemoteAnimationTarget[] apps = Stream.concat(
                        Arrays.stream(topLeftParams.getTargetSet().apps),
                        Arrays.stream(rightBottomParams.getTargetSet().apps))
                        .toArray(RemoteAnimationTarget[]::new);
                RemoteAnimationTarget[] wallpapers = Stream.concat(
                        Arrays.stream(topLeftParams.getTargetSet().wallpapers),
                        Arrays.stream(rightBottomParams.getTargetSet().wallpapers))
                        .toArray(RemoteAnimationTarget[]::new);
                targets = new RemoteAnimationTargets(apps, wallpapers,
                        topLeftParams.getTargetSet().nonApps,
                        topLeftParams.getTargetSet().targetMode);
            }
            if (targets == null) {
                // If the recents animation is cancelled somehow between the parent if block and
                // here, try to launch the task as a non live tile task.
                RunnableList runnableList = launchTaskAnimated();
                if (runnableList == null) {
                    Log.e(TAG, "Recents animation cancelled and cannot launch task as non-live tile"
                            + "; returning to home");
                }
                mIsClickableAsLiveTile = true;
                return runnableList;
            }

            RunnableList runnableList = new RunnableList();
            AnimatorSet anim = new AnimatorSet();
            TaskViewUtils.composeRecentsLaunchAnimator(
                    anim, this, targets.apps,
                    targets.wallpapers, targets.nonApps, true /* launcherClosing */,
                    mActivity.getStateManager(), recentsView,
                    recentsView.getDepthController());
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    recentsView.runActionOnRemoteHandles(
                            (Consumer<RemoteTargetHandle>) remoteTargetHandle ->
                                    remoteTargetHandle
                                            .getTaskViewSimulator()
                                            .setDrawsBelowRecents(false));
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    if (mTask != null && mTask.key.displayId != getRootViewDisplayId()) {
                        launchTaskAnimated();
                    }
                    mIsClickableAsLiveTile = true;
                    runEndCallback();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    runEndCallback();
                }

                private void runEndCallback() {
                    runnableList.executeAllAndDestroy();
                }
            });
            anim.start();
            recentsView.onTaskLaunchedInLiveTileMode();
            return runnableList;
        } else {
            return launchTaskAnimated();
        }
    }

    /**
     * See {@link TaskDataChanges}
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    public void onTaskListVisibilityChanged(boolean visible) {
        onTaskListVisibilityChanged(visible, FLAG_UPDATE_ALL);
    }

    /**
     * See {@link TaskDataChanges}
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    public void onTaskListVisibilityChanged(boolean visible, @TaskDataChanges int changes) {
        if (mTask == null) {
            return;
        }
        cancelPendingLoadTasks();
        if (visible) {
            // These calls are no-ops if the data is already loaded, try and load the high
            // resolution thumbnail if the state permits
            RecentsModel model = RecentsModel.INSTANCE.get(getContext());
            TaskThumbnailCache thumbnailCache = model.getThumbnailCache();
            TaskIconCache iconCache = model.getIconCache();

            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mThumbnailLoadRequest = thumbnailCache.updateThumbnailInBackground(
                        mTask, thumbnail -> {
                            mSnapshotView.setThumbnail(mTask, thumbnail);
                        });
            }
            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                mIconLoadRequest = iconCache.updateIconInBackground(mTask,
                        (task) -> {
                            setIcon(mIconView, task.icon);
                            mDigitalWellBeingToast.initialize(mTask);
                        });
            }
        } else {
            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mSnapshotView.setThumbnail(null, null);
                // Reset the task thumbnail reference as well (it will be fetched from the cache or
                // reloaded next time we need it)
                mTask.thumbnail = null;
            }
            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                setIcon(mIconView, null);
            }
        }
    }

    protected boolean needsUpdate(@TaskDataChanges int dataChange, @TaskDataChanges int flag) {
        return (dataChange & flag) == flag;
    }

    protected void cancelPendingLoadTasks() {
        if (mThumbnailLoadRequest != null) {
            mThumbnailLoadRequest.cancel();
            mThumbnailLoadRequest = null;
        }
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
    }

    private boolean showTaskMenu(IconView iconView) {
        if (!getRecentsView().canLaunchFullscreenTask()) {
            // Don't show menu when selecting second split screen app
            return true;
        }

        if (!mActivity.getDeviceProfile().isTablet
                && !getRecentsView().isClearAllHidden()) {
            getRecentsView().snapToPage(getRecentsView().indexOfChild(this));
            return false;
        } else {
            mActivity.getStatsLogManager().logger().withItemInfo(getItemInfo())
                    .log(LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS);
            return showTaskMenuWithContainer(iconView);
        }
    }

    protected boolean showTaskMenuWithContainer(IconView iconView) {
        TaskIdAttributeContainer menuContainer =
                mTaskIdAttributeContainer[iconView == mIconView ? 0 : 1];
        if (mActivity.getDeviceProfile().isTablet) {
            boolean alignSecondRow = getRecentsView().isOnGridBottomRow(menuContainer.getTaskView())
                    && mActivity.getDeviceProfile().isLandscape;
            return TaskMenuViewWithArrow.Companion.showForTask(menuContainer, alignSecondRow);
        } else {
            return TaskMenuView.showForTask(menuContainer);
        }
    }

    protected void setIcon(IconView iconView, @Nullable Drawable icon) {
        if (icon != null) {
            iconView.setDrawable(icon);
            iconView.setOnClickListener(v -> {
                if (confirmSecondSplitSelectApp()) {
                    return;
                }
                showTaskMenu(iconView);
            });
            iconView.setOnLongClickListener(v -> {
                requestDisallowInterceptTouchEvent(true);
                return showTaskMenu(iconView);
            });
        } else {
            iconView.setDrawable(null);
            iconView.setOnClickListener(null);
            iconView.setOnLongClickListener(null);
        }
    }

    public void setOrientationState(RecentsOrientedState orientationState) {
        PagedOrientationHandler orientationHandler = orientationState.getOrientationHandler();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();

        boolean isGridTask = isGridTask();
        LayoutParams iconParams = (LayoutParams) mIconView.getLayoutParams();

        int thumbnailTopMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;
        int taskIconHeight = deviceProfile.overviewTaskIconSizePx;
        int taskMargin = deviceProfile.overviewTaskMarginPx;

        orientationHandler.setTaskIconParams(iconParams, taskMargin, taskIconHeight,
                thumbnailTopMargin, isRtl);
        iconParams.width = iconParams.height = taskIconHeight;
        mIconView.setLayoutParams(iconParams);

        mIconView.setRotation(orientationHandler.getDegreesRotated());
        int iconDrawableSize = isGridTask ? deviceProfile.overviewTaskIconDrawableSizeGridPx
                : deviceProfile.overviewTaskIconDrawableSizePx;
        mIconView.setDrawableSize(iconDrawableSize, iconDrawableSize);

        LayoutParams snapshotParams = (LayoutParams) mSnapshotView.getLayoutParams();
        snapshotParams.topMargin = thumbnailTopMargin;
        mSnapshotView.setLayoutParams(snapshotParams);

        mSnapshotView.getTaskOverlay().updateOrientationState(orientationState);
        mDigitalWellBeingToast.initialize(mTask);
    }

    /**
     * Returns whether the task is part of overview grid and not being focused.
     */
    public boolean isGridTask() {
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        return deviceProfile.isTablet && !isFocusedTask();
    }

    /**
     * Called to animate a smooth transition when going directly from an app into Overview (and
     * vice versa). Icons fade in, and DWB banners slide in with a "shift up" animation.
     */
    protected void setIconsAndBannersTransitionProgress(float progress, boolean invert) {
        if (invert) {
            progress = 1 - progress;
        }
        mFocusTransitionProgress = progress;
        float iconScalePercentage = (float) SCALE_ICON_DURATION / DIM_ANIM_DURATION;
        float lowerClamp = invert ? 1f - iconScalePercentage : 0;
        float upperClamp = invert ? 1 : iconScalePercentage;
        float scale = Interpolators.clampToProgress(FAST_OUT_SLOW_IN, lowerClamp, upperClamp)
                .getInterpolation(progress);
        mIconView.setAlpha(scale);
        mDigitalWellBeingToast.updateBannerOffset(1f - scale,
                mCurrentFullscreenParams.mCurrentDrawnInsets.top
                        + mCurrentFullscreenParams.mCurrentDrawnInsets.bottom);
    }

    public void setIconScaleAnimStartProgress(float startProgress) {
        mIconScaleAnimStartProgress = startProgress;
    }

    public void animateIconScaleAndDimIntoView() {
        if (mIconAndDimAnimator != null) {
            mIconAndDimAnimator.cancel();
        }
        mIconAndDimAnimator = ObjectAnimator.ofFloat(this, FOCUS_TRANSITION, 1);
        mIconAndDimAnimator.setCurrentFraction(mIconScaleAnimStartProgress);
        mIconAndDimAnimator.setDuration(DIM_ANIM_DURATION).setInterpolator(LINEAR);
        mIconAndDimAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIconAndDimAnimator = null;
            }
        });
        mIconAndDimAnimator.start();
    }

    protected void setIconScaleAndDim(float iconScale) {
        setIconScaleAndDim(iconScale, false);
    }

    private void setIconScaleAndDim(float iconScale, boolean invert) {
        if (mIconAndDimAnimator != null) {
            mIconAndDimAnimator.cancel();
        }
        setIconsAndBannersTransitionProgress(iconScale, invert);
    }

    protected void resetPersistentViewTransforms() {
        mNonGridTranslationX = mNonGridTranslationY =
                mGridTranslationX = mGridTranslationY = mBoxTranslationY = 0f;
        resetViewTransforms();
    }

    protected void resetViewTransforms() {
        // fullscreenTranslation and accumulatedTranslation should not be reset, as
        // resetViewTransforms is called during Quickswitch scrolling.
        mDismissTranslationX = mTaskOffsetTranslationX =
                mTaskResistanceTranslationX = mSplitSelectTranslationX = mGridEndTranslationX = 0f;
        mDismissTranslationY = mTaskOffsetTranslationY = mTaskResistanceTranslationY = 0f;
        if (getRecentsView() == null || !getRecentsView().isSplitSelectionActive()) {
            mSplitSelectTranslationY = 0f;
        }

        setSnapshotScale(1f);
        applyTranslationX();
        applyTranslationY();
        setTranslationZ(0);
        setAlpha(mStableAlpha);
        setIconScaleAndDim(1);
        setColorTint(0, 0);
    }

    public void setStableAlpha(float parentAlpha) {
        mStableAlpha = parentAlpha;
        setAlpha(mStableAlpha);
    }

    @Override
    public void onRecycle() {
        resetPersistentViewTransforms();
        // Clear any references to the thumbnail (it will be re-read either from the cache or the
        // system on next bind)
        mSnapshotView.setThumbnail(mTask, null);
        setOverlayEnabled(false);
        onTaskListVisibilityChanged(false);
    }

    public float getTaskCornerRadius() {
        return mCurrentFullscreenParams.mCornerRadius;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mActivity.getDeviceProfile().isTablet) {
            setPivotX(getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 0 : right - left);
            setPivotY(mSnapshotView.getTop());
        } else {
            setPivotX((right - left) * 0.5f);
            setPivotY(mSnapshotView.getTop() + mSnapshotView.getHeight() * 0.5f);
        }
        if (Utilities.ATLEAST_Q) {
            SYSTEM_GESTURE_EXCLUSION_RECT.get(0).set(0, 0, getWidth(), getHeight());
            setSystemGestureExclusionRects(SYSTEM_GESTURE_EXCLUSION_RECT);
        }
    }

    /**
     * How much to scale down pages near the edge of the screen.
     */
    public static float getEdgeScaleDownFactor(DeviceProfile deviceProfile) {
        return deviceProfile.isTablet ? EDGE_SCALE_DOWN_FACTOR_GRID
                : EDGE_SCALE_DOWN_FACTOR_CAROUSEL;
    }

    private void setNonGridScale(float nonGridScale) {
        mNonGridScale = nonGridScale;
        applyScale();
    }

    public float getNonGridScale() {
        return mNonGridScale;
    }

    private void setSnapshotScale(float dismissScale) {
        mDismissScale = dismissScale;
        applyScale();
    }

    /**
     * Moves TaskView between carousel and 2 row grid.
     *
     * @param gridProgress 0 = carousel; 1 = 2 row grid.
     */
    public void setGridProgress(float gridProgress) {
        mGridProgress = gridProgress;
        applyTranslationX();
        applyTranslationY();
        applyScale();
    }

    private void applyScale() {
        float scale = 1;
        scale *= getPersistentScale();
        scale *= mDismissScale;
        setScaleX(scale);
        setScaleY(scale);
        updateSnapshotRadius();
    }

    /**
     * Returns multiplication of scale that is persistent (e.g. fullscreen and grid), and does not
     * change according to a temporary state.
     */
    public float getPersistentScale() {
        float scale = 1;
        float gridProgress = GRID_INTERPOLATOR.getInterpolation(mGridProgress);
        scale *= Utilities.mapRange(gridProgress, mNonGridScale, 1f);
        return scale;
    }

    /**
     * Updates alpha of task thumbnail splash on swipe up/down.
     */
    public void setTaskThumbnailSplashAlpha(float taskThumbnailSplashAlpha) {
        mTaskThumbnailSplashAlpha = taskThumbnailSplashAlpha;
        applyThumbnailSplashAlpha();
    }

    protected void applyThumbnailSplashAlpha() {
        mSnapshotView.setSplashAlpha(mTaskThumbnailSplashAlpha);
    }

    private void setSplitSelectTranslationX(float x) {
        mSplitSelectTranslationX = x;
        applyTranslationX();
    }

    private void setSplitSelectTranslationY(float y) {
        mSplitSelectTranslationY = y;
        applyTranslationY();
    }

    public void setSplitScrollOffsetPrimary(float splitSelectScrollOffsetPrimary) {
        mSplitSelectScrollOffsetPrimary = splitSelectScrollOffsetPrimary;
    }

    private void setDismissTranslationX(float x) {
        mDismissTranslationX = x;
        applyTranslationX();
    }

    private void setDismissTranslationY(float y) {
        mDismissTranslationY = y;
        applyTranslationY();
    }

    private void setTaskOffsetTranslationX(float x) {
        mTaskOffsetTranslationX = x;
        applyTranslationX();
    }

    private void setTaskOffsetTranslationY(float y) {
        mTaskOffsetTranslationY = y;
        applyTranslationY();
    }

    private void setTaskResistanceTranslationX(float x) {
        mTaskResistanceTranslationX = x;
        applyTranslationX();
    }

    private void setTaskResistanceTranslationY(float y) {
        mTaskResistanceTranslationY = y;
        applyTranslationY();
    }

    private void setNonGridTranslationX(float nonGridTranslationX) {
        mNonGridTranslationX = nonGridTranslationX;
        applyTranslationX();
    }

    private void setNonGridTranslationY(float nonGridTranslationY) {
        mNonGridTranslationY = nonGridTranslationY;
        applyTranslationY();
    }

    public void setGridTranslationX(float gridTranslationX) {
        mGridTranslationX = gridTranslationX;
        applyTranslationX();
    }

    public float getGridTranslationX() {
        return mGridTranslationX;
    }

    public void setGridTranslationY(float gridTranslationY) {
        mGridTranslationY = gridTranslationY;
        applyTranslationY();
    }

    public float getGridTranslationY() {
        return mGridTranslationY;
    }

    private void setGridEndTranslationX(float gridEndTranslationX) {
        mGridEndTranslationX = gridEndTranslationX;
        applyTranslationX();
    }

    public float getScrollAdjustment(boolean fullscreenEnabled, boolean gridEnabled) {
        float scrollAdjustment = 0;
        if (gridEnabled) {
            scrollAdjustment += mGridTranslationX;
        } else {
            scrollAdjustment += getPrimaryNonGridTranslationProperty().get(this);
        }
        scrollAdjustment += mSplitSelectScrollOffsetPrimary;
        return scrollAdjustment;
    }

    public float getOffsetAdjustment(boolean fullscreenEnabled, boolean gridEnabled) {
        return getScrollAdjustment(fullscreenEnabled, gridEnabled);
    }

    public float getSizeAdjustment(boolean fullscreenEnabled) {
        float sizeAdjustment = 1;
        if (fullscreenEnabled) {
            sizeAdjustment *= mNonGridScale;
        }
        return sizeAdjustment;
    }

    private void setBoxTranslationY(float boxTranslationY) {
        mBoxTranslationY = boxTranslationY;
        applyTranslationY();
    }

    private void applyTranslationX() {
        setTranslationX(mDismissTranslationX + mTaskOffsetTranslationX + mTaskResistanceTranslationX
                + mSplitSelectTranslationX + mGridEndTranslationX + getPersistentTranslationX());
    }

    private void applyTranslationY() {
        setTranslationY(mDismissTranslationY + mTaskOffsetTranslationY + mTaskResistanceTranslationY
                + mSplitSelectTranslationY + getPersistentTranslationY());
    }

    /**
     * Returns addition of translationX that is persistent (e.g. fullscreen and grid), and does not
     * change according to a temporary state (e.g. task offset).
     */
    public float getPersistentTranslationX() {
        return getNonGridTrans(mNonGridTranslationX) + getGridTrans(mGridTranslationX);
    }

    /**
     * Returns addition of translationY that is persistent (e.g. fullscreen and grid), and does not
     * change according to a temporary state (e.g. task offset).
     */
    public float getPersistentTranslationY() {
        return mBoxTranslationY
                + getNonGridTrans(mNonGridTranslationY)
                + getGridTrans(mGridTranslationY);
    }

    public FloatProperty<TaskView> getPrimarySplitTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                SPLIT_SELECT_TRANSLATION_X, SPLIT_SELECT_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getSecondarySplitTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                SPLIT_SELECT_TRANSLATION_X, SPLIT_SELECT_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getPrimaryDismissTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getSecondaryDismissTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getPrimaryTaskOffsetTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                TASK_OFFSET_TRANSLATION_X, TASK_OFFSET_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getTaskResistanceTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                TASK_RESISTANCE_TRANSLATION_X, TASK_RESISTANCE_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getPrimaryNonGridTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                NON_GRID_TRANSLATION_X, NON_GRID_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getSecondaryNonGridTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                NON_GRID_TRANSLATION_X, NON_GRID_TRANSLATION_Y);
    }

    @Override
    public boolean hasOverlappingRendering() {
        // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
        return false;
    }

    public boolean isEndQuickswitchCuj() {
        return mEndQuickswitchCuj;
    }

    public void setEndQuickswitchCuj(boolean endQuickswitchCuj) {
        mEndQuickswitchCuj = endQuickswitchCuj;
    }

    private int getExpectedViewHeight(View view) {
        int expectedHeight;
        int h = view.getLayoutParams().height;
        if (h > 0) {
            expectedHeight = h;
        } else {
            int m = MeasureSpec.makeMeasureSpec(MeasureSpec.EXACTLY - 1, MeasureSpec.AT_MOST);
            view.measure(m, m);
            expectedHeight = view.getMeasuredHeight();
        }
        return expectedHeight;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.addAction(
                new AccessibilityNodeInfo.AccessibilityAction(R.string.accessibility_close,
                        getContext().getText(R.string.accessibility_close)));

        final Context context = getContext();
        for (TaskIdAttributeContainer taskContainer : mTaskIdAttributeContainer) {
            if (taskContainer == null) {
                continue;
            }
            for (SystemShortcut s : TaskOverlayFactory.getEnabledShortcuts(this,
                    taskContainer)) {
                info.addAction(s.createAccessibilityAction(context));
            }
        }

        if (mDigitalWellBeingToast.hasLimit()) {
            info.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(
                            R.string.accessibility_app_usage_settings,
                            getContext().getText(R.string.accessibility_app_usage_settings)));
        }

        final RecentsView recentsView = getRecentsView();
        final AccessibilityNodeInfo.CollectionItemInfo itemInfo =
                AccessibilityNodeInfo.CollectionItemInfo.obtain(
                        0, 1, recentsView.getTaskViewCount() - recentsView.indexOfChild(this) - 1,
                        1, false);
        info.setCollectionItemInfo(itemInfo);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.string.accessibility_close) {
            getRecentsView().dismissTask(this, true /*animateTaskView*/,
                    true /*removeTask*/);
            return true;
        }

        if (action == R.string.accessibility_app_usage_settings) {
            mDigitalWellBeingToast.openAppUsageSettings(this);
            return true;
        }

        for (TaskIdAttributeContainer taskContainer : mTaskIdAttributeContainer) {
            if (taskContainer == null) {
                continue;
            }
            for (SystemShortcut s : TaskOverlayFactory.getEnabledShortcuts(this,
                    taskContainer)) {
                if (s.hasHandlerForAction(action)) {
                    s.onClick(this);
                    return true;
                }
            }
        }

        return super.performAccessibilityAction(action, arguments);
    }

    public RecentsView getRecentsView() {
        return (RecentsView) getParent();
    }

    PagedOrientationHandler getPagedOrientationHandler() {
        return getRecentsView().mOrientationState.getOrientationHandler();
    }

    private void notifyTaskLaunchFailed(String tag) {
        String msg = "Failed to launch task";
        if (mTask != null) {
            msg += " (task=" + mTask.key.baseIntent + " userId=" + mTask.key.userId + ")";
        }
        Log.w(tag, msg);
        Toast.makeText(getContext(), R.string.activity_not_available, LENGTH_SHORT).show();
    }

    /**
     * Hides the icon and shows insets when this TaskView is about to be shown fullscreen.
     *
     * @param progress: 0 = show icon and no insets; 1 = don't show icon and show full insets.
     */
    public void setFullscreenProgress(float progress) {
        progress = Utilities.boundToRange(progress, 0, 1);
        mFullscreenProgress = progress;
        mIconView.setVisibility(progress < 1 ? VISIBLE : INVISIBLE);
        mSnapshotView.getTaskOverlay().setFullscreenProgress(progress);

        // Animate icons and DWB banners in/out, except in QuickSwitch state, when tiles are
        // oversized and banner would look disproportionately large.
        if (mActivity.getStateManager().getState() != BACKGROUND_APP) {
            setIconsAndBannersTransitionProgress(progress, true);
        }

        updateSnapshotRadius();
    }

    protected void updateSnapshotRadius() {
        updateCurrentFullscreenParams(mSnapshotView.getPreviewPositionHelper());
        mSnapshotView.setFullscreenParams(mCurrentFullscreenParams);
    }

    void updateCurrentFullscreenParams(PreviewPositionHelper previewPositionHelper) {
        if (getRecentsView() == null) {
            return;
        }
        mCurrentFullscreenParams.setProgress(mFullscreenProgress, getRecentsView().getScaleX(),
                getScaleX(), getWidth(), mActivity.getDeviceProfile(), previewPositionHelper);
    }

    /**
     * Updates TaskView scaling and translation required to support variable width if enabled, while
     * ensuring TaskView fits into screen in fullscreen.
     */
    void updateTaskSize() {
        ViewGroup.LayoutParams params = getLayoutParams();
        float nonGridScale;
        float boxTranslationY;
        int expectedWidth;
        int expectedHeight;
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        if (deviceProfile.isTablet) {
            final int thumbnailPadding = deviceProfile.overviewTaskThumbnailTopMarginPx;
            final Rect lastComputedTaskSize = getRecentsView().getLastComputedTaskSize();
            final int taskWidth = lastComputedTaskSize.width();
            final int taskHeight = lastComputedTaskSize.height();

            int boxWidth;
            int boxHeight;
            boolean isFocusedTask = isFocusedTask();
            if (isFocusedTask) {
                // Task will be focused and should use focused task size. Use focusTaskRatio
                // that is associated with the original orientation of the focused task.
                boxWidth = taskWidth;
                boxHeight = taskHeight;
            } else {
                // Otherwise task is in grid, and should use lastComputedGridTaskSize.
                Rect lastComputedGridTaskSize = getRecentsView().getLastComputedGridTaskSize();
                boxWidth = lastComputedGridTaskSize.width();
                boxHeight = lastComputedGridTaskSize.height();
            }

            // Bound width/height to the box size.
            expectedWidth = boxWidth;
            expectedHeight = boxHeight + thumbnailPadding;

            // Scale to to fit task Rect.
            nonGridScale = taskWidth / (float) boxWidth;

            // Align to top of task Rect.
            boxTranslationY = (expectedHeight - thumbnailPadding - taskHeight) / 2.0f;
        } else {
            nonGridScale = 1f;
            boxTranslationY = 0f;
            expectedWidth = ViewGroup.LayoutParams.MATCH_PARENT;
            expectedHeight = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        setNonGridScale(nonGridScale);
        setBoxTranslationY(boxTranslationY);
        if (params.width != expectedWidth || params.height != expectedHeight) {
            params.width = expectedWidth;
            params.height = expectedHeight;
            setLayoutParams(params);
        }
    }

    private float getGridTrans(float endTranslation) {
        float progress = GRID_INTERPOLATOR.getInterpolation(mGridProgress);
        return Utilities.mapRange(progress, 0, endTranslation);
    }

    private float getNonGridTrans(float endTranslation) {
        return endTranslation - getGridTrans(endTranslation);
    }

    public boolean isRunningTask() {
        if (getRecentsView() == null) {
            return false;
        }
        return this == getRecentsView().getRunningTaskView();
    }

    public boolean isFocusedTask() {
        if (getRecentsView() == null) {
            return false;
        }
        return this == getRecentsView().getFocusedTaskView();
    }

    public void setShowScreenshot(boolean showScreenshot) {
        mShowScreenshot = showScreenshot;
    }

    public boolean showScreenshot() {
        if (!isRunningTask()) {
            return true;
        }
        return mShowScreenshot;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        mSnapshotView.setOverlayEnabled(overlayEnabled);
    }

    public void initiateSplitSelect(SplitPositionOption splitPositionOption) {
        getRecentsView().initiateSplitSelect(this, splitPositionOption.stagePosition,
                getLogEventForPosition(splitPositionOption.stagePosition));
    }

    /**
     * Set a color tint on the snapshot and supporting views.
     */
    public void setColorTint(float amount, int tintColor) {
        mSnapshotView.setDimAlpha(amount);
        mIconView.setIconColorTint(tintColor, amount);
        mDigitalWellBeingToast.setBannerColorTint(tintColor, amount);
    }


    private int getRootViewDisplayId() {
        Display  display = getRootView().getDisplay();
        return display != null ? display.getDisplayId() : DEFAULT_DISPLAY;
    }

    /**
     *     Sets visibility for the thumbnail and associated elements (DWB banners and action chips).
     *     IconView is unaffected.
     */
    void setThumbnailVisibility(int visibility) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != mIconView) {
                child.setVisibility(visibility);
            }
        }
    }

    /**
     * We update and subsequently draw these in {@link #setFullscreenProgress(float)}.
     */
    public static class FullscreenDrawParams {

        private final float mCornerRadius;
        private final float mWindowCornerRadius;

        public RectF mCurrentDrawnInsets = new RectF();
        public float mCurrentDrawnCornerRadius;
        /** The current scale we apply to the thumbnail to adjust for new left/right insets. */
        public float mScale = 1;

        private boolean mIsTaskbarTransient;

        public FullscreenDrawParams(Context context) {
            mCornerRadius = TaskCornerRadius.get(context);
            mWindowCornerRadius = QuickStepContract.getWindowCornerRadius(context);
            mIsTaskbarTransient = DisplayController.isTransientTaskbar(context);

            mCurrentDrawnCornerRadius = mCornerRadius;
        }

        /**
         * Sets the progress in range [0, 1]
         */
        public void setProgress(float fullscreenProgress, float parentScale, float taskViewScale,
                int previewWidth, DeviceProfile dp, PreviewPositionHelper pph) {
            RectF insets = getInsetsToDrawInFullscreen(pph, dp, mIsTaskbarTransient);

            float currentInsetsLeft = insets.left * fullscreenProgress;
            float currentInsetsTop = insets.top * fullscreenProgress;
            float currentInsetsRight = insets.right * fullscreenProgress;
            float currentInsetsBottom = insets.bottom * fullscreenProgress;
            mCurrentDrawnInsets.set(
                    currentInsetsLeft, currentInsetsTop, currentInsetsRight, currentInsetsBottom);

            mCurrentDrawnCornerRadius =
                    Utilities.mapRange(fullscreenProgress, mCornerRadius, mWindowCornerRadius)
                            / parentScale / taskViewScale;

            // We scaled the thumbnail to fit the content (excluding insets) within task view width.
            // Now that we are drawing left/right insets again, we need to scale down to fit them.
            if (previewWidth > 0) {
                mScale = previewWidth / (previewWidth + currentInsetsLeft + currentInsetsRight);
            }
        }

        /**
         * Insets to used for clipping the thumbnail (in case it is drawing outside its own space)
         */
        private static RectF getInsetsToDrawInFullscreen(PreviewPositionHelper pph,
                DeviceProfile dp, boolean isTaskbarTransient) {
            if (dp.isTaskbarPresent && isTaskbarTransient) {
                return pph.getClippedInsets();
            }
            return dp.isTaskbarPresent && !dp.isTaskbarPresentInApps
                    ? pph.getClippedInsets() : EMPTY_RECT_F;
        }
    }

    public class TaskIdAttributeContainer {
        private final TaskThumbnailView mThumbnailView;
        private final Task mTask;
        private final IconView mIconView;
        /** Defaults to STAGE_POSITION_UNDEFINED if in not a split screen task view */
        private @SplitConfigurationOptions.StagePosition int mStagePosition;
        @IdRes
        private final int mA11yNodeId;

        public TaskIdAttributeContainer(Task task, TaskThumbnailView thumbnailView,
                IconView iconView, int stagePosition) {
            this.mTask = task;
            this.mThumbnailView = thumbnailView;
            this.mIconView = iconView;
            this.mStagePosition = stagePosition;
            this.mA11yNodeId = (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) ?
                    R.id.split_bottomRight_appInfo : R.id.split_topLeft_appInfo;
        }

        public TaskThumbnailView getThumbnailView() {
            return mThumbnailView;
        }

        public Task getTask() {
            return mTask;
        }

        public WorkspaceItemInfo getItemInfo() {
            return TaskView.this.getItemInfo(mTask);
        }

        public TaskView getTaskView() {
            return TaskView.this;
        }

        public IconView getIconView() {
            return mIconView;
        }

        public int getStagePosition() {
            return mStagePosition;
        }

        void setStagePosition(@SplitConfigurationOptions.StagePosition int stagePosition) {
            this.mStagePosition = stagePosition;
        }

        public int getA11yNodeId() {
            return mA11yNodeId;
        }
    }
}
