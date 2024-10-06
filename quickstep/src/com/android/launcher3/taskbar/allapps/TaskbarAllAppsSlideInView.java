/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.allapps;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.launcher3.Flags.enablePredictiveBackGesture;
import static com.android.launcher3.touch.AllAppsSwipeController.ALL_APPS_FADE_MANUAL;
import static com.android.launcher3.touch.AllAppsSwipeController.SCRIM_FADE_MANUAL;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsViewController.TaskbarAllAppsCallbacks;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.AbstractSlideInView;

/** Wrapper for taskbar all apps with slide-in behavior. */
public class TaskbarAllAppsSlideInView extends AbstractSlideInView<TaskbarOverlayContext>
        implements Insettable, DeviceProfile.OnDeviceProfileChangeListener {
    private final Handler mHandler;

    private TaskbarAllAppsContainerView mAppsView;
    private float mShiftRange;
    private @Nullable Runnable mShowOnFullyAttachedToWindowRunnable;

    // Initialized in init.
    private TaskbarAllAppsCallbacks mAllAppsCallbacks;

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHandler = new Handler(Looper.myLooper());
    }

    void init(TaskbarAllAppsCallbacks callbacks) {
        mAllAppsCallbacks = callbacks;
    }

    /** Opens the all apps view. */
    void show(boolean animate) {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                removeOnAttachStateChangeListener(this);
                // Wait for view and its descendants to be fully attached before starting open.
                mShowOnFullyAttachedToWindowRunnable = () -> showOnFullyAttachedToWindow(animate);
                mHandler.post(mShowOnFullyAttachedToWindowRunnable);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                removeOnAttachStateChangeListener(this);
            }
        });
        attachToContainer();
    }

    private void showOnFullyAttachedToWindow(boolean animate) {
        mAllAppsCallbacks.onAllAppsTransitionStart(true);
        if (!animate) {
            mAllAppsCallbacks.onAllAppsTransitionEnd(true);
            setTranslationShift(TRANSLATION_SHIFT_OPENED);
            return;
        }

        setUpOpenAnimation(mAllAppsCallbacks.getOpenDuration());
        Animator animator = mOpenCloseAnimation.getAnimationPlayer();
        animator.setInterpolator(EMPHASIZED);
        animator.addListener(AnimatorListeners.forEndCallback(() -> {
            if (mIsOpen) {
                mAllAppsCallbacks.onAllAppsTransitionEnd(true);
            }
        }));
        animator.start();
    }

    @Override
    protected void onOpenCloseAnimationPending(PendingAnimation animation) {
        final boolean isOpening = mToTranslationShift == TRANSLATION_SHIFT_OPENED;

        if (mActivityContext.getDeviceProfile().isPhone) {
            final Interpolator allAppsFadeInterpolator = isOpening ? ALL_APPS_FADE_MANUAL
                    : Interpolators.reverse(ALL_APPS_FADE_MANUAL);
            animation.setViewAlpha(mAppsView, 1 - mToTranslationShift, allAppsFadeInterpolator);
        }

        mAllAppsCallbacks.onAllAppsAnimationPending(animation, isOpening);
    }

    @Override
    protected Interpolator getScrimInterpolator() {
        if (mActivityContext.getDeviceProfile().isTablet) {
            return super.getScrimInterpolator();
        }
        return mToTranslationShift == TRANSLATION_SHIFT_OPENED
                ? SCRIM_FADE_MANUAL
                : Interpolators.reverse(SCRIM_FADE_MANUAL);
    }

    /** The apps container inside this view. */
    TaskbarAllAppsContainerView getAppsView() {
        return mAppsView;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mShowOnFullyAttachedToWindowRunnable != null) {
            mHandler.removeCallbacks(mShowOnFullyAttachedToWindowRunnable);
            mShowOnFullyAttachedToWindowRunnable = null;
        }
        if (mIsOpen) {
            mAllAppsCallbacks.onAllAppsTransitionStart(false);
        }
        handleClose(animate, mAllAppsCallbacks.getCloseDuration());
    }

    @Override
    protected void onCloseComplete() {
        mAllAppsCallbacks.onAllAppsTransitionEnd(false);
        super.onCloseComplete();
    }

    @Override
    protected Interpolator getIdleInterpolator() {
        return EMPHASIZED;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TASKBAR_ALL_APPS) != 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAppsView = findViewById(R.id.apps_view);
        if (mActivityContext.getDeviceProfile().isPhone) {
            mAppsView.setAlpha(0);
        }
        mContent = mAppsView;

        // Setup header protection for search bar, if enabled.
        if (FeatureFlags.ENABLE_ALL_APPS_SEARCH_IN_TASKBAR.get()) {
            mAppsView.setOnInvalidateHeaderListener(this::invalidate);
        }

        DeviceProfile dp = mActivityContext.getDeviceProfile();
        setShiftRange(dp.allAppsShiftRange);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
        if (enablePredictiveBackGesture()) {
            mAppsView.getAppsRecyclerViewContainer().setOutlineProvider(mViewOutlineProvider);
            mAppsView.getAppsRecyclerViewContainer().setClipToOutline(true);
            OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
            if (dispatcher != null) {
                dispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, null);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
        if (enablePredictiveBackGesture()) {
            mAppsView.getAppsRecyclerViewContainer().setOutlineProvider(null);
            mAppsView.getAppsRecyclerViewContainer().setClipToOutline(false);
            OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
            if (dispatcher != null) {
                dispatcher.unregisterOnBackInvokedCallback(null);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // We should call drawOnScrimWithBottomOffset() rather than
        // drawOnScrimWithScale(). Because
        // for taskbar all apps, the scrim view is a child view of AbstractSlideInView.
        // Thus scaling
        // down in AbstractSlideInView#onScaleProgressChanged() with SCALE_PROPERTY has
        // already
        // done the job - there is no need to re-apply scale effect here. But it also
        // means we need
        // to pass extra bottom offset to background scrim to fill the bottom gap during
        // predictive
        // back swipe.
        mAppsView.drawOnScrimWithBottomOffset(canvas, getBottomOffsetPx());
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setTranslationShift(mTranslationShift);
    }

    @Override
    protected int getScrimColor(Context context) {
        return mActivityContext.getDeviceProfile().isPhone
                ? Themes.getAttrColor(context, R.attr.allAppsScrimColor)
                : context.getColor(R.color.widgets_picker_scrim);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !mAppsView.shouldContainerScroll(ev)
                    || getTopOpenViewWithType(
                            mActivityContext, TYPE_TOUCH_CONTROLLER_NO_INTERCEPT) != null;
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    @Override
    public void setInsets(Rect insets) {
        mAppsView.setInsets(insets);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        setShiftRange(dp.allAppsShiftRange);
        setTranslationShift(TRANSLATION_SHIFT_OPENED);
    }

    private void setShiftRange(float shiftRange) {
        mShiftRange = shiftRange;
    }

    @Override
    protected float getShiftRange() {
        return mShiftRange;
    }

    @Override
    protected boolean isEventOverContent(MotionEvent ev) {
        return getPopupContainer().isEventOverView(mAppsView.getVisibleContainerView(), ev);
    }

    /**
     * In taskbar all apps search mode, we should scale down content inside all
     * apps, rather
     * than the whole all apps bottom sheet, to indicate we will navigate back
     * within the all apps.
     */
    @Override
    public boolean shouldAnimateContentViewInBackSwipe() {
        return mAllAppsCallbacks.canHandleSearchBackInvoked();
    }

    @Override
    protected void onUserSwipeToDismissProgressChanged() {
        super.onUserSwipeToDismissProgressChanged();
        mAppsView.setClipChildren(!mIsDismissInProgress);
        mAppsView.getAppsRecyclerViewContainer().setClipChildren(!mIsDismissInProgress);
    }

    @Override
    public void onBackInvoked() {
        if (mAllAppsCallbacks.handleSearchBackInvoked()) {
            // We need to scale back taskbar all apps if we navigate back within search
            // inside all
            // apps
            post(this::animateSwipeToDismissProgressToStart);
        } else {
            super.onBackInvoked();
        }
    }
}
