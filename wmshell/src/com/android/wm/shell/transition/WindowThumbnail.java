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

package com.android.wm.shell.transition;

import android.graphics.ColorSpace;
import android.graphics.GraphicBuffer;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
import android.view.SurfaceSession;

/**
 * Represents a surface that is displayed over a transition surface.
 */
class WindowThumbnail {

    private SurfaceControl mSurfaceControl;

    private WindowThumbnail() {}

    /** Create a thumbnail surface and attach it over a parent surface. */
    static WindowThumbnail createAndAttach(SurfaceSession surfaceSession, SurfaceControl parent,
            HardwareBuffer thumbnailHeader, SurfaceControl.Transaction t) {
        WindowThumbnail windowThumbnail = new WindowThumbnail();
        windowThumbnail.mSurfaceControl = new SurfaceControl.Builder(surfaceSession)
                .setParent(parent)
                .setName("WindowThumanil : " + parent.toString())
                .setCallsite("WindowThumanil")
                .setFormat(PixelFormat.TRANSLUCENT)
                .build();

        GraphicBuffer graphicBuffer = GraphicBuffer.createFromHardwareBuffer(thumbnailHeader);
        t.setBuffer(windowThumbnail.mSurfaceControl, graphicBuffer);
        t.setColorSpace(windowThumbnail.mSurfaceControl, ColorSpace.get(ColorSpace.Named.SRGB));
        t.setLayer(windowThumbnail.mSurfaceControl, Integer.MAX_VALUE);
        t.show(windowThumbnail.mSurfaceControl);
        t.apply();

        return windowThumbnail;
    }

    SurfaceControl getSurface() {
        return mSurfaceControl;
    }

    /** Remove the thumbnail surface and release the surface. */
    void destroy(SurfaceControl.Transaction t) {
        if (mSurfaceControl == null) {
            return;
        }

        t.remove(mSurfaceControl);
        t.apply();
        mSurfaceControl.release();
        mSurfaceControl = null;
    }
}
