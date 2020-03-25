/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.TriangleShape;

/**
 * A base class for arrow tip view in launcher
 */
public class ArrowTipView extends AbstractFloatingView {

    private static final long AUTO_CLOSE_TIMEOUT_MILLIS = 10 * 1000;
    private static final long SHOW_DELAY_MS = 200;
    private static final long SHOW_DURATION_MS = 300;
    private static final long HIDE_DURATION_MS = 100;

    protected final Launcher mLauncher;
    private final Handler mHandler = new Handler();
    private Runnable mOnClosed;

    public ArrowTipView(Context context) {
        super(context, null, 0);
        mLauncher = Launcher.getLauncher(context);
        init(context);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            close(true);
        }
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsOpen) {
            if (animate) {
                animate().alpha(0f)
                        .withLayer()
                        .setStartDelay(0)
                        .setDuration(HIDE_DURATION_MS)
                        .setInterpolator(Interpolators.ACCEL)
                        .withEndAction(() -> mLauncher.getDragLayer().removeView(this))
                        .start();
            } else {
                animate().cancel();
                mLauncher.getDragLayer().removeView(this);
            }
            if (mOnClosed != null) mOnClosed.run();
            mIsOpen = false;
        }
    }

    @Override
    public void logActionCommand(int command) {
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }

    private void init(Context context) {
        inflate(context, R.layout.arrow_toast, this);
        setOrientation(LinearLayout.VERTICAL);
        View dismissButton = findViewById(R.id.dismiss);
        dismissButton.setOnClickListener(view -> {
            handleClose(true);
        });

        View arrowView = findViewById(R.id.arrow);
        ViewGroup.LayoutParams arrowLp = arrowView.getLayoutParams();
        ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                arrowLp.width, arrowLp.height, false));
        Paint arrowPaint = arrowDrawable.getPaint();
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        arrowPaint.setColor(ContextCompat.getColor(getContext(), typedValue.resourceId));
        // The corner path effect won't be reflected in the shadow, but shouldn't be noticeable.
        arrowPaint.setPathEffect(new CornerPathEffect(
                context.getResources().getDimension(R.dimen.arrow_toast_corner_radius)));
        arrowView.setBackground(arrowDrawable);

        mIsOpen = true;

        mHandler.postDelayed(() -> handleClose(true), AUTO_CLOSE_TIMEOUT_MILLIS);
    }

    /**
     * Show Tip with specified string and Y location
     */
    public ArrowTipView show(String text, int top) {
        ((TextView) findViewById(R.id.text)).setText(text);
        mLauncher.getDragLayer().addView(this);

        DragLayer.LayoutParams params = (DragLayer.LayoutParams) getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.leftMargin = mLauncher.getDeviceProfile().workspacePadding.left;
        params.rightMargin = mLauncher.getDeviceProfile().workspacePadding.right;
        post(() -> setY(top - getHeight()));
        setAlpha(0);
        animate()
                .alpha(1f)
                .withLayer()
                .setStartDelay(SHOW_DELAY_MS)
                .setDuration(SHOW_DURATION_MS)
                .setInterpolator(Interpolators.DEACCEL)
                .start();
        return this;
    }

    /**
     * Register a callback fired when toast is hidden
     */
    public ArrowTipView setOnClosedCallback(Runnable runnable) {
        mOnClosed = runnable;
        return this;
    }
}
