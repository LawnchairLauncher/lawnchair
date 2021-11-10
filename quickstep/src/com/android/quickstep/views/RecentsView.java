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

import static android.view.Surface.ROTATION_0;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.AbstractFloatingView.TYPE_TASK_MENU;
import static com.android.launcher3.AbstractFloatingView.getTopOpenViewWithType;
import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.ACCEL_0_5;
import static com.android.launcher3.anim.Interpolators.ACCEL_0_75;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_CLEAR_ALL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_DISMISS_SWIPE_UP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.launcher3.touch.PagedOrientationHandler.CANVAS_TRANSLATE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NON_ZERO_ROTATION;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_RECENTS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_TASKS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.LocusId;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.OverScroller;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseActivity.MultiWindowModeChangedListener;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringProperty;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.ResourceBasedOverride.Overrides;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TranslateEdgeEffect;
import com.android.launcher3.util.ViewPool;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.BaseActivityInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RecentsModel.TaskVisualsChangeListener;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.ViewUtils;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SplitScreenBounds;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.systemui.plugins.ResourceProvider;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.pip.IPipAnimationListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A list of recent tasks.
 */
@TargetApi(Build.VERSION_CODES.R)
public abstract class RecentsView<ACTIVITY_TYPE extends StatefulActivity<STATE_TYPE>,
        STATE_TYPE extends BaseState<STATE_TYPE>> extends PagedView implements Insettable,
        TaskThumbnailCache.HighResLoadingState.HighResLoadingStateChangedCallback,
        TaskVisualsChangeListener, SplitScreenBounds.OnChangeListener {

    // TODO(b/184899234): We use this timeout to wait a fixed period after switching to the
    // screenshot when dismissing the current live task to ensure the app can try and get stopped.
    private static final int REMOVE_TASK_WAIT_FOR_APP_STOP_MS = 100;

    public static final FloatProperty<RecentsView> CONTENT_ALPHA =
            new FloatProperty<RecentsView>("contentAlpha") {
                @Override
                public void setValue(RecentsView view, float v) {
                    view.setContentAlpha(v);
                }

                @Override
                public Float get(RecentsView view) {
                    return view.getContentAlpha();
                }
            };

    public static final FloatProperty<RecentsView> FULLSCREEN_PROGRESS =
            new FloatProperty<RecentsView>("fullscreenProgress") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setFullscreenProgress(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mFullscreenProgress;
                }
            };

    public static final FloatProperty<RecentsView> TASK_MODALNESS =
            new FloatProperty<RecentsView>("taskModalness") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskModalness(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskModalness;
                }
            };

    public static final FloatProperty<RecentsView> ADJACENT_PAGE_HORIZONTAL_OFFSET =
            new FloatProperty<RecentsView>("adjacentPageHorizontalOffset") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    if (recentsView.mAdjacentPageHorizontalOffset != v) {
                        recentsView.mAdjacentPageHorizontalOffset = v;
                        recentsView.updatePageOffsets();
                    }
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mAdjacentPageHorizontalOffset;
                }
            };

    /**
     * Can be used to tint the color of the RecentsView to simulate a scrim that can views
     * excluded from. Really should be a proper scrim.
     * TODO(b/187528071): Remove this and replace with a real scrim.
     */
    private static final FloatProperty<RecentsView> COLOR_TINT =
            new FloatProperty<RecentsView>("colorTint") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setColorTint(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.getColorTint();
                }
            };

    /**
     * Even though {@link TaskView} has distinct offsetTranslationX/Y and resistance property, they
     * are currently both used to apply secondary translation. Should their use cases change to be
     * more specific, we'd want to create a similar FloatProperty just for a TaskView's
     * offsetX/Y property
     */
    public static final FloatProperty<RecentsView> TASK_SECONDARY_TRANSLATION =
            new FloatProperty<RecentsView>("taskSecondaryTranslation") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskViewsResistanceTranslation(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskViewsSecondaryTranslation;
                }
            };

    /**
     * Even though {@link TaskView} has distinct offsetTranslationX/Y and resistance property, they
     * are currently both used to apply secondary translation. Should their use cases change to be
     * more specific, we'd want to create a similar FloatProperty just for a TaskView's
     * offsetX/Y property
     */
    public static final FloatProperty<RecentsView> TASK_PRIMARY_SPLIT_TRANSLATION =
            new FloatProperty<RecentsView>("taskPrimarySplitTranslation") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskViewsPrimarySplitTranslation(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskViewsPrimarySplitTranslation;
                }
            };

    public static final FloatProperty<RecentsView> TASK_SECONDARY_SPLIT_TRANSLATION =
            new FloatProperty<RecentsView>("taskSecondarySplitTranslation") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskViewsSecondarySplitTranslation(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskViewsSecondarySplitTranslation;
                }
            };

    /** Same as normal SCALE_PROPERTY, but also updates page offsets that depend on this scale. */
    public static final FloatProperty<RecentsView> RECENTS_SCALE_PROPERTY =
            new FloatProperty<RecentsView>("recentsScale") {
                @Override
                public void setValue(RecentsView view, float scale) {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                    view.mLastComputedTaskStartPushOutDistance = null;
                    view.mLastComputedTaskEndPushOutDistance = null;
                    view.mLiveTileTaskViewSimulator.recentsViewScale.value = scale;
                    view.setTaskViewsResistanceTranslation(view.mTaskViewsSecondaryTranslation);
                    view.updatePageOffsets();
                }

                @Override
                public Float get(RecentsView view) {
                    return view.getScaleX();
                }
            };

    public static final FloatProperty<RecentsView> RECENTS_GRID_PROGRESS =
            new FloatProperty<RecentsView>("recentsGrid") {
                @Override
                public void setValue(RecentsView view, float gridProgress) {
                    view.setGridProgress(gridProgress);
                }

                @Override
                public Float get(RecentsView view) {
                    return view.mGridProgress;
                }
            };

    // OverScroll constants
    private static final int OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION = 270;

    private static final int DISMISS_TASK_DURATION = 300;
    private static final int ADDITION_TASK_DURATION = 200;
    private static final float INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET = 0.55f;
    private static final float ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET = 0.05f;

    protected final RecentsOrientedState mOrientationState;
    protected final BaseActivityInterface<STATE_TYPE, ACTIVITY_TYPE> mSizeStrategy;
    protected RecentsAnimationController mRecentsAnimationController;
    protected SurfaceTransactionApplier mSyncTransactionApplier;
    protected int mTaskWidth;
    protected int mTaskHeight;
    protected final TransformParams mLiveTileParams = new TransformParams();
    protected final TaskViewSimulator mLiveTileTaskViewSimulator;
    protected final Rect mLastComputedTaskSize = new Rect();
    protected final Rect mLastComputedGridSize = new Rect();
    protected final Rect mLastComputedGridTaskSize = new Rect();
    // How much a task that is directly offscreen will be pushed out due to RecentsView scale/pivot.
    protected Float mLastComputedTaskStartPushOutDistance = null;
    protected Float mLastComputedTaskEndPushOutDistance = null;
    protected boolean mEnableDrawingLiveTile = false;
    protected final Rect mTempRect = new Rect();
    protected final RectF mTempRectF = new RectF();
    private final PointF mTempPointF = new PointF();
    private final float[] mTempFloat = new float[1];
    private final List<OnScrollChangedListener> mScrollListeners = new ArrayList<>();

    // The threshold at which we update the SystemUI flags when animating from the task into the app
    public static final float UPDATE_SYSUI_FLAGS_THRESHOLD = 0.85f;

    protected final ACTIVITY_TYPE mActivity;
    private final float mFastFlingVelocity;
    private final RecentsModel mModel;
    private final int mRowSpacing;
    private final int mGridSideMargin;
    private final ClearAllButton mClearAllButton;
    private final Rect mClearAllButtonDeadZoneRect = new Rect();
    private final Rect mTaskViewDeadZoneRect = new Rect();
    /**
     * Reflects if Recents is currently in the middle of a gesture
     */
    private boolean mGestureActive;

    // Keeps track of the previously known visible tasks for purposes of loading/unloading task data
    private final SparseBooleanArray mHasVisibleTaskData = new SparseBooleanArray();

    private final InvariantDeviceProfile mIdp;

    private final ViewPool<TaskView> mTaskViewPool;

    private final TaskOverlayFactory mTaskOverlayFactory;

    protected boolean mDisallowScrollToClearAll;
    private boolean mOverlayEnabled;
    protected boolean mFreezeViewVisibility;
    private boolean mOverviewGridEnabled;
    private boolean mOverviewFullscreenEnabled;

    private float mAdjacentPageHorizontalOffset = 0;
    protected float mTaskViewsSecondaryTranslation = 0;
    protected float mTaskViewsPrimarySplitTranslation = 0;
    protected float mTaskViewsSecondarySplitTranslation = 0;
    // Progress from 0 to 1 where 0 is a carousel and 1 is a 2 row grid.
    private float mGridProgress = 0;
    private final IntSet mTopRowIdSet = new IntSet();

    // The GestureEndTarget that is still in progress.
    protected GestureState.GestureEndTarget mCurrentGestureEndTarget;

    // TODO(b/187528071): Remove these and replace with a real scrim.
    private float mColorTint;
    private final int mTintingColor;
    private ObjectAnimator mTintingAnimator;

    private int mOverScrollShift = 0;

    /**
     * TODO: Call reloadIdNeeded in onTaskStackChanged.
     */
    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
            if (!mHandleTaskStackChanges) {
                return;
            }
            // Check this is for the right user
            if (!checkCurrentOrManagedUserId(userId, getContext())) {
                return;
            }

            // Remove the task immediately from the task list
            TaskView taskView = getTaskView(taskId);
            if (taskView != null) {
                removeView(taskView);
            }
        }

        @Override
        public void onActivityUnpinned() {
            if (!mHandleTaskStackChanges) {
                return;
            }

            reloadIfNeeded();
            enableLayoutTransitions();
        }

        @Override
        public void onTaskRemoved(int taskId) {
            if (!mHandleTaskStackChanges) {
                return;
            }

            TaskView taskView = getTaskView(taskId);
            if (taskView == null) {
                return;
            }
            Task.TaskKey taskKey = taskView.getTask().key;
            UI_HELPER_EXECUTOR.execute(new HandlerRunnable<>(
                    UI_HELPER_EXECUTOR.getHandler(),
                    () -> PackageManagerWrapper.getInstance()
                            .getActivityInfo(taskKey.getComponent(), taskKey.userId) == null,
                    MAIN_EXECUTOR,
                    apkRemoved -> {
                        if (apkRemoved) {
                            dismissTask(taskId);
                        } else {
                            mModel.isTaskRemoved(taskKey.id, taskRemoved -> {
                                if (taskRemoved) {
                                    dismissTask(taskId);
                                }
                            });
                        }
                    }));
        }
    };

    private final PinnedStackAnimationListener mIPipAnimationListener =
            new PinnedStackAnimationListener();
    private int mPipCornerRadius;

    // Used to keep track of the last requested task list id, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private int mTaskListChangeId = -1;

    // Only valid until the launcher state changes to NORMAL
    protected int mRunningTaskId = -1;
    protected boolean mRunningTaskTileHidden;
    private Task mTmpRunningTask;
    protected int mFocusedTaskId = -1;
    private float mFocusedTaskRatio;

    private boolean mRunningTaskIconScaledDown = false;
    private boolean mRunningTaskShowScreenshot = false;

    private boolean mOverviewStateEnabled;
    private boolean mHandleTaskStackChanges;
    private boolean mSwipeDownShouldLaunchApp;
    private boolean mTouchDownToStartHome;
    private final float mSquaredTouchSlop;
    private int mDownX;
    private int mDownY;

    private PendingAnimation mPendingAnimation;
    private LayoutTransition mLayoutTransition;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected float mContentAlpha = 1;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected float mFullscreenProgress = 0;
    /**
     * How modal is the current task to be displayed, 1 means the task is fully modal and no other
     * tasks are show. 0 means the task is displays in context in the list with other tasks.
     */
    @ViewDebug.ExportedProperty(category = "launcher")
    protected float mTaskModalness = 0;

    // Keeps track of task id whose visual state should not be reset
    private int mIgnoreResetTaskId = -1;

    // Variables for empty state
    private final Drawable mEmptyIcon;
    private final CharSequence mEmptyMessage;
    private final TextPaint mEmptyMessagePaint;
    private final Point mLastMeasureSize = new Point();
    private final int mEmptyMessagePadding;
    private boolean mShowEmptyMessage;
    private OnEmptyMessageUpdatedListener mOnEmptyMessageUpdatedListener;
    private Layout mEmptyTextLayout;

    /**
     * Placeholder view indicating where the first split screen selected app will be placed
     */
    private SplitPlaceholderView mSplitPlaceholderView;
    /**
     * The first task that split screen selection was initiated with. When split select state is
     * initialized, we create a
     * {@link #createTaskDismissAnimation(TaskView, boolean, boolean, long)} for this TaskView but
     * don't actually remove the task since the user might back out. As such, we also ensure this
     * View doesn't go back into the {@link #mTaskViewPool}, see {@link #onViewRemoved(View)}
     */
    private TaskView mSplitHiddenTaskView;
    /**
     * Keeps track of the index of the TaskView that split screen was initialized with so we know
     * where to insert it back into list of taskViews in case user backs out of entering split
     * screen.
     * NOTE: This index is the index while {@link #mSplitHiddenTaskView} was a child of recentsView,
     * this doesn't get adjusted to reflect the new child count after the taskView is dismissed/
     * removed from recentsView
     */
    private int mSplitHiddenTaskViewIndex;

    // Keeps track of the index where the first TaskView should be
    private int mTaskViewStartIndex = 0;
    private OverviewActionsView mActionsView;

    private MultiWindowModeChangedListener mMultiWindowModeChangedListener =
            new MultiWindowModeChangedListener() {
                @Override
                public void onMultiWindowModeChanged(boolean inMultiWindowMode) {
                    mOrientationState.setMultiWindowMode(inMultiWindowMode);
                    setLayoutRotation(mOrientationState.getTouchRotation(),
                            mOrientationState.getDisplayRotation());
                    updateChildTaskOrientations();
                    if (!inMultiWindowMode && mOverviewStateEnabled) {
                        // TODO: Re-enable layout transitions for addition of the unpinned task
                        reloadIfNeeded();
                    }
                }
            };

    private RunnableList mSideTaskLaunchCallback;

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr,
            BaseActivityInterface sizeStrategy) {
        super(context, attrs, defStyleAttr);
        setPageSpacing(getResources().getDimensionPixelSize(R.dimen.recents_page_spacing));
        setEnableFreeScroll(true);
        mSizeStrategy = sizeStrategy;
        mActivity = BaseActivity.fromContext(context);
        mOrientationState = new RecentsOrientedState(
                context, mSizeStrategy, this::animateRecentsRotationInPlace);
        final int rotation = mActivity.getDisplay().getRotation();
        mOrientationState.setRecentsRotation(rotation);

        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);
        mModel = RecentsModel.INSTANCE.get(context);
        mIdp = InvariantDeviceProfile.INSTANCE.get(context);

        mClearAllButton = (ClearAllButton) LayoutInflater.from(context)
                .inflate(R.layout.overview_clear_all_button, this, false);
        mClearAllButton.setOnClickListener(this::dismissAllTasks);
        mTaskViewPool = new ViewPool<>(context, this, R.layout.task, 20 /* max size */,
                10 /* initial size */);

        mIsRtl = mOrientationHandler.getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mRowSpacing = getResources().getDimensionPixelSize(R.dimen.overview_grid_row_spacing);
        mGridSideMargin = getResources().getDimensionPixelSize(R.dimen.overview_grid_side_margin);
        mSquaredTouchSlop = squaredTouchSlop(context);

        mEmptyIcon = context.getDrawable(R.drawable.ic_empty_recents);
        mEmptyIcon.setCallback(this);
        mEmptyMessage = context.getText(R.string.recents_empty_message);
        mEmptyMessagePaint = new TextPaint();
        mEmptyMessagePaint.setColor(Themes.getAttrColor(context, android.R.attr.textColorPrimary));
        mEmptyMessagePaint.setTextSize(getResources()
                .getDimension(R.dimen.recents_empty_message_text_size));
        mEmptyMessagePaint.setTypeface(Typeface.create(Themes.getDefaultBodyFont(context),
                Typeface.NORMAL));
        mEmptyMessagePaint.setAntiAlias(true);
        mEmptyMessagePadding = getResources()
                .getDimensionPixelSize(R.dimen.recents_empty_message_text_padding);
        setWillNotDraw(false);
        updateEmptyMessage();
        mOrientationHandler = mOrientationState.getOrientationHandler();

        mTaskOverlayFactory = Overrides.getObject(
                TaskOverlayFactory.class,
                context.getApplicationContext(),
                R.string.task_overlay_factory_class);

        // Initialize quickstep specific cache params here, as this is constructed only once
        mActivity.getViewCache().setCacheSize(R.layout.digital_wellbeing_toast, 5);

        mLiveTileTaskViewSimulator = new TaskViewSimulator(getContext(), getSizeStrategy(),
                true /* isForLiveTile */);
        mLiveTileTaskViewSimulator.recentsViewScale.value = 1;
        mLiveTileTaskViewSimulator.setOrientationState(mOrientationState);
        mLiveTileTaskViewSimulator.setDrawsBelowRecents(true);

        mTintingColor = getForegroundScrimDimColor(context);
    }

    public OverScroller getScroller() {
        return mScroller;
    }

    public boolean isRtl() {
        return mIsRtl;
    }

    @Override
    protected void initEdgeEffect() {
        mEdgeGlowLeft = new TranslateEdgeEffect(getContext());
        mEdgeGlowRight = new TranslateEdgeEffect(getContext());
    }

    @Override
    protected void drawEdgeEffect(Canvas canvas) {
        // Do not draw edge effect
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw overscroll
        if (mAllowOverScroll && (!mEdgeGlowRight.isFinished() || !mEdgeGlowLeft.isFinished())) {
            final int restoreCount = canvas.save();

            int primarySize = mOrientationHandler.getPrimaryValue(getWidth(), getHeight());
            int scroll = OverScroll.dampedScroll(getUndampedOverScrollShift(), primarySize);
            mOrientationHandler.set(canvas, CANVAS_TRANSLATE, scroll);

            if (mOverScrollShift != scroll) {
                mOverScrollShift = scroll;
                dispatchScrollChanged();
            }

            super.dispatchDraw(canvas);
            canvas.restoreToCount(restoreCount);
        } else {
            if (mOverScrollShift != 0) {
                mOverScrollShift = 0;
                dispatchScrollChanged();
            }
            super.dispatchDraw(canvas);
        }
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                && mLiveTileParams.getTargetSet() != null) {
            redrawLiveTile();
        }
    }

    private float getUndampedOverScrollShift() {
        final int width = getWidth();
        final int height = getHeight();
        int primarySize = mOrientationHandler.getPrimaryValue(width, height);
        int secondarySize = mOrientationHandler.getSecondaryValue(width, height);

        float effectiveShift = 0;
        if (!mEdgeGlowLeft.isFinished()) {
            mEdgeGlowLeft.setSize(secondarySize, primarySize);
            if (((TranslateEdgeEffect) mEdgeGlowLeft).getTranslationShift(mTempFloat)) {
                effectiveShift = mTempFloat[0];
                postInvalidateOnAnimation();
            }
        }
        if (!mEdgeGlowRight.isFinished()) {
            mEdgeGlowRight.setSize(secondarySize, primarySize);
            if (((TranslateEdgeEffect) mEdgeGlowRight).getTranslationShift(mTempFloat)) {
                effectiveShift -= mTempFloat[0];
                postInvalidateOnAnimation();
            }
        }

        return effectiveShift * primarySize;
    }

    /**
     * Returns the view shift due to overscroll
     */
    public int getOverScrollShift() {
        return mOverScrollShift;
    }

    @Override
    public Task onTaskThumbnailChanged(int taskId, ThumbnailData thumbnailData) {
        if (mHandleTaskStackChanges) {
            TaskView taskView = getTaskView(taskId);
            if (taskView != null) {
                Task task = taskView.getTask();
                taskView.getThumbnail().setThumbnail(task, thumbnailData);
                return task;
            }
        }
        return null;
    }

    @Override
    public void onTaskIconChanged(String pkg, UserHandle user) {
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView tv = getTaskViewAt(i);
            Task task = tv.getTask();
            if (task != null && task.key != null && pkg.equals(task.key.getPackageName())
                    && task.key.userId == user.getIdentifier()) {
                task.icon = null;
                if (tv.getIconView().getDrawable() != null) {
                    tv.onTaskListVisibilityChanged(true /* visible */);
                }
            }
        }
    }

    /**
     * Update the thumbnail of the task.
     * @param refreshNow Refresh immediately if it's true.
     */
    public TaskView updateThumbnail(int taskId, ThumbnailData thumbnailData, boolean refreshNow) {
        TaskView taskView = getTaskView(taskId);
        if (taskView != null) {
            taskView.getThumbnail().setThumbnail(taskView.getTask(), thumbnailData, refreshNow);
        }
        return taskView;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTaskStackListenerState();
    }

    public void init(OverviewActionsView actionsView, SplitPlaceholderView splitPlaceholderView) {
        mActionsView = actionsView;
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, getTaskViewCount() == 0);
        mSplitPlaceholderView = splitPlaceholderView;
    }

    public SplitPlaceholderView getSplitPlaceholder() {
        return mSplitPlaceholderView;
    }

    public boolean isSplitSelectionActive() {
        return mSplitPlaceholderView.getSplitController().isSplitSelectActive();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
        mModel.getThumbnailCache().getHighResLoadingState().addCallback(this);
        mActivity.addMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = new SurfaceTransactionApplier(this);
        mLiveTileParams.setSyncTransactionApplier(mSyncTransactionApplier);
        RecentsModel.INSTANCE.get(getContext()).addThumbnailChangeListener(this);
        mIPipAnimationListener.setActivityAndRecentsView(mActivity, this);
        SystemUiProxy.INSTANCE.get(getContext()).setPinnedStackAnimationListener(
                mIPipAnimationListener);
        mOrientationState.initListeners();
        SplitScreenBounds.INSTANCE.addOnChangeListener(this);
        mTaskOverlayFactory.initListeners();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateTaskStackListenerState();
        mModel.getThumbnailCache().getHighResLoadingState().removeCallback(this);
        mActivity.removeMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = null;
        mLiveTileParams.setSyncTransactionApplier(null);
        executeSideTaskLaunchCallback();
        RecentsModel.INSTANCE.get(getContext()).removeThumbnailChangeListener(this);
        SystemUiProxy.INSTANCE.get(getContext()).setPinnedStackAnimationListener(null);
        SplitScreenBounds.INSTANCE.removeOnChangeListener(this);
        mIPipAnimationListener.setActivityAndRecentsView(null, null);
        mOrientationState.destroyListeners();
        mTaskOverlayFactory.removeListeners();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);

        // Clear the task data for the removed child if it was visible unless it's the initial
        // taskview for entering split screen, we only pretend to dismiss the task
        if (child instanceof TaskView && child != mSplitHiddenTaskView) {
            TaskView taskView = (TaskView) child;
            mHasVisibleTaskData.delete(taskView.getTask().key.id);
            mTaskViewPool.recycle(taskView);
            mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, getTaskViewCount() == 0);
        }
        updateTaskStartIndex(child);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        child.setAlpha(mContentAlpha);
        // RecentsView is set to RTL in the constructor when system is using LTR. Here we set the
        // child direction back to match system settings.
        child.setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        updateTaskStartIndex(child);
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, false);
        updateEmptyMessage();
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    public void addSideTaskLaunchCallback(RunnableList callback) {
        if (mSideTaskLaunchCallback == null) {
            mSideTaskLaunchCallback = new RunnableList();
        }
        mSideTaskLaunchCallback.add(callback::executeAllAndDestroy);
    }

    private void executeSideTaskLaunchCallback() {
        if (mSideTaskLaunchCallback != null) {
            mSideTaskLaunchCallback.executeAllAndDestroy();
            mSideTaskLaunchCallback = null;
        }
    }

    public void launchSideTaskInLiveTileModeForRestartedApp(int taskId) {
        if (mRunningTaskId != -1 && mRunningTaskId == taskId) {
            RemoteAnimationTargets targets = getLiveTileParams().getTargetSet();
            if (targets != null && targets.findTask(taskId) != null) {
                launchSideTaskInLiveTileMode(taskId, targets.apps, targets.wallpapers,
                        targets.nonApps);
            }
        }
    }

    public void launchSideTaskInLiveTileMode(int taskId, RemoteAnimationTargetCompat[] apps,
            RemoteAnimationTargetCompat[] wallpaper, RemoteAnimationTargetCompat[] nonApps) {
        AnimatorSet anim = new AnimatorSet();
        TaskView taskView = getTaskView(taskId);
        if (taskView == null || !isTaskViewVisible(taskView)) {
            // TODO: Refine this animation.
            SurfaceTransactionApplier surfaceApplier =
                    new SurfaceTransactionApplier(mActivity.getDragLayer());
            ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
            appAnimator.setDuration(RECENTS_LAUNCH_DURATION);
            appAnimator.setInterpolator(ACCEL_DEACCEL);
            appAnimator.addUpdateListener(valueAnimator -> {
                float percent = valueAnimator.getAnimatedFraction();
                SurfaceParams.Builder builder = new SurfaceParams.Builder(
                        apps[apps.length - 1].leash);
                Matrix matrix = new Matrix();
                matrix.postScale(percent, percent);
                matrix.postTranslate(mActivity.getDeviceProfile().widthPx * (1 - percent) / 2,
                        mActivity.getDeviceProfile().heightPx * (1 - percent) / 2);
                builder.withAlpha(percent).withMatrix(matrix);
                surfaceApplier.scheduleApply(builder.build());
            });
            anim.play(appAnimator);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finishRecentsAnimation(false /* toRecents */, null);
                }
            });
        } else {
            TaskViewUtils.composeRecentsLaunchAnimator(anim, taskView, apps, wallpaper, nonApps,
                    true /* launcherClosing */, mActivity.getStateManager(), this,
                    getDepthController());
        }
        anim.start();
    }

    private void updateTaskStartIndex(View affectingView) {
        if (!(affectingView instanceof TaskView) && !(affectingView instanceof ClearAllButton)) {
            int childCount = getChildCount();

            mTaskViewStartIndex = 0;
            while (mTaskViewStartIndex < childCount
                    && !(getChildAt(mTaskViewStartIndex) instanceof TaskView)) {
                mTaskViewStartIndex++;
            }
        }
    }

    public boolean isTaskViewVisible(TaskView tv) {
        if (showAsGrid()) {
            int screenStart = mOrientationHandler.getPrimaryScroll(this);
            int screenEnd = screenStart + mOrientationHandler.getMeasuredSize(this);
            return isTaskViewWithinBounds(tv, screenStart, screenEnd);
        } else {
            // For now, just check if it's the active task or an adjacent task
            return Math.abs(indexOfChild(tv) - getNextPage()) <= 1;
        }
    }

    private boolean isTaskViewWithinBounds(TaskView tv, int start, int end) {
        int taskStart = mOrientationHandler.getChildStart(tv) + (int) tv.getOffsetAdjustment(
                showAsFullscreen(), showAsGrid());
        int taskSize = (int) (mOrientationHandler.getMeasuredSize(tv) * tv.getSizeAdjustment(
                showAsFullscreen()));
        int taskEnd = taskStart + taskSize;
        return (taskStart >= start && taskStart <= end) || (taskEnd >= start
                && taskEnd <= end);
    }

    public TaskView getTaskView(int taskId) {
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = getTaskViewAt(i);
            if (taskView.hasTaskId(taskId)) {
                return taskView;
            }
        }
        return null;
    }

    public void setOverviewStateEnabled(boolean enabled) {
        mOverviewStateEnabled = enabled;
        updateTaskStackListenerState();
        mOrientationState.setRotationWatcherEnabled(enabled);
        if (!enabled) {
            // Reset the running task when leaving overview since it can still have a reference to
            // its thumbnail
            mTmpRunningTask = null;
            if (mSplitPlaceholderView.getSplitController().isSplitSelectActive()) {
                cancelSplitSelect(false);
            }
        }
        updateLocusId();
    }

    /**
     * Whether the Clear All button is hidden or fully visible. Used to determine if center
     * displayed page is a task or the Clear All button.
     *
     * @return True = Clear All button not fully visible, center page is a task. False = Clear All
     * button fully visible, center page is Clear All button.
     */
    public boolean isClearAllHidden() {
        return mClearAllButton.getAlpha() != 1f;
    }

    @Override
    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, true);
    }

    @Override
    protected void onPageEndTransition() {
        super.onPageEndTransition();
        if (isClearAllHidden()) {
            mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, false);
        }
        if (getNextPage() > 0) {
            setSwipeDownShouldLaunchApp(true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);

        if (showAsGrid()) {
            int taskCount = getTaskViewCount();
            for (int i = 0; i < taskCount; i++) {
                TaskView taskView = getTaskViewAt(i);
                if (isTaskViewVisible(taskView) && taskView.offerTouchToChildren(ev)) {
                    // Keep consuming events to pass to delegate
                    return true;
                }
            }
        } else {
            TaskView taskView = getCurrentPageTaskView();
            if (taskView != null && taskView.offerTouchToChildren(ev)) {
                // Keep consuming events to pass to delegate
                return true;
            }
        }

        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_UP:
                if (mTouchDownToStartHome) {
                    startHome();
                }
                mTouchDownToStartHome = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                mTouchDownToStartHome = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // Passing the touch slop will not allow dismiss to home
                if (mTouchDownToStartHome &&
                        (isHandlingTouch() ||
                                squaredHypot(mDownX - x, mDownY - y) > mSquaredTouchSlop)) {
                    mTouchDownToStartHome = false;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                // Touch down anywhere but the deadzone around the visible clear all button and
                // between the task views will start home on touch up
                if (!isHandlingTouch() && !isModal()) {
                    if (mShowEmptyMessage) {
                        mTouchDownToStartHome = true;
                    } else {
                        updateDeadZoneRects();
                        final boolean clearAllButtonDeadZoneConsumed =
                                mClearAllButton.getAlpha() == 1
                                        && mClearAllButtonDeadZoneRect.contains(x, y);
                        final boolean cameFromNavBar = (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
                        if (!clearAllButtonDeadZoneConsumed && !cameFromNavBar
                                && !mTaskViewDeadZoneRect.contains(x + getScrollX(), y)) {
                            mTouchDownToStartHome = true;
                        }
                    }
                }
                mDownX = x;
                mDownY = y;
                break;
        }

        return isHandlingTouch();
    }

    @Override
    protected void onNotSnappingToPageInFreeScroll() {
        int finalPos = mScroller.getFinalX();
        if (!showAsGrid() && finalPos > mMinScroll && finalPos < mMaxScroll) {
            int firstPageScroll = getScrollForPage(!mIsRtl ? 0 : getPageCount() - 1);
            int lastPageScroll = getScrollForPage(!mIsRtl ? getPageCount() - 1 : 0);

            // If scrolling ends in the half of the added space that is closer to
            // the end, settle to the end. Otherwise snap to the nearest page.
            // If flinging past one of the ends, don't change the velocity as it
            // will get stopped at the end anyway.
            int pageSnapped = finalPos < (firstPageScroll + mMinScroll) / 2
                    ? mMinScroll
                    : finalPos > (lastPageScroll + mMaxScroll) / 2
                            ? mMaxScroll
                            : getScrollForPage(mNextPage);

            mScroller.setFinalX(pageSnapped);
            // Ensure the scroll/snap doesn't happen too fast;
            int extraScrollDuration = OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION
                    - mScroller.getDuration();
            if (extraScrollDuration > 0) {
                mScroller.extendDuration(extraScrollDuration);
            }
        }
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        // Enables swiping to the left or right only if the task overlay is not modal.
        if (!isModal()) {
            super.determineScrollingStart(ev, touchSlopScale);
        }
    }

    protected void applyLoadPlan(ArrayList<Task> tasks) {
        if (mPendingAnimation != null) {
            mPendingAnimation.addEndListener(success -> applyLoadPlan(tasks));
            return;
        }

        if (tasks == null || tasks.isEmpty()) {
            removeTasksViewsAndClearAllButton();
            onTaskStackUpdated();
            return;
        }

        int currentTaskId = -1;
        TaskView currentTaskView = getTaskViewAtByAbsoluteIndex(mCurrentPage);
        if (currentTaskView != null) {
            currentTaskId = currentTaskView.getTask().key.id;
        }

        // Unload existing visible task data
        unloadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);

        TaskView ignoreResetTaskView =
                mIgnoreResetTaskId == -1 ? null : getTaskView(mIgnoreResetTaskId);

        final int requiredTaskCount = tasks.size();
        if (getTaskViewCount() != requiredTaskCount) {
            if (indexOfChild(mClearAllButton) != -1) {
                removeView(mClearAllButton);
            }
            for (int i = getTaskViewCount(); i < requiredTaskCount; i++) {
                addView(mTaskViewPool.getView());
            }
            while (getTaskViewCount() > requiredTaskCount) {
                removeView(getChildAt(getChildCount() - 1));
            }
            if (requiredTaskCount > 0) {
                addView(mClearAllButton);
            }
        }

        // Rebind and reset all task views
        for (int i = requiredTaskCount - 1; i >= 0; i--) {
            final int pageIndex = requiredTaskCount - i - 1 + mTaskViewStartIndex;
            final Task task = tasks.get(i);
            final TaskView taskView = (TaskView) getChildAt(pageIndex);
            taskView.bind(task, mOrientationState);
        }
        updateTaskSize();

        int targetPage = -1;
        if (mNextPage == INVALID_PAGE) {
            // Set the current page to the running task, but not if settling on new task.
            TaskView runningTaskView = getRunningTaskView();
            if (runningTaskView != null) {
                targetPage = indexOfChild(runningTaskView);
            } else if (getTaskViewCount() > 0) {
                targetPage = indexOfChild(getTaskViewAt(0));
            }
        } else if (currentTaskId != -1) {
            currentTaskView = getTaskView(currentTaskId);
            if (currentTaskView != null) {
                targetPage = indexOfChild(currentTaskView);
            }
        }
        if (targetPage != -1 && mCurrentPage != targetPage) {
            setCurrentPage(targetPage);
        }

        if (mIgnoreResetTaskId != -1 && getTaskView(mIgnoreResetTaskId) != ignoreResetTaskView) {
            // If the taskView mapping is changing, do not preserve the visuals. Since we are
            // mostly preserving the first task, and new taskViews are added to the end, it should
            // generally map to the same task.
            mIgnoreResetTaskId = -1;
        }
        resetTaskVisuals();
        onTaskStackUpdated();
        updateEnabledOverlays();
    }

    private boolean isModal() {
        return mTaskModalness > 0;
    }

    public boolean isLoadingTasks() {
        return mModel.isLoadingTasksInBackground();
    }

    private void removeTasksViewsAndClearAllButton() {
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            removeView(getTaskViewAt(i));
        }
        if (indexOfChild(mClearAllButton) != -1) {
            removeView(mClearAllButton);
        }
    }

    public int getTaskViewCount() {
        int taskViewCount = getChildCount() - mTaskViewStartIndex;
        if (indexOfChild(mClearAllButton) != -1) {
            taskViewCount--;
        }
        return taskViewCount;
    }

    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }

    public void resetTaskVisuals() {
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView taskView = getTaskViewAt(i);
            if (mIgnoreResetTaskId != taskView.getTask().key.id) {
                taskView.resetViewTransforms();
                taskView.setStableAlpha(mContentAlpha);
                taskView.setFullscreenProgress(mFullscreenProgress);
                taskView.setModalness(mTaskModalness);
            }
        }
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // Since we reuse the same mLiveTileTaskViewSimulator in the RecentsView, we need
            // to reset the params after it settles in Overview from swipe up so that we don't
            // render with obsolete param values.
            mLiveTileTaskViewSimulator.taskPrimaryTranslation.value = 0;
            mLiveTileTaskViewSimulator.taskSecondaryTranslation.value = 0;
            mLiveTileTaskViewSimulator.fullScreenProgress.value = 0;
            mLiveTileTaskViewSimulator.recentsViewScale.value = 1;

            // Similar to setRunningTaskHidden below, reapply the state before runningTaskView is
            // null.
            if (!mRunningTaskShowScreenshot) {
                setRunningTaskViewShowScreenshot(mRunningTaskShowScreenshot);
            }
        }
        if (mRunningTaskTileHidden) {
            setRunningTaskHidden(mRunningTaskTileHidden);
        }

        // Force apply the scale.
        if (mIgnoreResetTaskId != mRunningTaskId) {
            applyRunningTaskIconScale();
        }

        updateCurveProperties();
        // Update the set of visible task's data
        loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);
        setTaskModalness(0);
        setColorTint(0);
    }

    public void setFullscreenProgress(float fullscreenProgress) {
        mFullscreenProgress = fullscreenProgress;
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            getTaskViewAt(i).setFullscreenProgress(mFullscreenProgress);
        }
        mClearAllButton.setFullscreenProgress(fullscreenProgress);

        // Fade out the actions view quickly (0.1 range)
        mActionsView.getFullscreenAlpha().setValue(
                mapToRange(fullscreenProgress, 0, 0.1f, 1f, 0f, LINEAR));
    }

    private void updateTaskStackListenerState() {
        boolean handleTaskStackChanges = mOverviewStateEnabled && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE;
        if (handleTaskStackChanges != mHandleTaskStackChanges) {
            mHandleTaskStackChanges = handleTaskStackChanges;
            if (handleTaskStackChanges) {
                reloadIfNeeded();
            }
        }
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);

        // Update DeviceProfile dependant state.
        DeviceProfile dp = mActivity.getDeviceProfile();
        setOverviewGridEnabled(
                mActivity.getStateManager().getState().displayOverviewTasksAsGrid(dp));

        // Propagate DeviceProfile change event.
        mLiveTileTaskViewSimulator.setDp(dp);
        mActionsView.setDp(dp);
        mOrientationState.setDeviceProfile(dp);

        // Update RecentsView adn TaskView's DeviceProfile dependent layout.
        updateOrientationHandler();
    }

    private void updateOrientationHandler() {
        updateOrientationHandler(true);
    }

    private void updateOrientationHandler(boolean forceRecreateDragLayerControllers) {
        // Handle orientation changes.
        PagedOrientationHandler oldOrientationHandler = mOrientationHandler;
        mOrientationHandler = mOrientationState.getOrientationHandler();

        mIsRtl = mOrientationHandler.getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl
                ? View.LAYOUT_DIRECTION_RTL
                : View.LAYOUT_DIRECTION_LTR);
        mClearAllButton.setLayoutDirection(mIsRtl
                ? View.LAYOUT_DIRECTION_LTR
                : View.LAYOUT_DIRECTION_RTL);
        mClearAllButton.setRotation(mOrientationHandler.getDegreesRotated());

        if (forceRecreateDragLayerControllers
                || !mOrientationHandler.equals(oldOrientationHandler)) {
            // Changed orientations, update controllers so they intercept accordingly.
            mActivity.getDragLayer().recreateControllers();
        }

        boolean isInLandscape = mOrientationState.getTouchRotation() != ROTATION_0
                || mOrientationState.getRecentsActivityRotation() != ROTATION_0;
        mActionsView.updateHiddenFlags(HIDDEN_NON_ZERO_ROTATION,
                !mOrientationState.canRecentsActivityRotate() && isInLandscape);

        // Update TaskView's DeviceProfile dependent layout.
        updateChildTaskOrientations();

        // Recalculate DeviceProfile dependent layout.
        updateSizeAndPadding();

        requestLayout();
        // Reapply the current page to update page scrolls.
        setCurrentPage(mCurrentPage);
    }

    // Update task size and padding that are dependent on DeviceProfile and insets.
    private void updateSizeAndPadding() {
        DeviceProfile dp = mActivity.getDeviceProfile();
        getTaskSize(mTempRect);
        mTaskWidth = mTempRect.width();
        mTaskHeight = mTempRect.height();

        mTempRect.top -= dp.overviewTaskThumbnailTopMarginPx;
        setPadding(mTempRect.left - mInsets.left, mTempRect.top - mInsets.top,
                dp.widthPx - mInsets.right - mTempRect.right,
                dp.heightPx - mInsets.bottom - mTempRect.bottom);

        mSizeStrategy.calculateGridSize(mActivity, mActivity.getDeviceProfile(),
                mLastComputedGridSize);
        mSizeStrategy.calculateGridTaskSize(mActivity, mActivity.getDeviceProfile(),
                mLastComputedGridTaskSize, mOrientationHandler);

        // Force TaskView to update size from thumbnail
        updateTaskSize();

        // Update ActionsView position
        if (mActionsView != null) {
            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) mActionsView.getLayoutParams();
            if (dp.isTablet && FeatureFlags.ENABLE_OVERVIEW_GRID.get()) {
                layoutParams.gravity = Gravity.BOTTOM;
                layoutParams.bottomMargin =
                        dp.heightPx - mInsets.bottom - mLastComputedGridSize.bottom;
                layoutParams.leftMargin = mLastComputedTaskSize.left;
                layoutParams.rightMargin = dp.widthPx - mLastComputedTaskSize.right;
                // When in modal state, remove bottom margin to avoid covering content.
                mActionsView.setModalTransformY(layoutParams.bottomMargin);
            } else {
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                layoutParams.bottomMargin = 0;
                layoutParams.leftMargin = 0;
                layoutParams.rightMargin = 0;
                mActionsView.setModalTransformY(0);
            }
            mActionsView.setLayoutParams(layoutParams);
        }
    }

    /**
     * Updates TaskView scaling and translation required to support variable width.
     */
    private void updateTaskSize() {
        final int taskCount = getTaskViewCount();
        if (taskCount == 0) {
            return;
        }

        float accumulatedTranslationX = 0;
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = getTaskViewAt(i);
            taskView.updateTaskSize();
            taskView.getPrimaryFullscreenTranslationProperty().set(taskView,
                    accumulatedTranslationX);
            taskView.getSecondaryFullscreenTranslationProperty().set(taskView, 0f);
            // Compensate space caused by TaskView scaling.
            float widthDiff =
                    taskView.getLayoutParams().width * (1 - taskView.getFullscreenScale());
            accumulatedTranslationX += mIsRtl ? widthDiff : -widthDiff;
        }

        mClearAllButton.setFullscreenTranslationPrimary(accumulatedTranslationX);

        updateGridProperties();
    }

    public void getTaskSize(Rect outRect) {
        mSizeStrategy.calculateTaskSize(mActivity, mActivity.getDeviceProfile(), outRect,
                mOrientationHandler);
        mLastComputedTaskSize.set(outRect);
    }

    /**
     * Returns the size of task selected to enter modal state.
     */
    public Point getSelectedTaskSize() {
        mSizeStrategy.calculateTaskSize(mActivity, mActivity.getDeviceProfile(), mTempRect,
                mOrientationHandler);
        int taskWidth = mTempRect.width();
        int taskHeight = mTempRect.height();
        if (mFocusedTaskId != -1) {
            int boxLength = Math.max(taskWidth, taskHeight);
            if (mFocusedTaskRatio > 1) {
                taskWidth = boxLength;
                taskHeight = (int) (boxLength / mFocusedTaskRatio);
            } else {
                taskWidth = (int) (boxLength * mFocusedTaskRatio);
                taskHeight = boxLength;
            }
        }
        return new Point(taskWidth, taskHeight);
    }

    /** Gets the last computed task size */
    public Rect getLastComputedTaskSize() {
        return mLastComputedTaskSize;
    }

    public Rect getLastComputedGridTaskSize() {
        return mLastComputedGridTaskSize;
    }

    /** Gets the task size for modal state. */
    public void getModalTaskSize(Rect outRect) {
        mSizeStrategy.calculateModalTaskSize(mActivity, mActivity.getDeviceProfile(), outRect);
    }

    @Override
    protected boolean computeScrollHelper() {
        boolean scrolling = super.computeScrollHelper();
        boolean isFlingingFast = false;
        updateCurveProperties();
        if (scrolling || isHandlingTouch()) {
            if (scrolling) {
                // Check if we are flinging quickly to disable high res thumbnail loading
                isFlingingFast = mScroller.getCurrVelocity() > mFastFlingVelocity;
            }

            // After scrolling, update the visible task's data
            loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);

            // After scrolling, update ActionsView's visibility.
            TaskView focusedTaskView = getFocusedTaskView();
            if (focusedTaskView != null) {
                float scrollDiff = Math.abs(getScrollForPage(indexOfChild(focusedTaskView))
                        - mOrientationHandler.getPrimaryScroll(this));
                float delta = (mGridSideMargin - scrollDiff) / (float) mGridSideMargin;
                mActionsView.getScrollAlpha().setValue(Utilities.boundToRange(delta, 0, 1));
            }
        }

        // Update the high res thumbnail loader state
        mModel.getThumbnailCache().getHighResLoadingState().setFlingingFast(isFlingingFast);
        return scrolling;
    }

    /**
     * Scales and adjusts translation of adjacent pages as if on a curved carousel.
     */
    public void updateCurveProperties() {
        if (getPageCount() == 0 || getPageAt(0).getMeasuredWidth() == 0) {
            return;
        }
        int scroll = mOrientationHandler.getPrimaryScroll(this);
        mClearAllButton.onRecentsViewScroll(scroll, mOverviewGridEnabled);
    }

    @Override
    protected int getDestinationPage(int scaledScroll) {
        if (!(mActivity.getDeviceProfile().isTablet && FeatureFlags.ENABLE_OVERVIEW_GRID.get())) {
            return super.getDestinationPage(scaledScroll);
        }

        final int childCount = getChildCount();
        if (mPageScrolls == null || childCount != mPageScrolls.length) {
            return -1;
        }

        // When in tablet with variable task width, return the page which scroll is closest to
        // screenStart instead of page nearest to center of screen.
        int minDistanceFromScreenStart = Integer.MAX_VALUE;
        int minDistanceFromScreenStartIndex = -1;
        for (int i = 0; i < childCount; ++i) {
            int distanceFromScreenStart = Math.abs(mPageScrolls[i] - scaledScroll);
            if (distanceFromScreenStart < minDistanceFromScreenStart) {
                minDistanceFromScreenStart = distanceFromScreenStart;
                minDistanceFromScreenStartIndex = i;
            }
        }
        return minDistanceFromScreenStartIndex;
    }

    /**
     * Iterates through all the tasks, and loads the associated task data for newly visible tasks,
     * and unloads the associated task data for tasks that are no longer visible.
     */
    public void loadVisibleTaskData(@TaskView.TaskDataChanges int dataChanges) {
        if (!mOverviewStateEnabled || mTaskListChangeId == -1) {
            // Skip loading visible task data if we've already left the overview state, or if the
            // task list hasn't been loaded yet (the task views will not reflect the task list)
            return;
        }

        int lower = 0;
        int upper = 0;
        int visibleStart = 0;
        int visibleEnd = 0;
        if (showAsGrid()) {
            int screenStart = mOrientationHandler.getPrimaryScroll(this);
            int pageOrientedSize = mOrientationHandler.getMeasuredSize(this);
            int halfScreenSize = pageOrientedSize / 2;
            // Use +/- 50% screen width as visible area.
            visibleStart = screenStart - halfScreenSize;
            visibleEnd = screenStart + pageOrientedSize + halfScreenSize;
        } else {
            int centerPageIndex = getPageNearestToCenterOfScreen();
            int numChildren = getChildCount();
            lower = Math.max(0, centerPageIndex - 2);
            upper = Math.min(centerPageIndex + 2, numChildren - 1);
        }

        // Update the task data for the in/visible children
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = getTaskViewAt(i);
            Task task = taskView.getTask();
            int index = indexOfChild(taskView);
            boolean visible;
            if (showAsGrid()) {
                visible = isTaskViewWithinBounds(taskView, visibleStart, visibleEnd);
            } else {
                visible = lower <= index && index <= upper;
            }
            if (visible) {
                if (task == mTmpRunningTask) {
                    // Skip loading if this is the task that we are animating into
                    continue;
                }
                if (!mHasVisibleTaskData.get(task.key.id)) {
                    // Ignore thumbnail update if it's current running task during the gesture
                    // We snapshot at end of gesture, it will update then
                    int changes = dataChanges;
                    if (taskView == getRunningTaskView() && mGestureActive) {
                        changes &= ~TaskView.FLAG_UPDATE_THUMBNAIL;
                    }
                    taskView.onTaskListVisibilityChanged(true /* visible */, changes);
                }
                mHasVisibleTaskData.put(task.key.id, visible);
            } else {
                if (mHasVisibleTaskData.get(task.key.id)) {
                    taskView.onTaskListVisibilityChanged(false /* visible */, dataChanges);
                }
                mHasVisibleTaskData.delete(task.key.id);
            }
        }
    }

    /**
     * Unloads any associated data from the currently visible tasks
     */
    private void unloadVisibleTaskData(@TaskView.TaskDataChanges int dataChanges) {
        for (int i = 0; i < mHasVisibleTaskData.size(); i++) {
            if (mHasVisibleTaskData.valueAt(i)) {
                TaskView taskView = getTaskView(mHasVisibleTaskData.keyAt(i));
                if (taskView != null) {
                    taskView.onTaskListVisibilityChanged(false /* visible */, dataChanges);
                }
            }
        }
        mHasVisibleTaskData.clear();
    }

    @Override
    public void onHighResLoadingStateChanged(boolean enabled) {
        // Whenever the high res loading state changes, poke each of the visible tasks to see if
        // they want to updated their thumbnail state
        for (int i = 0; i < mHasVisibleTaskData.size(); i++) {
            if (mHasVisibleTaskData.valueAt(i)) {
                TaskView taskView = getTaskView(mHasVisibleTaskData.keyAt(i));
                if (taskView != null) {
                    // Poke the view again, which will trigger it to load high res if the state
                    // is enabled
                    taskView.onTaskListVisibilityChanged(true /* visible */);
                }
            }
        }
    }

    public abstract void startHome();

    /** `true` if there is a +1 space available in overview. */
    public boolean hasRecentsExtraCard() {
        return false;
    }

    public void reset() {
        setCurrentTask(-1);
        mIgnoreResetTaskId = -1;
        mTaskListChangeId = -1;
        mFocusedTaskId = -1;

        if (mRecentsAnimationController != null) {
            if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile) {
                // We are still drawing the live tile, finish it now to clean up.
                finishRecentsAnimation(true /* toRecents */, null);
            } else {
                mRecentsAnimationController = null;
            }
        }
        setEnableDrawingLiveTile(false);
        mLiveTileParams.setTargetSet(null);
        mLiveTileTaskViewSimulator.setDrawsBelowRecents(true);

        // These are relatively expensive and don't need to be done this frame (RecentsView isn't
        // visible anyway), so defer by a frame to get off the critical path, e.g. app to home.
        post(() -> {
            unloadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);
            setCurrentPage(0);
            LayoutUtils.setViewEnabled(mActionsView, true);
            if (mOrientationState.setGestureActive(false)) {
                updateOrientationHandler(/* forceRecreateDragLayerControllers = */ false);
            }
        });
    }

    public int getRunningTaskId() {
        return mRunningTaskId;
    }

    public @Nullable TaskView getRunningTaskView() {
        return getTaskView(mRunningTaskId);
    }

    public int getRunningTaskIndex() {
        return getTaskIndexForId(mRunningTaskId);
    }

    public @Nullable TaskView getFocusedTaskView() {
        return getTaskView(mFocusedTaskId);
    }

    /**
     * Returns the width to height ratio of the focused {@link TaskView}.
     */
    public float getFocusedTaskRatio() {
        return mFocusedTaskRatio;
    }

    /**
     * Get the index of the task view whose id matches {@param taskId}.
     * @return -1 if there is no task view for the task id, else the index of the task view.
     */
    public int getTaskIndexForId(int taskId) {
        TaskView tv = getTaskView(taskId);
        return tv == null ? -1 : indexOfChild(tv);
    }

    public int getTaskViewStartIndex() {
        return mTaskViewStartIndex;
    }

    /**
     * Reloads the view if anything in recents changed.
     */
    public void reloadIfNeeded() {
        if (!mModel.isTaskListValid(mTaskListChangeId)) {
            mTaskListChangeId = mModel.getTasks(this::applyLoadPlan);
        }
    }

    /**
     * Called when a gesture from an app is starting.
     */
    public void onGestureAnimationStart(RunningTaskInfo runningTaskInfo) {
        mGestureActive = true;
        // This needs to be called before the other states are set since it can create the task view
        if (mOrientationState.setGestureActive(true)) {
            updateOrientationHandler();
        }

        showCurrentTask(runningTaskInfo);
        setEnableFreeScroll(false);
        setEnableDrawingLiveTile(false);
        setRunningTaskHidden(true);
        setRunningTaskIconScaledDown(true);
    }

    /**
     * Called only when a swipe-up gesture from an app has completed. Only called after
     * {@link #onGestureAnimationStart} and {@link #onGestureAnimationEnd()}.
     */
    public void onSwipeUpAnimationSuccess() {
        if (getRunningTaskView() != null) {
            animateUpRunningTaskIconScale();
        }
        setSwipeDownShouldLaunchApp(true);
    }

    private void animateRecentsRotationInPlace(int newRotation) {
        if (mOrientationState.canRecentsActivityRotate()) {
            // Let system take care of the rotation
            return;
        }
        AnimatorSet pa = setRecentsChangedOrientation(true);
        pa.addListener(AnimatorListeners.forSuccessCallback(() -> {
            setLayoutRotation(newRotation, mOrientationState.getDisplayRotation());
            mActivity.getDragLayer().recreateControllers();
            setRecentsChangedOrientation(false).start();
        }));
        pa.start();
    }

    public AnimatorSet setRecentsChangedOrientation(boolean fadeInChildren) {
        getRunningTaskIndex();
        int runningIndex = getCurrentPage();
        AnimatorSet as = new AnimatorSet();
        for (int i = 0; i < getTaskViewCount(); i++) {
            View taskView = getTaskViewAt(i);
            if (runningIndex == i && taskView.getAlpha() != 0) {
                continue;
            }
            as.play(ObjectAnimator.ofFloat(taskView, View.ALPHA, fadeInChildren ? 0 : 1));
        }
        return as;
    }


    private void updateChildTaskOrientations() {
        for (int i = 0; i < getTaskViewCount(); i++) {
            getTaskViewAt(i).setOrientationState(mOrientationState);
        }
        TaskMenuView tv = (TaskMenuView) getTopOpenViewWithType(mActivity, TYPE_TASK_MENU);
        if (tv != null) {
            tv.onRotationChanged();
        }
    }

    /**
     * Called when a gesture from an app has finished, and an end target has been determined.
     */
    public void onPrepareGestureEndAnimation(
            @Nullable AnimatorSet animatorSet, GestureState.GestureEndTarget endTarget) {
        if (mSizeStrategy.stateFromGestureEndTarget(endTarget)
                .displayOverviewTasksAsGrid(mActivity.getDeviceProfile())) {
            if (animatorSet == null) {
                setGridProgress(1);
            } else {
                animatorSet.play(ObjectAnimator.ofFloat(this, RECENTS_GRID_PROGRESS, 1));
            }
        }
        mCurrentGestureEndTarget = endTarget;
        if (endTarget == GestureState.GestureEndTarget.NEW_TASK
                || endTarget == GestureState.GestureEndTarget.LAST_TASK) {
            // When switching to tasks in quick switch, ensures the snapped page's scroll maintain
            // invariant between quick switch and overview, to ensure a smooth animation transition.
            updateGridProperties();
        }
    }

    /**
     * Called when a gesture from an app has finished, and the animation to the target has ended.
     */
    public void onGestureAnimationEnd() {
        mGestureActive = false;
        if (mOrientationState.setGestureActive(false)) {
            updateOrientationHandler(/* forceRecreateDragLayerControllers = */ false);
        }

        setEnableFreeScroll(true);
        setEnableDrawingLiveTile(mCurrentGestureEndTarget == GestureState.GestureEndTarget.RECENTS);
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            setRunningTaskViewShowScreenshot(true);
        }
        setRunningTaskHidden(false);
        animateUpRunningTaskIconScale();

        if (mCurrentGestureEndTarget == GestureState.GestureEndTarget.RECENTS
                && (!showAsGrid() || getFocusedTaskView() != null)) {
            animateActionsViewIn();
        }

        mCurrentGestureEndTarget = null;

        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mActivity.getDeviceProfile().isMultiWindowMode) {
            switchToScreenshot(
                    () -> finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                            null));
        }
    }

    /**
     * Returns true if we should add a stub taskView for the running task id
     */
    protected boolean shouldAddStubTaskView(RunningTaskInfo runningTaskInfo) {
        return runningTaskInfo != null && getTaskView(runningTaskInfo.taskId) == null;
    }

    /**
     * Creates a task view (if necessary) to represent the task with the {@param runningTaskId}.
     *
     * All subsequent calls to reload will keep the task as the first item until {@link #reset()}
     * is called.  Also scrolls the view to this task.
     */
    public void showCurrentTask(RunningTaskInfo runningTaskInfo) {
        if (shouldAddStubTaskView(runningTaskInfo)) {
            boolean wasEmpty = getChildCount() == 0;
            // Add an empty view for now until the task plan is loaded and applied
            final TaskView taskView = mTaskViewPool.getView();
            addView(taskView, mTaskViewStartIndex);
            if (wasEmpty) {
                addView(mClearAllButton);
            }
            // The temporary running task is only used for the duration between the start of the
            // gesture and the task list is loaded and applied
            mTmpRunningTask = Task.from(new TaskKey(runningTaskInfo), runningTaskInfo, false);
            taskView.bind(mTmpRunningTask, mOrientationState);

            // Measure and layout immediately so that the scroll values is updated instantly
            // as the user might be quick-switching
            measure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                    makeMeasureSpec(getMeasuredHeight(), EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }

        boolean runningTaskTileHidden = mRunningTaskTileHidden;
        int runningTaskId = runningTaskInfo == null ? -1 : runningTaskInfo.taskId;
        setCurrentTask(runningTaskId);
        if (mActivity.getDeviceProfile().isTablet && FeatureFlags.ENABLE_OVERVIEW_GRID.get()) {
            setFocusedTask(runningTaskId);
        }
        setCurrentPage(getRunningTaskIndex());
        setRunningTaskViewShowScreenshot(false);
        setRunningTaskHidden(runningTaskTileHidden);
        // Update task size after setting current task.
        updateTaskSize();

        // Reload the task list
        mTaskListChangeId = mModel.getTasks(this::applyLoadPlan);
    }

    /**
     * Sets the running task id, cleaning up the old running task if necessary.
     */
    public void setCurrentTask(int runningTaskId) {
        if (mRunningTaskId == runningTaskId) {
            return;
        }

        if (mRunningTaskId != -1) {
            // Reset the state on the old running task view
            setRunningTaskIconScaledDown(false);
            setRunningTaskViewShowScreenshot(true);
            setRunningTaskHidden(false);
        }
        mRunningTaskId = runningTaskId;
    }

    /**
     * Sets the focused task id and store the width to height ratio of the focused task.
     */
    protected void setFocusedTask(int focusedTaskId) {
        mFocusedTaskId = focusedTaskId;
        mFocusedTaskRatio =
                mLastComputedTaskSize.width() / (float) mLastComputedTaskSize.height();
    }

    /**
     * Hides the tile associated with {@link #mRunningTaskId}
     */
    public void setRunningTaskHidden(boolean isHidden) {
        mRunningTaskTileHidden = isHidden;
        TaskView runningTask = getRunningTaskView();
        if (runningTask != null) {
            runningTask.setStableAlpha(isHidden ? 0 : mContentAlpha);
            if (!isHidden) {
                AccessibilityManagerCompat.sendCustomAccessibilityEvent(runningTask,
                        AccessibilityEvent.TYPE_VIEW_FOCUSED, null);
            }
        }
    }

    private void setRunningTaskViewShowScreenshot(boolean showScreenshot) {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mRunningTaskShowScreenshot = showScreenshot;
            TaskView runningTaskView = getRunningTaskView();
            if (runningTaskView != null) {
                runningTaskView.setShowScreenshot(mRunningTaskShowScreenshot);
            }
        }
    }

    public void setRunningTaskIconScaledDown(boolean isScaledDown) {
        if (mRunningTaskIconScaledDown != isScaledDown) {
            mRunningTaskIconScaledDown = isScaledDown;
            applyRunningTaskIconScale();
        }
    }

    public boolean isTaskIconScaledDown(TaskView taskView) {
        return mRunningTaskIconScaledDown && getRunningTaskView() == taskView;
    }

    private void applyRunningTaskIconScale() {
        TaskView firstTask = getRunningTaskView();
        if (firstTask != null) {
            firstTask.setIconScaleAndDim(mRunningTaskIconScaledDown ? 0 : 1);
        }
    }

    private void animateActionsViewIn() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                mActionsView.getVisibilityAlpha(), MultiValueAlpha.VALUE, 0, 1);
        anim.setDuration(TaskView.SCALE_ICON_DURATION);
        anim.start();
    }

    private void animateActionsViewOut() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                mActionsView.getVisibilityAlpha(), MultiValueAlpha.VALUE, 1, 0);
        anim.setDuration(TaskView.SCALE_ICON_DURATION);
        anim.start();
    }

    public void animateUpRunningTaskIconScale() {
        mRunningTaskIconScaledDown = false;
        TaskView firstTask = getRunningTaskView();
        if (firstTask != null) {
            firstTask.setIconScaleAnimStartProgress(0f);
            firstTask.animateIconScaleAndDimIntoView();
        }
    }

    /** Updates TaskView and ClearAllButtion scaling and translation required to turn into grid
     * layout.
     * This method is used when no task dismissal has occurred.
     */
    private void updateGridProperties() {
        updateGridProperties(false);
    }

    /**
     * Updates TaskView and ClearAllButton scaling and translation required to turn into grid
     * layout.
     * This method only calculates the potential position and depends on {@link #setGridProgress} to
     * apply the actual scaling and translation.
     *
     * @param isTaskDismissal indicates if update was called due to task dismissal
     */
    private void updateGridProperties(boolean isTaskDismissal) {
        int taskCount = getTaskViewCount();
        if (taskCount == 0) {
            return;
        }

        final int boxLength = Math.max(mLastComputedGridTaskSize.width(),
                mLastComputedGridTaskSize.height());
        int taskTopMargin = mActivity.getDeviceProfile().overviewTaskThumbnailTopMarginPx;

        /*
         * taskGridVerticalDiff is used to position the top of a task in the top row of the grid
         * heightOffset is the vertical space one grid task takes + space between top and
         *   bottom row
         * Summed together they provide the top position for bottom row of grid tasks
         */
        final float taskGridVerticalDiff =
                mLastComputedGridTaskSize.top - mLastComputedTaskSize.top;
        final float heightOffset = (boxLength + taskTopMargin) + mRowSpacing;

        int topRowWidth = 0;
        int bottomRowWidth = 0;
        float topAccumulatedTranslationX = 0;
        float bottomAccumulatedTranslationX = 0;

        // Contains whether the child index is in top or bottom of grid (for non-focused task)
        // Different from mTopRowIdSet, which contains the taskId of what task is in top row
        IntSet topSet = new IntSet();
        IntSet bottomSet = new IntSet();

        // Horizontal grid translation for each task
        float[] gridTranslations = new float[taskCount];

        int focusedTaskIndex = Integer.MAX_VALUE;
        int focusedTaskShift = 0;
        int focusedTaskWidthAndSpacing = 0;
        int snappedTaskRowWidth = 0;
        int snappedPage = getNextPage();
        TaskView snappedTaskView = getTaskViewAtByAbsoluteIndex(snappedPage);

        if (!isTaskDismissal) {
            mTopRowIdSet.clear();
        }
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = getTaskViewAt(i);
            int taskWidthAndSpacing = taskView.getLayoutParams().width + mPageSpacing;
            // Evenly distribute tasks between rows unless rearranging due to task dismissal, in
            // which case keep tasks in their respective rows. For the running task, don't join
            // the grid.
            if (taskView.isFocusedTask()) {
                topRowWidth += taskWidthAndSpacing;
                bottomRowWidth += taskWidthAndSpacing;

                focusedTaskIndex = i;
                focusedTaskWidthAndSpacing = taskWidthAndSpacing;
                gridTranslations[i] += focusedTaskShift;
                gridTranslations[i] += mIsRtl ? taskWidthAndSpacing : -taskWidthAndSpacing;

                // Center view vertically in case it's from different orientation.
                taskView.setGridTranslationY((mLastComputedTaskSize.height() + taskTopMargin
                        - taskView.getLayoutParams().height) / 2f);

                if (taskView == snappedTaskView) {
                    // If focused task is snapped, the row width is just task width and spacing.
                    snappedTaskRowWidth = taskWidthAndSpacing;
                }
            } else {
                if (i > focusedTaskIndex) {
                    // For tasks after the focused task, shift by focused task's width and spacing.
                    gridTranslations[i] +=
                            mIsRtl ? focusedTaskWidthAndSpacing : -focusedTaskWidthAndSpacing;
                } else {
                    // For task before the focused task, accumulate the width and spacing to
                    // calculate the distance focused task need to shift.
                    focusedTaskShift += mIsRtl ? taskWidthAndSpacing : -taskWidthAndSpacing;
                }
                int taskId = taskView.getTask().key.id;
                boolean isTopRow = isTaskDismissal ? mTopRowIdSet.contains(taskId)
                        : topRowWidth <= bottomRowWidth;
                if (isTopRow) {
                    topRowWidth += taskWidthAndSpacing;
                    topSet.add(i);
                    mTopRowIdSet.add(taskId);

                    taskView.setGridTranslationY(taskGridVerticalDiff);

                    // Move horizontally into empty space.
                    float widthOffset = 0;
                    for (int j = i - 1; !topSet.contains(j) && j >= 0; j--) {
                        if (j == focusedTaskIndex) {
                            continue;
                        }
                        widthOffset += getTaskViewAt(j).getLayoutParams().width + mPageSpacing;
                    }

                    float currentTaskTranslationX = mIsRtl ? widthOffset : -widthOffset;
                    gridTranslations[i] += topAccumulatedTranslationX + currentTaskTranslationX;
                    topAccumulatedTranslationX += currentTaskTranslationX;
                } else {
                    bottomRowWidth += taskWidthAndSpacing;
                    bottomSet.add(i);

                    // Move into bottom row.
                    taskView.setGridTranslationY(heightOffset + taskGridVerticalDiff);

                    // Move horizontally into empty space.
                    float widthOffset = 0;
                    for (int j = i - 1; !bottomSet.contains(j) && j >= 0; j--) {
                        if (j == focusedTaskIndex) {
                            continue;
                        }
                        widthOffset += getTaskViewAt(j).getLayoutParams().width + mPageSpacing;
                    }

                    float currentTaskTranslationX = mIsRtl ? widthOffset : -widthOffset;
                    gridTranslations[i] += bottomAccumulatedTranslationX + currentTaskTranslationX;
                    bottomAccumulatedTranslationX += currentTaskTranslationX;
                }
                if (taskView == snappedTaskView) {
                    snappedTaskRowWidth = isTopRow ? topRowWidth : bottomRowWidth;
                }
            }
        }

        // We need to maintain snapped task's page scroll invariant between quick switch and
        // overview, so we sure snapped task's grid translation is 0, and add a non-fullscreen
        // translationX that is the same as snapped task's full scroll adjustment.
        float snappedTaskFullscreenScrollAdjustment = 0;
        float snappedTaskGridTranslationX = 0;
        if (snappedTaskView != null) {
            snappedTaskFullscreenScrollAdjustment = snappedTaskView.getScrollAdjustment(
                    /*fullscreenEnabled=*/true, /*gridEnabled=*/false);
            snappedTaskGridTranslationX = gridTranslations[snappedPage - mTaskViewStartIndex];
        }

        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = getTaskViewAt(i);
            taskView.setGridTranslationX(gridTranslations[i] - snappedTaskGridTranslationX);
            taskView.getPrimaryNonFullscreenTranslationProperty().set(taskView,
                    snappedTaskFullscreenScrollAdjustment);
            taskView.getSecondaryNonFullscreenTranslationProperty().set(taskView, 0f);
        }

        // Use the accumulated translation of the row containing the last task.
        float clearAllAccumulatedTranslation = topSet.contains(taskCount - 1)
                ? topAccumulatedTranslationX : bottomAccumulatedTranslationX;

        // If the last task is on the shorter row, ClearAllButton will embed into the shorter row
        // which is not what we want. Compensate the width difference of the 2 rows in that case.
        float shorterRowCompensation = 0;
        if (topRowWidth <= bottomRowWidth) {
            if (topSet.contains(taskCount - 1)) {
                shorterRowCompensation = bottomRowWidth - topRowWidth;
            }
        } else {
            if (bottomSet.contains(taskCount - 1)) {
                shorterRowCompensation = topRowWidth - bottomRowWidth;
            }
        }
        float clearAllShorterRowCompensation =
                mIsRtl ? -shorterRowCompensation : shorterRowCompensation;

        // If the total width is shorter than one grid's width, move ClearAllButton further away
        // accordingly. Update longRowWidth if ClearAllButton has been moved.
        float clearAllShortTotalCompensation = 0;
        int longRowWidth = Math.max(topRowWidth, bottomRowWidth);
        if (longRowWidth < mLastComputedGridSize.width()) {
            float shortTotalCompensation = mLastComputedGridSize.width() - longRowWidth;
            clearAllShortTotalCompensation =
                    mIsRtl ? -shortTotalCompensation : shortTotalCompensation;
            longRowWidth = mLastComputedGridSize.width();
        }

        float clearAllTotalTranslationX =
                clearAllAccumulatedTranslation + clearAllShorterRowCompensation
                        + clearAllShortTotalCompensation + snappedTaskFullscreenScrollAdjustment;
        if (focusedTaskIndex < taskCount) {
            // Shift by focused task's width and spacing if a task is focused.
            clearAllTotalTranslationX +=
                    mIsRtl ? focusedTaskWidthAndSpacing : -focusedTaskWidthAndSpacing;
        }

        // Make sure there are enough space between snapped page and ClearAllButton, for the case
        // of swiping up after quick switch.
        if (snappedTaskView != null) {
            int distanceFromClearAll = longRowWidth - snappedTaskRowWidth;
            int minimumDistance =
                    mLastComputedGridSize.width() - snappedTaskView.getLayoutParams().width;
            if (distanceFromClearAll < minimumDistance) {
                int distanceDifference = minimumDistance - distanceFromClearAll;
                clearAllTotalTranslationX += mIsRtl ? -distanceDifference : distanceDifference;
            }
        }

        mClearAllButton.setGridTranslationPrimary(
                clearAllTotalTranslationX - snappedTaskGridTranslationX);
        mClearAllButton.setGridScrollOffset(
                mIsRtl ? mLastComputedTaskSize.left - mLastComputedGridSize.left
                        : mLastComputedTaskSize.right - mLastComputedGridSize.right);

        setGridProgress(mGridProgress);
    }

    private boolean isSameGridRow(TaskView taskView1, TaskView taskView2) {
        if (taskView1 == null || taskView2 == null) {
            return false;
        }
        int taskId1 = taskView1.getTask().key.id;
        int taskId2 = taskView2.getTask().key.id;
        if (taskId1 == mFocusedTaskId || taskId2 == mFocusedTaskId) {
            return false;
        }
        return (mTopRowIdSet.contains(taskId1) && mTopRowIdSet.contains(taskId2)) || (
                !mTopRowIdSet.contains(taskId1) && !mTopRowIdSet.contains(taskId2));
    }

    /**
     * Moves TaskView and ClearAllButton between carousel and 2 row grid.
     *
     * @param gridProgress 0 = carousel; 1 = 2 row grid.
     */
    private void setGridProgress(float gridProgress) {
        int taskCount = getTaskViewCount();
        if (taskCount == 0) {
            return;
        }

        mGridProgress = gridProgress;

        for (int i = 0; i < taskCount; i++) {
            getTaskViewAt(i).setGridProgress(gridProgress);
        }
        mClearAllButton.setGridProgress(gridProgress);
    }

    private void enableLayoutTransitions() {
        if (mLayoutTransition == null) {
            mLayoutTransition = new LayoutTransition();
            mLayoutTransition.enableTransitionType(LayoutTransition.APPEARING);
            mLayoutTransition.setDuration(ADDITION_TASK_DURATION);
            mLayoutTransition.setStartDelay(LayoutTransition.APPEARING, 0);

            mLayoutTransition.addTransitionListener(new TransitionListener() {
                @Override
                public void startTransition(LayoutTransition transition, ViewGroup viewGroup,
                    View view, int i) {
                }

                @Override
                public void endTransition(LayoutTransition transition, ViewGroup viewGroup,
                    View view, int i) {
                    // When the unpinned task is added, snap to first page and disable transitions
                    if (view instanceof TaskView) {
                        snapToPage(0);
                        setLayoutTransition(null);
                    }

                }
            });
        }
        setLayoutTransition(mLayoutTransition);
    }

    public void setSwipeDownShouldLaunchApp(boolean swipeDownShouldLaunchApp) {
        mSwipeDownShouldLaunchApp = swipeDownShouldLaunchApp;
    }

    public boolean shouldSwipeDownLaunchApp() {
        return mSwipeDownShouldLaunchApp;
    }

    public void setIgnoreResetTask(int taskId) {
        mIgnoreResetTaskId = taskId;
    }

    public void clearIgnoreResetTask(int taskId) {
        if (mIgnoreResetTaskId == taskId) {
            mIgnoreResetTaskId = -1;
        }
    }

    private void addDismissedTaskAnimations(TaskView taskView, long duration,
            PendingAnimation anim) {
        // Use setFloat instead of setViewAlpha as we want to keep the view visible even when it's
        // alpha is set to 0 so that it can be recycled in the view pool properly
        anim.setFloat(taskView, VIEW_ALPHA, 0, clampToProgress(ACCEL, 0, 0.5f));
        SplitSelectStateController splitController = mSplitPlaceholderView.getSplitController();

        ResourceProvider rp = DynamicResource.provider(mActivity);
        SpringProperty sp = new SpringProperty(SpringProperty.FLAG_CAN_SPRING_ON_START)
                .setDampingRatio(rp.getFloat(R.dimen.dismiss_task_trans_y_damping_ratio))
                .setStiffness(rp.getFloat(R.dimen.dismiss_task_trans_y_stiffness));
        FloatProperty<TaskView> dismissingTaskViewTranslate =
                taskView.getSecondaryDissmissTranslationProperty();
        // TODO(b/186800707) translate entire grid size distance
        int translateDistance = mOrientationHandler.getSecondaryDimension(taskView);
        int positiveNegativeFactor = mOrientationHandler.getSecondaryTranslationDirectionFactor();
        if (splitController.isSplitSelectActive()) {
            // Have the task translate towards whatever side was just pinned
            int dir = mOrientationHandler.getSplitTaskViewDismissDirection(splitController
                    .getActiveSplitPositionOption(), mActivity.getDeviceProfile());
            switch (dir) {
                case PagedOrientationHandler.SPLIT_TRANSLATE_SECONDARY_NEGATIVE:
                    dismissingTaskViewTranslate = taskView
                            .getSecondaryDissmissTranslationProperty();
                    positiveNegativeFactor = -1;
                    break;

                case PagedOrientationHandler.SPLIT_TRANSLATE_PRIMARY_POSITIVE:
                    dismissingTaskViewTranslate = taskView.getPrimaryDismissTranslationProperty();
                    positiveNegativeFactor = 1;
                    break;

                case PagedOrientationHandler.SPLIT_TRANSLATE_PRIMARY_NEGATIVE:
                    dismissingTaskViewTranslate = taskView.getPrimaryDismissTranslationProperty();
                    positiveNegativeFactor = -1;
                    break;
                default:
                    throw new IllegalStateException("Invalid split task translation: " + dir);
            }
        }
        // Double translation distance so dismissal drag is the full height, as we only animate
        // the drag for the first half of the progress.
        anim.add(ObjectAnimator.ofFloat(taskView, dismissingTaskViewTranslate,
                positiveNegativeFactor * translateDistance * 2).setDuration(duration), LINEAR, sp);

        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                && taskView.isRunningTask()) {
            anim.addOnFrameCallback(() -> {
                mLiveTileTaskViewSimulator.taskSecondaryTranslation.value =
                        mOrientationHandler.getSecondaryValue(
                                taskView.getTranslationX(),
                                taskView.getTranslationY());
                redrawLiveTile();
            });
        }
    }

    public PendingAnimation createTaskDismissAnimation(TaskView taskView, boolean animateTaskView,
            boolean shouldRemoveTask, long duration) {
        if (mPendingAnimation != null) {
            mPendingAnimation.createPlaybackController().dispatchOnCancel().dispatchOnEnd();
        }
        PendingAnimation anim = new PendingAnimation(duration);

        int count = getPageCount();
        if (count == 0) {
            return anim;
        }

        int[] oldScroll = new int[count];
        int[] newScroll = new int[count];
        getPageScrolls(oldScroll, false, SIMPLE_SCROLL_LOGIC);
        getPageScrolls(newScroll, false, (v) -> v.getVisibility() != GONE && v != taskView);
        int taskCount = getTaskViewCount();
        int scrollDiffPerPage = 0;
        if (count > 1) {
            scrollDiffPerPage = Math.abs(oldScroll[1] - oldScroll[0]);
        }
        int draggedIndex = indexOfChild(taskView);

        boolean isFocusedTaskDismissed = taskView.getTask().key.id == mFocusedTaskId;
        if (isFocusedTaskDismissed && showAsGrid()) {
            anim.setFloat(mActionsView, VIEW_ALPHA, 0, clampToProgress(ACCEL_0_5, 0, 0.5f));
        }
        float dismissedTaskWidth = taskView.getLayoutParams().width + mPageSpacing;
        boolean needsCurveUpdates = false;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child == taskView) {
                if (animateTaskView) {
                    addDismissedTaskAnimations(taskView, duration, anim);
                }
            } else if (!showAsGrid()) {
                // Compute scroll offsets from task dismissal for animation.
                // If we just take newScroll - oldScroll, everything to the right of dragged task
                // translates to the left. We need to offset this in some cases:
                // - In RTL, add page offset to all pages, since we want pages to move to the right
                // Additionally, add a page offset if:
                // - Current page is rightmost page (leftmost for RTL)
                // - Dragging an adjacent page on the left side (right side for RTL)
                int offset = mIsRtl ? scrollDiffPerPage : 0;
                if (mCurrentPage == draggedIndex) {
                    int lastPage = taskCount - 1;
                    if (mCurrentPage == lastPage) {
                        offset += mIsRtl ? -scrollDiffPerPage : scrollDiffPerPage;
                    }
                } else {
                    // Dragging an adjacent page.
                    int negativeAdjacent = mCurrentPage - 1; // (Right in RTL, left in LTR)
                    if (draggedIndex == negativeAdjacent) {
                        offset += mIsRtl ? -scrollDiffPerPage : scrollDiffPerPage;
                    }
                }

                int scrollDiff = newScroll[i] - oldScroll[i] + offset;
                if (scrollDiff != 0) {
                    FloatProperty translationProperty = child instanceof TaskView
                            ? ((TaskView) child).getPrimaryDismissTranslationProperty()
                            : mOrientationHandler.getPrimaryViewTranslate();

                    float additionalDismissDuration =
                            ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET * Math.abs(
                                    i - draggedIndex);
                    anim.setFloat(child, translationProperty, scrollDiff, clampToProgress(LINEAR,
                            Utilities.boundToRange(INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                    + additionalDismissDuration, 0f, 1f), 1));
                    if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                            && child instanceof TaskView
                            && ((TaskView) child).isRunningTask()) {
                        anim.addOnFrameCallback(() -> {
                            mLiveTileTaskViewSimulator.taskPrimaryTranslation.value =
                                    mOrientationHandler.getPrimaryValue(child.getTranslationX(),
                                            child.getTranslationY());
                            redrawLiveTile();
                        });
                    }
                    needsCurveUpdates = true;
                }
            } else if (child instanceof TaskView) {
                // Animate task with index >= dismissed index and in the same row as the
                // dismissed index, or if the dismissed task was the focused task. Offset
                // successive task dismissal durations for a staggered effect.
                if (isFocusedTaskDismissed || (i >= draggedIndex && isSameGridRow((TaskView) child,
                        taskView))) {
                    FloatProperty translationProperty =
                            ((TaskView) child).getPrimaryDismissTranslationProperty();
                    float additionalDismissDuration =
                            ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET * Math.abs(
                                    i - draggedIndex);
                    anim.setFloat(child, translationProperty,
                            !mIsRtl ? -dismissedTaskWidth : dismissedTaskWidth,
                            clampToProgress(LINEAR, Utilities.boundToRange(
                                    INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                            + additionalDismissDuration, 0f, 1f), 1));
                }
            }
        }

        if (needsCurveUpdates) {
            anim.addOnFrameCallback(this::updateCurveProperties);
        }

        // Add a tiny bit of translation Z, so that it draws on top of other views
        if (animateTaskView) {
            taskView.setTranslationZ(0.1f);
        }

        mPendingAnimation = anim;
        mPendingAnimation.addEndListener(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                        && taskView.isRunningTask() && success) {
                    finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                            () -> onEnd(success));
                } else {
                    onEnd(success);
                }
            }

            @SuppressWarnings("WrongCall")
            private void onEnd(boolean success) {
                if (success) {
                    if (shouldRemoveTask) {
                        if (taskView.getTask() != null) {
                            if (ENABLE_QUICKSTEP_LIVE_TILE.get() && taskView.isRunningTask()) {
                                finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                                        () -> removeTaskInternal(taskView));
                            } else {
                                removeTaskInternal(taskView);
                            }
                            mActivity.getStatsLogManager().logger()
                                    .withItemInfo(taskView.getItemInfo())
                                    .log(LAUNCHER_TASK_DISMISS_SWIPE_UP);
                        }
                    }

                    // Reset task translations as they may have updated via animations in
                    // createTaskDismissAnimation
                    resetTaskVisuals();

                    int pageToSnapTo = mCurrentPage;
                    // Snap to start if focused task was dismissed, as after quick switch it could
                    // be at any page but the focused task always displays at the start.
                    if (taskView.getTask().key.id == mFocusedTaskId) {
                        pageToSnapTo = mTaskViewStartIndex;
                    } else if (draggedIndex < pageToSnapTo || pageToSnapTo == (getTaskViewCount()
                            - 1)) {
                        pageToSnapTo -= 1;
                    }
                    removeViewInLayout(taskView);

                    if (getTaskViewCount() == 0) {
                        removeViewInLayout(mClearAllButton);
                        startHome();
                    } else {
                        snapToPageImmediately(pageToSnapTo);
                        dispatchScrollChanged();
                        // Grid got messed up, reapply.
                        updateGridProperties(true);
                        if (showAsGrid() && getFocusedTaskView() == null
                                && mActionsView.getVisibilityAlpha().getValue() == 1) {
                            animateActionsViewOut();
                        }
                    }
                    // Update the layout synchronously so that the position of next view is
                    // immediately available.
                    onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());
                }
                onDismissAnimationEnds();
                mPendingAnimation = null;
            }
        });
        return anim;
    }

    private void removeTaskInternal(TaskView taskView) {
        UI_HELPER_EXECUTOR.getHandler().postDelayed(() ->
                        ActivityManagerWrapper.getInstance().removeTask(
                                taskView.getTask().key.id),
                REMOVE_TASK_WAIT_FOR_APP_STOP_MS);
    }

    /**
     * @return {@code true} if one of the task thumbnails would intersect/overlap with the
     *         {@link #mSplitPlaceholderView}
     */
    public boolean shouldShiftThumbnailsForSplitSelect(@SplitConfigurationOptions.StagePosition
            int stagePosition) {
        if (!mActivity.getDeviceProfile().isTablet) {
            // Never enough space on phones
            return true;
        } else if (!mActivity.getDeviceProfile().isLandscape) {
            return false;
        }

        Rect splitBounds = new Rect();
        float placeholderSize = getResources().getDimension(R.dimen.split_placeholder_size);
        // This acts as a best approximation on where the splitplaceholder view would be,
        // doesn't need to be exact necessarily. This also doesn't need to take translations
        // into account since placeholder view is not translated
        if (stagePosition == SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT) {
            splitBounds.set((int) (getWidth() - placeholderSize), 0, getWidth(), getHeight());
        } else {
            splitBounds.set(0, 0, (int) (placeholderSize), getHeight());
        }
        Rect taskBounds = new Rect();
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = getTaskViewAt(i);
            if (taskView == mSplitHiddenTaskView && taskView != getFocusedTaskView()) {
                // Case where the hidden task view would have overlapped w/ placeholder,
                // but because it's going to hide we don't care
                // TODO (b/187312247) edge case for thumbnails that are off screen but scroll on
                continue;
            }
            taskView.getBoundsOnScreen(taskBounds);
            if (Rect.intersects(taskBounds, splitBounds)) {
                return true;
            }
        }
        return false;
    }

    protected void onDismissAnimationEnds() {
    }

    public PendingAnimation createAllTasksDismissAnimation(long duration) {
        if (FeatureFlags.IS_STUDIO_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
        }
        PendingAnimation anim = new PendingAnimation(duration);

        int count = getTaskViewCount();
        for (int i = 0; i < count; i++) {
            addDismissedTaskAnimations(getTaskViewAt(i), duration, anim);
        }

        mPendingAnimation = anim;
        mPendingAnimation.addEndListener(isSuccess -> {
            if (isSuccess) {
                // Remove all the task views now
                finishRecentsAnimation(true /* toRecents */, false /* shouldPip */, () -> {
                    UI_HELPER_EXECUTOR.getHandler().postDelayed(
                            ActivityManagerWrapper.getInstance()::removeAllRecentTasks,
                            REMOVE_TASK_WAIT_FOR_APP_STOP_MS);
                    removeTasksViewsAndClearAllButton();
                    startHome();
                });
            }
            mPendingAnimation = null;
        });
        return anim;
    }

    private boolean snapToPageRelative(int pageCount, int delta, boolean cycle) {
        if (pageCount == 0) {
            return false;
        }
        final int newPageUnbound = getNextPage() + delta;
        if (!cycle && (newPageUnbound < 0 || newPageUnbound >= pageCount)) {
            return false;
        }
        snapToPage((newPageUnbound + pageCount) % pageCount);
        getChildAt(getNextPage()).requestFocus();
        return true;
    }

    private void runDismissAnimation(PendingAnimation pendingAnim) {
        AnimatorPlaybackController controller = pendingAnim.createPlaybackController();
        controller.dispatchOnStart();
        controller.getAnimationPlayer().setInterpolator(FAST_OUT_SLOW_IN);
        controller.start();
    }

    @UiThread
    private void dismissTask(int taskId) {
        TaskView taskView = getTaskView(taskId);
        if (taskView == null) {
            return;
        }
        dismissTask(taskView, true /* animate */, false /* removeTask */);
    }

    public void dismissTask(TaskView taskView, boolean animateTaskView, boolean removeTask) {
        runDismissAnimation(createTaskDismissAnimation(taskView, animateTaskView, removeTask,
                DISMISS_TASK_DURATION));
    }

    @SuppressWarnings("unused")
    private void dismissAllTasks(View view) {
        runDismissAnimation(createAllTasksDismissAnimation(DISMISS_TASK_DURATION));
        mActivity.getStatsLogManager().logger().log(LAUNCHER_TASK_CLEAR_ALL);
    }

    private void dismissCurrentTask() {
        TaskView taskView = getNextPageTaskView();
        if (taskView != null) {
            dismissTask(taskView, true /*animateTaskView*/, true /*removeTask*/);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_TAB:
                    return snapToPageRelative(getTaskViewCount(), event.isShiftPressed() ? -1 : 1,
                            event.isAltPressed() /* cycle */);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return snapToPageRelative(getPageCount(), mIsRtl ? -1 : 1, false /* cycle */);
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return snapToPageRelative(getPageCount(), mIsRtl ? 1 : -1, false /* cycle */);
                case KeyEvent.KEYCODE_DEL:
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    dismissCurrentTask();
                    return true;
                case KeyEvent.KEYCODE_NUMPAD_DOT:
                    if (event.isAltPressed()) {
                        // Numpad DEL pressed while holding Alt.
                        dismissCurrentTask();
                        return true;
                    }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction,
            @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && getChildCount() > 0) {
            switch (direction) {
                case FOCUS_FORWARD:
                    setCurrentPage(0);
                    break;
                case FOCUS_BACKWARD:
                case FOCUS_RIGHT:
                case FOCUS_LEFT:
                    setCurrentPage(getChildCount() - 1);
                    break;
            }
        }
    }

    public float getContentAlpha() {
        return mContentAlpha;
    }

    public void setContentAlpha(float alpha) {
        if (alpha == mContentAlpha) {
            return;
        }
        alpha = Utilities.boundToRange(alpha, 0, 1);
        mContentAlpha = alpha;
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView child = getTaskViewAt(i);
            if (!mRunningTaskTileHidden || child.getTask().key.id != mRunningTaskId) {
                child.setStableAlpha(alpha);
            }
        }
        mClearAllButton.setContentAlpha(mContentAlpha);
        int alphaInt = Math.round(alpha * 255);
        mEmptyMessagePaint.setAlpha(alphaInt);
        mEmptyIcon.setAlpha(alphaInt);
        mActionsView.getContentAlpha().setValue(mContentAlpha);

        if (alpha > 0) {
            setVisibility(VISIBLE);
        } else if (!mFreezeViewVisibility) {
            setVisibility(INVISIBLE);
        }
    }

    /**
     * Freezes the view visibility change. When frozen, the view will not change its visibility
     * to gone due to alpha changes.
     */
    public void setFreezeViewVisibility(boolean freezeViewVisibility) {
        if (mFreezeViewVisibility != freezeViewVisibility) {
            mFreezeViewVisibility = freezeViewVisibility;
            if (!mFreezeViewVisibility) {
                setVisibility(mContentAlpha > 0 ? VISIBLE : INVISIBLE);
            }
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mActionsView != null) {
            mActionsView.updateHiddenFlags(HIDDEN_NO_RECENTS, visibility != VISIBLE);
            if (visibility != VISIBLE) {
                mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, false);
            }
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRecentsRotation();
    }

    /**
     * Updates {@link RecentsOrientedState}'s cached RecentsView rotation.
     */
    public void updateRecentsRotation() {
        final int rotation = mActivity.getDisplay().getRotation();
        mOrientationState.setRecentsRotation(rotation);
    }

    public void setLayoutRotation(int touchRotation, int displayRotation) {
        if (mOrientationState.update(touchRotation, displayRotation)) {
            updateOrientationHandler();
        }
    }

    public RecentsOrientedState getPagedViewOrientedState() {
        return mOrientationState;
    }

    public PagedOrientationHandler getPagedOrientationHandler() {
        return mOrientationHandler;
    }

    @Nullable
    public TaskView getNextTaskView() {
        return getTaskViewAtByAbsoluteIndex(getRunningTaskIndex() + 1);
    }

    @Nullable
    public TaskView getCurrentPageTaskView() {
        return getTaskViewAtByAbsoluteIndex(getCurrentPage());
    }

    @Nullable
    public TaskView getNextPageTaskView() {
        return getTaskViewAtByAbsoluteIndex(getNextPage());
    }

    @Nullable
    public TaskView getTaskViewNearestToCenterOfScreen() {
        return getTaskViewAtByAbsoluteIndex(getPageNearestToCenterOfScreen());
    }

    /**
     * Returns null instead of indexOutOfBoundsError when index is not in range
     */
    @Nullable
    public TaskView getTaskViewAt(int index) {
        return getTaskViewAtByAbsoluteIndex(index + mTaskViewStartIndex);
    }

    @Nullable
    private TaskView getTaskViewAtByAbsoluteIndex(int index) {
        if (index < getChildCount() && index >= 0) {
            View child = getChildAt(index);
            return child instanceof TaskView ? (TaskView) child : null;
        }
        return null;
    }

    public void setOnEmptyMessageUpdatedListener(OnEmptyMessageUpdatedListener listener) {
        mOnEmptyMessageUpdatedListener = listener;
    }

    public void updateEmptyMessage() {
        boolean isEmpty = getTaskViewCount() == 0;
        boolean hasSizeChanged = mLastMeasureSize.x != getWidth()
                || mLastMeasureSize.y != getHeight();
        if (isEmpty == mShowEmptyMessage && !hasSizeChanged) {
            return;
        }
        setContentDescription(isEmpty ? mEmptyMessage : "");
        mShowEmptyMessage = isEmpty;
        updateEmptyStateUi(hasSizeChanged);
        invalidate();

        if (mOnEmptyMessageUpdatedListener != null) {
            mOnEmptyMessageUpdatedListener.onEmptyMessageUpdated(mShowEmptyMessage);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        updateEmptyStateUi(changed);

        // Update the pivots such that when the task is scaled, it fills the full page
        getTaskSize(mTempRect);
        getPagedViewOrientedState().getFullScreenScaleAndPivot(mTempRect,
                mActivity.getDeviceProfile(), mTempPointF);
        setPivotX(mTempPointF.x);
        setPivotY(mTempPointF.y);
        setTaskModalness(mTaskModalness);
        mLastComputedTaskStartPushOutDistance = null;
        mLastComputedTaskEndPushOutDistance = null;
        updatePageOffsets();
        setImportantForAccessibility(isModal() ? IMPORTANT_FOR_ACCESSIBILITY_NO
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void updatePageOffsets() {
        float offset = mAdjacentPageHorizontalOffset;
        float modalOffset = ACCEL_0_75.getInterpolation(mTaskModalness);
        int count = getChildCount();

        TaskView runningTask = mRunningTaskId == -1 || !mRunningTaskTileHidden
                ? null : getTaskView(mRunningTaskId);
        int midpoint = runningTask == null ? -1 : indexOfChild(runningTask);
        int modalMidpoint = getCurrentPage();

        float midpointOffsetSize = 0;
        float leftOffsetSize = midpoint - 1 >= 0
                ? getHorizontalOffsetSize(midpoint - 1, midpoint, offset)
                : 0;
        float rightOffsetSize = midpoint + 1 < count
                ? getHorizontalOffsetSize(midpoint + 1, midpoint, offset)
                : 0;

        boolean showAsGrid = showAsGrid();
        float modalMidpointOffsetSize = 0;
        float modalLeftOffsetSize = 0;
        float modalRightOffsetSize = 0;
        float gridOffsetSize = 0;

        if (showAsGrid) {
            // In grid, we only focus the task on the side. The reference index used for offset
            // calculation is the task directly next to the focus task in the grid.
            int referenceIndex = modalMidpoint == 0 ? 1 : 0;
            gridOffsetSize = referenceIndex < count
                    ? getHorizontalOffsetSize(referenceIndex, modalMidpoint, modalOffset)
                    : 0;
        } else {
            modalLeftOffsetSize = modalMidpoint - 1 >= 0
                    ? getHorizontalOffsetSize(modalMidpoint - 1, modalMidpoint, modalOffset)
                    : 0;
            modalRightOffsetSize = modalMidpoint + 1 < count
                    ? getHorizontalOffsetSize(modalMidpoint + 1, modalMidpoint, modalOffset)
                    : 0;
        }

        for (int i = 0; i < count; i++) {
            float translation = i == midpoint
                    ? midpointOffsetSize
                    : i < midpoint
                            ? leftOffsetSize
                            : rightOffsetSize;
            float modalTranslation = i == modalMidpoint
                    ? modalMidpointOffsetSize
                    : showAsGrid
                            ? gridOffsetSize
                            : i < modalMidpoint ? modalLeftOffsetSize : modalRightOffsetSize;
            float totalTranslation = translation + modalTranslation;
            View child = getChildAt(i);
            FloatProperty translationProperty = child instanceof TaskView
                    ? ((TaskView) child).getPrimaryTaskOffsetTranslationProperty()
                    : mOrientationHandler.getPrimaryViewTranslate();
            translationProperty.set(child, totalTranslation);
            if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                    && i == getRunningTaskIndex()) {
                mLiveTileTaskViewSimulator.taskPrimaryTranslation.value = totalTranslation;
                redrawLiveTile();
            }
        }
        updateCurveProperties();
    }

    /**
     * Computes the child position with persistent translation considered (see
     * {@link TaskView#getPersistentTranslationX()}.
     */
    private void getPersistentChildPosition(int childIndex, int midPointScroll, RectF outRect) {
        View child = getChildAt(childIndex);
        outRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        if (child instanceof TaskView) {
            TaskView taskView = (TaskView) child;
            outRect.offset(taskView.getPersistentTranslationX(),
                    taskView.getPersistentTranslationY());
            outRect.top += mActivity.getDeviceProfile().overviewTaskThumbnailTopMarginPx;
        }
        outRect.offset(mOrientationHandler.getPrimaryValue(-midPointScroll, 0),
                mOrientationHandler.getSecondaryValue(-midPointScroll, 0));
    }

    /**
     * Computes the distance to offset the given child such that it is completely offscreen when
     * translating away from the given midpoint.
     * @param offsetProgress From 0 to 1 where 0 means no offset and 1 means offset offscreen.
     */
    private float getHorizontalOffsetSize(int childIndex, int midpointIndex, float offsetProgress) {
        if (offsetProgress == 0) {
            // Don't bother calculating everything below if we won't offset anyway.
            return 0;
        }

        // First, get the position of the task relative to the midpoint. If there is no midpoint
        // then we just use the normal (centered) task position.
        RectF taskPosition = mTempRectF;
        // Whether the task should be shifted to start direction (i.e. left edge for portrait, top
        // edge for landscape/seascape).
        boolean isStartShift;
        if (midpointIndex > -1) {
            // When there is a midpoint reference task, adjacent tasks have less distance to travel
            // to reach offscreen. Offset the task position to the task's starting point, and offset
            // by current page's scroll diff.
            int midpointScroll = getScrollForPage(midpointIndex)
                    + mOrientationHandler.getPrimaryScroll(this) - getScrollForPage(mCurrentPage);

            getPersistentChildPosition(midpointIndex, midpointScroll, taskPosition);
            float midpointStart = mOrientationHandler.getStart(taskPosition);

            getPersistentChildPosition(childIndex, midpointScroll, taskPosition);
            // Assume child does not overlap with midPointChild.
            isStartShift = mOrientationHandler.getStart(taskPosition) < midpointStart;
        } else {
            // Position the task at scroll position.
            getPersistentChildPosition(childIndex, getScrollForPage(childIndex), taskPosition);
            isStartShift = mIsRtl;
        }

        // Next, calculate the distance to move the task off screen. We also need to account for
        // RecentsView scale, because it moves tasks based on its pivot. To do this, we move the
        // task position to where it would be offscreen at scale = 1 (computed above), then we
        // apply the scale via getMatrix() to determine how much that moves the task from its
        // desired position, and adjust the computed distance accordingly.
        float distanceToOffscreen;
        if (isStartShift) {
            float desiredStart = -mOrientationHandler.getPrimarySize(taskPosition);
            distanceToOffscreen = -mOrientationHandler.getEnd(taskPosition);
            if (mLastComputedTaskStartPushOutDistance == null) {
                taskPosition.offsetTo(
                        mOrientationHandler.getPrimaryValue(desiredStart, 0f),
                        mOrientationHandler.getSecondaryValue(desiredStart, 0f));
                getMatrix().mapRect(taskPosition);
                mLastComputedTaskStartPushOutDistance = mOrientationHandler.getEnd(taskPosition)
                        / mOrientationHandler.getPrimaryScale(this);
            }
            distanceToOffscreen -= mLastComputedTaskStartPushOutDistance;
        } else {
            float desiredStart = mOrientationHandler.getPrimarySize(this);
            distanceToOffscreen = desiredStart - mOrientationHandler.getStart(taskPosition);
            if (mLastComputedTaskEndPushOutDistance == null) {
                taskPosition.offsetTo(
                        mOrientationHandler.getPrimaryValue(desiredStart, 0f),
                        mOrientationHandler.getSecondaryValue(desiredStart, 0f));
                getMatrix().mapRect(taskPosition);
                mLastComputedTaskEndPushOutDistance = (mOrientationHandler.getStart(taskPosition)
                        - desiredStart) / mOrientationHandler.getPrimaryScale(this);
            }
            distanceToOffscreen -= mLastComputedTaskEndPushOutDistance;
        }
        return distanceToOffscreen * offsetProgress;
    }

    protected void setTaskViewsResistanceTranslation(float translation) {
        mTaskViewsSecondaryTranslation = translation;
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView task = getTaskViewAt(i);
            task.getTaskResistanceTranslationProperty().set(task, translation / getScaleY());
        }
        mLiveTileTaskViewSimulator.recentsViewSecondaryTranslation.value = translation;
    }

    protected void setTaskViewsPrimarySplitTranslation(float translation) {
        mTaskViewsPrimarySplitTranslation = translation;
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView task = getTaskViewAt(i);
            task.getPrimarySplitTranslationProperty().set(task, translation);
        }
    }

    protected void setTaskViewsSecondarySplitTranslation(float translation) {
        mTaskViewsSecondarySplitTranslation = translation;
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView task = getTaskViewAt(i);
            task.getSecondarySplitTranslationProperty().set(task, translation);
        }
    }

    /**
     * Resets the visuals when exit modal state.
     */
    public void resetModalVisuals() {
        TaskView taskView = getCurrentPageTaskView();
        if (taskView != null) {
            taskView.getThumbnail().getTaskOverlay().resetModalVisuals();
        }
    }

    public void initiateSplitSelect(TaskView taskView, SplitPositionOption splitPositionOption) {
        mSplitHiddenTaskView = taskView;
        SplitSelectStateController splitController = mSplitPlaceholderView.getSplitController();
        Rect initialBounds = new Rect(taskView.getLeft(), taskView.getTop(), taskView.getRight(),
                taskView.getBottom());
        splitController.setInitialTaskSelect(taskView, splitPositionOption, initialBounds);
        mSplitHiddenTaskViewIndex = indexOfChild(taskView);
        mSplitPlaceholderView.setLayoutParams(
                splitController.getLayoutParamsForActivePosition(getResources(),
                        mActivity.getDeviceProfile()));
        mSplitPlaceholderView.setIcon(taskView.getIconView());
    }

    public PendingAnimation createSplitSelectInitAnimation() {
        int duration = mActivity.getStateManager().getState().getTransitionDuration(getContext());
        return createTaskDismissAnimation(mSplitHiddenTaskView, true, false, duration);
    }

    public void confirmSplitSelect(TaskView taskView) {
        mSplitPlaceholderView.getSplitController().setSecondTaskId(taskView);
        resetTaskVisuals();
        setTranslationY(0);
    }

    public PendingAnimation cancelSplitSelect(boolean animate) {
        SplitSelectStateController splitController = mSplitPlaceholderView.getSplitController();
        SplitPositionOption splitOption = splitController.getActiveSplitPositionOption();
        Rect initialBounds = splitController.getInitialBounds();
        splitController.resetState();
        int duration = mActivity.getStateManager().getState().getTransitionDuration(getContext());
        PendingAnimation pendingAnim = new PendingAnimation(duration);
        if (!animate) {
            resetFromSplitSelectionState();
            return pendingAnim;
        }

        addViewInLayout(mSplitHiddenTaskView, mSplitHiddenTaskViewIndex,
                mSplitHiddenTaskView.getLayoutParams());
        mSplitHiddenTaskView.setAlpha(0);
        int[] oldScroll = new int[getChildCount()];
        getPageScrolls(oldScroll, false,
                view -> view.getVisibility() != GONE && view != mSplitHiddenTaskView);

        int[] newScroll = new int[getChildCount()];
        getPageScrolls(newScroll, false, SIMPLE_SCROLL_LOGIC);

        boolean needsCurveUpdates = false;
        for (int i = mSplitHiddenTaskViewIndex; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == mSplitHiddenTaskView) {
                TaskView taskView = (TaskView) child;

                int dir = mOrientationHandler.getSplitTaskViewDismissDirection(splitOption,
                        mActivity.getDeviceProfile());
                FloatProperty<TaskView> dismissingTaskViewTranslate;
                Rect hiddenBounds = new Rect(taskView.getLeft(), taskView.getTop(),
                        taskView.getRight(), taskView.getBottom());
                int distanceDelta = 0;
                if (dir == PagedOrientationHandler.SPLIT_TRANSLATE_SECONDARY_NEGATIVE) {
                    dismissingTaskViewTranslate = taskView
                            .getSecondaryDissmissTranslationProperty();
                    distanceDelta = initialBounds.top - hiddenBounds.top;
                    taskView.layout(initialBounds.left, hiddenBounds.top, initialBounds.right,
                            hiddenBounds.bottom);
                } else {
                    dismissingTaskViewTranslate = taskView
                            .getPrimaryDismissTranslationProperty();
                    distanceDelta = initialBounds.left - hiddenBounds.left;
                    taskView.layout(hiddenBounds.left, initialBounds.top, hiddenBounds.right,
                            initialBounds.bottom);
                    if (dir == PagedOrientationHandler.SPLIT_TRANSLATE_PRIMARY_POSITIVE) {
                        distanceDelta *= -1;
                    }
                }
                pendingAnim.add(ObjectAnimator.ofFloat(mSplitHiddenTaskView,
                        dismissingTaskViewTranslate,
                        distanceDelta));
                pendingAnim.add(ObjectAnimator.ofFloat(mSplitHiddenTaskView, ALPHA, 1));
            } else {
                // If insertion is on last index (furthest from clear all), we directly add the view
                // else we translate all views to the right of insertion index further right,
                // ignore views to left
                if (showAsGrid()) {
                    // TODO(b/186800707) handle more elegantly for grid
                    continue;
                }
                int scrollDiff = newScroll[i] - oldScroll[i];
                if (scrollDiff != 0) {
                    FloatProperty translationProperty = child instanceof TaskView
                            ? ((TaskView) child).getPrimaryDismissTranslationProperty()
                            : mOrientationHandler.getPrimaryViewTranslate();

                    ResourceProvider rp = DynamicResource.provider(mActivity);
                    SpringProperty sp = new SpringProperty(SpringProperty.FLAG_CAN_SPRING_ON_END)
                            .setDampingRatio(
                                    rp.getFloat(R.dimen.dismiss_task_trans_x_damping_ratio))
                            .setStiffness(rp.getFloat(R.dimen.dismiss_task_trans_x_stiffness));
                    pendingAnim.add(ObjectAnimator.ofFloat(child, translationProperty, scrollDiff)
                            .setDuration(duration), ACCEL, sp);
                    needsCurveUpdates = true;
                }
            }
        }

        if (needsCurveUpdates) {
            pendingAnim.addOnFrameCallback(this::updateCurveProperties);
        }

        pendingAnim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                // TODO(b/186800707) Figure out how to undo for grid view
                //  Need to handle cases where dismissed task is
                //  * Top Row
                //  * Bottom Row
                //  * Focused Task
                updateGridProperties();
                resetFromSplitSelectionState();
            }
        });

        return pendingAnim;
    }

    private void resetFromSplitSelectionState() {
        mSplitHiddenTaskView.setTranslationY(0);
        if (!showAsGrid()) {
            // TODO(b/186800707)
            int pageToSnapTo = mCurrentPage;
            if (mSplitHiddenTaskViewIndex <= pageToSnapTo) {
                pageToSnapTo += 1;
            } else {
                pageToSnapTo = mSplitHiddenTaskViewIndex;
            }
            snapToPageImmediately(pageToSnapTo);
        }
        onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());
        resetTaskVisuals();
        mSplitHiddenTaskView = null;
        mSplitHiddenTaskViewIndex = -1;
    }

    private void updateDeadZoneRects() {
        // Get the deadzone rect surrounding the clear all button to not dismiss overview to home
        mClearAllButtonDeadZoneRect.setEmpty();
        if (mClearAllButton.getWidth() > 0) {
            int verticalMargin = getResources()
                    .getDimensionPixelSize(R.dimen.recents_clear_all_deadzone_vertical_margin);
            mClearAllButton.getHitRect(mClearAllButtonDeadZoneRect);
            mClearAllButtonDeadZoneRect.inset(-getPaddingRight() / 2, -verticalMargin);
        }

        // Get the deadzone rect between the task views
        mTaskViewDeadZoneRect.setEmpty();
        int count = getTaskViewCount();
        if (count > 0) {
            final View taskView = getTaskViewAt(0);
            getTaskViewAt(count - 1).getHitRect(mTaskViewDeadZoneRect);
            mTaskViewDeadZoneRect.union(taskView.getLeft(), taskView.getTop(), taskView.getRight(),
                    taskView.getBottom());
        }
    }

    private void updateEmptyStateUi(boolean sizeChanged) {
        boolean hasValidSize = getWidth() > 0 && getHeight() > 0;
        if (sizeChanged && hasValidSize) {
            mEmptyTextLayout = null;
            mLastMeasureSize.set(getWidth(), getHeight());
        }

        if (mShowEmptyMessage && hasValidSize && mEmptyTextLayout == null) {
            int availableWidth = mLastMeasureSize.x - mEmptyMessagePadding - mEmptyMessagePadding;
            mEmptyTextLayout = StaticLayout.Builder.obtain(mEmptyMessage, 0, mEmptyMessage.length(),
                    mEmptyMessagePaint, availableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build();
            int totalHeight = mEmptyTextLayout.getHeight()
                    + mEmptyMessagePadding + mEmptyIcon.getIntrinsicHeight();

            int top = (mLastMeasureSize.y - totalHeight) / 2;
            int left = (mLastMeasureSize.x - mEmptyIcon.getIntrinsicWidth()) / 2;
            mEmptyIcon.setBounds(left, top, left + mEmptyIcon.getIntrinsicWidth(),
                    top + mEmptyIcon.getIntrinsicHeight());
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (mShowEmptyMessage && who == mEmptyIcon);
    }

    protected void maybeDrawEmptyMessage(Canvas canvas) {
        if (mShowEmptyMessage && mEmptyTextLayout != null) {
            // Offset to center in the visible (non-padded) part of RecentsView
            mTempRect.set(mInsets.left + getPaddingLeft(), mInsets.top + getPaddingTop(),
                    mInsets.right + getPaddingRight(), mInsets.bottom + getPaddingBottom());
            canvas.save();
            canvas.translate(getScrollX() + (mTempRect.left - mTempRect.right) / 2,
                    (mTempRect.top - mTempRect.bottom) / 2);
            mEmptyIcon.draw(canvas);
            canvas.translate(mEmptyMessagePadding,
                    mEmptyIcon.getBounds().bottom + mEmptyMessagePadding);
            mEmptyTextLayout.draw(canvas);
            canvas.restore();
        }
    }

    /**
     * Animate adjacent tasks off screen while scaling up.
     *
     * If launching one of the adjacent tasks, parallax the center task and other adjacent task
     * to the right.
     */
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(TaskView tv) {
        AnimatorSet anim = new AnimatorSet();

        int taskIndex = indexOfChild(tv);
        int centerTaskIndex = getCurrentPage();
        boolean launchingCenterTask = taskIndex == centerTaskIndex;

        float toScale = getMaxScaleForFullScreen();
        RecentsView recentsView = tv.getRecentsView();
        if (launchingCenterTask) {
            anim.play(ObjectAnimator.ofFloat(recentsView, RECENTS_SCALE_PROPERTY, toScale));
            anim.play(ObjectAnimator.ofFloat(recentsView, FULLSCREEN_PROGRESS, 1));
        } else {
            // We are launching an adjacent task, so parallax the center and other adjacent task.
            float displacementX = tv.getWidth() * (toScale - 1f);
            float primaryTranslation = mIsRtl ? -displacementX : displacementX;
            anim.play(ObjectAnimator.ofFloat(getPageAt(centerTaskIndex),
                    mOrientationHandler.getPrimaryViewTranslate(), primaryTranslation));
            int runningTaskIndex = recentsView.getRunningTaskIndex();
            if (ENABLE_QUICKSTEP_LIVE_TILE.get() && runningTaskIndex != -1
                    && runningTaskIndex != taskIndex) {
                anim.play(ObjectAnimator.ofFloat(
                        recentsView.getLiveTileTaskViewSimulator().taskPrimaryTranslation,
                        AnimatedFloat.VALUE,
                        primaryTranslation));
            }

            int otherAdjacentTaskIndex = centerTaskIndex + (centerTaskIndex - taskIndex);
            if (otherAdjacentTaskIndex >= 0 && otherAdjacentTaskIndex < getPageCount()) {
                PropertyValuesHolder[] properties = new PropertyValuesHolder[3];
                properties[0] = PropertyValuesHolder.ofFloat(
                        mOrientationHandler.getPrimaryViewTranslate(), primaryTranslation);
                properties[1] = PropertyValuesHolder.ofFloat(View.SCALE_X, 1);
                properties[2] = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1);

                anim.play(ObjectAnimator.ofPropertyValuesHolder(getPageAt(otherAdjacentTaskIndex),
                        properties));
            }
        }
        return anim;
    }

    /**
     * Returns the scale up required on the view, so that it coves the screen completely
     */
    public float getMaxScaleForFullScreen() {
        getTaskSize(mTempRect);
        return getPagedViewOrientedState().getFullScreenScaleAndPivot(
                mTempRect, mActivity.getDeviceProfile(), mTempPointF);
    }

    public PendingAnimation createTaskLaunchAnimation(
            TaskView tv, long duration, Interpolator interpolator) {
        if (FeatureFlags.IS_STUDIO_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
        }

        int count = getTaskViewCount();
        if (count == 0) {
            return new PendingAnimation(duration);
        }

        // When swiping down from overview to tasks, ensures the snapped page's scroll maintain
        // invariant between quick switch and overview, to ensure a smooth animation transition.
        updateGridProperties();
        updateScrollSynchronously();

        int targetSysUiFlags = tv.getThumbnail().getSysUiStatusNavFlags();
        final boolean[] passedOverviewThreshold = new boolean[] {false};
        ValueAnimator progressAnim = ValueAnimator.ofFloat(0, 1);
        progressAnim.addUpdateListener(animator -> {
            // Once we pass a certain threshold, update the sysui flags to match the target
            // tasks' flags
            if (animator.getAnimatedFraction() > UPDATE_SYSUI_FLAGS_THRESHOLD) {
                mActivity.getSystemUiController().updateUiState(
                        UI_STATE_FULLSCREEN_TASK, targetSysUiFlags);
            } else {
                mActivity.getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
            }

            // Passing the threshold from taskview to fullscreen app will vibrate
            final boolean passed = animator.getAnimatedFraction() >=
                    SUCCESS_TRANSITION_PROGRESS;
            if (passed != passedOverviewThreshold[0]) {
                passedOverviewThreshold[0] = passed;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        });

        AnimatorSet anim = createAdjacentPageAnimForTaskLaunch(tv);

        DepthController depthController = getDepthController();
        if (depthController != null) {
            ObjectAnimator depthAnimator = ObjectAnimator.ofFloat(depthController, DEPTH,
                    BACKGROUND_APP.getDepth(mActivity));
            anim.play(depthAnimator);
        }
        anim.play(progressAnim);
        anim.setInterpolator(interpolator);

        mPendingAnimation = new PendingAnimation(duration);
        mPendingAnimation.add(anim);
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mLiveTileTaskViewSimulator.addOverviewToAppAnim(mPendingAnimation, interpolator);
            mPendingAnimation.addOnFrameCallback(this::redrawLiveTile);
        }
        mPendingAnimation.addEndListener(isSuccess -> {
            if (isSuccess) {
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && tv.isRunningTask()) {
                    finishRecentsAnimation(false /* toRecents */, null);
                    onTaskLaunchAnimationEnd(true /* success */);
                } else {
                    tv.launchTask(this::onTaskLaunchAnimationEnd);
                }
                Task task = tv.getTask();
                if (task != null) {
                    mActivity.getStatsLogManager().logger().withItemInfo(tv.getItemInfo())
                            .log(LAUNCHER_TASK_LAUNCH_SWIPE_DOWN);
                }
            } else {
                onTaskLaunchAnimationEnd(false);
            }
            mPendingAnimation = null;
        });
        return mPendingAnimation;
    }

    protected void onTaskLaunchAnimationEnd(boolean success) {
        if (success) {
            resetTaskVisuals();
        }
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);
        updateEnabledOverlays();
    }

    @Override
    protected String getCurrentPageDescription() {
        return "";
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> outChildren) {
        // Add children in reverse order
        for (int i = getChildCount() - 1; i >= 0; --i) {
            outChildren.add(getChildAt(i));
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final AccessibilityNodeInfo.CollectionInfo
                collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                1, getTaskViewCount(), false,
                AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_NONE);
        info.setCollectionInfo(collectionInfo);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);

        final int taskViewCount = getTaskViewCount();
        event.setScrollable(taskViewCount > 0);

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            final int[] visibleTasks = getVisibleChildrenRange();
            event.setFromIndex(taskViewCount - visibleTasks[1]);
            event.setToIndex(taskViewCount - visibleTasks[0]);
            event.setItemCount(taskViewCount);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        // To hear position-in-list related feedback from Talkback.
        return ListView.class.getName();
    }

    @Override
    protected boolean isPageOrderFlipped() {
        return true;
    }

    public void setEnableDrawingLiveTile(boolean enableDrawingLiveTile) {
        mEnableDrawingLiveTile = enableDrawingLiveTile;
    }

    public void redrawLiveTile() {
        if (mLiveTileParams.getTargetSet() != null) {
            mLiveTileTaskViewSimulator.apply(mLiveTileParams);
        }
    }

    public TaskViewSimulator getLiveTileTaskViewSimulator() {
        return mLiveTileTaskViewSimulator;
    }

    public TransformParams getLiveTileParams() {
        return mLiveTileParams;
    }

    // TODO: To be removed in a follow up CL
    public void setRecentsAnimationTargets(RecentsAnimationController recentsAnimationController,
            RecentsAnimationTargets recentsAnimationTargets) {
        mRecentsAnimationController = recentsAnimationController;
        if (recentsAnimationTargets != null && recentsAnimationTargets.apps.length > 0) {
            if (mSyncTransactionApplier != null) {
                recentsAnimationTargets.addReleaseCheck(mSyncTransactionApplier);
            }
            mLiveTileTaskViewSimulator.setPreview(
                    recentsAnimationTargets.apps[recentsAnimationTargets.apps.length - 1]);
            mLiveTileParams.setTargetSet(recentsAnimationTargets);
        }
    }

    public void finishRecentsAnimation(boolean toRecents, Runnable onFinishComplete) {
        finishRecentsAnimation(toRecents, true /* shouldPip */, onFinishComplete);
    }

    public void finishRecentsAnimation(boolean toRecents, boolean shouldPip,
            Runnable onFinishComplete) {
        if (!toRecents && ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            // Reset the minimized state since we force-toggled the minimized state when entering
            // overview, but never actually finished the recents animation.  This is a catch all for
            // cases where we haven't already reset it.
            SystemUiProxy p = SystemUiProxy.INSTANCE.getNoCreate();
            if (p != null) {
                p.setSplitScreenMinimized(false);
            }
        }

        if (mRecentsAnimationController == null) {
            if (onFinishComplete != null) {
                onFinishComplete.run();
            }
            return;
        }

        final boolean sendUserLeaveHint = toRecents && shouldPip;
        if (sendUserLeaveHint) {
            // Notify the SysUI to use fade-in animation when entering PiP from live tile.
            final SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(getContext());
            systemUiProxy.notifySwipeToHomeFinished();
            systemUiProxy.setShelfHeight(true, mActivity.getDeviceProfile().hotseatBarSizePx);
        }
        mRecentsAnimationController.finish(toRecents, () -> {
            if (onFinishComplete != null) {
                onFinishComplete.run();
            }
            onRecentsAnimationComplete();
        }, sendUserLeaveHint);
    }

    /**
     * Called when a running recents animation has finished or canceled.
     */
    public void onRecentsAnimationComplete() {
        // At this point, the recents animation is not running and if the animation was canceled
        // by a display rotation then reset this state to show the screenshot
        setRunningTaskViewShowScreenshot(true);
        // After we finish the recents animation, the current task id should be correctly
        // reset so that when the task is launched from Overview later, it goes through the
        // flow of starting a new task instead of finishing recents animation to app. A
        // typical example of this is (1) user swipes up from app to Overview (2) user
        // taps on QSB (3) user goes back to Overview and launch the most recent task.
        setCurrentTask(-1);
        mRecentsAnimationController = null;
        executeSideTaskLaunchCallback();
    }

    public void setDisallowScrollToClearAll(boolean disallowScrollToClearAll) {
        if (mDisallowScrollToClearAll != disallowScrollToClearAll) {
            mDisallowScrollToClearAll = disallowScrollToClearAll;
            updateMinAndMaxScrollX();
        }
    }

    /**
     * Updates page scroll synchronously and layout child views.
     */
    public void updateScrollSynchronously() {
        onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());
        updateMinAndMaxScrollX();
    }

    @Override
    protected int computeMinScroll() {
        if (getTaskViewCount() > 0) {
            if (mIsRtl) {
                // If we aren't showing the clear all button, use the rightmost task as the min
                // scroll.
                return getScrollForPage(mDisallowScrollToClearAll ? indexOfChild(
                        getTaskViewAt(getTaskViewCount() - 1)) : indexOfChild(mClearAllButton));
            } else {
                TaskView focusedTaskView = showAsGrid() ? getFocusedTaskView() : null;
                return getScrollForPage(focusedTaskView != null ? indexOfChild(focusedTaskView)
                        : mTaskViewStartIndex);
            }
        }
        return super.computeMinScroll();
    }

    @Override
    protected int computeMaxScroll() {
        if (getTaskViewCount() > 0) {
            if (mIsRtl) {
                TaskView focusedTaskView = showAsGrid() ? getFocusedTaskView() : null;
                return getScrollForPage(focusedTaskView != null ? indexOfChild(focusedTaskView)
                        : mTaskViewStartIndex);
            } else {
                // If we aren't showing the clear all button, use the leftmost task as the min
                // scroll.
                return getScrollForPage(mDisallowScrollToClearAll ? indexOfChild(
                        getTaskViewAt(getTaskViewCount() - 1)) : indexOfChild(mClearAllButton));
            }
        }
        return super.computeMaxScroll();
    }

    /**
     * Returns page scroll of ClearAllButton.
     */
    public int getClearAllScroll() {
        return getScrollForPage(indexOfChild(mClearAllButton));
    }

    @Override
    protected boolean getPageScrolls(int[] outPageScrolls, boolean layoutChildren,
            ComputePageScrollsLogic scrollLogic) {
        int[] newPageScrolls = new int[outPageScrolls.length];
        super.getPageScrolls(newPageScrolls, layoutChildren, scrollLogic);
        boolean showAsFullscreen = showAsFullscreen();
        boolean showAsGrid = showAsGrid();

        // Align ClearAllButton to the left (RTL) or right (non-RTL), which is different from other
        // TaskViews. This must be called after laying out ClearAllButton.
        if (layoutChildren) {
            int clearAllWidthDiff = mOrientationHandler.getPrimaryValue(mTaskWidth, mTaskHeight)
                    - mOrientationHandler.getPrimarySize(mClearAllButton);
            mClearAllButton.setScrollOffsetPrimary(mIsRtl ? clearAllWidthDiff : -clearAllWidthDiff);
        }

        boolean pageScrollChanged = false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            float scrollDiff = 0;
            if (child instanceof TaskView) {
                scrollDiff = ((TaskView) child).getScrollAdjustment(showAsFullscreen, showAsGrid);
            } else if (child instanceof ClearAllButton) {
                scrollDiff = ((ClearAllButton) child).getScrollAdjustment(showAsFullscreen,
                        showAsGrid);
            }

            final int pageScroll = newPageScrolls[i] + (int) scrollDiff;
            if (outPageScrolls[i] != pageScroll) {
                pageScrollChanged = true;
                outPageScrolls[i] = pageScroll;
            }
        }
        return pageScrollChanged;
    }

    @Override
    protected int getChildOffset(int index) {
        int childOffset = super.getChildOffset(index);
        View child = getChildAt(index);
        if (child instanceof TaskView) {
            childOffset += ((TaskView) child).getOffsetAdjustment(showAsFullscreen(),
                    showAsGrid());
        } else if (child instanceof ClearAllButton) {
            childOffset += ((ClearAllButton) child).getOffsetAdjustment(mOverviewFullscreenEnabled,
                    showAsGrid());
        }
        return childOffset;
    }

    @Override
    protected int getChildVisibleSize(int index) {
        final TaskView taskView = getTaskViewAtByAbsoluteIndex(index);
        if (taskView == null) {
            return super.getChildVisibleSize(index);
        }
        return (int) (super.getChildVisibleSize(index) * taskView.getSizeAdjustment(
                showAsFullscreen()));
    }

    public ClearAllButton getClearAllButton() {
        return mClearAllButton;
    }

    /**
     * @return How many pixels the running task is offset on the currently laid out dominant axis.
     */
    public int getScrollOffset() {
        return getScrollOffset(getRunningTaskIndex());
    }

    /**
     * Returns how many pixels the page is offset on the currently laid out dominant axis.
     */
    public int getScrollOffset(int pageIndex) {
        if (pageIndex == -1) {
            return 0;
        }

        int overScrollShift = getOverScrollShift();
        if (mAdjacentPageHorizontalOffset > 0) {
            // Don't dampen the scroll (due to overscroll) if the adjacent tasks are offscreen, so
            // that the page can move freely given there's no visual indication why it shouldn't.
            overScrollShift = (int) Utilities.mapRange(mAdjacentPageHorizontalOffset,
                    overScrollShift, getUndampedOverScrollShift());
        }
        return getScrollForPage(pageIndex) - mOrientationHandler.getPrimaryScroll(this)
                + overScrollShift;
    }

    /**
     * Returns how many pixels the task is offset on the currently laid out secondary axis
     * according to {@link #mGridProgress}.
     */
    public float getGridTranslationSecondary(int pageIndex) {
        TaskView taskView = getTaskViewAtByAbsoluteIndex(pageIndex);
        if (taskView == null) {
            return 0;
        }

        return mOrientationHandler.getSecondaryValue(taskView.getGridTranslationX(),
                taskView.getGridTranslationY());
    }

    public Consumer<MotionEvent> getEventDispatcher(float navbarRotation) {
        float degreesRotated;
        if (navbarRotation == 0) {
            degreesRotated = mOrientationHandler.getDegreesRotated();
        } else {
            degreesRotated = -navbarRotation;
        }
        if (degreesRotated == 0) {
            return super::onTouchEvent;
        }

        // At this point the event coordinates have already been transformed, so we need to
        // undo that transformation since PagedView also accommodates for the transformation via
        // PagedOrientationHandler
        return e -> {
            if (navbarRotation != 0
                    && mOrientationState.isMultipleOrientationSupportedByDevice()
                    && !mOrientationState.getOrientationHandler().isLayoutNaturalToLauncher()) {
                mOrientationState.flipVertical(e);
                super.onTouchEvent(e);
                mOrientationState.flipVertical(e);
                return;
            }
            mOrientationState.transformEvent(-degreesRotated, e, true);
            super.onTouchEvent(e);
            mOrientationState.transformEvent(-degreesRotated, e, false);
        };
    }

    private void updateEnabledOverlays() {
        int overlayEnabledPage = mOverlayEnabled ? getNextPage() : -1;
        int taskCount = getTaskViewCount();
        for (int i = mTaskViewStartIndex; i < mTaskViewStartIndex + taskCount; i++) {
            getTaskViewAtByAbsoluteIndex(i).setOverlayEnabled(i == overlayEnabledPage);
        }
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        if (mOverlayEnabled != overlayEnabled) {
            mOverlayEnabled = overlayEnabled;
            updateEnabledOverlays();
        }
    }

    public void setOverviewGridEnabled(boolean overviewGridEnabled) {
        if (mOverviewGridEnabled != overviewGridEnabled) {
            mOverviewGridEnabled = overviewGridEnabled;
            // Request layout to ensure scroll position is recalculated with updated mGridProgress.
            requestLayout();
        }
    }

    public void setOverviewFullscreenEnabled(boolean overviewFullscreenEnabled) {
        if (mOverviewFullscreenEnabled != overviewFullscreenEnabled) {
            mOverviewFullscreenEnabled = overviewFullscreenEnabled;
            // Request layout to ensure scroll position is recalculated with updated
            // mFullscreenProgress.
            requestLayout();
        }
    }

    /**
     * Switch the current running task view to static snapshot mode,
     * capturing the snapshot at the same time.
     */
    public void switchToScreenshot(Runnable onFinishRunnable) {
        if (mRecentsAnimationController == null) {
            if (onFinishRunnable != null) {
                onFinishRunnable.run();
            }
            return;
        }
        switchToScreenshot(mRunningTaskId == -1 ? null
                : mRecentsAnimationController.screenshotTask(mRunningTaskId), onFinishRunnable);
    }

    /**
     * Switch the current running task view to static snapshot mode, using the
     * provided thumbnail data as the snapshot.
     */
    public void switchToScreenshot(ThumbnailData thumbnailData, Runnable onFinishRunnable) {
        TaskView taskView = getRunningTaskView();
        if (taskView != null) {
            taskView.setShowScreenshot(true);
            if (thumbnailData != null) {
                taskView.getThumbnail().setThumbnail(taskView.getTask(), thumbnailData);
            } else {
                taskView.getThumbnail().refresh();
            }
            ViewUtils.postFrameDrawn(taskView, onFinishRunnable);
        } else {
            onFinishRunnable.run();
        }
    }

    /**
     * The current task is fully modal (modalness = 1) when it is shown on its own in a modal
     * way. Modalness 0 means the task is shown in context with all the other tasks.
     */
    private void setTaskModalness(float modalness) {
        mTaskModalness = modalness;
        updatePageOffsets();
        if (getCurrentPageTaskView() != null) {
            getCurrentPageTaskView().setModalness(modalness);
        }
        // Only show actions view when it's modal for in-place landscape mode.
        boolean inPlaceLandscape = !mOrientationState.canRecentsActivityRotate()
                && mOrientationState.getTouchRotation() != ROTATION_0;
        mActionsView.updateHiddenFlags(HIDDEN_NON_ZERO_ROTATION, modalness < 1 && inPlaceLandscape);
        mActionsView.setTaskModalness(modalness);
    }

    @Nullable
    protected DepthController getDepthController() {
        return null;
    }

    @Override
    public void onSecondaryWindowBoundsChanged() {
        // Invalidate the task view size
        setInsets(mInsets);
    }

    /**
     * Enables or disables modal state for RecentsView
     * @param isModalState
     */
    public void setModalStateEnabled(boolean isModalState) { }

    public TaskOverlayFactory getTaskOverlayFactory() {
        return mTaskOverlayFactory;
    }

    public BaseActivityInterface getSizeStrategy() {
        return mSizeStrategy;
    }

    /**
     * Set all the task views to color tint scrim mode, dimming or tinting them all. Allows the
     * tasks to be dimmed while other elements in the recents view are left alone.
     */
    public void showForegroundScrim(boolean show) {
        if (!show && mColorTint == 0) {
            if (mTintingAnimator != null) {
                mTintingAnimator.cancel();
                mTintingAnimator = null;
            }
            return;
        }

        mTintingAnimator = ObjectAnimator.ofFloat(this, COLOR_TINT, show ? 0.5f : 0f);
        mTintingAnimator.setAutoCancel(true);
        mTintingAnimator.start();
    }

    /** Tint the RecentsView and TaskViews in to simulate a scrim. */
    // TODO(b/187528071): Replace this tinting with a scrim on top of RecentsView
    private void setColorTint(float tintAmount) {
        mColorTint = tintAmount;

        for (int i = 0; i < getTaskViewCount(); i++) {
            getTaskViewAt(i).setColorTint(mColorTint, mTintingColor);
        }

        Drawable scrimBg = mActivity.getScrimView().getBackground();
        if (scrimBg != null) {
            if (tintAmount == 0f) {
                scrimBg.setTintList(null);
            } else {
                scrimBg.setTintBlendMode(BlendMode.SRC_OVER);
                scrimBg.setTint(
                        ColorUtils.setAlphaComponent(mTintingColor, (int) (255 * tintAmount)));
            }
        }
    }

    private float getColorTint() {
        return mColorTint;
    }

    /** Returns {@code true} if the overview tasks are displayed as a grid. */
    public boolean showAsGrid() {
        return mOverviewGridEnabled || (mCurrentGestureEndTarget != null
                && mSizeStrategy.stateFromGestureEndTarget(
                mCurrentGestureEndTarget).displayOverviewTasksAsGrid(mActivity.getDeviceProfile()));
    }

    private boolean showAsFullscreen() {
        return mOverviewFullscreenEnabled
                && mCurrentGestureEndTarget != GestureState.GestureEndTarget.RECENTS;
    }

    public boolean shouldShowOverviewActionsForState(STATE_TYPE state) {
        return !state.displayOverviewTasksAsGrid(mActivity.getDeviceProfile())
                || getFocusedTaskView() != null;
    }

    /**
     * Used to register callbacks for when our empty message state changes.
     *
     * @see #setOnEmptyMessageUpdatedListener(OnEmptyMessageUpdatedListener)
     * @see #updateEmptyMessage()
     */
    public interface OnEmptyMessageUpdatedListener {
        /** @param isEmpty Whether RecentsView is empty (i.e. has no children) */
        void onEmptyMessageUpdated(boolean isEmpty);
    }

    /**
     * Adds a listener for scroll changes
     */
    public void addOnScrollChangedListener(OnScrollChangedListener listener) {
        mScrollListeners.add(listener);
    }

    /**
     * Removes a previously added scroll change listener
     */
    public void removeOnScrollChangedListener(OnScrollChangedListener listener) {
        mScrollListeners.remove(listener);
    }

    /**
     * @return Corner radius in pixel value for PiP window, which is updated via
     *         {@link #mIPipAnimationListener}
     */
    public int getPipCornerRadius() {
        return mPipCornerRadius;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        dispatchScrollChanged();
    }

    private void dispatchScrollChanged() {
        mLiveTileTaskViewSimulator.setScroll(getScrollOffset());
        for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
            mScrollListeners.get(i).onScrollChanged();
        }
    }

    private static class PinnedStackAnimationListener<T extends BaseActivity> extends
            IPipAnimationListener.Stub {
        private T mActivity;
        private RecentsView mRecentsView;

        public void setActivityAndRecentsView(T activity, RecentsView recentsView) {
            mActivity = activity;
            mRecentsView = recentsView;
        }

        @Override
        public void onPipAnimationStarted() {
            MAIN_EXECUTOR.execute(() -> {
                // Needed for activities that auto-enter PiP, which will not trigger a remote
                // animation to be created
                if (mActivity != null) {
                    mActivity.clearForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
                }
            });
        }

        @Override
        public void onPipCornerRadiusChanged(int cornerRadius) {
            if (mRecentsView != null) {
                mRecentsView.mPipCornerRadius = cornerRadius;
            }
        }
    }

    /** Get the color used for foreground scrimming the RecentsView for sharing. */
    public static int getForegroundScrimDimColor(Context context) {
        int baseColor = Themes.getAttrColor(context, R.attr.overviewScrimColor);
        // The Black blending is temporary until we have the proper color token.
        return ColorUtils.blendARGB(Color.BLACK, baseColor, 0.25f);
    }

    /** Get the RecentsAnimationController */
    @Nullable
    public RecentsAnimationController getRecentsAnimationController() {
        return mRecentsAnimationController;
    }

    /** Update the current activity locus id to show the enabled state of Overview */
    public void updateLocusId() {
        String locusId = "Overview";

        if (mOverviewStateEnabled && mActivity.isStarted()) {
            locusId += "|ENABLED";
        } else {
            locusId += "|DISABLED";
        }

        final LocusId id = new LocusId(locusId);
        // Set locus context is a binder call, don't want it to happen during a transition
        UI_HELPER_EXECUTOR.post(() -> mActivity.setLocusContext(id, Bundle.EMPTY));
    }
}
