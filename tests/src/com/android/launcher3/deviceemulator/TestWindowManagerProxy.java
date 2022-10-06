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
package com.android.launcher3.deviceemulator;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.WindowInsets;

import com.android.launcher3.deviceemulator.models.DeviceEmulationData;
import com.android.launcher3.util.RotationUtils;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

public class TestWindowManagerProxy extends WindowManagerProxy {

    private final DeviceEmulationData mDevice;

    public TestWindowManagerProxy(DeviceEmulationData device) {
        super(true);
        mDevice = device;
    }

    @Override
    protected int getDimenByName(Resources res, String resName) {
        Integer mock = mDevice.resourceOverrides.get(resName);
        return mock != null ? mock : super.getDimenByName(res, resName);
    }

    @Override
    protected int getDimenByName(Resources res, String resName, String fallback) {
        return getDimenByName(res, resName);
    }

    @Override
    public CachedDisplayInfo getDisplayInfo(Context displayInfoContext) {
        int rotation = getRotation(displayInfoContext);
        Point size = new Point(mDevice.width, mDevice.height);
        RotationUtils.rotateSize(size, rotation);
        Rect cutout = new Rect(mDevice.cutout);
        RotationUtils.rotateRect(cutout, rotation);
        return new CachedDisplayInfo(size, rotation, cutout);
    }

    @Override
    public WindowBounds getRealBounds(Context displayInfoContext, CachedDisplayInfo info) {
        return estimateInternalDisplayBounds(displayInfoContext).get(
                getDisplayInfo(displayInfoContext))[getDisplay(displayInfoContext).getRotation()];
    }

    @Override
    public WindowInsets normalizeWindowInsets(Context context, WindowInsets oldInsets,
            Rect outInsets) {
        outInsets.set(getRealBounds(context, getDisplayInfo(context)).insets);
        return oldInsets;
    }
}
