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
package com.android.launcher3.taskbar;

import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.views.AbstractSlideInView;

/** Wrapper for taskbar all apps with slide-in behavior. */
public class TaskbarAllAppsSlideInView extends
        AbstractSlideInView<TaskbarActivityContext> implements Insettable {

    private static final int DEFAULT_OPEN_DURATION = 500;
    private static final int DEFAULT_CLOSE_DURATION = 200;

    private TaskbarAllAppsContainerView mAppsView;

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Opens the all apps view. */
    public void show() {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        attachToContainer();

        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
        mOpenCloseAnimator.setInterpolator(AGGRESSIVE_EASE);
        mOpenCloseAnimator.setDuration(DEFAULT_OPEN_DURATION).start();
    }

    /** The apps container inside this view. */
    public TaskbarAllAppsContainerView getAppsView() {
        return mAppsView;
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, DEFAULT_CLOSE_DURATION);
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
}
