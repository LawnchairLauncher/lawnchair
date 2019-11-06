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
package com.android.launcher3.touch;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Utilities;

/**
 * One dimensional scroll/drag/swipe gesture detector (either HORIZONTAL or VERTICAL).
 */
public class SingleAxisSwipeDetector extends BaseSwipeDetector {

    public static final int DIRECTION_POSITIVE = 1 << 0;
    public static final int DIRECTION_NEGATIVE = 1 << 1;
    public static final int DIRECTION_BOTH = DIRECTION_NEGATIVE | DIRECTION_POSITIVE;

    public static final Direction VERTICAL = new Direction() {

        @Override
        boolean isPositive(float displacement) {
            // Up
            return displacement < 0;
        }

        @Override
        boolean isNegative(float displacement) {
            // Down
            return displacement > 0;
        }

        @Override
        float extractDirection(PointF direction) {
            return direction.y;
        }

        @Override
        boolean canScrollStart(PointF displacement, float touchSlop) {
            return Math.abs(displacement.y) >= Math.max(Math.abs(displacement.x), touchSlop);
        }

    };

    public static final Direction HORIZONTAL = new Direction() {

        @Override
        boolean isPositive(float displacement) {
            // Right
            return displacement > 0;
        }

        @Override
        boolean isNegative(float displacement) {
            // Left
            return displacement < 0;
        }

        @Override
        float extractDirection(PointF direction) {
            return direction.x;
        }

        @Override
        boolean canScrollStart(PointF displacement, float touchSlop) {
            return Math.abs(displacement.x) >= Math.max(Math.abs(displacement.y), touchSlop);
        }
    };

    private final Direction mDir;
    /* Client of this gesture detector can register a callback. */
    private final Listener mListener;

    private int mScrollDirections;

    public SingleAxisSwipeDetector(@NonNull Context context, @NonNull Listener l,
            @NonNull Direction dir) {
        this(ViewConfiguration.get(context), l, dir, Utilities.isRtl(context.getResources()));
    }

    @VisibleForTesting
    protected SingleAxisSwipeDetector(@NonNull ViewConfiguration config, @NonNull Listener l,
            @NonNull Direction dir, boolean isRtl) {
        super(config, isRtl);
        mListener = l;
        mDir = dir;
    }

    public void setDetectableScrollConditions(int scrollDirectionFlags, boolean ignoreSlop) {
        mScrollDirections = scrollDirectionFlags;
        mIgnoreSlopWhenSettling = ignoreSlop;
    }

    public int getScrollDirections() {
        return mScrollDirections;
    }

    /**
     * Returns if the start drag was towards the positive direction or negative.
     *
     * @see #setDetectableScrollConditions(int, boolean)
     * @see #DIRECTION_BOTH
     */
    public boolean wasInitialTouchPositive() {
        return mDir.isPositive(mDir.extractDirection(mSubtractDisplacement));
    }

    @Override
    protected boolean shouldScrollStart(PointF displacement) {
        // Reject cases where the angle or slop condition is not met.
        if (!mDir.canScrollStart(displacement, mTouchSlop)) {
            return false;
        }

        // Check if the client is interested in scroll in current direction.
        float displacementComponent = mDir.extractDirection(displacement);
        return canScrollNegative(displacementComponent) || canScrollPositive(displacementComponent);
    }

    private boolean canScrollNegative(float displacement) {
        return (mScrollDirections & DIRECTION_NEGATIVE) > 0 && mDir.isNegative(displacement);
    }

    private boolean canScrollPositive(float displacement) {
        return (mScrollDirections & DIRECTION_POSITIVE) > 0 && mDir.isPositive(displacement);
    }

    @Override
    protected void reportDragStartInternal(boolean recatch) {
        mListener.onDragStart(!recatch);
    }

    @Override
    protected void reportDraggingInternal(PointF displacement, MotionEvent event) {
        mListener.onDrag(mDir.extractDirection(displacement), event);
    }

    @Override
    protected void reportDragEndInternal(PointF velocity) {
        float velocityComponent = mDir.extractDirection(velocity);
        mListener.onDragEnd(velocityComponent);
    }

    /** Listener to receive updates on the swipe. */
    public interface Listener {
        /** @param start whether this was the original drag start, as opposed to a recatch. */
        void onDragStart(boolean start);

        // TODO remove
        boolean onDrag(float displacement);

        default boolean onDrag(float displacement, MotionEvent event) {
            return onDrag(displacement);
        }

        void onDragEnd(float velocity);
    }

    public abstract static class Direction {

        abstract boolean isPositive(float displacement);

        abstract boolean isNegative(float displacement);

        /** Returns the part of the given {@link PointF} that is relevant to this direction. */
        abstract float extractDirection(PointF point);

        /** Reject cases where the angle or slop condition is not met. */
        abstract boolean canScrollStart(PointF displacement, float touchSlop);

    }
}
