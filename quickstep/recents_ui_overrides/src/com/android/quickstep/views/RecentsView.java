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

import static androidx.dynamicanimation.animation.DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS;
import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.InvariantDeviceProfile.CHANGE_FLAG_ICON_PARAMS;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.config.FeatureFlags.QUICKSTEP_SPRINGS;
import static com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchController.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.TAP;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ControlType.CLEAR_ALL_BUTTON;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SystemUiController.UI_STATE_OVERVIEW;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.anim.SpringObjectAnimator;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.OverScroller;
import com.android.launcher3.util.PendingAnimation;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.ViewPool;
import com.android.quickstep.RecentsAnimationWrapper;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RecentsModel.TaskThumbnailChangeListener;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.LauncherEventUtil;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A list of recent tasks.
 */
@TargetApi(Build.VERSION_CODES.P)
public abstract class RecentsView<T extends BaseActivity> extends PagedView implements Insettable,
        TaskThumbnailCache.HighResLoadingState.HighResLoadingStateChangedCallback,
        InvariantDeviceProfile.OnIDPChangeListener, TaskThumbnailChangeListener {

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

    protected RecentsAnimationWrapper mRecentsAnimationWrapper;
    protected ClipAnimationHelper mClipAnimationHelper;
    protected SyncRtSurfaceTransactionApplierCompat mSyncTransactionApplier;
    protected int mTaskWidth;
    protected int mTaskHeight;
    protected boolean mEnableDrawingLiveTile = false;
    protected final Rect mTempRect = new Rect();
    protected final RectF mTempRectF = new RectF();

    private static final int DISMISS_TASK_DURATION = 300;
    private static final int ADDITION_TASK_DURATION = 200;
    // The threshold at which we update the SystemUI flags when animating from the task into the app
    public static final float UPDATE_SYSUI_FLAGS_THRESHOLD = 0.85f;

    protected final T mActivity;
    private final float mFastFlingVelocity;
    private final RecentsModel mModel;
    private final int mTaskTopMargin;
    private final ClearAllButton mClearAllButton;
    private final Rect mClearAllButtonDeadZoneRect = new Rect();
    private final Rect mTaskViewDeadZoneRect = new Rect();
    protected final ClipAnimationHelper mTempClipAnimationHelper;

    private final ScrollState mScrollState = new ScrollState();
    // Keeps track of the previously known visible tasks for purposes of loading/unloading task data
    private final SparseBooleanArray mHasVisibleTaskData = new SparseBooleanArray();

    private final InvariantDeviceProfile mIdp;

    private final ViewPool<TaskView> mTaskViewPool;

    private boolean mDwbToastShown;
    protected boolean mDisallowScrollToClearAll;
    private boolean mOverlayEnabled;
    private boolean mFreezeViewVisibility;

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

        @Override
        public void onPinnedStackAnimationStarted() {
            // Needed for activities that auto-enter PiP, which will not trigger a remote
            // animation to be created
            mActivity.clearForceInvisibleFlag(STATE_HANDLER_INVISIBILITY_FLAGS);
        }
    };

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

    // Keeps track of task id whose visual state should not be reset
    private int mIgnoreResetTaskId = -1;

    // Variables for empty state
    private final Drawable mEmptyIcon;
    private final CharSequence mEmptyMessage;
    private final TextPaint mEmptyMessagePaint;
    private final Point mLastMeasureSize = new Point();
    private final int mEmptyMessagePadding;
    private boolean mShowEmptyMessage;
    private Layout mEmptyTextLayout;
    private LiveTileOverlay mLiveTileOverlay;

    // Keeps track of the index where the first TaskView should be
    private int mTaskViewStartIndex = 0;

    private BaseActivity.MultiWindowModeChangedListener mMultiWindowModeChangedListener =
            (inMultiWindowMode) -> {
        if (!inMultiWindowMode && mOverviewStateEnabled) {
            // TODO: Re-enable layout transitions for addition of the unpinned task
            reloadIfNeeded();
        }
    };

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setPageSpacing(getResources().getDimensionPixelSize(R.dimen.recents_page_spacing));
        setEnableFreeScroll(true);

        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);
        mActivity = (T) BaseActivity.fromContext(context);
        mModel = RecentsModel.INSTANCE.get(context);
        mIdp = InvariantDeviceProfile.INSTANCE.get(context);
        mTempClipAnimationHelper = new ClipAnimationHelper(context);

        mClearAllButton = (ClearAllButton) LayoutInflater.from(context)
                .inflate(R.layout.overview_clear_all_button, this, false);
        mClearAllButton.setOnClickListener(this::dismissAllTasks);
        mTaskViewPool = new ViewPool<>(context, this, R.layout.task, 20 /* max size */,
                10 /* initial size */);

        mIsRtl = !Utilities.isRtl(getResources());
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

    public TaskView updateThumbnail(int taskId, ThumbnailData thumbnailData) {
        TaskView taskView = getTaskView(taskId);
        if (taskView != null) {
            taskView.getThumbnail().setThumbnail(taskView.getTask(), thumbnailData);
        }
        return taskView;
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
        reset();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
        mModel.getThumbnailCache().getHighResLoadingState().addCallback(this);
        mActivity.addMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackListener);
        mSyncTransactionApplier = new SyncRtSurfaceTransactionApplierCompat(this);
        RecentsModel.INSTANCE.get(getContext()).addThumbnailChangeListener(this);
        mIdp.addOnChangeListener(this);
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
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);

        // Clear the task data for the removed child if it was visible
        if (child instanceof TaskView) {
            TaskView taskView = (TaskView) child;
            mHasVisibleTaskData.delete(taskView.getTask().key.id);
            mTaskViewPool.recycle(taskView);
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
    }

    public void onDigitalWellbeingToastShown() {
        if (!mDwbToastShown) {
            mDwbToastShown = true;
            mActivity.getUserEventDispatcher().logActionTip(
                    LauncherEventUtil.VISIBLE,
                    LauncherLogProto.TipType.DWB_TOAST);
        }
    }

    @Override
    protected void onPageEndTransition() {
        super.onPageEndTransition();
        if (getNextPage() > 0) {
            setSwipeDownShouldLaunchApp(true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);
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
                if (!isHandlingTouch()) {
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


        // Do not let touch escape to siblings below this view.
        return isHandlingTouch() || shouldStealTouchFromSiblingsBelow(ev);
    }

    protected boolean shouldStealTouchFromSiblingsBelow(MotionEvent ev) {
        return true;
    }

    protected void applyLoadPlan(ArrayList<Task> tasks) {
        if (mPendingAnimation != null) {
            mPendingAnimation.addEndListener((onEndListener) -> applyLoadPlan(tasks));
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
            taskView.bind(task);
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

    protected void onTaskStackUpdated() { }

    public void resetTaskVisuals() {
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView taskView = getTaskViewAt(i);
            if (mIgnoreResetTaskId != taskView.getTask().key.id) {
                taskView.resetVisualProperties();
                taskView.setStableAlpha(mContentAlpha);
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
    }

    public void setFullscreenProgress(float fullscreenProgress) {
        mFullscreenProgress = fullscreenProgress;
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            getTaskViewAt(i).setFullscreenProgress(mFullscreenProgress);
        }
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
        DeviceProfile dp = mActivity.getDeviceProfile();
        getTaskSize(dp, mTempRect);
        mTaskWidth = mTempRect.width();
        mTaskHeight = mTempRect.height();

        mTempRect.top -= mTaskTopMargin;
        setPadding(mTempRect.left - mInsets.left, mTempRect.top - mInsets.top,
                dp.widthPx - mInsets.right - mTempRect.right,
                dp.heightPx - mInsets.bottom - mTempRect.bottom);
    }

    protected abstract void getTaskSize(DeviceProfile dp, Rect outRect);

    public void getTaskSize(Rect outRect) {
        getTaskSize(mActivity.getDeviceProfile(), outRect);
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
        int scrollX = getScrollX();
        final int halfPageWidth = getNormalChildWidth() / 2;
        final int screenCenter = mInsets.left + getPaddingLeft() + scrollX + halfPageWidth;
        final int halfScreenWidth = getMeasuredWidth() / 2;
        final int pageSpacing = mPageSpacing;
        mScrollState.scrollFromEdge = mIsRtl ? scrollX : (mMaxScrollX - scrollX);

        final int pageCount = getPageCount();
        for (int i = 0; i < pageCount; i++) {
            View page = getPageAt(i);
            float pageCenter = page.getLeft() + page.getTranslationX() + halfPageWidth;
            float distanceFromScreenCenter = screenCenter - pageCenter;
            float distanceToReachEdge = halfScreenWidth + halfPageWidth + pageSpacing;
            mScrollState.linearInterpolation = Math.min(1,
                    Math.abs(distanceFromScreenCenter) / distanceToReachEdge);
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

    public void reset() {
        setCurrentTask(-1);
        mIgnoreResetTaskId = -1;
        mTaskListChangeId = -1;

        mRecentsAnimationWrapper = null;
        mClipAnimationHelper = null;

        unloadVisibleTaskData();
        setCurrentPage(0);
        mDwbToastShown = false;
        mActivity.getSystemUiController().updateUiState(UI_STATE_OVERVIEW, 0);
    }

    public @Nullable TaskView getRunningTaskView() {
        return getTaskView(mRunningTaskId);
    }

    public int getRunningTaskIndex() {
        TaskView tv = getRunningTaskView();
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
    public void onGestureAnimationStart(int runningTaskId) {
        // This needs to be called before the other states are set since it can create the task view
        showCurrentTask(runningTaskId);
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
            float startProgress = ENABLE_QUICKSTEP_LIVE_TILE.get() && mLiveTileOverlay != null
                    ? mLiveTileOverlay.cancelIconAnimation()
                    : 0f;
            animateUpRunningTaskIconScale(startProgress);
        }
        setSwipeDownShouldLaunchApp(true);
    }

    /**
     * Called when a gesture from an app has finished.
     */
    public void onGestureAnimationEnd() {
        setEnableFreeScroll(true);
        setEnableDrawingLiveTile(true);
        setOnScrollChangeListener(null);
        setRunningTaskViewShowScreenshot(true);
        setRunningTaskHidden(false);
        animateUpRunningTaskIconScale();
    }

    /**
     * Creates a task view (if necessary) to represent the task with the {@param runningTaskId}.
     *
     * All subsequent calls to reload will keep the task as the first item until {@link #reset()}
     * is called.  Also scrolls the view to this task.
     */
    public void showCurrentTask(int runningTaskId) {
        if (getTaskView(runningTaskId) == null) {
            boolean wasEmpty = getTaskViewCount() == 0;
            // Add an empty view for now until the task plan is loaded and applied
            final TaskView taskView = mTaskViewPool.getView();
            addView(taskView, mTaskViewStartIndex);
            if (wasEmpty) {
                addView(mClearAllButton);
            }
            // The temporary running task is only used for the duration between the start of the
            // gesture and the task list is loaded and applied
            mTmpRunningTask = new Task(new Task.TaskKey(runningTaskId, 0, new Intent(),
                    new ComponentName(getContext(), getClass()), 0, 0), null, null, "", "", 0, 0,
                    false, true, false, false, new ActivityManager.TaskDescription(), 0,
                    new ComponentName("", ""), false);
            taskView.bind(mTmpRunningTask);
        }

        boolean runningTaskTileHidden = mRunningTaskTileHidden;
        setCurrentTask(runningTaskId);
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

    public static class ScrollState {

        /**
         * The progress from 0 to 1, where 0 is the center
         * of the screen and 1 is the edge of the screen.
         */
        public float linearInterpolation;

        /**
         * The amount by which all the content is scrolled relative to the end of the list.
         */
        public float scrollFromEdge;
    }

    public void setIgnoreResetTask(int taskId) {
        mIgnoreResetTaskId = taskId;
    }

    public void clearIgnoreResetTask(int taskId) {
        if (mIgnoreResetTaskId == taskId) {
            mIgnoreResetTaskId = -1;
        }
    }

    private void addDismissedTaskAnimations(View taskView, AnimatorSet anim, long duration) {
        addAnim(ObjectAnimator.ofFloat(taskView, ALPHA, 0), duration, ACCEL_2, anim);
        if (QUICKSTEP_SPRINGS.get() && taskView instanceof TaskView)
            addAnim(new SpringObjectAnimator<>(taskView, VIEW_TRANSLATE_Y,
                            MIN_VISIBLE_CHANGE_PIXELS, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY,
                            SpringForce.STIFFNESS_MEDIUM,
                            0, -taskView.getHeight()),
                    duration, LINEAR, anim);
        else {
            addAnim(ObjectAnimator.ofFloat(taskView, TRANSLATION_Y, -taskView.getHeight()),
                    duration, LINEAR, anim);
        }
    }

    private void removeTask(Task task, int index, PendingAnimation.OnEndListener onEndListener,
                            boolean shouldLog) {
        if (task != null) {
            ActivityManagerWrapper.getInstance().removeTask(task.key.id);
            if (shouldLog) {
                ComponentKey componentKey = TaskUtils.getLaunchComponentKeyForTask(task.key);
                mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(
                        onEndListener.logAction, Direction.UP, index, componentKey);
                mActivity.getStatsLogManager().logTaskDismiss(this, componentKey);
            }
        }
    }

    public PendingAnimation createTaskDismissAnimation(TaskView taskView, boolean animateTaskView,
            boolean shouldRemoveTask, long duration) {
        if (mPendingAnimation != null) {
            mPendingAnimation.finish(false, Touch.SWIPE);
        }
        AnimatorSet anim = new AnimatorSet();
        PendingAnimation pendingAnimation = new PendingAnimation(anim);

        int count = getPageCount();
        if (count == 0) {
            return pendingAnimation;
        }

        int[] oldScroll = new int[count];
        getPageScrolls(oldScroll, false, SIMPLE_SCROLL_LOGIC);

        int[] newScroll = new int[count];
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
                    addDismissedTaskAnimations(taskView, anim, duration);
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
                    if (QUICKSTEP_SPRINGS.get() && child instanceof TaskView) {
                        addAnim(new SpringObjectAnimator<>(child, VIEW_TRANSLATE_X,
                                MIN_VISIBLE_CHANGE_PIXELS, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY,
                                SpringForce.STIFFNESS_MEDIUM,
                                0, scrollDiff), duration, ACCEL, anim);
                    } else {
                        addAnim(ObjectAnimator.ofFloat(child, TRANSLATION_X, scrollDiff), duration,
                                ACCEL, anim);
                    }

                    needsCurveUpdates = true;
                }
            }
        }

        if (needsCurveUpdates) {
            ValueAnimator va = ValueAnimator.ofFloat(0, 1);
            va.addUpdateListener((a) -> updateCurveProperties());
            anim.play(va);
        }

        // Add a tiny bit of translation Z, so that it draws on top of other views
        if (animateTaskView) {
            taskView.setTranslationZ(0.1f);
        }

        mPendingAnimation = pendingAnimation;
        mPendingAnimation.addEndListener(new Consumer<PendingAnimation.OnEndListener>() {
            @Override
            public void accept(PendingAnimation.OnEndListener onEndListener) {
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() &&
                        taskView.isRunningTask() && onEndListener.isSuccess) {
                    finishRecentsAnimation(true /* toHome */, () -> onEnd(onEndListener));
                } else {
                    onEnd(onEndListener);
                }
            }

            private void onEnd(PendingAnimation.OnEndListener onEndListener) {
                if (onEndListener.isSuccess) {
                    if (shouldRemoveTask) {
                        removeTask(taskView.getTask(), draggedIndex, onEndListener, true);
                    }

                    int pageToSnapTo = mCurrentPage;
                    if (draggedIndex < pageToSnapTo ||
                            pageToSnapTo == (getTaskViewCount() - 1)) {
                        pageToSnapTo -= 1;
                    }
                    removeView(taskView);

                    if (getTaskViewCount() == 0) {
                        removeView(mClearAllButton);
                        startHome();
                    } else {
                        snapToPageImmediately(pageToSnapTo);
                    }
                }
                resetTaskVisuals();
                mPendingAnimation = null;
            }
        });
        return pendingAnimation;
    }

    public PendingAnimation createAllTasksDismissAnimation(long duration) {
        if (FeatureFlags.IS_DOGFOOD_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
        }
        AnimatorSet anim = new AnimatorSet();
        PendingAnimation pendingAnimation = new PendingAnimation(anim);

        int count = getTaskViewCount();
        for (int i = 0; i < count; i++) {
            addDismissedTaskAnimations(getTaskViewAt(i), anim, duration);
        }

        mPendingAnimation = pendingAnimation;
        mPendingAnimation.addEndListener((onEndListener) -> {
            if (onEndListener.isSuccess) {
                // Remove all the task views now
                ActivityManagerWrapper.getInstance().removeAllRecentTasks();
                removeTasksViewsAndClearAllButton();
                startHome();
            }
            mPendingAnimation = null;
        });
        return pendingAnimation;
    }

    private static void addAnim(Animator anim, long duration,
            TimeInterpolator interpolator, AnimatorSet set) {
        anim.setDuration(duration).setInterpolator(interpolator);
        set.play(anim);
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
        AnimatorPlaybackController controller = AnimatorPlaybackController.wrap(
                pendingAnim.anim, DISMISS_TASK_DURATION);
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
        TaskView taskView = getTaskView(getNextPage());
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
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        child.setAlpha(mContentAlpha);
    }

    @Nullable
    public TaskView getNextTaskView() {
        return getTaskViewAtByAbsoluteIndex(getRunningTaskIndex() + 1);
    }

    @Nullable
    public TaskView getPreviousTaskView() {
        return getTaskViewAtByAbsoluteIndex(getRunningTaskIndex() - 1);
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
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmptyStateUi(changed);

        // Set the pivot points to match the task preview center
        setPivotY(((mInsets.top + getPaddingTop() + mTaskTopMargin)
                + (getHeight() - mInsets.bottom - getPaddingBottom())) / 2);
        setPivotX(((mInsets.left + getPaddingLeft())
                + (getWidth() - mInsets.right - getPaddingRight())) / 2);
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
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(
            TaskView tv, ClipAnimationHelper clipAnimationHelper) {
        AnimatorSet anim = new AnimatorSet();

        int taskIndex = indexOfChild(tv);
        int centerTaskIndex = getCurrentPage();
        boolean launchingCenterTask = taskIndex == centerTaskIndex;

        LauncherState.ScaleAndTranslation toScaleAndTranslation = clipAnimationHelper
                .getScaleAndTranslation();
        float toScale = toScaleAndTranslation.scale;
        float toTranslationY = toScaleAndTranslation.translationY;
        if (launchingCenterTask) {
            RecentsView recentsView = tv.getRecentsView();
            anim.play(ObjectAnimator.ofFloat(recentsView, SCALE_PROPERTY, toScale));
            anim.play(ObjectAnimator.ofFloat(recentsView, TRANSLATION_Y, toTranslationY));
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

    public PendingAnimation createTaskLauncherAnimation(TaskView tv, long duration) {
        if (FeatureFlags.IS_DOGFOOD_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
        }

        int count = getTaskViewCount();
        if (count == 0) {
            return new PendingAnimation(new AnimatorSet());
        }

        int targetSysUiFlags = tv.getThumbnail().getSysUiStatusNavFlags();
        final boolean[] passedOverviewThreshold = new boolean[] {false};
        ValueAnimator progressAnim = ValueAnimator.ofFloat(0, 1);
        progressAnim.setInterpolator(LINEAR);
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

        ClipAnimationHelper clipAnimationHelper = new ClipAnimationHelper(mActivity);
        clipAnimationHelper.fromTaskThumbnailView(tv.getThumbnail(), this);
        clipAnimationHelper.prepareAnimation(mActivity.getDeviceProfile(), true /* isOpening */);
        AnimatorSet anim = createAdjacentPageAnimForTaskLaunch(tv, clipAnimationHelper);
        anim.play(progressAnim);
        anim.setDuration(duration);

        Consumer<Boolean> onTaskLaunchFinish = this::onTaskLaunched;

        mPendingAnimation = new PendingAnimation(anim);
        mPendingAnimation.addEndListener((onEndListener) -> {
            if (onEndListener.isSuccess) {
                Consumer<Boolean> onLaunchResult = (result) -> {
                    onTaskLaunchFinish.accept(result);
                    if (!result) {
                        tv.notifyTaskLaunchFailed(TAG);
                    }
                };
                tv.launchTask(false, onLaunchResult, getHandler());
                Task task = tv.getTask();
                if (task != null) {
                    mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(
                            onEndListener.logAction, Direction.DOWN, indexOfChild(tv),
                            TaskUtils.getLaunchComponentKeyForTask(task.key));
                }
            } else {
                onTaskLaunchFinish.accept(false);
            }
            mPendingAnimation = null;
        });
        return mPendingAnimation;
    }

    protected void onTaskLaunchAnimationUpdate(float progress, TaskView tv) {
    }

    public abstract boolean shouldUseMultiWindowTaskSizeStrategy();

    protected void onTaskLaunched(boolean success) {
        if (success) {
            resetTaskVisuals();
        }
    }

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
            event.setFromIndex(taskViewCount - visibleTasks[1] - 1);
            event.setToIndex(taskViewCount - visibleTasks[0] - 1);
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

    public void setRecentsAnimationWrapper(RecentsAnimationWrapper recentsAnimationWrapper) {
        mRecentsAnimationWrapper = recentsAnimationWrapper;
    }

    public void setClipAnimationHelper(ClipAnimationHelper clipAnimationHelper) {
        mClipAnimationHelper = clipAnimationHelper;
    }

    public void setLiveTileOverlay(LiveTileOverlay liveTileOverlay) {
        mLiveTileOverlay = liveTileOverlay;
    }

    public void updateLiveTileIcon(Drawable icon) {
        if (mLiveTileOverlay != null) {
            mLiveTileOverlay.setIcon(icon);
        }
    }

    public void finishRecentsAnimation(boolean toRecents, Runnable onFinishComplete) {
        if (mRecentsAnimationWrapper == null) {
            if (onFinishComplete != null) {
                onFinishComplete.run();
            }
            return;
        }

        mRecentsAnimationWrapper.finish(toRecents, onFinishComplete);
    }

    public void setDisallowScrollToClearAll(boolean disallowScrollToClearAll) {
        if (mDisallowScrollToClearAll != disallowScrollToClearAll) {
            mDisallowScrollToClearAll = disallowScrollToClearAll;
            updateMinAndMaxScrollX();
        }
    }

    @Override
    protected int computeMinScrollX() {
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
        return super.computeMinScrollX();
    }

    @Override
    protected int computeMaxScrollX() {
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
        return super.computeMaxScrollX();
    }

    public ClearAllButton getClearAllButton() {
        return mClearAllButton;
    }

    /**
     * @return How many pixels the running task is offset on the x-axis due to the current scrollX.
     */
    public float getScrollOffset() {
        if (getRunningTaskIndex() == -1) {
            return 0;
        }
        int startScroll = getScrollForPage(getRunningTaskIndex());
        int offsetX = startScroll - getScrollX();
        offsetX *= getScaleX();
        return offsetX;
    }

    public Consumer<MotionEvent> getEventDispatcher(RotationMode rotationMode) {
        if (rotationMode.isTransposed) {
            Matrix transform = new Matrix();
            transform.setRotate(-rotationMode.surfaceRotation);

            if (getWidth() > 0 && getHeight() > 0) {
                float scale = ((float) getWidth()) / getHeight();
                transform.postScale(scale, 1 / scale);
            }

            Matrix inverse = new Matrix();
            transform.invert(inverse);
            return e -> {
                e.transform(transform);
                super.onTouchEvent(e);
                e.transform(inverse);
            };
        } else {
            return super::onTouchEvent;
        }
    }

    public ClipAnimationHelper getTempClipAnimationHelper() {
        return mTempClipAnimationHelper;
    }

    private void updateEnabledOverlays() {
        int overlayEnabledPage = mOverlayEnabled ? getNextPage() : -1;
        int taskCount = getTaskViewCount();
        for (int i = 0; i < taskCount; i++) {
            getTaskViewAt(i).setOverlayEnabled(i == overlayEnabledPage);
        }
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        if (mOverlayEnabled != overlayEnabled) {
            mOverlayEnabled = overlayEnabled;
            updateEnabledOverlays();
        }
    }

    public int getLeftGestureMargin() {
        final WindowInsets insets = getRootWindowInsets();
        return Math.max(insets.getSystemGestureInsets().left, insets.getSystemWindowInsetLeft());
    }

    public int getRightGestureMargin() {
        final WindowInsets insets = getRootWindowInsets();
        return Math.max(insets.getSystemGestureInsets().right, insets.getSystemWindowInsetRight());
    }

    @Override
    public void addView(View child, int index) {
        super.addView(child, index);
        if (isExtraCardView(child, index)) {
            mTaskViewStartIndex++;
        }
    }

    @Override
    public void removeView(View view) {
        if (isExtraCardView(view, indexOfChild(view))) {
            mTaskViewStartIndex--;
        }
        super.removeView(view);
    }

    private boolean isExtraCardView(View view, int index) {
        return !(view instanceof TaskView) && !(view instanceof ClearAllButton)
                && index <= mTaskViewStartIndex;
    }
}
