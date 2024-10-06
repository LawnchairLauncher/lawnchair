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
import static android.view.Surface.ROTATION_0;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.app.animation.Interpolators.ACCELERATE_0_75;
import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.DECELERATE_2;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.OVERSHOOT_0_75;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASK_MENU;
import static com.android.launcher3.AbstractFloatingView.getTopOpenViewWithType;
import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.Flags.enableAdditionalHomeAnimations;
import static com.android.launcher3.Flags.enableGridOnlyOverview;
import static com.android.launcher3.Flags.enableRefactorTaskThumbnail;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_ACTIONS_SPLIT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_ORIENTATION_CHANGED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_CLEAR_ALL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_DISMISS_SWIPE_UP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.testing.shared.TestProtocol.DISMISS_ANIMATION_ENDS_MESSAGE;
import static com.android.launcher3.touch.PagedOrientationHandler.CANVAS_TRANSLATE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;
import static com.android.quickstep.util.LogUtils.splitFailureMessage;
import static com.android.quickstep.util.TaskGridNavHelper.DIRECTION_DOWN;
import static com.android.quickstep.util.TaskGridNavHelper.DIRECTION_LEFT;
import static com.android.quickstep.util.TaskGridNavHelper.DIRECTION_RIGHT;
import static com.android.quickstep.util.TaskGridNavHelper.DIRECTION_TAB;
import static com.android.quickstep.util.TaskGridNavHelper.DIRECTION_UP;
import static com.android.quickstep.views.ClearAllButton.DISMISS_ALPHA;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_ACTIONS_IN_MENU;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_DESKTOP;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NON_ZERO_ROTATION;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_RECENTS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_TASKS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_SPLIT_SCREEN;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_SPLIT_SELECT_ACTIVE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Pair;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.ListView;
import android.widget.OverScroller;
import android.widget.Toast;
import android.window.PictureInPictureSurfaceTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.graphics.ColorUtils;

import com.android.internal.jank.Cuj;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseActivity.MultiWindowModeChangedListener;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringProperty;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ResourceBasedOverride.Overrides;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.TranslateEdgeEffect;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.util.ViewPool;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RecentsFilterState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.RemoteTargetGluer;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.RotationTouchHelper;
import com.android.quickstep.SplitSelectionListener;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.ViewUtils;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.recents.data.TasksRepository;
import com.android.quickstep.recents.viewmodel.RecentsViewData;
import com.android.quickstep.util.ActiveGestureErrorDetector;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.AnimUtils;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RecentsAtomicAnimationFactory;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SplitAnimationController.Companion.SplitAnimInitProps;
import com.android.quickstep.util.SplitAnimationTimings;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskGridNavHelper;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TaskVisualsChangeListener;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.VibrationConstants;
import com.android.quickstep.views.TaskView.TaskContainer;
import com.android.systemui.plugins.ResourceProvider;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.common.pip.IPipAnimationListener;
import com.android.wm.shell.shared.DesktopModeStatus;

import kotlin.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A list of recent tasks.
 * @param <CONTAINER_TYPE> : the container that should host recents view
 * @param <STATE_TYPE> : the type of base state that will be used
 */

