/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_7;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALL_APPS_EDU_SHOWN;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.Themes;
import com.android.quickstep.util.MultiValueUpdateListener;

/**
 * View used to educate the user on how to access All Apps when in No Nav Button navigation mode.
 * Consumes all touches until after the animation is completed and the view is removed.
 */
public class AllAppsEduView extends AbstractFloatingView {

    private Launcher mLauncher;

    private AnimatorSet mAnimation;

    private GradientDrawable mCircle;
    private GradientDrawable mGradient;

    private int mCircleSizePx;
    private int mPaddingPx;
    private int mWidthPx;
    private int mMaxHeightPx;

    public AllAppsEduView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCircle = (GradientDrawable) context.getDrawable(R.drawable.all_apps_edu_circle);
        mCircleSizePx = getResources().getDimensionPixelSize(R.dimen.swipe_edu_circle_size);
        mPaddingPx = getResources().getDimensionPixelSize(R.dimen.swipe_edu_padding);
        mWidthPx = getResources().getDimensionPixelSize(R.dimen.swipe_edu_width);
        mMaxHeightPx = getResources().getDimensionPixelSize(R.dimen.swipe_edu_max_height);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mGradient.draw(canvas);
        mCircle.draw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIsOpen = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsOpen = false;
    }

    @Override
    protected void handleClose(boolean animate) {
        mLauncher.getDragLayer().removeView(this);
    }

    @Override
    public void logActionCommand(int command) {
        // TODO
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ALL_APPS_EDU) != 0;
    }

    @Override
    public boolean onBackPressed() {
        return true;
    }

    @Override
    public boolean canInterceptEventsInSystemGestureRegion() {
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    private void playAnimation() {
        if (mAnimation != null) {
            return;
        }
        mAnimation = new AnimatorSet();

        final Rect circleBoundsOg = new Rect(mCircle.getBounds());
        final Rect gradientBoundsOg = new Rect(mGradient.getBounds());
        final Rect temp = new Rect();
        final float transY = mMaxHeightPx - mCircleSizePx - mPaddingPx;

        // 1st: Circle alpha/scale
        int firstPart = 600;
        // 2nd: Circle animates upwards, Gradient alpha fades in, Gradient grows, All Apps hint
        int secondPart = 1200;
        int introDuration = firstPart + secondPart;

        StateAnimationConfig config = new StateAnimationConfig();
        config.setInterpolator(ANIM_ALL_APPS_FADE, Interpolators.clampToProgress(ACCEL,
                0, 0.08f));
        config.duration = secondPart;
        config.userControlled = false;
        AnimatorPlaybackController stateAnimationController =
                mLauncher.getStateManager().createAnimationToNewWorkspace(ALL_APPS, config);
        float maxAllAppsProgress = mLauncher.getDeviceProfile().isLandscape ? 0.35f : 0.15f;

        AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
        PendingAnimation allAppsAlpha = new PendingAnimation(config.duration);
        allAppsController.setAlphas(ALL_APPS, config, allAppsAlpha);
        mAnimation.play(allAppsAlpha.buildAnim());

        ValueAnimator intro = ValueAnimator.ofFloat(0, 1f);
        intro.setInterpolator(LINEAR);
        intro.setDuration(introDuration);
        intro.addUpdateListener((new MultiValueUpdateListener() {
            FloatProp mCircleAlpha = new FloatProp(0, 255, 0, firstPart, LINEAR);
            FloatProp mCircleScale = new FloatProp(2f, 1f, 0, firstPart, OVERSHOOT_1_7);
            FloatProp mDeltaY = new FloatProp(0, transY, firstPart, secondPart, FAST_OUT_SLOW_IN);
            FloatProp mGradientAlpha = new FloatProp(0, 255, firstPart, secondPart * 0.3f, LINEAR);

            @Override
            public void onUpdate(float progress) {
                temp.set(circleBoundsOg);
                temp.offset(0, (int) -mDeltaY.value);
                Utilities.scaleRectAboutCenter(temp, mCircleScale.value);
                mCircle.setBounds(temp);
                mCircle.setAlpha((int) mCircleAlpha.value);
                mGradient.setAlpha((int) mGradientAlpha.value);

                temp.set(gradientBoundsOg);
                temp.top -= mDeltaY.value;
                mGradient.setBounds(temp);
                invalidate();

                float stateProgress = Utilities.mapToRange(mDeltaY.value, 0, transY, 0,
                        maxAllAppsProgress, LINEAR);
                stateAnimationController.setPlayFraction(stateProgress);
            }
        }));
        intro.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCircle.setAlpha(0);
                mGradient.setAlpha(0);
            }
        });
        mAnimation.play(intro);

        ValueAnimator closeAllApps = ValueAnimator.ofFloat(maxAllAppsProgress, 0f);
        closeAllApps.addUpdateListener(valueAnimator -> {
            stateAnimationController.setPlayFraction((float) valueAnimator.getAnimatedValue());
        });
        closeAllApps.setInterpolator(FAST_OUT_SLOW_IN);
        closeAllApps.setStartDelay(introDuration);
        closeAllApps.setDuration(250);
        mAnimation.play(closeAllApps);

        mAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimation = null;
                // Handles cancelling the animation used to hint towards All Apps.
                mLauncher.getStateManager().goToState(NORMAL, false);
                handleClose(false);
            }
        });
        mAnimation.start();
    }

    private void init(Launcher launcher) {
        mLauncher = launcher;

        int accentColor = Themes.getColorAccent(launcher);
        mGradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                Themes.getAttrBoolean(launcher, R.attr.isMainColorDark)
                        ? new int[] {0xB3FFFFFF, 0x00FFFFFF}
                        : new int[] {ColorUtils.setAlphaComponent(accentColor, 127),
                                ColorUtils.setAlphaComponent(accentColor, 0)});
        float r = mWidthPx / 2f;
        mGradient.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});

        int top = mMaxHeightPx - mCircleSizePx + mPaddingPx;
        mCircle.setBounds(mPaddingPx, top, mPaddingPx + mCircleSizePx, top + mCircleSizePx);
        mGradient.setBounds(0, mMaxHeightPx - mCircleSizePx, mWidthPx, mMaxHeightPx);

        DeviceProfile grid = launcher.getDeviceProfile();
        DragLayer.LayoutParams lp = new DragLayer.LayoutParams(mWidthPx, mMaxHeightPx);
        lp.ignoreInsets = true;
        lp.leftMargin = (grid.widthPx - mWidthPx) / 2;
        lp.topMargin = grid.heightPx - grid.hotseatBarSizePx - mMaxHeightPx;
        setLayoutParams(lp);
    }

    /**
     * Shows the All Apps education view and plays the animation.
     */
    public static void show(Launcher launcher) {
        final DragLayer dragLayer = launcher.getDragLayer();
        ViewGroup parent = (ViewGroup) dragLayer.getParent();
        AllAppsEduView view = launcher.getViewCache().getView(R.layout.all_apps_edu_view,
                launcher, parent);
        view.init(launcher);
        launcher.getDragLayer().addView(view);
        launcher.getStatsLogManager().logger().log(LAUNCHER_ALL_APPS_EDU_SHOWN);

        view.requestLayout();
        view.playAnimation();
    }
}
