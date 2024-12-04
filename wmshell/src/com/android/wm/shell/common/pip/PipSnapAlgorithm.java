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

import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_LEFT;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_RIGHT;

import android.graphics.Rect;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Calculates the snap targets and the snap position for the PIP given a position and a velocity.
 * All bounds are relative to the display top/left.
 */
public class PipSnapAlgorithm {

    /**
     * Returns a fraction that describes where the PiP bounds is.
     * See {@link #getSnapFraction(Rect, Rect, int)}.
     */
    public float getSnapFraction(Rect stackBounds, Rect movementBounds) {
        return getSnapFraction(stackBounds, movementBounds, STASH_TYPE_NONE);
    }

    /**
     * @return returns a fraction that describes where along the movementBounds the
     *         stackBounds are. If the stackBounds are not currently on the
     *         movementBounds exactly, then they will be snapped to the movement bounds.
     *         stashType dictates whether the PiP is stashed (off-screen) or not. If
     *         that's the case, we will have to do some math to calculate the snap fraction
     *         correctly.
     *
     *         The fraction is defined in a clockwise fashion against the movementBounds:
     *
     *            0   1
     *          4 +---+ 1
     *            |   |
     *          3 +---+ 2
     *            3   2
     */
    public float getSnapFraction(Rect stackBounds, Rect movementBounds,
            @PipBoundsState.StashType int stashType) {
        final Rect tmpBounds = new Rect();
        snapRectToClosestEdge(stackBounds, movementBounds, tmpBounds, stashType);
        final float widthFraction = (float) (tmpBounds.left - movementBounds.left)
                / movementBounds.width();
        final float heightFraction = (float) (tmpBounds.top - movementBounds.top)
                / movementBounds.height();
        if (tmpBounds.top == movementBounds.top) {
            return widthFraction;
        } else if (tmpBounds.left == movementBounds.right) {
            return 1f + heightFraction;
        } else if (tmpBounds.top == movementBounds.bottom) {
            return 2f + (1f - widthFraction);
        } else {
            return 3f + (1f - heightFraction);
        }
    }

    /**
     * Moves the stackBounds along the movementBounds to the given snap fraction.
     * See {@link #getSnapFraction(Rect, Rect)}.
     *
     * The fraction is define in a clockwise fashion against the movementBounds:
     *
     *    0   1
     *  4 +---+ 1
     *    |   |
     *  3 +---+ 2
     *    3   2
     */
    public void applySnapFraction(Rect stackBounds, Rect movementBounds, float snapFraction) {
        if (snapFraction < 1f) {
            int offset = movementBounds.left + (int) (snapFraction * movementBounds.width());
            stackBounds.offsetTo(offset, movementBounds.top);
        } else if (snapFraction < 2f) {
            snapFraction -= 1f;
            int offset = movementBounds.top + (int) (snapFraction * movementBounds.height());
            stackBounds.offsetTo(movementBounds.right, offset);
        } else if (snapFraction < 3f) {
            snapFraction -= 2f;
            int offset = movementBounds.left + (int) ((1f - snapFraction) * movementBounds.width());
            stackBounds.offsetTo(offset, movementBounds.bottom);
        } else {
            snapFraction -= 3f;
            int offset = movementBounds.top + (int) ((1f - snapFraction) * movementBounds.height());
            stackBounds.offsetTo(movementBounds.left, offset);
        }
    }

    /**
     * Same as {@link #applySnapFraction(Rect, Rect, float)}, but take stash state into
     * consideration.
     */
    public void applySnapFraction(Rect stackBounds, Rect movementBounds, float snapFraction,
            @PipBoundsState.StashType int stashType, int stashOffset, Rect displayBounds,
            Rect insetBounds) {
        applySnapFraction(stackBounds, movementBounds, snapFraction);

        if (stashType != STASH_TYPE_NONE) {
            stackBounds.offsetTo(stashType == STASH_TYPE_LEFT
                            ? stashOffset - stackBounds.width() + insetBounds.left
                            : displayBounds.right - stashOffset - insetBounds.right,
                    stackBounds.top);
        }
    }

    /**
     * Snaps the stackBounds to the closest edge of the movementBounds and writes
     * the new bounds out to boundsOut.
     */
    @VisibleForTesting
    public void snapRectToClosestEdge(Rect stackBounds, Rect movementBounds, Rect boundsOut,
            @PipBoundsState.StashType int stashType) {
        int leftEdge = stackBounds.left;
        if (stashType == STASH_TYPE_LEFT) {
            leftEdge = movementBounds.left;
        } else if (stashType == STASH_TYPE_RIGHT) {
            leftEdge = movementBounds.right;
        }
        final int boundedLeft = Math.max(movementBounds.left, Math.min(movementBounds.right,
                leftEdge));
        final int boundedTop = Math.max(movementBounds.top, Math.min(movementBounds.bottom,
                stackBounds.top));
        boundsOut.set(stackBounds);

        // Otherwise, just find the closest edge
        final int fromLeft = Math.abs(leftEdge - movementBounds.left);
        final int fromTop = Math.abs(stackBounds.top - movementBounds.top);
        final int fromRight = Math.abs(movementBounds.right - leftEdge);
        final int fromBottom = Math.abs(movementBounds.bottom - stackBounds.top);
        final int shortest = Math.min(Math.min(fromLeft, fromRight), Math.min(fromTop, fromBottom));
        if (shortest == fromLeft) {
            boundsOut.offsetTo(movementBounds.left, boundedTop);
        } else if (shortest == fromTop) {
            boundsOut.offsetTo(boundedLeft, movementBounds.top);
        } else if (shortest == fromRight) {
            boundsOut.offsetTo(movementBounds.right, boundedTop);
        } else {
            boundsOut.offsetTo(boundedLeft, movementBounds.bottom);
        }
    }
}
