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

import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.window.ScreenCapture;

import java.util.function.Consumer;

/**
 * Helpers for working with screenshots.
 */
public class ScreenshotUtils {

    /**
     * Takes a screenshot of the specified SurfaceControl.
     *
     * @param sc the SurfaceControl to take a screenshot of
     * @param crop the crop to use when capturing the screenshot
     * @param consumer Consumer for the captured buffer
     */
    public static void captureLayer(SurfaceControl sc, Rect crop,
            Consumer<ScreenCapture.ScreenshotHardwareBuffer> consumer) {
        consumer.accept(ScreenCapture.captureLayers(
                new ScreenCapture.LayerCaptureArgs.Builder(sc)
                    .setSourceCrop(crop)
                    .setCaptureSecureLayers(true)
                    .setAllowProtected(true)
                    .setHintForSeamlessTransition(true)
                    .build()));
    }

    private static class BufferConsumer implements
            Consumer<ScreenCapture.ScreenshotHardwareBuffer> {
        SurfaceControl mScreenshot = null;
        SurfaceControl.Transaction mTransaction;
        SurfaceControl mSurfaceControl;
        SurfaceControl mParentSurfaceControl;
        int mLayer;

        BufferConsumer(SurfaceControl.Transaction t, SurfaceControl sc, SurfaceControl parentSc,
                int layer) {
            mTransaction = t;
            mSurfaceControl = sc;
            mParentSurfaceControl = parentSc;
            mLayer = layer;
        }

        @Override
        public void accept(ScreenCapture.ScreenshotHardwareBuffer buffer) {
            if (buffer == null || buffer.getHardwareBuffer() == null) {
                return;
            }
            mScreenshot = new SurfaceControl.Builder()
                .setName("ScreenshotUtils screenshot")
                .setFormat(PixelFormat.TRANSLUCENT)
                .setSecure(buffer.containsSecureLayers())
                .setCallsite("ScreenshotUtils.takeScreenshot")
                .setBLASTLayer()
                .build();

            mTransaction.setBuffer(mScreenshot, buffer.getHardwareBuffer());
            mTransaction.setColorSpace(mScreenshot, buffer.getColorSpace());
            mTransaction.reparent(mScreenshot, mParentSurfaceControl);
            mTransaction.setLayer(mScreenshot, mLayer);
            if (buffer.containsHdrLayers()) {
                mTransaction.setDimmingEnabled(mScreenshot, false);
            }
            mTransaction.show(mScreenshot);
            mTransaction.apply();
        }
    }

    /**
     * Takes a screenshot of the specified SurfaceControl.
     *
     * @param t the transaction used to set changes on the resulting screenshot.
     * @param sc the SurfaceControl to take a screenshot of
     * @param crop the crop to use when capturing the screenshot
     * @param layer the layer to place the screenshot
     *
     * @return A SurfaceControl where the screenshot will be attached, or null if failed.
     */
    public static SurfaceControl takeScreenshot(SurfaceControl.Transaction t, SurfaceControl sc,
            Rect crop, int layer) {
        return takeScreenshot(t, sc, sc /* parentSc */, crop, layer);
    }

    /**
     * Takes a screenshot of the specified SurfaceControl.
     *
     * @param t the transaction used to set changes on the resulting screenshot.
     * @param sc the SurfaceControl to take a screenshot of
     * @param parentSc  the SurfaceControl to attach the screenshot to.
     * @param crop the crop to use when capturing the screenshot
     * @param layer the layer to place the screenshot
     *
     * @return A SurfaceControl where the screenshot will be attached, or null if failed.
     */
    public static SurfaceControl takeScreenshot(SurfaceControl.Transaction t, SurfaceControl sc,
            SurfaceControl parentSc, Rect crop, int layer) {
        BufferConsumer consumer = new BufferConsumer(t, sc, parentSc, layer);
        captureLayer(sc, crop, consumer);
        return consumer.mScreenshot;
    }
}
