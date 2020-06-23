/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.shadows;

import static org.robolectric.shadow.api.Shadow.directlyOn;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.Display;

import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowDisplay;

/**
 * Extension of {@link ShadowDisplay} with missing shadow methods
 */
@Implements(value = Display.class)
public class LShadowDisplay extends ShadowDisplay {

    private final Rect mInsets = new Rect();

    @RealObject Display realObject;

    /**
     * Sets the insets for the display
     */
    public void setInsets(Rect insets) {
        mInsets.set(insets);
    }

    @Override
    protected void getCurrentSizeRange(Point outSmallestSize, Point outLargestSize) {
        directlyOn(realObject, Display.class).getCurrentSizeRange(outSmallestSize, outLargestSize);
        outSmallestSize.x -= mInsets.left + mInsets.right;
        outLargestSize.x -= mInsets.left + mInsets.right;

        outSmallestSize.y -= mInsets.top + mInsets.bottom;
        outLargestSize.y -= mInsets.top + mInsets.bottom;
    }
}
