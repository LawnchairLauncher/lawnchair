/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.window.TaskConstants.TASK_CHILD_LAYER_COMPAT_UI;

import android.annotation.Nullable;
import android.app.AppCompatTaskInfo;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Window manager for the reachability education
 */
class ReachabilityEduWindowManager extends CompatUIWindowManagerAbstract {

    private static final int REACHABILITY_LEFT_OR_UP_POSITION = 0;
    private static final int REACHABILITY_RIGHT_OR_BOTTOM_POSITION = 2;

    private final CompatUIConfiguration mCompatUIConfiguration;

    private final ShellExecutor mMainExecutor;

    private boolean mIsLetterboxDoubleTapEnabled;

    private int mLetterboxVerticalPosition;

    private int mLetterboxHorizontalPosition;

    private int mTopActivityLetterboxWidth;

    private int mTopActivityLetterboxHeight;

    private long mNextHideTime = -1L;

    private boolean mForceUpdate = false;

    // We decided to force the visualization of the double-tap animated icons every time the user
    // double-taps.
    private boolean mHasUserDoubleTapped;

    // When the size of the letterboxed app changes and the icons are visible
    // we need to animate them.
    private boolean mHasLetterboxSizeChanged;

    private final BiConsumer<TaskInfo, ShellTaskOrganizer.TaskListener> mOnDismissCallback;

    private final Function<Integer, Integer> mDisappearTimeSupplier;

    @Nullable
    @VisibleForTesting
    ReachabilityEduLayout mLayout;

    ReachabilityEduWindowManager(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue,
            ShellTaskOrganizer.TaskListener taskListener, DisplayLayout displayLayout,
            CompatUIConfiguration compatUIConfiguration, ShellExecutor mainExecutor,
            BiConsumer<TaskInfo, ShellTaskOrganizer.TaskListener> onDismissCallback,
            Function<Integer, Integer> disappearTimeSupplier) {
        super(context, taskInfo, syncQueue, taskListener, displayLayout);
        final AppCompatTaskInfo appCompatTaskInfo = taskInfo.appCompatTaskInfo;
        mIsLetterboxDoubleTapEnabled = appCompatTaskInfo.isLetterboxDoubleTapEnabled();
        mLetterboxVerticalPosition = appCompatTaskInfo.topActivityLetterboxVerticalPosition;
        mLetterboxHorizontalPosition = appCompatTaskInfo.topActivityLetterboxHorizontalPosition;
        mTopActivityLetterboxWidth = appCompatTaskInfo.topActivityLetterboxWidth;
        mTopActivityLetterboxHeight = appCompatTaskInfo.topActivityLetterboxHeight;
        mCompatUIConfiguration = compatUIConfiguration;
        mMainExecutor = mainExecutor;
        mOnDismissCallback = onDismissCallback;
        mDisappearTimeSupplier = disappearTimeSupplier;
    }

    @Override
    protected int getZOrder() {
        return TASK_CHILD_LAYER_COMPAT_UI + 1;
    }

    @Override
    protected @Nullable View getLayout() {
        return mLayout;
    }

    @Override
    protected void removeLayout() {
        mLayout = null;
    }

    @Override
    protected boolean eligibleToShowLayout() {
        return mIsLetterboxDoubleTapEnabled
                && (mLetterboxVerticalPosition != -1 || mLetterboxHorizontalPosition != -1);
    }

    @Override
    protected View createLayout() {
        mLayout = inflateLayout();
        mLayout.inject(this);

        updateVisibilityOfViews();

        return mLayout;
    }

    @VisibleForTesting
    ReachabilityEduLayout inflateLayout() {
        return (ReachabilityEduLayout) LayoutInflater.from(mContext).inflate(
                R.layout.reachability_ui_layout, null);
    }

