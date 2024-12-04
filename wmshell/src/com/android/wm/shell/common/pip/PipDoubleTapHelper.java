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

    /**
     * Returns MAX or DEFAULT {@link PipSizeSpec} to toggle to/from.
     *
     * <p>Each double tap toggles back and forth between {@code PipSizeSpec.CUSTOM} and
     * either {@code PipSizeSpec.MAX} or {@code PipSizeSpec.DEFAULT}. The choice between
     * the latter two sizes is determined based on the current state of the pip screen.</p>
     *
     * @param mPipBoundsState current state of the pip screen
     */
    @PipSizeSpec
    private static int getMaxOrDefaultPipSizeSpec(@NonNull PipBoundsState mPipBoundsState) {
        // determine the average pip screen width
        int averageWidth = (mPipBoundsState.getMaxSize().x
                + mPipBoundsState.getMinSize().x) / 2;

        // If pip screen width is above average, DEFAULT is the size spec we need to
        // toggle to. Otherwise, we choose MAX.
        return (mPipBoundsState.getBounds().width() > averageWidth)
                ? SIZE_SPEC_DEFAULT
                : SIZE_SPEC_MAX;
    }

    /**
     * Determines the {@link PipSizeSpec} to toggle to on double tap.
     *
     * @param mPipBoundsState current state of the pip screen
     * @param userResizeBounds latest user resized bounds (by pinching in/out)
     * @return pip screen size to switch to
     */
    @PipSizeSpec
    public static int nextSizeSpec(@NonNull PipBoundsState mPipBoundsState,
            @NonNull Rect userResizeBounds) {
        // is pip screen at its maximum
        boolean isScreenMax = mPipBoundsState.getBounds().width()
                == mPipBoundsState.getMaxSize().x;

        // is pip screen at its normal default size
        boolean isScreenDefault = (mPipBoundsState.getBounds().width()
                == mPipBoundsState.getNormalBounds().width())
                && (mPipBoundsState.getBounds().height()
                == mPipBoundsState.getNormalBounds().height());

        // edge case 1
        // if user hasn't resized screen yet, i.e. CUSTOM size does not exist yet
        // or if user has resized exactly to DEFAULT, then we just want to maximize
        if (isScreenDefault
                && userResizeBounds.width() == mPipBoundsState.getNormalBounds().width()) {
            return SIZE_SPEC_MAX;
        }

        // edge case 2
        // if user has maximized, then we want to toggle to DEFAULT
        if (isScreenMax
                && userResizeBounds.width() == mPipBoundsState.getMaxSize().x) {
            return SIZE_SPEC_DEFAULT;
        }

        // otherwise in general we want to toggle back to user's CUSTOM size
        if (isScreenDefault || isScreenMax) {
            return SIZE_SPEC_CUSTOM;
        }

        // if we are currently in user resized CUSTOM size state
        // then we toggle either to MAX or DEFAULT depending on the current pip screen state
        return getMaxOrDefaultPipSizeSpec(mPipBoundsState);
    }
}