public abstract class RecentsView<CONTAINER_TYPE extends Context & RecentsViewContainer,
        STATE_TYPE extends BaseState<STATE_TYPE>> extends PagedView implements Insettable,
        TaskThumbnailCache.HighResLoadingState.HighResLoadingStateChangedCallback,
        TaskVisualsChangeListener {

    private static final String TAG = "RecentsView";
    private static final boolean DEBUG = false;

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

    public static final int SCROLL_VIBRATION_PRIMITIVE =
            Utilities.ATLEAST_S ? VibrationEffect.Composition.PRIMITIVE_LOW_TICK : -1;
    public static final float SCROLL_VIBRATION_PRIMITIVE_SCALE = 0.6f;
    public static final VibrationEffect SCROLL_VIBRATION_FALLBACK =
            VibrationConstants.EFFECT_TEXTURE_TICK;
    public static final int UNBOUND_TASK_VIEW_ID = -1;

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
                    if (enableRefactorTaskThumbnail()) {
                        view.mRecentsViewData.getScale().setValue(scale);
                    }
                    view.mLastComputedTaskStartPushOutDistance = null;
                    view.mLastComputedTaskEndPushOutDistance = null;
                    view.runActionOnRemoteHandles(new Consumer<RemoteTargetHandle>() {
                        @Override
                        public void accept(RemoteTargetHandle remoteTargetHandle) {
                            remoteTargetHandle.getTaskViewSimulator().recentsViewScale.value =
                                    scale;
                        }
                    });
                    view.setTaskViewsResistanceTranslation(view.mTaskViewsSecondaryTranslation);
                    view.updateTaskViewsSnapshotRadius();
                    view.updatePageOffsets();
                }

                @Override
                public Float get(RecentsView view) {
                    return view.getScaleX();
                }
            };

    /**
     * Progress of Recents view from carousel layout to grid layout. If Recents is not shown as a
     * grid, then the value remains 0.
     */
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

    /**
     * Alpha of the task thumbnail splash, where being in BackgroundAppState has a value of 1, and
     * being in any other state has a value of 0.
     */
    public static final FloatProperty<RecentsView> TASK_THUMBNAIL_SPLASH_ALPHA =
            new FloatProperty<RecentsView>("taskThumbnailSplashAlpha") {
                @Override
                public void setValue(RecentsView view, float taskThumbnailSplashAlpha) {
                    view.setTaskThumbnailSplashAlpha(taskThumbnailSplashAlpha);
                }

                @Override
                public Float get(RecentsView view) {
                    return view.mTaskThumbnailSplashAlpha;
                }
            };

    // OverScroll constants
    private static final int OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION = 270;

    private static final int DEFAULT_ACTIONS_VIEW_ALPHA_ANIMATION_DURATION = 300;

    private static final int DISMISS_TASK_DURATION = 300;
    private static final int ADDITION_TASK_DURATION = 200;
    private static final float INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET = 0.55f;
    private static final float ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET = 0.05f;
    private static final float ANIMATION_DISMISS_PROGRESS_MIDPOINT = 0.5f;
    private static final float END_DISMISS_TRANSLATION_INTERPOLATION_OFFSET = 0.75f;

    private static final float SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE = 0.15f;

    private static final float FOREGROUND_SCRIM_TINT = 0.32f;

    public final RecentsViewData mRecentsViewData = new RecentsViewData();
    public final TasksRepository mTasksRepository;

    protected final RecentsOrientedState mOrientationState;
    protected final BaseContainerInterface<STATE_TYPE, CONTAINER_TYPE> mSizeStrategy;
    @Nullable
    protected RecentsAnimationController mRecentsAnimationController;
    @Nullable
    protected SurfaceTransactionApplier mSyncTransactionApplier;
    protected int mTaskWidth;
    protected int mTaskHeight;
    // Used to position the top of a task in the top row of the grid
    private float mTaskGridVerticalDiff;
    // The vertical space one grid task takes + space between top and bottom row.
    private float mTopBottomRowHeightDiff;
    // mTaskGridVerticalDiff and mTopBottomRowHeightDiff summed together provides the top
    // position for bottom row of grid tasks.

    @Nullable
    protected RemoteTargetHandle[] mRemoteTargetHandles;
    protected final Rect mLastComputedCarouselTaskSize = new Rect();
    protected final Rect mLastComputedTaskSize = new Rect();
    protected final Rect mLastComputedGridSize = new Rect();
    protected final Rect mLastComputedGridTaskSize = new Rect();
    private TaskView mSelectedTask = null;
    // How much a task that is directly offscreen will be pushed out due to RecentsView scale/pivot.
    @Nullable
    protected Float mLastComputedTaskStartPushOutDistance = null;
    @Nullable
    protected Float mLastComputedTaskEndPushOutDistance = null;
    protected boolean mEnableDrawingLiveTile = false;
    protected final Rect mTempRect = new Rect();
    protected final RectF mTempRectF = new RectF();
    private final PointF mTempPointF = new PointF();
    private final Matrix mTempMatrix = new Matrix();
    private final float[] mTempFloat = new float[1];
    private final List<OnScrollChangedListener> mScrollListeners = new ArrayList<>();

    // The threshold at which we update the SystemUI flags when animating from the task into the app
    public static final float UPDATE_SYSUI_FLAGS_THRESHOLD = 0.85f;

    protected final CONTAINER_TYPE mContainer;
    private final float mFastFlingVelocity;
    private final int mScrollHapticMinGapMillis;
    private final RecentsModel mModel;
    private final int mSplitPlaceholderSize;
    private final int mSplitPlaceholderInset;
    private final ClearAllButton mClearAllButton;
    private final Rect mClearAllButtonDeadZoneRect = new Rect();
    private final Rect mTaskViewDeadZoneRect = new Rect();
    /**
     * Reflects if Recents is currently in the middle of a gesture, and if so, which tasks are
     * running. If a gesture is not in progress, this will be null.
     */
    private @Nullable Task[] mActiveGestureRunningTasks;

    // Keeps track of the previously known visible tasks for purposes of loading/unloading task data
    private final SparseBooleanArray mHasVisibleTaskData = new SparseBooleanArray();

    private final InvariantDeviceProfile mIdp;

    /**
     * Getting views should be done via {@link #getTaskViewFromPool(int)}
     */
    private final ViewPool<TaskView> mTaskViewPool;
    private final ViewPool<GroupedTaskView> mGroupedTaskViewPool;
    private final ViewPool<DesktopTaskView> mDesktopTaskViewPool;

    private final TaskOverlayFactory mTaskOverlayFactory;

    protected boolean mDisallowScrollToClearAll;
    private boolean mOverlayEnabled;
    protected boolean mFreezeViewVisibility;
    private boolean mOverviewGridEnabled;
    private boolean mOverviewFullscreenEnabled;
    private boolean mOverviewSelectEnabled;

    private boolean mShouldClampScrollOffset;
    private int mClampedScrollOffsetBound;

    private float mAdjacentPageHorizontalOffset = 0;
    protected float mTaskViewsSecondaryTranslation = 0;
    protected float mTaskViewsPrimarySplitTranslation = 0;
    protected float mTaskViewsSecondarySplitTranslation = 0;
    // Progress from 0 to 1 where 0 is a carousel and 1 is a 2 row grid.
    private float mGridProgress = 0;
    private float mTaskThumbnailSplashAlpha = 0;
    private boolean mShowAsGridLastOnLayout = false;
    private final IntSet mTopRowIdSet = new IntSet();
    private int mClearAllShortTotalWidthTranslation = 0;

    // The GestureEndTarget that is still in progress.
    @Nullable
    protected GestureState.GestureEndTarget mCurrentGestureEndTarget;

    // TODO(b/187528071): Remove these and replace with a real scrim.
    private float mColorTint;
    private final int mTintingColor;
    @Nullable
    private ObjectAnimator mTintingAnimator;

    private int mOverScrollShift = 0;
    private long mScrollLastHapticTimestamp;

    private int mKeyboardTaskFocusSnapAnimationDuration;
    private int mKeyboardTaskFocusIndex = INVALID_PAGE;

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
            TaskView taskView = getTaskViewByTaskId(taskId);
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

            TaskView taskView = getTaskViewByTaskId(taskId);
            if (taskView == null) {
                return;
            }
            Task.TaskKey taskKey = taskView.getFirstTask().key;
            UI_HELPER_EXECUTOR.execute(new CancellableTask<>(
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
                            }, RecentsFilterState.getFilter(mFilterState.getPackageNameToFilter()));
                        }
                    }));
        }
    };

    private final PinnedStackAnimationListener mIPipAnimationListener =
            new PinnedStackAnimationListener();
    private int mPipCornerRadius;
    private int mPipShadowRadius;

    // Used to keep track of the last requested task list id, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private int mTaskListChangeId = -1;

    // Only valid until the launcher state changes to NORMAL
    /**
     * ID for the current running TaskView view, unique amongst TaskView instances. ID's are set
     * through {@link #getTaskViewFromPool(boolean)} and incremented by {@link #mTaskViewIdCount}
     */
    protected int mRunningTaskViewId = -1;
    private int mTaskViewIdCount;
    protected boolean mRunningTaskTileHidden;
    @Nullable
    private Task[] mTmpRunningTasks;
    protected int mFocusedTaskViewId = -1;

    private boolean mTaskIconScaledDown = false;
    private boolean mRunningTaskShowScreenshot = false;

    private boolean mOverviewStateEnabled;
    private boolean mHandleTaskStackChanges;
    private boolean mSwipeDownShouldLaunchApp;
    private boolean mTouchDownToStartHome;
    private final float mSquaredTouchSlop;
    private int mDownX;
    private int mDownY;

    @Nullable
    private PendingAnimation mPendingAnimation;
    @Nullable
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
    protected boolean mLoadPlanEverApplied;

    // Variables for empty state
    private final Drawable mEmptyIcon;
    private final CharSequence mEmptyMessage;
    private final TextPaint mEmptyMessagePaint;
    private final Point mLastMeasureSize = new Point();
    private final int mEmptyMessagePadding;
    private boolean mShowEmptyMessage;
    @Nullable
    private OnEmptyMessageUpdatedListener mOnEmptyMessageUpdatedListener;
    @Nullable
    private Layout mEmptyTextLayout;

    /**
     * Placeholder view indicating where the first split screen selected app will be placed
     */
    protected SplitSelectStateController mSplitSelectStateController;

    /**
     * The first task that split screen selection was initiated with. When split select state is
     * initialized, we create a
     * {@link #createTaskDismissAnimation(TaskView, boolean, boolean, long, boolean)} for this
     * TaskView but don't actually remove the task since the user might back out. As such, we also
     * ensure this View doesn't go back into the {@link #mTaskViewPool},
     * see {@link #onViewRemoved(View)}
     */
    @Nullable
    private TaskView mSplitHiddenTaskView;
    @Nullable
    private TaskView mSecondSplitHiddenView;
    @Nullable
    private SplitBounds mSplitBoundsConfig;
    private final Toast mSplitUnsupportedToast = Toast.makeText(getContext(),
            R.string.toast_split_app_unsupported, Toast.LENGTH_SHORT);

    @Nullable
    private SplitSelectSource mSplitSelectSource;

    private final SplitSelectionListener mSplitSelectionListener = new SplitSelectionListener() {
        @Override
        public void onSplitSelectionConfirmed() { }

        @Override
        public void onSplitSelectionActive() { }

        @Override
        public void onSplitSelectionExit(boolean launchedSplit) {
            resetFromSplitSelectionState();
        }
    };

    /**
     * Keeps track of the index of the TaskView that split screen was initialized with so we know
     * where to insert it back into list of taskViews in case user backs out of entering split
     * screen.
     * NOTE: This index is the index while {@link #mSplitHiddenTaskView} was a child of recentsView,
     * this doesn't get adjusted to reflect the new child count after the taskView is dismissed/
     * removed from recentsView
     */
    private int mSplitHiddenTaskViewIndex = -1;
    @Nullable
    private FloatingTaskView mSecondFloatingTaskView;
    /**
     * A fullscreen scrim that goes behind the splitscreen animation to hide color conflicts and
     * possible flickers. Removed after tasks + divider finish animating in.
     */
    private View mSplitScrim;

    /**
     * The task to be removed and immediately re-added. Should not be added to task pool.
     */
    @Nullable
    private TaskView mMovingTaskView;

    private OverviewActionsView mActionsView;
    private ObjectAnimator mActionsViewAlphaAnimator;
    private float mActionsViewAlphaAnimatorFinalValue;

    @Nullable
    private DesktopRecentsTransitionController mDesktopRecentsTransitionController;

    /**
     * Keeps track of the desktop task. Optional and only present when the feature flag is enabled.
     */
    @Nullable
    private DesktopTaskView mDesktopTaskView;

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

    @Nullable
    private RunnableList mSideTaskLaunchCallback;
    @Nullable
    private TaskLaunchListener mTaskLaunchListener;
    @Nullable
    private Runnable mOnTaskLaunchCancelledRunnable;


    // keeps track of the state of the filter for tasks in recents view
    private final RecentsFilterState mFilterState = new RecentsFilterState();

    private int mOffsetMidpointIndexOverride = INVALID_PAGE;

    public RecentsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            BaseContainerInterface sizeStrategy) {
        super(context, attrs, defStyleAttr);
        setEnableFreeScroll(true);
        mSizeStrategy = sizeStrategy;
        mContainer = RecentsViewContainer.containerFromContext(context);
        mOrientationState = new RecentsOrientedState(
                context, mSizeStrategy, this::animateRecentsRotationInPlace);
        final int rotation = mContainer.getDisplay().getRotation();
        mOrientationState.setRecentsRotation(rotation);

        mScrollHapticMinGapMillis = getResources()
                .getInteger(R.integer.recentsScrollHapticMinGapMillis);
        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);
        mModel = RecentsModel.INSTANCE.get(context);
        mIdp = InvariantDeviceProfile.INSTANCE.get(context);
        if (enableRefactorTaskThumbnail()) {
            mTasksRepository = new TasksRepository(
                    mModel, mModel.getThumbnailCache(), mModel.getIconCache());
        } else {
            mTasksRepository = null;
        }

        mClearAllButton = (ClearAllButton) LayoutInflater.from(context)
                .inflate(R.layout.overview_clear_all_button, this, false);
        mClearAllButton.setOnClickListener(this::dismissAllTasks);
        mTaskViewPool = new ViewPool<>(context, this, R.layout.task, 20 /* max size */,
                10 /* initial size */);
        mGroupedTaskViewPool = new ViewPool<>(context, this,
                R.layout.task_grouped, 20 /* max size */, 10 /* initial size */);
        mDesktopTaskViewPool = new ViewPool<>(context, this, R.layout.task_desktop,
                5 /* max size */, 1 /* initial size */);

        setOrientationHandler(mOrientationState.getOrientationHandler());
        mIsRtl = getPagedOrientationHandler().getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mSplitPlaceholderSize = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_size);
        mSplitPlaceholderInset = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_inset);
        mSquaredTouchSlop = squaredTouchSlop(context);
        mClampedScrollOffsetBound = getResources().getDimensionPixelSize(
                R.dimen.transient_taskbar_clamped_offset_bound);

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

        mTaskOverlayFactory = Overrides.getObject(
                TaskOverlayFactory.class,
                context.getApplicationContext(),
                R.string.task_overlay_factory_class);

        // Initialize quickstep specific cache params here, as this is constructed only once
        mContainer.getViewCache().setCacheSize(R.layout.digital_wellbeing_toast, 5);

        mTintingColor = getForegroundScrimDimColor(context);

        // if multi-instance feature is enabled
        if (FeatureFlags.ENABLE_MULTI_INSTANCE.get()) {
            // invalidate the current list of tasks if filter changes with a fading in/out animation
            mFilterState.setOnFilterUpdatedListener(() -> {
                Animator animatorFade = getStateManager().createStateElementAnimation(
                        RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM, 1f, 0f);
                Animator animatorAppear = getStateManager().createStateElementAnimation(
                        RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM, 0f, 1f);
                animatorFade.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(@NonNull Animator animation) {
                        RecentsView.this.invalidateTaskList();
                        updateClearAllFunction();
                        reloadIfNeeded();
                        if (mPendingAnimation != null) {
                            mPendingAnimation.addEndListener(success -> {
                                animatorAppear.start();
                            });
                        } else {
                            animatorAppear.start();
                        }
                    }
                });
                animatorFade.start();
            });
        }
        // make sure filter is turned off by default
        mFilterState.setFilterBy(null);
    }

    /** Get the state of the filter */
    public RecentsFilterState getFilterState() {
        return mFilterState;
    }

    /**
     * Toggles the filter and reloads the recents view if needed.
     *
     * @param packageName package name to filter by if the filter is being turned on;
     *                    should be null if filter is being turned off
     */
    public void setAndApplyFilter(@Nullable String packageName) {
        mFilterState.setFilterBy(packageName);
    }

    /**
     * Updates the "Clear All" button and its function depending on the recents view state.
     *
     * TODO: add a different button for going back to overview. Present solution is for demo only.
     */
    public void updateClearAllFunction() {
        if (mFilterState.isFiltered()) {
            mClearAllButton.setText(R.string.recents_back);
            mClearAllButton.setOnClickListener((view) -> {
                this.setAndApplyFilter(null);
            });
        } else {
            mClearAllButton.setText(R.string.recents_clear_all);
            mClearAllButton.setOnClickListener(this::dismissAllTasks);
        }
    }

    /**
     * Invalidates the list of tasks so that an update occurs to the list of tasks if requested.
     */
    private void invalidateTaskList() {
        mTaskListChangeId = -1;
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

            int primarySize = getPagedOrientationHandler().getPrimaryValue(getWidth(), getHeight());
            int scroll = OverScroll.dampedScroll(getUndampedOverScrollShift(), primarySize);
            getPagedOrientationHandler().setPrimary(canvas, CANVAS_TRANSLATE, scroll);

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
        if (mEnableDrawingLiveTile && mRemoteTargetHandles != null) {
            redrawLiveTile();
        }
    }

    private float getUndampedOverScrollShift() {
        final int width = getWidth();
        final int height = getHeight();
        int primarySize = getPagedOrientationHandler().getPrimaryValue(width, height);
        int secondarySize = getPagedOrientationHandler().getSecondaryValue(width, height);

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
    @Nullable
    public Task onTaskThumbnailChanged(int taskId, ThumbnailData thumbnailData) {
        if (mHandleTaskStackChanges) {
            TaskView taskView = getTaskViewByTaskId(taskId);
            if (taskView != null) {
                for (TaskContainer container : taskView.getTaskContainers()) {
                    if (container == null || taskId != container.getTask().key.id) {
                        continue;
                    }
                    container.getThumbnailViewDeprecated().setThumbnail(container.getTask(),
                            thumbnailData);
                }
            }
        }
        return null;
    }

    @Override
    public void onTaskIconChanged(String pkg, UserHandle user) {
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView tv = requireTaskViewAt(i);
            Task task = tv.getFirstTask();
            if (pkg.equals(task.key.getPackageName()) && task.key.userId == user.getIdentifier()) {
                task.icon = null;
                if (tv.getTaskContainers().stream().anyMatch(
                        container -> container.getIconView().getDrawable() != null)) {
                    tv.onTaskListVisibilityChanged(true /* visible */);
                }
            }
        }
    }

    @Override
    public void onTaskIconChanged(int taskId) {
        TaskView taskView = getTaskViewByTaskId(taskId);
        if (taskView != null) {
            taskView.refreshTaskThumbnailSplash();
        }
    }

    /**
     * Update the thumbnail(s) of the relevant TaskView.
     * @param refreshNow Refresh immediately if it's true.
     */
    @Nullable
    public TaskView updateThumbnail(
            HashMap<Integer, ThumbnailData> thumbnailData, boolean refreshNow) {
        TaskView updatedTaskView = null;
        for (Map.Entry<Integer, ThumbnailData> entry : thumbnailData.entrySet()) {
            Integer id = entry.getKey();
            ThumbnailData thumbnail = entry.getValue();
            TaskView taskView = getTaskViewByTaskId(id);
            if (taskView == null) {
                continue;
            }
            // taskView could be a GroupedTaskView, so select the relevant task by ID
            TaskContainer taskAttributes = taskView.getTaskContainerById(id);
            if (taskAttributes == null) {
                continue;
            }
            Task task = taskAttributes.getTask();
            TaskThumbnailViewDeprecated taskThumbnailViewDeprecated =
                    taskAttributes.getThumbnailViewDeprecated();
            taskThumbnailViewDeprecated.setThumbnail(task, thumbnail, refreshNow);
            // thumbnailData can contain 1-2 ids, but they should correspond to the same
            // TaskView, so overwriting is ok
            updatedTaskView = taskView;
        }

        return updatedTaskView;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTaskStackListenerState();
    }

    public void init(OverviewActionsView actionsView, SplitSelectStateController splitController,
            @Nullable DesktopRecentsTransitionController desktopRecentsTransitionController) {
        mActionsView = actionsView;
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, getTaskViewCount() == 0);
        // Update flags for 1p/3p launchers
        mActionsView.updateFor3pLauncher(!supportsAppPairs());
        mSplitSelectStateController = splitController;
        mDesktopRecentsTransitionController = desktopRecentsTransitionController;
    }

    public SplitSelectStateController getSplitSelectController() {
        return mSplitSelectStateController;
    }

    public boolean isSplitSelectionActive() {
        return mSplitSelectStateController.isSplitSelectActive();
    }

    /**
     * See overridden implementations
     * @return {@code true} if child TaskViews can be launched when user taps on them
     */
    protected boolean canLaunchFullscreenTask() {
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
        mModel.getThumbnailCache().getHighResLoadingState().addCallback(this);
        mContainer.addMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = new SurfaceTransactionApplier(this);
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTransformParams()
                .setSyncTransactionApplier(mSyncTransactionApplier));
        RecentsModel.INSTANCE.get(getContext()).addThumbnailChangeListener(this);
        mIPipAnimationListener.setActivityAndRecentsView(mContainer, this);
        SystemUiProxy.INSTANCE.get(getContext()).setPipAnimationListener(
                mIPipAnimationListener);
        mOrientationState.initListeners();
        mTaskOverlayFactory.initListeners();
        if (FeatureFlags.enableSplitContextually()) {
            mSplitSelectStateController.registerSplitListener(mSplitSelectionListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateTaskStackListenerState();
        mModel.getThumbnailCache().getHighResLoadingState().removeCallback(this);
        mContainer.removeMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = null;
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTransformParams()
                .setSyncTransactionApplier(null));
        executeSideTaskLaunchCallback();
        RecentsModel.INSTANCE.get(getContext()).removeThumbnailChangeListener(this);
        SystemUiProxy.INSTANCE.get(getContext()).setPipAnimationListener(null);
        mIPipAnimationListener.setActivityAndRecentsView(null, null);
        mOrientationState.destroyListeners();
        mTaskOverlayFactory.removeListeners();
        if (FeatureFlags.enableSplitContextually()) {
            mSplitSelectStateController.unregisterSplitListener(mSplitSelectionListener);
        }
        reset();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);

        // Clear the task data for the removed child if it was visible unless:
        // - It's the initial taskview for entering split screen, we only pretend to dismiss the
        // task
        // - It's the focused task to be moved to the front, we immediately re-add the task
        if (child instanceof TaskView && child != mSplitHiddenTaskView
                && child != mMovingTaskView) {
            TaskView taskView = (TaskView) child;
            for (int i : taskView.getTaskIds()) {
                mHasVisibleTaskData.delete(i);
            }
            if (child instanceof GroupedTaskView) {
                mGroupedTaskViewPool.recycle((GroupedTaskView) taskView);
            } else if (child instanceof DesktopTaskView) {
                mDesktopTaskViewPool.recycle((DesktopTaskView) taskView);
            } else {
                mTaskViewPool.recycle(taskView);
            }
            mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, getTaskViewCount() == 0);
        }
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        child.setAlpha(mContentAlpha);
        // RecentsView is set to RTL in the constructor when system is using LTR. Here we set the
        // child direction back to match system settings.
        child.setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, false);
        updateEmptyMessage();
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (isModal()) {
            // Do not scroll when clicking on a modal grid task, as it will already be centered
            // on screen.
            return false;
        }
        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    public void addSideTaskLaunchCallback(RunnableList callback) {
        if (mSideTaskLaunchCallback == null) {
            mSideTaskLaunchCallback = new RunnableList();
        }
        mSideTaskLaunchCallback.add(callback::executeAllAndDestroy);
    }

    /**
     * This is a one-time callback when touching in live tile mode. It's reset to null right
     * after it's called.
     */
    public void setTaskLaunchListener(TaskLaunchListener taskLaunchListener) {
        mTaskLaunchListener = taskLaunchListener;
    }

    public void onTaskLaunchedInLiveTileMode() {
        if (mTaskLaunchListener != null) {
            mTaskLaunchListener.onTaskLaunched();
            mTaskLaunchListener = null;
        }
    }

    /**
     * This is a one-time callback when touching in live tile mode. It's reset to null right
     * after it's called.
     */
    public void setTaskLaunchCancelledRunnable(Runnable onTaskLaunchCancelledRunnable) {
        mOnTaskLaunchCancelledRunnable = onTaskLaunchCancelledRunnable;
    }

    public void onTaskLaunchedInLiveTileModeCancelled() {
        if (mOnTaskLaunchCancelledRunnable != null) {
            mOnTaskLaunchCancelledRunnable.run();
            mOnTaskLaunchCancelledRunnable = null;
        }
    }

    private void executeSideTaskLaunchCallback() {
        if (mSideTaskLaunchCallback != null) {
            mSideTaskLaunchCallback.executeAllAndDestroy();
            mSideTaskLaunchCallback = null;
        }
    }

    /**
     * TODO(b/195675206) Check both taskIDs from runningTaskViewId
     *  and launch if either of them is {@param taskId}
     */
    public void launchSideTaskInLiveTileModeForRestartedApp(int taskId) {
        int runningTaskViewId = getTaskViewIdFromTaskId(taskId);
        if (mRunningTaskViewId == -1 ||
                mRunningTaskViewId != runningTaskViewId ||
                mRemoteTargetHandles == null) {
            return;
        }

        TransformParams params = mRemoteTargetHandles[0].getTransformParams();
        RemoteAnimationTargets targets = params.getTargetSet();
        if (targets != null && targets.findTask(taskId) != null) {
            launchSideTaskInLiveTileMode(taskId, targets.apps, targets.wallpapers,
                    targets.nonApps);
        }
    }

    public void launchSideTaskInLiveTileMode(int taskId, RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpaper, RemoteAnimationTarget[] nonApps) {
        AnimatorSet anim = new AnimatorSet();
        TaskView taskView = getTaskViewByTaskId(taskId);
        if (taskView == null || !isTaskViewVisible(taskView)) {
            // TODO: Refine this animation.
            SurfaceTransactionApplier surfaceApplier =
                    new SurfaceTransactionApplier(mContainer.getDragLayer());
            ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
            appAnimator.setDuration(RECENTS_LAUNCH_DURATION);
            appAnimator.setInterpolator(ACCELERATE_DECELERATE);
            final Matrix matrix = new Matrix();
            appAnimator.addUpdateListener(valueAnimator -> {
                float percent = valueAnimator.getAnimatedFraction();
                SurfaceTransaction transaction = new SurfaceTransaction();
                for (int i = apps.length - 1; i >= 0; --i) {
                    RemoteAnimationTarget app = apps[i];

                    float dx = mContainer.getDeviceProfile().widthPx * (1 - percent) / 2
                            + app.screenSpaceBounds.left * percent;
                    float dy = mContainer.getDeviceProfile().heightPx * (1 - percent) / 2
                            + app.screenSpaceBounds.top * percent;
                    matrix.setScale(percent, percent);
                    matrix.postTranslate(dx, dy);
                    transaction.forSurface(app.leash)
                            .setAlpha(percent)
                            .setMatrix(matrix);
                }
                surfaceApplier.scheduleApply(transaction);
            });
            appAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    final SurfaceTransaction showTransaction = new SurfaceTransaction();
                    for (int i = apps.length - 1; i >= 0; --i) {
                        showTransaction.getTransaction().show(apps[i].leash);
                        showTransaction.forSurface(apps[i].leash).setLayer(
                                Integer.MAX_VALUE - 1000 + apps[i].prefixOrderIndex);
                    }
                    surfaceApplier.scheduleApply(showTransaction);
                }
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
                    true /* launcherClosing */, getStateManager(), this,
                    getDepthController());
        }
        anim.start();
    }

    public boolean isTaskViewVisible(TaskView tv) {
        if (showAsGrid()) {
            int screenStart = getPagedOrientationHandler().getPrimaryScroll(this);
            int screenEnd = screenStart + getPagedOrientationHandler().getMeasuredSize(this);
            return isTaskViewWithinBounds(tv, screenStart, screenEnd);
        } else {
            // For now, just check if it's the active task or an adjacent task
            return Math.abs(indexOfChild(tv) - getNextPage()) <= 1;
        }
    }

    public boolean isTaskViewFullyVisible(TaskView tv) {
        if (showAsGrid()) {
            int screenStart = getPagedOrientationHandler().getPrimaryScroll(this);
            int screenEnd = screenStart + getPagedOrientationHandler().getMeasuredSize(this);
            return isTaskViewFullyWithinBounds(tv, screenStart, screenEnd);
        } else {
            // For now, just check if it's the active task
            return indexOfChild(tv) == getNextPage();
        }
    }

    @Nullable
    private TaskView getLastGridTaskView() {
        return getLastGridTaskView(getTopRowIdArray(), getBottomRowIdArray());
    }

    @Nullable
    private TaskView getLastGridTaskView(IntArray topRowIdArray, IntArray bottomRowIdArray) {
        if (topRowIdArray.isEmpty() && bottomRowIdArray.isEmpty()) {
            return null;
        }
        int lastTaskViewId = topRowIdArray.size() >= bottomRowIdArray.size() ? topRowIdArray.get(
                topRowIdArray.size() - 1) : bottomRowIdArray.get(bottomRowIdArray.size() - 1);
        return getTaskViewFromTaskViewId(lastTaskViewId);
    }

    private int getSnapToLastTaskScrollDiff() {
        // Snap to a position where ClearAll is just invisible.
        int screenStart = getPagedOrientationHandler().getPrimaryScroll(this);
        int clearAllScroll = getScrollForPage(indexOfChild(mClearAllButton));
        int clearAllWidth = getPagedOrientationHandler().getPrimarySize(mClearAllButton);
        int lastTaskScroll = getLastTaskScroll(clearAllScroll, clearAllWidth);
        return screenStart - lastTaskScroll;
    }

    private int getLastTaskScroll(int clearAllScroll, int clearAllWidth) {
        int distance = clearAllWidth + getClearAllExtraPageSpacing();
        return clearAllScroll + (mIsRtl ? distance : -distance);
    }

    private boolean isTaskViewWithinBounds(TaskView tv, int start, int end) {
        int taskStart = getPagedOrientationHandler().getChildStart(tv)
                + (int) tv.getOffsetAdjustment(showAsGrid());
        int taskSize = (int) (getPagedOrientationHandler().getMeasuredSize(tv)
                * tv.getSizeAdjustment(showAsFullscreen()));
        int taskEnd = taskStart + taskSize;
        return (taskStart >= start && taskStart <= end) || (taskEnd >= start
                && taskEnd <= end);
    }

    private boolean isTaskViewFullyWithinBounds(TaskView tv, int start, int end) {
        int taskStart = getPagedOrientationHandler().getChildStart(tv)
                + (int) tv.getOffsetAdjustment(showAsGrid());
        int taskSize = (int) (getPagedOrientationHandler().getMeasuredSize(tv)
                * tv.getSizeAdjustment(showAsFullscreen()));
        int taskEnd = taskStart + taskSize;
        return taskStart >= start && taskEnd <= end;
    }

    /**
     * Returns true if the task is in expected scroll position.
     *
     * @param taskIndex the index of the task
     */
    public boolean isTaskInExpectedScrollPosition(int taskIndex) {
        return getScrollForPage(taskIndex) == getPagedOrientationHandler().getPrimaryScroll(this);
    }

    private boolean isFocusedTaskInExpectedScrollPosition() {
        TaskView focusedTask = getFocusedTaskView();
        return focusedTask != null && isTaskInExpectedScrollPosition(indexOfChild(focusedTask));
    }

    /**
     * Returns a {@link TaskView} that has taskId matching {@code taskId} or null if no match.
     */
    @Nullable
    public TaskView getTaskViewByTaskId(int taskId) {
        if (taskId == INVALID_TASK_ID) {
            return null;
        }

        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = requireTaskViewAt(i);
            if (taskView.containsTaskId(taskId)) {
                return taskView;
            }
        }
        return null;
    }

    /**
     * Returns a {@link TaskView} that has taskIds matching {@code taskIds} or null if no match.
     */
    @Nullable
    public TaskView getTaskViewByTaskIds(int[] taskIds) {
        if (!hasAllValidTaskIds(taskIds)) {
            return null;
        }

        // We're looking for a taskView that matches these ids, regardless of order
        int[] taskIdsCopy = Arrays.copyOf(taskIds, taskIds.length);
        Arrays.sort(taskIdsCopy);

        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = requireTaskViewAt(i);
            int[] taskViewIdsCopy = taskView.getTaskIds();
            Arrays.sort(taskViewIdsCopy);
            if (Arrays.equals(taskIdsCopy, taskViewIdsCopy)) {
                return taskView;
            }
        }
        return null;
    }

    /** Returns false if {@code taskIds} is null or contains any invalid values, true otherwise */
    private boolean hasAllValidTaskIds(int[] taskIds) {
        return taskIds != null
                && taskIds.length > 0
                && Arrays.stream(taskIds).noneMatch(taskId -> taskId == INVALID_TASK_ID);
    }

    public void setOverviewStateEnabled(boolean enabled) {
        mOverviewStateEnabled = enabled;
        updateTaskStackListenerState();
        mOrientationState.setRotationWatcherEnabled(enabled);
        if (!enabled) {
            // Reset the running task when leaving overview since it can still have a reference to
            // its thumbnail
            mTmpRunningTasks = null;
            mSplitBoundsConfig = null;
            mTaskOverlayFactory.clearAllActiveState();
        }
        updateLocusId();
    }

    /**
     * Enable or disable showing border on hover and focus change on task views
     */
    public void setTaskBorderEnabled(boolean enabled) {
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            taskView.setBorderEnabled(enabled);
        }
        mClearAllButton.setBorderEnabled(enabled);
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
        if (!mContainer.getDeviceProfile().isTablet) {
            mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, true);
        }
        if (mOverviewStateEnabled) { // only when in overview
            InteractionJankMonitorWrapper.begin(/* view= */ this, Cuj.CUJ_RECENTS_SCROLLING);
        }
    }

    @Override
    protected void onPageEndTransition() {
        super.onPageEndTransition();
        ActiveGestureLog.INSTANCE.addLog(
                "onPageEndTransition: current page index updated", getNextPage());
        if (isClearAllHidden() && !mContainer.getDeviceProfile().isTablet) {
            mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING, false);
        }
        if (getNextPage() > 0) {
            setSwipeDownShouldLaunchApp(true);
        }
        InteractionJankMonitorWrapper.end(Cuj.CUJ_RECENTS_SCROLLING);
    }

    @Override
    protected boolean isSignificantMove(float absoluteDelta, int pageOrientedSize) {
        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        if (!deviceProfile.isTablet) {
            return super.isSignificantMove(absoluteDelta, pageOrientedSize);
        }

        return absoluteDelta
                > deviceProfile.availableWidthPx * SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = super.onInterceptTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            Log.d("b/318590728", "onInterceptTouchEvent: " + ev);
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);

        if (showAsGrid()) {
            int taskCount = getTaskViewCount();
            for (int i = 0; i < taskCount; i++) {
                TaskView taskView = requireTaskViewAt(i);
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
        if (finalPos > mMinScroll && finalPos < mMaxScroll) {
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

            if (showAsGrid()) {
                if (isSplitSelectionActive()) {
                    return;
                }
                TaskView taskView = getTaskViewAt(mNextPage);
                // Snap to fully visible focused task and clear all button.
                boolean shouldSnapToFocusedTask = taskView != null && taskView.isFocusedTask()
                        && isTaskViewFullyVisible(taskView);
                boolean shouldSnapToClearAll = mNextPage == indexOfChild(mClearAllButton);
                if (!shouldSnapToFocusedTask && !shouldSnapToClearAll) {
                    return;
                }
            }

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
    protected void onEdgeAbsorbingScroll() {
        vibrateForScroll();
    }

    @Override
    protected void onScrollOverPageChanged() {
        vibrateForScroll();
    }

    private void vibrateForScroll() {
        long now = SystemClock.uptimeMillis();
        if (now - mScrollLastHapticTimestamp > mScrollHapticMinGapMillis) {
            mScrollLastHapticTimestamp = now;
            VibratorWrapper.INSTANCE.get(mContext).vibrate(SCROLL_VIBRATION_PRIMITIVE,
                    SCROLL_VIBRATION_PRIMITIVE_SCALE, SCROLL_VIBRATION_FALLBACK);
        }
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        // Enables swiping to the left or right only if the task overlay is not modal.
        if (!isModal()) {
            super.determineScrollingStart(ev, touchSlopScale);
        }
    }

    /**
     * Moves the running task to the front of the carousel in tablets, to minimize animation
     * required to move the running task in grid.
     */
    public void moveRunningTaskToFront() {
        if (!mContainer.getDeviceProfile().isTablet) {
            return;
        }

        TaskView runningTaskView = getRunningTaskView();
        if (runningTaskView == null) {
            return;
        }

        if (indexOfChild(runningTaskView) != mCurrentPage) {
            return;
        }

        if (mCurrentPage == 0) {
            return;
        }

        int primaryScroll = getPagedOrientationHandler().getPrimaryScroll(this);
        int currentPageScroll = getScrollForPage(mCurrentPage);
        mCurrentPageScrollDiff = primaryScroll - currentPageScroll;

        mMovingTaskView = runningTaskView;
        removeView(runningTaskView);
        mMovingTaskView = null;
        runningTaskView.resetPersistentViewTransforms();
        addView(runningTaskView, 0);
        setCurrentPage(0);

        updateTaskSize();
    }

    @Override
    protected void onScrollerAnimationAborted() {
        ActiveGestureLog.INSTANCE.addLog("scroller animation aborted",
                ActiveGestureErrorDetector.GestureEvent.SCROLLER_ANIMATION_ABORTED);
    }

    @Override
    protected boolean isPageScrollsInitialized() {
        return super.isPageScrollsInitialized() && mLoadPlanEverApplied;
    }

    protected void applyLoadPlan(List<GroupTask> taskGroups) {
        if (mPendingAnimation != null) {
            mPendingAnimation.addEndListener(success -> applyLoadPlan(taskGroups));
            return;
        }

        if (taskGroups == null) {
            Log.d(TAG, "applyLoadPlan - taskGroups is null");
        } else {
            Log.d(TAG, "applyLoadPlan - taskGroups: " + taskGroups.stream().map(
                    GroupTask::toString).toList());
        }
        mLoadPlanEverApplied = true;
        if (taskGroups == null || taskGroups.isEmpty()) {
            removeTasksViewsAndClearAllButton();
            onTaskStackUpdated();
            // With all tasks removed, touch handling in PagedView is disabled and we need to reset
            // touch state or otherwise values will be obsolete.
            resetTouchState();
            if (isPageScrollsInitialized()) {
                onPageScrollsInitialized();
            }
            return;
        }

        int[] currentTaskIds;
        TaskView currentTaskView = getTaskViewAt(mCurrentPage);
        if (currentTaskView != null) {
            currentTaskIds = currentTaskView.getTaskIds();
        } else {
            currentTaskIds = new int[0];
        }

        // Unload existing visible task data
        unloadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);

        TaskView ignoreResetTaskView =
                mIgnoreResetTaskId == INVALID_TASK_ID
                        ? null : getTaskViewByTaskId(mIgnoreResetTaskId);

        // Save running task ID if it exists before rebinding all taskViews, otherwise the task from
        // the runningTaskView currently bound could get assigned to another TaskView
        int[] runningTaskIds = getTaskIdsForTaskViewId(mRunningTaskViewId);
        int[] focusedTaskIds = getTaskIdsForTaskViewId(mFocusedTaskViewId);

        // Reset the focused task to avoiding initializing TaskViews layout as focused task during
        // binding. The focused task view will be updated after all the TaskViews are bound.
        mFocusedTaskViewId = INVALID_TASK_ID;

        // Removing views sets the currentPage to 0, so we save this and restore it after
        // the new set of views are added
        int previousCurrentPage = mCurrentPage;
        int previousFocusedPage = indexOfChild(getFocusedChild());
        removeAllViews();

        // If we are entering Overview as a result of initiating a split from somewhere else
        // (e.g. split from Home), we need to make sure the staged app is not drawn as a thumbnail.
        int stagedTaskIdToBeRemoved;
        if (isSplitSelectionActive()) {
            stagedTaskIdToBeRemoved = mSplitSelectStateController.getInitialTaskId();
            updateCurrentTaskActionsVisibility();
        } else {
            stagedTaskIdToBeRemoved = INVALID_TASK_ID;
        }
        // update the map of instance counts
        mFilterState.updateInstanceCountMap(taskGroups);

        // Clear out desktop view if it is set
        mDesktopTaskView = null;

        // Add views as children based on whether it's grouped or single task. Looping through
        // taskGroups backwards populates the thumbnail grid from least recent to most recent.
        for (int i = taskGroups.size() - 1; i >= 0; i--) {
            GroupTask groupTask = taskGroups.get(i);
            boolean isRemovalNeeded = stagedTaskIdToBeRemoved != INVALID_TASK_ID
                    && groupTask.containsTask(stagedTaskIdToBeRemoved);

            if (isRemovalNeeded && !groupTask.hasMultipleTasks()) {
                // If the task we need to remove is not part of a pair, avoiding creating the
                // TaskView.
                continue;
            }

            // If we need to remove half of a pair of tasks, force a TaskView with Type.SINGLE
            // to be a temporary container for the remaining task.
            TaskView taskView = getTaskViewFromPool(
                    isRemovalNeeded ? TaskView.Type.SINGLE : groupTask.taskViewType);
            if (taskView instanceof GroupedTaskView) {
                boolean firstTaskIsLeftTopTask =
                        groupTask.mSplitBounds.leftTopTaskId == groupTask.task1.key.id;
                Task leftTopTask = firstTaskIsLeftTopTask ? groupTask.task1 : groupTask.task2;
                Task rightBottomTask = firstTaskIsLeftTopTask ? groupTask.task2 : groupTask.task1;
                ((GroupedTaskView) taskView).bind(leftTopTask, rightBottomTask, mOrientationState,
                        mTaskOverlayFactory, groupTask.mSplitBounds);
            } else if (taskView instanceof DesktopTaskView) {
                ((DesktopTaskView) taskView).bind(((DesktopTask) groupTask).tasks,
                        mOrientationState, mTaskOverlayFactory);
                mDesktopTaskView = (DesktopTaskView) taskView;
            } else {
                Task task = groupTask.task1.key.id == stagedTaskIdToBeRemoved ? groupTask.task2
                        : groupTask.task1;
                taskView.bind(task, mOrientationState, mTaskOverlayFactory);
            }
            addView(taskView);

            // enables instance filtering if the feature flag for it is on
            if (FeatureFlags.ENABLE_MULTI_INSTANCE.get()) {
                taskView.setUpShowAllInstancesListener();
            }
        }

        if (!taskGroups.isEmpty()) {
            addView(mClearAllButton);
        }

        // Keep same previous focused task
        TaskView newFocusedTaskView = getTaskViewByTaskIds(focusedTaskIds);
        // If the list changed, maybe the focused task doesn't exist anymore
        if (newFocusedTaskView == null && getTaskViewCount() > 0) {
            newFocusedTaskView = getTaskViewAt(0);
        }
        mFocusedTaskViewId = newFocusedTaskView != null && !enableGridOnlyOverview()
                ? newFocusedTaskView.getTaskViewId() : INVALID_TASK_ID;
        updateTaskSize();
        updateChildTaskOrientations();

        TaskView newRunningTaskView = null;
        if (hasAllValidTaskIds(runningTaskIds)) {
            // Update mRunningTaskViewId to be the new TaskView that was assigned by binding
            // the full list of tasks to taskViews
            newRunningTaskView = getTaskViewByTaskIds(runningTaskIds);
            if (newRunningTaskView != null) {
                setRunningTaskViewId(newRunningTaskView.getTaskViewId());
            } else {
                if (mActiveGestureRunningTasks != null) {
                    // This will update mRunningTaskViewId and create a stub view if necessary.
                    // We try to avoid this because it can cause a scroll jump, but it is needed
                    // for cases where the running task isn't included in this load plan (e.g. if
                    // the current running task is excludedFromRecents.)
                    showCurrentTask(mActiveGestureRunningTasks);
                } else {
                    setRunningTaskViewId(INVALID_TASK_ID);
                }
            }
        }

        int targetPage = -1;
        if (mNextPage != INVALID_PAGE) {
            // Restore mCurrentPage but don't call setCurrentPage() as that clobbers the scroll.
            mCurrentPage = previousCurrentPage;
            if (hasAllValidTaskIds(currentTaskIds)) {
                currentTaskView = getTaskViewByTaskIds(currentTaskIds);
                if (currentTaskView != null) {
                    targetPage = indexOfChild(currentTaskView);
                }
            }
        } else if (previousFocusedPage != INVALID_PAGE) {
            targetPage = previousFocusedPage;
        } else {
            // Set the current page to the running task, but not if settling on new task.
            if (hasAllValidTaskIds(runningTaskIds)) {
                targetPage = indexOfChild(newRunningTaskView);
            } else if (getTaskViewCount() > 0) {
                targetPage = indexOfChild(requireTaskViewAt(0));
            }
        }
        if (targetPage != -1 && mCurrentPage != targetPage) {
            int finalTargetPage = targetPage;
            runOnPageScrollsInitialized(() -> {
                // TODO(b/246283207): Remove logging once root cause of flake detected.
                if (Utilities.isRunningInTestHarness()) {
                    Log.d("b/246283207", "RecentsView#applyLoadPlan() -> "
                            + "previousCurrentPage: " + previousCurrentPage
                            + ", targetPage: " + finalTargetPage
                            + ", getScrollForPage(targetPage): "
                            + getScrollForPage(finalTargetPage));
                }
                setCurrentPage(finalTargetPage);
            });
        }

        if (mIgnoreResetTaskId != INVALID_TASK_ID &&
                getTaskViewByTaskId(mIgnoreResetTaskId) != ignoreResetTaskView) {
            // If the taskView mapping is changing, do not preserve the visuals. Since we are
            // mostly preserving the first task, and new taskViews are added to the end, it should
            // generally map to the same task.
            mIgnoreResetTaskId = INVALID_TASK_ID;
        }
        resetTaskVisuals();
        onTaskStackUpdated();
        updateEnabledOverlays();
        if (isPageScrollsInitialized()) {
            onPageScrollsInitialized();
        }
    }

    private boolean isModal() {
        return mTaskModalness > 0;
    }

    public boolean isLoadingTasks() {
        return mModel.isLoadingTasksInBackground();
    }

    private void removeTasksViewsAndClearAllButton() {
        // This handles an edge case where applyLoadPlan happens during a gesture when the
        // only Task is one with excludeFromRecents, in which case we should not remove it.
        final int stubRunningTaskIndex = isGestureActive() ? getRunningTaskIndex() : -1;

        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            if (i == stubRunningTaskIndex) {
                continue;
            }
            removeView(requireTaskViewAt(i));
        }
        if (getTaskViewCount() == 0 && indexOfChild(mClearAllButton) != -1) {
            removeView(mClearAllButton);
        }
    }

    public int getTaskViewCount() {
        int taskViewCount = getChildCount();
        if (indexOfChild(mClearAllButton) != -1) {
            taskViewCount--;
        }
        return taskViewCount;
    }

    public int getGroupedTaskViewCount() {
        int groupViewCount = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof GroupedTaskView) {
                groupViewCount++;
            }
        }
        return groupViewCount;
    }

    /**
     * Returns the number of tasks in the top row of the overview grid.
     */
    public int getTopRowTaskCountForTablet() {
        return mTopRowIdSet.size();
    }

    /**
     * Returns the number of tasks in the bottom row of the overview grid.
     */
    public int getBottomRowTaskCountForTablet() {
        return getTaskViewCount() - mTopRowIdSet.size() - (enableGridOnlyOverview() ? 0 : 1);
    }

    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }

    public void resetTaskVisuals() {
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView taskView = requireTaskViewAt(i);
            if (Arrays.stream(taskView.getTaskIds()).noneMatch(
                    taskId -> taskId == mIgnoreResetTaskId)) {
                taskView.resetViewTransforms();
                taskView.setIconScaleAndDim(mTaskIconScaledDown ? 0 : 1);
                taskView.setStableAlpha(mContentAlpha);
                taskView.setFullscreenProgress(mFullscreenProgress);
                taskView.setModalness(mTaskModalness);
                taskView.setTaskThumbnailSplashAlpha(mTaskThumbnailSplashAlpha);
            }
        }
        // resetTaskVisuals is called at the end of dismiss animation which could update
        // primary and secondary translation of the live tile cut out. We will need to do so
        // here accordingly.
        runActionOnRemoteHandles(remoteTargetHandle -> {
            TaskViewSimulator simulator = remoteTargetHandle.getTaskViewSimulator();
            simulator.taskPrimaryTranslation.value = 0;
            simulator.taskSecondaryTranslation.value = 0;
            simulator.fullScreenProgress.value = 0;
            simulator.recentsViewScale.value = 1;
        });
        // Similar to setRunningTaskHidden below, reapply the state before runningTaskView is
        // null.
        if (!mRunningTaskShowScreenshot) {
            setRunningTaskViewShowScreenshot(mRunningTaskShowScreenshot);
        }
        if (mRunningTaskTileHidden) {
            setRunningTaskHidden(mRunningTaskTileHidden);
        }

        updateCurveProperties();
        // Update the set of visible task's data
        loadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);
        setTaskModalness(0);
        setColorTint(0);
    }

    public void setFullscreenProgress(float fullscreenProgress) {
        mFullscreenProgress = fullscreenProgress;
        if (enableRefactorTaskThumbnail()) {
            mRecentsViewData.getFullscreenProgress().setValue(mFullscreenProgress);
        }
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            requireTaskViewAt(i).setFullscreenProgress(mFullscreenProgress);
        }
        mClearAllButton.setFullscreenProgress(fullscreenProgress);

        // Fade out the actions view quickly (0.1 range)
        mActionsView.getFullscreenAlpha().updateValue(
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
        DeviceProfile dp = mContainer.getDeviceProfile();
        setOverviewGridEnabled(
                getStateManager().getState().displayOverviewTasksAsGrid(dp));
        if (enableGridOnlyOverview()) {
            mActionsView.updateHiddenFlags(HIDDEN_ACTIONS_IN_MENU, dp.isTablet);
        }
        setPageSpacing(dp.overviewPageSpacing);

        // Propagate DeviceProfile change event.
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator().setDp(dp));
        mOrientationState.setDeviceProfile(dp);

        // Update RecentsView and TaskView's DeviceProfile dependent layout.
        updateOrientationHandler();
        mActionsView.updateDimension(dp, mLastComputedTaskSize);
    }

    private void updateOrientationHandler() {
        updateOrientationHandler(true);
    }

    private void updateOrientationHandler(boolean forceRecreateDragLayerControllers) {
        // Handle orientation changes.
        RecentsPagedOrientationHandler oldOrientationHandler = getPagedOrientationHandler();
        setOrientationHandler(mOrientationState.getOrientationHandler());

        mIsRtl = getPagedOrientationHandler().getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl
                ? View.LAYOUT_DIRECTION_RTL
                : View.LAYOUT_DIRECTION_LTR);
        mClearAllButton.setLayoutDirection(mIsRtl
                ? View.LAYOUT_DIRECTION_LTR
                : View.LAYOUT_DIRECTION_RTL);
        mClearAllButton.setRotation(getPagedOrientationHandler().getDegreesRotated());

        boolean isOrientationHandlerChanged =
                !getPagedOrientationHandler().equals(oldOrientationHandler);
        if (forceRecreateDragLayerControllers || isOrientationHandlerChanged) {
            // Changed orientations, update controllers so they intercept accordingly.
            mContainer.getDragLayer().recreateControllers();
            onOrientationChanged();
            resetTaskVisuals();
            // Log fake orientation changed.
            if (isOrientationHandlerChanged) {
                logOrientationChanged();
            }
        }

        boolean isInLandscape = mOrientationState.getTouchRotation() != ROTATION_0
                || mOrientationState.getRecentsActivityRotation() != ROTATION_0;
        mActionsView.updateHiddenFlags(HIDDEN_NON_ZERO_ROTATION,
                !mOrientationState.isRecentsActivityRotationAllowed() && isInLandscape);

        // Recalculate DeviceProfile dependent layout.
        updateSizeAndPadding();

        // Update TaskView's DeviceProfile dependent layout.
        updateChildTaskOrientations();

        requestLayout();
        // Reapply the current page to update page scrolls.
        setCurrentPage(mCurrentPage);
    }

    private void onOrientationChanged() {
        // If overview is in modal state when rotate, reset it to overview state without running
        // animation.
        setModalStateEnabled(/* taskId= */ INVALID_TASK_ID, /* animate= */ false);
        if (isSplitSelectionActive()) {
            onRotateInSplitSelectionState();
        }
    }

    // Update task size and padding that are dependent on DeviceProfile and insets.
    private void updateSizeAndPadding() {
        DeviceProfile dp = mContainer.getDeviceProfile();
        getTaskSize(mLastComputedTaskSize);
        mTaskWidth = mLastComputedTaskSize.width();
        mTaskHeight = mLastComputedTaskSize.height();
        setPadding(mLastComputedTaskSize.left - mInsets.left,
                mLastComputedTaskSize.top - dp.overviewTaskThumbnailTopMarginPx - mInsets.top,
                dp.widthPx - mInsets.right - mLastComputedTaskSize.right,
                dp.heightPx - mInsets.bottom - mLastComputedTaskSize.bottom);

        mSizeStrategy.calculateGridSize(dp, mContainer, mLastComputedGridSize);
        mSizeStrategy.calculateGridTaskSize(mContainer, dp, mLastComputedGridTaskSize,
                getPagedOrientationHandler());

        if (enableGridOnlyOverview()) {
            mSizeStrategy.calculateCarouselTaskSize(mContainer, dp, mLastComputedCarouselTaskSize,
                    getPagedOrientationHandler());
        }

        mTaskGridVerticalDiff = mLastComputedGridTaskSize.top - mLastComputedTaskSize.top;
        mTopBottomRowHeightDiff =
                mLastComputedGridTaskSize.height() + dp.overviewTaskThumbnailTopMarginPx
                        + dp.overviewRowSpacing;

        // Force TaskView to update size from thumbnail
        updateTaskSize();
        updatePivots();
    }

    /**
     * Updates TaskView scaling and translation required to support variable width.
     */
    private void updateTaskSize() {
        updateTaskSize(false);
    }

    /**
     * Updates TaskView scaling and translation required to support variable width.
     *
     * @param isTaskDismissal indicates if update was called due to task dismissal
     */
    private void updateTaskSize(boolean isTaskDismissal) {
        final int taskCount = getTaskViewCount();
        if (taskCount == 0) {
            return;
        }

        float accumulatedTranslationX = 0;
        float translateXToMiddle = 0;
        if (enableGridOnlyOverview() && mContainer.getDeviceProfile().isTablet) {
            translateXToMiddle = mIsRtl
                    ? mLastComputedCarouselTaskSize.right - mLastComputedTaskSize.right
                    : mLastComputedCarouselTaskSize.left - mLastComputedTaskSize.left;
        }
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            taskView.updateTaskSize(mLastComputedTaskSize, mLastComputedGridTaskSize,
                    mLastComputedCarouselTaskSize);
            taskView.setNonGridTranslationX(accumulatedTranslationX);
            taskView.setNonGridPivotTranslationX(translateXToMiddle);
            // Compensate space caused by TaskView scaling.
            float widthDiff =
                    taskView.getLayoutParams().width * (1 - taskView.getNonGridScale());
            accumulatedTranslationX += mIsRtl ? widthDiff : -widthDiff;
        }

        mClearAllButton.setFullscreenTranslationPrimary(accumulatedTranslationX);

        updateGridProperties(isTaskDismissal);
    }

    public void getTaskSize(Rect outRect) {
        mSizeStrategy.calculateTaskSize(mContainer, mContainer.getDeviceProfile(), outRect,
                getPagedOrientationHandler());
    }

    /**
     * Sets the last TaskView selected.
     */
    public void setSelectedTask(int lastSelectedTaskId) {
        mSelectedTask = getTaskViewByTaskId(lastSelectedTaskId);
    }

    /**
     * Returns the bounds of the task selected to enter modal state.
     */
    public Rect getSelectedTaskBounds() {
        if (mSelectedTask == null) {
            return mLastComputedTaskSize;
        }
        return getTaskBounds(mSelectedTask);
    }

    private Rect getTaskBounds(TaskView taskView) {
        int selectedPage = indexOfChild(taskView);
        int primaryScroll = getPagedOrientationHandler().getPrimaryScroll(this);
        int selectedPageScroll = getScrollForPage(selectedPage);
        boolean isTopRow = taskView != null && mTopRowIdSet.contains(taskView.getTaskViewId());
        Rect outRect = new Rect(mLastComputedTaskSize);
        outRect.offset(
                -(primaryScroll - (selectedPageScroll + getOffsetFromScrollPosition(selectedPage))),
                (int) (showAsGrid() && enableGridOnlyOverview() && !isTopRow
                        ? mTopBottomRowHeightDiff : 0));
        return outRect;
    }

    /** Gets the last computed task size */
    public Rect getLastComputedTaskSize() {
        return mLastComputedTaskSize;
    }

    public Rect getLastComputedGridTaskSize() {
        return mLastComputedGridTaskSize;
    }

    public Rect getLastComputedCarouselTaskSize() {
        return mLastComputedCarouselTaskSize;
    }

    /** Gets the task size for modal state. */
    public void getModalTaskSize(Rect outRect) {
        mSizeStrategy.calculateModalTaskSize(mContainer, mContainer.getDeviceProfile(), outRect,
                getPagedOrientationHandler());
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
        }

        // Update ActionsView's visibility when scroll changes.
        updateActionsViewFocusedScroll();

        // Update the high res thumbnail loader state
        mModel.getThumbnailCache().getHighResLoadingState().setFlingingFast(isFlingingFast);
        return scrolling;
    }

    private void updateActionsViewFocusedScroll() {
        if (showAsGrid()) {
            float actionsViewAlphaValue = isFocusedTaskInExpectedScrollPosition() ? 1 : 0;
            // If animation is already in progress towards the same end value, do not restart.
            if (mActionsViewAlphaAnimator == null || !mActionsViewAlphaAnimator.isStarted()
                    || (mActionsViewAlphaAnimator.isStarted()
                    && mActionsViewAlphaAnimatorFinalValue != actionsViewAlphaValue)) {
                animateActionsViewAlpha(actionsViewAlphaValue,
                        DEFAULT_ACTIONS_VIEW_ALPHA_ANIMATION_DURATION);
            }
        }
    }

    private void animateActionsViewAlpha(float alphaValue, long duration) {
        mActionsViewAlphaAnimator = ObjectAnimator.ofFloat(mActionsView.getVisibilityAlpha(),
                AnimatedFloat.VALUE, alphaValue);
        mActionsViewAlphaAnimatorFinalValue = alphaValue;
        mActionsViewAlphaAnimator.setDuration(duration);
        // Set autocancel to prevent race-conditiony setting of alpha from other animations
        mActionsViewAlphaAnimator.setAutoCancel(true);
        mActionsViewAlphaAnimator.start();
    }

    /**
     * Scales and adjusts translation of adjacent pages as if on a curved carousel.
     */
    public void updateCurveProperties() {
        if (getPageCount() == 0 || getPageAt(0).getMeasuredWidth() == 0) {
            return;
        }
        int scroll = getPagedOrientationHandler().getPrimaryScroll(this);
        mClearAllButton.onRecentsViewScroll(scroll, mOverviewGridEnabled);

        // Clear all button alpha was set by the previous line.
        mActionsView.getIndexScrollAlpha().updateValue(1 - mClearAllButton.getScrollAlpha());
    }

    @Override
    protected int getDestinationPage(int scaledScroll) {
        if (!mContainer.getDeviceProfile().isTablet) {
            return super.getDestinationPage(scaledScroll);
        }
        if (!isPageScrollsInitialized()) {
            Log.e(TAG,
                    "Cannot get destination page: RecentsView not properly initialized",
                    new IllegalStateException());
            return INVALID_PAGE;
        }

        // When in tablet with variable task width, return the page which scroll is closest to
        // screenStart instead of page nearest to center of screen.
        int minDistanceFromScreenStart = Integer.MAX_VALUE;
        int minDistanceFromScreenStartIndex = INVALID_PAGE;
        for (int i = 0; i < getChildCount(); ++i) {
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
        boolean hasLeftOverview = !mOverviewStateEnabled && mScroller.isFinished();
        if (hasLeftOverview || mTaskListChangeId == -1) {
            // Skip loading visible task data if we've already left the overview state, or if the
            // task list hasn't been loaded yet (the task views will not reflect the task list)
            return;
        }

        int lower = 0;
        int upper = 0;
        int visibleStart = 0;
        int visibleEnd = 0;
        if (showAsGrid()) {
            int screenStart = getPagedOrientationHandler().getPrimaryScroll(this);
            int pageOrientedSize = getPagedOrientationHandler().getMeasuredSize(this);
            // For GRID_ONLY_OVERVIEW, use +/- 1 task column as visible area for preloading
            // adjacent thumbnails, otherwise use +/-50% screen width
            int extraWidth = enableGridOnlyOverview() ? getLastComputedTaskSize().width()
                    + getPageSpacing() : pageOrientedSize / 2;
            visibleStart = screenStart - extraWidth;
            visibleEnd = screenStart + pageOrientedSize + extraWidth;
        } else {
            int centerPageIndex = getPageNearestToCenterOfScreen();
            int numChildren = getChildCount();
            lower = Math.max(0, centerPageIndex - 2);
            upper = Math.min(centerPageIndex + 2, numChildren - 1);
        }

        List<Integer> visibleTaskIds = new ArrayList<>();

        // Update the task data for the in/visible children
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = requireTaskViewAt(i);
            List<TaskContainer> containers = taskView.getTaskContainers();
            if (containers.isEmpty()) {
                continue;
            }
            int index = indexOfChild(taskView);
            boolean visible;
            if (showAsGrid()) {
                visible = isTaskViewWithinBounds(taskView, visibleStart, visibleEnd);
            } else {
                visible = lower <= index && index <= upper;
            }
            if (visible) {
                // Default update all non-null tasks, then remove running ones
                List<Task> tasksToUpdate = containers.stream()
                        .map(TaskContainer::getTask)
                        .collect(Collectors.toCollection(ArrayList::new));
                if (enableRefactorTaskThumbnail()) {
                    visibleTaskIds.addAll(
                            tasksToUpdate.stream().map((task) -> task.key.id).toList());
                }
                if (mTmpRunningTasks != null) {
                    for (Task t : mTmpRunningTasks) {
                        // Skip loading if this is the task that we are animating into
                        // TODO(b/280812109) change this equality check to use A.equals(B)
                        tasksToUpdate.removeIf(task -> task == t);
                    }
                }
                if (tasksToUpdate.isEmpty()) {
                    continue;
                }
                for (Task task : tasksToUpdate) {
                    if (!mHasVisibleTaskData.get(task.key.id)) {
                        // Ignore thumbnail update if it's current running task during the gesture
                        // We snapshot at end of gesture, it will update then
                        int changes = dataChanges;
                        if (taskView == getRunningTaskView() && isGestureActive()) {
                            changes &= ~TaskView.FLAG_UPDATE_THUMBNAIL;
                        }
                        taskView.onTaskListVisibilityChanged(true /* visible */, changes);
                    }
                    mHasVisibleTaskData.put(task.key.id, visible);
                }
            } else {
                for (TaskContainer container : containers) {
                    if (container == null) {
                        continue;
                    }

                    if (mHasVisibleTaskData.get(container.getTask().key.id)) {
                        taskView.onTaskListVisibilityChanged(false /* visible */, dataChanges);
                    }
                    mHasVisibleTaskData.delete(container.getTask().key.id);
                }
            }
        }
        if (enableRefactorTaskThumbnail()) {
            mTasksRepository.setVisibleTasks(visibleTaskIds);
        }
    }

    /**
     * Unloads any associated data from the currently visible tasks
     */
    private void unloadVisibleTaskData(@TaskView.TaskDataChanges int dataChanges) {
        for (int i = 0; i < mHasVisibleTaskData.size(); i++) {
            if (mHasVisibleTaskData.valueAt(i)) {
                TaskView taskView = getTaskViewByTaskId(mHasVisibleTaskData.keyAt(i));
                if (taskView != null) {
                    taskView.onTaskListVisibilityChanged(false /* visible */, dataChanges);
                }
            }
        }
        mHasVisibleTaskData.clear();
    }

    @Override
    public void onHighResLoadingStateChanged(boolean enabled) {
        // Preload cache when no overview task is visible (e.g. not in overview page), so when
        // user goes to overview next time, the task thumbnails would show up without delay
        if (mHasVisibleTaskData.size() == 0) {
            mModel.preloadCacheIfNeeded();
        }

        // Whenever the high res loading state changes, poke each of the visible tasks to see if
        // they want to updated their thumbnail state
        for (int i = 0; i < mHasVisibleTaskData.size(); i++) {
            if (mHasVisibleTaskData.valueAt(i)) {
                TaskView taskView = getTaskViewByTaskId(mHasVisibleTaskData.keyAt(i));
                if (taskView != null) {
                    // Poke the view again, which will trigger it to load high res if the state
                    // is enabled
                    taskView.onTaskListVisibilityChanged(true /* visible */);
                }
            }
        }
    }

    public void startHome() {
        startHome(mContainer.isStarted());
    }

    public void startHome(boolean animated) {
        if (!canStartHomeSafely()) return;
        handleStartHome(animated);
    }

    protected abstract void handleStartHome(boolean animated);

    /** Returns whether user can start home based on state in {@link OverviewCommandHelper}. */
    protected abstract boolean canStartHomeSafely();

    /** Returns the state manager used in RecentsView **/
    public abstract StateManager<STATE_TYPE,
            ? extends StatefulContainer<STATE_TYPE>> getStateManager();

    public void reset() {
        setCurrentTask(-1);
        mCurrentPageScrollDiff = 0;
        mIgnoreResetTaskId = -1;
        mTaskListChangeId = -1;
        mFocusedTaskViewId = -1;

        Log.d(TAG, "reset - mEnableDrawingLiveTile: " + mEnableDrawingLiveTile
                + ", mRecentsAnimationController: " + mRecentsAnimationController);
        if (mEnableDrawingLiveTile) {
            if (mRecentsAnimationController != null) {
                // We owns mRecentsAnimationController, finish it now to clean up.
                finishRecentsAnimation(true /* toRecents */, null);
            } else {
                // Only clean up target set if we no longer owns mRecentsAnimationController.
                runActionOnRemoteHandles(remoteTargetHandle ->
                        remoteTargetHandle.getTransformParams().setTargetSet(null));
            }
            setEnableDrawingLiveTile(false);
        }
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTaskViewSimulator().setDrawsBelowRecents(false));
        if (!FeatureFlags.enableSplitContextually()) {
            resetFromSplitSelectionState();
        }

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

    public int getRunningTaskViewId() {
        return mRunningTaskViewId;
    }

    protected int[] getTaskIdsForRunningTaskView() {
        return getTaskIdsForTaskViewId(mRunningTaskViewId);
    }

    private int[] getTaskIdsForTaskViewId(int taskViewId) {
        // For now 2 distinct task IDs is max for split screen
        TaskView runningTaskView = getTaskViewFromTaskViewId(taskViewId);
        if (runningTaskView == null) {
            return new int[0];
        }

        return runningTaskView.getTaskIds();
    }

    public @Nullable TaskView getRunningTaskView() {
        return getTaskViewFromTaskViewId(mRunningTaskViewId);
    }

    public @Nullable TaskView getFocusedTaskView() {
        return getTaskViewFromTaskViewId(mFocusedTaskViewId);
    }

    @Nullable
    private TaskView getTaskViewFromTaskViewId(int taskViewId) {
        if (taskViewId == -1) {
            return null;
        }

        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = requireTaskViewAt(i);
            if (taskView.getTaskViewId() == taskViewId) {
                return taskView;
            }
        }
        return null;
    }

    public int getRunningTaskIndex() {
        TaskView taskView = getRunningTaskView();
        return taskView == null ? -1 : indexOfChild(taskView);
    }

    protected @Nullable TaskView getHomeTaskView() {
        return null;
    }

    /**
     * Handle the edge case where Recents could increment task count very high over long
     * period of device usage. Probably will never happen, but meh.
     */
    private TaskView getTaskViewFromPool(@TaskView.Type int type) {
        TaskView taskView;
        switch (type) {
            case TaskView.Type.GROUPED:
                taskView = mGroupedTaskViewPool.getView();
                break;
            case TaskView.Type.DESKTOP:
                taskView = mDesktopTaskViewPool.getView();
                break;
            case TaskView.Type.SINGLE:
            default:
                taskView = mTaskViewPool.getView();
        }
        taskView.setTaskViewId(mTaskViewIdCount);
        if (mTaskViewIdCount == Integer.MAX_VALUE) {
            mTaskViewIdCount = 0;
        } else {
            mTaskViewIdCount++;
        }

        return taskView;
    }

    /**
     * Get the index of the task view whose id matches {@param taskId}.
     * @return -1 if there is no task view for the task id, else the index of the task view.
     */
    public int getTaskIndexForId(int taskId) {
        TaskView tv = getTaskViewByTaskId(taskId);
        return tv == null ? -1 : indexOfChild(tv);
    }

    /**
     * Reloads the view if anything in recents changed.
     */
    public void reloadIfNeeded() {
        if (!mModel.isTaskListValid(mTaskListChangeId)) {
            mTaskListChangeId = mModel.getTasks(this::applyLoadPlan, RecentsFilterState
                    .getFilter(mFilterState.getPackageNameToFilter()));
            if (enableRefactorTaskThumbnail()) {
                mTasksRepository.getAllTaskData(/* forceRefresh = */ true);
            }
        }
    }

    /**
     * Called when a gesture from an app is starting.
     */
    public void onGestureAnimationStart(
            Task[] runningTasks, RotationTouchHelper rotationTouchHelper) {
        Log.d(TAG, "onGestureAnimationStart - runningTasks: " + Arrays.toString(runningTasks));
        mActiveGestureRunningTasks = runningTasks;
        // This needs to be called before the other states are set since it can create the task view
        if (mOrientationState.setGestureActive(true)) {
            setLayoutRotation(rotationTouchHelper.getCurrentActiveRotation(),
                    rotationTouchHelper.getDisplayRotation());
            // Force update to ensure the initial task size is computed even if the orientation has
            // not changed.
            updateSizeAndPadding();
        }

        showCurrentTask(mActiveGestureRunningTasks);
        setEnableFreeScroll(false);
        setEnableDrawingLiveTile(false);
        setRunningTaskHidden(true);
        setTaskIconScaledDown(true);
    }

    private boolean isGestureActive() {
        return mActiveGestureRunningTasks != null;
    }

    /**
     * Called only when a swipe-up gesture from an app has completed. Only called after
     * {@link #onGestureAnimationStart} and {@link #onGestureAnimationEnd()}.
     */
    public void onSwipeUpAnimationSuccess() {
        animateUpTaskIconScale();
        setSwipeDownShouldLaunchApp(true);
    }

    private void animateRecentsRotationInPlace(int newRotation) {
        if (mOrientationState.isRecentsActivityRotationAllowed()) {
            // Let system take care of the rotation
            return;
        }

        if (mRunningTaskShowScreenshot) {
            animateRotation(newRotation);
        } else {
            // Animate the rotation and stops running task
            switchToScreenshot(() -> {
                animateRotation(newRotation);
                finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                        null /* onFinishComplete */);
            });
        }
    }

    private void animateRotation(int newRotation) {
        AbstractFloatingView.closeAllOpenViewsExcept(mContainer, false, TYPE_REBIND_SAFE);
        AnimatorSet pa = setRecentsChangedOrientation(true);
        pa.addListener(AnimatorListeners.forSuccessCallback(() -> {
            setLayoutRotation(newRotation, mOrientationState.getDisplayRotation());
            mContainer.getDragLayer().recreateControllers();
            setRecentsChangedOrientation(false).start();
        }));
        pa.start();
    }

    public AnimatorSet setRecentsChangedOrientation(boolean fadeOut) {
        AnimatorSet as = new AnimatorSet();
        as.play(ObjectAnimator.ofFloat(this, View.ALPHA, fadeOut ? 0 : 1));
        return as;
    }

    private void updateChildTaskOrientations() {
        for (int i = 0; i < getTaskViewCount(); i++) {
            requireTaskViewAt(i).setOrientationState(mOrientationState);
        }
        boolean shouldRotateMenuForFakeRotation =
                !mOrientationState.isRecentsActivityRotationAllowed();
        if (!shouldRotateMenuForFakeRotation) {
            return;
        }
        TaskMenuView tv = (TaskMenuView) getTopOpenViewWithType(mContainer, TYPE_TASK_MENU);
        if (tv != null) {
            // Rotation is supported on phone (details at b/254198019#comment4)
            tv.onRotationChanged();
        }
    }

    /**
     * Called when a gesture from an app has finished, and an end target has been determined.
     */
    public void onPrepareGestureEndAnimation(
            @Nullable AnimatorSet animatorSet, GestureState.GestureEndTarget endTarget,
            TaskViewSimulator[] taskViewSimulators) {
        Log.d(TAG, "onPrepareGestureEndAnimation - endTarget: " + endTarget);
        mCurrentGestureEndTarget = endTarget;
        boolean isOverviewEndTarget = endTarget == GestureState.GestureEndTarget.RECENTS;
        if (isOverviewEndTarget) {
            updateGridProperties();
        }

        BaseState<?> endState = mSizeStrategy.stateFromGestureEndTarget(endTarget);
        if (endState.displayOverviewTasksAsGrid(mContainer.getDeviceProfile())) {
            TaskView runningTaskView = getRunningTaskView();
            float runningTaskPrimaryGridTranslation = 0;
            float runningTaskSecondaryGridTranslation = 0;
            if (runningTaskView != null) {
                // Apply the grid translation to running task unless it's being snapped to
                // and removes the current translation applied to the running task.
                runningTaskPrimaryGridTranslation = runningTaskView.getGridTranslationX()
                        - runningTaskView.getNonGridTranslationX();
                runningTaskSecondaryGridTranslation = runningTaskView.getGridTranslationY();
            }
            for (TaskViewSimulator tvs : taskViewSimulators) {
                if (animatorSet == null) {
                    setGridProgress(1);
                    tvs.taskPrimaryTranslation.value = runningTaskPrimaryGridTranslation;
                    tvs.taskSecondaryTranslation.value = runningTaskSecondaryGridTranslation;
                } else {
                    animatorSet.play(ObjectAnimator.ofFloat(this, RECENTS_GRID_PROGRESS, 1));
                    animatorSet.play(tvs.carouselScale.animateToValue(1));
                    animatorSet.play(tvs.carouselPrimaryTranslation.animateToValue(0));
                    animatorSet.play(tvs.carouselSecondaryTranslation.animateToValue(0));
                    animatorSet.play(tvs.taskPrimaryTranslation.animateToValue(
                            runningTaskPrimaryGridTranslation));
                    animatorSet.play(tvs.taskSecondaryTranslation.animateToValue(
                            runningTaskSecondaryGridTranslation));
                }
            }
        }
        int splashAlpha = endState.showTaskThumbnailSplash() ? 1 : 0;
        if (animatorSet == null) {
            setTaskThumbnailSplashAlpha(splashAlpha);
        } else {
            animatorSet.play(
                    ObjectAnimator.ofFloat(this, TASK_THUMBNAIL_SPLASH_ALPHA, splashAlpha));
        }
    }

    /**
     * Called when a gesture from an app has finished, and the animation to the target has ended.
     */
    public void onGestureAnimationEnd() {
        mActiveGestureRunningTasks = null;
        if (mOrientationState.setGestureActive(false)) {
            updateOrientationHandler(/* forceRecreateDragLayerControllers = */ false);
        }

        setEnableFreeScroll(true);
        setEnableDrawingLiveTile(mCurrentGestureEndTarget == GestureState.GestureEndTarget.RECENTS);
        Log.d(TAG, "onGestureAnimationEnd - mEnableDrawingLiveTile: " + mEnableDrawingLiveTile);
        setRunningTaskHidden(false);
        animateUpTaskIconScale();
        animateActionsViewIn();

        mCurrentGestureEndTarget = null;
    }

    /**
     * Returns true if we should add a stub taskView for the running task id
     */
    protected boolean shouldAddStubTaskView(Task[] runningTasks) {
        int[] runningTaskIds = Arrays.stream(runningTasks).mapToInt(task -> task.key.id).toArray();
        TaskView matchingTaskView = null;
        if (hasDesktopTask(runningTasks) && runningTaskIds.length == 1) {
            // TODO(b/249371338): Unsure if it's expected, desktop runningTasks only have a single
            // taskId, therefore we match any DesktopTaskView that contains the runningTaskId.
            TaskView taskview = getTaskViewByTaskId(runningTaskIds[0]);
            if (taskview instanceof DesktopTaskView) {
                matchingTaskView = taskview;
            }
        } else {
            matchingTaskView = getTaskViewByTaskIds(runningTaskIds);
        }
        return matchingTaskView == null;
    }

    /**
     * Creates a task view (if necessary) to represent the task with the {@param runningTaskId}.
     *
     * All subsequent calls to reload will keep the task as the first item until {@link #reset()}
     * is called.  Also scrolls the view to this task.
     */
    private void showCurrentTask(Task[] runningTasks) {
        Log.d(TAG, "showCurrentTask - runningTasks: " + Arrays.toString(runningTasks));
        if (runningTasks.length == 0) {
            return;
        }
        int runningTaskViewId = -1;
        boolean needGroupTaskView = runningTasks.length > 1;
        boolean needDesktopTask = hasDesktopTask(runningTasks);
        if (shouldAddStubTaskView(runningTasks)) {
            boolean wasEmpty = getChildCount() == 0;
            // Add an empty view for now until the task plan is loaded and applied
            final TaskView taskView;
            if (needDesktopTask) {
                taskView = getTaskViewFromPool(TaskView.Type.DESKTOP);
                mTmpRunningTasks = Arrays.copyOf(runningTasks, runningTasks.length);
                ((DesktopTaskView) taskView).bind(Arrays.asList(mTmpRunningTasks),
                        mOrientationState, mTaskOverlayFactory);
            } else if (needGroupTaskView) {
                taskView = getTaskViewFromPool(TaskView.Type.GROUPED);
                mTmpRunningTasks = new Task[]{runningTasks[0], runningTasks[1]};
                // When we create a placeholder task view mSplitBoundsConfig will be null, but with
                // the actual app running we won't need to show the thumbnail until all the tasks
                // load later anyways
                ((GroupedTaskView)taskView).bind(mTmpRunningTasks[0], mTmpRunningTasks[1],
                        mOrientationState, mTaskOverlayFactory, mSplitBoundsConfig);
            } else {
                taskView = getTaskViewFromPool(TaskView.Type.SINGLE);
                // The temporary running task is only used for the duration between the start of the
                // gesture and the task list is loaded and applied
                mTmpRunningTasks = new Task[]{runningTasks[0]};
                taskView.bind(mTmpRunningTasks[0], mOrientationState, mTaskOverlayFactory);
            }
            addView(taskView, 0);
            runningTaskViewId = taskView.getTaskViewId();
            if (wasEmpty) {
                addView(mClearAllButton);
            }

            // Measure and layout immediately so that the scroll values is updated instantly
            // as the user might be quick-switching
            measure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                    makeMeasureSpec(getMeasuredHeight(), EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        } else if (getTaskViewByTaskId(runningTasks[0].key.id) != null) {
            runningTaskViewId = getTaskViewByTaskId(runningTasks[0].key.id).getTaskViewId();
        }

        boolean runningTaskTileHidden = mRunningTaskTileHidden;
        setCurrentTask(runningTaskViewId);
        mFocusedTaskViewId = enableGridOnlyOverview() ? INVALID_TASK_ID : runningTaskViewId;
        runOnPageScrollsInitialized(() -> setCurrentPage(getRunningTaskIndex()));
        setRunningTaskViewShowScreenshot(false);
        setRunningTaskHidden(runningTaskTileHidden);
        // Update task size after setting current task.
        updateTaskSize();
        updateChildTaskOrientations();

        // Reload the task list
        reloadIfNeeded();
    }

    private boolean hasDesktopTask(Task[] runningTasks) {
        if (!DesktopModeStatus.canEnterDesktopMode(mContext)) {
            return false;
        }
        for (Task task : runningTasks) {
            if (task.key.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the running task id, cleaning up the old running task if necessary.
     */
    public void setCurrentTask(int runningTaskViewId) {
        if (mRunningTaskViewId == runningTaskViewId) {
            return;
        }

        if (mRunningTaskViewId != -1) {
            // Reset the state on the old running task view
            setTaskIconScaledDown(false);
            setRunningTaskViewShowScreenshot(true);
            setRunningTaskHidden(false);
        }
        setRunningTaskViewId(runningTaskViewId);
    }

    private void setRunningTaskViewId(int runningTaskViewId) {
        int prevRunningTaskViewId = mRunningTaskViewId;
        mRunningTaskViewId = runningTaskViewId;

        if (Flags.enableRefactorTaskThumbnail()) {
            TaskView previousRunningTaskView = getTaskViewFromTaskViewId(prevRunningTaskViewId);
            if (previousRunningTaskView != null) {
                previousRunningTaskView.notifyIsRunningTaskUpdated();
            }
            TaskView newRunningTaskView = getTaskViewFromTaskViewId(runningTaskViewId);
            if (newRunningTaskView != null) {
                newRunningTaskView.notifyIsRunningTaskUpdated();
            }
        }
    }

    private int getTaskViewIdFromTaskId(int taskId) {
        TaskView taskView = getTaskViewByTaskId(taskId);
        return taskView != null ? taskView.getTaskViewId() : -1;
    }

    /**
     * Hides the tile associated with {@link #mRunningTaskViewId}
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
        mRunningTaskShowScreenshot = showScreenshot;
        TaskView runningTaskView = getRunningTaskView();
        if (runningTaskView != null) {
            runningTaskView.setShouldShowScreenshot(mRunningTaskShowScreenshot);
        }
    }

    public void setTaskIconScaledDown(boolean isScaledDown) {
        if (mTaskIconScaledDown != isScaledDown) {
            mTaskIconScaledDown = isScaledDown;
            int taskCount = getTaskViewCount();
            for (int i = 0; i < taskCount; i++) {
                requireTaskViewAt(i).setIconScaleAndDim(mTaskIconScaledDown ? 0 : 1);
            }
        }
    }

    private void animateActionsViewIn() {
        if (!showAsGrid() || isFocusedTaskInExpectedScrollPosition()) {
            animateActionsViewAlpha(1, TaskView.SCALE_ICON_DURATION);
        }
    }

    public void animateUpTaskIconScale() {
        mTaskIconScaledDown = false;
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            taskView.animateIconScaleAndDimIntoView();
        }
    }

    /**
     * Updates TaskView and ClearAllButtion scaling and translation required to turn into grid
     * layout.
     * This method is used when no task dismissal has occurred.
     */
    private void updateGridProperties() {
        updateGridProperties(false, Integer.MAX_VALUE);
    }

    /**
     * Updates TaskView and ClearAllButtion scaling and translation required to turn into grid
     * layout.
     *
     * This method is used when task dismissal has occurred, but rebalance is not needed.
     *
     * @param isTaskDismissal indicates if update was called due to task dismissal
     */
    private void updateGridProperties(boolean isTaskDismissal) {
        updateGridProperties(isTaskDismissal, Integer.MAX_VALUE);
    }

    /**
     * Updates TaskView and ClearAllButton scaling and translation required to turn into grid
     * layout.
     *
     * This method only calculates the potential position and depends on {@link #setGridProgress} to
     * apply the actual scaling and translation.
     *
     * @param isTaskDismissal    indicates if update was called due to task dismissal
     * @param startRebalanceAfter which view index to start rebalancing from. Use Integer.MAX_VALUE
     *                           to skip rebalance
     */
    private void updateGridProperties(boolean isTaskDismissal, int startRebalanceAfter) {
        int taskCount = getTaskViewCount();
        if (taskCount == 0) {
            return;
        }

        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        int taskTopMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;

        int topRowWidth = 0;
        int bottomRowWidth = 0;
        float topAccumulatedTranslationX = 0;
        float bottomAccumulatedTranslationX = 0;

        // Contains whether the child index is in top or bottom of grid (for non-focused task)
        // Different from mTopRowIdSet, which contains the taskViewId of what task is in top row
        IntSet topSet = new IntSet();
        IntSet bottomSet = new IntSet();

        // Horizontal grid translation for each task
        float[] gridTranslations = new float[taskCount];

        int focusedTaskIndex = Integer.MAX_VALUE;
        int focusedTaskShift = 0;
        int focusedTaskWidthAndSpacing = 0;
        int snappedTaskRowWidth = 0;
        int snappedPage = isKeyboardTaskFocusPending() ? mKeyboardTaskFocusIndex : getNextPage();
        TaskView snappedTaskView = getTaskViewAt(snappedPage);
        TaskView homeTaskView = getHomeTaskView();
        TaskView nextFocusedTaskView = null;

        if (!isTaskDismissal) {
            mTopRowIdSet.clear();
        }
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
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
                int taskViewId = taskView.getTaskViewId();

                // Rebalance the grid starting after a certain index
                boolean isTopRow;
                if (isTaskDismissal) {
                    if (i > startRebalanceAfter) {
                        mTopRowIdSet.remove(taskViewId);
                        isTopRow = topRowWidth <= bottomRowWidth;
                    } else {
                        isTopRow = mTopRowIdSet.contains(taskViewId);
                    }
                } else {
                    isTopRow = topRowWidth <= bottomRowWidth;
                }

                if (isTopRow) {
                    if (homeTaskView != null && nextFocusedTaskView == null) {
                        // TaskView will be focused when swipe up, don't count towards row width.
                        nextFocusedTaskView = taskView;
                    } else {
                        topRowWidth += taskWidthAndSpacing;
                    }
                    topSet.add(i);
                    mTopRowIdSet.add(taskViewId);

                    taskView.setGridTranslationY(mTaskGridVerticalDiff);

                    // Move horizontally into empty space.
                    float widthOffset = 0;
                    for (int j = i - 1; !topSet.contains(j) && j >= 0; j--) {
                        if (j == focusedTaskIndex) {
                            continue;
                        }
                        widthOffset += requireTaskViewAt(j).getLayoutParams().width + mPageSpacing;
                    }

                    float currentTaskTranslationX = mIsRtl ? widthOffset : -widthOffset;
                    gridTranslations[i] += topAccumulatedTranslationX + currentTaskTranslationX;
                    topAccumulatedTranslationX += currentTaskTranslationX;
                } else {
                    bottomRowWidth += taskWidthAndSpacing;
                    bottomSet.add(i);

                    // Move into bottom row.
                    taskView.setGridTranslationY(mTopBottomRowHeightDiff + mTaskGridVerticalDiff);

                    // Move horizontally into empty space.
                    float widthOffset = 0;
                    for (int j = i - 1; !bottomSet.contains(j) && j >= 0; j--) {
                        if (j == focusedTaskIndex) {
                            continue;
                        }
                        widthOffset += requireTaskViewAt(j).getLayoutParams().width + mPageSpacing;
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
        float snappedTaskNonGridScrollAdjustment = 0;
        float snappedTaskGridTranslationX = 0;
        if (snappedTaskView != null) {
            snappedTaskNonGridScrollAdjustment = snappedTaskView.getScrollAdjustment(
                    /*gridEnabled=*/false);
            snappedTaskGridTranslationX = gridTranslations[snappedPage];
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
        float clearAllShortTotalWidthTranslation = 0;
        int longRowWidth = Math.max(topRowWidth, bottomRowWidth);
        if (longRowWidth < mLastComputedGridSize.width()) {
            mClearAllShortTotalWidthTranslation =
                    (mIsRtl
                            ? mLastComputedTaskSize.right
                            : deviceProfile.widthPx - mLastComputedTaskSize.left)
                    - longRowWidth - deviceProfile.overviewGridSideMargin;
            clearAllShortTotalWidthTranslation = mIsRtl
                    ? -mClearAllShortTotalWidthTranslation : mClearAllShortTotalWidthTranslation;
            if (snappedTaskRowWidth == longRowWidth) {
                // Updated snappedTaskRowWidth as well if it's same as longRowWidth.
                snappedTaskRowWidth += mClearAllShortTotalWidthTranslation;
            }
            longRowWidth += mClearAllShortTotalWidthTranslation;
        } else {
            mClearAllShortTotalWidthTranslation = 0;
        }

        float clearAllTotalTranslationX =
                clearAllAccumulatedTranslation + clearAllShorterRowCompensation
                        + clearAllShortTotalWidthTranslation + snappedTaskNonGridScrollAdjustment;
        if (focusedTaskIndex < taskCount) {
            // Shift by focused task's width and spacing if a task is focused.
            clearAllTotalTranslationX +=
                    mIsRtl ? focusedTaskWidthAndSpacing : -focusedTaskWidthAndSpacing;
        }

        // Make sure there are enough space between snapped page and ClearAllButton, for the case
        // of swiping up after quick switch.
        if (snappedTaskView != null) {
            int distanceFromClearAll = longRowWidth - snappedTaskRowWidth;
            // ClearAllButton should be off screen when snapped task is in its snapped position.
            int minimumDistance =
                    (mIsRtl
                            ? mLastComputedTaskSize.left
                            : deviceProfile.widthPx - mLastComputedTaskSize.right)
                    - deviceProfile.overviewGridSideMargin - mPageSpacing
                    + (mTaskWidth - snappedTaskView.getLayoutParams().width)
                    - mClearAllShortTotalWidthTranslation;
            if (distanceFromClearAll < minimumDistance) {
                int distanceDifference = minimumDistance - distanceFromClearAll;
                snappedTaskGridTranslationX += mIsRtl ? distanceDifference : -distanceDifference;
            }
        }

        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            taskView.setGridTranslationX(gridTranslations[i] - snappedTaskGridTranslationX
                    + snappedTaskNonGridScrollAdjustment);
        }

        final TaskView runningTask = getRunningTaskView();
        if (showAsGrid() && enableGridOnlyOverview() && runningTask != null) {
            runActionOnRemoteHandles(
                    remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                            .taskSecondaryTranslation.value = runningTask.getGridTranslationY()
            );
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
        int taskViewId1 = taskView1.getTaskViewId();
        int taskViewId2 = taskView2.getTaskViewId();
        if (taskViewId1 == mFocusedTaskViewId || taskViewId2 == mFocusedTaskViewId) {
            return false;
        }
        return (mTopRowIdSet.contains(taskViewId1) && mTopRowIdSet.contains(taskViewId2)) || (
                !mTopRowIdSet.contains(taskViewId1) && !mTopRowIdSet.contains(taskViewId2));
    }

    /**
     * Moves TaskView and ClearAllButton between carousel and 2 row grid.
     *
     * @param gridProgress 0 = carousel; 1 = 2 row grid.
     */
    private void setGridProgress(float gridProgress) {
        mGridProgress = gridProgress;

        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            requireTaskViewAt(i).setGridProgress(gridProgress);
        }
        mClearAllButton.setGridProgress(gridProgress);
    }

    private void setTaskThumbnailSplashAlpha(float taskThumbnailSplashAlpha) {
        int taskCount = getTaskViewCount();
        if (taskCount == 0) {
            return;
        }

        mTaskThumbnailSplashAlpha = taskThumbnailSplashAlpha;
        for (int i = 0; i < taskCount; i++) {
            requireTaskViewAt(i).setTaskThumbnailSplashAlpha(taskThumbnailSplashAlpha);
        }
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
        anim.setFloat(taskView, VIEW_ALPHA, 0,
                clampToProgress(isOnGridBottomRow(taskView) ? ACCELERATE : FINAL_FRAME, 0, 0.5f));
        FloatProperty<TaskView> secondaryViewTranslate =
                taskView.getSecondaryDismissTranslationProperty();
        int secondaryTaskDimension = getPagedOrientationHandler().getSecondaryDimension(taskView);
        int verticalFactor = getPagedOrientationHandler().getSecondaryTranslationDirectionFactor();

        ResourceProvider rp = DynamicResource.provider(mContainer);
        SpringProperty sp = new SpringProperty(SpringProperty.FLAG_CAN_SPRING_ON_START)
                .setDampingRatio(rp.getFloat(R.dimen.dismiss_task_trans_y_damping_ratio))
                .setStiffness(rp.getFloat(R.dimen.dismiss_task_trans_y_stiffness));

        anim.add(ObjectAnimator.ofFloat(taskView, secondaryViewTranslate,
                verticalFactor * secondaryTaskDimension * 2).setDuration(duration), LINEAR, sp);

        if (taskView.isRunningTask()) {
            anim.addOnFrameCallback(() -> {
                if (!mEnableDrawingLiveTile) return;
                runActionOnRemoteHandles(
                        remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                                .taskSecondaryTranslation.value = getPagedOrientationHandler()
                                .getSecondaryValue(taskView.getTranslationX(),
                                        taskView.getTranslationY()
                                ));
                redrawLiveTile();
            });
        }
    }

    /**
     * Places an {@link FloatingTaskView} on top of the thumbnail for {@link #mSplitHiddenTaskView}
     * and then animates it into the split position that was desired
     */
    private void createInitialSplitSelectAnimation(PendingAnimation anim) {
        getPagedOrientationHandler().getInitialSplitPlaceholderBounds(mSplitPlaceholderSize,
                mSplitPlaceholderInset, mContainer.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), mTempRect);
        SplitAnimationTimings timings =
                AnimUtils.getDeviceOverviewToSplitTimings(mContainer.getDeviceProfile().isTablet);

        RectF startingTaskRect = new RectF();
        safeRemoveDragLayerView(mSplitSelectStateController.getFirstFloatingTaskView());
        SplitAnimInitProps splitAnimInitProps =
                mSplitSelectStateController.getSplitAnimationController().getFirstAnimInitViews(
                        () -> mSplitHiddenTaskView, () -> mSplitSelectSource);
        if (mSplitSelectStateController.isAnimateCurrentTaskDismissal()) {
            // Create the split select animation from Overview
            mSplitHiddenTaskView.setThumbnailVisibility(INVISIBLE,
                    mSplitSelectStateController.getInitialTaskId());
            anim.setViewAlpha(splitAnimInitProps.getIconView(), 0, clampToProgress(LINEAR,
                    timings.getIconFadeStartOffset(),
                    timings.getIconFadeEndOffset()));
        }

        FloatingTaskView firstFloatingTaskView = FloatingTaskView.getFloatingTaskView(mContainer,
                splitAnimInitProps.getOriginalView(),
                splitAnimInitProps.getOriginalBitmap(),
                splitAnimInitProps.getIconDrawable(), startingTaskRect);
        firstFloatingTaskView.setAlpha(1);
        firstFloatingTaskView.addStagingAnimation(anim, startingTaskRect, mTempRect,
                splitAnimInitProps.getFadeWithThumbnail(), splitAnimInitProps.isStagedTask());
        mSplitSelectStateController.setFirstFloatingTaskView(firstFloatingTaskView);

        // Allow user to click staged app to launch into fullscreen
        firstFloatingTaskView.setOnClickListener(view ->
                mSplitSelectStateController.getSplitAnimationController().
                        playAnimPlaceholderToFullscreen(mContainer, view,
                                Optional.of(() -> resetFromSplitSelectionState())));

        // SplitInstructionsView: animate in
        safeRemoveDragLayerView(mSplitSelectStateController.getSplitInstructionsView());
        SplitInstructionsView splitInstructionsView =
                SplitInstructionsView.getSplitInstructionsView(mContainer);
        splitInstructionsView.setAlpha(0);
        anim.setViewAlpha(splitInstructionsView, 1, clampToProgress(LINEAR,
                timings.getInstructionsContainerFadeInStartOffset(),
                timings.getInstructionsContainerFadeInEndOffset()));
        anim.addFloat(splitInstructionsView, splitInstructionsView.UNFOLD, 0.1f, 1,
                clampToProgress(EMPHASIZED_DECELERATE,
                        timings.getInstructionsUnfoldStartOffset(),
                        timings.getInstructionsUnfoldEndOffset()));
        mSplitSelectStateController.setSplitInstructionsView(splitInstructionsView);

        InteractionJankMonitorWrapper.begin(this, Cuj.CUJ_SPLIT_SCREEN_ENTER,
                "First tile selected");
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (mSplitHiddenTaskView == getRunningTaskView()) {
                    finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                            null /* onFinishComplete */);
                } else {
                    switchToScreenshot(
                            () -> finishRecentsAnimation(true /* toRecents */,
                                    false /* shouldPip */, null /* onFinishComplete */));
                }
            }
        });
        anim.addEndListener(success -> {
            if (success) {
                InteractionJankMonitorWrapper.end(Cuj.CUJ_SPLIT_SCREEN_ENTER);
            } else {
                // If transition to split select was interrupted, clean up to prevent glitches
                if (FeatureFlags.enableSplitContextually()) {
                    mSplitSelectStateController.resetState();
                } else {
                    resetFromSplitSelectionState();
                }
                InteractionJankMonitorWrapper.cancel(Cuj.CUJ_SPLIT_SCREEN_ENTER);
            }

            updateCurrentTaskActionsVisibility();
        });
    }

    /**
     * Creates a {@link PendingAnimation} for dismissing the specified {@link TaskView}.
     * @param dismissedTaskView the {@link TaskView} to be dismissed
     * @param animateTaskView whether the {@link TaskView} to be dismissed should be animated
     * @param shouldRemoveTask whether the associated {@link Task} should be removed from
     *                         ActivityManager after dismissal
     * @param duration duration of the animation
     * @param dismissingForSplitSelection task dismiss animation is used for entering split
     *                                    selection state from app icon
     */
    public void createTaskDismissAnimation(PendingAnimation anim, TaskView dismissedTaskView,
            boolean animateTaskView, boolean shouldRemoveTask, long duration,
            boolean dismissingForSplitSelection) {
        if (mPendingAnimation != null) {
            mPendingAnimation.createPlaybackController().dispatchOnCancel().dispatchOnEnd();
        }

        int count = getPageCount();
        if (count == 0) {
            return;
        }

        boolean showAsGrid = showAsGrid();
        int taskCount = getTaskViewCount();
        int dismissedIndex = indexOfChild(dismissedTaskView);
        int dismissedTaskViewId = dismissedTaskView.getTaskViewId();

        // Grid specific properties.
        boolean isFocusedTaskDismissed = false;
        boolean isStagingFocusedTask = false;
        TaskView nextFocusedTaskView = null;
        boolean nextFocusedTaskFromTop = false;
        float dismissedTaskWidth = 0;
        float nextFocusedTaskWidth = 0;

        // Non-grid specific properties.
        int[] oldScroll = new int[count];
        int[] newScroll = new int[count];
        int scrollDiffPerPage = 0;
        boolean needsCurveUpdates = false;

        if (showAsGrid) {
            dismissedTaskWidth = dismissedTaskView.getLayoutParams().width + mPageSpacing;
            isFocusedTaskDismissed = dismissedTaskViewId == mFocusedTaskViewId;
            if (isFocusedTaskDismissed) {
                if (isSplitSelectionActive()) {
                    isStagingFocusedTask = true;
                } else {
                    nextFocusedTaskFromTop =
                            mTopRowIdSet.size() > 0 && mTopRowIdSet.size() >= (taskCount - 1) / 2f;
                    // Pick the next focused task from the preferred row.
                    for (int i = 0; i < taskCount; i++) {
                        TaskView taskView = requireTaskViewAt(i);
                        if (taskView == dismissedTaskView) {
                            continue;
                        }
                        boolean isTopRow = mTopRowIdSet.contains(taskView.getTaskViewId());
                        if ((nextFocusedTaskFromTop && isTopRow
                                || (!nextFocusedTaskFromTop && !isTopRow))) {
                            nextFocusedTaskView = taskView;
                            break;
                        }
                    }
                    if (nextFocusedTaskView != null) {
                        nextFocusedTaskWidth =
                                nextFocusedTaskView.getLayoutParams().width + mPageSpacing;
                    }
                }
            }
        } else {
            getPageScrolls(oldScroll, false, SIMPLE_SCROLL_LOGIC);
            getPageScrolls(newScroll, false,
                    v -> v.getVisibility() != GONE && v != dismissedTaskView);
            if (count > 1) {
                scrollDiffPerPage = Math.abs(oldScroll[1] - oldScroll[0]);
            }
        }

        float dismissTranslationInterpolationEnd = 1;
        boolean closeGapBetweenClearAll = false;
        boolean isClearAllHidden = isClearAllHidden();
        boolean snapToLastTask = false;
        boolean isLeftRightSplit =
                mContainer.getDeviceProfile().isLeftRightSplit && isSplitSelectionActive();
        TaskView lastGridTaskView = showAsGrid ? getLastGridTaskView() : null;
        int currentPageScroll = getScrollForPage(mCurrentPage);
        int lastGridTaskScroll = getScrollForPage(indexOfChild(lastGridTaskView));
        boolean currentPageSnapsToEndOfGrid = currentPageScroll == lastGridTaskScroll;
        if (lastGridTaskView != null && lastGridTaskView.isVisibleToUser()) {
            // After dismissal, animate translation of the remaining tasks to fill any gap left
            // between the end of the grid and the clear all button. Only animate if the clear
            // all button is visible or would become visible after dismissal.
            float longGridRowWidthDiff = 0;

            int topGridRowSize = mTopRowIdSet.size();
            int bottomGridRowSize = taskCount - mTopRowIdSet.size()
                    - (enableGridOnlyOverview() ? 0 : 1);
            boolean topRowLonger = topGridRowSize > bottomGridRowSize;
            boolean bottomRowLonger = bottomGridRowSize > topGridRowSize;
            boolean dismissedTaskFromTop = mTopRowIdSet.contains(dismissedTaskViewId);
            boolean dismissedTaskFromBottom = !dismissedTaskFromTop && !isFocusedTaskDismissed;
            if (dismissedTaskFromTop || (isFocusedTaskDismissed && nextFocusedTaskFromTop)) {
                topGridRowSize--;
            }
            if (dismissedTaskFromBottom || (isFocusedTaskDismissed && !nextFocusedTaskFromTop)) {
                bottomGridRowSize--;
            }
            int longRowWidth = Math.max(topGridRowSize, bottomGridRowSize)
                    * (mLastComputedGridTaskSize.width() + mPageSpacing);
            if (!enableGridOnlyOverview() && !isStagingFocusedTask) {
                longRowWidth += mLastComputedTaskSize.width() + mPageSpacing;
            }

            float gapWidth = 0;
            if ((topRowLonger && dismissedTaskFromTop)
                    || (bottomRowLonger && dismissedTaskFromBottom)) {
                gapWidth = dismissedTaskWidth;
            } else if (nextFocusedTaskView != null
                    && ((topRowLonger && nextFocusedTaskFromTop)
                    || (bottomRowLonger && !nextFocusedTaskFromTop))) {
                gapWidth = nextFocusedTaskWidth;
            }
            if (gapWidth > 0) {
                if (mClearAllShortTotalWidthTranslation == 0) {
                    // Compensate the removed gap if we don't already have shortTotalCompensation,
                    // and adjust accordingly to the new shortTotalCompensation after dismiss.
                    int newClearAllShortTotalWidthTranslation = 0;
                    if (longRowWidth < mLastComputedGridSize.width()) {
                        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
                        newClearAllShortTotalWidthTranslation =
                                (mIsRtl
                                        ? mLastComputedTaskSize.right
                                        : deviceProfile.widthPx - mLastComputedTaskSize.left)
                                        - longRowWidth - deviceProfile.overviewGridSideMargin;
                    }
                    float gapCompensation = gapWidth - newClearAllShortTotalWidthTranslation;
                    longGridRowWidthDiff += mIsRtl ? -gapCompensation : gapCompensation;
                }
                if (isClearAllHidden) {
                    // If ClearAllButton isn't fully shown, snap to the last task.
                    snapToLastTask = true;
                }
            }
            if (isLeftRightSplit && !isStagingFocusedTask) {
                // LastTask's scroll is the minimum scroll in split select, if current scroll is
                // beyond that, we'll need to snap to last task instead.
                TaskView lastTask = getLastGridTaskView();
                if (lastTask != null) {
                    int primaryScroll = getPagedOrientationHandler().getPrimaryScroll(this);
                    int lastTaskScroll = getScrollForPage(indexOfChild(lastTask));
                    if ((mIsRtl && primaryScroll < lastTaskScroll)
                            || (!mIsRtl && primaryScroll > lastTaskScroll)) {
                        snapToLastTask = true;
                    }
                }
            }
            if (snapToLastTask) {
                longGridRowWidthDiff += getSnapToLastTaskScrollDiff();
            } else if (isLeftRightSplit && currentPageSnapsToEndOfGrid) {
                // Use last task as reference point for scroll diff and snapping calculation as it's
                // the only invariant point in landscape split screen.
                snapToLastTask = true;
            }

            // If we need to animate the grid to compensate the clear all gap, we split the second
            // half of the dismiss pending animation (in which the non-dismissed tasks slide into
            // place) in half again, making the first quarter the existing non-dismissal sliding
            // and the second quarter this new animation of gap filling. This is due to the fact
            // that PendingAnimation is a single animation, not a sequence of animations, so we
            // fake it using interpolation.
            if (longGridRowWidthDiff != 0) {
                closeGapBetweenClearAll = true;
                // Stagger the offsets of each additional task for a delayed animation. We use
                // half here as this animation is half of half of an animation (1/4th).
                float halfAdditionalDismissTranslationOffset =
                        (0.5f * ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET);
                dismissTranslationInterpolationEnd = Utilities.boundToRange(
                        END_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                + (taskCount - 1) * halfAdditionalDismissTranslationOffset,
                        END_DISMISS_TRANSLATION_INTERPOLATION_OFFSET, 1);
                for (int i = 0; i < taskCount; i++) {
                    TaskView taskView = requireTaskViewAt(i);
                    anim.setFloat(taskView, TaskView.GRID_END_TRANSLATION_X, longGridRowWidthDiff,
                            clampToProgress(LINEAR, dismissTranslationInterpolationEnd, 1));
                    dismissTranslationInterpolationEnd = Utilities.boundToRange(
                            dismissTranslationInterpolationEnd
                                    - halfAdditionalDismissTranslationOffset,
                            END_DISMISS_TRANSLATION_INTERPOLATION_OFFSET, 1);
                    if (mEnableDrawingLiveTile && taskView.isRunningTask()) {
                        anim.addOnFrameCallback(() -> {
                            runActionOnRemoteHandles(
                                    remoteTargetHandle ->
                                            remoteTargetHandle.getTaskViewSimulator()
                                                    .taskPrimaryTranslation.value =
                                                    TaskView.GRID_END_TRANSLATION_X.get(taskView));
                            redrawLiveTile();
                        });
                    }
                }

                // Change alpha of clear all if translating grid to hide it
                if (isClearAllHidden) {
                    anim.setFloat(mClearAllButton, DISMISS_ALPHA, 0, LINEAR);
                    anim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mClearAllButton.setDismissAlpha(1);
                        }
                    });
                }
            }
        }

        SplitAnimationTimings splitTimings =
                AnimUtils.getDeviceOverviewToSplitTimings(mContainer.getDeviceProfile().isTablet);

        int distanceFromDismissedTask = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child == dismissedTaskView) {
                if (animateTaskView) {
                    if (dismissingForSplitSelection) {
                        createInitialSplitSelectAnimation(anim);
                    } else {
                        addDismissedTaskAnimations(dismissedTaskView, duration, anim);
                    }
                }
            } else if (!showAsGrid) {
                // Compute scroll offsets from task dismissal for animation.
                // If we just take newScroll - oldScroll, everything to the right of dragged task
                // translates to the left. We need to offset this in some cases:
                // - In RTL, add page offset to all pages, since we want pages to move to the right
                // Additionally, add a page offset if:
                // - Current page is rightmost page (leftmost for RTL)
                // - Dragging an adjacent page on the left side (right side for RTL)
                int offset = mIsRtl ? scrollDiffPerPage : 0;
                if (mCurrentPage == dismissedIndex) {
                    int lastPage = taskCount - 1;
                    if (mCurrentPage == lastPage) {
                        offset += mIsRtl ? -scrollDiffPerPage : scrollDiffPerPage;
                    }
                } else {
                    // Dismissing an adjacent page.
                    int negativeAdjacent = mCurrentPage - 1; // (Right in RTL, left in LTR)
                    if (dismissedIndex == negativeAdjacent) {
                        offset += mIsRtl ? -scrollDiffPerPage : scrollDiffPerPage;
                    }
                }

                int scrollDiff = newScroll[i] - oldScroll[i] + offset;
                if (scrollDiff != 0) {
                    FloatProperty translationProperty = child instanceof TaskView
                            ? ((TaskView) child).getPrimaryDismissTranslationProperty()
                            : getPagedOrientationHandler().getPrimaryViewTranslate();

                    float additionalDismissDuration =
                            ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET * Math.abs(
                                    i - dismissedIndex);

                    // We are in non-grid layout.
                    // If dismissing for split select, use split timings.
                    // If not, use dismiss timings.
                    float animationStartProgress = isSplitSelectionActive()
                            ? Utilities.boundToRange(splitTimings.getGridSlideStartOffset(), 0f, 1f)
                            : Utilities.boundToRange(
                                    INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                            + additionalDismissDuration, 0f, 1f);

                    float animationEndProgress = isSplitSelectionActive()
                            ? Utilities.boundToRange(splitTimings.getGridSlideStartOffset()
                                            + splitTimings.getGridSlideDurationOffset(), 0f, 1f)
                            : 1f;

                    // Slide tiles in horizontally to fill dismissed area
                    anim.setFloat(child, translationProperty, scrollDiff,
                            clampToProgress(
                                    splitTimings.getGridSlidePrimaryInterpolator(),
                                    animationStartProgress,
                                    animationEndProgress
                            )
                    );

                    if (mEnableDrawingLiveTile && child instanceof TaskView
                            && ((TaskView) child).isRunningTask()) {
                        anim.addOnFrameCallback(() -> {
                            runActionOnRemoteHandles(
                                    remoteTargetHandle ->
                                            remoteTargetHandle.getTaskViewSimulator()
                                                    .taskPrimaryTranslation.value =
                                                    getPagedOrientationHandler().getPrimaryValue(
                                                            child.getTranslationX(),
                                                            child.getTranslationY()
                                                    ));
                            redrawLiveTile();
                        });
                    }
                    needsCurveUpdates = true;
                }
            } else if (child instanceof TaskView) {
                TaskView taskView = (TaskView) child;
                if (isFocusedTaskDismissed) {
                    if (nextFocusedTaskView != null &&
                            !isSameGridRow(taskView, nextFocusedTaskView)) {
                        continue;
                    }
                } else {
                    if (i < dismissedIndex || !isSameGridRow(taskView, dismissedTaskView)) {
                        continue;
                    }
                }
                // Animate task with index >= dismissed index and in the same row as the
                // dismissed index or next focused index. Offset successive task dismissal
                // durations for a staggered effect.
                distanceFromDismissedTask++;
                int staggerColumn =  isStagingFocusedTask
                        ? (int) Math.ceil(distanceFromDismissedTask / 2f)
                        : distanceFromDismissedTask;
                // Set timings based on if user is initiating splitscreen on the focused task,
                // or splitting/dismissing some other task.
                float animationStartProgress = isStagingFocusedTask
                        ? Utilities.boundToRange(
                                splitTimings.getGridSlideStartOffset()
                                        + (splitTimings.getGridSlideStaggerOffset()
                                        * staggerColumn),
                        0f,
                        dismissTranslationInterpolationEnd)
                        : Utilities.boundToRange(
                                INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                        + ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                        * staggerColumn, 0f, dismissTranslationInterpolationEnd);
                float animationEndProgress = isStagingFocusedTask
                        ? Utilities.boundToRange(
                                splitTimings.getGridSlideStartOffset()
                                        + (splitTimings.getGridSlideStaggerOffset() * staggerColumn)
                                        + splitTimings.getGridSlideDurationOffset(),
                        0f,
                        dismissTranslationInterpolationEnd)
                        : dismissTranslationInterpolationEnd;
                Interpolator dismissInterpolator = isStagingFocusedTask ? OVERSHOOT_0_75 : LINEAR;

                if (taskView == nextFocusedTaskView) {
                    // Enlarge the task to be focused next, and translate into focus position.
                    float scale = mTaskWidth / (float) mLastComputedGridTaskSize.width();
                    anim.setFloat(taskView, TaskView.DISMISS_SCALE, scale,
                            clampToProgress(LINEAR, animationStartProgress,
                                    dismissTranslationInterpolationEnd));
                    anim.setFloat(taskView, taskView.getPrimaryDismissTranslationProperty(),
                            mIsRtl ? dismissedTaskWidth : -dismissedTaskWidth,
                            clampToProgress(LINEAR, animationStartProgress,
                                    dismissTranslationInterpolationEnd));
                    float secondaryTranslation = -mTaskGridVerticalDiff;
                    if (!nextFocusedTaskFromTop) {
                        secondaryTranslation -= mTopBottomRowHeightDiff;
                    }
                    anim.setFloat(taskView, taskView.getSecondaryDismissTranslationProperty(),
                            secondaryTranslation, clampToProgress(LINEAR, animationStartProgress,
                                    dismissTranslationInterpolationEnd));
                    anim.add(taskView.getFocusTransitionScaleAndDimOutAnimator(),
                            clampToProgress(LINEAR, 0f, ANIMATION_DISMISS_PROGRESS_MIDPOINT));
                } else {
                    float primaryTranslation =
                            nextFocusedTaskView != null ? nextFocusedTaskWidth : dismissedTaskWidth;
                    if (isStagingFocusedTask) {
                        // Moves less if focused task is not in scroll position.
                        int focusedTaskScroll = getScrollForPage(dismissedIndex);
                        int primaryScroll = getPagedOrientationHandler().getPrimaryScroll(this);
                        int focusedTaskScrollDiff = primaryScroll - focusedTaskScroll;
                        primaryTranslation +=
                                mIsRtl ? focusedTaskScrollDiff : -focusedTaskScrollDiff;
                    }

                    anim.setFloat(taskView, taskView.getPrimaryDismissTranslationProperty(),
                            mIsRtl ? primaryTranslation : -primaryTranslation,
                            clampToProgress(dismissInterpolator, animationStartProgress,
                                    animationEndProgress));
                }
            }
        }

        if (needsCurveUpdates) {
            anim.addOnFrameCallback(this::updateCurveProperties);
        }

        // Add a tiny bit of translation Z, so that it draws on top of other views. This is relevant
        // (e.g.) when we dismiss a task by sliding it upward: if there is a row of icons above, we
        // want the dragged task to stay above all other views.
        if (animateTaskView) {
            dismissedTaskView.setTranslationZ(0.1f);
        }

        mPendingAnimation = anim;
        final TaskView finalNextFocusedTaskView = nextFocusedTaskView;
        final boolean finalCloseGapBetweenClearAll = closeGapBetweenClearAll;
        final boolean finalSnapToLastTask = snapToLastTask;
        final boolean finalIsFocusedTaskDismissed = isFocusedTaskDismissed;
        mPendingAnimation.addEndListener(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (mEnableDrawingLiveTile && dismissedTaskView.isRunningTask() && success) {
                    finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                            () -> onEnd(success));
                } else {
                    onEnd(success);
                }
            }

            @SuppressWarnings("WrongCall")
            private void onEnd(boolean success) {
                // Reset task translations as they may have updated via animations in
                // createTaskDismissAnimation
                resetTaskVisuals();

                if (success) {
                    if (shouldRemoveTask) {
                        if (dismissedTaskView.isRunningTask()) {
                            finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                                    () -> removeTaskInternal(dismissedTaskViewId));
                        } else {
                            removeTaskInternal(dismissedTaskViewId);
                        }
                        announceForAccessibility(
                                getResources().getString(R.string.task_view_closed));
                        mContainer.getStatsLogManager().logger()
                                .withItemInfo(dismissedTaskView.getFirstItemInfo())
                                .log(LAUNCHER_TASK_DISMISS_SWIPE_UP);
                    }

                    int pageToSnapTo = mCurrentPage;
                    mCurrentPageScrollDiff = 0;
                    int taskViewIdToSnapTo = -1;
                    if (showAsGrid) {
                        if (finalCloseGapBetweenClearAll) {
                            if (finalSnapToLastTask) {
                                // Last task will be determined after removing dismissed task.
                                pageToSnapTo = -1;
                            } else if (taskCount > 2) {
                                pageToSnapTo = indexOfChild(mClearAllButton);
                            } else if (isClearAllHidden) {
                                // Snap to focused task if clear all is hidden.
                                pageToSnapTo = 0;
                            }
                        } else {
                            // Get the id of the task view we will snap to based on the current
                            // page's relative position as the order of indices change over time due
                            // to dismissals.
                            TaskView snappedTaskView = getTaskViewAt(mCurrentPage);
                            boolean calculateScrollDiff = true;
                            if (snappedTaskView != null && !finalSnapToLastTask) {
                                if (snappedTaskView.getTaskViewId() == mFocusedTaskViewId) {
                                    if (finalNextFocusedTaskView != null) {
                                        taskViewIdToSnapTo =
                                                finalNextFocusedTaskView.getTaskViewId();
                                    } else if (dismissedTaskViewId != mFocusedTaskViewId) {
                                        taskViewIdToSnapTo = mFocusedTaskViewId;
                                    } else {
                                        // Won't focus next task in split select, so snap to the
                                        // first task.
                                        pageToSnapTo = 0;
                                        calculateScrollDiff = false;
                                    }
                                } else {
                                    int snappedTaskViewId = snappedTaskView.getTaskViewId();
                                    boolean isSnappedTaskInTopRow = mTopRowIdSet.contains(
                                            snappedTaskViewId);
                                    IntArray taskViewIdArray =
                                            isSnappedTaskInTopRow ? getTopRowIdArray()
                                                    : getBottomRowIdArray();
                                    int snappedIndex = taskViewIdArray.indexOf(snappedTaskViewId);
                                    taskViewIdArray.removeValue(dismissedTaskViewId);
                                    if (finalNextFocusedTaskView != null) {
                                        taskViewIdArray.removeValue(
                                                finalNextFocusedTaskView.getTaskViewId());
                                    }
                                    if (snappedIndex >= 0
                                            && snappedIndex < taskViewIdArray.size()) {
                                        taskViewIdToSnapTo = taskViewIdArray.get(snappedIndex);
                                    } else if (snappedIndex == taskViewIdArray.size()) {
                                        // If the snapped task is the last item from the
                                        // dismissed row,
                                        // snap to the same column in the other grid row
                                        IntArray inverseRowTaskViewIdArray =
                                                isSnappedTaskInTopRow ? getBottomRowIdArray()
                                                        : getTopRowIdArray();
                                        if (snappedIndex < inverseRowTaskViewIdArray.size()) {
                                            taskViewIdToSnapTo = inverseRowTaskViewIdArray.get(
                                                    snappedIndex);
                                        }
                                    }
                                }
                            }

                            if (calculateScrollDiff) {
                                int primaryScroll = getPagedOrientationHandler().getPrimaryScroll(
                                        RecentsView.this);
                                int currentPageScroll = getScrollForPage(mCurrentPage);
                                mCurrentPageScrollDiff = primaryScroll - currentPageScroll;
                            }
                        }
                    } else if (dismissedIndex < pageToSnapTo || pageToSnapTo == taskCount - 1) {
                        pageToSnapTo--;
                    }
                    boolean isHomeTaskDismissed = dismissedTaskView == getHomeTaskView();
                    removeViewInLayout(dismissedTaskView);
                    mTopRowIdSet.remove(dismissedTaskViewId);

                    if (taskCount == 1) {
                        removeViewInLayout(mClearAllButton);
                        if (isHomeTaskDismissed) {
                            updateEmptyMessage();
                        } else if (!mSplitSelectStateController.isSplitSelectActive()) {
                            startHome();
                        }
                    } else {
                        // Update focus task and its size.
                        if (finalIsFocusedTaskDismissed && finalNextFocusedTaskView != null) {
                            mFocusedTaskViewId = enableGridOnlyOverview()
                                    ? INVALID_TASK_ID
                                    : finalNextFocusedTaskView.getTaskViewId();
                            mTopRowIdSet.remove(mFocusedTaskViewId);
                            finalNextFocusedTaskView.animateIconScaleAndDimIntoView();
                        }
                        updateTaskSize(/*isTaskDismissal=*/ true);
                        updateChildTaskOrientations();
                        // Update scroll and snap to page.
                        updateScrollSynchronously();

                        if (showAsGrid) {
                            // Rebalance tasks in the grid
                            int highestVisibleTaskIndex = getHighestVisibleTaskIndex();
                            if (highestVisibleTaskIndex < Integer.MAX_VALUE) {
                                TaskView taskView = requireTaskViewAt(highestVisibleTaskIndex);

                                boolean shouldRebalance;
                                int screenStart = getPagedOrientationHandler().getPrimaryScroll(
                                        RecentsView.this);
                                int taskStart = getPagedOrientationHandler().getChildStart(taskView)
                                        + (int) taskView.getOffsetAdjustment(/*gridEnabled=*/ true);

                                // Rebalance only if there is a maximum gap between the task and the
                                // screen's edge; this ensures that rebalanced tasks are outside the
                                // visible screen.
                                if (mIsRtl) {
                                    shouldRebalance = taskStart <= screenStart + mPageSpacing;
                                } else {
                                    int screenEnd = screenStart
                                            + getPagedOrientationHandler().getMeasuredSize(
                                            RecentsView.this);
                                    int taskSize = (int) (
                                            getPagedOrientationHandler().getMeasuredSize(
                                            taskView) * taskView
                                            .getSizeAdjustment(/*fullscreenEnabled=*/false));
                                    int taskEnd = taskStart + taskSize;

                                    shouldRebalance = taskEnd >= screenEnd - mPageSpacing;
                                }

                                if (shouldRebalance) {
                                    updateGridProperties(/*isTaskDismissal=*/ true,
                                            highestVisibleTaskIndex);
                                    updateScrollSynchronously();
                                }
                            }

                            IntArray topRowIdArray = getTopRowIdArray();
                            IntArray bottomRowIdArray = getBottomRowIdArray();
                            if (finalSnapToLastTask) {
                                // If snapping to last task, find the last task after dismissal.
                                pageToSnapTo = indexOfChild(
                                        getLastGridTaskView(topRowIdArray, bottomRowIdArray));
                            } else if (taskViewIdToSnapTo != -1) {
                                // If snapping to another page due to indices rearranging, find
                                // the new index after dismissal & rearrange using the task view id.
                                pageToSnapTo = indexOfChild(
                                        getTaskViewFromTaskViewId(taskViewIdToSnapTo));
                                if (!currentPageSnapsToEndOfGrid) {
                                    // If it wasn't snapped to one of the last pages, but is now
                                    // snapped to last pages, we'll need to compensate for the
                                    // offset from the page's scroll to its visual position.
                                    mCurrentPageScrollDiff += getOffsetFromScrollPosition(
                                            pageToSnapTo, topRowIdArray, bottomRowIdArray);
                                }
                            }
                        }
                        pageBeginTransition();
                        setCurrentPage(pageToSnapTo);
                        // Update various scroll-dependent UI.
                        dispatchScrollChanged();
                        updateActionsViewFocusedScroll();
                        if (isClearAllHidden() && !mContainer.getDeviceProfile().isTablet) {
                            mActionsView.updateDisabledFlags(OverviewActionsView.DISABLED_SCROLLING,
                                    false);
                        }
                    }
                }
                updateCurrentTaskActionsVisibility();
                onDismissAnimationEnds();
                mPendingAnimation = null;
            }
        });
    }

    /**
     * Hides all overview actions if user is halfway through split selection, shows otherwise.
     * We only show split option if:
     * * Focused view is a single app
     * * Device is large screen
     */
    private void updateCurrentTaskActionsVisibility() {
        TaskView taskView = getCurrentPageTaskView();
        boolean isCurrentSplit = taskView instanceof GroupedTaskView;
        GroupedTaskView groupedTaskView = isCurrentSplit ? (GroupedTaskView) taskView : null;
        // Update flags to see if entire actions bar should be hidden.
        if (!FeatureFlags.enableAppPairs()) {
            mActionsView.updateHiddenFlags(HIDDEN_SPLIT_SCREEN, isCurrentSplit);
        }
        mActionsView.updateHiddenFlags(HIDDEN_SPLIT_SELECT_ACTIVE, isSplitSelectionActive());
        // Update flags to see if actions bar should show buttons for a single task or a pair of
        // tasks.
        boolean canSaveAppPair = isCurrentSplit && supportsAppPairs() &&
                getSplitSelectController().getAppPairsController().canSaveAppPair(groupedTaskView);
        mActionsView.updateForGroupedTask(isCurrentSplit, canSaveAppPair);

        boolean isCurrentDesktop = taskView instanceof DesktopTaskView;
        mActionsView.updateHiddenFlags(HIDDEN_DESKTOP, isCurrentDesktop);
    }

    /** Returns if app pairs are supported in this launcher. Overridden in subclasses. */
    public boolean supportsAppPairs() {
        return true;
    }

    /**
     * Returns all the tasks in the top row, without the focused task
     */
    private IntArray getTopRowIdArray() {
        if (mTopRowIdSet.isEmpty()) {
            return new IntArray(0);
        }
        IntArray topArray = new IntArray(mTopRowIdSet.size());
        int taskViewCount = getTaskViewCount();
        for (int i = 0; i < taskViewCount; i++) {
            int taskViewId = requireTaskViewAt(i).getTaskViewId();
            if (mTopRowIdSet.contains(taskViewId)) {
                topArray.add(taskViewId);
            }
        }
        return topArray;
    }

    /**
     * Returns all the tasks in the bottom row, without the focused task
     */
    private IntArray getBottomRowIdArray() {
        int bottomRowIdArraySize = getBottomRowTaskCountForTablet();
        if (bottomRowIdArraySize <= 0) {
            return new IntArray(0);
        }
        IntArray bottomArray = new IntArray(bottomRowIdArraySize);
        int taskViewCount = getTaskViewCount();
        for (int i = 0; i < taskViewCount; i++) {
            int taskViewId = requireTaskViewAt(i).getTaskViewId();
            if (!mTopRowIdSet.contains(taskViewId) && taskViewId != mFocusedTaskViewId) {
                bottomArray.add(taskViewId);
            }
        }
        return bottomArray;
    }

    /**
     * Iterate the grid by columns instead of by TaskView index, starting after the focused task and
     * up to the last balanced column.
     *
     * @return the highest visible TaskView index between both rows
     */
    private int getHighestVisibleTaskIndex() {
        if (mTopRowIdSet.isEmpty()) return Integer.MAX_VALUE; // return earlier

        int lastVisibleIndex = Integer.MAX_VALUE;
        IntArray topRowIdArray = getTopRowIdArray();
        IntArray bottomRowIdArray = getBottomRowIdArray();
        int balancedColumns = Math.min(bottomRowIdArray.size(), topRowIdArray.size());

        for (int i = 0; i < balancedColumns; i++) {
            TaskView topTask = getTaskViewFromTaskViewId(topRowIdArray.get(i));

            if (isTaskViewVisible(topTask)) {
                TaskView bottomTask = getTaskViewFromTaskViewId(bottomRowIdArray.get(i));
                lastVisibleIndex = Math.max(indexOfChild(topTask), indexOfChild(bottomTask));
            } else if (lastVisibleIndex < Integer.MAX_VALUE) {
                break;
            }
        }

        return lastVisibleIndex;
    }

    private void removeTaskInternal(int dismissedTaskViewId) {
        int[] taskIds = getTaskIdsForTaskViewId(dismissedTaskViewId);
        UI_HELPER_EXECUTOR.getHandler().post(
                () -> {
                    for (int taskId : taskIds) {
                        if (taskId != -1) {
                            ActivityManagerWrapper.getInstance().removeTask(taskId);
                        }
                    }
                });
    }

    protected void onDismissAnimationEnds() {
        AccessibilityManagerCompat.sendTestProtocolEventToTest(getContext(),
                DISMISS_ANIMATION_ENDS_MESSAGE);
    }

    public PendingAnimation createAllTasksDismissAnimation(long duration) {
        if (FeatureFlags.IS_STUDIO_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
        }
        PendingAnimation anim = new PendingAnimation(duration);

        int count = getTaskViewCount();
        for (int i = 0; i < count; i++) {
            addDismissedTaskAnimations(requireTaskViewAt(i), duration, anim);
        }

        mPendingAnimation = anim;
        mPendingAnimation.addEndListener(isSuccess -> {
            if (isSuccess) {
                // Remove all the task views now
                finishRecentsAnimation(true /* toRecents */, false /* shouldPip */, () -> {
                    UI_HELPER_EXECUTOR.getHandler().post(
                            ActivityManagerWrapper.getInstance()::removeAllRecentTasks);
                    removeTasksViewsAndClearAllButton();
                    startHome();
                });
            }
            mPendingAnimation = null;
        });
        return anim;
    }

    private boolean snapToPageRelative(int delta, boolean cycle,
            @TaskGridNavHelper.TASK_NAV_DIRECTION int direction) {
        // Set next page if scroll animation is still running, otherwise cannot snap to the
        // next page on successive key presses. Setting the current page aborts the scroll.
        if (!mScroller.isFinished()) {
            setCurrentPage(getNextPage());
        }
        int pageCount = getPageCount();
        if (pageCount == 0) {
            return false;
        }
        final int newPageUnbound = getNextPageInternal(delta, direction, cycle);
        if (!cycle && (newPageUnbound < 0 || newPageUnbound > pageCount)) {
            return false;
        }
        snapToPage((newPageUnbound + pageCount) % pageCount);
        getChildAt(getNextPage()).requestFocus();
        return true;
    }

    private int getNextPageInternal(int delta, @TaskGridNavHelper.TASK_NAV_DIRECTION int direction,
            boolean cycle) {
        if (!showAsGrid()) {
            return getNextPage() + delta;
        }

        // Init task grid nav helper with top/bottom id arrays.
        TaskGridNavHelper taskGridNavHelper = new TaskGridNavHelper(getTopRowIdArray(),
                getBottomRowIdArray(), mFocusedTaskViewId);

        // Get current page's task view ID.
        TaskView currentPageTaskView = getCurrentPageTaskView();
        int currentPageTaskViewId;
        if (currentPageTaskView != null) {
            currentPageTaskViewId = currentPageTaskView.getTaskViewId();
        } else if (mCurrentPage == indexOfChild(mClearAllButton)) {
            currentPageTaskViewId = TaskGridNavHelper.CLEAR_ALL_PLACEHOLDER_ID;
        } else {
            return INVALID_PAGE;
        }

        int nextGridPage =
                taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle);
        return nextGridPage == TaskGridNavHelper.CLEAR_ALL_PLACEHOLDER_ID
                ? indexOfChild(mClearAllButton)
                : indexOfChild(getTaskViewFromTaskViewId(nextGridPage));
    }

    private void runDismissAnimation(PendingAnimation pendingAnim) {
        AnimatorPlaybackController controller = pendingAnim.createPlaybackController();
        controller.dispatchOnStart();
        controller.getAnimationPlayer().setInterpolator(FAST_OUT_SLOW_IN);
        controller.start();
    }

    @UiThread
    private void dismissTask(int taskId) {
        TaskView taskView = getTaskViewByTaskId(taskId);
        if (taskView == null) {
            return;
        }
        dismissTask(taskView, true /* animate */, false /* removeTask */);
    }

    public void dismissTask(TaskView taskView, boolean animateTaskView, boolean removeTask) {
        PendingAnimation pa = new PendingAnimation(DISMISS_TASK_DURATION);
        createTaskDismissAnimation(pa, taskView, animateTaskView, removeTask, DISMISS_TASK_DURATION,
                false /* dismissingForSplitSelection*/);
        runDismissAnimation(pa);
    }

    @SuppressWarnings("unused")
    private void dismissAllTasks(View view) {
        runDismissAnimation(createAllTasksDismissAnimation(DISMISS_TASK_DURATION));
        mContainer.getStatsLogManager().logger().log(LAUNCHER_TASK_CLEAR_ALL);
    }

    private void dismissCurrentTask() {
        TaskView taskView = getNextPageTaskView();
        if (taskView != null) {
            dismissTask(taskView, true /*animateTaskView*/, true /*removeTask*/);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isHandlingTouch() || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_TAB:
                return snapToPageRelative(event.isShiftPressed() ? -1 : 1, true /* cycle */,
                        DIRECTION_TAB);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return snapToPageRelative(mIsRtl ? -1 : 1, true /* cycle */, DIRECTION_RIGHT);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return snapToPageRelative(mIsRtl ? 1 : -1, true /* cycle */, DIRECTION_LEFT);
            case KeyEvent.KEYCODE_DPAD_UP:
                return snapToPageRelative(1, false /* cycle */, DIRECTION_UP);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return snapToPageRelative(1, false /* cycle */, DIRECTION_DOWN);
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

        TaskView runningTaskView = getRunningTaskView();
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView child = requireTaskViewAt(i);
            if (runningTaskView != null && mRunningTaskTileHidden && child == runningTaskView) {
                continue;
            }
            child.setStableAlpha(alpha);
        }
        mClearAllButton.setContentAlpha(mContentAlpha);
        int alphaInt = Math.round(alpha * 255);
        mEmptyMessagePaint.setAlpha(alphaInt);
        mEmptyIcon.setAlpha(alphaInt);
        mActionsView.getContentAlpha().updateValue(mContentAlpha);

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
        onOrientationChanged();
    }

    /**
     * Updates {@link RecentsOrientedState}'s cached RecentsView rotation.
     */
    public void updateRecentsRotation() {
        final int rotation = TraceHelper.allowIpcs(
                "RecentsView.updateRecentsRotation", () -> mContainer.getDisplay().getRotation());
        // Log real orientation change.
        if (mOrientationState.setRecentsRotation(rotation)) {
            logOrientationChanged();
        }
    }

    public void setLayoutRotation(int touchRotation, int displayRotation) {
        if (mOrientationState.update(touchRotation, displayRotation)) {
            updateOrientationHandler();
        }
    }

    public RecentsOrientedState getPagedViewOrientedState() {
        return mOrientationState;
    }

    public RecentsPagedOrientationHandler getPagedOrientationHandler() {
        return (RecentsPagedOrientationHandler) super.getPagedOrientationHandler();
    }

    @Nullable
    public TaskView getNextTaskView() {
        return getTaskViewAt(getRunningTaskIndex() + 1);
    }

    @Nullable
    public TaskView getCurrentPageTaskView() {
        return getTaskViewAt(getCurrentPage());
    }

    @Nullable
    public TaskView getNextPageTaskView() {
        return getTaskViewAt(getNextPage());
    }

    @Nullable
    public TaskView getTaskViewNearestToCenterOfScreen() {
        return getTaskViewAt(getPageNearestToCenterOfScreen());
    }

    /**
     * Returns null instead of indexOutOfBoundsError when index is not in range
     */
    @Nullable
    public TaskView getTaskViewAt(int index) {
        View child = getChildAt(index);
        return child instanceof TaskView ? (TaskView) child : null;
    }

    /**
     * A version of {@link #getTaskViewAt} when the caller is sure about the input index.
     */
    @NonNull
    private TaskView requireTaskViewAt(int index) {
        return Objects.requireNonNull(getTaskViewAt(index));
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
        // If we're going to a state without overview panel, avoid unnecessary onLayout that
        // cause TaskViews to re-arrange during animation to that state.
        if (!mOverviewStateEnabled && !mFirstLayout) {
            return;
        }

        mShowAsGridLastOnLayout = showAsGrid();

        super.onLayout(changed, left, top, right, bottom);

        updateEmptyStateUi(changed);

        setTaskModalness(mTaskModalness);
        mLastComputedTaskStartPushOutDistance = null;
        mLastComputedTaskEndPushOutDistance = null;
        updatePageOffsets();
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                        .setScroll(getScrollOffset()));
        setImportantForAccessibility(isModal() ? IMPORTANT_FOR_ACCESSIBILITY_NO
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void updatePivots() {
        if (mOverviewSelectEnabled) {
            if (enableGridOnlyOverview()) {
                getModalTaskSize(mTempRect);
                Rect selectedTaskPosition = getSelectedTaskBounds();
                Utilities.getPivotsForScalingRectToRect(mTempRect, selectedTaskPosition,
                        mTempPointF);
            } else {
                mTempPointF.set(mLastComputedTaskSize.centerX(), mLastComputedTaskSize.bottom);
            }
        } else {
            // Only update pivot when it is tablet and not in grid yet, so the pivot is correct
            // for non-current tasks when swiping up to overview
            if (enableGridOnlyOverview() && mContainer.getDeviceProfile().isTablet
                    && !mOverviewGridEnabled) {
                mTempRect.set(mLastComputedCarouselTaskSize);
            } else {
                mTempRect.set(mLastComputedTaskSize);
            }
            getPagedViewOrientedState().getFullScreenScaleAndPivot(mTempRect,
                    mContainer.getDeviceProfile(), mTempPointF);
        }
        setPivotX(mTempPointF.x);
        setPivotY(mTempPointF.y);
    }

    /**
     * Sets whether we should force-override the page offset mid-point to the current task, rather
     * than the running task, when updating page offsets.
     */
    public void setOffsetMidpointIndexOverride(int offsetMidpointIndexOverride) {
        if (!enableAdditionalHomeAnimations()) {
            return;
        }
        mOffsetMidpointIndexOverride = offsetMidpointIndexOverride;
        updatePageOffsets();
    }

    private void updatePageOffsets() {
        float offset = mAdjacentPageHorizontalOffset;
        float modalOffset = ACCELERATE_0_75.getInterpolation(mTaskModalness);
        int count = getChildCount();
        boolean showAsGrid = showAsGrid();

        TaskView runningTask = mRunningTaskViewId == INVALID_PAGE || !mRunningTaskTileHidden
                ? null : getRunningTaskView();
        int midpoint = mOffsetMidpointIndexOverride == INVALID_PAGE
                ? (runningTask == null ? INVALID_PAGE : indexOfChild(runningTask))
                : mOffsetMidpointIndexOverride;
        int modalMidpoint = getCurrentPage();
        boolean isModalGridWithoutFocusedTask =
                showAsGrid && enableGridOnlyOverview() && mTaskModalness > 0;
        if (isModalGridWithoutFocusedTask) {
            modalMidpoint = indexOfChild(mSelectedTask);
        }

        float midpointOffsetSize = 0;
        float leftOffsetSize = midpoint - 1 >= 0
                ? getHorizontalOffsetSize(midpoint - 1, midpoint, offset)
                : 0;
        float rightOffsetSize = midpoint + 1 < count
                ? getHorizontalOffsetSize(midpoint + 1, midpoint, offset)
                : 0;

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
            if (isModalGridWithoutFocusedTask) {
                gridOffsetSize = getHorizontalOffsetSize(i, modalMidpoint, modalOffset);
                gridOffsetSize = Math.abs(gridOffsetSize) * (i <= modalMidpoint ? 1 : -1);
            }
            float modalTranslation = i == modalMidpoint
                    ? modalMidpointOffsetSize
                    : showAsGrid
                            ? gridOffsetSize
                            : i < modalMidpoint ? modalLeftOffsetSize : modalRightOffsetSize;
            float totalTranslationX = translation + modalTranslation;
            View child = getChildAt(i);
            FloatProperty translationPropertyX = child instanceof TaskView
                    ? ((TaskView) child).getPrimaryTaskOffsetTranslationProperty()
                    : getPagedOrientationHandler().getPrimaryViewTranslate();
            translationPropertyX.set(child, totalTranslationX);
            if (mEnableDrawingLiveTile && i == getRunningTaskIndex()) {
                runActionOnRemoteHandles(
                        remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                                .taskPrimaryTranslation.value = totalTranslationX);
                redrawLiveTile();
            }

            if (showAsGrid && enableGridOnlyOverview() && child instanceof TaskView) {
                float totalTranslationY = getVerticalOffsetSize(i, modalOffset);
                FloatProperty translationPropertyY =
                        ((TaskView) child).getSecondaryTaskOffsetTranslationProperty();
                translationPropertyY.set(child, totalTranslationY);
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
            outRect.top += mContainer.getDeviceProfile().overviewTaskThumbnailTopMarginPx;

            mTempMatrix.reset();
            float persistentScale = taskView.getPersistentScale();
            mTempMatrix.postScale(persistentScale, persistentScale,
                    mIsRtl ? outRect.right : outRect.left, outRect.top);
            mTempMatrix.mapRect(outRect);
        }
        outRect.offset(getPagedOrientationHandler().getPrimaryValue(-midPointScroll, 0),
                getPagedOrientationHandler().getSecondaryValue(-midPointScroll, 0));
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
                    + getPagedOrientationHandler().getPrimaryScroll(this)
                    - getScrollForPage(mCurrentPage);

            getPersistentChildPosition(midpointIndex, midpointScroll, taskPosition);
            float midpointStart = getPagedOrientationHandler().getStart(taskPosition);

            getPersistentChildPosition(childIndex, midpointScroll, taskPosition);
            // Assume child does not overlap with midPointChild.
            isStartShift = getPagedOrientationHandler().getStart(taskPosition) < midpointStart;
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
            float desiredStart = -getPagedOrientationHandler().getPrimarySize(taskPosition);
            distanceToOffscreen = -getPagedOrientationHandler().getEnd(taskPosition);
            if (mLastComputedTaskStartPushOutDistance == null) {
                taskPosition.offsetTo(
                        getPagedOrientationHandler().getPrimaryValue(desiredStart, 0f),
                        getPagedOrientationHandler().getSecondaryValue(desiredStart, 0f));
                getMatrix().mapRect(taskPosition);
                mLastComputedTaskStartPushOutDistance = getPagedOrientationHandler().getEnd(
                        taskPosition) / getPagedOrientationHandler().getPrimaryScale(this);
            }
            distanceToOffscreen -= mLastComputedTaskStartPushOutDistance;
        } else {
            float desiredStart = getPagedOrientationHandler().getPrimarySize(this);
            distanceToOffscreen = desiredStart - getPagedOrientationHandler().getStart(
                    taskPosition);
            if (mLastComputedTaskEndPushOutDistance == null) {
                taskPosition.offsetTo(
                        getPagedOrientationHandler().getPrimaryValue(desiredStart, 0f),
                        getPagedOrientationHandler().getSecondaryValue(desiredStart, 0f));
                getMatrix().mapRect(taskPosition);
                mLastComputedTaskEndPushOutDistance = (getPagedOrientationHandler().getStart(
                        taskPosition) - desiredStart)
                        / getPagedOrientationHandler().getPrimaryScale(this);
            }
            distanceToOffscreen -= mLastComputedTaskEndPushOutDistance;
        }
        return distanceToOffscreen * offsetProgress;
    }

    /**
     * Computes the vertical distance to offset a given child such that it is completely offscreen.
     *
     * @param offsetProgress From 0 to 1 where 0 means no offset and 1 means offset offscreen.
     */
    private float getVerticalOffsetSize(int childIndex, float offsetProgress) {
        if (offsetProgress == 0 || !(showAsGrid() && enableGridOnlyOverview())
                || mSelectedTask == null) {
            // Don't bother calculating everything below if we won't offset vertically.
            return 0;
        }

        // First, get the position of the task relative to the top row.
        TaskView child = getTaskViewAt(childIndex);
        Rect taskPosition = getTaskBounds(child);

        boolean isSelectedTaskTopRow = mTopRowIdSet.contains(mSelectedTask.getTaskViewId());
        boolean isChildTopRow = mTopRowIdSet.contains(child.getTaskViewId());
        // Whether the task should be shifted to the top.
        boolean isTopShift = !isSelectedTaskTopRow && isChildTopRow;
        boolean isBottomShift = isSelectedTaskTopRow && !isChildTopRow;

        // Next, calculate the distance to move the task off screen at scale = 1.
        float distanceToOffscreen = 0;
        if (isTopShift) {
            distanceToOffscreen = -taskPosition.bottom;
        } else if (isBottomShift) {
            distanceToOffscreen = mContainer.getDeviceProfile().heightPx - taskPosition.top;
        }
        return distanceToOffscreen * offsetProgress;
    }

    protected void setTaskViewsResistanceTranslation(float translation) {
        mTaskViewsSecondaryTranslation = translation;
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView task = requireTaskViewAt(i);
            task.getTaskResistanceTranslationProperty().set(task, translation / getScaleY());
        }
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                        .recentsViewSecondaryTranslation.value = translation);
    }

    private void updateTaskViewsSnapshotRadius() {
        for (int i = 0; i < getTaskViewCount(); i++) {
            requireTaskViewAt(i).updateSnapshotRadius();
        }
    }

    protected void setTaskViewsPrimarySplitTranslation(float translation) {
        mTaskViewsPrimarySplitTranslation = translation;
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView task = requireTaskViewAt(i);
            task.getPrimarySplitTranslationProperty().set(task, translation);
        }
    }

    protected void setTaskViewsSecondarySplitTranslation(float translation) {
        mTaskViewsSecondarySplitTranslation = translation;
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = requireTaskViewAt(i);
            if (taskView == mSplitHiddenTaskView && !taskView.containsMultipleTasks()) {
                continue;
            }
            taskView.getSecondarySplitTranslationProperty().set(taskView, translation);
        }
    }

    /**
     * Resets the visuals when exit modal state.
     */
    public void resetModalVisuals() {
        if (mSelectedTask != null) {
            mSelectedTask.taskContainers.forEach(
                    taskContainer -> taskContainer.getOverlay().resetModalVisuals());
        }
    }

    /**
     * Primarily used by overview actions to initiate split from focused task, logs the source
     * of split invocation as such.
     */
    public void initiateSplitSelect(TaskView taskView) {
        int defaultSplitPosition = getPagedOrientationHandler()
                .getDefaultSplitPosition(mContainer.getDeviceProfile());
        initiateSplitSelect(taskView, defaultSplitPosition, LAUNCHER_OVERVIEW_ACTIONS_SPLIT);
    }

    /** TODO(b/266477929): Consolidate this call w/ the one below */
    public void initiateSplitSelect(TaskView taskView, @StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent) {
        mSplitHiddenTaskView = taskView;
        mSplitSelectStateController.setInitialTaskSelect(null /*intent*/, stagePosition,
                taskView.getFirstItemInfo(), splitEvent, taskView.getFirstTask().key.id);
        mSplitSelectStateController.setAnimateCurrentTaskDismissal(
                true /*animateCurrentTaskDismissal*/);
        mSplitHiddenTaskViewIndex = indexOfChild(taskView);
        updateDesktopTaskVisibility(false /* visible */);
    }

    /**
     * Called when staging a split from Home/AllApps/Overview (Taskbar),
     * using the icon long-press menu.
     * Attempts to initiate split with an existing taskView, if one exists
     */
    public void initiateSplitSelect(SplitSelectSource splitSelectSource) {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "enterSplitSelect");
        mSplitSelectSource = splitSelectSource;
        mSplitHiddenTaskView = getTaskViewByTaskId(splitSelectSource.alreadyRunningTaskId);
        mSplitHiddenTaskViewIndex = indexOfChild(mSplitHiddenTaskView);
        mSplitSelectStateController
                .setAnimateCurrentTaskDismissal(splitSelectSource.animateCurrentTaskDismissal);

        // Prevent dismissing whole task if we're only initiating from one of 2 tasks in split pair
        mSplitSelectStateController.setDismissingFromSplitPair(mSplitHiddenTaskView != null
                && mSplitHiddenTaskView instanceof GroupedTaskView);
        mSplitSelectStateController.setInitialTaskSelect(splitSelectSource.intent,
                splitSelectSource.position.stagePosition, splitSelectSource.itemInfo,
                splitSelectSource.splitEvent, splitSelectSource.alreadyRunningTaskId);
        updateDesktopTaskVisibility(false /* visible */);
    }

    private void updateDesktopTaskVisibility(boolean visible) {
        if (mDesktopTaskView != null) {
            mDesktopTaskView.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    /**
     * Modifies a PendingAnimation with the animations for entering split staging
     */
    public void createSplitSelectInitAnimation(PendingAnimation builder, int duration) {
        boolean isInitiatingSplitFromTaskView =
                mSplitSelectStateController.isAnimateCurrentTaskDismissal();
        boolean isInitiatingTaskViewSplitPair =
                mSplitSelectStateController.isDismissingFromSplitPair();
        if (isInitiatingSplitFromTaskView && isInitiatingTaskViewSplitPair
                && mSplitHiddenTaskView instanceof GroupedTaskView) {
            // Splitting from Overview for split pair task
            createInitialSplitSelectAnimation(builder);

            // Animate pair thumbnail into full thumbnail
            boolean primaryTaskSelected = mSplitHiddenTaskView.getTaskIds()[0]
                    == mSplitSelectStateController.getInitialTaskId();
            TaskContainer taskContainer = mSplitHiddenTaskView
                    .getTaskContainers().get(primaryTaskSelected ? 1 : 0);
            TaskThumbnailViewDeprecated thumbnail = taskContainer.getThumbnailViewDeprecated();
            mSplitSelectStateController.getSplitAnimationController()
                    .addInitialSplitFromPair(taskContainer, builder,
                            mContainer.getDeviceProfile(),
                            mSplitHiddenTaskView.getWidth(), mSplitHiddenTaskView.getHeight(),
                            primaryTaskSelected);
            builder.addOnFrameCallback(() ->{
                thumbnail.refreshSplashView();
                mSplitHiddenTaskView.updateSnapshotRadius();
            });
        } else if (isInitiatingSplitFromTaskView) {
            // Splitting from Overview for fullscreen task
            createTaskDismissAnimation(builder, mSplitHiddenTaskView, true, false, duration,
                    true /* dismissingForSplitSelection*/);
        } else {
            // Splitting from Home
            createInitialSplitSelectAnimation(builder);
        }
    }

    /**
     * Confirms the selection of the next split task. The extra data is passed through because the
     * user may be selecting a subtask in a group.
     *
     * @param containerTaskView If our second selected app is currently running in Recents, this is
     *                          the "container" TaskView from Recents. If we are starting a fresh
     *                          instance of the app from an Intent, this will be null.
     * @param task The Task corresponding to our second selected app. If we are starting a fresh
     *             instance of the app from an Intent, this will be null.
     * @param drawable The Drawable corresponding to our second selected app's icon.
     * @param secondView The View representing the current space on the screen where the second app
     *                   is (either the ThumbnailView or the tapped icon).
     * @param intent If we are launching a fresh instance of the app, this is the Intent for it. If
     *               the second app is already running in Recents, this will be null.
     * @param user If we are launching a fresh instance of the app, this is the UserHandle for it.
     *             If the second app is already running in Recents, this will be null.
     * @return true if waiting for confirmation of second app or if split animations are running,
     *          false otherwise
     */
    public boolean confirmSplitSelect(TaskView containerTaskView, Task task, Drawable drawable,
            View secondView, @Nullable Bitmap thumbnail, Intent intent, UserHandle user,
            ItemInfo itemInfo) {
        if (canLaunchFullscreenTask()) {
            return false;
        }
        if (mSplitSelectStateController.isBothSplitAppsConfirmed()) {
            Log.w(TAG, splitFailureMessage(
                    "confirmSplitSelect", "both apps have already been set"));
            return true;
        }
        // Second task is selected either as an already-running Task or an Intent
        if (task != null) {
            if (!task.isDockable) {
                // Task does not support split screen
                mSplitUnsupportedToast.show();
                Log.w(TAG, splitFailureMessage("confirmSplitSelect",
                        "selected Task (" + task.key.getPackageName()
                                + ") is not dockable / does not support splitscreen"));
                return true;
            }
            mSplitSelectStateController.setSecondTask(task, itemInfo);
        } else {
            mSplitSelectStateController.setSecondTask(intent, user, itemInfo);
        }

        RectF secondTaskStartingBounds = new RectF();
        Rect secondTaskEndingBounds = new Rect();
        // TODO(194414938) starting bounds seem slightly off, investigate
        Rect firstTaskStartingBounds = new Rect();
        Rect firstTaskEndingBounds = mTempRect;

        boolean isTablet = mContainer.getDeviceProfile().isTablet;
        SplitAnimationTimings timings = AnimUtils.getDeviceSplitToConfirmTimings(isTablet);
        PendingAnimation pendingAnimation = new PendingAnimation(timings.getDuration());

        int halfDividerSize = getResources()
                .getDimensionPixelSize(R.dimen.multi_window_task_divider_size) / 2;
        getPagedOrientationHandler().getFinalSplitPlaceholderBounds(halfDividerSize,
                mContainer.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), firstTaskEndingBounds,
                secondTaskEndingBounds);

        mSplitScrim = mSplitSelectStateController.getSplitAnimationController()
                .addScrimBehindAnim(pendingAnimation, mContainer, getContext());
        FloatingTaskView firstFloatingTaskView =
                mSplitSelectStateController.getFirstFloatingTaskView();
        firstFloatingTaskView.getBoundsOnScreen(firstTaskStartingBounds);
        firstFloatingTaskView.addConfirmAnimation(pendingAnimation,
                new RectF(firstTaskStartingBounds), firstTaskEndingBounds,
                false /* fadeWithThumbnail */, true /* isStagedTask */);

        safeRemoveDragLayerView(mSecondFloatingTaskView);

        mSecondFloatingTaskView = FloatingTaskView.getFloatingTaskView(mContainer, secondView,
                thumbnail, drawable, secondTaskStartingBounds);
        mSecondFloatingTaskView.setAlpha(1);
        mSecondFloatingTaskView.addConfirmAnimation(pendingAnimation, secondTaskStartingBounds,
                secondTaskEndingBounds, true /* fadeWithThumbnail */, false /* isStagedTask */);

        pendingAnimation.setViewAlpha(mSplitSelectStateController.getSplitInstructionsView(), 0,
                clampToProgress(LINEAR, timings.getInstructionsFadeStartOffset(),
                        timings.getInstructionsFadeEndOffset()));

        pendingAnimation.addEndListener(aBoolean -> {
            mSplitSelectStateController.launchSplitTasks(
                    aBoolean1 -> {
                        if (FeatureFlags.enableSplitContextually()) {
                            mSplitSelectStateController.resetState();
                        } else {
                            resetFromSplitSelectionState();
                        }
                        InteractionJankMonitorWrapper.end(Cuj.CUJ_SPLIT_SCREEN_ENTER);
                    });
        });

        mSecondSplitHiddenView = containerTaskView;
        if (mSecondSplitHiddenView != null) {
            mSecondSplitHiddenView.setThumbnailVisibility(INVISIBLE,
                    mSplitSelectStateController.getSecondTaskId());
        }

        InteractionJankMonitorWrapper.begin(this, Cuj.CUJ_SPLIT_SCREEN_ENTER,
                "Second tile selected");

        // Fade out all other views underneath placeholders
        ObjectAnimator tvFade = ObjectAnimator.ofFloat(this, RecentsView.CONTENT_ALPHA,1, 0);
        pendingAnimation.add(tvFade, DECELERATE_2, SpringProperty.DEFAULT);
        pendingAnimation.buildAnim().start();
        return true;
    }

    @SuppressLint("WrongCall")
    protected void resetFromSplitSelectionState() {
        if (mSplitSelectSource != null || mSplitHiddenTaskViewIndex != -1 ||
                FeatureFlags.enableSplitContextually()) {
            safeRemoveDragLayerView(mSplitSelectStateController.getFirstFloatingTaskView());
            safeRemoveDragLayerView(mSecondFloatingTaskView);
            safeRemoveDragLayerView(mSplitSelectStateController.getSplitInstructionsView());
            safeRemoveDragLayerView(mSplitScrim);
            mSecondFloatingTaskView = null;
            mSplitSelectSource = null;
            mSplitSelectStateController.getSplitAnimationController()
                    .removeSplitInstructionsView(mContainer);
        }

        if (mSecondSplitHiddenView != null) {
            mSecondSplitHiddenView.setThumbnailVisibility(VISIBLE, INVALID_TASK_ID);
            mSecondSplitHiddenView = null;
        }

        // We are leaving split selection state, so it is safe to reset thumbnail translations for
        // the next time split is invoked.
        setTaskViewsPrimarySplitTranslation(0);
        setTaskViewsSecondarySplitTranslation(0);

        if (!FeatureFlags.enableSplitContextually()) {
            // When flag is on, this method gets called from resetState() call below, let's avoid
            // infinite recursion today
            mSplitSelectStateController.resetState();
        }
        if (mSplitHiddenTaskViewIndex == -1) {
            return;
        }
        if (!mContainer.getDeviceProfile().isTablet) {
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
        mSplitHiddenTaskViewIndex = -1;
        if (mSplitHiddenTaskView != null) {
            mSplitHiddenTaskView.setThumbnailVisibility(VISIBLE, INVALID_TASK_ID);
            mSplitHiddenTaskView = null;
        }
        updateDesktopTaskVisibility(true /* visible */);
    }

    private void safeRemoveDragLayerView(@Nullable View viewToRemove) {
        if (viewToRemove != null) {
            mContainer.getDragLayer().removeView(viewToRemove);
        }
    }

    /**
     * Returns how much additional translation there should be for each of the child TaskViews.
     * Note that the translation can be its primary or secondary dimension.
     */
    public float getSplitSelectTranslation() {
        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        RecentsPagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        int splitPosition = getSplitSelectController().getActiveSplitStagePosition();
        int splitPlaceholderSize =
                mContainer.getResources().getDimensionPixelSize(R.dimen.split_placeholder_size);
        int direction = orientationHandler.getSplitTranslationDirectionFactor(
                splitPosition, deviceProfile);

        if (deviceProfile.isTablet && deviceProfile.isLeftRightSplit) {
            // Only shift TaskViews if there is not enough space on the side of
            // mLastComputedTaskSize to minimize motion.
            int sideSpace = mIsRtl
                    ? deviceProfile.widthPx - mLastComputedTaskSize.right
                    : mLastComputedTaskSize.left;
            int extraSpace = splitPlaceholderSize + mPageSpacing - sideSpace;
            if (extraSpace <= 0f) {
                return 0f;
            }

            return extraSpace * direction;
        }

        return splitPlaceholderSize * direction;
    }

    protected void onRotateInSplitSelectionState() {
        getPagedOrientationHandler().getInitialSplitPlaceholderBounds(mSplitPlaceholderSize,
                mSplitPlaceholderInset, mContainer.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), mTempRect);
        mTempRectF.set(mTempRect);
        FloatingTaskView firstFloatingTaskView =
                mSplitSelectStateController.getFirstFloatingTaskView();
        firstFloatingTaskView.updateOrientationHandler(getPagedOrientationHandler());
        firstFloatingTaskView.update(mTempRectF, /*progress=*/1f);

        RecentsPagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        Pair<FloatProperty<RecentsView>, FloatProperty<RecentsView>> taskViewsFloat =
                orientationHandler.getSplitSelectTaskOffset(
                        TASK_PRIMARY_SPLIT_TRANSLATION, TASK_SECONDARY_SPLIT_TRANSLATION,
                        mContainer.getDeviceProfile());
        taskViewsFloat.first.set(this, getSplitSelectTranslation());
        taskViewsFloat.second.set(this, 0f);

        if (mSplitSelectStateController.getSplitInstructionsView() != null) {
            mSplitSelectStateController.getSplitInstructionsView().ensureProperRotation();
        }
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
            final View taskView = requireTaskViewAt(0);
            requireTaskViewAt(count - 1).getHitRect(mTaskViewDeadZoneRect);
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
            // Offsets icon and text up so that the vertical center of screen (accounting for
            // insets) is between icon and text.
            int offset = (mEmptyIcon.getIntrinsicHeight() + mEmptyMessagePadding) / 2;

            canvas.save();
            canvas.translate(getScrollX() + (mInsets.left - mInsets.right) / 2f,
                    (mInsets.top - mInsets.bottom) / 2f - offset);
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
    @SuppressLint("Recycle")
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(TaskView tv) {
        AnimatorSet anim = new AnimatorSet();

        int taskIndex = indexOfChild(tv);
        int centerTaskIndex = getCurrentPage();

        float toScale = getMaxScaleForFullScreen();
        boolean showAsGrid = showAsGrid();
        boolean launchingCenterTask = showAsGrid
                ? tv.isFocusedTask() && isTaskViewFullyVisible(tv)
                : taskIndex == centerTaskIndex;
        if (launchingCenterTask) {
            anim.play(ObjectAnimator.ofFloat(this, RECENTS_SCALE_PROPERTY, toScale));
            anim.play(ObjectAnimator.ofFloat(this, FULLSCREEN_PROGRESS, 1));
        } else if (!showAsGrid) {
            // We are launching an adjacent task, so parallax the center and other adjacent task.
            float displacementX = tv.getWidth() * (toScale - 1f);
            float primaryTranslation = mIsRtl ? -displacementX : displacementX;
            anim.play(ObjectAnimator.ofFloat(getPageAt(centerTaskIndex),
                    getPagedOrientationHandler().getPrimaryViewTranslate(), primaryTranslation));
            int runningTaskIndex = getRunningTaskIndex();
            if (runningTaskIndex != -1 && runningTaskIndex != taskIndex
                    && getRemoteTargetHandles() != null) {
                for (RemoteTargetHandle remoteHandle : getRemoteTargetHandles()) {
                    anim.play(ObjectAnimator.ofFloat(
                            remoteHandle.getTaskViewSimulator().taskPrimaryTranslation,
                            AnimatedFloat.VALUE,
                            primaryTranslation));
                }
            }

            int otherAdjacentTaskIndex = centerTaskIndex + (centerTaskIndex - taskIndex);
            if (otherAdjacentTaskIndex >= 0 && otherAdjacentTaskIndex < getPageCount()) {
                PropertyValuesHolder[] properties = new PropertyValuesHolder[3];
                properties[0] = PropertyValuesHolder.ofFloat(
                        getPagedOrientationHandler().getPrimaryViewTranslate(), primaryTranslation);
                properties[1] = PropertyValuesHolder.ofFloat(View.SCALE_X, 1);
                properties[2] = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1);

                anim.play(ObjectAnimator.ofPropertyValuesHolder(getPageAt(otherAdjacentTaskIndex),
                        properties));
            }
        }
        anim.play(ObjectAnimator.ofFloat(this, TASK_THUMBNAIL_SPLASH_ALPHA, 0, 1));
        return anim;
    }

    /**
     * Returns the scale up required on the view, so that it coves the screen completely
     */
    public float getMaxScaleForFullScreen() {
        if (enableGridOnlyOverview() && mContainer.getDeviceProfile().isTablet
                && !mOverviewGridEnabled) {
            if (mLastComputedCarouselTaskSize.isEmpty()) {
                mSizeStrategy.calculateCarouselTaskSize(mContainer, mContainer.getDeviceProfile(),
                        mLastComputedCarouselTaskSize, getPagedOrientationHandler());
            }
            mTempRect.set(mLastComputedCarouselTaskSize);
        } else {
            if (mLastComputedTaskSize.isEmpty()) {
                getTaskSize(mLastComputedTaskSize);
            }
            mTempRect.set(mLastComputedTaskSize);
        }
        return getPagedViewOrientedState().getFullScreenScaleAndPivot(
                mTempRect, mContainer.getDeviceProfile(), mTempPointF);
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

        int targetSysUiFlags = tv.getFirstThumbnailViewDeprecated().getSysUiStatusNavFlags();
        final boolean[] passedOverviewThreshold = new boolean[] {false};
        ValueAnimator progressAnim = ValueAnimator.ofFloat(0, 1);
        progressAnim.addUpdateListener(animator -> {
            // Once we pass a certain threshold, update the sysui flags to match the target
            // tasks' flags
            if (animator.getAnimatedFraction() > UPDATE_SYSUI_FLAGS_THRESHOLD) {
                mContainer.getSystemUiController().updateUiState(
                        UI_STATE_FULLSCREEN_TASK, targetSysUiFlags);
            } else {
                mContainer.getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
            }

            // Passing the threshold from taskview to fullscreen app will vibrate
            final boolean passed = animator.getAnimatedFraction() >=
                    SUCCESS_TRANSITION_PROGRESS;
            if (passed != passedOverviewThreshold[0]) {
                passedOverviewThreshold[0] = passed;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                // Also update recents animation controller state if it is ongoing.
                if (mRecentsAnimationController != null) {
                    mRecentsAnimationController.setWillFinishToHome(!passed);
                }
            }
        });

        AnimatorSet anim = createAdjacentPageAnimForTaskLaunch(tv);

        DepthController depthController = getDepthController();
        if (depthController != null) {
            ObjectAnimator depthAnimator = ObjectAnimator.ofFloat(depthController.stateDepth,
                    MULTI_PROPERTY_VALUE, BACKGROUND_APP.getDepth(mContainer));
            anim.play(depthAnimator);
        }
        anim.play(ObjectAnimator.ofFloat(this, TASK_THUMBNAIL_SPLASH_ALPHA, 0f, 1f));

        anim.play(progressAnim);
        anim.setInterpolator(interpolator);

        mPendingAnimation = new PendingAnimation(duration);
        mPendingAnimation.add(anim);
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                        .addOverviewToAppAnim(mPendingAnimation, interpolator));
        mPendingAnimation.addOnFrameCallback(this::redrawLiveTile);
        mPendingAnimation.addEndListener(isSuccess -> {
            if (isSuccess) {
                if (tv instanceof GroupedTaskView && hasAllValidTaskIds(tv.getTaskIds())
                        && mRemoteTargetHandles != null) {
                    // TODO(b/194414938): make this part of the animations instead.
                    TaskViewUtils.createSplitAuxiliarySurfacesAnimator(
                            mRemoteTargetHandles[0].getTransformParams().getTargetSet().nonApps,
                            true /*shown*/, (dividerAnimator) -> {
                                dividerAnimator.start();
                                dividerAnimator.end();
                            });
                }
                if (tv.isRunningTask()) {
                    finishRecentsAnimation(false /* toRecents */, null);
                    onTaskLaunchAnimationEnd(true /* success */);
                } else {
                    tv.launchTask(this::onTaskLaunchAnimationEnd);
                }
                mContainer.getStatsLogManager().logger().withItemInfo(tv.getFirstItemInfo())
                        .log(LAUNCHER_TASK_LAUNCH_SWIPE_DOWN);
            } else {
                onTaskLaunchAnimationEnd(false);
            }
            mPendingAnimation = null;
        });
        return mPendingAnimation;
    }

    protected Unit onTaskLaunchAnimationEnd(boolean success) {
        if (success) {
            resetTaskVisuals();
        }
        return Unit.INSTANCE;
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        updateCurrentTaskActionsVisibility();
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
        runActionOnRemoteHandles(remoteTargetHandle -> {
            TransformParams params = remoteTargetHandle.getTransformParams();
            if (params.getTargetSet() != null) {
                remoteTargetHandle.getTaskViewSimulator().apply(params);
            }
        });
    }

    public RemoteTargetHandle[] getRemoteTargetHandles() {
        return mRemoteTargetHandles;
    }

    // TODO: To be removed in a follow up CL
    public void setRecentsAnimationTargets(RecentsAnimationController recentsAnimationController,
            RecentsAnimationTargets recentsAnimationTargets) {
        Log.d(TAG, "setRecentsAnimationTargets "
                + "- recentsAnimationController: " + recentsAnimationController
                + ", recentsAnimationTargets: " + recentsAnimationTargets);
        mRecentsAnimationController = recentsAnimationController;
        mSplitSelectStateController.setRecentsAnimationRunning(true);
        if (recentsAnimationTargets == null || recentsAnimationTargets.apps.length == 0) {
            return;
        }

        RemoteTargetGluer gluer;
        if (recentsAnimationTargets.hasDesktopTasks()) {
            gluer = new RemoteTargetGluer(getContext(), getSizeStrategy(), recentsAnimationTargets,
                    true /* forDesktop */);
            mRemoteTargetHandles = gluer.assignTargetsForDesktop(recentsAnimationTargets);
        } else {
            gluer = new RemoteTargetGluer(getContext(), getSizeStrategy(), recentsAnimationTargets,
                    false);
            mRemoteTargetHandles = gluer.assignTargetsForSplitScreen(recentsAnimationTargets);
        }
        mSplitBoundsConfig = gluer.getSplitBounds();
        // Add release check to the targets from the RemoteTargetGluer and not the targets
        // passed in because in the event we're in split screen, we use the passed in targets
        // to create new RemoteAnimationTargets in assignTargetsForSplitScreen(), and the
        // mSyncTransactionApplier doesn't get transferred over
        runActionOnRemoteHandles(remoteTargetHandle -> {
            final TransformParams params = remoteTargetHandle.getTransformParams();
            if (mSyncTransactionApplier != null) {
                params.setSyncTransactionApplier(mSyncTransactionApplier);
                params.getTargetSet().addReleaseCheck(mSyncTransactionApplier);
            }

            TaskViewSimulator tvs = remoteTargetHandle.getTaskViewSimulator();
            tvs.setOrientationState(mOrientationState);
            tvs.setDp(mContainer.getDeviceProfile());
            tvs.recentsViewScale.value = 1;
        });

        TaskView runningTaskView = getRunningTaskView();
        if (runningTaskView instanceof GroupedTaskView) {
            // We initially create a GroupedTaskView in showCurrentTask() before launcher even
            // receives the leashes for the remote apps, so the mSplitBoundsConfig that gets passed
            // in there is either null or outdated, so we need to update here as soon as we're
            // notified.
            ((GroupedTaskView) runningTaskView).updateSplitBoundsConfig(mSplitBoundsConfig);
        }
    }

    /** Helper to avoid writing some for-loops to iterate over {@link #mRemoteTargetHandles} */
    public void runActionOnRemoteHandles(Consumer<RemoteTargetHandle> consumer) {
        if (mRemoteTargetHandles == null) {
            return;
        }

        for (RemoteTargetHandle handle : mRemoteTargetHandles) {
            consumer.accept(handle);
        }
    }

    /**
     * Finish recents animation.
     */
    public void finishRecentsAnimation(boolean toRecents, @Nullable Runnable onFinishComplete) {
        finishRecentsAnimation(toRecents, true /* shouldPip */, onFinishComplete);
    }

    /**
     * NOTE: Whatever value gets passed through to the toRecents param may need to also be set on
     * {@link #mRecentsAnimationController#setWillFinishToHome}.
     */
    public void finishRecentsAnimation(boolean toRecents, boolean shouldPip,
            @Nullable Runnable onFinishComplete) {
        Log.d(TAG, "finishRecentsAnimation - mRecentsAnimationController: "
                + mRecentsAnimationController);
        // TODO(b/197232424#comment#10) Move this back into onRecentsAnimationComplete(). Maybe?
        cleanupRemoteTargets();

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
            systemUiProxy.setPipAnimationTypeToAlpha();
            systemUiProxy.setShelfHeight(true, mContainer.getDeviceProfile().hotseatBarSizePx);
            // Transaction to hide the task to avoid flicker for entering PiP from split-screen.
            // See also {@link AbsSwipeUpHandler#maybeFinishSwipeToHome}.
            PictureInPictureSurfaceTransaction tx =
                    new PictureInPictureSurfaceTransaction.Builder()
                            .setAlpha(0f)
                            .build();
            tx.setShouldDisableCanAffectSystemUiFlags(false);
            int[] taskIds = TopTaskTracker.INSTANCE.get(getContext()).getRunningSplitTaskIds();
            for (int taskId : taskIds) {
                mRecentsAnimationController.setFinishTaskTransaction(taskId,
                        tx, null /* overlay */);
            }
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
        Log.d(TAG, "onRecentsAnimationComplete "
                + "- mRecentsAnimationController: " + mRecentsAnimationController
                + ", mSideTaskLaunchCallback: " + mSideTaskLaunchCallback);
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
        mSplitSelectStateController.setRecentsAnimationRunning(false);
        executeSideTaskLaunchCallback();
    }

    public void setDisallowScrollToClearAll(boolean disallowScrollToClearAll) {
        if (mDisallowScrollToClearAll != disallowScrollToClearAll) {
            mDisallowScrollToClearAll = disallowScrollToClearAll;
            updateMinAndMaxScrollX();
        }
    }

    /**
     * Updates page scroll synchronously after measure and layout child views.
     */
    @SuppressLint("WrongCall")
    public void updateScrollSynchronously() {
        // onMeasure is needed to update child's measured width which is used in scroll calculation,
        // in case TaskView sizes has changed when being focused/unfocused.
        onMeasure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                makeMeasureSpec(getMeasuredHeight(), EXACTLY));
        onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());
        updateMinAndMaxScrollX();
    }

    @Override
    protected int getChildGap(int fromIndex, int toIndex) {
        int clearAllIndex = indexOfChild(mClearAllButton);
        return fromIndex == clearAllIndex || toIndex == clearAllIndex
                ? getClearAllExtraPageSpacing() : 0;
    }

    protected int getClearAllExtraPageSpacing() {
        return showAsGrid()
                ? Math.max(mContainer.getDeviceProfile().overviewGridSideMargin - mPageSpacing, 0)
                : 0;
    }

    @Override
    protected void updateMinAndMaxScrollX() {
        super.updateMinAndMaxScrollX();
        if (DEBUG) {
            Log.d(TAG, "updateMinAndMaxScrollX - mMinScroll: " + mMinScroll);
            Log.d(TAG, "updateMinAndMaxScrollX - mMaxScroll: " + mMaxScroll);
        }
    }

    @Override
    protected int computeMinScroll() {
        if (getTaskViewCount() <= 0) {
            return super.computeMinScroll();
        }

        return getScrollForPage(mIsRtl ? getLastViewIndex() : getFirstViewIndex());
    }

    @Override
    protected int computeMaxScroll() {
        if (getTaskViewCount() <= 0) {
            return super.computeMaxScroll();
        }

        return getScrollForPage(mIsRtl ? getFirstViewIndex() : getLastViewIndex());
    }

    private int getFirstViewIndex() {
        TaskView focusedTaskView = mShowAsGridLastOnLayout ? getFocusedTaskView() : null;
        return focusedTaskView != null ? indexOfChild(focusedTaskView) : 0;
    }

    private int getLastViewIndex() {
        if (!mDisallowScrollToClearAll) {
            return indexOfChild(mClearAllButton);
        }

        if (!mShowAsGridLastOnLayout) {
            return getTaskViewCount() - 1;
        }

        TaskView lastGridTaskView = getLastGridTaskView();
        if (lastGridTaskView != null) {
            return indexOfChild(lastGridTaskView);
        }

        // Returns focus task if there are no grid tasks.
        return indexOfChild(getFocusedTaskView());
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
            int clearAllWidthDiff = getPagedOrientationHandler().getPrimaryValue(mTaskWidth,
                    mTaskHeight) - getPagedOrientationHandler().getPrimarySize(mClearAllButton);
            mClearAllButton.setScrollOffsetPrimary(mIsRtl ? clearAllWidthDiff : -clearAllWidthDiff);
        }

        boolean pageScrollChanged = false;

        int clearAllIndex = indexOfChild(mClearAllButton);
        int clearAllScroll = 0;
        int clearAllWidth = getPagedOrientationHandler().getPrimarySize(mClearAllButton);
        if (clearAllIndex != -1 && clearAllIndex < outPageScrolls.length) {
            float scrollDiff = mClearAllButton.getScrollAdjustment(showAsFullscreen, showAsGrid);
            clearAllScroll = newPageScrolls[clearAllIndex] + (int) scrollDiff;
            if (outPageScrolls[clearAllIndex] != clearAllScroll) {
                pageScrollChanged = true;
                outPageScrolls[clearAllIndex] = clearAllScroll;
            }
        }

        final int taskCount = getTaskViewCount();
        int lastTaskScroll = getLastTaskScroll(clearAllScroll, clearAllWidth);
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            float scrollDiff = taskView.getScrollAdjustment(showAsGrid);
            int pageScroll = newPageScrolls[i] + Math.round(scrollDiff);
            if ((mIsRtl && pageScroll < lastTaskScroll)
                    || (!mIsRtl && pageScroll > lastTaskScroll)) {
                pageScroll = lastTaskScroll;
            }
            if (outPageScrolls[i] != pageScroll) {
                pageScrollChanged = true;
                outPageScrolls[i] = pageScroll;
            }
            if (DEBUG) {
                Log.d(TAG, "getPageScrolls - outPageScrolls[" + i + "]: " + outPageScrolls[i]);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "getPageScrolls - clearAllScroll: " + clearAllScroll);
        }
        return pageScrollChanged;
    }

    @Override
    protected int getChildOffset(int index) {
        int childOffset = super.getChildOffset(index);
        View child = getChildAt(index);
        if (child instanceof TaskView) {
            childOffset += ((TaskView) child).getOffsetAdjustment(showAsGrid());
        } else if (child instanceof ClearAllButton) {
            childOffset += ((ClearAllButton) child).getOffsetAdjustment(mOverviewFullscreenEnabled,
                    showAsGrid());
        }
        return childOffset;
    }

    @Override
    protected int getChildVisibleSize(int index) {
        final TaskView taskView = getTaskViewAt(index);
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
     * Returns how many pixels the running task is offset on the currently laid out dominant axis
     * specifically during a Keyboard task focus.
     */
    public int getScrollOffsetForKeyboardTaskFocus() {
        if (!isKeyboardTaskFocusPending()) {
            return getScrollOffset(getRunningTaskIndex());
        }
        return getPagedOrientationHandler().getPrimaryScroll(this)
                - getScrollForPage(mKeyboardTaskFocusIndex)
                + getScrollOffset(getRunningTaskIndex());
    }

    /**
     * Sets whether or not we should clamp the scroll offset.
     * This is used to avoid x-axis movement when swiping up transient taskbar.
     * Should only be set at the beginning and end of the gesture, otherwise a jump may occur.
     * @param clampScrollOffset When true, we clamp the scroll to 0 before the clamp threshold is
     *                          met.
     */
    public void setClampScrollOffset(boolean clampScrollOffset) {
        mShouldClampScrollOffset = clampScrollOffset;
    }

    /**
     * Returns how many pixels the page is offset on the currently laid out dominant axis.
     */
    public int getScrollOffset(int pageIndex) {
        int unclampedOffset = getUnclampedScrollOffset(pageIndex);
        if (!mShouldClampScrollOffset) {
            return unclampedOffset;
        }
        if (Math.abs(unclampedOffset) < mClampedScrollOffsetBound) {
            return 0;
        }
        return unclampedOffset
                - Math.round(Math.signum(unclampedOffset) * mClampedScrollOffsetBound);
    }

    /**
     * Returns how many pixels the page is offset on the currently laid out dominant axis.
     */
    private int getUnclampedScrollOffset(int pageIndex) {
        if (pageIndex == -1) {
            return 0;
        }
        // Don't dampen the scroll (due to overscroll) if the adjacent tasks are offscreen, so that
        // the page can move freely given there's no visual indication why it shouldn't.
        int overScrollShift = mAdjacentPageHorizontalOffset > 0
                        ? (int) Utilities.mapRange(
                                mAdjacentPageHorizontalOffset,
                                getOverScrollShift(),
                                getUndampedOverScrollShift())
                        : getOverScrollShift();
        return getScrollForPage(pageIndex) - getPagedOrientationHandler().getPrimaryScroll(this)
                + overScrollShift + getOffsetFromScrollPosition(pageIndex);
    }

    /**
     * Returns how many pixels the page is offset from its scroll position.
     */
    private int getOffsetFromScrollPosition(int pageIndex) {
        return getOffsetFromScrollPosition(pageIndex, getTopRowIdArray(), getBottomRowIdArray());
    }

    private int getOffsetFromScrollPosition(
            int pageIndex, IntArray topRowIdArray, IntArray bottomRowIdArray) {
        if (!showAsGrid()) {
            return 0;
        }

        TaskView taskView = getTaskViewAt(pageIndex);
        if (taskView == null) {
            return 0;
        }

        TaskView lastGridTaskView = getLastGridTaskView(topRowIdArray, bottomRowIdArray);
        if (lastGridTaskView == null) {
            return 0;
        }

        if (getScrollForPage(pageIndex) != getScrollForPage(indexOfChild(lastGridTaskView))) {
            return 0;
        }

        // Check distance from lastGridTaskView to taskView.
        int lastGridTaskViewPosition =
                getPositionInRow(lastGridTaskView, topRowIdArray, bottomRowIdArray);
        int taskViewPosition = getPositionInRow(taskView, topRowIdArray, bottomRowIdArray);
        int gridTaskSizeAndSpacing = mLastComputedGridTaskSize.width() + mPageSpacing;
        int positionDiff = gridTaskSizeAndSpacing * (lastGridTaskViewPosition - taskViewPosition);

        int taskEnd = getLastTaskEnd() + (mIsRtl ? positionDiff : -positionDiff);
        int normalTaskEnd = mIsRtl
                ? mLastComputedGridTaskSize.left
                : mLastComputedGridTaskSize.right;
        return taskEnd - normalTaskEnd;
    }

    private int getLastTaskEnd() {
        return mIsRtl
                ? mLastComputedGridSize.left + mPageSpacing + mClearAllShortTotalWidthTranslation
                : mLastComputedGridSize.right - mPageSpacing - mClearAllShortTotalWidthTranslation;
    }

    private int getPositionInRow(
            TaskView taskView, IntArray topRowIdArray, IntArray bottomRowIdArray) {
        int position = topRowIdArray.indexOf(taskView.getTaskViewId());
        return position != -1 ? position : bottomRowIdArray.indexOf(taskView.getTaskViewId());
    }

    /**
     * @return true if the task in on the top of the grid
     */
    public boolean isOnGridBottomRow(TaskView taskView) {
        return showAsGrid()
                && !mTopRowIdSet.contains(taskView.getTaskViewId())
                && taskView.getTaskViewId() != mFocusedTaskViewId;
    }

    public Consumer<MotionEvent> getEventDispatcher(float navbarRotation) {
        float degreesRotated;
        if (navbarRotation == 0) {
            degreesRotated = getPagedOrientationHandler().getDegreesRotated();
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
        TaskView focusedTaskView = getFocusedTaskView();
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            if (taskView == focusedTaskView) {
                continue;
            }
            taskView.setOverlayEnabled(mOverlayEnabled && isTaskViewFullyVisible(taskView));
        }
        // Focus task overlay should be enabled and refreshed at last
        if (focusedTaskView != null) {
            focusedTaskView.setOverlayEnabled(
                    mOverlayEnabled && isTaskViewFullyVisible(focusedTaskView));
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
            updateActionsViewFocusedScroll();
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
     * Update whether RecentsView is in select mode. Should be enabled before transitioning to
     * select mode, and only disabled after transitioning from select mode.
     */
    public void setOverviewSelectEnabled(boolean overviewSelectEnabled) {
        if (mOverviewSelectEnabled != overviewSelectEnabled) {
            mOverviewSelectEnabled = overviewSelectEnabled;
            updatePivots();
            if (!mOverviewSelectEnabled) {
                setSelectedTask(INVALID_TASK_ID);
            }
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

        switchToScreenshotInternal(onFinishRunnable);
    }

    private void switchToScreenshotInternal(Runnable onFinishRunnable) {
        TaskView taskView = getRunningTaskView();
        if (taskView == null) {
            onFinishRunnable.run();
            return;
        }

        setRunningTaskViewShowScreenshot(true);
        for (TaskContainer container : taskView.getTaskContainers()) {
            if (container == null) {
                continue;
            }

            ThumbnailData td =
                    mRecentsAnimationController.screenshotTask(container.getTask().key.id);
            TaskThumbnailViewDeprecated thumbnailView = container.getThumbnailViewDeprecated();
            if (td != null) {
                thumbnailView.setThumbnail(container.getTask(), td);
            } else {
                thumbnailView.refresh();
            }
        }
        ViewUtils.postFrameDrawn(taskView, onFinishRunnable);
    }

    /**
     * Switch the current running task view to static snapshot mode, using the
     * provided thumbnail data as the snapshot.
     * TODO(b/195609063) Consolidate this method w/ the one above, except this thumbnail data comes
     *  from gesture state, which is a larger change of it having to keep track of multiple tasks.
     *  OR. Maybe it doesn't need to pass in a thumbnail and we can use the exact same flow as above
     */
    public void switchToScreenshot(@Nullable HashMap<Integer, ThumbnailData> thumbnailDatas,
            Runnable onFinishRunnable) {
        final TaskView taskView = getRunningTaskView();
        if (taskView != null) {
            taskView.setShouldShowScreenshot(true);
            taskView.refreshThumbnails(thumbnailDatas);
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
        if (mSelectedTask != null) {
            mSelectedTask.setModalness(modalness);
        } else if (getCurrentPageTaskView() != null) {
            getCurrentPageTaskView().setModalness(modalness);
        }
        // Only show actions view when it's modal for in-place landscape mode.
        boolean inPlaceLandscape = !mOrientationState.isRecentsActivityRotationAllowed()
                && mOrientationState.getTouchRotation() != ROTATION_0;
        mActionsView.updateHiddenFlags(HIDDEN_NON_ZERO_ROTATION, modalness < 1 && inPlaceLandscape);
    }

    @Nullable
    protected DepthController getDepthController() {
        return null;
    }

    @Nullable
    protected DesktopRecentsTransitionController getDesktopRecentsController() {
        return mDesktopRecentsTransitionController;
    }

    /** Enables or disables modal state for RecentsView */
    public abstract void setModalStateEnabled(int taskId, boolean animate);

    public TaskOverlayFactory getTaskOverlayFactory() {
        return mTaskOverlayFactory;
    }

    public BaseContainerInterface getSizeStrategy() {
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

        mTintingAnimator = ObjectAnimator.ofFloat(this, COLOR_TINT,
                show ? FOREGROUND_SCRIM_TINT : 0f);
        mTintingAnimator.setAutoCancel(true);
        mTintingAnimator.start();
    }

    /** Tint the RecentsView and TaskViews in to simulate a scrim. */
    // TODO(b/187528071): Replace this tinting with a scrim on top of RecentsView
    private void setColorTint(float tintAmount) {
        mColorTint = tintAmount;

        for (int i = 0; i < getTaskViewCount(); i++) {
            requireTaskViewAt(i).setColorTint(mColorTint, mTintingColor);
        }

        Drawable scrimBg = mContainer.getScrimView().getBackground();
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
                && mSizeStrategy.stateFromGestureEndTarget(mCurrentGestureEndTarget)
                    .displayOverviewTasksAsGrid(mContainer.getDeviceProfile()));
    }

    private boolean showAsFullscreen() {
        return mOverviewFullscreenEnabled
                && mCurrentGestureEndTarget != GestureState.GestureEndTarget.RECENTS;
    }

    public void cleanupRemoteTargets() {
        Log.d(TAG, "cleanupRemoteTargets - mRemoteTargetHandles: " + Arrays.toString(
                mRemoteTargetHandles));
        mRemoteTargetHandles = null;
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

    /**
     * @return Shadow radius in pixel value for PiP window, which is updated via
     *         {@link #mIPipAnimationListener}
     */
    public int getPipShadowRadius() {
        return mPipShadowRadius;
    }

    @Override
    public boolean scrollLeft() {
        if (!showAsGrid()) {
            return super.scrollLeft();
        }

        int targetPage = getNextPage();
        if (targetPage >= 0) {
            // Find the next page that is not fully visible.
            TaskView taskView = getTaskViewAt(targetPage);
            while ((taskView == null || isTaskViewFullyVisible(taskView)) && targetPage - 1 >= 0) {
                taskView = getTaskViewAt(--targetPage);
            }
            // Target a scroll where targetPage is on left of screen but still fully visible.
            int normalTaskEnd = mIsRtl
                    ? mLastComputedGridTaskSize.left
                    : mLastComputedGridTaskSize.right;
            int targetScroll = getScrollForPage(targetPage) + normalTaskEnd - getLastTaskEnd();
            // Find a page that is close to targetScroll while not over it.
            while (targetPage - 1 >= 0
                    && (mIsRtl
                    ? getScrollForPage(targetPage - 1) < targetScroll
                    : getScrollForPage(targetPage - 1) > targetScroll)) {
                targetPage--;
            }
            snapToPage(targetPage);
            return true;
        }

        return mAllowOverScroll;
    }

    @Override
    public boolean scrollRight() {
        if (!showAsGrid()) {
            return super.scrollRight();
        }

        int targetPage = getNextPage();
        if (targetPage < getChildCount()) {
            // Find the next page that is not fully visible.
            TaskView taskView = getTaskViewAt(targetPage);
            while ((taskView != null && isTaskViewFullyVisible(taskView))
                    && targetPage + 1 < getChildCount()) {
                taskView = getTaskViewAt(++targetPage);
            }
            snapToPage(targetPage);
            return true;
        }
        return mAllowOverScroll;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        dispatchScrollChanged();
    }

    /**
     * Prepares this RecentsView to scroll properly for an upcoming child view focus request from
     * keyboard quick switching
     */
    public void setKeyboardTaskFocusIndex(int taskIndex) {
        mKeyboardTaskFocusIndex = taskIndex;
    }

    /** Returns whether this RecentsView will be scrolling to a child view for a focus request */
    public boolean isKeyboardTaskFocusPending() {
        return mKeyboardTaskFocusIndex != INVALID_PAGE;
    }

    private boolean isKeyboardTaskFocusPendingForChild(View child) {
        return isKeyboardTaskFocusPending() && mKeyboardTaskFocusIndex == indexOfChild(child);
    }

    @Override
    protected int getSnapAnimationDuration() {
        return isKeyboardTaskFocusPending()
                ? mKeyboardTaskFocusSnapAnimationDuration : super.getSnapAnimationDuration();
    }

    @Override
    protected void onVelocityValuesUpdated() {
        super.onVelocityValuesUpdated();
        mKeyboardTaskFocusSnapAnimationDuration =
                getResources().getInteger(R.integer.config_keyboardTaskFocusSnapAnimationDuration);
    }

    @Override
    protected boolean shouldHandleRequestChildFocus(View child) {
        // If we are already scrolling to a task view and we aren't focusing to this child from
        // keyboard quick switch, then the focus request has already been handled
        return mScroller.isFinished() || isKeyboardTaskFocusPendingForChild(child);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (isKeyboardTaskFocusPendingForChild(child)) {
            updateGridProperties();
            updateScrollSynchronously();
        }
        super.requestChildFocus(child, focused);
    }

    private void dispatchScrollChanged() {
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTaskViewSimulator().setScroll(getScrollOffset()));
        for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
            mScrollListeners.get(i).onScrollChanged();
        }
    }

    private static class PinnedStackAnimationListener<T extends RecentsViewContainer> extends
            IPipAnimationListener.Stub {
        @Nullable
        private T mActivity;
        @Nullable
        private RecentsView mRecentsView;

        public void setActivityAndRecentsView(@Nullable T activity,
                @Nullable RecentsView recentsView) {
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
        public void onPipResourceDimensionsChanged(int cornerRadius, int shadowRadius) {
            if (mRecentsView != null) {
                mRecentsView.mPipCornerRadius = cornerRadius;
                mRecentsView.mPipShadowRadius = shadowRadius;
            }
        }

        @Override
        public void onExpandPip() {
            MAIN_EXECUTOR.execute(() -> {
                if (mRecentsView == null
                        || mRecentsView.mSizeStrategy.getTaskbarController() == null) {
                    return;
                }
                // Hide the task bar when leaving PiP to prevent it from flickering once
                // the app settles in full-screen mode.
                mRecentsView.mSizeStrategy.getTaskbarController().onExpandPip();
            });
        }
    }

    /** Get the color used for foreground scrimming the RecentsView for sharing. */
    public static int getForegroundScrimDimColor(Context context) {
        return context.getColor(R.color.overview_foreground_scrim_color);
    }

    /** Get the RecentsAnimationController */
    @Nullable
    public RecentsAnimationController getRecentsAnimationController() {
        return mRecentsAnimationController;
    }

    @Nullable
    public SplitInstructionsView getSplitInstructionsView() {
        return mSplitSelectStateController.getSplitInstructionsView();
    }

    /** Update the current activity locus id to show the enabled state of Overview */
    public void updateLocusId() {
        String locusId = "Overview";

        if (mOverviewStateEnabled && mContainer.isStarted()) {
            locusId += "|ENABLED";
        } else {
            locusId += "|DISABLED";
        }

        final LocusId id = new LocusId(locusId);
        // Set locus context is a binder call, don't want it to happen during a transition
        UI_HELPER_EXECUTOR.post(() -> mContainer.setLocusContext(id, Bundle.EMPTY));
    }

    /**
     * Moves the provided task into desktop mode, and invoke {@code successCallback} if succeeded.
     */
    public void moveTaskToDesktop(TaskContainer taskContainer,
            DesktopModeTransitionSource transitionSource,
            Runnable successCallback) {
        if (!DesktopModeStatus.canEnterDesktopMode(mContext)) {
            return;
        }
        switchToScreenshot(() -> finishRecentsAnimation(/* toRecents= */true, /* shouldPip= */false,
                () -> moveTaskToDesktopInternal(taskContainer, successCallback, transitionSource)));
    }

    private void moveTaskToDesktopInternal(TaskContainer taskContainer,
            Runnable successCallback, DesktopModeTransitionSource transitionSource) {
        if (mDesktopRecentsTransitionController == null) {
            return;
        }
        mDesktopRecentsTransitionController.moveToDesktop(taskContainer.getTask().key.id,
                transitionSource);
        successCallback.run();
    }

    // Logs when the orientation of Overview changes. We log both real and fake orientation changes.
    private void logOrientationChanged() {
        // Only log when Overview is showing.
        if (mOverviewStateEnabled) {
            mContainer.getStatsLogManager()
                    .logger()
                    .withContainerInfo(
                            LauncherAtom.ContainerInfo.newBuilder()
                                    .setTaskSwitcherContainer(
                                            LauncherAtom.TaskSwitcherContainer.newBuilder()
                                                    .setOrientationHandler(
                                                            getPagedOrientationHandler()
                                                                    .getHandlerTypeForLogging()))
                                    .build())
                    .log(LAUNCHER_OVERVIEW_ORIENTATION_CHANGED);
        }
    }

    public interface TaskLaunchListener {
        void onTaskLaunched();
    }
}
