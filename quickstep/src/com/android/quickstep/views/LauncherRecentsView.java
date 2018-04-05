/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.LauncherAppTransitionManagerImpl.ALL_APPS_PROGRESS_OFF_SCREEN;
import static com.android.launcher3.LauncherState.ALL_APPS_HEADER_EXTRA;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.AnyThread;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewDebug;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.quickstep.util.LayoutUtils;

/**
 * {@link RecentsView} used in Launcher activity
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherRecentsView extends RecentsView<Launcher> {

    public static final FloatProperty<LauncherRecentsView> TRANSLATION_Y_FACTOR =
            new FloatProperty<LauncherRecentsView>("translationYFactor") {

                @Override
                public void setValue(LauncherRecentsView view, float v) {
                    view.setTranslationYFactor(v);
                }

                @Override
                public Float get(LauncherRecentsView view) {
                    return view.mTranslationYFactor;
                }
            };

    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTranslationYFactor;

    public LauncherRecentsView(Context context) {
        this(context, null);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setContentAlpha(0);
    }

    @Override
    protected void onAllTasksRemoved() {
        mActivity.getStateManager().goToState(NORMAL);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setTranslationYFactor(mTranslationYFactor);
    }

    public void setTranslationYFactor(float translationFactor) {
        mTranslationYFactor = translationFactor;
        setTranslationY(mTranslationYFactor * (getPaddingBottom() - getPaddingTop()));
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateEmptyMessage();
    }

    @Override
    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }

    /**
     * Animates adjacent tasks and translate hotseat off screen as well.
     */
    @Override
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(TaskView tv) {
        AnimatorSet anim = super.createAdjacentPageAnimForTaskLaunch(tv);

        float allAppsProgressOffscreen = ALL_APPS_PROGRESS_OFF_SCREEN;
        LauncherState state = mActivity.getStateManager().getState();
        if ((state.getVisibleElements(mActivity) & ALL_APPS_HEADER_EXTRA) != 0) {
            float maxShiftRange = mActivity.getDeviceProfile().heightPx;
            float currShiftRange = mActivity.getAllAppsController().getShiftRange();
            allAppsProgressOffscreen = 1f + (maxShiftRange - currShiftRange) / maxShiftRange;
        }
        anim.play(ObjectAnimator.ofFloat(
                mActivity.getAllAppsController(), ALL_APPS_PROGRESS, allAppsProgressOffscreen));
        return anim;
    }

    @Override
    protected void getTaskSize(DeviceProfile dp, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(getContext(), dp, outRect);
    }

    @AnyThread
    public static void getPageRect(DeviceProfile grid, Context context, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(context, grid, outRect);
    }
}
