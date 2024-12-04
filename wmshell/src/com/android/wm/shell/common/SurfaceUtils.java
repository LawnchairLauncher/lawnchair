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

package com.android.wm.shell.common;

import android.view.SurfaceControl;
import android.view.SurfaceSession;

/**
 * Helpers for handling surface.
 */
public class SurfaceUtils {
    /** Creates a dim layer above host surface. */
    public static SurfaceControl makeDimLayer(SurfaceControl.Transaction t, SurfaceControl host,
            String name, SurfaceSession surfaceSession) {
        final SurfaceControl dimLayer = makeColorLayer(host, name, surfaceSession);
        t.setLayer(dimLayer, Integer.MAX_VALUE).setColor(dimLayer, new float[]{0f, 0f, 0f});
        return dimLayer;
    }

    /** Creates a color layer for host surface. */
    public static SurfaceControl makeColorLayer(SurfaceControl host, String name,
            SurfaceSession surfaceSession) {
        return new SurfaceControl.Builder(surfaceSession)
                .setParent(host)
                .setColorLayer()
                .setName(name)
                .setCallsite("SurfaceUtils.makeColorLayer")
                .build();
    }
}
