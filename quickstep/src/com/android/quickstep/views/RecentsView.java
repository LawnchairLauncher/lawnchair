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
import static com.android.launcher3.anim.Interpolators.ACCEL_0_75;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.FINAL_FRAME;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_CLEAR_ALL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_DISMISS_SWIPE_UP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.launcher3.testing.TestProtocol.TASK_VIEW_ID_CRASH;
import static com.android.launcher3.touch.PagedOrientationHandler.CANVAS_TRANSLATE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;
import static com.android.quickstep.views.ClearAllButton.DISMISS_ALPHA;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NON_ZERO_ROTATION;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_RECENTS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_TASKS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_SPLIT_SCREEN;

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

import androidx.annotation.NonNull;
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
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.ResourceBasedOverride.Overrides;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.SplitConfigurationOptions.StagedSplitBounds;
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
import com.android.quickstep.RemoteTargetGluer;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.ViewUtils;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SplitScreenBounds;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.VibratorWrapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A list of recent tasks.
 */
@TargetApi(Build.VERSION_CODES.R)
public abstract class RecentsView<ACTIVITY_TYPE extends StatefulActivity<STATE_TYPE>,
        STATE_TYPE extends BaseState<STATE_TYPE>> extends PagedView implements Insettable,
        TaskThumbnailCache.HighResLoadingState.HighResLoadingStateChangedCallback,
        TaskVisualsChangeListener, SplitScreenBounds.OnChangeListener {

    private static final String TAG = "RecentsView";
    private static final boolean DEBUG = false;

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

    public static final int SCROLL_VIBRATION_PRIMITIVE =
            Utilities.ATLEAST_S ? VibrationEffect.Composition.PRIMITIVE_LOW_TICK : -1;
    public static final float SCROLL_VIBRATION_PRIMITIVE_SCALE = 0.6f;
    public static final VibrationEffect SCROLL_VIBRATION_FALLBACK =
            VibratorWrapper.EFFECT_TEXTURE_TICK;

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
    private static final float ANIMATION_DISMISS_PROGRESS_MIDPOINT = 0.5f;
    private static final float END_DISMISS_TRANSLATION_INTERPOLATION_OFFSET = 0.75f;

    private static final float SIGNIFICANT_MOVE_THRESHOLD_TABLET = 0.15f;

    protected final RecentsOrientedState mOrientationState;
    protected final BaseActivityInterface<STATE_TYPE, ACTIVITY_TYPE> mSizeStrategy;
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
    protected final Rect mLastComputedTaskSize = new Rect();
    protected final Rect mLastComputedGridSize = new Rect();
    protected final Rect mLastComputedGridTaskSize = new Rect();
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

    protected final ACTIVITY_TYPE mActivity;
    private final float mFastFlingVelocity;
    private final int mScrollHapticMinGapMillis;
    private final RecentsModel mModel;
    private final int mSplitPlaceholderSize;
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

    /**
     * Getting views should be done via {@link #getTaskViewFromPool(boolean)}
     */
    private final ViewPool<TaskView> mTaskViewPool;
    private final ViewPool<GroupedTaskView> mGroupedTaskViewPool;

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
    private boolean mShowAsGridLastOnLayout = false;
    private final IntSet mTopRowIdSet = new IntSet();

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
    /**
     * ID for the current running TaskView view, unique amongst TaskView instances. ID's are set
     * through {@link #getTaskViewFromPool(boolean)} and incremented by {@link #mTaskViewIdCount}
     */
    protected int mRunningTaskViewId = -1;
    private int mTaskViewIdCount;
    private final int[] INVALID_TASK_IDS = new int[]{-1, -1};
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
    private SplitSelectStateController mSplitSelectStateController;
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
    private TaskView mSecondSplitHiddenTaskView;
    @Nullable
    private StagedSplitBounds mSplitBoundsConfig;
    private final Toast mSplitToast = Toast.makeText(getContext(),
            R.string.toast_split_select_app, Toast.LENGTH_SHORT);
    private final Toast mSplitUnsupportedToast = Toast.makeText(getContext(),
            R.string.toast_split_app_unsupported, Toast.LENGTH_SHORT);

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
    private FloatingTaskView mFirstFloatingTaskView;
    @Nullable
    private FloatingTaskView mSecondFloatingTaskView;

    /**
     * The task to be removed and immediately re-added. Should not be added to task pool.
     */
    @Nullable
    private TaskView mMovingTaskView;

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

    @Nullable
    private RunnableList mSideTaskLaunchCallback;
    @Nullable
    private TaskLaunchListener mTaskLaunchListener;

    public RecentsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            BaseActivityInterface sizeStrategy) {
        super(context, attrs, defStyleAttr);
        setEnableFreeScroll(true);
        mSizeStrategy = sizeStrategy;
        mActivity = BaseActivity.fromContext(context);
        mOrientationState = new RecentsOrientedState(
                context, mSizeStrategy, this::animateRecentsRotationInPlace);
        final int rotation = mActivity.getDisplay().getRotation();
        mOrientationState.setRecentsRotation(rotation);

        mScrollHapticMinGapMillis = getResources()
                .getInteger(R.integer.recentsScrollHapticMinGapMillis);
        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);
        mModel = RecentsModel.INSTANCE.get(context);
        mIdp = InvariantDeviceProfile.INSTANCE.get(context);

        mClearAllButton = (ClearAllButton) LayoutInflater.from(context)
                .inflate(R.layout.overview_clear_all_button, this, false);
        mClearAllButton.setOnClickListener(this::dismissAllTasks);
        mTaskViewPool = new ViewPool<>(context, this, R.layout.task, 20 /* max size */,
                10 /* initial size */);
        mGroupedTaskViewPool = new ViewPool<>(context, this,
                R.layout.task_grouped, 20 /* max size */, 10 /* initial size */);

        mIsRtl = mOrientationHandler.getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mSplitPlaceholderSize = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_size);
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
            mOrientationHandler.setPrimary(canvas, CANVAS_TRANSLATE, scroll);

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
                && mRemoteTargetHandles != null) {
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
    @Nullable
    public Task onTaskThumbnailChanged(int taskId, ThumbnailData thumbnailData) {
        if (mHandleTaskStackChanges) {
            TaskView taskView = getTaskViewByTaskId(taskId);
            if (taskView != null) {
                for (TaskView.TaskIdAttributeContainer container :
                        taskView.getTaskIdAttributeContainers()) {
                    if (container == null || taskId != container.getTask().key.id) {
                        continue;
                    }
                    container.getThumbnailView().setThumbnail(container.getTask(), thumbnailData);
                }
            }
        }
        return null;
    }

    @Override
    public void onTaskIconChanged(String pkg, UserHandle user) {
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView tv = requireTaskViewAt(i);
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
    @Nullable
    public TaskView updateThumbnail(int taskId, ThumbnailData thumbnailData, boolean refreshNow) {
        TaskView taskView = getTaskViewByTaskId(taskId);
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

    public void init(OverviewActionsView actionsView, SplitSelectStateController splitController) {
        mActionsView = actionsView;
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, getTaskViewCount() == 0);
        mSplitSelectStateController = splitController;
    }

    public SplitSelectStateController getSplitPlaceholder() {
        return mSplitSelectStateController;
    }

    public boolean isSplitSelectionActive() {
        return mSplitSelectStateController.isSplitSelectActive();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
        mModel.getThumbnailCache().getHighResLoadingState().addCallback(this);
        mActivity.addMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = new SurfaceTransactionApplier(this);
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTransformParams()
                .setSyncTransactionApplier(mSyncTransactionApplier));
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
        runActionOnRemoteHandles(remoteTargetHandle -> remoteTargetHandle.getTransformParams()
                .setSyncTransactionApplier(null));
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
                mGroupedTaskViewPool.recycle((GroupedTaskView)taskView);
            } else {
                mTaskViewPool.recycle(taskView);
            }
            taskView.setTaskViewId(-1);
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

    public void launchSideTaskInLiveTileMode(int taskId, RemoteAnimationTargetCompat[] apps,
            RemoteAnimationTargetCompat[] wallpaper, RemoteAnimationTargetCompat[] nonApps) {
        AnimatorSet anim = new AnimatorSet();
        TaskView taskView = getTaskViewByTaskId(taskId);
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

    public boolean isTaskViewFullyVisible(TaskView tv) {
        if (showAsGrid()) {
            int screenStart = mOrientationHandler.getPrimaryScroll(this);
            int screenEnd = screenStart + mOrientationHandler.getMeasuredSize(this);
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
        int screenStart = mOrientationHandler.getPrimaryScroll(this);
        int clearAllWidth = mOrientationHandler.getPrimarySize(mClearAllButton);
        int clearAllScroll = getScrollForPage(indexOfChild(mClearAllButton));
        int targetScroll = clearAllScroll + (mIsRtl ? clearAllWidth : -clearAllWidth);
        return screenStart - targetScroll;
    }

    private int getSnapToFocusedTaskScrollDiff(boolean isClearAllHidden) {
        int screenStart = mOrientationHandler.getPrimaryScroll(this);
        int targetScroll = getScrollForPage(indexOfChild(getFocusedTaskView()));
        if (!isClearAllHidden) {
            int clearAllWidth = mOrientationHandler.getPrimarySize(mClearAllButton);
            int taskGridHorizontalDiff = mLastComputedTaskSize.right - mLastComputedGridSize.right;
            int clearAllFocusScrollDiff =  taskGridHorizontalDiff - clearAllWidth;
            targetScroll += mIsRtl ? clearAllFocusScrollDiff : -clearAllFocusScrollDiff;
        }
        return screenStart - targetScroll;
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

    private boolean isTaskViewFullyWithinBounds(TaskView tv, int start, int end) {
        int taskStart = mOrientationHandler.getChildStart(tv) + (int) tv.getOffsetAdjustment(
                showAsFullscreen(), showAsGrid());
        int taskSize = (int) (mOrientationHandler.getMeasuredSize(tv) * tv.getSizeAdjustment(
                showAsFullscreen()));
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

    /**
     * Returns a {@link TaskView} that has taskId matching {@code taskId} or null if no match.
     */
    @Nullable
    public TaskView getTaskViewByTaskId(int taskId) {
        if (taskId == -1) {
            return null;
        }

        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = requireTaskViewAt(i);
            int[] taskIds = taskView.getTaskIds();
            if (taskIds[0] == taskId || taskIds[1] == taskId) {
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
            mTmpRunningTasks = null;
            mSplitBoundsConfig = null;
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
    protected float getSignificantMoveThreshold() {
        return mActivity.getDeviceProfile().isTablet ? SIGNIFICANT_MOVE_THRESHOLD_TABLET
                : super.getSignificantMoveThreshold();
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
                // Only snap to fully visible focused task.
                if (taskView == null
                        || !taskView.isFocusedTask()
                        || !isTaskViewFullyVisible(taskView)) {
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
     * Moves the focused task to the front of the carousel in tablets, to minimize animation
     * required to focus the task in grid.
     */
    public void moveFocusedTaskToFront() {
        if (!mActivity.getDeviceProfile().overviewShowAsGrid) {
            return;
        }

        TaskView focusedTaskView = getFocusedTaskView();
        if (focusedTaskView == null) {
            return;
        }

        if (indexOfChild(focusedTaskView) != mCurrentPage) {
            return;
        }

        if (mCurrentPage == 0) {
            return;
        }

        int primaryScroll = mOrientationHandler.getPrimaryScroll(this);
        int currentPageScroll = getScrollForPage(mCurrentPage);
        mCurrentPageScrollDiff = primaryScroll - currentPageScroll;

        mMovingTaskView = focusedTaskView;
        removeView(focusedTaskView);
        mMovingTaskView = null;
        focusedTaskView.resetPersistentViewTransforms();
        addView(focusedTaskView, 0);
        setCurrentPage(0);

        updateGridProperties();
    }

    protected void applyLoadPlan(ArrayList<GroupTask> taskGroups) {
        if (mPendingAnimation != null) {
            mPendingAnimation.addEndListener(success -> applyLoadPlan(taskGroups));
            return;
        }

        mLoadPlanEverApplied = true;
        if (taskGroups == null || taskGroups.isEmpty()) {
            removeTasksViewsAndClearAllButton();
            onTaskStackUpdated();
            return;
        }

        int currentTaskId = -1;
        TaskView currentTaskView = getTaskViewAt(mCurrentPage);
        if (currentTaskView != null) {
            currentTaskId = currentTaskView.getTask().key.id;
        }

        // Unload existing visible task data
        unloadVisibleTaskData(TaskView.FLAG_UPDATE_ALL);

        TaskView ignoreResetTaskView =
                mIgnoreResetTaskId == -1 ? null : getTaskViewByTaskId(mIgnoreResetTaskId);

        // Save running task ID if it exists before rebinding all taskViews, otherwise the task from
        // the runningTaskView currently bound could get assigned to another TaskView
        int runningTaskId = getTaskIdsForTaskViewId(mRunningTaskViewId)[0];
        int focusedTaskId = getTaskIdsForTaskViewId(mFocusedTaskViewId)[0];

        // Removing views sets the currentPage to 0, so we save this and restore it after
        // the new set of views are added
        int previousCurrentPage = mCurrentPage;
        removeAllViews();

        // Add views as children based on whether it's grouped or single task
        for (int i = taskGroups.size() - 1; i >= 0; i--) {
            GroupTask groupTask = taskGroups.get(i);
            boolean hasMultipleTasks = groupTask.hasMultipleTasks();
            TaskView taskView = getTaskViewFromPool(hasMultipleTasks);
            addView(taskView);

            if (hasMultipleTasks) {
                boolean firstTaskIsLeftTopTask =
                        groupTask.mStagedSplitBounds.leftTopTaskId == groupTask.task1.key.id;
                Task leftTopTask = firstTaskIsLeftTopTask ? groupTask.task1 : groupTask.task2;
                Task rightBottomTask = firstTaskIsLeftTopTask ? groupTask.task2 : groupTask.task1;
                ((GroupedTaskView) taskView).bind(leftTopTask, rightBottomTask, mOrientationState,
                        groupTask.mStagedSplitBounds);
            } else {
                taskView.bind(groupTask.task1, mOrientationState);
            }
        }
        if (!taskGroups.isEmpty()) {
            addView(mClearAllButton);
        }

        boolean settlingOnNewTask = mNextPage != INVALID_PAGE;
        if (settlingOnNewTask) {
            // Restore mCurrentPage but don't call setCurrentPage() as that clobbers the scroll.
            mCurrentPage = previousCurrentPage;
        } else {
            setCurrentPage(previousCurrentPage);
        }

        // Keep same previous focused task
        TaskView newFocusedTaskView = getTaskViewByTaskId(focusedTaskId);
        // If the list changed, maybe the focused task doesn't exist anymore
        if (newFocusedTaskView == null && getTaskViewCount() > 0) {
            newFocusedTaskView = getTaskViewAt(0);
        }
        mFocusedTaskViewId = newFocusedTaskView != null ?
                newFocusedTaskView.getTaskViewId() : -1;
        updateTaskSize();
        updateChildTaskOrientations();

        TaskView newRunningTaskView = null;
        if (runningTaskId != -1) {
            // Update mRunningTaskViewId to be the new TaskView that was assigned by binding
            // the full list of tasks to taskViews
            newRunningTaskView = getTaskViewByTaskId(runningTaskId);
            if (newRunningTaskView != null) {
                mRunningTaskViewId = newRunningTaskView.getTaskViewId();
            } else {
                mRunningTaskViewId = -1;
            }
        }

        int targetPage = -1;
        if (!settlingOnNewTask) {
            // Set the current page to the running task, but not if settling on new task.
            if (runningTaskId != -1) {
                targetPage = indexOfChild(newRunningTaskView);
            } else if (getTaskViewCount() > 0) {
                targetPage = indexOfChild(requireTaskViewAt(0));
            }
        } else if (currentTaskId != -1) {
            currentTaskView = getTaskViewByTaskId(currentTaskId);
            if (currentTaskView != null) {
                targetPage = indexOfChild(currentTaskView);
            }
        }
        if (targetPage != -1 && mCurrentPage != targetPage) {
            setCurrentPage(targetPage);
        }

        if (mIgnoreResetTaskId != -1 &&
                getTaskViewByTaskId(mIgnoreResetTaskId) != ignoreResetTaskView) {
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
            removeView(requireTaskViewAt(i));
        }
        if (indexOfChild(mClearAllButton) != -1) {
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
        return getTaskViewCount() - mTopRowIdSet.size() - 1;
    }

    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }

    public void resetTaskVisuals() {
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView taskView = requireTaskViewAt(i);
            if (mIgnoreResetTaskId != taskView.getTaskIds()[0]) {
                taskView.resetViewTransforms();
                taskView.setIconScaleAndDim(mTaskIconScaledDown ? 0 : 1);
                taskView.setStableAlpha(mContentAlpha);
                taskView.setFullscreenProgress(mFullscreenProgress);
                taskView.setModalness(mTaskModalness);
            }
        }
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
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
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            requireTaskViewAt(i).setFullscreenProgress(mFullscreenProgress);
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
        setPageSpacing(dp.overviewPageSpacing);

        // Propagate DeviceProfile change event.
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator().setDp(dp));
        mActionsView.setDp(dp);
        mOrientationState.setDeviceProfile(dp);

        // Update RecentsView and TaskView's DeviceProfile dependent layout.
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
            onOrientationChanged();
        }

        boolean isInLandscape = mOrientationState.getTouchRotation() != ROTATION_0
                || mOrientationState.getRecentsActivityRotation() != ROTATION_0;
        mActionsView.updateHiddenFlags(HIDDEN_NON_ZERO_ROTATION,
                !mOrientationState.isRecentsActivityRotationAllowed() && isInLandscape);

        // Update TaskView's DeviceProfile dependent layout.
        updateChildTaskOrientations();

        // Recalculate DeviceProfile dependent layout.
        updateSizeAndPadding();

        requestLayout();
        // Reapply the current page to update page scrolls.
        setCurrentPage(mCurrentPage);
    }

    private void onOrientationChanged() {
        // If overview is in modal state when rotate, reset it to overview state without running
        // animation.
        setModalStateEnabled(false);
        if (isSplitSelectionActive()) {
            onRotateInSplitSelectionState();
        }
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

        mTaskGridVerticalDiff = mLastComputedGridTaskSize.top - mLastComputedTaskSize.top;
        mTopBottomRowHeightDiff =
                mLastComputedGridTaskSize.height() + dp.overviewTaskThumbnailTopMarginPx
                        + dp.overviewRowSpacing;

        // Force TaskView to update size from thumbnail
        updateTaskSize();
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
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            taskView.updateTaskSize();
            taskView.getPrimaryNonGridTranslationProperty().set(taskView, accumulatedTranslationX);
            taskView.getSecondaryNonGridTranslationProperty().set(taskView, 0f);
            // Compensate space caused by TaskView scaling.
            float widthDiff =
                    taskView.getLayoutParams().width * (1 - taskView.getNonGridScale());
            accumulatedTranslationX += mIsRtl ? widthDiff : -widthDiff;
        }

        mClearAllButton.setFullscreenTranslationPrimary(accumulatedTranslationX);

        updateGridProperties(isTaskDismissal);
    }

    public void getTaskSize(Rect outRect) {
        mSizeStrategy.calculateTaskSize(mActivity, mActivity.getDeviceProfile(), outRect);
        mLastComputedTaskSize.set(outRect);
    }

    /**
     * Returns the size of task selected to enter modal state.
     */
    public Point getSelectedTaskSize() {
        mSizeStrategy.calculateTaskSize(mActivity, mActivity.getDeviceProfile(), mTempRect);
        return new Point(mTempRect.width(), mTempRect.height());
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
        }

        // Update ActionsView's visibility when scroll changes.
        updateActionsViewFocusedScroll();

        // Update the high res thumbnail loader state
        mModel.getThumbnailCache().getHighResLoadingState().setFlingingFast(isFlingingFast);
        return scrolling;
    }

    private void updateActionsViewFocusedScroll() {
        boolean hiddenFocusedScroll;
        if (showAsGrid()) {
            TaskView focusedTaskView = getFocusedTaskView();
            hiddenFocusedScroll = focusedTaskView == null
                    || !isTaskInExpectedScrollPosition(indexOfChild(focusedTaskView));
        } else {
            hiddenFocusedScroll = false;
        }
        mActionsView.updateHiddenFlags(OverviewActionsView.HIDDEN_FOCUSED_SCROLL,
                hiddenFocusedScroll);
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
        if (!mActivity.getDeviceProfile().overviewShowAsGrid) {
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
            TaskView taskView = requireTaskViewAt(i);
            Task task = taskView.getTask();
            int index = indexOfChild(taskView);
            boolean visible;
            if (showAsGrid()) {
                visible = isTaskViewWithinBounds(taskView, visibleStart, visibleEnd);
            } else {
                visible = lower <= index && index <= upper;
            }
            if (visible) {
                boolean skipLoadingTask = false;
                if (mTmpRunningTasks != null) {
                    for (Task t : mTmpRunningTasks) {
                        if (task == t) {
                            // Skip loading if this is the task that we are animating into
                            skipLoadingTask = true;
                            break;
                        }
                    }
                }
                if (skipLoadingTask) {
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

    public abstract void startHome();

    public void reset() {
        setCurrentTask(-1);
        mCurrentPageScrollDiff = 0;
        mIgnoreResetTaskId = -1;
        mTaskListChangeId = -1;
        mFocusedTaskViewId = -1;

        if (mRecentsAnimationController != null) {
            if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile) {
                // We are still drawing the live tile, finish it now to clean up.
                finishRecentsAnimation(true /* toRecents */, null);
            } else {
                mRecentsAnimationController = null;
            }
        }
        setEnableDrawingLiveTile(false);
        runActionOnRemoteHandles(remoteTargetHandle -> {
            remoteTargetHandle.getTransformParams().setTargetSet(null);
            remoteTargetHandle.getTaskViewSimulator().setDrawsBelowRecents(false);
        });
        resetFromSplitSelectionState();
        mSplitSelectStateController.resetState();

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
            return INVALID_TASK_IDS;
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
    private <T extends TaskView> T getTaskViewFromPool(boolean isGrouped) {
        T taskView = isGrouped ?
                (T) mGroupedTaskViewPool.getView() :
                (T) mTaskViewPool.getView();
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
            mTaskListChangeId = mModel.getTasks(this::applyLoadPlan);
        }
    }

    /**
     * Called when a gesture from an app is starting.
     */
    public void onGestureAnimationStart(RunningTaskInfo[] runningTaskInfo) {
        mGestureActive = true;
        // This needs to be called before the other states are set since it can create the task view
        if (mOrientationState.setGestureActive(true)) {
            updateOrientationHandler();
        }

        showCurrentTask(runningTaskInfo);
        setEnableFreeScroll(false);
        setEnableDrawingLiveTile(false);
        setRunningTaskHidden(true);
        setTaskIconScaledDown(true);
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
            View taskView = requireTaskViewAt(i);
            if (runningIndex == i && taskView.getAlpha() != 0) {
                continue;
            }
            as.play(ObjectAnimator.ofFloat(taskView, View.ALPHA, fadeInChildren ? 0 : 1));
        }
        return as;
    }

    private void updateChildTaskOrientations() {
        for (int i = 0; i < getTaskViewCount(); i++) {
            requireTaskViewAt(i).setOrientationState(mOrientationState);
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
            @Nullable AnimatorSet animatorSet, GestureState.GestureEndTarget endTarget,
            TaskViewSimulator[] taskViewSimulators) {
        mCurrentGestureEndTarget = endTarget;
        if (endTarget == GestureState.GestureEndTarget.RECENTS) {
            updateGridProperties();
        }

        if (mSizeStrategy.stateFromGestureEndTarget(endTarget)
                .displayOverviewTasksAsGrid(mActivity.getDeviceProfile())) {
            TaskView runningTaskView = getRunningTaskView();
            float runningTaskPrimaryGridTranslation = 0;
            if (runningTaskView != null) {
                // Apply the grid translation to running task unless it's being snapped to
                // and removes the current translation applied to the running task.
                runningTaskPrimaryGridTranslation = mOrientationHandler.getPrimaryValue(
                        runningTaskView.getGridTranslationX(),
                        runningTaskView.getGridTranslationY())
                        - runningTaskView.getPrimaryNonGridTranslationProperty().get(
                        runningTaskView);
            }
            for (TaskViewSimulator tvs : taskViewSimulators) {
                if (animatorSet == null) {
                    setGridProgress(1);
                    tvs.taskPrimaryTranslation.value =
                            runningTaskPrimaryGridTranslation;
                } else {
                    animatorSet.play(ObjectAnimator.ofFloat(this, RECENTS_GRID_PROGRESS, 1));
                    animatorSet.play(tvs.taskPrimaryTranslation.animateToValue(
                            runningTaskPrimaryGridTranslation));
                }
            }
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
        animateUpTaskIconScale();
        animateActionsViewIn();

        mCurrentGestureEndTarget = null;
    }

    /**
     * Returns true if we should add a stub taskView for the running task id
     */
    protected boolean shouldAddStubTaskView(RunningTaskInfo[] runningTaskInfos) {
        if (runningTaskInfos.length > 1) {
            TaskView primaryTaskView = getTaskViewByTaskId(runningTaskInfos[0].taskId);
            TaskView secondaryTaskView = getTaskViewByTaskId(runningTaskInfos[1].taskId);
            int leftTopTaskViewId =
                    (primaryTaskView == null) ? -1 : primaryTaskView.getTaskViewId();
            int rightBottomTaskViewId =
                    (secondaryTaskView == null) ? -1 : secondaryTaskView.getTaskViewId();
            // Add a new stub view if both taskIds don't match any taskViews
            return leftTopTaskViewId != rightBottomTaskViewId || leftTopTaskViewId == -1;
        }
        RunningTaskInfo runningTaskInfo = runningTaskInfos[0];
        return runningTaskInfo != null && getTaskViewByTaskId(runningTaskInfo.taskId) == null;
    }

    /**
     * Creates a task view (if necessary) to represent the task with the {@param runningTaskId}.
     *
     * All subsequent calls to reload will keep the task as the first item until {@link #reset()}
     * is called.  Also scrolls the view to this task.
     */
    private void showCurrentTask(RunningTaskInfo[] runningTaskInfo) {
        int runningTaskViewId = -1;
        boolean needGroupTaskView = runningTaskInfo.length > 1;
        RunningTaskInfo taskInfo = runningTaskInfo[0];
        if (shouldAddStubTaskView(runningTaskInfo)) {
            boolean wasEmpty = getChildCount() == 0;
            // Add an empty view for now until the task plan is loaded and applied
            final TaskView taskView;
            if (needGroupTaskView) {
                taskView = getTaskViewFromPool(true);
                RunningTaskInfo secondaryTaskInfo = runningTaskInfo[1];
                mTmpRunningTasks = new Task[]{
                        Task.from(new TaskKey(taskInfo), taskInfo, false),
                        Task.from(new TaskKey(secondaryTaskInfo), secondaryTaskInfo, false)
                };
                addView(taskView, 0);
                // When we create a placeholder task view mSplitBoundsConfig will be null, but with
                // the actual app running we won't need to show the thumbnail until all the tasks
                // load later anyways
                ((GroupedTaskView)taskView).bind(mTmpRunningTasks[0], mTmpRunningTasks[1],
                        mOrientationState, mSplitBoundsConfig);
            } else {
                taskView = getTaskViewFromPool(false);
                addView(taskView, 0);
                // The temporary running task is only used for the duration between the start of the
                // gesture and the task list is loaded and applied
                mTmpRunningTasks = new Task[]{Task.from(new TaskKey(taskInfo), taskInfo, false)};
                taskView.bind(mTmpRunningTasks[0], mOrientationState);
            }
            runningTaskViewId = taskView.getTaskViewId();
            if (wasEmpty) {
                addView(mClearAllButton);
            }

            // Measure and layout immediately so that the scroll values is updated instantly
            // as the user might be quick-switching
            measure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                    makeMeasureSpec(getMeasuredHeight(), EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        } else if (getTaskViewByTaskId(taskInfo.taskId) != null) {
            runningTaskViewId = getTaskViewByTaskId(taskInfo.taskId).getTaskViewId();
        }

        boolean runningTaskTileHidden = mRunningTaskTileHidden;
        setCurrentTask(runningTaskViewId);
        mFocusedTaskViewId = runningTaskViewId;
        setCurrentPage(getRunningTaskIndex());
        setRunningTaskViewShowScreenshot(false);
        setRunningTaskHidden(runningTaskTileHidden);
        // Update task size after setting current task.
        updateTaskSize();
        updateChildTaskOrientations();

        // Reload the task list
        reloadIfNeeded();
    }

    /**
     * Sets the running task id, cleaning up the old running task if necessary.
     */
    public void setCurrentTask(int runningTaskViewId) {
        Log.d(TASK_VIEW_ID_CRASH, "currentRunningTaskViewId: " + mRunningTaskViewId
                + " requestedTaskViewId: " + runningTaskViewId);
        if (mRunningTaskViewId == runningTaskViewId) {
            return;
        }

        if (mRunningTaskViewId != -1) {
            // Reset the state on the old running task view
            setTaskIconScaledDown(false);
            setRunningTaskViewShowScreenshot(true);
            setRunningTaskHidden(false);
        }
        mRunningTaskViewId = runningTaskViewId;
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
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mRunningTaskShowScreenshot = showScreenshot;
            TaskView runningTaskView = getRunningTaskView();
            if (runningTaskView != null) {
                runningTaskView.setShowScreenshot(mRunningTaskShowScreenshot);
            }
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
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                mActionsView.getVisibilityAlpha(), MultiValueAlpha.VALUE, 0, 1);
        anim.setDuration(TaskView.SCALE_ICON_DURATION);
        anim.start();
    }

    public void animateUpTaskIconScale() {
        mTaskIconScaledDown = false;
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            taskView.setIconScaleAnimStartProgress(0f);
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

        int taskTopMargin = mActivity.getDeviceProfile().overviewTaskThumbnailTopMarginPx;

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
        int snappedPage = getNextPage();
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
                    /*fullscreenEnabled=*/true, /*gridEnabled=*/false);
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
                        + clearAllShortTotalCompensation + snappedTaskNonGridScrollAdjustment;
        if (focusedTaskIndex < taskCount) {
            // Shift by focused task's width and spacing if a task is focused.
            clearAllTotalTranslationX +=
                    mIsRtl ? focusedTaskWidthAndSpacing : -focusedTaskWidthAndSpacing;
        }

        // Make sure there are enough space between snapped page and ClearAllButton, for the case
        // of swiping up after quick switch.
        if (snappedTaskView != null) {
            int distanceFromClearAll = longRowWidth - snappedTaskRowWidth + mPageSpacing;
            // ClearAllButton should be off screen when snapped task is in its snapped position.
            int minimumDistance =
                    mTaskWidth - snappedTaskView.getLayoutParams().width
                            + (mLastComputedGridSize.width() - mTaskWidth) / 2;
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
        int taskCount = getTaskViewCount();
        if (taskCount == 0) {
            return;
        }

        mGridProgress = gridProgress;

        for (int i = 0; i < taskCount; i++) {
            requireTaskViewAt(i).setGridProgress(gridProgress);
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
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && taskView.isRunningTask()) {
            runActionOnRemoteHandles(remoteTargetHandle -> {
                TransformParams params = remoteTargetHandle.getTransformParams();
                anim.setFloat(params, TransformParams.TARGET_ALPHA, 0,
                        clampToProgress(FINAL_FRAME, 0, 0.5f));
            });
        }
        anim.setFloat(taskView, VIEW_ALPHA, 0,
                clampToProgress(isOnGridBottomRow(taskView) ? ACCEL : FINAL_FRAME, 0, 0.5f));
        FloatProperty<TaskView> secondaryViewTranslate =
                taskView.getSecondaryDissmissTranslationProperty();
        int secondaryTaskDimension = mOrientationHandler.getSecondaryDimension(taskView);
        int verticalFactor = mOrientationHandler.getSecondaryTranslationDirectionFactor();

        ResourceProvider rp = DynamicResource.provider(mActivity);
        SpringProperty sp = new SpringProperty(SpringProperty.FLAG_CAN_SPRING_ON_START)
                .setDampingRatio(rp.getFloat(R.dimen.dismiss_task_trans_y_damping_ratio))
                .setStiffness(rp.getFloat(R.dimen.dismiss_task_trans_y_stiffness));

        anim.add(ObjectAnimator.ofFloat(taskView, secondaryViewTranslate,
                verticalFactor * secondaryTaskDimension * 2).setDuration(duration), LINEAR, sp);

        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                && taskView.isRunningTask()) {
            anim.addOnFrameCallback(() -> {
                runActionOnRemoteHandles(
                        remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                                .taskSecondaryTranslation.value = mOrientationHandler
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
        mOrientationHandler.getInitialSplitPlaceholderBounds(mSplitPlaceholderSize,
                mActivity.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), mTempRect);

        RectF startingTaskRect = new RectF();
        mSplitHiddenTaskView.setVisibility(INVISIBLE);
        mFirstFloatingTaskView = FloatingTaskView.getFloatingTaskView(mActivity,
                mSplitHiddenTaskView, startingTaskRect);
        mFirstFloatingTaskView.setAlpha(1);
        mFirstFloatingTaskView.addAnimation(anim, startingTaskRect,
                mTempRect, mSplitHiddenTaskView, true /*fadeWithThumbnail*/);
        anim.addEndListener(success -> {
            if (success) {
                mSplitToast.show();
            }
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
    public PendingAnimation createTaskDismissAnimation(TaskView dismissedTaskView,
            boolean animateTaskView, boolean shouldRemoveTask, long duration,
            boolean dismissingForSplitSelection) {
        if (mPendingAnimation != null) {
            mPendingAnimation.createPlaybackController().dispatchOnCancel().dispatchOnEnd();
        }
        PendingAnimation anim = new PendingAnimation(duration);

        int count = getPageCount();
        if (count == 0) {
            return anim;
        }

        boolean showAsGrid = showAsGrid();
        int taskCount = getTaskViewCount();
        int dismissedIndex = indexOfChild(dismissedTaskView);
        int dismissedTaskViewId = dismissedTaskView.getTaskViewId();

        // Grid specific properties.
        boolean isFocusedTaskDismissed = false;
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
            if (isFocusedTaskDismissed && !isSplitSelectionActive()) {
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
        } else {
            getPageScrolls(oldScroll, false, SIMPLE_SCROLL_LOGIC);
            getPageScrolls(newScroll, false,
                    v -> v.getVisibility() != GONE && v != dismissedTaskView);
            if (count > 1) {
                scrollDiffPerPage = Math.abs(oldScroll[1] - oldScroll[0]);
            }
        }

        announceForAccessibility(getResources().getString(R.string.task_view_closed));

        float dismissTranslationInterpolationEnd = 1;
        boolean closeGapBetweenClearAll = false;
        boolean isClearAllHidden = isClearAllHidden();
        boolean snapToLastTask = false;
        boolean isLandscapeSplit =
                mActivity.getDeviceProfile().isLandscape && isSplitSelectionActive();
        boolean isSplitPlaceholderFirstInGrid = isSplitPlaceholderFirstInGrid();
        boolean isSplitPlaceholderLastInGrid = isSplitPlaceholderLastInGrid();
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
            int bottomGridRowSize = taskCount - mTopRowIdSet.size() - 1;
            boolean topRowLonger = topGridRowSize > bottomGridRowSize;
            boolean bottomRowLonger = bottomGridRowSize > topGridRowSize;
            boolean dismissedTaskFromTop = mTopRowIdSet.contains(dismissedTaskViewId);
            boolean dismissedTaskFromBottom = !dismissedTaskFromTop && !isFocusedTaskDismissed;
            float gapWidth = 0;
            if ((topRowLonger && dismissedTaskFromTop)
                    || (bottomRowLonger && dismissedTaskFromBottom)) {
                gapWidth = dismissedTaskWidth;
            } else if ((topRowLonger && nextFocusedTaskFromTop)
                    || (bottomRowLonger && !nextFocusedTaskFromTop)) {
                gapWidth = nextFocusedTaskWidth;
            }
            if (gapWidth > 0) {
                if (taskCount > 2) {
                    // Compensate the removed gap.
                    longGridRowWidthDiff += mIsRtl ? -gapWidth : gapWidth;
                    if (isClearAllHidden) {
                        // If ClearAllButton isn't fully shown, snap to the last task.
                        snapToLastTask = true;
                    }
                } else {
                    // If only focused task will be left, snap to focused task instead.
                    longGridRowWidthDiff += getSnapToFocusedTaskScrollDiff(isClearAllHidden);
                }
            }
            if (mClearAllButton.getAlpha() != 0f && isLandscapeSplit) {
                // ClearAllButton will not be available in split select, snap to last task instead.
                snapToLastTask = true;
            }
            if (snapToLastTask) {
                longGridRowWidthDiff += getSnapToLastTaskScrollDiff();
                if (isSplitPlaceholderLastInGrid) {
                    // Shift all the tasks to make space for split placeholder.
                    longGridRowWidthDiff += mIsRtl ? mSplitPlaceholderSize : -mSplitPlaceholderSize;
                }
            } else if (isLandscapeSplit && currentPageSnapsToEndOfGrid) {
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
                    if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                            && taskView.isRunningTask()) {
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
                            : mOrientationHandler.getPrimaryViewTranslate();

                    float additionalDismissDuration =
                            ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET * Math.abs(
                                    i - dismissedIndex);
                    anim.setFloat(child, translationProperty, scrollDiff, clampToProgress(LINEAR,
                            Utilities.boundToRange(INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                    + additionalDismissDuration, 0f, 1f), 1));
                    if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                            && child instanceof TaskView
                            && ((TaskView) child).isRunningTask()) {
                        anim.addOnFrameCallback(() -> {
                            runActionOnRemoteHandles(
                                    remoteTargetHandle ->
                                            remoteTargetHandle.getTaskViewSimulator()
                                                    .taskPrimaryTranslation.value =
                                                    mOrientationHandler.getPrimaryValue(
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
                float animationStartProgress = Utilities.boundToRange(
                        INITIAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                + ADDITIONAL_DISMISS_TRANSLATION_INTERPOLATION_OFFSET
                                * ++distanceFromDismissedTask, 0f,
                        dismissTranslationInterpolationEnd);
                if (taskView == nextFocusedTaskView) {
                    // Enlarge the task to be focused next, and translate into focus position.
                    float scale = mTaskWidth / (float) mLastComputedGridTaskSize.width();
                    anim.setFloat(taskView, TaskView.SNAPSHOT_SCALE, scale,
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
                    anim.setFloat(taskView, taskView.getSecondaryDissmissTranslationProperty(),
                            secondaryTranslation, clampToProgress(LINEAR, animationStartProgress,
                                    dismissTranslationInterpolationEnd));
                    anim.setFloat(taskView, TaskView.FOCUS_TRANSITION, 0f,
                            clampToProgress(LINEAR, 0f, ANIMATION_DISMISS_PROGRESS_MIDPOINT));
                } else {
                    float primaryTranslation =
                            nextFocusedTaskView != null ? nextFocusedTaskWidth : dismissedTaskWidth;
                    if (isFocusedTaskDismissed && nextFocusedTaskView == null) {
                        // Moves less if focused task is not in scroll position.
                        int focusedTaskScroll = getScrollForPage(dismissedIndex);
                        int primaryScroll = mOrientationHandler.getPrimaryScroll(this);
                        int focusedTaskScrollDiff = primaryScroll - focusedTaskScroll;
                        primaryTranslation +=
                                mIsRtl ? focusedTaskScrollDiff : -focusedTaskScrollDiff;
                        if (isSplitPlaceholderFirstInGrid) {
                            // Moves less if split placeholder is at the start.
                            primaryTranslation +=
                                    mIsRtl ? -mSplitPlaceholderSize : mSplitPlaceholderSize;
                        }
                    }

                    anim.setFloat(taskView, taskView.getPrimaryDismissTranslationProperty(),
                            mIsRtl ? primaryTranslation : -primaryTranslation,
                            clampToProgress(LINEAR, animationStartProgress,
                                    dismissTranslationInterpolationEnd));
                }
            }
        }

        if (needsCurveUpdates) {
            anim.addOnFrameCallback(this::updateCurveProperties);
        }

        // Add a tiny bit of translation Z, so that it draws on top of other views
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
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile
                        && dismissedTaskView.isRunningTask() && success) {
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
                        if (dismissedTaskView.getTask() != null) {
                            if (ENABLE_QUICKSTEP_LIVE_TILE.get()
                                    && dismissedTaskView.isRunningTask()) {
                                finishRecentsAnimation(true /* toRecents */, false /* shouldPip */,
                                        () -> removeTaskInternal(dismissedTaskViewId));
                            } else {
                                removeTaskInternal(dismissedTaskViewId);
                            }
                            mActivity.getStatsLogManager().logger()
                                    .withItemInfo(dismissedTaskView.getItemInfo())
                                    .log(LAUNCHER_TASK_DISMISS_SWIPE_UP);
                        }
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
                                    if (snappedIndex < taskViewIdArray.size()) {
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
                                int primaryScroll = mOrientationHandler.getPrimaryScroll(
                                        RecentsView.this);
                                int currentPageScroll = getScrollForPage(mCurrentPage);
                                mCurrentPageScrollDiff = primaryScroll - currentPageScroll;
                                // Compensate for coordinate shift by split placeholder.
                                if (isSplitPlaceholderFirstInGrid && !finalSnapToLastTask) {
                                    mCurrentPageScrollDiff +=
                                            mIsRtl ? -mSplitPlaceholderSize : mSplitPlaceholderSize;
                                } else if (isSplitPlaceholderLastInGrid && finalSnapToLastTask) {
                                    mCurrentPageScrollDiff +=
                                            mIsRtl ? mSplitPlaceholderSize : -mSplitPlaceholderSize;
                                }
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
                        } else {
                            startHome();
                        }
                    } else {
                        // Update focus task and its size.
                        if (finalIsFocusedTaskDismissed && finalNextFocusedTaskView != null) {
                            mFocusedTaskViewId = finalNextFocusedTaskView.getTaskViewId();
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
                                int screenStart = mOrientationHandler.getPrimaryScroll(
                                        RecentsView.this);
                                int taskStart = mOrientationHandler.getChildStart(taskView)
                                        + (int) taskView.getOffsetAdjustment(/*fullscreenEnabled=*/
                                        false, /*gridEnabled=*/ true);

                                // Rebalance only if there is a maximum gap between the task and the
                                // screen's edge; this ensures that rebalanced tasks are outside the
                                // visible screen.
                                if (mIsRtl) {
                                    shouldRebalance = taskStart <= screenStart + mPageSpacing;
                                } else {
                                    int screenEnd =
                                            screenStart + mOrientationHandler.getMeasuredSize(
                                                    RecentsView.this);
                                    int taskSize = (int) (mOrientationHandler.getMeasuredSize(
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
                        if (isClearAllHidden()) {
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
        return anim;
    }

    /**
     * Hides all overview actions if current page is for split apps, shows otherwise
     * If actions are showing, we only show split option if
     * * Device is large screen
     * * There are at least 2 tasks to invoke split
     */
    private void updateCurrentTaskActionsVisibility() {
        boolean isCurrentSplit = getCurrentPageTaskView() instanceof GroupedTaskView;
        mActionsView.updateHiddenFlags(HIDDEN_SPLIT_SCREEN, isCurrentSplit);
        if (isCurrentSplit) {
            return;
        }
        mActionsView.setSplitButtonVisible(
                mActivity.getDeviceProfile().overviewShowAsGrid && getTaskViewCount() > 1);
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
        int primaryTaskId = taskIds[0];
        int secondaryTaskId = taskIds[1];
        UI_HELPER_EXECUTOR.getHandler().postDelayed(
                () -> {
                    ActivityManagerWrapper.getInstance().removeTask(primaryTaskId);
                    if (secondaryTaskId != -1) {
                        ActivityManagerWrapper.getInstance().removeTask(secondaryTaskId);
                    }
                },
                REMOVE_TASK_WAIT_FOR_APP_STOP_MS);
    }

    /**
     * Returns {@code true} if one of the task thumbnails would intersect/overlap with the
     * {@link #mFirstFloatingTaskView}.
     */
    public boolean shouldShiftThumbnailsForSplitSelect() {
        return !mActivity.getDeviceProfile().isTablet || !mActivity.getDeviceProfile().isLandscape;
    }

    protected void onDismissAnimationEnds() {
        AccessibilityManagerCompat.sendDismissAnimationEndsEventToTest(getContext());
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
        TaskView taskView = getTaskViewByTaskId(taskId);
        if (taskView == null) {
            return;
        }
        dismissTask(taskView, true /* animate */, false /* removeTask */);
    }

    public void dismissTask(TaskView taskView, boolean animateTaskView, boolean removeTask) {
        runDismissAnimation(createTaskDismissAnimation(taskView, animateTaskView, removeTask,
                DISMISS_TASK_DURATION, false /* dismissingForSplitSelection*/));
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
        int runningTaskId = getTaskIdsForRunningTaskView()[0];
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView child = requireTaskViewAt(i);
            int[] childTaskIds = child.getTaskIds();
            if (!mRunningTaskTileHidden ||
                    (childTaskIds[0] != runningTaskId && childTaskIds[1] != runningTaskId)) {
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
        onOrientationChanged();
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
        runActionOnRemoteHandles(
                remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                        .setScroll(getScrollOffset()));
        setImportantForAccessibility(isModal() ? IMPORTANT_FOR_ACCESSIBILITY_NO
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void updatePageOffsets() {
        float offset = mAdjacentPageHorizontalOffset;
        float modalOffset = ACCEL_0_75.getInterpolation(mTaskModalness);
        int count = getChildCount();

        TaskView runningTask = mRunningTaskViewId == -1 || !mRunningTaskTileHidden
                ? null : getRunningTaskView();
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
                runActionOnRemoteHandles(
                        remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                                .taskPrimaryTranslation.value = totalTranslation);
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

            mTempMatrix.reset();
            float persistentScale = taskView.getPersistentScale();
            mTempMatrix.postScale(persistentScale, persistentScale,
                    mIsRtl ? outRect.right : outRect.left, outRect.top);
            mTempMatrix.mapRect(outRect);
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
            if (taskView == mSplitHiddenTaskView) {
                continue;
            }
            taskView.getSecondarySplitTranslationProperty().set(taskView, translation);
        }
    }

    /**
     * Apply scroll offset to children of RecentsView when entering split select.
     */
    public void applySplitPrimaryScrollOffset() {
        float taskSplitScrollOffsetPrimary = 0f;
        float clearAllSplitScrollOffsetPrimar = 0f;
        if (isSplitPlaceholderFirstInGrid()) {
            taskSplitScrollOffsetPrimary = mSplitPlaceholderSize;
        } else if (isSplitPlaceholderLastInGrid()) {
            clearAllSplitScrollOffsetPrimar = -mSplitPlaceholderSize;
        }

        for (int i = 0; i < getTaskViewCount(); i++) {
            requireTaskViewAt(i).setSplitScrollOffsetPrimary(taskSplitScrollOffsetPrimary);
        }
        mClearAllButton.setSplitSelectScrollOffsetPrimary(clearAllSplitScrollOffsetPrimar);
    }

    /**
     * Returns if split placeholder is at the beginning of RecentsView. Always returns {@code false}
     * if RecentsView is in portrait or RecentsView isn't shown as grid.
     */
    private boolean isSplitPlaceholderFirstInGrid() {
        if (!mActivity.getDeviceProfile().isLandscape || !showAsGrid()
                || !isSplitSelectionActive()) {
            return false;
        }
        @StagePosition int position = mSplitSelectStateController.getActiveSplitStagePosition();
        return mIsRtl
                ? position == STAGE_POSITION_BOTTOM_OR_RIGHT
                : position == STAGE_POSITION_TOP_OR_LEFT;
    }

    /**
     * Returns if split placeholder is at the end of RecentsView. Always returns {@code false} if
     * RecentsView is in portrait or RecentsView isn't shown as grid.
     */
    private boolean isSplitPlaceholderLastInGrid() {
        if (!mActivity.getDeviceProfile().isLandscape || !showAsGrid()
                || !isSplitSelectionActive()) {
            return false;
        }
        @StagePosition int position = mSplitSelectStateController.getActiveSplitStagePosition();
        return mIsRtl
                ? position == STAGE_POSITION_TOP_OR_LEFT
                : position == STAGE_POSITION_BOTTOM_OR_RIGHT;
    }

    /**
     * Reset scroll offset on children of RecentsView when exiting split select.
     */
    public void resetSplitPrimaryScrollOffset() {
        for (int i = 0; i < getTaskViewCount(); i++) {
            requireTaskViewAt(i).setSplitScrollOffsetPrimary(0);
        }
        mClearAllButton.setSplitSelectScrollOffsetPrimary(0);
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

    public void initiateSplitSelect(TaskView taskView) {
        int defaultSplitPosition = mOrientationHandler
                .getDefaultSplitPosition(mActivity.getDeviceProfile());
        initiateSplitSelect(taskView, defaultSplitPosition);
    }

    public void initiateSplitSelect(TaskView taskView, @StagePosition int stagePosition) {
        mSplitHiddenTaskView = taskView;
        Rect initialBounds = new Rect(taskView.getLeft(), taskView.getTop(), taskView.getRight(),
                taskView.getBottom());
        mSplitSelectStateController.setInitialTaskSelect(taskView.getTask(),
                stagePosition, initialBounds);
        mSplitHiddenTaskViewIndex = indexOfChild(taskView);
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            finishRecentsAnimation(true, null);
        }
    }

    public PendingAnimation createSplitSelectInitAnimation() {
        int duration = mActivity.getStateManager().getState().getTransitionDuration(getContext());
        return createTaskDismissAnimation(mSplitHiddenTaskView, true, false, duration,
                true /* dismissingForSplitSelection*/);
    }

    public void confirmSplitSelect(TaskView taskView) {
        mSplitToast.cancel();
        if (!taskView.getTask().isDockable) {
            // Task not split screen supported
            mSplitUnsupportedToast.show();
            return;
        }
        RectF secondTaskStartingBounds = new RectF();
        Rect secondTaskEndingBounds = new Rect();
        // TODO(194414938) starting bounds seem slightly off, investigate
        Rect firstTaskStartingBounds = new Rect();
        Rect firstTaskEndingBounds = mTempRect;
        int duration = mActivity.getStateManager().getState().getTransitionDuration(getContext());
        PendingAnimation pendingAnimation = new PendingAnimation(duration);

        int halfDividerSize = getResources()
                .getDimensionPixelSize(R.dimen.multi_window_task_divider_size) / 2;
        mOrientationHandler.getFinalSplitPlaceholderBounds(halfDividerSize,
                mActivity.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), firstTaskEndingBounds,
                secondTaskEndingBounds);

        mFirstFloatingTaskView.getBoundsOnScreen(firstTaskStartingBounds);
        mFirstFloatingTaskView.addAnimation(pendingAnimation,
                new RectF(firstTaskStartingBounds), firstTaskEndingBounds, mFirstFloatingTaskView,
                false /*fadeWithThumbnail*/);

        mSecondFloatingTaskView = FloatingTaskView.getFloatingTaskView(mActivity,
                taskView, secondTaskStartingBounds);
        mSecondFloatingTaskView.setAlpha(1);
        mSecondFloatingTaskView.addAnimation(pendingAnimation, secondTaskStartingBounds,
                secondTaskEndingBounds, taskView.getThumbnail(),
                true /*fadeWithThumbnail*/);
        pendingAnimation.addEndListener(aBoolean ->
                mSplitSelectStateController.setSecondTaskId(taskView.getTask(),
                aBoolean1 -> RecentsView.this.resetFromSplitSelectionState()));
        mSecondSplitHiddenTaskView = taskView;
        taskView.setVisibility(INVISIBLE);
        pendingAnimation.buildAnim().start();
    }

    /** TODO(b/181707736) More gracefully handle exiting split selection state */
    protected void resetFromSplitSelectionState() {
        if (mSplitHiddenTaskViewIndex == -1) {
            return;
        }
        if (!mActivity.getDeviceProfile().overviewShowAsGrid) {
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
            mSplitHiddenTaskView.setVisibility(VISIBLE);
            mSplitHiddenTaskView = null;
        }
        if (mFirstFloatingTaskView != null) {
            mActivity.getRootView().removeView(mFirstFloatingTaskView);
            mFirstFloatingTaskView = null;
        }
        if (mSecondFloatingTaskView != null) {
            mActivity.getRootView().removeView(mSecondFloatingTaskView);
            mSecondFloatingTaskView = null;
            mSecondSplitHiddenTaskView.setVisibility(VISIBLE);
            mSecondSplitHiddenTaskView = null;
        }
    }

    /**
     * Returns how much additional translation there should be for each of the child TaskViews.
     * Note that the translation can be its primary or secondary dimension.
     */
    public float getSplitSelectTranslation() {
        int splitPosition = getSplitPlaceholder().getActiveSplitStagePosition();
        if (!shouldShiftThumbnailsForSplitSelect()) {
            return 0f;
        }
        PagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        int direction = orientationHandler.getSplitTranslationDirectionFactor(
                splitPosition, mActivity.getDeviceProfile());
        return mActivity.getResources().getDimension(R.dimen.split_placeholder_size) * direction;
    }

    protected void onRotateInSplitSelectionState() {
        mOrientationHandler.getInitialSplitPlaceholderBounds(mSplitPlaceholderSize,
                mActivity.getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), mTempRect);
        mTempRectF.set(mTempRect);
        // TODO(194414938) set correct corner radius
        mFirstFloatingTaskView.updateOrientationHandler(mOrientationHandler);
        mFirstFloatingTaskView.update(mTempRectF, /*progress=*/1f, /*windowRadius=*/0f);

        PagedOrientationHandler orientationHandler = getPagedOrientationHandler();
        Pair<FloatProperty, FloatProperty> taskViewsFloat =
                orientationHandler.getSplitSelectTaskOffset(
                        TASK_PRIMARY_SPLIT_TRANSLATION, TASK_SECONDARY_SPLIT_TRANSLATION,
                        mActivity.getDeviceProfile());
        taskViewsFloat.first.set(this, getSplitSelectTranslation());
        taskViewsFloat.second.set(this, 0f);

        applySplitPrimaryScrollOffset();
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
            if (ENABLE_QUICKSTEP_LIVE_TILE.get()
                    && runningTaskIndex != -1
                    && runningTaskIndex != taskIndex
                    && recentsView.getRemoteTargetHandles() != null) {
                for (RemoteTargetHandle remoteHandle : recentsView.getRemoteTargetHandles()) {
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
            runActionOnRemoteHandles(
                    remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator()
                            .addOverviewToAppAnim(mPendingAnimation, interpolator));
            mPendingAnimation.addOnFrameCallback(this::redrawLiveTile);
        }
        mPendingAnimation.addEndListener(isSuccess -> {
            if (isSuccess) {
                if (tv.getTaskIds()[1] != -1) {
                    // TODO(b/194414938): make this part of the animations instead.
                    TaskViewUtils.createSplitAuxiliarySurfacesAnimator(
                            mRemoteTargetHandles[0].getTransformParams().getTargetSet().nonApps,
                            true /*shown*/, (dividerAnimator) -> {
                                dividerAnimator.start();
                                dividerAnimator.end();
                            });
                }
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
        mRecentsAnimationController = recentsAnimationController;
        mSplitSelectStateController.setRecentsAnimationRunning(true);
        if (recentsAnimationTargets == null || recentsAnimationTargets.apps.length == 0) {
            return;
        }

        RemoteTargetGluer gluer = new RemoteTargetGluer(getContext(), getSizeStrategy());
        mRemoteTargetHandles = gluer.assignTargetsForSplitScreen(recentsAnimationTargets);
        mSplitBoundsConfig = gluer.getStagedSplitBounds();
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
            tvs.setDp(mActivity.getDeviceProfile());
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

    public void finishRecentsAnimation(boolean toRecents, boolean shouldPip,
            @Nullable Runnable onFinishComplete) {
        // TODO(b/197232424#comment#10) Move this back into onRecentsAnimationComplete(). Maybe?
        cleanupRemoteTargets();
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
    public void updateScrollSynchronously() {
        // onMeasure is needed to update child's measured width which is used in scroll calculation,
        // in case TaskView sizes has changed when being focused/unfocused.
        onMeasure(makeMeasureSpec(getMeasuredWidth(), EXACTLY),
                makeMeasureSpec(getMeasuredHeight(), EXACTLY));
        onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());
        updateMinAndMaxScrollX();
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
        return mDisallowScrollToClearAll
                ? mShowAsGridLastOnLayout
                    ? indexOfChild(getLastGridTaskView())
                    : getTaskViewCount() - 1
                : indexOfChild(mClearAllButton);
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

        int clearAllIndex = indexOfChild(mClearAllButton);
        int clearAllScroll = 0;
        int clearAllWidth = mOrientationHandler.getPrimarySize(mClearAllButton);
        if (clearAllIndex != -1 && clearAllIndex < outPageScrolls.length) {
            float scrollDiff = mClearAllButton.getScrollAdjustment(showAsFullscreen, showAsGrid);
            clearAllScroll = newPageScrolls[clearAllIndex] + (int) scrollDiff;
            if (outPageScrolls[clearAllIndex] != clearAllScroll) {
                pageScrollChanged = true;
                outPageScrolls[clearAllIndex] = clearAllScroll;
            }
        }

        final int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            TaskView taskView = requireTaskViewAt(i);
            float scrollDiff = taskView.getScrollAdjustment(showAsFullscreen, showAsGrid);
            int pageScroll = newPageScrolls[i] + (int) scrollDiff;
            if ((mIsRtl && pageScroll < clearAllScroll + clearAllWidth)
                    || (!mIsRtl && pageScroll > clearAllScroll - clearAllWidth)) {
                pageScroll = clearAllScroll + (mIsRtl ? clearAllWidth : -clearAllWidth);
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

        int lastTaskEnd = (mIsRtl
                ? mLastComputedGridSize.left
                : mLastComputedGridSize.right)
                + (mIsRtl ? mPageSpacing : -mPageSpacing);
        int taskEnd = lastTaskEnd + (mIsRtl ? positionDiff : -positionDiff);
        int normalTaskEnd = mIsRtl
                ? mLastComputedGridTaskSize.left
                : mLastComputedGridTaskSize.right;
        return taskEnd - normalTaskEnd;
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
        for (int i = 0; i < taskCount; i++) {
            requireTaskViewAt(i).setOverlayEnabled(i == overlayEnabledPage);
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

        taskView.setShowScreenshot(true);
        for (TaskView.TaskIdAttributeContainer container :
                taskView.getTaskIdAttributeContainers()) {
            if (container == null) {
                continue;
            }

            ThumbnailData td =
                    mRecentsAnimationController.screenshotTask(container.getTask().key.id);
            TaskThumbnailView thumbnailView = container.getThumbnailView();
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
            taskView.setShowScreenshot(true);
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
        if (getCurrentPageTaskView() != null) {
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
            requireTaskViewAt(i).setColorTint(mColorTint, mTintingColor);
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

    public void cleanupRemoteTargets() {
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
            int lastTaskEnd = (mIsRtl
                    ? mLastComputedGridSize.left
                    : mLastComputedGridSize.right)
                    + (mIsRtl ? mPageSpacing : -mPageSpacing);
            int normalTaskEnd = mIsRtl
                    ? mLastComputedGridTaskSize.left
                    : mLastComputedGridTaskSize.right;
            int targetScroll = getScrollForPage(targetPage) + normalTaskEnd - lastTaskEnd;
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

    private void dispatchScrollChanged() {
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTaskViewSimulator().setScroll(getScrollOffset()));
        for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
            mScrollListeners.get(i).onScrollChanged();
        }
    }

    private static class PinnedStackAnimationListener<T extends BaseActivity> extends
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

    public interface TaskLaunchListener {
        void onTaskLaunched();
    }
}
