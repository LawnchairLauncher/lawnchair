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

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;

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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityEvent;

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
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A list of recent tasks.
 */
@TargetApi(Build.VERSION_CODES.P)
public abstract class RecentsView<T extends BaseActivity> extends PagedView implements Insettable {

    private static final String TAG = RecentsView.class.getSimpleName();

    private final Rect mTempRect = new Rect();

    public static final FloatProperty<RecentsView> ADJACENT_SCALE =
            new FloatProperty<RecentsView>("adjacentScale") {
        @Override
        public void setValue(RecentsView recentsView, float v) {
            recentsView.setAdjacentScale(v);
        }

        @Override
        public Float get(RecentsView recentsView) {
            return recentsView.mAdjacentScale;
        }
    };
    public static final boolean FLIP_RECENTS = true;
    private static final int DISMISS_TASK_DURATION = 300;

    private static final float[] sTempFloatArray = new float[3];

    protected final T mActivity;
    private final QuickScrubController mQuickScrubController;
    private final float mFastFlingVelocity;
    private final RecentsModel mModel;

    private final ScrollState mScrollState = new ScrollState();
    // Keeps track of the previously known visible tasks for purposes of loading/unloading task data
    private final SparseBooleanArray mHasVisibleTaskData = new SparseBooleanArray();

