/*
 * Copyright (C) 2026 The Lawnchair Authors
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
package com.android.launcher3;

import android.view.MotionEvent;

/**
 * Tracks a touch that starts on an icon and determines whether its current direction has an icon
 * gesture configured. Controllers can then leave only configured directions to the icon.
 */
public final class IconGestureTouchTracker {

    private final Launcher mLauncher;
    private Workspace mWorkspace;
    private boolean mStartedOnIconWithGesture;
    private float mDownX;
    private float mDownY;
    private float mWorkspaceDownX;
    private float mWorkspaceDownY;

    /** Creates a tracker for touch events dispatched through {@code launcher}'s drag layer. */
    public IconGestureTouchTracker(Launcher launcher) {
        mLauncher = launcher;
    }

    /** Records the icon, if any, where this touch began. */
    public void onTouchDown(MotionEvent ev) {
        mStartedOnIconWithGesture = false;
        mWorkspace = mLauncher.getWorkspace();
        if (mWorkspace == null || mLauncher.getDragLayer() == null) {
            return;
        }

        float[] coord = new float[]{ev.getX(), ev.getY()};
        mLauncher.getDragLayer().mapCoordInSelfToDescendant(mWorkspace, coord);
        mDownX = ev.getX();
        mDownY = ev.getY();
        mWorkspaceDownX = coord[0];
        mWorkspaceDownY = coord[1];
        mStartedOnIconWithGesture = mWorkspace.isTouchOnIconWithSwipeGesture(
                coord[0], coord[1], true) || mWorkspace.isTouchOnIconWithSwipeGesture(
                coord[0], coord[1], false);
    }

    /** Returns whether this single-pointer move targets an action configured on the touched icon. */
    public boolean isMovingTowardConfiguredIconGesture(MotionEvent ev) {
        if (!mStartedOnIconWithGesture || ev.getActionMasked() != MotionEvent.ACTION_MOVE
                || ev.getPointerCount() != 1) {
            return false;
        }
        return mWorkspace.isTouchOnIconWithSwipeGestureInDirection(
                mWorkspaceDownX, mWorkspaceDownY, ev.getX() - mDownX, ev.getY() - mDownY);
    }

    /** Clears the touch state after the gesture finishes or is cancelled. */
    public void reset() {
        mStartedOnIconWithGesture = false;
        mWorkspace = null;
    }
}