    @Override
    public boolean updateCompatInfo(TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener,
            boolean canShow) {
        final boolean prevIsLetterboxDoubleTapEnabled = mIsLetterboxDoubleTapEnabled;
        final int prevLetterboxVerticalPosition = mLetterboxVerticalPosition;
        final int prevLetterboxHorizontalPosition = mLetterboxHorizontalPosition;
        final int prevTopActivityLetterboxWidth = mTopActivityLetterboxWidth;
        final int prevTopActivityLetterboxHeight = mTopActivityLetterboxHeight;
        final AppCompatTaskInfo appCompatTaskInfo = taskInfo.appCompatTaskInfo;
        mIsLetterboxDoubleTapEnabled = appCompatTaskInfo.isLetterboxDoubleTapEnabled();
        mLetterboxVerticalPosition = appCompatTaskInfo.topActivityLetterboxVerticalPosition;
        mLetterboxHorizontalPosition = appCompatTaskInfo.topActivityLetterboxHorizontalPosition;
        mTopActivityLetterboxWidth = appCompatTaskInfo.topActivityLetterboxWidth;
        mTopActivityLetterboxHeight = appCompatTaskInfo.topActivityLetterboxHeight;
        mHasUserDoubleTapped = appCompatTaskInfo.isFromLetterboxDoubleTap();

        if (!super.updateCompatInfo(taskInfo, taskListener, canShow)) {
            return false;
        }

        mHasLetterboxSizeChanged = prevTopActivityLetterboxWidth != mTopActivityLetterboxWidth
                || prevTopActivityLetterboxHeight != mTopActivityLetterboxHeight;

        if (mHasUserDoubleTapped || prevIsLetterboxDoubleTapEnabled != mIsLetterboxDoubleTapEnabled
                || prevLetterboxVerticalPosition != mLetterboxVerticalPosition
                || prevLetterboxHorizontalPosition != mLetterboxHorizontalPosition
                || prevTopActivityLetterboxWidth != mTopActivityLetterboxWidth
                || prevTopActivityLetterboxHeight != mTopActivityLetterboxHeight) {
            updateVisibilityOfViews();
        }

        return true;
    }

    void forceUpdate(boolean forceUpdate) {
        mForceUpdate = forceUpdate;
    }

    @Override
    protected void onParentBoundsChanged() {
        if (mLayout == null) {
            return;
        }
        // Both the layout dimensions and dialog margins depend on the parent bounds.
        WindowManager.LayoutParams windowLayoutParams = getWindowLayoutParams();
        mLayout.setLayoutParams(windowLayoutParams);
        relayout(windowLayoutParams);
    }

