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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;

/**
 * A base container view, which supports resizing.
 */
public abstract class BaseContainerView extends LinearLayout implements Insettable {

    private final static String TAG = "BaseContainerView";

    // The window insets
    private Rect mInsets = new Rect();
    // The bounds of the search bar.  Only the left, top, right are used to inset the
    // search bar and the height is determined by the measurement of the layout
    private Rect mFixedSearchBarBounds = new Rect();
    // The computed bounds of the search bar
    private Rect mSearchBarBounds = new Rect();
    // The computed bounds of the container
    protected Rect mContentBounds = new Rect();
    // The computed padding to apply to the container to achieve the container bounds
    private Rect mContentPadding = new Rect();
    // The inset to apply to the edges and between the search bar and the container
    private int mContainerBoundsInset;
    private boolean mHasSearchBar;

    public BaseContainerView(Context context) {
        this(context, null);
    }

    public BaseContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContainerBoundsInset = getResources().getDimensionPixelSize(R.dimen.container_bounds_inset);
    }

    @Override
    final public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateBackgroundAndPaddings();
    }

    protected void setHasSearchBar() {
        mHasSearchBar = true;
    }

    /**
     * Sets the search bar bounds for this container view to match.
     */
    final public void setSearchBarBounds(Rect bounds) {
        if (LauncherAppState.isDogfoodBuild() && !isValidSearchBarBounds(bounds)) {
            Log.e(TAG, "Invalid search bar bounds: " + bounds);
        }

        mFixedSearchBarBounds.set(bounds);

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
        Rect searchBarBounds = new Rect();
        if (!isValidSearchBarBounds(mFixedSearchBarBounds)) {
            // Use the default bounds
            padding = new Rect(mInsets.left + mContainerBoundsInset,
                    (mHasSearchBar ? 0 : (mInsets.top + mContainerBoundsInset)),
                    mInsets.right + mContainerBoundsInset,
                    mInsets.bottom + mContainerBoundsInset);

            // Special case -- we have the search bar, but no specific bounds, so just give it
            // the inset bounds without a height.
            searchBarBounds.set(mInsets.left + mContainerBoundsInset,
                    mInsets.top + mContainerBoundsInset,
                    getMeasuredWidth() - (mInsets.right + mContainerBoundsInset), 0);
        } else {
            // Use the search bounds, if there is a search bar, the bounds will contain
            // the offsets for the insets so we can ignore that
            padding = new Rect(mFixedSearchBarBounds.left,
                    (mHasSearchBar ? 0 : (mInsets.top + mContainerBoundsInset)),
                    getMeasuredWidth() - mFixedSearchBarBounds.right,
                    mInsets.bottom + mContainerBoundsInset);

            // Use the search bounds
            searchBarBounds.set(mFixedSearchBarBounds);
        }

        // If either the computed container padding has changed, or the computed search bar bounds
        // has changed, then notify the container
        if (!padding.equals(mContentPadding) || !searchBarBounds.equals(mSearchBarBounds)) {
            mContentPadding.set(padding);
            mContentBounds.set(padding.left, padding.top,
                    getMeasuredWidth() - padding.right,
                    getMeasuredHeight() - padding.bottom);
            mSearchBarBounds.set(searchBarBounds);
            onUpdateBackgroundAndPaddings(mSearchBarBounds, padding);
        }
    }

    /**
     * To be implemented by container views to update themselves when the bounds changes.
     */
    protected abstract void onUpdateBackgroundAndPaddings(Rect searchBarBounds, Rect padding);

    /**
     * Returns whether the search bar bounds we got are considered valid.
     */
    private boolean isValidSearchBarBounds(Rect searchBarBounds) {
        return !searchBarBounds.isEmpty() &&
                searchBarBounds.right <= getMeasuredWidth() &&
                searchBarBounds.bottom <= getMeasuredHeight();
    }
}