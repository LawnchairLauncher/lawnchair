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
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
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
import static com.android.quickstep.TaskView.CURVE_FACTOR;
import static com.android.quickstep.TaskView.CURVE_INTERPOLATOR;

/**
 * A list of recent tasks.
 */
public class RecentsView extends PagedView implements Insettable {

    private static final Rect sTempStableInsets = new Rect();

    public static final int SCROLL_TYPE_NONE = 0;
    public static final int SCROLL_TYPE_TASK = 1;
    public static final int SCROLL_TYPE_WORKSPACE = 2;

    private final Launcher mLauncher;
    private QuickScrubController mQuickScrubController;
    private final ScrollState mScrollState = new ScrollState();
    private boolean mOverviewStateEnabled;
    private boolean mTaskStackListenerRegistered;
    private LayoutTransition mLayoutTransition;
    private Runnable mNextPageSwitchRunnable;

    /**
     * TODO: Call reloadIdNeeded in onTaskStackChanged.
     */
    private TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
            for (int i = mFirstTaskIndex; i < getChildCount(); i++) {
                final TaskView taskView = (TaskView) getChildAt(i);
                if (taskView.getTask().key.id == taskId) {
                    taskView.getThumbnail().setThumbnail(taskView.getTask(), snapshot);
                    return;
                }
            }
        }
    };

    private RecentsViewStateController mStateController;
    private int mFirstTaskIndex;

    private final RecentsModel mModel;
    private int mLoadPlanId = -1;

    // Only valid until the launcher state changes to NORMAL
    private int mRunningTaskId = -1;

    private Bitmap mScrim;
    private Paint mFadePaint;
    private Shader mFadeShader;
    private Matrix mFadeMatrix;
    private boolean mScrimOnLeft;

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
        setupLayoutTransition();
        setClipToOutline(true);

        mLauncher = Launcher.getLauncher(context);
        mQuickScrubController = new QuickScrubController(this);
        mModel = RecentsModel.getInstance(context);

        mScrollState.isRtl = mIsRtl;
    }

    public void updateThumbnail(int taskId, ThumbnailData thumbnailData) {
        for (int i = mFirstTaskIndex; i < getChildCount(); i++) {
            final TaskView taskView = (TaskView) getChildAt(i);
            if (taskView.getTask().key.id == taskId) {
                taskView.onTaskDataLoaded(taskView.getTask(), thumbnailData);
                taskView.setAlpha(1);
                return;
            }
        }
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

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile dp = Launcher.getLauncher(getContext()).getDeviceProfile();
        Rect padding = getPadding(dp, getContext());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.bottomMargin = padding.bottom;
        setLayoutParams(lp);

        setPadding(padding.left, padding.top, padding.right, 0);

        if (dp.isVerticalBarLayout()) {
            boolean wasScrimOnLeft = mScrimOnLeft;
            mScrimOnLeft = dp.isSeascape();

            if (mScrim == null || wasScrimOnLeft != mScrimOnLeft) {
                Drawable scrim = getContext().getDrawable(mScrimOnLeft
                        ? R.drawable.recents_horizontal_scrim_left
                        : R.drawable.recents_horizontal_scrim_right);
                if (scrim instanceof BitmapDrawable) {
                    mScrim = ((BitmapDrawable) scrim).getBitmap();
                    mFadePaint = new Paint();
                    mFadePaint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
                    mFadeShader = new BitmapShader(mScrim, TileMode.CLAMP, TileMode.REPEAT);
                    mFadeMatrix = new Matrix();
                } else {
                    mScrim = null;
                }
            }
        } else {
            mScrim = null;
            mFadePaint = null;
            mFadeShader = null;
            mFadeMatrix = null;
        }
    }

    public int getFirstTaskIndex() {
        return mFirstTaskIndex;
    }

    public boolean isTaskViewVisible(TaskView tv) {
        // For now, just check if it's the active task
        return indexOfChild(tv) == getNextPage();
    }

    public TaskView getTaskView(int taskId) {
        for (int i = getFirstTaskIndex(); i < getChildCount(); i++) {
            TaskView tv = (TaskView) getChildAt(i);
            if (tv.getTask().key.id == taskId) {
                return tv;
            }
        }
        return null;
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
    }

    private void applyLoadPlan(RecentsTaskLoadPlan loadPlan) {
        final RecentsTaskLoader loader = mModel.getRecentsTaskLoader();
        TaskStack stack = loadPlan != null ? loadPlan.getTaskStack() : null;
        if (stack == null) {
            removeAllViews();
            return;
        }

        int oldChildCount = getChildCount();

        // Ensure there are as many views as there are tasks in the stack (adding and trimming as
        // necessary)
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final ArrayList<Task> tasks = new ArrayList<>(stack.getTasks());
        setLayoutTransition(null);

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

        // Rebind and reset all task views
        for (int i = tasks.size() - 1; i >= 0; i--) {
            final Task task = tasks.get(i);
            final TaskView taskView = (TaskView) getChildAt(tasks.size() - i - 1 + mFirstTaskIndex);
            taskView.bind(task);
            taskView.setScaleX(1f);
            taskView.setScaleY(1f);
            taskView.setTranslationX(0f);
            taskView.setTranslationY(0f);
            taskView.setAlpha(1f);
            loader.loadTaskData(task);
        }

        if (oldChildCount != getChildCount()) {
            mQuickScrubController.snapToPageForCurrentQuickScrubSection();
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
        WindowManagerWrapper.getInstance().getStableInsets(sTempStableInsets);
        Rect padding = new Rect(profile.workspacePadding);

        float taskWidth = profile.widthPx - sTempStableInsets.left - sTempStableInsets.right;
        float taskHeight = profile.heightPx - sTempStableInsets.top - sTempStableInsets.bottom;

        float overviewHeight, overviewWidth;
        if (profile.isVerticalBarLayout()) {
            // Use the same padding on both sides for symmetry.
            float availableWidth = taskWidth - 2 * Math.max(padding.left, padding.right);
            float availableHeight = profile.availableHeightPx - padding.top
                    - sTempStableInsets.top - Math.max(padding.bottom,
                    profile.heightPx * (1 - OverviewState.getVerticalProgress(profile, context)));
            float scaledRatio = Math.min(availableWidth / taskWidth, availableHeight / taskHeight);
            overviewHeight = taskHeight * scaledRatio;
            overviewWidth = taskWidth * scaledRatio;

        } else {
            overviewHeight = profile.availableHeightPx - padding.top - padding.bottom
                    - sTempStableInsets.top;
            overviewWidth = taskWidth * overviewHeight / taskHeight;
        }

        padding.bottom = profile.availableHeightPx - padding.top - sTempStableInsets.top
                - Math.round(overviewHeight);
        padding.left = padding.right = (int) ((profile.availableWidthPx - overviewWidth) / 2);
        return padding;
    }

    /**
     * Sets the {@param outRect} to match the position of the first tile such that it is scaled
     * down to match the 2nd taskView.
     * @return returns the factor which determines the scaling factor for the second task.
     */
    public static float getScaledDownPageRect(DeviceProfile dp, Context context, Rect outRect) {
        getPageRect(dp, context, outRect);

        int pageSpacing = context.getResources()
                .getDimensionPixelSize(R.dimen.recents_page_spacing);
        float halfScreenWidth = dp.widthPx * 0.5f;
        float halfPageWidth = outRect.width() * 0.5f;
        float pageCenter = outRect.right + pageSpacing + halfPageWidth;
        float distanceFromCenter = Math.abs(halfScreenWidth - pageCenter);
        float distanceToReachEdge = halfScreenWidth + halfPageWidth + pageSpacing;
        float linearInterpolation = Math.min(1, distanceFromCenter / distanceToReachEdge);

        float scale = 1 - CURVE_INTERPOLATOR.getInterpolation(linearInterpolation) * CURVE_FACTOR;

        int topMargin = context.getResources()
                .getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
        outRect.top -= topMargin;
        Utilities.scaleRectAboutCenter(outRect, scale);
        outRect.top += (int) (scale * topMargin);
        return linearInterpolation;
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
        final int halfPageWidth = mScrollState.halfPageWidth = getNormalChildWidth() / 2;
        mScrollState.lastScrollType = SCROLL_TYPE_NONE;
        final int screenCenter = mInsets.left + getPaddingLeft() + getScrollX() + halfPageWidth;
        final int halfScreenWidth = getMeasuredWidth() / 2;
        final int pageSpacing = mPageSpacing;

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
        mRunningTaskId = -1;
        setCurrentPage(0);
    }

    public int getTaskCount() {
        return getChildCount() - mFirstTaskIndex;
    }

    public int getRunningTaskId() {
        return mRunningTaskId;
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
        boolean needsReload = false;
        if (getTaskCount() == 0) {
            needsReload = true;
            // Add an empty view for now
            setLayoutTransition(null);
            final TaskView taskView = (TaskView) LayoutInflater.from(getContext())
                    .inflate(R.layout.task, this, false);
            addView(taskView, mFirstTaskIndex);
            setLayoutTransition(mLayoutTransition);
        }
        if (!needsReload) {
            needsReload = !mModel.isLoadPlanValid(mLoadPlanId);
        }
        if (needsReload) {
            mLoadPlanId = mModel.loadTasks(runningTaskId, this::applyLoadPlan);
        }
        mRunningTaskId = runningTaskId;
        setCurrentPage(mFirstTaskIndex);
        if (mCurrentPage >= mFirstTaskIndex) {
            getPageAt(mCurrentPage).setAlpha(0);
        }
    }

    public QuickScrubController getQuickScrubController() {
        return mQuickScrubController;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mScrim == null) {
            super.draw(canvas);
            return;
        }

        final int flags = Canvas.HAS_ALPHA_LAYER_SAVE_FLAG;

        int length = mScrim.getWidth();
        int height = getHeight();
        int saveCount = canvas.getSaveCount();

        int scrimLeft;
        if (mScrimOnLeft) {
            scrimLeft = getScrollX();
        } else {
            scrimLeft = getScrollX() + getWidth() - length;
        }
        canvas.saveLayer(scrimLeft, 0, scrimLeft + length, height, null, flags);
        super.draw(canvas);

        mFadeMatrix.setTranslate(scrimLeft, 0);
        mFadeShader.setLocalMatrix(mFadeMatrix);
        mFadePaint.setShader(mFadeShader);
        canvas.drawRect(scrimLeft, 0, scrimLeft + length, height, mFadePaint);
        canvas.restoreToCount(saveCount);
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

        public float prevPageExtraWidth;
    }
}
