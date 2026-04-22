/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.common.pip;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.graphics.Rect;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Static utilities to get appropriate {@link PipDoubleTapHelper.PipSizeSpec} on a double tap.
 */
public class PipDoubleTapHelper {

    /**
     * Should not be instantiated as a stateless class.
     */
    private PipDoubleTapHelper() {}

    /**
     * A constant that represents a pip screen size.
     *
     * <p>CUSTOM - user resized screen size (by pinching in/out)</p>
     * <p>DEFAULT - normal screen size used as default when entering pip mode</p>
     * <p>MAX - maximum allowed screen size</p>
     */
    @IntDef(value = {
        SIZE_SPEC_DEFAULT,
        SIZE_SPEC_MAX,
        SIZE_SPEC_CUSTOM
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface PipSizeSpec {}

    public static final int SIZE_SPEC_DEFAULT = 0;
    public static final int SIZE_SPEC_MAX = 1;
    public static final int SIZE_SPEC_CUSTOM = 2;

    @PipSizeSpec
    private static int getMaxOrDefaultPipSizeSpec(@NonNull PipBoundsState pipBoundsState) {
        // determine the average pip screen width
        int averageWidth = (pipBoundsState.getMaxSize().x
                + pipBoundsState.getMinSize().x) / 2;

        // If pip screen width is above average, DEFAULT is the size spec we need to
        // toggle to. Otherwise, we choose MAX.
        return (pipBoundsState.getBounds().width() > averageWidth)
                ? SIZE_SPEC_DEFAULT
                : SIZE_SPEC_MAX;
    }

    /**
     * Determines the {@link PipSizeSpec} to toggle to on double tap.
     *
     * @param pipBoundsState current state of the pip bounds
     * @param userResizeBounds latest user resized bounds (by pinching in/out)
     */
    @PipSizeSpec
    public static int nextSizeSpec(@NonNull PipBoundsState pipBoundsState,
            @NonNull Rect userResizeBounds) {
        boolean isScreenMax = pipBoundsState.getBounds().width() == pipBoundsState.getMaxSize().x
                && pipBoundsState.getBounds().height() == pipBoundsState.getMaxSize().y;
        boolean isScreenDefault = (pipBoundsState.getBounds().width()
                == pipBoundsState.getNormalBounds().width())
                && (pipBoundsState.getBounds().height()
                == pipBoundsState.getNormalBounds().height());

        // edge case 1
        // if user hasn't resized screen yet, i.e. CUSTOM size does not exist yet
        // or if user has resized exactly to DEFAULT, then we just want to maximize
        if (isScreenDefault
                && userResizeBounds.width() == pipBoundsState.getNormalBounds().width()
                && userResizeBounds.height() == pipBoundsState.getNormalBounds().height()) {
            return SIZE_SPEC_MAX;
        }

        // edge case 2
        // if user has resized to max, then we want to toggle to DEFAULT
        if (isScreenMax
                && userResizeBounds.width() == pipBoundsState.getMaxSize().x
                && userResizeBounds.height() == pipBoundsState.getMaxSize().y) {
            return SIZE_SPEC_DEFAULT;
        }

        // otherwise in general we want to toggle back to user's CUSTOM size
        if (isScreenDefault || isScreenMax) {
            return SIZE_SPEC_CUSTOM;
        }
        return getMaxOrDefaultPipSizeSpec(pipBoundsState);
    }
}
