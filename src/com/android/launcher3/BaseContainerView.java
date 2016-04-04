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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A base container view, which supports resizing.
 */
public abstract class BaseContainerView extends FrameLayout implements Insettable {

    private final static String TAG = "BaseContainerView";

    // The window insets
    private final Rect mInsets = new Rect();
    // The computed padding to apply to the container to achieve the container bounds
    protected final Rect mContentPadding = new Rect();
    // The inset to apply to the edges and between the search bar and the container
    private final int mContainerBoundsInset;

    private final Drawable mRevealDrawable;

    private View mRevealView;
    private View mContent;

    protected final int mHorizontalPadding;

    public BaseContainerView(Context context) {
        this(context, null);
    }

    public BaseContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContainerBoundsInset = getResources().getDimensionPixelSize(R.dimen.container_bounds_inset);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BaseContainerView, defStyleAttr, 0);
        mRevealDrawable = a.getDrawable(R.styleable.BaseContainerView_revealBackground);
        a.recycle();

        int maxSize = getResources().getDimensionPixelSize(R.dimen.container_max_width);
        int minMargin = getResources().getDimensionPixelSize(R.dimen.container_min_margin);
        int width = ((Launcher) context).getDeviceProfile().availableWidthPx;

        if (maxSize > 0) {
            mHorizontalPadding = Math.max(minMargin, (width - maxSize) / 2);
        } else {
            mHorizontalPadding = Math.max(minMargin,
                    (int) getResources().getFraction(R.fraction.container_margin, width, 1));
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContent = findViewById(R.id.main_content);
        mRevealView = findViewById(R.id.reveal_view);
    }

    @Override
    final public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateBackgroundAndPaddings();
    }

    /**
     * Sets the search bar bounds for this container view to match.
     */
    final public void setSearchBarBounds(Rect bounds) {
        // Post the updates since they can trigger a relayout, and this call can be triggered from
        // a layout pass itself.
        post(new Runnable() {
            @Override
            public void run() {
                updateBackgroundAndPaddings();
            }
        });
    }

    /**
     * Update the backgrounds and padding in response to a change in the bounds or insets.
     */
    protected void updateBackgroundAndPaddings() {
        Rect padding;
        padding = new Rect(
                mHorizontalPadding,
                mInsets.top + mContainerBoundsInset,
                mHorizontalPadding,
                mInsets.bottom + mContainerBoundsInset
        );

        // The container padding changed, notify the container.
        if (!padding.equals(mContentPadding)) {
            mContentPadding.set(padding);
            onUpdateBackgroundAndPaddings(padding);
        }
    }

    private void onUpdateBackgroundAndPaddings(Rect padding) {
        // Apply the top-bottom padding to itself so that the launcher transition is
        // clipped correctly
        setPadding(0, padding.top, 0, padding.bottom);

        InsetDrawable background = new InsetDrawable(mRevealDrawable,
                padding.left, 0, padding.right, 0);
        mRevealView.setBackground(background.getConstantState().newDrawable());
        mContent.setBackground(background);

        // We let the content have a intent background, but still have full width.
        // This allows the scroll bar to be used responsive outside the background bounds as well.
        mContent.setPadding(0, 0, 0, 0);

        Rect bgPadding = new Rect();
        background.getPadding(bgPadding);
        onUpdateBgPadding(padding, bgPadding);
    }

    protected abstract void onUpdateBgPadding(Rect padding, Rect bgPadding);

    public final View getContentView() {
        return mContent;
    }

    public final View getRevealView() {
        return mRevealView;
    }
}