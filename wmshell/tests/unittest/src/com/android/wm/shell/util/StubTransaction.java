/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wm.shell.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.ColorSpace;
import android.graphics.GraphicBuffer;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.os.IBinder;
import android.os.Parcel;
import android.view.InputWindowHandle;
import android.view.Surface;
import android.view.SurfaceControl;

import java.util.HashSet;
import java.util.concurrent.Executor;

/**
 * Stubbed {@link SurfaceControl.Transaction} class that can be used when unit
 * testing to avoid calls to native code.
 *
 * Note: This is a copy of com.android.server.testutils.StubTransaction
 */
public class StubTransaction extends SurfaceControl.Transaction {

    private HashSet<Runnable> mWindowInfosReportedListeners = new HashSet<>();

    @Override
    public void apply() {
        for (Runnable listener : mWindowInfosReportedListeners) {
            listener.run();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void apply(boolean sync) {
        apply();
    }

    @Override
    public SurfaceControl.Transaction setVisibility(SurfaceControl sc, boolean visible) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction show(SurfaceControl sc) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction hide(SurfaceControl sc) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setPosition(SurfaceControl sc, float x, float y) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setBufferSize(SurfaceControl sc,
            int w, int h) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setLayer(SurfaceControl sc, int z) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setRelativeLayer(SurfaceControl sc, SurfaceControl relativeTo,
            int z) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setTransparentRegionHint(SurfaceControl sc,
            Region transparentRegion) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setAlpha(SurfaceControl sc, float alpha) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setInputWindowInfo(SurfaceControl sc,
            InputWindowHandle handle) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setGeometry(SurfaceControl sc, Rect sourceCrop,
            Rect destFrame, @Surface.Rotation int orientation) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setMatrix(SurfaceControl sc,
            float dsdx, float dtdx, float dtdy, float dsdy) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setMatrix(SurfaceControl sc, Matrix matrix, float[] float9) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setColorTransform(SurfaceControl sc, float[] matrix,
            float[] translation) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setWindowCrop(SurfaceControl sc, Rect crop) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setWindowCrop(SurfaceControl sc, int width, int height) {
        return this;
    }

    @Override
    @NonNull
    public SurfaceControl.Transaction setCrop(@NonNull SurfaceControl sc, @Nullable Rect crop) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setCornerRadius(SurfaceControl sc, float cornerRadius) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setBackgroundBlurRadius(SurfaceControl sc, int radius) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setLayerStack(SurfaceControl sc, int layerStack) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction reparent(SurfaceControl sc, SurfaceControl newParent) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setColor(SurfaceControl sc, float[] color) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setSecure(SurfaceControl sc, boolean isSecure) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setOpaque(SurfaceControl sc, boolean isOpaque) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setDisplaySurface(IBinder displayToken, Surface surface) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setDisplayLayerStack(IBinder displayToken, int layerStack) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setDisplayFlags(IBinder displayToken, int flags) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setDisplayProjection(IBinder displayToken,
            int orientation, Rect layerStackRect, Rect displayRect) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setDisplaySize(IBinder displayToken, int width, int height) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setAnimationTransaction() {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setMetadata(SurfaceControl sc, int key, int data) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setMetadata(SurfaceControl sc, int key, Parcel data) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction merge(SurfaceControl.Transaction other) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction remove(SurfaceControl sc) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction addTransactionCommittedListener(Executor executor,
            SurfaceControl.TransactionCommittedListener listener) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setColorSpaceAgnostic(SurfaceControl sc, boolean agnostic) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setFrameRateSelectionPriority(SurfaceControl sc,
            int priority) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setFrameRate(SurfaceControl sc, float frameRate,
            int compatibility, int changeFrameRateStrategy) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction unsetColor(SurfaceControl sc) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setShadowRadius(SurfaceControl sc, float shadowRadius) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setFixedTransformHint(SurfaceControl sc,
            @Surface.Rotation int transformHint) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction unsetFixedTransformHint(@NonNull SurfaceControl sc) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setBuffer(SurfaceControl sc, GraphicBuffer buffer) {
        return this;
    }

    @Override
    @NonNull
    public SurfaceControl.Transaction setBuffer(@NonNull SurfaceControl sc,
            @Nullable HardwareBuffer buffer) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setColorSpace(SurfaceControl sc, ColorSpace colorSpace) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction setTrustedOverlay(SurfaceControl sc,
            boolean isTrustedOverlay) {
        return this;
    }

    @Override
    public SurfaceControl.Transaction addWindowInfosReportedListener(@NonNull Runnable listener) {
        mWindowInfosReportedListeners.add(listener);
        return this;
    }
}
