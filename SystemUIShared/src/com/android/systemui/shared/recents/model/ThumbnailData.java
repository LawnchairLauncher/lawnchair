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

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;
import static android.graphics.Bitmap.Config.ARGB_8888;

import static com.android.systemui.shared.system.WindowManagerWrapper.WINDOWING_MODE_UNDEFINED;

import android.window.TaskSnapshot;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.util.Log;
import android.view.WindowInsetsController.Appearance;

/**
 * Data for a single thumbnail.
 */
public class ThumbnailData {

    public final Bitmap thumbnail;
    public int orientation;
    public int rotation;
    public Rect insets;
    public boolean reducedResolution;
    public boolean isRealSnapshot;
    public boolean isTranslucent;
    public int windowingMode;
    public @Appearance int appearance;
    public float scale;
    public long snapshotId;

    public ThumbnailData() {
        thumbnail = null;
        orientation = ORIENTATION_UNDEFINED;
        rotation = ROTATION_UNDEFINED;
        insets = new Rect();
        reducedResolution = false;
        scale = 1f;
        isRealSnapshot = true;
        isTranslucent = false;
        windowingMode = WINDOWING_MODE_UNDEFINED;
        snapshotId = 0;
    }

    private static Bitmap makeThumbnail(TaskSnapshot snapshot) {
        Bitmap thumbnail = null;
        try (final HardwareBuffer buffer = snapshot.getHardwareBuffer()) {
            if (buffer != null) {
                thumbnail = Bitmap.wrapHardwareBuffer(buffer, snapshot.getColorSpace());
            }
        } catch (IllegalArgumentException ex) {
            // TODO(b/157562905): Workaround for a crash when we get a snapshot without this state
            Log.e("ThumbnailData", "Unexpected snapshot without USAGE_GPU_SAMPLED_IMAGE: "
                    + snapshot.getHardwareBuffer(), ex);
        }
        if (thumbnail == null) {
            Point taskSize = snapshot.getTaskSize();
            thumbnail = Bitmap.createBitmap(taskSize.x, taskSize.y, ARGB_8888);
            thumbnail.eraseColor(Color.BLACK);
        }
        return thumbnail;
    }

    public ThumbnailData(TaskSnapshot snapshot) {
        thumbnail = makeThumbnail(snapshot);
        insets = new Rect(snapshot.getContentInsets());
        orientation = snapshot.getOrientation();
        rotation = snapshot.getRotation();
        reducedResolution = snapshot.isLowResolution();
        // TODO(b/149579527): Pass task size instead of computing scale.
        // Assume width and height were scaled the same; compute scale only for width
        scale = (float) thumbnail.getWidth() / snapshot.getTaskSize().x;
        isRealSnapshot = snapshot.isRealSnapshot();
        isTranslucent = snapshot.isTranslucent();
        windowingMode = snapshot.getWindowingMode();
        appearance = snapshot.getAppearance();
        snapshotId = snapshot.getId();
    }
}