    /**
     * TODO: Call reloadIdNeeded in onTaskStackChanged.
     */
    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
            updateThumbnail(taskId, snapshot);
        }

        @Override
        public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
            // Check this is for the right user
            if (!checkCurrentUserId(userId, false /* debug */)) {
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
            // TODO: Re-enable layout transitions for addition of the unpinned task
            reloadIfNeeded();
        }

        @Override
        public void onTaskRemoved(int taskId) {
            TaskView taskView = getTaskView(taskId);
            if (taskView != null) {
                dismissTask(taskView, true /* animate */, false /* removeTask */);
            }
        }
    };

    private int mLoadPlanId = -1;

    // Only valid until the launcher state changes to NORMAL
    private int mRunningTaskId = -1;
    private boolean mRunningTaskTileHidden;
    private Task mTmpRunningTask;

    private boolean mFirstTaskIconScaledDown = false;

    private boolean mOverviewStateEnabled;
    private boolean mTaskStackListenerRegistered;
    private Runnable mNextPageSwitchRunnable;
    private boolean mSwipeDownShouldLaunchApp;

    private PendingAnimation mPendingAnimation;

    @ViewDebug.ExportedProperty(category = "launcher")
    private float mContentAlpha = 1;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mAdjacentScale = 1;

    // Keeps track of task views whose visual state should not be reset
    private ArraySet<TaskView> mIgnoreResetTaskViews = new ArraySet<>();

    private View mClearAllButton;

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
        setClipToOutline(true);

        mFastFlingVelocity = getResources()
                .getDimensionPixelSize(R.dimen.recents_fast_fling_velocity);
        mActivity = (T) BaseActivity.fromContext(context);
        mQuickScrubController = new QuickScrubController(mActivity, this);
        mModel = RecentsModel.getInstance(context);

        mIsRtl = Utilities.isRtl(getResources());
        if (FLIP_RECENTS) {
            mIsRtl = !mIsRtl;
        }
        setLayoutDirection(mIsRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateTaskStackListenerState();
        mActivity.removeMultiWindowModeChangedListener(mMultiWindowModeChangedListener);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);

        // Clear the task data for the removed child if it was visible
        Task task = ((TaskView) child).getTask();
        if (mHasVisibleTaskData.get(task.key.id)) {
            mHasVisibleTaskData.delete(task.key.id);
            RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
            loader.unloadTaskData(task);
            loader.getHighResThumbnailLoader().onTaskInvisible(task);
        }
        onChildViewsChanged();
    }

    public boolean isTaskViewVisible(TaskView tv) {
        // For now, just check if it's the active task or an adjacent task
        return Math.abs(indexOfChild(tv) - getNextPage()) <= 1;
    }

    public TaskView getTaskView(int taskId) {
        for (int i = 0; i < getChildCount(); i++) {
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

    private float calculateClearAllButtonAlpha() {
        final int childCount = getChildCount();
        if (mShowEmptyMessage || childCount == 0) return 0;

        final View lastChild = getChildAt(childCount - 1);

        // Current visible coordinate of the end of the oldest task.
        final int carouselCurrentEnd =
                (mIsRtl ? lastChild.getLeft() : lastChild.getRight()) - getScrollX();

        // Visible button-facing end of a centered task.
        final int centeredTaskEnd = mIsRtl ?
                getPaddingLeft() + mInsets.left :
                getWidth() - getPaddingRight() - mInsets.right;

        // The distance of the carousel travel during which the alpha changes from 0 to 1. This
        // is the motion between the oldest task in its centered position and the oldest task
        // scrolled to the end.
        final int alphaChangeRange = (mIsRtl ? 0 : mMaxScrollX) - getScrollForPage(childCount - 1);

        return Utilities.boundToRange(
                ((float) (centeredTaskEnd - carouselCurrentEnd)) /
                        alphaChangeRange, 0, 1);
    }

    private void updateClearAllButtonAlpha() {
        if (mClearAllButton != null) {
            final float alpha = calculateClearAllButtonAlpha();
            mClearAllButton.setAlpha(alpha * mContentAlpha);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        updateClearAllButtonAlpha();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && mTouchState == TOUCH_STATE_REST
                && mScroller.isFinished() && mClearAllButton.getAlpha() > 0) {
            mClearAllButton.getHitRect(mTempRect);
            mTempRect.offset(-getLeft(), -getTop());
            if (mTempRect.contains((int) ev.getX(), (int) ev.getY())) {
                // If nothing is in motion, let the Clear All button process the event.
                return false;
            }
        }

        if (ev.getAction() == MotionEvent.ACTION_UP && mShowEmptyMessage) {
            onAllTasksRemoved();
        }
        return super.onTouchEvent(ev);
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

        final int requiredChildCount = tasks.size();
        for (int i = getChildCount(); i < requiredChildCount; i++) {
            final TaskView taskView = (TaskView) inflater.inflate(R.layout.task, this, false);
            addView(taskView);
        }
        while (getChildCount() > requiredChildCount) {
            final TaskView taskView = (TaskView) getChildAt(getChildCount() - 1);
            removeView(taskView);
        }

        // Unload existing visible task data
        unloadVisibleTaskData();

        // Rebind and reset all task views
        for (int i = requiredChildCount - 1; i >= 0; i--) {
            final int pageIndex = requiredChildCount - i - 1;
            final Task task = tasks.get(i);
            final TaskView taskView = (TaskView) getChildAt(pageIndex);
            taskView.bind(task);
        }
        resetTaskVisuals();
        applyIconScale(false /* animate */);

        if (oldChildCount != getChildCount()) {
            mQuickScrubController.snapToNextTaskIfAvailable();
        }
        onTaskStackUpdated();
    }

    protected void onTaskStackUpdated() { }

    public void resetTaskVisuals() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            TaskView taskView = (TaskView) getChildAt(i);
            if (!mIgnoreResetTaskViews.contains(taskView)) {
                taskView.resetVisualProperties();
            }
        }
        if (mRunningTaskTileHidden) {
            setRunningTaskHidden(mRunningTaskTileHidden);
        }

        updateCurveProperties();
        // Update the set of visible task's data
        loadVisibleTaskData();
    }

    private void updateTaskStackListenerState() {
        boolean registerStackListener = mOverviewStateEnabled && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE;
        if (registerStackListener != mTaskStackListenerRegistered) {
            if (registerStackListener) {
                ActivityManagerWrapper.getInstance()
                        .registerTaskStackListener(mTaskStackListener);
                reloadIfNeeded();
            } else {
                ActivityManagerWrapper.getInstance()
                        .unregisterTaskStackListener(mTaskStackListener);
            }
            mTaskStackListenerRegistered = registerStackListener;
        }
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile dp = mActivity.getDeviceProfile();
        getTaskSize(dp, mTempRect);
        mTempRect.top -= getResources()
                .getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
        setPadding(mTempRect.left - mInsets.left, mTempRect.top - mInsets.top,
                dp.widthPx - mTempRect.right - mInsets.right,
                dp.heightPx - mTempRect.bottom - mInsets.bottom);
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
        final int halfPageWidth = getNormalChildWidth() / 2;
        final int screenCenter = mInsets.left + getPaddingLeft() + getScrollX() + halfPageWidth;
        final int halfScreenWidth = getMeasuredWidth() / 2;
        final int pageSpacing = mPageSpacing;

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
        RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
        int centerPageIndex = getPageNearestToCenterOfScreen();
        int lower = Math.max(0, centerPageIndex - 2);
        int upper = Math.min(centerPageIndex + 2, getChildCount() - 1);
        int numChildren = getChildCount();

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

    protected abstract void onAllTasksRemoved();

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

            // The temporary running task is only used for the duration between the start of the
            // gesture and the task list is loaded and applied
            mTmpRunningTask = new Task(new Task.TaskKey(runningTaskId, 0, new Intent(), 0, 0), null,
                    null, "", "", 0, 0, false, true, false, false,
                    new ActivityManager.TaskDescription(), 0, new ComponentName("", ""), false);
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
        if (mRunningTaskTileHidden) {
            setRunningTaskHidden(false);
            mRunningTaskId = runningTaskId;
            setRunningTaskHidden(true);
        } else {
            mRunningTaskId = runningTaskId;
        }
        setCurrentPage(0);

        // Load the tasks (if the loading is already
        mLoadPlanId = mModel.loadTasks(runningTaskId, this::applyLoadPlan);
    }

    public void showNextTask() {
        TaskView runningTaskView = getTaskView(mRunningTaskId);
        if (runningTaskView == null) {
            // Launch the first task
            if (getChildCount() > 0) {
                ((TaskView) getChildAt(0)).launchTask(true /* animate */);
            }
        } else {
            // Get the next launch task
            int runningTaskIndex = indexOfChild(runningTaskView);
            int nextTaskIndex = Math.max(0, Math.min(getChildCount() - 1, runningTaskIndex + 1));
            if (nextTaskIndex < getChildCount()) {
                ((TaskView) getChildAt(nextTaskIndex)).launchTask(true /* animate */);
            }
        }
    }

    public QuickScrubController getQuickScrubController() {
        return mQuickScrubController;
    }

    public void setFirstTaskIconScaledDown(boolean isScaledDown, boolean animate) {
        if (mFirstTaskIconScaledDown == isScaledDown) {
            return;
        }
        mFirstTaskIconScaledDown = isScaledDown;
        applyIconScale(animate);
    }

    private void applyIconScale(boolean animate) {
        float scale = mFirstTaskIconScaledDown ? 0 : 1;
        TaskView firstTask = (TaskView) getChildAt(0);
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
        default void onPageScroll(ScrollState scrollState) {};
    }

    public static class ScrollState {

        /**
         * The progress from 0 to 1, where 0 is the center
         * of the screen and 1 is the edge of the screen.
         */
        public float linearInterpolation;
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

    private void removeTask(Task task, PendingAnimation.OnEndListener onEndListener,
            boolean shouldLog) {
        if (task != null) {
            ActivityManagerWrapper.getInstance().removeTask(task.key.id);
            if (shouldLog) {
                mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(
                        onEndListener.logAction, Direction.UP,
                        TaskUtils.getComponentKeyForTask(task.key));
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

        int count = getChildCount();
        if (count == 0) {
            return pendingAnimation;
        }

        int[] oldScroll = new int[count];
        getPageScrolls(oldScroll, false, SIMPLE_SCROLL_LOGIC);

        int[] newScroll = new int[count];
        getPageScrolls(newScroll, false, (v) -> v.getVisibility() != GONE && v != taskView);

        int scrollDiffPerPage = 0;
        int leftmostPage = mIsRtl ? count -1 : 0;
        int rightmostPage = mIsRtl ? 0 : count - 1;
        if (count > 1) {
            int secondRightmostPage = mIsRtl ? 1 : count - 2;
            scrollDiffPerPage = oldScroll[rightmostPage] - oldScroll[secondRightmostPage];
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
                    int lastPage = mIsRtl ? leftmostPage : rightmostPage;
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
                   removeTask(taskView.getTask(), onEndListener, true);
               }
               int pageToSnapTo = mCurrentPage;
               if (draggedIndex < pageToSnapTo) {
                   pageToSnapTo -= 1;
               }
               removeView(taskView);
               if (getChildCount() == 0) {
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

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            addDismissedTaskAnimations(getChildAt(i), anim, duration);
        }

        mPendingAnimation = pendingAnimation;
        mPendingAnimation.addEndListener((onEndListener) -> {
            if (onEndListener.isSuccess) {
                while (getChildCount() != 0) {
                    TaskView taskView = getPageAt(getChildCount() - 1);
                    removeTask(taskView.getTask(), onEndListener, false);
                    removeView(taskView);
                }
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

    private void snapToPageRelative(int delta) {
        if (getPageCount() == 0) {
            return;
        }
        snapToPage((getNextPage() + getPageCount() + delta) % getPageCount());
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible && !isFocused()) {
            // Having focus, even in touch mode, keeps us from losing [Alt+]Tab by preventing
            // switching to keyboard mode.
            requestFocus();
        }
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

    public void dismissAllTasks() {
        runDismissAnimation(createAllTasksDismissAnimation(DISMISS_TASK_DURATION));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_TAB:
                    snapToPageRelative(event.isShiftPressed() ? -1 : 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    snapToPageRelative(mIsRtl ? -1 : 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    snapToPageRelative(mIsRtl ? 1 : -1);
                    return true;
                case KeyEvent.KEYCODE_DEL:
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    dismissTask((TaskView) getChildAt(getNextPage()), true /*animateTaskView*/,
                            true /*removeTask*/);
                    return true;
                case KeyEvent.KEYCODE_NUMPAD_DOT:
                    if (event.isAltPressed()) {
                        // Numpad DEL pressed while holding Alt.
                        dismissTask((TaskView) getChildAt(getNextPage()), true /*animateTaskView*/,
                                true /*removeTask*/);
                        return true;
                    }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public void snapToTaskAfterNext() {
        snapToPageRelative(1);
    }

    public float getContentAlpha() {
        return mContentAlpha;
    }

    public void setContentAlpha(float alpha) {
        mContentAlpha = alpha;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            TaskView child = getPageAt(i);
            if (!mRunningTaskTileHidden || child.getTask().key.id != mRunningTaskId) {
                getChildAt(i).setAlpha(alpha);
            }
        }

        int alphaInt = Math.round(alpha * 255);
        mEmptyMessagePaint.setAlpha(alphaInt);
        mEmptyIcon.setAlpha(alphaInt);
        updateClearAllButtonAlpha();
    }

    public void setAdjacentScale(float adjacentScale) {
        if (mAdjacentScale == adjacentScale) {
            return;
        }
        mAdjacentScale = adjacentScale;
        TaskView currTask = getPageAt(mCurrentPage);
        if (currTask == null) {
            return;
        }
        currTask.setScaleX(mAdjacentScale);
        currTask.setScaleY(mAdjacentScale);

        if (mCurrentPage - 1 >= 0) {
            TaskView adjacentTask = getPageAt(mCurrentPage - 1);
            float[] scaleAndTranslation = getAdjacentScaleAndTranslation(currTask, adjacentTask,
                    mAdjacentScale, 0);
            adjacentTask.setScaleX(scaleAndTranslation[0]);
            adjacentTask.setScaleY(scaleAndTranslation[0]);
            adjacentTask.setTranslationX(-scaleAndTranslation[1]);
            adjacentTask.setTranslationY(scaleAndTranslation[2]);
        }
        if (mCurrentPage + 1 < getChildCount()) {
            TaskView adjacentTask = getPageAt(mCurrentPage + 1);
            float[] scaleAndTranslation = getAdjacentScaleAndTranslation(currTask, adjacentTask,
                    mAdjacentScale, 0);
            adjacentTask.setScaleX(scaleAndTranslation[0]);
            adjacentTask.setScaleY(scaleAndTranslation[0]);
            adjacentTask.setTranslationX(scaleAndTranslation[1]);
            adjacentTask.setTranslationY(scaleAndTranslation[2]);
        }
    }

    private float[] getAdjacentScaleAndTranslation(TaskView currTask, TaskView adjacentTask,
            float currTaskToScale, float currTaskToTranslationY) {
        float displacement = currTask.getWidth() * (currTaskToScale - currTask.getCurveScale());
        sTempFloatArray[0] = currTaskToScale * adjacentTask.getCurveScale();
        sTempFloatArray[1] = mIsRtl ? -displacement : displacement;
        sTempFloatArray[2] = currTaskToTranslationY;
        return sTempFloatArray;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        child.setAlpha(mContentAlpha);
        setAdjacentScale(mAdjacentScale);
        onChildViewsChanged();
    }

    @Override
    public TaskView getPageAt(int index) {
        return (TaskView) getChildAt(index);
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
    }

    private void updateEmptyStateUi(boolean sizeChanged) {
        boolean hasValidSize = getWidth() > 0 && getHeight() > 0;
        if (sizeChanged && hasValidSize) {
            mEmptyTextLayout = null;
            mLastMeasureSize.set(getWidth(), getHeight());
        }
        updateClearAllButtonAlpha();

        if (!mShowEmptyMessage) return;

        // The icon needs to be centered. Need to scoll to horizontal 0 because with Clear-All
        // space on the right, it's not guaranteed that after deleting all tasks, the horizontal
        // scroll position will be zero.
        scrollTo(0, 0);

        if (hasValidSize && mEmptyTextLayout == null) {
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
            canvas.translate((mTempRect.left - mTempRect.right) / 2,
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
            TaskView centerTask = getPageAt(centerTaskIndex);
            if (taskIndex - 1 >= 0) {
                TaskView adjacentTask = getPageAt(taskIndex - 1);
                float[] scaleAndTranslation = getAdjacentScaleAndTranslation(centerTask,
                        adjacentTask, toScale, toTranslationY);
                scaleAndTranslation[1] = -scaleAndTranslation[1];
                anim.play(createAnimForChild(adjacentTask, scaleAndTranslation));
            }
            if (taskIndex + 1 < getPageCount()) {
                TaskView adjacentTask = getPageAt(taskIndex + 1);
                float[] scaleAndTranslation = getAdjacentScaleAndTranslation(centerTask,
                        adjacentTask, toScale, toTranslationY);
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

    private ObjectAnimator createAnimForChild(View child, float[] toScaleAndTranslation) {
        return ObjectAnimator.ofPropertyValuesHolder(child,
                        new PropertyListBuilder()
                                .scale(child.getScaleX() * toScaleAndTranslation[0])
                                .translationX(toScaleAndTranslation[1])
                                .translationY(toScaleAndTranslation[2])
                                .build());
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
        TaskViewDrawable drawable = new TaskViewDrawable(tv, this);
        getOverlay().add(drawable);

        ObjectAnimator drawableAnim =
                ObjectAnimator.ofFloat(drawable, TaskViewDrawable.PROGRESS, 1, 0);
        drawableAnim.setInterpolator(LINEAR);

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
                            onEndListener.logAction, Direction.DOWN,
                            TaskUtils.getComponentKeyForTask(task.key));
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
        View currChild = getChildAt(mCurrentPage);
        if (currChild != null) {
            currChild.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    @Override
    protected String getCurrentPageDescription() {
        return "";
    }

    private int additionalScrollForClearAllButton() {
        return (int) getResources().getDimension(
                R.dimen.clear_all_container_width) - getPaddingEnd();
    }

    @Override
    protected int computeMaxScrollX() {
        if (getChildCount() == 0) {
            return super.computeMaxScrollX();
        }

        // Allow a clear_all_container_width-sized gap after the last task.
        return super.computeMaxScrollX() + (mIsRtl ? 0 : additionalScrollForClearAllButton());
    }

    @Override
    protected int offsetForPageScrolls() {
        return mIsRtl ? additionalScrollForClearAllButton() : 0;
    }

    public void setClearAllButton(View clearAllButton) {
        mClearAllButton = clearAllButton;
        updateClearAllButtonAlpha();
    }

    private void onChildViewsChanged() {
        final int childCount = getChildCount();
        mClearAllButton.setAccessibilityTraversalAfter(
                childCount == 0 ? NO_ID : getChildAt(childCount - 1).getId());
        mClearAllButton.setVisibility(childCount == 0 ? INVISIBLE : VISIBLE);
    }

    public void revealClearAllButton() {
        scrollTo(mIsRtl ? 0 : computeMaxScrollX(), 0);
    }
}
