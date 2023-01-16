/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.systemui.shared.system;

import android.graphics.HardwareRenderer;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;

import java.util.function.LongConsumer;

/**
 * Helper class to expose some ViewRoomImpl methods
 */
public class ViewRootImplCompat {

    private final ViewRootImpl mViewRoot;

    public ViewRootImplCompat(View view) {
        mViewRoot = view == null ? null : view.getViewRootImpl();
    }

    public SurfaceControl getRenderSurfaceControl() {
        return mViewRoot == null ? null : mViewRoot.getSurfaceControl();
    }

    public boolean isValid() {
        return mViewRoot != null;
    }

    public View getView() {
        return mViewRoot == null ? null : mViewRoot.getView();
    }

    public void registerRtFrameCallback(LongConsumer callback) {
        if (mViewRoot != null) {
            mViewRoot.registerRtFrameCallback(
                    new HardwareRenderer.FrameDrawingCallback() {
                        @Override
                        public void onFrameDraw(long l) {
                            callback.accept(l);
                        }
                    });
        }
    }

    public void mergeWithNextTransaction(SurfaceControl.Transaction t, long frame) {
        if (mViewRoot != null) {
            mViewRoot.mergeWithNextTransaction(t, frame);
        } else {
            t.apply();
        }
    }
}
