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

import static com.android.launcher3.states.RotationHelper.REQUEST_LOCK;
import static com.android.launcher3.states.RotationHelper.REQUEST_NONE;

import android.graphics.Rect;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.quickstep.ActivityControlHelper.LayoutListener;
import com.android.quickstep.WindowTransformSwipeHandler;

/**
 * Floating view which shows the task snapshot allowing it to be dragged and placed.
 */
public class LauncherLayoutListener extends AbstractFloatingView
        implements Insettable, LayoutListener {

    private final Launcher mLauncher;
    private WindowTransformSwipeHandler mHandler;

    public LauncherLayoutListener(Launcher launcher) {
        super(launcher, null);
        mLauncher = launcher;
        setVisibility(INVISIBLE);

        // For the duration of the gesture, lock the screen orientation to ensure that we do not
        // rotate mid-quickscrub
        launcher.getRotationHelper().setStateHandlerRequest(REQUEST_LOCK);
    }

    @Override
    public void setHandler(WindowTransformSwipeHandler handler) {
        mHandler = handler;
    }

    @Override
    public void setInsets(Rect insets) {
        if (mHandler != null) {
            mHandler.buildAnimationController();
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsOpen) {
            mIsOpen = false;
            // We don't support animate.
            mLauncher.getDragLayer().removeView(this);

            if (mHandler != null) {
                mHandler.layoutListenerClosed();
            }
        }
    }

    @Override
    public void open() {
        if (!mIsOpen) {
            mLauncher.getDragLayer().addView(this);
            mIsOpen = true;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(1, 1);
    }

    @Override
    public void logActionCommand(int command) {
        // We should probably log the weather
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_QUICKSTEP_PREVIEW) != 0;
    }

    @Override
    public void finish() {
        setHandler(null);
        close(false);
        mLauncher.getRotationHelper().setStateHandlerRequest(REQUEST_NONE);
    }
}
