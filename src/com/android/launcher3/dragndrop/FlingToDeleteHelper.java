/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.graphics.PointF;
import android.os.SystemClock;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.FlingAnimation;

/**
 * Utility class to manage fling to delete action during drag and drop.
 */
public class FlingToDeleteHelper {

    private static final float MAX_FLING_DEGREES = 35f;

    private final Launcher mLauncher;
    private final int mFlingToDeleteThresholdVelocity;

    private ButtonDropTarget mDropTarget;
    private VelocityTracker mVelocityTracker;

    public FlingToDeleteHelper(Launcher launcher) {
        mLauncher = launcher;
        mFlingToDeleteThresholdVelocity = launcher.getResources()
                .getDimensionPixelSize(R.dimen.drag_flingToDeleteMinVelocity);
    }

    public void recordMotionEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    /**
     * Same as {@link #recordMotionEvent}. It creates a temporary {@link MotionEvent} object
     * using {@param event} for tracking velocity.
     */
    public void recordDragEvent(long dragStartTime, DragEvent event) {
        final int motionAction;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                motionAction = MotionEvent.ACTION_DOWN;
                break;
            case DragEvent.ACTION_DRAG_LOCATION:
                motionAction = MotionEvent.ACTION_MOVE;
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                motionAction = MotionEvent.ACTION_UP;
                break;
            default:
                return;
        }
        MotionEvent emulatedEvent = MotionEvent.obtain(dragStartTime, SystemClock.uptimeMillis(),
                motionAction, event.getX(), event.getY(), 0);
        recordMotionEvent(emulatedEvent);
        emulatedEvent.recycle();
    }

    public void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    public DropTarget getDropTarget() {
        return mDropTarget;
    }

    public Runnable getFlingAnimation(DropTarget.DragObject dragObject) {
        PointF vel = isFlingingToDelete();
        if (vel == null) {
            return null;
        }
        return new FlingAnimation(dragObject, vel, mDropTarget, mLauncher);
    }

    /**
     * Determines whether the user flung the current item to delete it.
     *
     * @return the vector at which the item was flung, or null if no fling was detected.
     */
    private PointF isFlingingToDelete() {
        if (mDropTarget == null) {
            mDropTarget = (ButtonDropTarget) mLauncher.findViewById(R.id.delete_target_text);
        }
        if (mDropTarget == null || !mDropTarget.isDropEnabled()) return null;
        ViewConfiguration config = ViewConfiguration.get(mLauncher);
        mVelocityTracker.computeCurrentVelocity(1000, config.getScaledMaximumFlingVelocity());
        PointF vel = new PointF(mVelocityTracker.getXVelocity(), mVelocityTracker.getYVelocity());
        float theta = MAX_FLING_DEGREES + 1;
        if (mVelocityTracker.getYVelocity() < mFlingToDeleteThresholdVelocity) {
            // Do a quick dot product test to ensure that we are flinging upwards
            PointF upVec = new PointF(0f, -1f);
            theta = getAngleBetweenVectors(vel, upVec);
        } else if (mLauncher.getDeviceProfile().isVerticalBarLayout() &&
                mVelocityTracker.getXVelocity() < mFlingToDeleteThresholdVelocity) {
            // Remove icon is on left side instead of top, so check if we are flinging to the left.
            PointF leftVec = new PointF(-1f, 0f);
            theta = getAngleBetweenVectors(vel, leftVec);
        }
        if (theta <= Math.toRadians(MAX_FLING_DEGREES)) {
            return vel;
        }
        return null;
    }

    private float getAngleBetweenVectors(PointF vec1, PointF vec2) {
        return (float) Math.acos(((vec1.x * vec2.x) + (vec1.y * vec2.y)) /
                (vec1.length() * vec2.length()));
    }
}
