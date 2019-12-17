/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Empty view to house recents overview extra card
 */
public class RecentsExtraViewContainer extends FrameLayout implements RecentsView.PageCallbacks {

    private boolean mScrollable = false;

    public RecentsExtraViewContainer(Context context) {
        super(context);
    }

    public RecentsExtraViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecentsExtraViewContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Determine whether the view should be scrolled to in the recents overview, similar to the
     * taskviews.
     * @return true if viewed should be scrolled to, false if not
     */
    public boolean isScrollable() {
        return mScrollable;
    }

    public void setScrollable(boolean scrollable) {
        this.mScrollable = scrollable;
    }
}
