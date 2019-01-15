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

import static com.android.launcher3.BaseActivity.STATE_HANDLER_INVISIBILITY_FLAGS;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.util.SystemUiController.UI_STATE_OVERVIEW;
import static com.android.quickstep.TaskUtils.checkCurrentOrManagedUserId;
import static com.android.quickstep.WindowTransformSwipeHandler.MIN_PROGRESS_FOR_OVERVIEW;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ListView;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.PendingAnimation;
import com.android.launcher3.util.Themes;
import com.android.quickstep.OverviewCallbacks;
import com.android.quickstep.QuickScrubController;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.TaskViewDrawable;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.TaskStack;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A list of recent tasks.
 */
@TargetApi(Build.VERSION_CODES.P)
public abstract class RecentsView<T extends BaseActivity> extends PagedView implements Insettable {

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

    private final Rect mTempRect = new Rect();

    private static final int DISMISS_TASK_DURATION = 300;
    // The threshold at which we update the SystemUI flags when animating from the task into the app
    public static final float UPDATE_SYSUI_FLAGS_THRESHOLD = 0.85f;

    private static final float[] sTempFloatArray = new float[3];

    protected final T mActivity;
    private final QuickScrubController mQuickScrubController;
    private final float mFastFlingVelocity;
    private final RecentsModel mModel;
    private final int mTaskTopMargin;
    private final ClearAllButton mClearAllButton;
    private final Rect mClearAllButtonDeadZoneRect = new Rect();
    private final Rect mTaskViewDeadZoneRect = new Rect();

    private final ScrollState mScrollState = new ScrollState();
    // Keeps track of the previously known visible tasks for purposes of loading/unloading task data
    private final SparseBooleanArray mHasVisibleTaskData = new SparseBooleanArray();

