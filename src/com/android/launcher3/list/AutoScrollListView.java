/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher3.list;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * A ListView that can be asked to scroll (smoothly or otherwise) to a specific
 * position.  This class takes advantage of similar functionality that exists
 * in {@link ListView} and enhances it.
 */
public class AutoScrollListView extends ListView {

    /**
     * Position the element at about 1/3 of the list height
     */
    private static final float PREFERRED_SELECTION_OFFSET_FROM_TOP = 0.33f;

    private int mRequestedScrollPosition = -1;
    private boolean mSmoothScrollRequested;

    public AutoScrollListView(Context context) {
        super(context);
    }

    public AutoScrollListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoScrollListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Brings the specified position to view by optionally performing a jump-scroll maneuver:
     * first it jumps to some position near the one requested and then does a smooth
     * scroll to the requested position.  This creates an impression of full smooth
     * scrolling without actually traversing the entire list.  If smooth scrolling is
     * not requested, instantly positions the requested item at a preferred offset.
     */
    public void requestPositionToScreen(int position, boolean smoothScroll) {
        mRequestedScrollPosition = position;
        mSmoothScrollRequested = smoothScroll;
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (mRequestedScrollPosition == -1) {
            return;
        }

        final int position = mRequestedScrollPosition;
        mRequestedScrollPosition = -1;

        int firstPosition = getFirstVisiblePosition() + 1;
        int lastPosition = getLastVisiblePosition();
        if (position >= firstPosition && position <= lastPosition) {
            return; // Already on screen
        }

        final int offset = (int) (getHeight() * PREFERRED_SELECTION_OFFSET_FROM_TOP);
        if (!mSmoothScrollRequested) {
            setSelectionFromTop(position, offset);

            // Since we have changed the scrolling position, we need to redo child layout
            // Calling "requestLayout" in the middle of a layout pass has no effect,
            // so we call layoutChildren explicitly
            super.layoutChildren();

        } else {
            // We will first position the list a couple of screens before or after
            // the new selection and then scroll smoothly to it.
            int twoScreens = (lastPosition - firstPosition) * 2;
            int preliminaryPosition;
            if (position < firstPosition) {
                preliminaryPosition = position + twoScreens;
                if (preliminaryPosition >= getCount()) {
                    preliminaryPosition = getCount() - 1;
                }
                if (preliminaryPosition < firstPosition) {
                    setSelection(preliminaryPosition);
                    super.layoutChildren();
                }
            } else {
                preliminaryPosition = position - twoScreens;
                if (preliminaryPosition < 0) {
                    preliminaryPosition = 0;
                }
                if (preliminaryPosition > lastPosition) {
                    setSelection(preliminaryPosition);
                    super.layoutChildren();
                }
            }


            smoothScrollToPositionFromTop(position, offset);
        }
    }
}
