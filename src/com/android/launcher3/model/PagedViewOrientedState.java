/*
 *
 *  * Copyright (C) 2020 The Android Open Source Project
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.android.launcher3.model;

import android.view.Surface;

import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.touch.PortraitPagedViewHandler;
import com.android.launcher3.touch.LandscapePagedViewHandler;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.touch.SeascapePagedViewHandler;

/**
 * Container to hold orientation/rotation related information for Launcher.
 * This is not meant to be an abstraction layer for applying different functionality between
 * the different orientation/rotations. For that see {@link PagedOrientationHandler}
 *
 * This class has initial default state assuming the device and foreground app have
 * no ({@link Surface.ROTATION_0} rotation.
 *
 * Currently this class resides in {@link com.android.launcher3.PagedView}, but there's a ticket
 * to disassociate it from Launcher since it's needed before Launcher is instantiated
 * See TODO(b/150300347)
 */
public final class PagedViewOrientedState {

    private PagedOrientationHandler mOrientationHandler = new PortraitPagedViewHandler();

    private int mTouchRotation = Surface.ROTATION_0;
    private int mDisplayRotation = Surface.ROTATION_0;
    /**
     * If {@code true} we default to {@link PortraitPagedViewHandler} and don't support any fake
     * launcher orientations.
     */
    private boolean mDisableMultipleOrientations;

    /**
     * Sets the appropriate {@link PagedOrientationHandler} for {@link #mOrientationHandler}
     * @param touchRotation The rotation the nav bar region that is touched is in
     * @param displayRotation Rotation of the display/device
     */
    public void update(int touchRotation, int displayRotation) {
        if (mDisableMultipleOrientations) {
            return;
        }

        mDisplayRotation = displayRotation;
        mTouchRotation = touchRotation;
        if (mTouchRotation == Surface.ROTATION_90) {
            mOrientationHandler = new LandscapePagedViewHandler();
        } else if (mTouchRotation == Surface.ROTATION_270) {
            mOrientationHandler = new SeascapePagedViewHandler();
        } else {
            mOrientationHandler = new PortraitPagedViewHandler();
        }
    }

    public boolean areMultipleLayoutOrientationsDisabled() {
        return mDisableMultipleOrientations;
    }

    /**
     * Setting this preference will render future calls to {@link #update(int, int)} as a no-op.
     */
    public void disableMultipleOrientations(boolean disable) {
        mDisableMultipleOrientations = disable;
        if (disable) {
            mOrientationHandler = new PortraitPagedViewHandler();
        }
    }

    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    /**
     * Gets the difference between the rotation of the device/display and which region the
     * user is currently interacting with in factors of 90 degree clockwise rotations.
     * Ex. Display is in portrait -> 0, user touches landscape region -> 1, this
     * method would return 3 because it takes 3 clockwise 90 degree rotations from normal to
     * landscape (portrait -> seascape -> reverse portrait -> landscape)
     */
    public int getTouchDisplayDelta() {
        return RotationHelper.deltaRotation(mTouchRotation, mDisplayRotation);
    }

    public PagedOrientationHandler getOrientationHandler() {
        return mOrientationHandler;
    }
}