    /**
     * TODO: Call reloadIdNeeded in onTaskStackChanged.
     */
    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
            if (!mHandleTaskStackChanges) {
                return;
            }
            updateThumbnail(taskId, snapshot);
        }

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
            // TODO: Re-enable layout transitions for addition of the unpinned task
            reloadIfNeeded();
        }

        @Override
        public void onTaskRemoved(int taskId) {
            if (!mHandleTaskStackChanges) {
                return;
            }
            BackgroundExecutor.get().submit(() -> {
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
                    RecentsTaskLoadPlan loadPlan = new RecentsTaskLoadPlan(getContext());
                    RecentsTaskLoadPlan.PreloadOptions opts =
                            new RecentsTaskLoadPlan.PreloadOptions();
                    opts.loadTitles = false;
                    loadPlan.preloadPlan(opts, mModel.getRecentsTaskLoader(), -1,
                            UserHandle.myUserId());
                    if (loadPlan.getTaskStack().findTaskWithId(taskId) == null) {
                        // The task was removed from the recents list
                        handler.post(() ->
                                dismissTask(taskView, true /* animate */, false /* removeTask */));
                    }
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

    private int mLoadPlanId = -1;

    // Only valid until the launcher state changes to NORMAL
    private int mRunningTaskId = -1;
    private boolean mRunningTaskTileHidden;
    private Task mTmpRunningTask;

    private boolean mRunningTaskIconScaledDown = false;

    private boolean mOverviewStateEnabled;
    private boolean mHandleTaskStackChanges;
    private Runnable mNextPageSwitchRunnable;
    private boolean mSwipeDownShouldLaunchApp;
    private boolean mTouchDownToStartHome;
    private final int mTouchSlop;
    private int mDownX;
    private int mDownY;

    private PendingAnimation mPendingAnimation;

    @ViewDebug.ExportedProperty(category = "launcher")
    private float mContentAlpha = 1;

    // Keeps track of task views whose visual state should not be reset
    private ArraySet<TaskView> mIgnoreResetTaskViews = new ArraySet<>();

    // Variables for empty state
    private final Drawable mEmptyIcon;
    private final CharSequence mEmptyMessage;
    private final TextPaint mEmptyMessagePaint;
    private final Point mLastMeasureSize = new Point();
    private final int mEmptyMessagePadding;
    private boolean mShowEmptyMessage;
    private Layout mEmptyTextLayout;

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
        enableFreeScroll(true);

        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);
        mActivity = (T) BaseActivity.fromContext(context);
        mQuickScrubController = new QuickScrubController(mActivity, this);
        mModel = RecentsModel.getInstance(context);

        mClearAllButton = (ClearAllButton) LayoutInflater.from(context)
                .inflate(R.layout.overview_clear_all_button, this, false);
        mClearAllButton.setOnClickListener(this::dismissAllTasks);

        mIsRtl = !Utilities.isRtl(getResources());
        setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mTaskTopMargin = getResources()
                .getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mEmptyIcon = context.getDrawable(R.drawable.ic_empty_recents);
        mEmptyIcon.setCallback(this);
        mEmptyMessage = context.getText(R.string.recents_empty_message);
        mEmptyMessagePaint = new TextPaint();
        mEmptyMessagePaint.setColor(Themes.getAttrColor(context, android.R.attr.textColorPrimary));
        mEmptyMessagePaint.setTextSize(getResources()
                .getDimension(R.dimen.recents_empty_message_text_size));
        mEmptyMessagePadding = getResources()
                .getDimensionPixelSize(R.dimen.recents_empty_message_text_padding);
        setWillNotDraw(false);
        updateEmptyMessage();
    }

    public boolean isRtl() {
        return mIsRtl;
    }

    public TaskView updateThumbnail(int taskId, ThumbnailData thumbnailData) {
        TaskView taskView = getTaskView(taskId);
        if (taskView != null) {
            taskView.onTaskDataLoaded(taskView.getTask(), thumbnailData);
        }
        return taskView;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateTaskStackListenerState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTaskStackListenerState();
        mActivity.addMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateTaskStackListenerState();
        mActivity.removeMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
        ActivityManagerWrapper.getInstance().unregisterTaskStackListener(mTaskStackListener);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);

        // Clear the task data for the removed child if it was visible
        if (child != mClearAllButton) {
            Task task = ((TaskView) child).getTask();
            if (mHasVisibleTaskData.get(task.key.id)) {
                mHasVisibleTaskData.delete(task.key.id);
                RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
                loader.unloadTaskData(task);
                loader.getHighResThumbnailLoader().onTaskInvisible(task);
            }
        }
    }

    public boolean isTaskViewVisible(TaskView tv) {
        // For now, just check if it's the active task or an adjacent task
        return Math.abs(indexOfChild(tv) - getNextPage()) <= 1;
    }

    public TaskView getTaskView(int taskId) {
        for (int i = 0; i < getTaskViewCount(); i++) {
            TaskView tv = (TaskView) getChildAt(i);
            if (tv.getTask().key.id == taskId) {
                return tv;
            }
        }
        return null;
    }

    public void setOverviewStateEnabled(boolean enabled) {
        mOverviewStateEnabled = enabled;
        updateTaskStackListenerState();
    }

    public void setNextPageSwitchRunnable(Runnable r) {
        mNextPageSwitchRunnable = r;
    }

    @Override
    protected void onPageEndTransition() {
        super.onPageEndTransition();
        if (mNextPageSwitchRunnable != null) {
            mNextPageSwitchRunnable.run();
            mNextPageSwitchRunnable = null;
        }
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
                if (mShowEmptyMessage) {
                    onAllTasksRemoved();
                }
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
                if (mTouchDownToStartHome && Math.hypot(mDownX - x, mDownY - y) > mTouchSlop) {
                    mTouchDownToStartHome = false;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                // Touch down anywhere but the deadzone around the visible clear all button and
                // between the task views will start home on touch up
                if (mTouchState == TOUCH_STATE_REST) {
                    updateDeadZoneRects();
                    final boolean clearAllButtonDeadZoneConsumed = mClearAllButton.getAlpha() == 1
                            && mClearAllButtonDeadZoneRect.contains(x, y);
                    if (!clearAllButtonDeadZoneConsumed
                            && !mTaskViewDeadZoneRect.contains(x + getScrollX(), y)) {
                        mTouchDownToStartHome = true;
                    }
                }
                mDownX = x;
                mDownY = y;
                break;
        }


        // Do not let touch escape to siblings below this view.
        return true;
    }

    private void applyLoadPlan(RecentsTaskLoadPlan loadPlan) {
        if (mPendingAnimation != null) {
            mPendingAnimation.addEndListener((onEndListener) -> applyLoadPlan(loadPlan));
            return;
        }
        TaskStack stack = loadPlan != null ? loadPlan.getTaskStack() : null;
        if (stack == null) {
            removeAllViews();
            onTaskStackUpdated();
            return;
        }

        int oldChildCount = getChildCount();

        // Ensure there are as many views as there are tasks in the stack (adding and trimming as
        // necessary)
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final ArrayList<Task> tasks = new ArrayList<>(stack.getTasks());

        final int requiredTaskCount = tasks.size();
        if (getTaskViewCount() != requiredTaskCount) {
            if (oldChildCount > 0) {
                removeView(mClearAllButton);
            }
            for (int i = getChildCount(); i < requiredTaskCount; i++) {
                final TaskView taskView = (TaskView) inflater.inflate(R.layout.task, this, false);
                addView(taskView);
            }
            while (getChildCount() > requiredTaskCount) {
                final TaskView taskView = (TaskView) getChildAt(getChildCount() - 1);
                removeView(taskView);
            }
            if (requiredTaskCount > 0) {
                addView(mClearAllButton);
            }
        }

        // Unload existing visible task data
        unloadVisibleTaskData();

        // Rebind and reset all task views
        for (int i = requiredTaskCount - 1; i >= 0; i--) {
            final int pageIndex = requiredTaskCount - i - 1;
            final Task task = tasks.get(i);
            final TaskView taskView = (TaskView) getChildAt(pageIndex);
            taskView.bind(task);
        }
        resetTaskVisuals();

        if (oldChildCount != getChildCount()) {
            mQuickScrubController.snapToNextTaskIfAvailable();
        }
        onTaskStackUpdated();
    }

    public int getTaskViewCount() {
        // Account for the clear all button.
        int childCount = getChildCount();
        return childCount == 0 ? 0 : childCount - 1;
    }

    protected void onTaskStackUpdated() { }

    public void resetTaskVisuals() {
        for (int i = getTaskViewCount() - 1; i >= 0; i--) {
            TaskView taskView = (TaskView) getChildAt(i);
            if (!mIgnoreResetTaskViews.contains(taskView)) {
                taskView.resetVisualProperties();
            }
        }
        if (mRunningTaskTileHidden) {
            setRunningTaskHidden(mRunningTaskTileHidden);
        }
        applyIconScale(false /* animate */);

        updateCurveProperties();
        // Update the set of visible task's data
        loadVisibleTaskData();
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

        // Keep this logic in sync with ActivityControlHelper.getTranslationYForQuickScrub.
        mTempRect.top -= mTaskTopMargin;
        setPadding(mTempRect.left - mInsets.left, mTempRect.top - mInsets.top,
                dp.availableWidthPx + mInsets.left - mTempRect.right,
                dp.availableHeightPx + mInsets.top - mTempRect.bottom);
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
        if (scrolling || (mTouchState == TOUCH_STATE_SCROLLING)) {
            if (scrolling) {
                // Check if we are flinging quickly to disable high res thumbnail loading
                isFlingingFast = mScroller.getCurrVelocity() > mFastFlingVelocity;
            }

            // After scrolling, update the visible task's data
            loadVisibleTaskData();
        }

        // Update the high res thumbnail loader
        RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
        loader.getHighResThumbnailLoader().setFlingingFast(isFlingingFast);
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
     * Iterates through all thet asks, and loads the associated task data for newly visible tasks,
     * and unloads the associated task data for tasks that are no longer visible.
     */
    public void loadVisibleTaskData() {
        if (!mOverviewStateEnabled) {
            // Skip loading visible task data if we've already left the overview state
            return;
        }

        RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
        int centerPageIndex = getPageNearestToCenterOfScreen();
        int numChildren = getTaskViewCount();
        int lower = Math.max(0, centerPageIndex - 2);
        int upper = Math.min(centerPageIndex + 2, numChildren - 1);

        // Update the task data for the in/visible children
        for (int i = 0; i < numChildren; i++) {
            TaskView taskView = (TaskView) getChildAt(i);
            Task task = taskView.getTask();
            boolean visible = lower <= i && i <= upper;
            if (visible) {
                if (task == mTmpRunningTask) {
                    // Skip loading if this is the task that we are animating into
                    continue;
                }
                if (!mHasVisibleTaskData.get(task.key.id)) {
                    loader.loadTaskData(task);
                    loader.getHighResThumbnailLoader().onTaskVisible(task);
                }
                mHasVisibleTaskData.put(task.key.id, visible);
            } else {
                if (mHasVisibleTaskData.get(task.key.id)) {
                    loader.unloadTaskData(task);
                    loader.getHighResThumbnailLoader().onTaskInvisible(task);
                }
                mHasVisibleTaskData.delete(task.key.id);
            }
        }
    }

    /**
     * Unloads any associated data from the currently visible tasks
     */
    private void unloadVisibleTaskData() {
        RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
        for (int i = 0; i < mHasVisibleTaskData.size(); i++) {
            if (mHasVisibleTaskData.valueAt(i)) {
                TaskView taskView = getTaskView(mHasVisibleTaskData.keyAt(i));
                Task task = taskView.getTask();
                loader.unloadTaskData(task);
                loader.getHighResThumbnailLoader().onTaskInvisible(task);
            }
        }
        mHasVisibleTaskData.clear();
    }

    protected void onAllTasksRemoved() {
        startHome();
    }

    protected abstract void startHome();

    public void reset() {
        mRunningTaskId = -1;
        mRunningTaskTileHidden = false;

        unloadVisibleTaskData();
        setCurrentPage(0);

        OverviewCallbacks.get(getContext()).onResetOverview();
    }

    /**
     * Reloads the view if anything in recents changed.
     */
    public void reloadIfNeeded() {
        if (!mModel.isLoadPlanValid(mLoadPlanId)) {
            mLoadPlanId = mModel.loadTasks(mRunningTaskId, this::applyLoadPlan);
        }
    }

    /**
     * Ensures that the first task in the view represents {@param task} and reloads the view
     * if needed. This allows the swipe-up gesture to assume that the first tile always
     * corresponds to the correct task.
     * All subsequent calls to reload will keep the task as the first item until {@link #reset()}
     * is called.
     * Also scrolls the view to this task
     */
    public void showTask(int runningTaskId) {
        if (getChildCount() == 0) {
            // Add an empty view for now until the task plan is loaded and applied
            final TaskView taskView = (TaskView) LayoutInflater.from(getContext())
                    .inflate(R.layout.task, this, false);
            addView(taskView);
            addView(mClearAllButton);

            // The temporary running task is only used for the duration between the start of the
            // gesture and the task list is loaded and applied
            mTmpRunningTask = new Task(new Task.TaskKey(runningTaskId, 0, new Intent(),
                    new ComponentName(getContext(), getClass()), 0, 0), null, null, "", "", 0, 0,
                    false, true, false, false, new ActivityManager.TaskDescription(), 0,
                    new ComponentName("", ""), false);
            taskView.bind(mTmpRunningTask);
        }
        setCurrentTask(runningTaskId);
    }

    /**
     * Hides the tile associated with {@link #mRunningTaskId}
     */
    public void setRunningTaskHidden(boolean isHidden) {
        mRunningTaskTileHidden = isHidden;
        TaskView runningTask = getTaskView(mRunningTaskId);
        if (runningTask != null) {
            runningTask.setAlpha(isHidden ? 0 : mContentAlpha);
        }
    }

    /**
     * Similar to {@link #showTask(int)} but does not put any restrictions on the first tile.
     */
    public void setCurrentTask(int runningTaskId) {
        boolean runningTaskTileHidden = mRunningTaskTileHidden;
        boolean runningTaskIconScaledDown = mRunningTaskIconScaledDown;

        setRunningTaskIconScaledDown(false, false);
        setRunningTaskHidden(false);
        mRunningTaskId = runningTaskId;
        setRunningTaskIconScaledDown(runningTaskIconScaledDown, false);
        setRunningTaskHidden(runningTaskTileHidden);

        setCurrentPage(0);

        // Load the tasks (if the loading is already
        mLoadPlanId = mModel.loadTasks(runningTaskId, this::applyLoadPlan);
    }

    public void showNextTask() {
        TaskView runningTaskView = getTaskView(mRunningTaskId);
        if (runningTaskView == null) {
            // Launch the first task
            if (getTaskViewCount() > 0) {
                ((TaskView) getChildAt(0)).launchTask(true /* animate */);
            }
        } else {
            // Get the next launch task
            int runningTaskIndex = indexOfChild(runningTaskView);
            int nextTaskIndex = Math.max(0, Math.min(getTaskViewCount() - 1, runningTaskIndex + 1));
            if (nextTaskIndex < getTaskViewCount()) {
                ((TaskView) getChildAt(nextTaskIndex)).launchTask(true /* animate */);
            }
        }
    }

    public QuickScrubController getQuickScrubController() {
        return mQuickScrubController;
    }

    public void setRunningTaskIconScaledDown(boolean isScaledDown, boolean animate) {
        if (mRunningTaskIconScaledDown == isScaledDown) {
            return;
        }
        mRunningTaskIconScaledDown = isScaledDown;
        applyIconScale(animate);
    }

    private void applyIconScale(boolean animate) {
        float scale = mRunningTaskIconScaledDown ? 0 : 1;
        TaskView firstTask = getTaskView(mRunningTaskId);
        if (firstTask != null) {
            if (animate) {
                firstTask.animateIconToScaleAndDim(scale);
            } else {
                firstTask.setIconScaleAndDim(scale);
            }
        }
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

    public void addIgnoreResetTask(TaskView taskView) {
        mIgnoreResetTaskViews.add(taskView);
    }

    public void removeIgnoreResetTask(TaskView taskView) {
        mIgnoreResetTaskViews.remove(taskView);
    }

    private void addDismissedTaskAnimations(View taskView, AnimatorSet anim, long duration) {
        addAnim(ObjectAnimator.ofFloat(taskView, ALPHA, 0), duration, ACCEL_2, anim);
        addAnim(ObjectAnimator.ofFloat(taskView, TRANSLATION_Y, -taskView.getHeight()),
                duration, LINEAR, anim);
    }

    private void removeTask(Task task, int index, PendingAnimation.OnEndListener onEndListener,
                            boolean shouldLog) {
        if (task != null) {
            ActivityManagerWrapper.getInstance().removeTask(task.key.id);
            if (shouldLog) {
                mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(
                        onEndListener.logAction, Direction.UP, index,
                        TaskUtils.getLaunchComponentKeyForTask(task.key));
            }
        }
    }

    public PendingAnimation createTaskDismissAnimation(TaskView taskView, boolean animateTaskView,
            boolean shouldRemoveTask, long duration) {
        if (FeatureFlags.IS_DOGFOOD_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
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
                    addAnim(ObjectAnimator.ofFloat(child, TRANSLATION_X, scrollDiff),
                            duration, ACCEL, anim);
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
        mPendingAnimation.addEndListener((onEndListener) -> {
           if (onEndListener.isSuccess) {
               if (shouldRemoveTask) {
                   removeTask(taskView.getTask(), draggedIndex, onEndListener, true);
               }
               int pageToSnapTo = mCurrentPage;
               if (draggedIndex < pageToSnapTo || pageToSnapTo == (getTaskViewCount() - 1)) {
                   pageToSnapTo -= 1;
               }
               removeView(taskView);

               if (getTaskViewCount() == 0) {
                   removeView(mClearAllButton);
                   onAllTasksRemoved();
               } else {
                   snapToPageImmediately(pageToSnapTo);
               }
           }
           resetTaskVisuals();
           mPendingAnimation = null;
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
            addDismissedTaskAnimations(getChildAt(i), anim, duration);
        }

        mPendingAnimation = pendingAnimation;
        mPendingAnimation.addEndListener((onEndListener) -> {
            if (onEndListener.isSuccess) {
                int taskViewCount = getTaskViewCount();
                for (int i = 0; i < taskViewCount; i++) {
                    removeTask(getTaskViewAt(i).getTask(), -1, onEndListener, false);
                }
                removeAllViews();
                onAllTasksRemoved();
            }
            mPendingAnimation = null;
        });
        return pendingAnimation;
    }

    private static void addAnim(ObjectAnimator anim, long duration,
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
                getChildAt(i).setAlpha(alpha);
            }
        }
        mClearAllButton.setContentAlpha(mContentAlpha);

        int alphaInt = Math.round(alpha * 255);
        mEmptyMessagePaint.setAlpha(alphaInt);
        mEmptyIcon.setAlpha(alphaInt);

        setVisibility(alpha > 0 ? VISIBLE : GONE);
    }

    private float[] getAdjacentScaleAndTranslation(TaskView currTask,
            float currTaskToScale, float currTaskToTranslationY) {
        float displacement = currTask.getWidth() * (currTaskToScale - currTask.getCurveScale());
        sTempFloatArray[0] = currTaskToScale;
        sTempFloatArray[1] = mIsRtl ? -displacement : displacement;
        sTempFloatArray[2] = currTaskToTranslationY;
        return sTempFloatArray;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        child.setAlpha(mContentAlpha);
    }

    public TaskView getTaskViewAt(int index) {
        View child = getChildAt(index);
        return child == mClearAllButton ? null : (TaskView) child;
    }

    public void updateEmptyMessage() {
        boolean isEmpty = getChildCount() == 0;
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

        float toScale = clipAnimationHelper.getSourceRect().width()
                / clipAnimationHelper.getTargetRect().width();
        float toTranslationY = clipAnimationHelper.getSourceRect().centerY()
                - clipAnimationHelper.getTargetRect().centerY();
        if (launchingCenterTask) {
            TaskView centerTask = getTaskViewAt(centerTaskIndex);
            if (taskIndex - 1 >= 0) {
                TaskView adjacentTask = getTaskViewAt(taskIndex - 1);
                float[] scaleAndTranslation = getAdjacentScaleAndTranslation(centerTask,
                        toScale, toTranslationY);
                scaleAndTranslation[1] = -scaleAndTranslation[1];
                anim.play(createAnimForChild(adjacentTask, scaleAndTranslation));
            }
            if (taskIndex + 1 < getTaskViewCount()) {
                TaskView adjacentTask = getTaskViewAt(taskIndex + 1);
                float[] scaleAndTranslation = getAdjacentScaleAndTranslation(centerTask,
                        toScale, toTranslationY);
                anim.play(createAnimForChild(adjacentTask, scaleAndTranslation));
            }
        } else {
            // We are launching an adjacent task, so parallax the center and other adjacent task.
            float displacementX = tv.getWidth() * (toScale - tv.getCurveScale());
            anim.play(ObjectAnimator.ofFloat(getPageAt(centerTaskIndex), TRANSLATION_X,
                    mIsRtl ? -displacementX : displacementX));

            int otherAdjacentTaskIndex = centerTaskIndex + (centerTaskIndex - taskIndex);
            if (otherAdjacentTaskIndex >= 0 && otherAdjacentTaskIndex < getPageCount()) {
                anim.play(ObjectAnimator.ofPropertyValuesHolder(getPageAt(otherAdjacentTaskIndex),
                        new PropertyListBuilder()
                                .translationX(mIsRtl ? -displacementX : displacementX)
                                .scale(1)
                                .build()));
            }
        }
        return anim;
    }

    private Animator createAnimForChild(TaskView child, float[] toScaleAndTranslation) {
        AnimatorSet anim = new AnimatorSet();
        anim.play(ObjectAnimator.ofFloat(child, TaskView.ZOOM_SCALE, toScaleAndTranslation[0]));
        anim.play(ObjectAnimator.ofPropertyValuesHolder(child,
                        new PropertyListBuilder()
                                .translationX(toScaleAndTranslation[1])
                                .translationY(toScaleAndTranslation[2])
                                .build()));
        return anim;
    }

    public PendingAnimation createTaskLauncherAnimation(TaskView tv, long duration) {
        if (FeatureFlags.IS_DOGFOOD_BUILD && mPendingAnimation != null) {
            throw new IllegalStateException("Another pending animation is still running");
        }

        int count = getChildCount();
        if (count == 0) {
            return new PendingAnimation(new AnimatorSet());
        }

        tv.setVisibility(INVISIBLE);
        int targetSysUiFlags = tv.getThumbnail().getSysUiStatusNavFlags();
        TaskViewDrawable drawable = new TaskViewDrawable(tv, this);
        getOverlay().add(drawable);

        final boolean[] passedOverviewThreshold = new boolean[] {false};
        ObjectAnimator drawableAnim =
                ObjectAnimator.ofFloat(drawable, TaskViewDrawable.PROGRESS, 1, 0);
        drawableAnim.setInterpolator(LINEAR);
        drawableAnim.addUpdateListener((animator) -> {
            // Once we pass a certain threshold, update the sysui flags to match the target tasks'
            // flags
            mActivity.getSystemUiController().updateUiState(UI_STATE_OVERVIEW,
                    animator.getAnimatedFraction() > UPDATE_SYSUI_FLAGS_THRESHOLD
                            ? targetSysUiFlags
                            : 0);

            // Passing the threshold from taskview to fullscreen app will vibrate
            final boolean passed = animator.getAnimatedFraction() >= MIN_PROGRESS_FOR_OVERVIEW;
            if (passed != passedOverviewThreshold[0]) {
                passedOverviewThreshold[0] = passed;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        });

        AnimatorSet anim = createAdjacentPageAnimForTaskLaunch(tv,
                drawable.getClipAnimationHelper());
        anim.play(drawableAnim);
        anim.setDuration(duration);

        Consumer<Boolean> onTaskLaunchFinish = (result) -> {
            onTaskLaunched(result);
            tv.setVisibility(VISIBLE);
            getOverlay().remove(drawable);
        };

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

    public abstract boolean shouldUseMultiWindowTaskSizeStrategy();

    protected void onTaskLaunched(boolean success) {
        resetTaskVisuals();
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        loadVisibleTaskData();
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
}
