/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.anim;

import android.support.animation.DynamicAnimation;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AlphabeticalAppsList;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * Handler class that manages springs for a set of views that should all move based on the same
 * {@link MotionEvent}s.
 *
 * Supports using physics for X or Y translations.
 */
public class SpringAnimationHandler {

    private static final String TAG = "SpringAnimationHandler";
    private static final boolean DEBUG = false;

    private static final float DEFAULT_MAX_VALUE = 100;
    private static final float DEFAULT_MIN_VALUE = -DEFAULT_MAX_VALUE;

    private static final float SPRING_DAMPING_RATIO = 0.55f;
    private static final float MIN_SPRING_STIFFNESS = 580f;
    private static final float MAX_SPRING_STIFFNESS = 900f;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Y_DIRECTION, X_DIRECTION})
    public @interface Direction {}
    public static final int Y_DIRECTION = 0;
    public static final int X_DIRECTION = 1;
    private int mDirection;

    private VelocityTracker mVelocityTracker;
    private float mCurrentVelocity = 0;
    private boolean mShouldComputeVelocity = false;

    private ArrayList<SpringAnimation> mAnimations = new ArrayList<>();

    public SpringAnimationHandler(@Direction int direction) {
        mDirection = direction;
        mVelocityTracker = VelocityTracker.obtain();
    }

    public SpringAnimation add(View view, int position, AlphabeticalAppsList apps, int appsPerRow,
            SpringAnimation recycle) {
        int numPredictedApps = Math.min(appsPerRow, apps.getPredictedApps().size());
        int appPosition = getAppPosition(position, numPredictedApps, appsPerRow);

        int col = appPosition % appsPerRow;
        int row = appPosition / appsPerRow;

        int numTotalRows = apps.getNumAppRows() - 1; // zero offset
        if (row > (numTotalRows / 2)) {
            // Mirror the rows so that the top row acts the same as the bottom row.
            row = Math.abs(numTotalRows - row);
        }

        // We manipulate the stiffness, min, and max values based on the items distance to the first
        // row and the items distance to the center column to create the ^-shaped motion effect.
        float rowFactor = (1 + row) * 0.5f;
        float colFactor = getColumnFactor(col, appsPerRow);

        float minValue = DEFAULT_MIN_VALUE * (rowFactor + colFactor);
        float maxValue = DEFAULT_MAX_VALUE * (rowFactor + colFactor);

        float stiffness = Utilities.boundToRange(MAX_SPRING_STIFFNESS - (row * 50f),
                MIN_SPRING_STIFFNESS, MAX_SPRING_STIFFNESS);

        SpringAnimation animation = (recycle != null ? recycle : createSpringAnimation(view))
                .setStartVelocity(mCurrentVelocity)
                .setMinValue(minValue)
                .setMaxValue(maxValue);
        animation.getSpring().setStiffness(stiffness);

        mAnimations.add(animation);
        return animation;
    }

    public SpringAnimation remove(SpringAnimation animation) {
        animation.skipToEnd();
        mAnimations.remove(animation);
        return animation;
    }

    public void addMovement(MotionEvent event) {
        int action = event.getActionMasked();
        if (DEBUG) Log.d(TAG, "addMovement#action=" + action);
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_DOWN:
                reset();
                break;
        }

        getVelocityTracker().addMovement(event);
        mShouldComputeVelocity = true;
    }

    public void animateToFinalPosition(float position) {
        if (DEBUG) Log.d(TAG, "animateToFinalPosition#computeVelocity=" + mShouldComputeVelocity);

        if (mShouldComputeVelocity) {
            computeVelocity();
            setStartVelocity(mCurrentVelocity);
        }

        int size = mAnimations.size();
        for (int i = 0; i < size; ++i) {
            mAnimations.get(i).animateToFinalPosition(position);
        }

        reset();
    }

    public void skipToEnd() {
        if (DEBUG) Log.d(TAG, "setStartVelocity#skipToEnd");
        if (DEBUG) Log.v(TAG, "setStartVelocity#skipToEnd", new Exception());

        int size = mAnimations.size();
        for (int i = 0; i < size; ++i) {
            mAnimations.get(i).skipToEnd();
        }
    }

    public void reset() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        mCurrentVelocity = 0;
    }

    private void setStartVelocity(float velocity) {
        int size = mAnimations.size();
        for (int i = 0; i < size; ++i) {
            mAnimations.get(i).setStartVelocity(velocity);
        }
    }

    private void computeVelocity() {
        getVelocityTracker().computeCurrentVelocity(175);

        mCurrentVelocity = isVerticalDirection()
                ? getVelocityTracker().getYVelocity()
                : getVelocityTracker().getXVelocity();
        mShouldComputeVelocity = false;

        if (DEBUG) Log.d(TAG, "computeVelocity=" + mCurrentVelocity);
    }

    private boolean isVerticalDirection() {
        return mDirection == Y_DIRECTION;
    }

    public SpringAnimation createSpringAnimation(View view) {
        DynamicAnimation.ViewProperty property = isVerticalDirection()
                ? DynamicAnimation.TRANSLATION_Y
                : DynamicAnimation.TRANSLATION_X;

        return new SpringAnimation(view, property, 0)
                .setStartValue(1f)
                .setSpring(new SpringForce(0)
                .setDampingRatio(SPRING_DAMPING_RATIO));
    }

    /**
     * @return The app position is the position of the app in the Adapter if we ignored all other
     * view types.
     *
     * ie. The first predicted app is at position 0, and the first app of all apps is
     *     at {@param appsPerRow}.
     */
    private int getAppPosition(int position, int numPredictedApps, int appsPerRow) {
        int appPosition = position;
        int numDividerViews = 1 + (numPredictedApps == 0 ? 0 : 1);

        int allAppsStartAt = numDividerViews + numPredictedApps;
        if (numDividerViews == 1 || position < allAppsStartAt) {
            appPosition -= 1;
        } else {
            // We cannot assume that the predicted row will always be full.
            int numPredictedAppsOffset = appsPerRow - numPredictedApps;
            appPosition = position + numPredictedAppsOffset - numDividerViews;
        }

        return appPosition;
    }

    /**
     * Increase the column factor as the distance increases between the column and the center
     * column(s).
     */
    private float getColumnFactor(int col, int numCols) {
        float centerColumn = numCols / 2;
        int distanceToCenter = (int) Math.abs(col - centerColumn);

        boolean evenNumberOfColumns = numCols % 2 == 0;
        if (evenNumberOfColumns && col < centerColumn) {
            distanceToCenter -= 1;
        }

        float factor = 0;
        while (distanceToCenter > 0) {
            if (distanceToCenter == 1) {
                factor += 0.2f;
            } else {
                factor += 0.1f;
            }
            --distanceToCenter;
        }

        return factor;
    }

    private VelocityTracker getVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        return mVelocityTracker;
    }
}
