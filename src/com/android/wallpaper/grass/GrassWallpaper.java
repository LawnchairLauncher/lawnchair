/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.wallpaper.grass;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.view.Surface;
import android.renderscript.RenderScript;

public class GrassWallpaper extends WallpaperService {
    public Engine onCreateEngine() {
        return new RenderScriptEngine();
    }

    private class RenderScriptEngine extends Engine {
        private RenderScript mRs;
        private GrassRS mRenderer;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mRenderer = new GrassRS(width, height);
            mRenderer.init(mRs, getResources());
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);

            Surface surface = null;
            while (surface == null) {
                surface = holder.getSurface();
            }
            mRs = new RenderScript(surface);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            mRenderer.destroy();
        }
    }
}
