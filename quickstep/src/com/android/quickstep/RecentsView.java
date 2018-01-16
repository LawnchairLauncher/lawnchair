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

package com.android.quickstep;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.uioverrides.OverviewState;
import com.android.launcher3.uioverrides.RecentsViewStateController;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.TaskStack;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.ArrayList;

import static com.android.launcher3.LauncherState.NORMAL;

/**
 * A list of recent tasks.
 */
public class RecentsView extends PagedView {

    public static final int SCROLL_TYPE_NONE = 0;
    public static final int SCROLL_TYPE_TASK = 1;
    public static final int SCROLL_TYPE_WORKSPACE = 2;

    private final Launcher mLauncher;
    private QuickScrubController mQuickScrubController;
    private final ScrollState mScrollState = new ScrollState();
    private boolean mOverviewStateEnabled;
    private boolean mTaskStackListenerRegistered;
    private LayoutTransition mLayoutTransition;

    /**
     * TODO: Call reloadIdNeeded in onTaskStackChanged.
     */
    private TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
            for (int i = mFirstTaskIndex; i < getChildCount(); i++) {
                final TaskView taskView = (TaskView) getChildAt(i);
                if (taskView.getTask().key.id == taskId) {
                    taskView.getThumbnail().setThumbnail(snapshot);
                    return;
                }
            }
        }
    };

    private RecentsViewStateController mStateController;
    private int mFirstTaskIndex;

    private final RecentsModel mModel;
    private int mLoadPlanId = -1;

    private Task mFirstTask;

    public RecentsView(Context context) {
        this(context, null);
    }

    public RecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setPageSpacing(getResources().getDimensionPixelSize(R.dimen.recents_page_spacing));
        enableFreeScroll(true);
        setClipChildren(true);
        setupLayoutTransition();

        mLauncher = Launcher.getLauncher(context);
        mQuickScrubController = new QuickScrubController(mLauncher);
        mModel = RecentsModel.getInstance(context);

        mScrollState.isRtl = mIsRtl;
    }

    private void setupLayoutTransition() {
        // We want to show layout transitions when pages are deleted, to close the gap.
        mLayoutTransition = new LayoutTransition();
        mLayoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        mLayoutTransition.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);

        mLayoutTransition.disableTransitionType(LayoutTransition.APPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        setLayoutTransition(mLayoutTransition);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Rect padding =
                getPadding(Launcher.getLauncher(getContext()).getDeviceProfile(), getContext());
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        mFirstTaskIndex = getPageCount();
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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateTaskStackListenerState();
    }

    public int getFirstTaskIndex() {
        return mFirstTaskIndex;
    }

    public void setStateController(RecentsViewStateController stateController) {
        mStateController = stateController;
    }

    public RecentsViewStateController getStateController() {
        return mStateController;
    }

    public void setOverviewStateEnabled(boolean enabled) {
        mOverviewStateEnabled = enabled;
        updateTaskStackListenerState();
    }

    private void applyLoadPlan(RecentsTaskLoadPlan loadPlan) {
        final RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
        TaskStack stack = loadPlan != null ? loadPlan.getTaskStack() : null;
        if (stack == null) {
            removeAllViews();
            return;
        }

        // Ensure there are as many views as there are tasks in the stack (adding and trimming as
        // necessary)
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final ArrayList<Task> tasks = new ArrayList<>(stack.getTasks());
        setLayoutTransition(null);

        if (mFirstTask != null) {
            // TODO: Handle this case here once we have a valid implementation for mFirstTask
            if (tasks.isEmpty() || !keysEquals(tasks.get(tasks.size() - 1), mFirstTask)) {
                // tasks.add(mFirstTask);
            }
        }

        final int requiredChildCount = tasks.size() + mFirstTaskIndex;
        for (int i = getChildCount(); i < requiredChildCount; i++) {
            final TaskView taskView = (TaskView) inflater.inflate(R.layout.task, this, false);
            addView(taskView);
        }
        while (getChildCount() > requiredChildCount) {
            final TaskView taskView = (TaskView) getChildAt(getChildCount() - 1);
            removeView(taskView);
            loader.unloadTaskData(taskView.getTask());
        }
        setLayoutTransition(mLayoutTransition);

        // Rebind all task views
        for (int i = tasks.size() - 1; i >= 0; i--) {
            final Task task = tasks.get(i);
            final TaskView taskView = (TaskView) getChildAt(tasks.size() - i - 1 + mFirstTaskIndex);
            taskView.bind(task);
            loader.loadTaskData(task);
        }
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

    private static Rect getPadding(DeviceProfile profile, Context context) {
        Rect stableInsets = new Rect();
        WindowManagerWrapper.getInstance().getStableInsets(stableInsets);
        Rect padding = new Rect(profile.workspacePadding);

        float taskWidth = profile.widthPx - stableInsets.left - stableInsets.right;
        float taskHeight = profile.heightPx - stableInsets.top - stableInsets.bottom;

        float overviewHeight, overviewWidth;
        if (profile.isVerticalBarLayout()) {
            // Use the same padding on both sides for symmetry.
            float availableWidth = taskWidth - 2 * Math.max(padding.left, padding.right);
            float availableHeight = profile.availableHeightPx - padding.top - padding.bottom
                    - stableInsets.top
                    - profile.heightPx * (1 - OverviewState.getVerticalProgress(profile, context));

            float scaledRatio = Math.min(availableWidth / taskWidth, availableHeight / taskHeight);
            overviewHeight = taskHeight * scaledRatio;
            overviewWidth = taskWidth * scaledRatio;

        } else {
            overviewHeight = profile.availableHeightPx - padding.top - padding.bottom
                    - stableInsets.top;
            overviewWidth = taskWidth * overviewHeight / taskHeight;
        }

        padding.bottom = profile.availableHeightPx - padding.top - stableInsets.top
                - Math.round(overviewHeight);
        padding.left = padding.right = (int) ((profile.availableWidthPx - overviewWidth) / 2);
        return padding;
    }

    public static void getPageRect(Launcher launcher, Rect outRect) {
        getPageRect(launcher.getDeviceProfile(), launcher, outRect);
    }

    public static void getPageRect(DeviceProfile grid, Context context, Rect outRect) {
        Rect targetPadding = getPadding(grid, context);
        Rect insets = grid.getInsets();
        outRect.set(
                targetPadding.left + insets.left,
                targetPadding.top + insets.top,
                grid.widthPx - targetPadding.right - insets.right,
                grid.heightPx - targetPadding.bottom - insets.bottom);
        outRect.top += context.getResources()
                .getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        updateCurveProperties();
    }

    /**
     * Scales and adjusts translation of adjacent pages as if on a curved carousel.
     */
    private void updateCurveProperties() {
        if (getPageCount() == 0 || getPageAt(0).getMeasuredWidth() == 0) {
            return;
        }
        final int halfScreenWidth = getMeasuredWidth() / 2;
        final int screenCenter = halfScreenWidth + getScrollX();
        final int pageSpacing = mPageSpacing;
        final int halfPageWidth = mScrollState.halfPageWidth = getNormalChildWidth() / 2;
        mScrollState.lastScrollType = SCROLL_TYPE_NONE;

        final int pageCount = getPageCount();
        for (int i = 0; i < pageCount; i++) {
            View page = getPageAt(i);
            int pageCenter = page.getLeft() + halfPageWidth;
            mScrollState.distanceFromScreenCenter = screenCenter - pageCenter;
            float distanceToReachEdge = halfScreenWidth + halfPageWidth + pageSpacing;
            mScrollState.linearInterpolation = Math.min(1,
                    Math.abs(mScrollState.distanceFromScreenCenter) / distanceToReachEdge);
            mScrollState.lastScrollType = ((PageCallbacks) page).onPageScroll(mScrollState);
        }
    }

    public void onTaskDismissed(TaskView taskView) {
        ActivityManagerWrapper.getInstance().removeTask(taskView.getTask().key.id);
        removeView(taskView);
        if (getTaskCount() == 0) {
            mLauncher.getStateManager().goToState(NORMAL);
        }
    }

    public void reset() {
        mFirstTask = null;
        setCurrentPage(0);
    }

    public int getTaskCount() {
        return getChildCount() - mFirstTaskIndex;
    }

    /**
     * Reloads the view if anything in recents changed.
     */
    public void reloadIfNeeded() {
        if (!mModel.isLoadPlanValid(mLoadPlanId)) {
            int taskId = -1;
            if (mFirstTask != null) {
                taskId = mFirstTask.key.id;
            }
            mLoadPlanId = mModel.loadTasks(taskId, this::applyLoadPlan);
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
    public void showTask(Task task) {
        boolean needsReload = false;
        boolean inflateFirstChild = true;
        if (getTaskCount() > 0) {
            TaskView tv = (TaskView) getChildAt(mFirstTaskIndex);
            inflateFirstChild = !keysEquals(tv.getTask(), task);
        }
        if (inflateFirstChild) {
            needsReload = true;
            setLayoutTransition(null);
            // Add an empty view for now
            final TaskView taskView = (TaskView) LayoutInflater.from(getContext())
                    .inflate(R.layout.task, this, false);
            addView(taskView, mFirstTaskIndex);
            taskView.bind(task);
            setLayoutTransition(mLayoutTransition);
        }
        if (!needsReload) {
            needsReload = !mModel.isLoadPlanValid(mLoadPlanId);
        }
        if (needsReload) {
            mLoadPlanId = mModel.loadTasks(task.key.id, this::applyLoadPlan);
        }
        mFirstTask = task;
        setCurrentPage(mFirstTaskIndex);
        ((TaskView) getPageAt(mCurrentPage)).setIconScale(0);
    }

    private static boolean keysEquals(Task t1, Task t2) {
        // TODO: Match the keys directly
        return t1.key.id == t2.key.id;
    }

    public QuickScrubController getQuickScrubController() {
        return mQuickScrubController;
    }

    public interface PageCallbacks {

        /**
         * Updates the page UI based on scroll params and returns the type of scroll
         * effect performed.
         *
         * @see #SCROLL_TYPE_NONE
         * @see #SCROLL_TYPE_TASK
         * @see #SCROLL_TYPE_WORKSPACE
         */
        int onPageScroll(ScrollState scrollState);
    }

    public static class ScrollState {

        public boolean isRtl;
        public int lastScrollType;

        public int halfPageWidth;
        public float distanceFromScreenCenter;
        public float linearInterpolation;
    }
}
