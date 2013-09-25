/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.support.v4.widget.AutoScrollHelper;
import android.widget.ScrollView;

/**
 * An implementation of {@link AutoScrollHelper} that knows how to scroll
 * through a {@link Folder}.
 */
public class FolderAutoScrollHelper extends AutoScrollHelper {
    private static final float MAX_SCROLL_VELOCITY = 1500f;

    private final ScrollView mTarget;

    public FolderAutoScrollHelper(ScrollView target) {
        super(target);

        mTarget = target;

        setActivationDelay(0);
        setEdgeType(EDGE_TYPE_INSIDE_EXTEND);
        setExclusive(true);
        setMaximumVelocity(MAX_SCROLL_VELOCITY, MAX_SCROLL_VELOCITY);
        setRampDownDuration(0);
        setRampUpDuration(0);
    }

    @Override
    public void scrollTargetBy(int deltaX, int deltaY) {
        mTarget.scrollBy(deltaX, deltaY);
    }

    @Override
    public boolean canTargetScrollHorizontally(int direction) {
        // List do not scroll horizontally.
        return false;
    }

    @Override
    public boolean canTargetScrollVertically(int direction) {
        return mTarget.canScrollVertically(direction);
    }
}