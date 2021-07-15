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
        float extractOrthogonalDirection(PointF direction) {
            return direction.x;
        }

        @NonNull
        @Override
        public String toString() {
            return "VERTICAL";
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
        float extractOrthogonalDirection(PointF direction) {
            return direction.y;
        }

        @NonNull
        @Override
        public String toString() {
            return "HORIZONTAL";
        }
    };

    private final Direction mDir;
    /* Client of this gesture detector can register a callback. */
    private final Listener mListener;

    private int mScrollDirections;

    private float mTouchSlopMultiplier = 1f;

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

    /**
     * Provides feasibility to adjust touch slop when visible window size changed. When visible
     * bounds translate become smaller, multiply a larger multiplier could ensure the UX
     * more consistent.
     *
     * @see #shouldScrollStart(PointF)
     *
     * @param touchSlopMultiplier the value to multiply original touch slop.
     */
    public void setTouchSlopMultiplier(float touchSlopMultiplier) {
        mTouchSlopMultiplier = touchSlopMultiplier;
    }

    public void setDetectableScrollConditions(int scrollDirectionFlags, boolean ignoreSlop) {
        mScrollDirections = scrollDirectionFlags;
        mIgnoreSlopWhenSettling = ignoreSlop;
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
        float minDisplacement = Math.max(mTouchSlop * mTouchSlopMultiplier,
                Math.abs(mDir.extractOrthogonalDirection(displacement)));
        if (Math.abs(mDir.extractDirection(displacement)) < minDisplacement) {
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
        float startDisplacement = mDir.extractDirection(mSubtractDisplacement);
        mListener.onDragStart(!recatch, startDisplacement);
    }

    @Override
    protected void reportDraggingInternal(PointF displacement, MotionEvent event) {
        mListener.onDrag(mDir.extractDirection(displacement),
                mDir.extractOrthogonalDirection(displacement), event);
    }

    @Override
    protected void reportDragEndInternal(PointF velocity) {
        float velocityComponent = mDir.extractDirection(velocity);
        mListener.onDragEnd(velocityComponent);
    }

    /** Listener to receive updates on the swipe. */
    public interface Listener {
        /**
         * TODO(b/150256055) consolidate all the different onDrag() methods into one
         * @param start whether this was the original drag start, as opposed to a recatch.
         * @param startDisplacement the initial touch displacement for the primary direction as
         *        given by by {@link Direction#extractDirection(PointF)}
         */
        void onDragStart(boolean start, float startDisplacement);

        boolean onDrag(float displacement);

        default boolean onDrag(float displacement, MotionEvent event) {
            return onDrag(displacement);
        }

        default boolean onDrag(float displacement, float orthogonalDisplacement, MotionEvent ev) {
            return onDrag(displacement, ev);
        }

        void onDragEnd(float velocity);
    }

    public abstract static class Direction {

        abstract boolean isPositive(float displacement);

        abstract boolean isNegative(float displacement);

        /** Returns the part of the given {@link PointF} that is relevant to this direction. */
        abstract float extractDirection(PointF point);

        abstract float extractOrthogonalDirection(PointF point);

    }
}
