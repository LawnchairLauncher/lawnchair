/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.icons;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Build.VERSION_CODES;

/**
 * Interface representing a bitmap draw operation.
 */
public interface BitmapRenderer {

    boolean USE_HARDWARE_BITMAP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;

    static Bitmap createSoftwareBitmap(int width, int height, BitmapRenderer renderer) {
        GraphicsUtils.noteNewBitmapCreated();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(result));
        return result;
    }

    @TargetApi(Build.VERSION_CODES.P)
    static Bitmap createHardwareBitmap(int width, int height, BitmapRenderer renderer) {
        if (!USE_HARDWARE_BITMAP) {
            return createSoftwareBitmap(width, height, renderer);
        }

        GraphicsUtils.noteNewBitmapCreated();
        Picture picture = new Picture();
        renderer.draw(picture.beginRecording(width, height));
        picture.endRecording();
        return Bitmap.createBitmap(picture);
    }

    /**
     * Returns a bitmap from subset of the source bitmap. The new bitmap may be the
     * same object as source, or a copy may have been made.
     */
    static Bitmap createBitmap(Bitmap source, int x, int y, int width, int height) {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.O && source.getConfig() == Config.HARDWARE) {
            return createHardwareBitmap(width, height, c -> c.drawBitmap(source,
                    new Rect(x, y, x + width, y + height), new RectF(0, 0, width, height), null));
        } else {
            GraphicsUtils.noteNewBitmapCreated();
            return Bitmap.createBitmap(source, x, y, width, height);
        }
    }

    void draw(Canvas out);
}
