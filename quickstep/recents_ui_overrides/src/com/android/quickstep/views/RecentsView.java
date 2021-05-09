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

import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.InvariantDeviceProfile.CHANGE_FLAG_ICON_PARAMS;
import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW_MODAL_TASK;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.ACCEL_0_75;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_DISMISS_SWIPE_UP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchController.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.TAP;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ControlType.CLEAR_ALL_BUTTON;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SystemUiController.UI_STATE_OVERVIEW;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_GESTURE_RUNNING;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NON_ZERO_ROTATION;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_RECENTS;
import static com.android.quickstep.views.OverviewActionsView.HIDDEN_NO_TASKS;

import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Property;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PendingAnimation.EndState;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.anim.SpringProperty;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.touch.PagedOrientationHandler.CurveProperties;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.OverScroller;
import com.android.launcher3.util.ResourceBasedOverride.Overrides;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.ViewPool;
import com.android.quickstep.BaseActivityInterface;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RecentsModel.TaskVisualsChangeListener;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.ViewUtils;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SplitScreenBounds;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TransformParams;
import com.android.systemui.plugins.ResourceProvider;
import com.android.systemui.shared.recents.IPinnedStackAnimationListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.LauncherEventUtil;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A list of recent tasks.
 */
@TargetApi(Build.VERSION_CODES.R)
public abstract class RecentsView<T extends StatefulActivity> extends PagedView implements
        Insettable, TaskThumbnailCache.HighResLoadingState.HighResLoadingStateChangedCallback,
        InvariantDeviceProfile.OnIDPChangeListener, TaskVisualsChangeListener,
        SplitScreenBounds.OnChangeListener {

    private static final String TAG = RecentsView.class.getSimpleName();

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

    public static final FloatProperty<RecentsView> ADJACENT_PAGE_OFFSET =
            new FloatProperty<RecentsView>("adjacentPageOffset") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    if (recentsView.mAdjacentPageOffset != v) {
                        recentsView.mAdjacentPageOffset = v;
                        recentsView.updatePageOffsets();
                    }
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mAdjacentPageOffset;
                }
            };

    public static final FloatProperty<RecentsView> TASK_SECONDARY_TRANSLATION =
            new FloatProperty<RecentsView>("taskSecondaryTranslation") {
                @Override
                public void setValue(RecentsView recentsView, float v) {
                    recentsView.setTaskViewsSecondaryTranslation(v);
                }

                @Override
                public Float get(RecentsView recentsView) {
                    return recentsView.mTaskViewsSecondaryTranslation;
                }
            };

    /** Same as normal SCALE_PROPERTY, but also updates page offsets that depend on this scale. */
    public static final FloatProperty<RecentsView> RECENTS_SCALE_PROPERTY =
            new FloatProperty<RecentsView>("recentsScale") {
                @Override
                public void setValue(RecentsView view, float scale) {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                    view.mLastComputedTaskPushOutDistance = null;
                    view.updatePageOffsets();
                    view.setTaskViewsSecondaryTranslation(view.mTaskViewsSecondaryTranslation);
                }

                @Override
                public Float get(RecentsView view) {
                    return view.getScaleX();
                }
            };

    protected RecentsOrientedState mOrientationState;
    protected final BaseActivityInterface mSizeStrategy;
    protected RecentsAnimationController mRecentsAnimationController;
    protected RecentsAnimationTargets mRecentsAnimationTargets;
    protected SurfaceTransactionApplier mSyncTransactionApplier;
    protected int mTaskWidth;
    protected int mTaskHeight;
    protected final Rect mLastComputedTaskSize = new Rect();
    // How much a task that is directly offscreen will be pushed out due to RecentsView scale/pivot.
    protected Float mLastComputedTaskPushOutDistance = null;
    protected boolean mEnableDrawingLiveTile = false;
    protected final Rect mTempRect = new Rect();
    protected final RectF mTempRectF = new RectF();
    private final PointF mTempPointF = new PointF();

    private static final int DISMISS_TASK_DURATION = 300;
    private static final int ADDITION_TASK_DURATION = 200;
    // The threshold at which we update the SystemUI flags when animating from the task into the app
    public static final float UPDATE_SYSUI_FLAGS_THRESHOLD = 0.85f;

    protected final T mActivity;
    private final float mFastFlingVelocity;
    private final RecentsModel mModel;
    private final int mTaskTopMargin;
    private Button mClearAllButton;
    private final Rect mTaskViewDeadZoneRect = new Rect();

    private final ScrollState mScrollState = new ScrollState();
    // Keeps track of the previously known visible tasks for purposes of loading/unloading task data
    private final SparseBooleanArray mHasVisibleTaskData = new SparseBooleanArray();

    private final InvariantDeviceProfile mIdp;

    private final ViewPool<TaskView> mTaskViewPool;

    private final TaskOverlayFactory mTaskOverlayFactory;

    private boolean mDwbToastShown;
    protected boolean mDisallowScrollToClearAll;
    private boolean mOverlayEnabled;
    protected boolean mFreezeViewVisibility;

    private float mAdjacentPageOffset = 0;
    private float mTaskViewsSecondaryTranslation = 0;

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

            UI_HELPER_EXECUTOR.execute(() -> {
                TaskView taskView = getTaskView(taskId);
                if (taskView == null) {
                    return;
                }
                Handler handler = taskView.getHandler();
                if (handler == null) {
                    return;
                }

                // TODO: Add callbacks from AM reflecting adding/removing from the recents list, and
                //       remove all these checks
                Task.TaskKey taskKey = taskView.getTask().key;
                if (PackageManagerWrapper.getInstance().getActivityInfo(taskKey.getComponent(),
                        taskKey.userId) == null) {
                    // The package was uninstalled
                    handler.post(() ->
                            dismissTask(taskView, true /* animate */, false /* removeTask */));
                } else {
                    mModel.findTaskWithId(taskKey.id, (key) -> {
                        if (key == null) {
                            // The task was removed from the recents list
                            handler.post(() -> dismissTask(taskView, true /* animate */,
                                    false /* removeTask */));
                        }
                    });
                }
            });
        }
    };

    private final PinnedStackAnimationListener mIPinnedStackAnimationListener =
            new PinnedStackAnimationListener();

    // Used to keep track of the last requested task list id, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private int mTaskListChangeId = -1;

    // Only valid until the launcher state changes to NORMAL
    protected int mRunningTaskId = -1;
    protected boolean mRunningTaskTileHidden;
    private Task mTmpRunningTask;

    private boolean mRunningTaskIconScaledDown = false;

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
    private boolean mLiveTileOverlayAttached;

    // Keeps track of the index where the first TaskView should be
    private int mTaskViewStartIndex = 0;
    private OverviewActionsView mActionsView;

    private BaseActivity.MultiWindowModeChangedListener mMultiWindowModeChangedListener =
            (inMultiWindowMode) -> {
                if (mOrientationState != null) {
                    mOrientationState.setMultiWindowMode(inMultiWindowMode);
                    setLayoutRotation(mOrientationState.getTouchRotation(),
                            mOrientationState.getDisplayRotation());
                    updateChildTaskOrientations();
                }
                if (!inMultiWindowMode && mOverviewStateEnabled) {
                    // TODO: Re-enable layout transitions for addition of the unpinned task
                    reloadIfNeeded();
                }
            };

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr,
            BaseActivityInterface sizeStrategy) {
        super(context, attrs, defStyleAttr);
        setPageSpacing(getResources().getDimensionPixelSize(R.dimen.recents_page_spacing));
        setEnableFreeScroll(true);
        mSizeStrategy = sizeStrategy;
        mActivity = BaseActivity.fromContext(context);
        mOrientationState = new RecentsOrientedState(
                context, mSizeStrategy, this::animateRecentsRotationInPlace);
        mOrientationState.setRecentsRotation(mActivity.getDisplay().getRotation());

        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);
        mModel = RecentsModel.INSTANCE.get(context);
        mIdp = InvariantDeviceProfile.INSTANCE.get(context);

        mTaskViewPool = new ViewPool<>(context, this, R.layout.task, 20 /* max size */,
                10 /* initial size */);

        mIsRtl = mOrientationHandler.getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mTaskTopMargin = getResources()
                .getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
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
    }

    public OverScroller getScroller() {
        return mScroller;
    }

    public boolean isRtl() {
        return mIsRtl;
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

    /** See {@link #updateThumbnail(int, ThumbnailData, boolean)} */
    public TaskView updateThumbnail(int taskId, ThumbnailData thumbnailData) {
        return updateThumbnail(taskId, thumbnailData, true /* refreshNow */);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTaskStackListenerState();
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile idp) {
        if ((changeFlags & CHANGE_FLAG_ICON_PARAMS) == 0) {
            return;
        }
        mModel.getIconCache().clear();
        unloadVisibleTaskData();
        loadVisibleTaskData();
    }

    public void init(OverviewActionsView actionsView) {
        mActionsView = actionsView;
        mActionsView.updateHiddenFlags(HIDDEN_NO_TASKS, getTaskViewCount() == 0);
        mClearAllButton = (Button) mActionsView.findViewById(R.id.clear_all);
        mClearAllButton.setOnClickListener(this::dismissAllTasks);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
        mModel.getThumbnailCache().getHighResLoadingState().addCallback(this);
        mActivity.addMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = new SurfaceTransactionApplier(this);
        RecentsModel.INSTANCE.get(getContext()).addThumbnailChangeListener(this);
        mIdp.addOnChangeListener(this);
        mIPinnedStackAnimationListener.setActivity(mActivity);
        SystemUiProxy.INSTANCE.get(getContext()).setPinnedStackAnimationListener(
                mIPinnedStackAnimationListener);
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
        ActivityManagerWrapper.getInstance().unregisterTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = null;
        RecentsModel.INSTANCE.get(getContext()).removeThumbnailChangeListener(this);
        mIdp.removeOnChangeListener(this);
        SystemUiProxy.INSTANCE.get(getContext()).setPinnedStackAnimationListener(null);
        SplitScreenBounds.INSTANCE.removeOnChangeListener(this);
        mIPinnedStackAnimationListener.setActivity(null);
        mOrientationState.destroyListeners();
        mTaskOverlayFactory.removeListeners();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);

        // Clear the task data for the removed child if it was visible
        if (child instanceof TaskView) {
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

    private void updateTaskStartIndex(View affectingView) {
        if (!(affectingView instanceof TaskView)) {
            int childCount = getChildCount();

            mTaskViewStartIndex = 0;
            while (mTaskViewStartIndex < childCount
                    && !(getChildAt(mTaskViewStartIndex) instanceof TaskView)) {
                mTaskViewStartIndex++;
            }
        }
    }

    public boolean isTaskViewVisible(TaskView tv) {
        // For now, just check if it's the active task or an adjacent task
        return Math.abs(indexOfChild(tv) - getNextPage()) <= 1;
    }

    public TaskView getTaskView(int taskId) {
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView tv = getTaskViewAt(i);
            if (tv.getTask() != null && tv.getTask().key != null && tv.getTask().key.id == taskId) {
                return tv;
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
        }
    }

    public void onDigitalWellbeingToastShown() {
        if (!mDwbToastShown) {
            mDwbToastShown = true;
            mActivity.getUserEventDispatcher().logActionTip(
                    LauncherEventUtil.VISIBLE,
                    LauncherLogProto.TipType.DWB_TOAST);
        }
    }

    /**
     * Whether the Clear All button is hidden or fully visible. Used to determine if center
     * displayed page is a task or the Clear All button.
     *
     * @return True = Clear All button not fully visible, center page is a task. False = Clear All
     * button fully visible, center page is Clear All button.
     */
    public boolean isClearAllHidden() {
        return true;
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

        TaskView taskView = getCurrentPageTaskView();
        if (taskView != null && taskView.offerTouchToChildren(ev)) {
            // Keep consuming events to pass to delegate
            return true;
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
                        final boolean cameFromNavBar = (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
                        if (!cameFromNavBar
                                && !mTaskViewDeadZoneRect.contains(x + getScrollX(), y)) {
                            mTouchDownToStartHome = true;
                        }
                    }
                }
                mDownX = x;
                mDownY = y;
                break;
        }


        // Do not let touch escape to siblings below this view.
        return isHandlingTouch() || shouldStealTouchFromSiblingsBelow(ev);
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        // Enables swiping to the left or right only if the task overlay is not modal.
        if (!isModal()) {
            super.determineScrollingStart(ev, touchSlopScale);
        }
    }
    protected boolean shouldStealTouchFromSiblingsBelow(MotionEvent ev) {
        return true;
    }

    protected void applyLoadPlan(ArrayList<Task> tasks) {
        if (mPendingAnimation != null) {
            mPendingAnimation.addEndListener((endState) -> applyLoadPlan(tasks));
            return;
        }

        if (tasks == null || tasks.isEmpty()) {
            removeTasksViewsAndClearAllButton();
            onTaskStackUpdated();
            return;
        }

        // Unload existing visible task data
        unloadVisibleTaskData();

        TaskView ignoreResetTaskView =
                mIgnoreResetTaskId == -1 ? null : getTaskView(mIgnoreResetTaskId);

        final int requiredTaskCount = tasks.size();
        if (getTaskViewCount() != requiredTaskCount) {
            for (int i = getTaskViewCount(); i < requiredTaskCount; i++) {
                addView(mTaskViewPool.getView());
            }
            while (getTaskViewCount() > requiredTaskCount) {
                removeView(getChildAt(getChildCount() - 1));
            }
        }

        // Rebind and reset all task views
        for (int i = requiredTaskCount - 1; i >= 0; i--) {
            final int pageIndex = requiredTaskCount - i - 1 + mTaskViewStartIndex;
            final Task task = tasks.get(i);
            final TaskView taskView = (TaskView) getChildAt(pageIndex);
            taskView.bind(task, mOrientationState);
        }

        if (mNextPage == INVALID_PAGE) {
            // Set the current page to the running task, but not if settling on new task.
            TaskView runningTaskView = getRunningTaskView();
            if (runningTaskView != null) {
                setCurrentPage(indexOfChild(runningTaskView));
            } else if (getTaskViewCount() > 0) {
                setCurrentPage(indexOfChild(getTaskViewAt(0)));
            }
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

    private void removeTasksViewsAndClearAllButton() {
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            removeView(getTaskViewAt(i));
        }
    }

    public int getTaskViewCount() {
        int taskViewCount = getChildCount() - mTaskViewStartIndex;
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
        if (mRunningTaskTileHidden) {
            setRunningTaskHidden(mRunningTaskTileHidden);
        }

        // Force apply the scale.
        if (mIgnoreResetTaskId != mRunningTaskId) {
            applyRunningTaskIconScale();
        }

        updateCurveProperties();
        // Update the set of visible task's data
        loadVisibleTaskData();
        setTaskModalness(0);
    }

    public void setFullscreenProgress(float fullscreenProgress) {
        mFullscreenProgress = fullscreenProgress;
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            getTaskViewAt(i).setFullscreenProgress(mFullscreenProgress);
        }
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
        resetPaddingFromTaskSize();
    }

    private void resetPaddingFromTaskSize() {
        DeviceProfile dp = mActivity.getDeviceProfile();
        getTaskSize(mTempRect);
        mTaskWidth = mTempRect.width();
        mTaskHeight = mTempRect.height();

        mTempRect.top -= mTaskTopMargin;
        setPadding(mTempRect.left - mInsets.left, mTempRect.top - mInsets.top,
                dp.widthPx - mInsets.right - mTempRect.right,
                dp.heightPx - mInsets.bottom - mTempRect.bottom);
    }

    public void getTaskSize(Rect outRect) {
        mSizeStrategy.calculateTaskSize(mActivity, mActivity.getDeviceProfile(), outRect,
                mOrientationHandler);
        mLastComputedTaskSize.set(outRect);
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
            loadVisibleTaskData();
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
        mOrientationHandler.getCurveProperties(this, mInsets, mScrollState);
        mScrollState.scrollFromEdge =
                mIsRtl ? mScrollState.scroll : (mMaxScroll - mScrollState.scroll);

        final int pageCount = getPageCount();
        for (int i = 0; i < pageCount; i++) {
            View page = getPageAt(i);
            mScrollState.updateInterpolation(
                    mOrientationHandler.getChildStartWithTranslation(page));
            ((PageCallbacks) page).onPageScroll(mScrollState);
        }
    }

    /**
     * Iterates through all the tasks, and loads the associated task data for newly visible tasks,
     * and unloads the associated task data for tasks that are no longer visible.
     */
    public void loadVisibleTaskData() {
        if (!mOverviewStateEnabled || mTaskListChangeId == -1) {
            // Skip loading visible task data if we've already left the overview state, or if the
            // task list hasn't been loaded yet (the task views will not reflect the task list)
            return;
        }

        int centerPageIndex = getPageNearestToCenterOfScreen();
        int numChildren = getChildCount();
        int lower = Math.max(0, centerPageIndex - 2);
        int upper = Math.min(centerPageIndex + 2, numChildren - 1);

        // Update the task data for the in/visible children
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView taskView = getTaskViewAt(i);
            Task task = taskView.getTask();
            int index = indexOfChild(taskView);
            boolean visible = lower <= index && index <= upper;
            if (visible) {
                if (task == mTmpRunningTask) {
                    // Skip loading if this is the task that we are animating into
                    continue;
                }
                if (!mHasVisibleTaskData.get(task.key.id)) {
                    taskView.onTaskListVisibilityChanged(true /* visible */);
                }
                mHasVisibleTaskData.put(task.key.id, visible);
            } else {
                if (mHasVisibleTaskData.get(task.key.id)) {
                    taskView.onTaskListVisibilityChanged(false /* visible */);
                }
                mHasVisibleTaskData.delete(task.key.id);
            }
        }
    }

    /**
     * Unloads any associated data from the currently visible tasks
     */
    private void unloadVisibleTaskData() {
        for (int i = 0; i < mHasVisibleTaskData.size(); i++) {
            if (mHasVisibleTaskData.valueAt(i)) {
                TaskView taskView = getTaskView(mHasVisibleTaskData.keyAt(i));
                if (taskView != null) {
                    taskView.onTaskListVisibilityChanged(false /* visible */);
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

        mRecentsAnimationController = null;
        mRecentsAnimationTargets = null;

        unloadVisibleTaskData();
        setCurrentPage(0);
        mDwbToastShown = false;
        mActivity.getSystemUiController().updateUiState(UI_STATE_OVERVIEW, 0);
        LayoutUtils.setViewEnabled(mActionsView, true);
        if (mOrientationState.setGestureActive(false)) {
            updateOrientationHandler();
        }
    }

    public @Nullable TaskView getRunningTaskView() {
        return getTaskView(mRunningTaskId);
    }

    public int getRunningTaskIndex() {
        return getTaskIndexForId(mRunningTaskId);
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
        // This needs to be called before the other states are set since it can create the task view
        if (mOrientationState.setGestureActive(true)) {
            updateOrientationHandler();
        }

        showCurrentTask(runningTaskInfo);
        setEnableFreeScroll(false);
        setEnableDrawingLiveTile(false);
        setRunningTaskHidden(true);
        setRunningTaskIconScaledDown(true);
        mActionsView.updateHiddenFlags(HIDDEN_GESTURE_RUNNING, true);
    }

    /**
     * Called only when a swipe-up gesture from an app has completed. Only called after
     * {@link #onGestureAnimationStart} and {@link #onGestureAnimationEnd()}.
     */
    public void onSwipeUpAnimationSuccess() {
        if (getRunningTaskView() != null) {
            float startProgress = ENABLE_QUICKSTEP_LIVE_TILE.get() && mLiveTileOverlayAttached
                    ? LiveTileOverlay.INSTANCE.cancelIconAnimation()
                    : 0f;
            animateUpRunningTaskIconScale(startProgress);
        }
        setSwipeDownShouldLaunchApp(true);
    }

    private void animateRecentsRotationInPlace(int newRotation) {
        if (mOrientationState.canRecentsActivityRotate()) {
            // Let system take care of the rotation
            return;
        }
        AnimatorSet pa = setRecentsChangedOrientation(true);
        pa.addListener(AnimationSuccessListener.forRunnable(() -> {
            setLayoutRotation(newRotation, mOrientationState.getDisplayRotation());
            mActivity.getDragLayer().recreateControllers();
            updateChildTaskOrientations();
            setRecentsChangedOrientation(false).start();
        }));
        pa.start();
    }

    public AnimatorSet setRecentsChangedOrientation(boolean fadeInChildren) {
        getRunningTaskIndex();
        int runningIndex = getCurrentPage();
        AnimatorSet as = new AnimatorSet();
        for (int i = 0; i < getTaskViewCount(); i++) {
            if (runningIndex == i) {
                continue;
            }
            View taskView = getTaskViewAt(i);
            as.play(ObjectAnimator.ofFloat(taskView, View.ALPHA, fadeInChildren ? 0 : 1));
        }
        return as;
    }


    private void updateChildTaskOrientations() {
        for (int i = 0; i < getTaskViewCount(); i++) {
            getTaskViewAt(i).setOrientationState(mOrientationState);
        }
    }

    /**
     * Called when a gesture from an app has finished.
     */
    public void onGestureAnimationEnd() {
        if (mOrientationState.setGestureActive(false)) {
            updateOrientationHandler();
        }

        setOnScrollChangeListener(null);
        setEnableFreeScroll(true);
        setEnableDrawingLiveTile(true);
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            setRunningTaskViewShowScreenshot(true);
        }
        setRunningTaskHidden(false);
        animateUpRunningTaskIconScale();
        animateActionsViewIn();
    }

    /**
     * Returns true if we should add a dummy taskView for the running task id
     */
    protected boolean shouldAddDummyTaskView(RunningTaskInfo runningTaskInfo) {
        return runningTaskInfo != null && getTaskView(runningTaskInfo.taskId) == null;
    }

    /**
     * Creates a task view (if necessary) to represent the task with the {@param runningTaskId}.
     *
     * All subsequent calls to reload will keep the task as the first item until {@link #reset()}
     * is called.  Also scrolls the view to this task.
     */
    public void showCurrentTask(RunningTaskInfo runningTaskInfo) {
        if (shouldAddDummyTaskView(runningTaskInfo)) {
            boolean wasEmpty = getChildCount() == 0;
            // Add an empty view for now until the task plan is loaded and applied
            final TaskView taskView = mTaskViewPool.getView();
            addView(taskView, mTaskViewStartIndex);
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
        setCurrentTask(runningTaskInfo == null ? -1 : runningTaskInfo.taskId);
        setCurrentPage(getRunningTaskIndex());
        setRunningTaskViewShowScreenshot(false);
        setRunningTaskHidden(runningTaskTileHidden);

        // Reload the task list
        mTaskListChangeId = mModel.getTasks(this::applyLoadPlan);
    }

    /**
     * Sets the running task id, cleaning up the old running task if necessary.
     * @param runningTaskId
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
            TaskView runningTaskView = getRunningTaskView();
            if (runningTaskView != null) {
                runningTaskView.setShowScreenshot(showScreenshot);
            }
        }
    }

    public void showNextTask() {
        TaskView runningTaskView = getRunningTaskView();
        if (runningTaskView == null) {
            // Launch the first task
            if (getTaskViewCount() > 0) {
                getTaskViewAt(0).launchTask(true);
            }
        } else {
            if (getNextTaskView() != null) {
                getNextTaskView().launchTask(true);
            } else {
                runningTaskView.launchTask(true);
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
        mActionsView.updateHiddenFlags(HIDDEN_GESTURE_RUNNING, false);
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                mActionsView.getVisibilityAlpha(), MultiValueAlpha.VALUE, 0, 1);
        anim.setDuration(TaskView.SCALE_ICON_DURATION);
        anim.start();
    }

    public void animateUpRunningTaskIconScale() {
        animateUpRunningTaskIconScale(0);
    }

    public void animateUpRunningTaskIconScale(float startProgress) {
        mRunningTaskIconScaledDown = false;
        TaskView firstTask = getRunningTaskView();
        if (firstTask != null) {
            firstTask.animateIconScaleAndDimIntoView();
            firstTask.setIconScaleAnimStartProgress(startProgress);
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
                        disableLayoutTransitions();
                    }

                }
            });
        }
        setLayoutTransition(mLayoutTransition);
    }

    private void disableLayoutTransitions() {
        setLayoutTransition(null);
    }

    public void setSwipeDownShouldLaunchApp(boolean swipeDownShouldLaunchApp) {
        mSwipeDownShouldLaunchApp = swipeDownShouldLaunchApp;
    }

    public boolean shouldSwipeDownLaunchApp() {
        return mSwipeDownShouldLaunchApp;
    }

    public interface PageCallbacks {

        /**
         * Updates the page UI based on scroll params.
         */
        default void onPageScroll(ScrollState scrollState) {}
    }

    public static class ScrollState extends CurveProperties {

        /**
         * The progress from 0 to 1, where 0 is the center
         * of the screen and 1 is the edge of the screen.
         */
        public float linearInterpolation;

        /**
         * The amount by which all the content is scrolled relative to the end of the list.
         */
        public float scrollFromEdge;

        /**
         * Updates linearInterpolation for the provided child position
         */
        public void updateInterpolation(float childStart) {
            float pageCenter = childStart + halfPageSize;
            float distanceFromScreenCenter = screenCenter - pageCenter;
            // How far the page has to move from the center to be offscreen, taking into account
            // the EDGE_SCALE_DOWN_FACTOR that will be applied at that position.
            float distanceToReachEdge = halfScreenSize
                    + halfPageSize * (1 - TaskView.EDGE_SCALE_DOWN_FACTOR);
            linearInterpolation = Math.min(1,
                    Math.abs(distanceFromScreenCenter) / distanceToReachEdge);
        }
    }

    public void setIgnoreResetTask(int taskId) {
        mIgnoreResetTaskId = taskId;
    }

    public void clearIgnoreResetTask(int taskId) {
        if (mIgnoreResetTaskId == taskId) {
            mIgnoreResetTaskId = -1;
        }
    }

    private void addDismissedTaskAnimations(View taskView, long duration, PendingAnimation anim) {
        // Use setFloat instead of setViewAlpha as we want to keep the view visible even when it's
        // alpha is set to 0 so that it can be recycled in the view pool properly
        anim.setFloat(taskView, VIEW_ALPHA, 0, ACCEL_2);
        FloatProperty<View> secondaryViewTranslate =
            mOrientationHandler.getSecondaryViewTranslate();
        int secondaryTaskDimension = mOrientationHandler.getSecondaryDimension(taskView);
        int verticalFactor = mOrientationHandler.getSecondaryTranslationDirectionFactor();

        ResourceProvider rp = DynamicResource.provider(mActivity);
        SpringProperty sp = new SpringProperty(SpringProperty.FLAG_CAN_SPRING_ON_START)
                .setDampingRatio(rp.getFloat(R.dimen.dismiss_task_trans_y_damping_ratio))
                .setStiffness(rp.getFloat(R.dimen.dismiss_task_trans_y_stiffness));

        anim.add(ObjectAnimator.ofFloat(taskView, secondaryViewTranslate,
                verticalFactor * secondaryTaskDimension).setDuration(duration), LINEAR, sp);
    }

    private void removeTask(TaskView taskView, int index, EndState endState) {
        if (taskView.getTask() != null) {
            ActivityManagerWrapper.getInstance().removeTask(taskView.getTask().key.id);
            ComponentKey compKey = TaskUtils.getLaunchComponentKeyForTask(taskView.getTask().key);
            mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(
                    endState.logAction, Direction.UP, index, compKey);
            mActivity.getStatsLogManager().logger().withItemInfo(taskView.getItemInfo())
                    .log(LAUNCHER_TASK_DISMISS_SWIPE_UP);
        }
    }

    public PendingAnimation createTaskDismissAnimation(TaskView taskView, boolean animateTaskView,
            boolean shouldRemoveTask, long duration) {
        if (mPendingAnimation != null) {
            mPendingAnimation.finish(false, Touch.SWIPE);
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

        boolean needsCurveUpdates = false;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child == taskView) {
                if (animateTaskView) {
                    addDismissedTaskAnimations(taskView, duration, anim);
                }
            } else {
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
                    Property translationProperty = mOrientationHandler.getPrimaryViewTranslate();

                    ResourceProvider rp = DynamicResource.provider(mActivity);
                    SpringProperty sp = new SpringProperty(SpringProperty.FLAG_CAN_SPRING_ON_END)
                            .setDampingRatio(
                                    rp.getFloat(R.dimen.dismiss_task_trans_x_damping_ratio))
                            .setStiffness(rp.getFloat(R.dimen.dismiss_task_trans_x_stiffness));
                    anim.add(ObjectAnimator.ofFloat(child, translationProperty, scrollDiff)
                            .setDuration(duration), ACCEL, sp);
                    needsCurveUpdates = true;
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
        mPendingAnimation.addEndListener(new Consumer<EndState>() {
            @Override
            public void accept(EndState endState) {
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() &&
                        taskView.isRunningTask() && endState.isSuccess) {
                    finishRecentsAnimation(true /* toHome */, () -> onEnd(endState));
                } else {
                    onEnd(endState);
                }
            }

            @SuppressWarnings("WrongCall")
            private void onEnd(EndState endState) {
                if (endState.isSuccess) {
                    if (shouldRemoveTask) {
                        removeTask(taskView, draggedIndex, endState);
                    }

                    int pageToSnapTo = mCurrentPage;
                    if (draggedIndex < pageToSnapTo ||
                            pageToSnapTo == (getTaskViewCount() - 1)) {
                        pageToSnapTo -= 1;
                    }
                    removeViewInLayout(taskView);

                    if (getTaskViewCount() == 0) {
                        startHome();
                    } else {
                        snapToPageImmediately(pageToSnapTo);
                    }
                    // Update the layout synchronously so that the position of next view is
                    // immediately available.
                    onLayout(false /*  changed */, getLeft(), getTop(), getRight(), getBottom());
                }
                resetTaskVisuals();
                mPendingAnimation = null;
            }
        });
        return anim;
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
        mPendingAnimation.addEndListener((endState) -> {
            if (endState.isSuccess) {
                // Remove all the task views now
                ActivityManagerWrapper.getInstance().removeAllRecentTasks();
                removeTasksViewsAndClearAllButton();
                startHome();
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

    protected void runDismissAnimation(PendingAnimation pendingAnim) {
        AnimatorPlaybackController controller = pendingAnim.createPlaybackController();
        controller.dispatchOnStart();
        controller.setEndAction(() -> pendingAnim.finish(true, Touch.SWIPE));
        controller.getAnimationPlayer().setInterpolator(FAST_OUT_SLOW_IN);
        controller.start();
    }

    public void dismissTask(TaskView taskView, boolean animateTaskView, boolean removeTask) {
        runDismissAnimation(createTaskDismissAnimation(taskView, animateTaskView, removeTask,
                DISMISS_TASK_DURATION));
    }

    @SuppressWarnings("unused")
    private void dismissAllTasks(View view) {
        runDismissAnimation(createAllTasksDismissAnimation(DISMISS_TASK_DURATION));
        mActivity.getUserEventDispatcher().logActionOnControl(TAP, CLEAR_ALL_BUTTON);
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
        int alphaInt = Math.round(alpha * 255);
        mEmptyMessagePaint.setAlpha(alphaInt);
        mEmptyIcon.setAlpha(alphaInt);
        mActionsView.getContentAlpha().setValue(mContentAlpha);

        if (alpha > 0) {
            setVisibility(VISIBLE);
        } else if (!mFreezeViewVisibility) {
            setVisibility(GONE);
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
                setVisibility(mContentAlpha > 0 ? VISIBLE : GONE);
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
        if (mOrientationState.setRecentsRotation(mActivity.getDisplay().getRotation())) {
            updateOrientationHandler();
        }
        // If overview is in modal state when rotate, reset it to overview state without running
        // animation.
        if (mActivity.isInState(OVERVIEW_MODAL_TASK)) {
            mActivity.getStateManager().goToState(LauncherState.OVERVIEW, false);
            resetModalVisuals();
        }
    }

    public void setLayoutRotation(int touchRotation, int displayRotation) {
        if (mOrientationState.update(touchRotation, displayRotation)) {
            updateOrientationHandler();
        }
    }

    private void updateOrientationHandler() {
        mOrientationHandler = mOrientationState.getOrientationHandler();
        mIsRtl = mOrientationHandler.getRecentsRtlSetting(getResources());
        setLayoutDirection(mIsRtl
                ? View.LAYOUT_DIRECTION_RTL
                : View.LAYOUT_DIRECTION_LTR);
        mActivity.getDragLayer().recreateControllers();
        boolean isInLandscape = mOrientationState.getTouchRotation() != ROTATION_0
                || mOrientationState.getRecentsActivityRotation() != ROTATION_0;
        mActionsView.updateHiddenFlags(HIDDEN_NON_ZERO_ROTATION,
                !mOrientationState.canRecentsActivityRotate() && isInLandscape);
        updateChildTaskOrientations();
        resetPaddingFromTaskSize();
        requestLayout();
        // Reapply the current page to update page scrolls.
        setCurrentPage(mCurrentPage);
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
        getPagedViewOrientedState().getFullScreenScaleAndPivot(
                mTempRect, mActivity.getDeviceProfile(), mTempPointF);
        setPivotX(mTempPointF.x);
        setPivotY(mTempPointF.y);
        setTaskModalness(mTaskModalness);
        mLastComputedTaskPushOutDistance = null;
        updatePageOffsets();
        setImportantForAccessibility(isModal() ? IMPORTANT_FOR_ACCESSIBILITY_NO
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void updatePageOffsets() {
        float offset = mAdjacentPageOffset;
        float modalOffset = ACCEL_0_75.getInterpolation(mTaskModalness);
        if (mIsRtl) {
            offset = -offset;
            modalOffset = -modalOffset;
        }
        int count = getChildCount();

        TaskView runningTask = mRunningTaskId == -1 || !mRunningTaskTileHidden
                ? null : getTaskView(mRunningTaskId);
        int midpoint = runningTask == null ? -1 : indexOfChild(runningTask);
        int modalMidpoint = getCurrentPage();

        float midpointOffsetSize = 0;
        float leftOffsetSize = midpoint - 1 >= 0
                ? -getOffsetSize(midpoint - 1, midpoint, offset)
                : 0;
        float rightOffsetSize = midpoint + 1 < count
                ? getOffsetSize(midpoint + 1, midpoint, offset)
                : 0;

        float modalMidpointOffsetSize = 0;
        float modalLeftOffsetSize = modalMidpoint - 1 >= 0
                ? -getOffsetSize(modalMidpoint - 1, modalMidpoint, modalOffset)
                : 0;
        float modalRightOffsetSize = modalMidpoint + 1 < count
                ? getOffsetSize(modalMidpoint + 1, modalMidpoint, modalOffset)
                : 0;

        for (int i = 0; i < count; i++) {
            float translation = i == midpoint
                    ? midpointOffsetSize
                    : i < midpoint
                            ? leftOffsetSize
                            : rightOffsetSize;
            float modalTranslation = i == modalMidpoint
                    ? modalMidpointOffsetSize
                    : i < modalMidpoint
                            ? modalLeftOffsetSize
                            : modalRightOffsetSize;
            float totalTranslation = translation + modalTranslation;
            mOrientationHandler.getPrimaryViewTranslate().set(getChildAt(i),
                    totalTranslation * mOrientationHandler.getPrimaryTranslationDirectionFactor());
        }
        updateCurveProperties();
    }

    /**
     * Computes the distance to offset the given child such that it is completely offscreen when
     * translating away from the given midpoint.
     * @param offsetProgress From 0 to 1 where 0 means no offset and 1 means offset offscreen.
     */
    private float getOffsetSize(int childIndex, int midpointIndex, float offsetProgress) {
        if (offsetProgress == 0) {
            // Don't bother calculating everything below if we won't offset anyway.
            return 0;
        }
        // First, get the position of the task relative to the midpoint. If there is no midpoint
        // then we just use the normal (centered) task position.
        mTempRectF.set(mLastComputedTaskSize);
        RectF taskPosition = mTempRectF;
        float desiredLeft = getWidth();
        float distanceToOffscreen = desiredLeft - taskPosition.left;
        // Used to calculate the scale of the task view based on its new offset.
        float centerToOffscreenProgress = Math.abs(offsetProgress);
        if (midpointIndex > -1) {
            // When there is a midpoint reference task, adjacent tasks have less distance to travel
            // to reach offscreen. Offset the task position to the task's starting point.
            View child = getChildAt(childIndex);
            View midpointChild = getChildAt(midpointIndex);
            int distanceFromMidpoint = Math.abs(mOrientationHandler.getChildStart(child)
                    - mOrientationHandler.getChildStart(midpointChild)
                    + getDisplacementFromScreenCenter(midpointIndex));
            taskPosition.offset(distanceFromMidpoint, 0);
            centerToOffscreenProgress = Utilities.mapRange(centerToOffscreenProgress,
                    distanceFromMidpoint / distanceToOffscreen, 1);
        }
        // Find the task's scale based on its offscreen progress, then see how far it still needs to
        // move to be completely offscreen.
        Utilities.scaleRectFAboutCenter(taskPosition,
                TaskView.getCurveScaleForInterpolation(centerToOffscreenProgress));
        distanceToOffscreen = desiredLeft - taskPosition.left;
        // Finally, we need to account for RecentsView scale, because it moves tasks based on its
        // pivot. To do this, we move the task position to where it would be offscreen at scale = 1
        // (computed above), then we apply the scale via getMatrix() to determine how much that
        // moves the task from its desired position, and adjust the computed distance accordingly.
        if (mLastComputedTaskPushOutDistance == null) {
            taskPosition.offsetTo(desiredLeft, 0);
            getMatrix().mapRect(taskPosition);
            mLastComputedTaskPushOutDistance = (taskPosition.left - desiredLeft) / getScaleX();
        }
        distanceToOffscreen -= mLastComputedTaskPushOutDistance;
        return distanceToOffscreen * offsetProgress;
    }

    private void setTaskViewsSecondaryTranslation(float translation) {
        mTaskViewsSecondaryTranslation = translation;
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView task = getTaskViewAt(i);
            mOrientationHandler.getSecondaryViewTranslate().set(task, translation / getScaleY());
        }
    }

    /**
     * TODO: Do not assume motion across X axis for adjacent page
     */
    public float getPageOffsetScale() {
        return Math.max(getWidth(), 1);
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

    private void updateDeadZoneRects() {
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
        if (launchingCenterTask) {
            RecentsView recentsView = tv.getRecentsView();
            anim.play(ObjectAnimator.ofFloat(recentsView, RECENTS_SCALE_PROPERTY, toScale));
            anim.play(ObjectAnimator.ofFloat(recentsView, FULLSCREEN_PROGRESS, 1));
        } else {
            // We are launching an adjacent task, so parallax the center and other adjacent task.
            float displacementX = tv.getWidth() * (toScale - tv.getCurveScale());
            anim.play(ObjectAnimator.ofFloat(getPageAt(centerTaskIndex), TRANSLATION_X,
                    mIsRtl ? -displacementX : displacementX));

            int otherAdjacentTaskIndex = centerTaskIndex + (centerTaskIndex - taskIndex);
            if (otherAdjacentTaskIndex >= 0 && otherAdjacentTaskIndex < getPageCount()) {
                anim.play(new PropertyListBuilder()
                        .translationX(mIsRtl ? -displacementX : displacementX)
                        .scale(1)
                        .build(getPageAt(otherAdjacentTaskIndex)));
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

        int targetSysUiFlags = tv.getThumbnail().getSysUiStatusNavFlags();
        final boolean[] passedOverviewThreshold = new boolean[] {false};
        ValueAnimator progressAnim = ValueAnimator.ofFloat(0, 1);
        progressAnim.addUpdateListener(animator -> {
            // Once we pass a certain threshold, update the sysui flags to match the target
            // tasks' flags
            mActivity.getSystemUiController().updateUiState(UI_STATE_OVERVIEW,
                    animator.getAnimatedFraction() > UPDATE_SYSUI_FLAGS_THRESHOLD
                            ? targetSysUiFlags
                            : 0);

            onTaskLaunchAnimationUpdate(animator.getAnimatedFraction(), tv);

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
        mPendingAnimation.addEndListener((endState) -> {
            if (endState.isSuccess) {
                Consumer<Boolean> onLaunchResult = (result) -> {
                    onTaskLaunchAnimationEnd(result);
                    if (!result) {
                        tv.notifyTaskLaunchFailed(TAG);
                    }
                };
                tv.launchTask(false, onLaunchResult, getHandler());
                Task task = tv.getTask();
                if (task != null) {
                    mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(
                            endState.logAction, Direction.DOWN, indexOfChild(tv),
                            TaskUtils.getLaunchComponentKeyForTask(task.key));
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

    protected void onTaskLaunchAnimationUpdate(float progress, TaskView tv) {
    }

    public abstract boolean shouldUseMultiWindowTaskSizeStrategy();

    protected void onTaskLaunchAnimationEnd(boolean success) {
        if (success) {
            resetTaskVisuals();
        }
    }

    /**
     * Called when task activity is launched
     */
    public void onTaskLaunched(Task task){ }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        loadVisibleTaskData();
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

    public void redrawLiveTile(boolean mightNeedToRefill) { }

    // TODO: To be removed in a follow up CL
    public void setRecentsAnimationTargets(RecentsAnimationController recentsAnimationController,
            RecentsAnimationTargets recentsAnimationTargets) {
        mRecentsAnimationController = recentsAnimationController;
        mRecentsAnimationTargets = recentsAnimationTargets;
    }

    public void setLiveTileOverlayAttached(boolean liveTileOverlayAttached) {
        mLiveTileOverlayAttached = liveTileOverlayAttached;
    }

    public void updateLiveTileIcon(Drawable icon) {
        if (mLiveTileOverlayAttached) {
            LiveTileOverlay.INSTANCE.setIcon(icon);
        }
    }

    public void finishRecentsAnimation(boolean toRecents, Runnable onFinishComplete) {
        if (mRecentsAnimationController == null) {
            if (onFinishComplete != null) {
                onFinishComplete.run();
            }
            return;
        }

        mRecentsAnimationController.finish(toRecents, () -> {
            if (onFinishComplete != null) {
                onFinishComplete.run();
                // After we finish the recents animation, the current task id should be correctly
                // reset so that when the task is launched from Overview later, it goes through the
                // flow of starting a new task instead of finishing recents animation to app. A
                // typical example of this is (1) user swipes up from app to Overview (2) user
                // taps on QSB (3) user goes back to Overview and launch the most recent task.
                setCurrentTask(-1);
            }
        });
    }

    public void setDisallowScrollToClearAll(boolean disallowScrollToClearAll) {
        if (mDisallowScrollToClearAll != disallowScrollToClearAll) {
            mDisallowScrollToClearAll = disallowScrollToClearAll;
            updateMinAndMaxScrollX();
        }
    }

    @Override
    protected int computeMinScroll() {
        if (getTaskViewCount() > 0) {
            if (mDisallowScrollToClearAll) {
                // We aren't showing the clear all button,
                // so use the leftmost task as the min scroll.
                if (mIsRtl) {
                    return getScrollForPage(indexOfChild(getTaskViewAt(getTaskViewCount() - 1)));
                }
                return getScrollForPage(mTaskViewStartIndex);
            }
            if (mIsRtl) {
                return getScrollForPage(indexOfChild(getTaskViewAt(getTaskViewCount() - 1)) + 1);
            }
            return getScrollForPage(mTaskViewStartIndex);
        }
        return super.computeMinScroll();
    }

    @Override
    protected int computeMaxScroll() {
        if (getTaskViewCount() > 0) {
            if (mDisallowScrollToClearAll) {
                // We aren't showing the clear all button,
                // so use the rightmost task as the min scroll.
                if (mIsRtl) {
                    return getScrollForPage(mTaskViewStartIndex);
                }
                return getScrollForPage(indexOfChild(getTaskViewAt(getTaskViewCount() - 1)));
            }
            if (mIsRtl) {
                return getScrollForPage(mTaskViewStartIndex);
            }
            return getScrollForPage(indexOfChild(getTaskViewAt(getTaskViewCount() - 1)) + 1);
        }
        return super.computeMaxScroll();
    }

    @Override
    protected boolean onOverscroll(int amount) {
        // overscroll should only be accepted on -1 direction (for clear all button)
        if ((amount > 0 && !mIsRtl) || (amount < 0 && mIsRtl)) return false;
        return super.onOverscroll(amount);
    }

    /**
     * @return How many pixels the running task is offset on the currently laid out dominant axis.
     */
    public int getScrollOffset() {
        return getScrollOffset(getRunningTaskIndex());
    }

    /**
     * @return How many pixels the page is offset on the currently laid out dominant axis.
     */
    public int getScrollOffset(int pageIndex) {
        if (pageIndex == -1) {
            return 0;
        }
        // Unbound the scroll (due to overscroll) if the adjacent tasks are offset away from it.
        // This allows the page to move freely, given there's no visual indication why it shouldn't.
        int boundedScroll = mOrientationHandler.getPrimaryScroll(this);
        int unboundedScroll = getUnboundedScroll();
        float unboundedProgress = mAdjacentPageOffset;
        int scroll = Math.round(unboundedScroll * unboundedProgress
                + boundedScroll * (1 - unboundedProgress));
        return getScrollForPage(pageIndex) - scroll;
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

    public TransformParams getLiveTileParams(
            boolean mightNeedToRefill) {
        return null;
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

    /** If it's in the live tile mode, switch the running task into screenshot mode. */
    public void switchToScreenshot(ThumbnailData thumbnailData, Runnable onFinishRunnable) {
        TaskView taskView = getRunningTaskView();
        if (taskView != null) {
            taskView.setShowScreenshot(true);
            if (thumbnailData != null) {
                taskView.getThumbnail().setThumbnail(taskView.getTask(), thumbnailData);
            } else {
                taskView.getThumbnail().refresh();
            }
            ViewUtils.postDraw(taskView, onFinishRunnable);
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
    }

    @Nullable
    protected DepthController getDepthController() {
        return null;
    }

    @Override
    public void onSecondaryWindowBoundsChanged() {
        // Invalidate the task view size
        setInsets(mInsets);
        requestLayout();
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
     * Used to register callbacks for when our empty message state changes.
     *
     * @see #setOnEmptyMessageUpdatedListener(OnEmptyMessageUpdatedListener)
     * @see #updateEmptyMessage()
     */
    public interface OnEmptyMessageUpdatedListener {
        /** @param isEmpty Whether RecentsView is empty (i.e. has no children) */
        void onEmptyMessageUpdated(boolean isEmpty);
    }

    private static class PinnedStackAnimationListener<T extends BaseActivity> extends
            IPinnedStackAnimationListener.Stub {
        private T mActivity;

        public void setActivity(T activity) {
            mActivity = activity;
        }

        @Override
        public void onPinnedStackAnimationStarted() {
            // Needed for activities that auto-enter PiP, which will not trigger a remote
            // animation to be created
            mActivity.clearForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
        }
    }
}
