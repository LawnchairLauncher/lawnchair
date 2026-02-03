/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.util.launcheremulator;

import static android.view.Surface.ROTATION_0;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.view.WindowInsets;

import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.launcheremulator.models.DeviceEmulationData;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.util.List;

/**
 * Testing class that overrides some values of the Launcher in order to be able to emulate it.
 * This class overrides the singleton of {@code WindowManagerProxy}.
 */
public class TestWindowManagerProxy extends WindowManagerProxy {

    private DeviceEmulationData mDevice;

    private boolean mIsInDesktopMode;

    /**
     * Constructor to be used when initiating using xml overrides.
     *
     * Use a new DisplayController.Info object to avoid circular dependency when initiating
     * DisplayController
     */
    public TestWindowManagerProxy(Context context) {
        this(DeviceEmulationData.Companion.getCurrentDeviceData(context, new Info(context)));
    }

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
        return mDevice.toCachedDisplayInfo(getRotation(displayInfoContext));
    }

    @Override
    public WindowBounds getRealBounds(Context displayInfoContext, CachedDisplayInfo info) {
        List<WindowBounds> windowBounds = estimateInternalDisplayBounds(displayInfoContext).get(
                getDisplayInfo(displayInfoContext).normalize(this));
        return windowBounds.get(getDisplay(displayInfoContext).getRotation());
    }

    @Override
    public WindowInsets normalizeWindowInsets(Context context, WindowInsets oldInsets,
            Rect outInsets) {

        boolean isGesture = isGestureNav(context);
        outInsets.set(getRealBounds(context, getDisplayInfo(context)).insets);

        WindowInsets.Builder insetsBuilder = new WindowInsets.Builder(oldInsets);

        // This is the same implementation used in WindowManagerProxy to prevent the taskbar
        // size to be count in the inset. It overrides the tappable bottom inset to be 0
        // for gesture nav (otherwise taskbar would count towards it).
        // This is used for the bottom protection in All Apps for example.
        if (isGesture) {
            Insets oldTappableInsets = oldInsets.getInsets(WindowInsets.Type.tappableElement());
            Insets newTappableInsets = Insets.of(oldTappableInsets.left, oldTappableInsets.top,
                    oldTappableInsets.right, 0);
            insetsBuilder.setInsets(WindowInsets.Type.tappableElement(), newTappableInsets);
        }

        return insetsBuilder.build();
    }

    protected CachedDisplayInfo getSecondaryDisplayInfo(int rotation) {
        return mDevice.secondDisplay.toCachedDisplayInfo(rotation);
    }

    /**
     * Returns a map of normalized info of internal displays to estimated window bounds
     * for that display
     */
    @Override
    public ArrayMap<CachedDisplayInfo, List<WindowBounds>> estimateInternalDisplayBounds(
            Context displayInfoContext) {
        ArrayMap<CachedDisplayInfo, List<WindowBounds>> result =
                super.estimateInternalDisplayBounds(displayInfoContext);
        if (mDevice.secondDisplay == null) {
            return result;
        }
        CachedDisplayInfo info = getSecondaryDisplayInfo(ROTATION_0).normalize(this);
        result.put(info, estimateWindowBounds(displayInfoContext, info));
        return result;
    }

    @Override
    public boolean isInDesktopMode(int displayId) {
        return mIsInDesktopMode;
    }

    public void setInDesktopMode(boolean isInDesktopMode) {
        mIsInDesktopMode = isInDesktopMode;
    }

    public void setDevice(DeviceEmulationData device) {
        mDevice = device;
    }
}
