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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.states.RotationHelper.REQUEST_NONE;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.FrameLayout;

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

    public static LauncherLayoutListener resetAndGet(Launcher launcher) {
        LauncherRecentsView lrv = launcher.getOverviewPanel();
        LauncherLayoutListener listener = lrv.mLauncherLayoutListener;
        if (listener.isOpen()) {
            listener.close(false);
        }
        listener.setHandler(null);
        return listener;
    }

    private final Launcher mLauncher;
    private final Paint mPaint = new Paint();
    private WindowTransformSwipeHandler mHandler;
    private RectF mCurrentRect;
    private float mCornerRadius;

    private boolean mWillNotDraw;

    /**
     * package private
     */
    LauncherLayoutListener(Launcher launcher) {
        super(launcher, null);
        mLauncher = launcher;
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        mWillNotDraw = willNotDraw();
        super.setWillNotDraw(false);
    }

    @Override
    public void update(boolean shouldFinish, boolean isLongSwipe, RectF currentRect,
                  float cornerRadius) {
        if (!ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (shouldFinish) {
                finish();
            }
            return;
        }

        mCurrentRect = currentRect;
        mCornerRadius = cornerRadius;

        setWillNotDraw(mCurrentRect == null || isLongSwipe);
        invalidate();
    }

    @Override
    public void setWillNotDraw(boolean willNotDraw) {
        // Prevent super call as that causes additional relayout.
        mWillNotDraw = willNotDraw;
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
    public void logActionCommand(int command) {
        // We should probably log the weather
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_QUICKSTEP_PREVIEW) != 0;
    }

    @Override
    public void finish() {
        close(false);
        setHandler(null);
        mLauncher.getRotationHelper().setStateHandlerRequest(REQUEST_NONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mWillNotDraw) {
            canvas.drawRoundRect(mCurrentRect, mCornerRadius, mCornerRadius, mPaint);
        }
    }
}
