/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.systemui.shared.recents.model;

import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static com.android.systemui.shared.system.WindowManagerWrapper.WINDOWING_MODE_UNDEFINED;

import android.app.ActivityManager.TaskSnapshot;
import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * Data for a single thumbnail.
 */
public class ThumbnailData {

    public final Bitmap thumbnail;
    public int orientation;
    public Rect insets;
    public boolean reducedResolution;
    public boolean isRealSnapshot;
    public boolean isTranslucent;
    public int windowingMode;
    public int systemUiVisibility;
    public float scale;

    public ThumbnailData() {
        thumbnail = null;
        orientation = ORIENTATION_UNDEFINED;
        insets = new Rect();
        reducedResolution = false;
        scale = 1f;
        isRealSnapshot = true;
        isTranslucent = false;
        windowingMode = WINDOWING_MODE_UNDEFINED;
        systemUiVisibility = 0;
    }

    public ThumbnailData(TaskSnapshot snapshot) {
        thumbnail = Bitmap.createHardwareBitmap(snapshot.getSnapshot());
        insets = new Rect(snapshot.getContentInsets());
        orientation = snapshot.getOrientation();
        reducedResolution = snapshot.isReducedResolution();
        scale = snapshot.getScale();
        isRealSnapshot = snapshot.isRealSnapshot();
        isTranslucent = snapshot.isTranslucent();
        windowingMode = snapshot.getWindowingMode();
        systemUiVisibility = snapshot.getSystemUiVisibility();
    }
}
