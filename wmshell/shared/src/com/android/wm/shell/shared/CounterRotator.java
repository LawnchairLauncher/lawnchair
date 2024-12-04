/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.shared;

import android.graphics.Point;
import android.util.RotationUtils;
import android.view.SurfaceControl;

/**
 * Utility class that takes care of rotating unchanging child-surfaces to match the parent rotation
 * during a transition animation. This gives the illusion that the child surfaces haven't rotated
 * relative to the screen.
 */
public class CounterRotator {
    private SurfaceControl mSurface = null;

    /** Gets the surface with the counter-rotation. */
    public SurfaceControl getSurface() {
        return mSurface;
    }

    /**
     * Sets up this rotator.
     *
     * @param rotateDelta is the forward rotation change (the rotation the display is making).
     * @param parentW (and H) Is the size of the rotating parent after the rotation.
     */
    public void setup(SurfaceControl.Transaction t, SurfaceControl parent, int rotateDelta,
            float parentW, float parentH) {
        if (rotateDelta == 0) return;
        mSurface = new SurfaceControl.Builder()
                .setName("Transition Unrotate")
                .setContainerLayer()
                .setParent(parent)
                .build();
        // Rotate forward to match the new rotation (rotateDelta is the forward rotation the parent
        // already took). Child surfaces will be in the old rotation relative to the new parent
        // rotation, so we need to forward-rotate the child surfaces to match.
        RotationUtils.rotateSurface(t, mSurface, rotateDelta);
        final Point tmpPt = new Point(0, 0);
        // parentW/H are the size in the END rotation, the rotation utilities expect the starting
        // size. So swap them if necessary
        if ((rotateDelta % 2) != 0) {
            final float w = parentW;
            parentW = parentH;
            parentH = w;
        }
        RotationUtils.rotatePoint(tmpPt, rotateDelta, (int) parentW, (int) parentH);
        t.setPosition(mSurface, tmpPt.x, tmpPt.y);
        t.show(mSurface);
    }

    /**
     * Adds a surface that needs to be counter-rotate.
     */
    public void addChild(SurfaceControl.Transaction t, SurfaceControl child) {
        if (mSurface == null) return;
        t.reparent(child, mSurface);
    }

    /**
     * Clean-up. Since finishTransaction should reset all change leashes, we only need to remove the
     * counter rotation surface.
     */
    public void cleanUp(SurfaceControl.Transaction finishTransaction) {
        if (mSurface == null) return;
        finishTransaction.remove(mSurface);
    }
}
