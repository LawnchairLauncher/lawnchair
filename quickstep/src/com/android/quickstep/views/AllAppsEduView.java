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

import static com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.OVERSHOOT_1_7;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALL_APPS_EDU_SHOWN;
import static com.android.quickstep.util.AnimUtils.clampToDuration;

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
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.launcher3.util.Themes;
import com.android.quickstep.util.MultiValueUpdateListener;

/**
 * View used to educate the user on how to access All Apps when in No Nav Button navigation mode.
 * If the user drags on the view, the animation is overridden so the user can swipe to All Apps or
 * Home.
 */
public class AllAppsEduView extends AbstractFloatingView {

    private Launcher mLauncher;
    private AllAppsEduTouchController mTouchController;

    @Nullable
    private AnimatorSet mAnimation;

    private GradientDrawable mCircle;
    private GradientDrawable mGradient;

    private int mCircleSizePx;
    private int mPaddingPx;
    private int mWidthPx;
    private int mMaxHeightPx;

    private boolean mCanInterceptTouch;

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
    protected boolean isOfType(int type) {
        return (type & TYPE_ALL_APPS_EDU) != 0;
    }

    @Override
    public boolean canInterceptEventsInSystemGestureRegion() {
        return true;
    }


    private boolean shouldInterceptTouch(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mCanInterceptTouch = (ev.getEdgeFlags() & EDGE_NAV_BAR) == 0;
        }
        return mCanInterceptTouch;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (shouldInterceptTouch(ev)) {
            mTouchController.onControllerTouchEvent(ev);
            updateAnimationOnTouchEvent(ev);
        }
        return true;
    }

    private void updateAnimationOnTouchEvent(MotionEvent ev) {
        if (mAnimation == null) {
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mAnimation.pause();
                return;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mAnimation.resume();
                return;
        }

        if (mTouchController.isDraggingOrSettling()) {
            mAnimation = null;
            handleClose(false);
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (shouldInterceptTouch(ev)) {
            mTouchController.onControllerInterceptTouchEvent(ev);
            updateAnimationOnTouchEvent(ev);
        }
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

        AnimatorPlaybackController stateAnimationController =
                mTouchController.initAllAppsAnimation();
        float maxAllAppsProgress = 0.75f;

        ValueAnimator intro = ValueAnimator.ofFloat(0, 1f);
        intro.setInterpolator(LINEAR);
        intro.setDuration(introDuration);
        intro.addUpdateListener((new MultiValueUpdateListener() {
            FloatProp mCircleAlpha = new FloatProp(0, 255,
                    clampToDuration(LINEAR, 0, firstPart, introDuration));
            FloatProp mCircleScale = new FloatProp(2f, 1f,
                    clampToDuration(OVERSHOOT_1_7, 0, firstPart, introDuration));
            FloatProp mDeltaY = new FloatProp(0, transY,
                    clampToDuration(FAST_OUT_SLOW_IN, firstPart, secondPart, introDuration));
            FloatProp mGradientAlpha = new FloatProp(0, 255,
                    clampToDuration(LINEAR, firstPart, secondPart * 0.3f, introDuration));

            @Override
            public void onUpdate(float progress, boolean initOnly) {
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
        mLauncher.getAppsView().setVisibility(View.VISIBLE);
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
        mTouchController = new AllAppsEduTouchController(mLauncher);

        int accentColor = Themes.getColorAccent(launcher);
        mGradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                Themes.getAttrBoolean(launcher, R.attr.isMainColorDark)
                        ? new int[]{0xB3FFFFFF, 0x00FFFFFF}
                        : new int[]{ColorUtils.setAlphaComponent(accentColor, 127),
                                ColorUtils.setAlphaComponent(accentColor, 0)});
        float r = mWidthPx / 2f;
        mGradient.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});

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
        AllAppsEduView view = (AllAppsEduView) launcher.getLayoutInflater().inflate(
                R.layout.all_apps_edu_view, dragLayer, false);
        view.init(launcher);
        launcher.getDragLayer().addView(view);
        launcher.getStatsLogManager().logger().log(LAUNCHER_ALL_APPS_EDU_SHOWN);

        view.requestLayout();
        view.playAnimation();
    }

    private static class AllAppsEduTouchController extends PortraitStatesTouchController {

        private AllAppsEduTouchController(Launcher l) {
            super(l);
        }

        @Override
        protected boolean canInterceptTouch(MotionEvent ev) {
            return true;
        }

        private AnimatorPlaybackController initAllAppsAnimation() {
            mFromState = NORMAL;
            mToState = ALL_APPS;
            mProgressMultiplier = initCurrentAnimation();
            return mCurrentAnimation;
        }

        private boolean isDraggingOrSettling() {
            return mDetector.isDraggingOrSettling();
        }
    }
}
