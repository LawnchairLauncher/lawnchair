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
import android.widget.FrameLayout;

/**
 * A base container view, which supports resizing.
 */
public class BaseContainerView extends FrameLayout implements Insettable {

    protected Rect mInsets = new Rect();
    protected Rect mFixedBounds = new Rect();
    protected int mFixedBoundsContainerInset;

    public BaseContainerView(Context context) {
        this(context, null);
    }

    public BaseContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFixedBoundsContainerInset = context.getResources().getDimensionPixelSize(
                R.dimen.container_fixed_bounds_inset);
    }

    @Override
    final public void setInsets(Rect insets) {
        mInsets.set(insets);
        onUpdateBackgrounds();
        onUpdatePaddings();
    }

    /**
     * Sets the fixed bounds for this container view.
     */
    final public void setFixedBounds(Rect fixedBounds) {
        if (!fixedBounds.isEmpty() && !fixedBounds.equals(mFixedBounds)) {
            mFixedBounds.set(fixedBounds);
            if (Launcher.DISABLE_ALL_APPS_SEARCH_INTEGRATION) {
                mFixedBounds.top = mInsets.top;
                mFixedBounds.bottom = getMeasuredHeight();
            }
            // To ensure that the child RecyclerView has the full width to handle touches right to
            // the edge of the screen, we only apply the top and bottom padding to the bounds
            mFixedBounds.inset(0, mFixedBoundsContainerInset);
            onFixedBoundsUpdated();
        }
        // Post the updates since they can trigger a relayout, and this call can be triggered from
        // a layout pass itself.
        post(new Runnable() {
            @Override
            public void run() {
                onUpdateBackgrounds();
                onUpdatePaddings();
            }
        });
    }

    /**
     * Update the UI in response to a change in the fixed bounds.
     */
    protected void onFixedBoundsUpdated() {
        // Do nothing
    }

    /**
     * Update the paddings in response to a change in the bounds or insets.
     */
    protected void onUpdatePaddings() {
        // Do nothing
    }

    /**
     * Update the backgrounds in response to a change in the bounds or insets.
     */
    protected void onUpdateBackgrounds() {
        // Do nothing
    }
}