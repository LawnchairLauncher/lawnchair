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

import static com.android.launcher3.anim.Interpolators.EMPHASIZED;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsViewController.TaskbarAllAppsCallbacks;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.views.AbstractSlideInView;

/** Wrapper for taskbar all apps with slide-in behavior. */
public class TaskbarAllAppsSlideInView extends AbstractSlideInView<TaskbarOverlayContext>
        implements Insettable, DeviceProfile.OnDeviceProfileChangeListener {
    private TaskbarAllAppsContainerView mAppsView;
    private float mShiftRange;

    // Initialized in init.
    private TaskbarAllAppsCallbacks mAllAppsCallbacks;

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void init(TaskbarAllAppsCallbacks callbacks) {
        mAllAppsCallbacks = callbacks;
    }

    /** Opens the all apps view. */
    void show(boolean animate) {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        attachToContainer();

        if (animate) {
            mOpenCloseAnimator.setValues(
                    PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
            mOpenCloseAnimator.setInterpolator(EMPHASIZED);
            mOpenCloseAnimator.setDuration(mAllAppsCallbacks.getOpenDuration()).start();
        } else {
            mTranslationShift = TRANSLATION_SHIFT_OPENED;
        }
    }

    /** The apps container inside this view. */
    TaskbarAllAppsContainerView getAppsView() {
        return mAppsView;
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, mAllAppsCallbacks.getCloseDuration());
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
        mContent = mAppsView;

        DeviceProfile dp = mActivityContext.getDeviceProfile();
        setShiftRange(dp.allAppsShiftRange);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setTranslationShift(mTranslationShift);
    }

    @Override
    protected int getScrimColor(Context context) {
        return context.getColor(R.color.widgets_picker_scrim);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !mAppsView.shouldContainerScroll(ev);
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
}
