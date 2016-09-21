/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.TransformingTouchDelegate;

/**
 * A base container view, which supports resizing.
 */
public abstract class BaseContainerView extends FrameLayout
        implements DeviceProfile.LauncherLayoutChangeListener {

    protected int mContainerPaddingLeft;
    protected int mContainerPaddingRight;
    protected int mContainerPaddingTop;
    protected int mContainerPaddingBottom;

    protected final Drawable mBaseDrawable;
    private final Rect mBgPaddingRect = new Rect();

    private View mRevealView;
    private View mContent;

    private TransformingTouchDelegate mTouchDelegate;

    public BaseContainerView(Context context) {
        this(context, null);
    }

    public BaseContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP && this instanceof AllAppsContainerView) {
            mBaseDrawable = new ColorDrawable();
        } else {
            TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.BaseContainerView, defStyleAttr, 0);
            mBaseDrawable = a.getDrawable(R.styleable.BaseContainerView_revealBackground);
            a.recycle();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        grid.addLauncherLayoutChangedListener(this);

        View touchDelegateTargetView = getTouchDelegateTargetView();
        if (touchDelegateTargetView != null) {
            mTouchDelegate = new TransformingTouchDelegate(touchDelegateTargetView);
            ((View) touchDelegateTargetView.getParent()).setTouchDelegate(mTouchDelegate);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        grid.removeLauncherLayoutChangedListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContent = findViewById(R.id.main_content);
        mRevealView = findViewById(R.id.reveal_view);

        updatePaddings();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        getRevealView().getBackground().getPadding(mBgPaddingRect);

        View touchDelegateTargetView = getTouchDelegateTargetView();
        if (touchDelegateTargetView != null) {
            mTouchDelegate.setBounds(
                    touchDelegateTargetView.getLeft() - mBgPaddingRect.left,
                    touchDelegateTargetView.getTop() - mBgPaddingRect.top,
                    touchDelegateTargetView.getRight() + mBgPaddingRect.right,
                    touchDelegateTargetView.getBottom() + mBgPaddingRect.bottom);
        }
    }

    @Override
    public void onLauncherLayoutChanged() {
        updatePaddings();
    }

    public void setRevealDrawableColor(int color) {
        ((ColorDrawable) mBaseDrawable).setColor(color);
    }

    public final View getContentView() {
        return mContent;
    }

    public final View getRevealView() {
        return mRevealView;
    }

    private void updatePaddings() {
        Context context = getContext();
        Launcher launcher = Launcher.getLauncher(context);

        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP &&
                this instanceof AllAppsContainerView &&
                !launcher.getDeviceProfile().isVerticalBarLayout()) {
            mContainerPaddingLeft = mContainerPaddingRight = 0;
            mContainerPaddingTop = mContainerPaddingBottom = 0;
        } else {
            DeviceProfile grid = launcher.getDeviceProfile();
            int[] padding = grid.getContainerPadding(context);
            mContainerPaddingLeft = padding[0] + grid.edgeMarginPx;
            mContainerPaddingRight = padding[1] + grid.edgeMarginPx;
            if (!launcher.getDeviceProfile().isVerticalBarLayout()) {
                mContainerPaddingTop = mContainerPaddingBottom = grid.edgeMarginPx;
            } else {
                mContainerPaddingTop = mContainerPaddingBottom = 0;
            }
        }

        InsetDrawable revealDrawable = new InsetDrawable(mBaseDrawable,
                mContainerPaddingLeft, mContainerPaddingTop, mContainerPaddingRight,
                mContainerPaddingBottom);
        mRevealView.setBackground(revealDrawable);
        mContent.setBackground(revealDrawable);
    }

    public abstract View getTouchDelegateTargetView();
}
