/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.taskbar;

import android.graphics.Rect;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.util.TouchController;

import java.util.function.Supplier;

/**
 * Extends the Recents touch area during the taskbar to overview animation
 * to give user some error room when trying to quickly double tap recents button since it moves.
 *
 * Listens for icon alignment as our indication for the animation.
 */
public class RecentsHitboxExtender implements TouchController {

    private static final int RECENTS_HITBOX_TIMEOUT_MS = 500;

    private View mRecentsButton;
    private View mRecentsParent;
    private DeviceProfile mDeviceProfile;
    private Supplier<float[]> mParentCoordSupplier;
    private TouchDelegate mRecentsTouchDelegate;
    /**
     * Will be true while the animation from taskbar to overview is occurring.
     * Lifecycle of this variable slightly extends past the animation by
     * {@link #RECENTS_HITBOX_TIMEOUT_MS}, so can use this variable as a proxy for if
     * the current hitbox is extended or not.
     */
    private boolean mAnimatingFromTaskbarToOverview;
    private float mLastIconAlignment;
    private final Rect mRecentsHitBox = new Rect();
    private boolean mRecentsButtonClicked;
    private Handler mHandler;
    private final Runnable mRecentsHitboxResetRunnable = this::reset;

    public void init(View recentsButton, View recentsParent, DeviceProfile deviceProfile,
            Supplier<float[]> parentCoordSupplier, Handler handler) {
        mRecentsButton = recentsButton;
        mRecentsParent = recentsParent;
        mDeviceProfile = deviceProfile;
        mParentCoordSupplier = parentCoordSupplier;
        mHandler = handler;
    }

    public void onRecentsButtonClicked() {
        mRecentsButtonClicked = true;
    }

    /**
     * @param progress 0 -> Taskbar, 1 -> Overview
     */
    public void onAnimationProgressToOverview(float progress) {
        if (progress == 1 || progress == 0) {
            // Done w/ animation
            mLastIconAlignment = progress;
            if (mAnimatingFromTaskbarToOverview) {
                if (progress == 1) {
                    // Finished animation to workspace, remove the touch delegate shortly
                    mHandler.postDelayed(mRecentsHitboxResetRunnable, RECENTS_HITBOX_TIMEOUT_MS);
                    return;
                } else {
                    // Went back to taskbar, reset immediately
                    mHandler.removeCallbacks(mRecentsHitboxResetRunnable);
                    reset();
                }
            }
        }

        if (mAnimatingFromTaskbarToOverview) {
            return;
        }

        if (progress > 0 && mLastIconAlignment == 0 && mRecentsButtonClicked) {
            // Starting animation, previously we were showing taskbar
            mAnimatingFromTaskbarToOverview = true;
            float[] recentsCoords = mParentCoordSupplier.get();
            int x = (int) recentsCoords[0];
            int y = (int) (recentsCoords[1]);
            // Extend hitbox vertically by the offset amount from mDeviceProfile.getTaskbarOffsetY()
            mRecentsHitBox.set(x, y,
                    x + mRecentsButton.getWidth(),
                    y + mRecentsButton.getHeight() + mDeviceProfile.getTaskbarOffsetY()
            );
            mRecentsTouchDelegate = new TouchDelegate(mRecentsHitBox, mRecentsButton);
            mRecentsParent.setTouchDelegate(mRecentsTouchDelegate);
        }
    }

    private void reset() {
        mAnimatingFromTaskbarToOverview = false;
        mRecentsButton.setTouchDelegate(null);
        mRecentsHitBox.setEmpty();
        mRecentsButtonClicked = false;
    }

    /**
     * @return {@code true} if the bounds for recents touches are currently extended
     */
    public boolean extendedHitboxEnabled() {
        return mAnimatingFromTaskbarToOverview;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mRecentsTouchDelegate.onTouchEvent(ev);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return mRecentsHitBox.contains((int)ev.getX(), (int)ev.getY());
    }
}
