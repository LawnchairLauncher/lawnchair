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

package com.android.launcher3.shadows;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.app.WallpaperManager;
import android.content.Context;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowUserManager;
import org.robolectric.shadows.ShadowWallpaperManager;

/**
 * Extension of {@link ShadowUserManager} with missing shadow methods
 */
@Implements(WallpaperManager.class)
public class LShadowWallpaperManager extends ShadowWallpaperManager {

    @Implementation
    protected static WallpaperManager getInstance(Context context) {
        return context.getSystemService(WallpaperManager.class);
    }

    /**
     * Remove this once the fix for
     * https://github.com/robolectric/robolectric/issues/5285
     * is available
     */
    public static void initializeMock() {
        WallpaperManager wm = mock(WallpaperManager.class);
        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.setSystemService(Context.WALLPAPER_SERVICE, wm);
        doReturn(0).when(wm).getDesiredMinimumWidth();
        doReturn(0).when(wm).getDesiredMinimumHeight();
    }
}
