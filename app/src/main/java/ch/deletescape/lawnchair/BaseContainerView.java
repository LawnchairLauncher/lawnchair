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

package ch.deletescape.lawnchair;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import ch.deletescape.lawnchair.allapps.AllAppsContainerView;

/**
 * A base container view, which supports resizing.
 */
public abstract class BaseContainerView extends FrameLayout
        implements DeviceProfile.LauncherLayoutChangeListener {

    protected int mContainerPaddingLeft;
    protected int mContainerPaddingRight;
    protected int mContainerPaddingTop;
    protected int mContainerPaddingBottom;

    private InsetDrawable mRevealDrawable;
    protected Drawable mBaseDrawable;

    private View mRevealView;
    private View mContent;

    public BaseContainerView(Context context) {
        this(context, null);
    }

    public BaseContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (this instanceof AllAppsContainerView) {
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

    protected void updatePaddings() {
        Context context = getContext();
        Launcher launcher = Launcher.getLauncher(context);

        if (this instanceof AllAppsContainerView &&
                !launcher.getDeviceProfile().isVerticalBarLayout()) {
            mContainerPaddingLeft = mContainerPaddingRight = 0;
            mContainerPaddingTop = mContainerPaddingBottom = 0;
        } else {
            DeviceProfile grid = launcher.getDeviceProfile();
            int[] padding = grid.getContainerPadding();
            mContainerPaddingLeft = padding[0] + grid.edgeMarginPx;
            mContainerPaddingRight = padding[1] + grid.edgeMarginPx;
            if (!launcher.getDeviceProfile().isVerticalBarLayout()) {
                mContainerPaddingTop = mContainerPaddingBottom = grid.edgeMarginPx;
            } else {
                mContainerPaddingTop = mContainerPaddingBottom = 0;
            }
        }

        mRevealDrawable = new InsetDrawable(mBaseDrawable,
                mContainerPaddingLeft, mContainerPaddingTop, mContainerPaddingRight,
                mContainerPaddingBottom);
        mRevealView.setBackground(mRevealDrawable);
        if (!(this instanceof AllAppsContainerView)) {
            mContent.setBackground(mRevealDrawable);
        }
    }
}