    /** Gets the layout params. */
    protected WindowManager.LayoutParams getWindowLayoutParams() {
        View layout = getLayout();
        if (layout == null) {
            return new WindowManager.LayoutParams();
        }
        // Measure how big the hint is since its size depends on the text size.
        final Rect taskBounds = getTaskBounds();
        layout.measure(View.MeasureSpec.makeMeasureSpec(taskBounds.width(),
                        View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(taskBounds.height(),
                        View.MeasureSpec.EXACTLY));
        return getWindowLayoutParams(layout.getMeasuredWidth(), layout.getMeasuredHeight());
    }

    /**
     * @return Flags to use for the WindowManager layout
     */
    @Override
    protected int getWindowManagerLayoutParamsFlags() {
        return FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE;
    }

    @Override
    @VisibleForTesting
    public void updateSurfacePosition() {
        if (mLayout == null) {
            return;
        }
        updateSurfacePosition(0, 0);
    }

    void updateHideTime() {
        mNextHideTime = SystemClock.uptimeMillis() + getDisappearTimeMs();
    }

    private long getDisappearTimeMs() {
        return mDisappearTimeSupplier.apply(
                AccessibilityManager.FLAG_CONTENT_ICONS | AccessibilityManager.FLAG_CONTENT_TEXT);
    }

    private void updateVisibilityOfViews() {
        if (mLayout == null) {
            return;
        }
        final TaskInfo lastTaskInfo = getLastTaskInfo();
        final boolean hasSeenHorizontalReachabilityEdu =
                mCompatUIConfiguration.hasSeenHorizontalReachabilityEducation(lastTaskInfo);
        final boolean hasSeenVerticalReachabilityEdu =
                mCompatUIConfiguration.hasSeenVerticalReachabilityEducation(lastTaskInfo);
        final boolean eligibleForDisplayHorizontalEducation = mForceUpdate
                || !hasSeenHorizontalReachabilityEdu
                || (mHasUserDoubleTapped
                    && (mLetterboxHorizontalPosition == REACHABILITY_LEFT_OR_UP_POSITION
                        || mLetterboxHorizontalPosition == REACHABILITY_RIGHT_OR_BOTTOM_POSITION));
        final boolean eligibleForDisplayVerticalEducation = mForceUpdate
                || !hasSeenVerticalReachabilityEdu
                || (mHasUserDoubleTapped
                    && (mLetterboxVerticalPosition == REACHABILITY_LEFT_OR_UP_POSITION
                        || mLetterboxVerticalPosition == REACHABILITY_RIGHT_OR_BOTTOM_POSITION));

        if (mIsLetterboxDoubleTapEnabled && (eligibleForDisplayHorizontalEducation
                || eligibleForDisplayVerticalEducation)) {
            int availableWidth = getTaskBounds().width() - mTopActivityLetterboxWidth;
            int availableHeight = getTaskBounds().height() - mTopActivityLetterboxHeight;
            mLayout.handleVisibility(eligibleForDisplayHorizontalEducation,
                    eligibleForDisplayVerticalEducation,
                    mLetterboxVerticalPosition, mLetterboxHorizontalPosition, availableWidth,
                    availableHeight, mCompatUIConfiguration, lastTaskInfo);
            if (!mHasLetterboxSizeChanged) {
                updateHideTime();
                final long disappearTimeMs = getDisappearTimeMs();
                mMainExecutor.executeDelayed(this::hideReachability, disappearTimeMs);
                // If reachability education has been seen for the first time, trigger callback to
                // display aspect ratio settings button once reachability education disappears
                if (hasShownHorizontalReachabilityEduFirstTime(hasSeenHorizontalReachabilityEdu)
                        || hasShownVerticalReachabilityEduFirstTime(
                        hasSeenVerticalReachabilityEdu)) {
                    mMainExecutor.executeDelayed(this::triggerOnDismissCallback,
                            disappearTimeMs);
                }
            }
            mHasUserDoubleTapped = false;
        } else {
            mLayout.hideAllImmediately();
        }
    }

    /**
     * Compares the value of
     * {@link CompatUIConfiguration#hasSeenHorizontalReachabilityEducation} before and after the
     * layout is shown. Horizontal reachability education is considered seen for the first time if
     * prior to viewing the layout,
     * {@link CompatUIConfiguration#hasSeenHorizontalReachabilityEducation} is {@code false}
     * but becomes {@code true} once the current layout is shown.
     */
    private boolean hasShownHorizontalReachabilityEduFirstTime(
            boolean previouslyShownHorizontalReachabilityEducation) {
        return !previouslyShownHorizontalReachabilityEducation
                && mCompatUIConfiguration.hasSeenHorizontalReachabilityEducation(getLastTaskInfo());
    }

    /**
     * Compares the value of
     * {@link CompatUIConfiguration#hasSeenVerticalReachabilityEducation} before and after the
     * layout is shown. Horizontal reachability education is considered seen for the first time if
     * prior to viewing the layout,
     * {@link CompatUIConfiguration#hasSeenVerticalReachabilityEducation} is {@code false}
     * but becomes {@code true} once the current layout is shown.
     */
    private boolean hasShownVerticalReachabilityEduFirstTime(
            boolean previouslyShownVerticalReachabilityEducation) {
        return !previouslyShownVerticalReachabilityEducation
                && mCompatUIConfiguration.hasSeenVerticalReachabilityEducation(getLastTaskInfo());
    }

    private void triggerOnDismissCallback() {
        mOnDismissCallback.accept(getLastTaskInfo(), getTaskListener());
    }

    private void hideReachability() {
        if (mLayout == null || !shouldHideEducation()) {
            return;
        }
        mLayout.hideAllImmediately();
    }

    private boolean shouldHideEducation() {
        return SystemClock.uptimeMillis() >= mNextHideTime;
    }
}
