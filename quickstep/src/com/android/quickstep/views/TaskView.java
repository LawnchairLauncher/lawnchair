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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.widget.Toast.LENGTH_SHORT;

import static com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.Flags.enableGridOnlyOverview;
import static com.android.launcher3.Flags.enableOverviewIconMenu;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;
import static com.android.launcher3.testing.shared.TestProtocol.SUCCESSFUL_GESTURE_MISMATCH_EVENTS;
import static com.android.launcher3.testing.shared.TestProtocol.testLogD;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;
import static com.android.quickstep.TaskOverlayFactory.getEnabledShortcuts;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.EXPECTING_TASK_APPEARED;
import static com.android.quickstep.util.BorderAnimator.DEFAULT_BORDER_COLOR;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.IdRes;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.app.animation.Interpolators;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.launcher3.util.ViewPool.Reusable;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.TaskIconCache;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.BorderAnimator;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.quickstep.util.TaskRemovedDuringLaunchListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;

import kotlin.Unit;

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

    public static final int FLAG_UPDATE_ICON = 1;
    public static final int FLAG_UPDATE_THUMBNAIL = FLAG_UPDATE_ICON << 1;
    public static final int FLAG_UPDATE_CORNER_RADIUS = FLAG_UPDATE_THUMBNAIL << 1;

    public static final int FLAG_UPDATE_ALL = FLAG_UPDATE_ICON | FLAG_UPDATE_THUMBNAIL
            | FLAG_UPDATE_CORNER_RADIUS;

    /**
     * Used in conjunction with {@link #onTaskListVisibilityChanged(boolean, int)}, providing more
     * granularity on which components of this task require an update
     */
    @Retention(SOURCE)
    @IntDef({FLAG_UPDATE_ALL, FLAG_UPDATE_ICON, FLAG_UPDATE_THUMBNAIL, FLAG_UPDATE_CORNER_RADIUS})
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
    protected TaskViewIcon mIconView;
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
    private float mNonGridPivotTranslationX;
    // Used when in SplitScreenSelectState
    private float mSplitSelectTranslationY;
    private float mSplitSelectTranslationX;

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
    private boolean mBorderEnabled;

    // The current background requests to load the task thumbnail and icon
    @Nullable
    private CancellableTask mThumbnailLoadRequest;
    @Nullable
    private CancellableTask mIconLoadRequest;

    private boolean mEndQuickswitchCuj;

    private final float[] mIconCenterCoords = new float[2];

    protected final PointF mLastTouchDownPosition = new PointF();

    private boolean mIsClickableAsLiveTile = true;

    @Nullable private final BorderAnimator mFocusBorderAnimator;

    @Nullable private final BorderAnimator mHoverBorderAnimator;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr, defStyleRes, null, null);
    }

    @VisibleForTesting
    public TaskView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes, BorderAnimator focusBorderAnimator,
            BorderAnimator hoverBorderAnimator) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mActivity = StatefulActivity.fromContext(context);
        setOnClickListener(this::onClick);

        mCurrentFullscreenParams = new FullscreenDrawParams(context);
        mDigitalWellBeingToast = new DigitalWellBeingToast(mActivity, this);

        boolean keyboardFocusHighlightEnabled = FeatureFlags.ENABLE_KEYBOARD_QUICK_SWITCH.get();
        boolean cursorHoverStatesEnabled = enableCursorHoverStates();

        setWillNotDraw(!keyboardFocusHighlightEnabled && !cursorHoverStatesEnabled);

        TypedArray styledAttrs = context.obtainStyledAttributes(
                attrs, R.styleable.TaskView, defStyleAttr, defStyleRes);

        if (focusBorderAnimator != null) {
            mFocusBorderAnimator = focusBorderAnimator;
        } else {
            mFocusBorderAnimator = keyboardFocusHighlightEnabled
                    ? BorderAnimator.createSimpleBorderAnimator(
                    /* borderRadiusPx= */ (int) mCurrentFullscreenParams.mCornerRadius,
                    /* borderWidthPx= */ context.getResources().getDimensionPixelSize(
                            R.dimen.keyboard_quick_switch_border_width),
                    /* boundsBuilder= */ this::updateBorderBounds,
                    /* targetView= */ this,
                    /* borderColor= */ styledAttrs.getColor(
                            R.styleable.TaskView_focusBorderColor, DEFAULT_BORDER_COLOR))
                    : null;
        }

        if (hoverBorderAnimator != null) {
            mHoverBorderAnimator = hoverBorderAnimator;
        } else {
            mHoverBorderAnimator = cursorHoverStatesEnabled
                    ? BorderAnimator.createSimpleBorderAnimator(
                    /* borderRadiusPx= */ (int) mCurrentFullscreenParams.mCornerRadius,
                    /* borderWidthPx= */ context.getResources().getDimensionPixelSize(
                            R.dimen.task_hover_border_width),
                    /* boundsBuilder= */ this::updateBorderBounds,
                    /* targetView= */ this,
                    /* borderColor= */ styledAttrs.getColor(
                            R.styleable.TaskView_hoverBorderColor, DEFAULT_BORDER_COLOR))
                    : null;
        }
        styledAttrs.recycle();
    }

    protected Unit updateBorderBounds(@NonNull Rect bounds) {
        bounds.set(mSnapshotView.getLeft() + Math.round(mSnapshotView.getTranslationX()),
                mSnapshotView.getTop() + Math.round(mSnapshotView.getTranslationY()),
                mSnapshotView.getRight() + Math.round(mSnapshotView.getTranslationX()),
                mSnapshotView.getBottom() + Math.round(mSnapshotView.getTranslationY()));
        return Unit.INSTANCE;
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
        if (Flags.privateSpaceRestrictAccessibilityDrag()) {
            if (UserCache.getInstance(getContext()).getUserInfo(componentKey.user).isPrivate()) {
                stubInfo.runtimeStatusFlags |= FLAG_NOT_PINNABLE;
            }
        }
        return stubInfo;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView = findViewById(R.id.snapshot);
        ViewStub iconViewStub = findViewById(R.id.icon);
        if (enableOverviewIconMenu()) {
            iconViewStub.setLayoutResource(R.layout.icon_app_chip_view);
        } else {
            iconViewStub.setLayoutResource(R.layout.icon_view);
        }
        mIconView = (TaskViewIcon) iconViewStub.inflate();
        mIconTouchDelegate = new TransformingTouchDelegate(mIconView.asView());
    }

    @Override
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (mFocusBorderAnimator != null && mBorderEnabled) {
            mFocusBorderAnimator.setBorderVisibility(gainFocus, /* animated= */ true);
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (mHoverBorderAnimator != null && mBorderEnabled) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    mHoverBorderAnimator.setBorderVisibility(/* visible= */ true, /* animated= */
                            true);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    mHoverBorderAnimator.setBorderVisibility(/* visible= */ false, /* animated= */
                            true);
                    break;
                default:
                    break;
            }
        }
        return super.onHoverEvent(event);
    }

    /**
     * Enable or disable showing border on hover and focus change
     */
    public void setBorderEnabled(boolean enabled) {
        mBorderEnabled = enabled;
        // Set the animation correctly in case it misses the hover/focus event during state
        // transition
        if (mHoverBorderAnimator != null) {
            mHoverBorderAnimator.setBorderVisibility(/* visible= */
                    enabled && isHovered(), /* animated= */ true);
        }

        if (mFocusBorderAnimator != null) {
            mFocusBorderAnimator.setBorderVisibility(/* visible= */
                    enabled && isFocused(), /* animated= */true);
        }
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent event) {
        if (enableCursorHoverStates()) {
            // avoid triggering hover event on child elements which would cause HOVER_EXIT for this
            // task view
            return true;
        } else {
            return super.onInterceptHoverEvent(event);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        // Draw border first so any child views outside of the thumbnail bounds are drawn above it.
        if (mFocusBorderAnimator != null) {
            mFocusBorderAnimator.drawBorder(canvas);
        }
        if (mHoverBorderAnimator != null) {
            mHoverBorderAnimator.drawBorder(canvas);
        }
        super.draw(canvas);
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

    protected void computeAndSetIconTouchDelegate(TaskViewIcon view, float[] tempCenterCoords,
            TransformingTouchDelegate transformingTouchDelegate) {
        if (view == null) {
            return;
        }
        float viewHalfWidth = view.getWidth() / 2f;
        float viewHalfHeight = view.getHeight() / 2f;
        tempCenterCoords[0] = viewHalfWidth;
        tempCenterCoords[1] = viewHalfHeight;
        getDescendantCoordRelativeToAncestor(view.asView(), mActivity.getDragLayer(),
                tempCenterCoords, false);
        transformingTouchDelegate.setBounds(
                (int) (tempCenterCoords[0] - viewHalfWidth),
                (int) (tempCenterCoords[1] - viewHalfHeight),
                (int) (tempCenterCoords[0] + viewHalfWidth),
                (int) (tempCenterCoords[1] + viewHalfHeight));
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
        mIconView.setModalAlpha(1 - modalness);
        mDigitalWellBeingToast.updateBannerOffset(modalness);
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
        mTaskIdAttributeContainer[0] = new TaskIdAttributeContainer(task, mSnapshotView, mIconView,
                STAGE_POSITION_UNDEFINED);
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
        return Arrays.copyOf(mTaskIdContainer, mTaskIdContainer.length);
    }

    public boolean containsMultipleTasks() {
        return mTaskIdContainer[1] != -1;
    }

    /**
     * Returns the TaskIdAttributeContainer corresponding to a given taskId, or null if the TaskView
     * does not contain a Task with that ID.
     */
    @Nullable
    public TaskIdAttributeContainer getTaskAttributesById(int taskId) {
        for (TaskIdAttributeContainer attributes : mTaskIdAttributeContainer) {
            if (attributes.getTask().key.id == taskId) {
                return attributes;
            }
        }

        return null;
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

    public TaskViewIcon getIconView() {
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
        // Disable taps for split selection animation unless we have multiple tasks
        boolean disableTapsForSplitSelect =
                splitSelectStateController.isSplitSelectActive()
                        && splitSelectStateController.getInitialTaskId() == getTask().key.id
                        && !containsMultipleTasks();
        if (disableTapsForSplitSelect) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mLastTouchDownPosition.set(ev.getX(), ev.getY());
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * @return taskId that split selection was initiated with,
     *         {@link ActivityTaskManager#INVALID_TASK_ID} if no tasks in this TaskView are part of
     *         split selection
     */
    protected int getThisTaskCurrentlyInSplitSelection() {
        SplitSelectStateController splitSelectController =
                getRecentsView().getSplitSelectController();
        int initSplitTaskId = INVALID_TASK_ID;
        for (TaskIdAttributeContainer container : getTaskIdAttributeContainers()) {
            int taskId = container.getTask().key.id;
            if (taskId == splitSelectController.getInitialTaskId()) {
                initSplitTaskId = taskId;
                break;
            }
        }
        return initSplitTaskId;
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
                    container.getThumbnailView().getThumbnail(), /* intent */ null,
                    /* user */ null, container.getItemInfo());
        }
        return false;
    }

    /**
     * Returns the task index of the last selected child task (0 or 1).
     * If we contain multiple tasks and this TaskView is used as part of split selection, the
     * selected child task index will be that of the remaining task.
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
            testLogD(SUCCESSFUL_GESTURE_MISMATCH_EVENTS,
                    "TaskView.launchTaskAnimated: startActivityFromRecentsAsync");
            TestLogging.recordEvent(
                    TestProtocol.SEQUENCE_MAIN, "startActivityFromRecentsAsync", mTask);
            ActivityOptionsWrapper opts =  mActivity.getActivityLaunchOptions(this, null);
            opts.options.setLaunchDisplayId(
                    getDisplay() == null ? DEFAULT_DISPLAY : getDisplay().getDisplayId());
            if (ActivityManagerWrapper.getInstance()
                    .startActivityFromRecents(mTask.key, opts.options)) {
                ActiveGestureLog.INSTANCE.trackEvent(EXPECTING_TASK_APPEARED);
                RecentsView recentsView = getRecentsView();
                if (recentsView.getRunningTaskViewId() != -1) {
                    recentsView.onTaskLaunchedInLiveTileMode();

                    // Return a fresh callback in the live tile case, so that it's not accidentally
                    // triggered by QuickstepTransitionManager.AppLaunchAnimationRunner.
                    RunnableList callbackList = new RunnableList();
                    recentsView.addSideTaskLaunchCallback(callbackList);
                    return callbackList;
                }
                if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
                    // If the recents transition is running (ie. in live tile mode), then the start
                    // of a new task will merge into the existing transition and it currently will
                    // not be run independently, so we need to rely on the onTaskAppeared() call
                    // for the new task to trigger the side launch callback to flush this runnable
                    // list (which is usually flushed when the app launch animation finishes)
                    recentsView.addSideTaskLaunchCallback(opts.onEndCallback);
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
        launchTask(callback, false /* isQuickswitch */);
    }

    /**
     * Starts the task associated with this view without any animation
     */
    public void launchTask(@NonNull Consumer<Boolean> callback, boolean isQuickswitch) {
        if (mTask != null) {
            testLogD(SUCCESSFUL_GESTURE_MISMATCH_EVENTS,
                    "TaskView.launchTask: startActivityFromRecentsAsync");
            TestLogging.recordEvent(
                    TestProtocol.SEQUENCE_MAIN, "startActivityFromRecentsAsync", mTask);

            final TaskRemovedDuringLaunchListener
                    failureListener = new TaskRemovedDuringLaunchListener();
            if (isQuickswitch) {
                // We only listen for failures to launch in quickswitch because the during this
                // gesture launcher is in the background state, vs other launches which are in
                // the actual overview state
                failureListener.register(mActivity, mTask.key.id, () -> {
                    notifyTaskLaunchFailed(TAG);
                    RecentsView rv = getRecentsView();
                    if (rv != null) {
                        // Disable animations for now, as it is an edge case and the app usually
                        // covers launcher and also any state transition animation also gets
                        // clobbered by QuickstepTransitionManager.createWallpaperOpenAnimations
                        // when launcher shows again
                        rv.startHome(false /* animated */);
                        if (rv.mSizeStrategy.getTaskbarController() != null) {
                            // LauncherTaskbarUIController depends on the launcher state when
                            // checking whether to handle resume, but that can come in before
                            // startHome() changes the state, so force-refresh here to ensure the
                            // taskbar is updated
                            rv.mSizeStrategy.getTaskbarController().refreshResumedState();
                        }
                    }
                });
            }
            // Indicate success once the system has indicated that the transition has started
            ActivityOptions opts = ActivityOptions.makeCustomTaskAnimation(getContext(), 0, 0,
                    MAIN_EXECUTOR.getHandler(),
                    elapsedRealTime -> {
                        callback.accept(true);
                    },
                    elapsedRealTime -> {
                        failureListener.onTransitionFinished();
                    });
            opts.setLaunchDisplayId(
                    getDisplay() == null ? DEFAULT_DISPLAY : getDisplay().getDisplayId());
            if (isQuickswitch) {
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
                RemoteAnimationTarget[] apps = Arrays.stream(remoteTargetHandles)
                        .flatMap(handle -> Stream.of(
                                handle.getTransformParams().getTargetSet().apps))
                        .toArray(RemoteAnimationTarget[]::new);
                RemoteAnimationTarget[] wallpapers = Arrays.stream(remoteTargetHandles)
                        .flatMap(handle -> Stream.of(
                                handle.getTransformParams().getTargetSet().wallpapers))
                        .toArray(RemoteAnimationTarget[]::new);
                targets = new RemoteAnimationTargets(apps, wallpapers,
                        remoteTargetHandles[0].getTransformParams().getTargetSet().nonApps,
                        remoteTargetHandles[0].getTransformParams().getTargetSet().targetMode);
            }
            if (targets == null) {
                // If the recents animation is cancelled somehow between the parent if block and
                // here, try to launch the task as a non live tile task.
                testLogD(SUCCESSFUL_GESTURE_MISMATCH_EVENTS,
                        "TaskView.java - launchTasks: recents animation is cancelled");
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
                public void onAnimationEnd(Animator animator) {
                    if (mTask != null && mTask.key.displayId != getRootViewDisplayId()) {
                        testLogD(SUCCESSFUL_GESTURE_MISMATCH_EVENTS,
                                "TaskView.java - launchTasks: onAnimationEnd");
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
            testLogD(SUCCESSFUL_GESTURE_MISMATCH_EVENTS,
                    "TaskView.java - launchTasks: isRunningTask=" + isRunningTask() + "||"
                            + "remoteTargetHandles == null?" + (remoteTargetHandles == null));
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
                            if (enableOverviewIconMenu()) {
                                setText(mIconView, task.title);
                            }
                            mDigitalWellBeingToast.initialize(task);
                        });
            }
            if (needsUpdate(changes, FLAG_UPDATE_CORNER_RADIUS)) {
                mCurrentFullscreenParams.updateCornerRadius(getContext());
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
                if (enableOverviewIconMenu()) {
                    setText(mIconView, null);
                }
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

    private boolean showTaskMenu(TaskViewIcon iconView) {
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

    protected boolean showTaskMenuWithContainer(TaskViewIcon iconView) {
        TaskIdAttributeContainer menuContainer =
                mTaskIdAttributeContainer[iconView == mIconView ? 0 : 1];
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (enableOverviewIconMenu() && iconView instanceof IconAppChipView) {
            ((IconAppChipView) iconView).revealAnim(/* isRevealing= */ true);
            return TaskMenuView.showForTask(menuContainer,
                    () -> ((IconAppChipView) iconView).revealAnim(/* isRevealing= */ false));
        } else if (dp.isTablet) {
            int alignedOptionIndex = 0;
            if (getRecentsView().isOnGridBottomRow(menuContainer.getTaskView()) && dp.isLandscape) {
                if (Flags.enableGridOnlyOverview()) {
                    // With no focused task, there is less available space below the tasks, so align
                    // the arrow to the third option in the menu.
                    alignedOptionIndex = 2;
                } else  {
                    // Bottom row of landscape grid aligns arrow to second option to avoid clipping
                    alignedOptionIndex = 1;
                }
            }
            return TaskMenuViewWithArrow.Companion.showForTask(menuContainer,
                    alignedOptionIndex);
        } else {
            return TaskMenuView.showForTask(menuContainer);
        }
    }

    protected void setIcon(TaskViewIcon iconView, @Nullable Drawable icon) {
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

    protected void setText(TaskViewIcon iconView, CharSequence text) {
        iconView.setText(text);
    }

    public void setOrientationState(RecentsOrientedState orientationState) {
        mIconView.setIconOrientation(orientationState, isGridTask());
        setThumbnailOrientation(orientationState);
    }

    protected void setThumbnailOrientation(RecentsOrientedState orientationState) {
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        int thumbnailTopMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;

        // TODO(b/271468547), we should default to setting trasnlations only on the snapshot instead
        //  of a hybrid of both margins and translations
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

    /** Whether this task view represents the desktop */
    public boolean isDesktopTask() {
        return false;
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
        mIconView.setContentAlpha(scale);
        mDigitalWellBeingToast.updateBannerOffset(1f - scale);
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
        mNonGridTranslationX = mGridTranslationX =
                mGridTranslationY = mBoxTranslationY = mNonGridPivotTranslationX = 0f;
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
        mSnapshotView.resetViewTransforms();
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
        mBorderEnabled = false;
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
        SYSTEM_GESTURE_EXCLUSION_RECT.get(0).set(0, 0, getWidth(), getHeight());
        setSystemGestureExclusionRects(SYSTEM_GESTURE_EXCLUSION_RECT);
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
        scale *= Utilities.mapRange(mGridProgress, mNonGridScale, 1f);
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

    protected void refreshTaskThumbnailSplash() {
        mSnapshotView.refreshSplashView();
    }

    private void setSplitSelectTranslationX(float x) {
        mSplitSelectTranslationX = x;
        applyTranslationX();
    }

    private void setSplitSelectTranslationY(float y) {
        mSplitSelectTranslationY = y;
        applyTranslationY();
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

    public float getNonGridTranslationX() {
        return mNonGridTranslationX;
    }

    /**
     * Updates X coordinate of non-grid translation.
     */
    public void setNonGridTranslationX(float nonGridTranslationX) {
        mNonGridTranslationX = nonGridTranslationX;
        applyTranslationX();
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

    /**
     * Set translation X for non-grid pivot
     */
    public void setNonGridPivotTranslationX(float nonGridPivotTranslationX) {
        mNonGridPivotTranslationX = nonGridPivotTranslationX;
        applyTranslationX();
    }

    public float getScrollAdjustment(boolean gridEnabled) {
        float scrollAdjustment = 0;
        if (gridEnabled) {
            scrollAdjustment += mGridTranslationX;
        } else {
            scrollAdjustment += getNonGridTranslationX();
        }
        return scrollAdjustment;
    }

    public float getOffsetAdjustment(boolean gridEnabled) {
        return getScrollAdjustment(gridEnabled);
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
        return getNonGridTrans(mNonGridTranslationX) + getGridTrans(mGridTranslationX)
                + getNonGridTrans(mNonGridPivotTranslationX);
    }

    /**
     * Returns addition of translationY that is persistent (e.g. fullscreen and grid), and does not
     * change according to a temporary state (e.g. task offset).
     */
    public float getPersistentTranslationY() {
        return mBoxTranslationY + getGridTrans(mGridTranslationY);
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

    public FloatProperty<TaskView> getSecondaryTaskOffsetTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                TASK_OFFSET_TRANSLATION_X, TASK_OFFSET_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getTaskResistanceTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                TASK_RESISTANCE_TRANSLATION_X, TASK_RESISTANCE_TRANSLATION_Y);
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
            for (SystemShortcut s : TraceHelper.allowIpcs(
                    "TV.a11yInfo", () -> getEnabledShortcuts(this, taskContainer))) {
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
            for (SystemShortcut s : getEnabledShortcuts(this,
                    taskContainer)) {
                if (s.hasHandlerForAction(action)) {
                    s.onClick(this);
                    return true;
                }
            }
        }

        return super.performAccessibilityAction(action, arguments);
    }

    @Nullable
    public RecentsView getRecentsView() {
        return (RecentsView) getParent();
    }

    RecentsPagedOrientationHandler getPagedOrientationHandler() {
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
        updateCurrentFullscreenParams();
        mSnapshotView.setFullscreenParams(mCurrentFullscreenParams);
    }

    void updateCurrentFullscreenParams() {
        updateFullscreenParams(mCurrentFullscreenParams);
    }

    protected void updateFullscreenParams(TaskView.FullscreenDrawParams fullscreenParams) {
        if (getRecentsView() == null) {
            return;
        }
        fullscreenParams.setProgress(mFullscreenProgress, getRecentsView().getScaleX(),
                getScaleX());
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
        final int thumbnailPadding = deviceProfile.overviewTaskThumbnailTopMarginPx;
        final Rect lastComputedTaskSize = getRecentsView().getLastComputedTaskSize();
        final int taskWidth = lastComputedTaskSize.width();
        final int taskHeight = lastComputedTaskSize.height();
        if (deviceProfile.isTablet) {
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
            if (enableGridOnlyOverview()) {
                final Rect lastComputedCarouselTaskSize =
                        getRecentsView().getLastComputedCarouselTaskSize();
                nonGridScale = lastComputedCarouselTaskSize.width() / (float) taskWidth;
            } else {
                nonGridScale = taskWidth / (float) boxWidth;
            }

            // Align to top of task Rect.
            boxTranslationY = (expectedHeight - thumbnailPadding - taskHeight) / 2.0f;
        } else {
            nonGridScale = 1f;
            boxTranslationY = 0f;
            expectedWidth = enableOverviewIconMenu() ? taskWidth : LayoutParams.MATCH_PARENT;
            expectedHeight = enableOverviewIconMenu()
                    ? taskHeight + thumbnailPadding
                    : LayoutParams.MATCH_PARENT;
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
        return Utilities.mapRange(mGridProgress, 0, endTranslation);
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
     *  Sets visibility for the thumbnail and associated elements (DWB banners and action chips).
     *  IconView is unaffected.
     *
     * @param taskId is only used when setting visibility to a non-{@link View#VISIBLE} value
     */
    void setThumbnailVisibility(int visibility, int taskId) {
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

        private float mCornerRadius;
        private float mWindowCornerRadius;

        public float mCurrentDrawnCornerRadius;

        public FullscreenDrawParams(Context context) {
            updateCornerRadius(context);
        }

        /** Recomputes the start and end corner radius for the given Context. */
        public void updateCornerRadius(Context context) {
            mCornerRadius = computeTaskCornerRadius(context);
            mWindowCornerRadius = computeWindowCornerRadius(context);
        }

        @VisibleForTesting
        public float computeTaskCornerRadius(Context context) {
            return TaskCornerRadius.get(context);
        }

        @VisibleForTesting
        public float computeWindowCornerRadius(Context context) {
            return QuickStepContract.getWindowCornerRadius(context);
        }

        /**
         * Sets the progress in range [0, 1]
         */
        public void setProgress(float fullscreenProgress, float parentScale, float taskViewScale) {
            mCurrentDrawnCornerRadius =
                    Utilities.mapRange(fullscreenProgress, mCornerRadius, mWindowCornerRadius)
                            / parentScale / taskViewScale;
        }
    }

    public class TaskIdAttributeContainer {
        private final TaskThumbnailView mThumbnailView;
        private final Task mTask;
        private final TaskViewIcon mIconView;
        /** Defaults to STAGE_POSITION_UNDEFINED if in not a split screen task view */
        private @SplitConfigurationOptions.StagePosition int mStagePosition;
        @IdRes
        private final int mA11yNodeId;

        public TaskIdAttributeContainer(Task task, TaskThumbnailView thumbnailView,
                TaskViewIcon iconView, int stagePosition) {
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

        public TaskViewIcon getIconView() {
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
