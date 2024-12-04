/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.SystemProperties;
import android.util.ArraySet;
import android.view.Gravity;

import com.android.wm.shell.R;

import java.util.Set;

/**
 * Calculates the adjusted position that does not occlude keep clear areas.
 */
public class PhonePipKeepClearAlgorithm implements PipKeepClearAlgorithmInterface {

    private boolean mKeepClearAreaGravityEnabled =
            SystemProperties.getBoolean(
                    "persist.wm.debug.enable_pip_keep_clear_algorithm_gravity", false);

    protected int mKeepClearAreasPadding;
    private int mImeOffset;

    public PhonePipKeepClearAlgorithm(Context context) {
        reloadResources(context);
    }

    private void reloadResources(Context context) {
        final Resources res = context.getResources();
        mKeepClearAreasPadding = res.getDimensionPixelSize(R.dimen.pip_keep_clear_areas_padding);
        mImeOffset = res.getDimensionPixelSize(R.dimen.pip_ime_offset);
    }

    /**
     * Adjusts the current position of PiP to avoid occluding keep clear areas. This will push PiP
     * towards the closest edge and then apply calculations to avoid occluding keep clear areas.
     */
    public Rect adjust(PipBoundsState pipBoundsState, PipBoundsAlgorithm pipBoundsAlgorithm) {
        Rect startingBounds = pipBoundsState.getBounds().isEmpty()
                ? pipBoundsAlgorithm.getEntryDestinationBoundsIgnoringKeepClearAreas()
                : pipBoundsState.getBounds();
        Rect insets = new Rect();
        pipBoundsAlgorithm.getInsetBounds(insets);
        if (pipBoundsState.isImeShowing()) {
            insets.bottom -= (pipBoundsState.getImeHeight() + mImeOffset);
        }
        // if PiP is stashed we only adjust the vertical position if it's outside of insets and
        // ignore all keep clear areas, since it's already on the side
        if (pipBoundsState.isStashed()) {
            if (startingBounds.bottom > insets.bottom || startingBounds.top < insets.top) {
                // bring PiP back to be aligned by bottom inset
                startingBounds.offset(0, insets.bottom - startingBounds.bottom);
            }
            return startingBounds;
        }
        Rect pipBounds = new Rect(startingBounds);

        boolean shouldApplyGravity = false;
        // if PiP is outside of screen insets, reposition using gravity
        if (!insets.contains(pipBounds)) {
            shouldApplyGravity = true;
        }
        // if user has not interacted with PiP, reposition using gravity
        if (!pipBoundsState.hasUserMovedPip() && !pipBoundsState.hasUserResizedPip()) {
            shouldApplyGravity = true;
        }

        // apply gravity that will position PiP in bottom left or bottom right corner within insets
        if (mKeepClearAreaGravityEnabled || shouldApplyGravity) {
            float snapFraction = pipBoundsAlgorithm.getSnapFraction(startingBounds);
            int verticalGravity = Gravity.BOTTOM;
            int horizontalGravity;
            if (snapFraction >= 0.5f && snapFraction < 2.5f) {
                horizontalGravity = Gravity.RIGHT;
            } else {
                horizontalGravity = Gravity.LEFT;
            }
            if (verticalGravity == Gravity.BOTTOM) {
                pipBounds.offsetTo(pipBounds.left,
                        insets.bottom - pipBounds.height());
            }
            if (horizontalGravity == Gravity.RIGHT) {
                pipBounds.offsetTo(insets.right - pipBounds.width(), pipBounds.top);
            } else {
                pipBounds.offsetTo(insets.left, pipBounds.top);
            }
        }

        return findUnoccludedPosition(pipBounds, pipBoundsState.getRestrictedKeepClearAreas(),
                pipBoundsState.getUnrestrictedKeepClearAreas(), insets);
    }

    /** Returns a new {@code Rect} that does not occlude the provided keep clear areas. */
    public Rect findUnoccludedPosition(Rect defaultBounds, Set<Rect> restrictedKeepClearAreas,
            Set<Rect> unrestrictedKeepClearAreas, Rect allowedBounds) {
        if (restrictedKeepClearAreas.isEmpty() && unrestrictedKeepClearAreas.isEmpty()) {
            return defaultBounds;
        }
        Set<Rect> keepClearAreas = new ArraySet<>();
        if (!restrictedKeepClearAreas.isEmpty()) {
            keepClearAreas.addAll(restrictedKeepClearAreas);
        }
        if (!unrestrictedKeepClearAreas.isEmpty()) {
            keepClearAreas.addAll(unrestrictedKeepClearAreas);
        }
        Rect outBounds = new Rect(defaultBounds);
        for (Rect r : keepClearAreas) {
            Rect tmpRect = new Rect(r);
            // add extra padding to the keep clear area
            tmpRect.inset(-mKeepClearAreasPadding, -mKeepClearAreasPadding);
            if (Rect.intersects(r, outBounds)) {
                if (tryOffsetUp(outBounds, tmpRect, allowedBounds)) continue;
                if (tryOffsetLeft(outBounds, tmpRect, allowedBounds)) continue;
                if (tryOffsetDown(outBounds, tmpRect, allowedBounds)) continue;
                if (tryOffsetRight(outBounds, tmpRect, allowedBounds)) continue;
            }
        }
        return outBounds;
    }

    private static boolean tryOffsetLeft(Rect rectToMove, Rect rectToAvoid, Rect allowedBounds) {
        return tryOffset(rectToMove, rectToAvoid, allowedBounds,
                rectToAvoid.left - rectToMove.right, 0);
    }

    private static boolean tryOffsetRight(Rect rectToMove, Rect rectToAvoid, Rect allowedBounds) {
        return tryOffset(rectToMove, rectToAvoid, allowedBounds,
                rectToAvoid.right - rectToMove.left, 0);
    }

    private static boolean tryOffsetUp(Rect rectToMove, Rect rectToAvoid, Rect allowedBounds) {
        return tryOffset(rectToMove, rectToAvoid, allowedBounds,
                0, rectToAvoid.top - rectToMove.bottom);
    }

    private static boolean tryOffsetDown(Rect rectToMove, Rect rectToAvoid, Rect allowedBounds) {
        return tryOffset(rectToMove, rectToAvoid, allowedBounds,
                0, rectToAvoid.bottom - rectToMove.top);
    }

    private static boolean tryOffset(Rect rectToMove, Rect rectToAvoid, Rect allowedBounds,
            int dx, int dy) {
        Rect tmp = new Rect(rectToMove);
        tmp.offset(dx, dy);
        if (!Rect.intersects(rectToAvoid, tmp) && allowedBounds.contains(tmp)) {
            rectToMove.offsetTo(tmp.left, tmp.top);
            return true;
        }
        return false;
    }
}
