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
 * limitations under the License.
 */

package app.lawnchair.compatlib.utils;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ParcelableColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Bundle;
import java.util.Objects;
/**
 * Utils for working with Bitmaps.
 */
object BitmapUtil {

    private const val KEY_BUFFER = "bitmap_util_buffer"
    private const val KEY_COLOR_SPACE = "bitmap_util_color_space"

    /**
     * Creates a Bundle that represents the given Bitmap.
     * The Bundle will contain a wrapped version of the Bitmap's HardwareBuffer, so will avoid
     * copies when passing across processes, only pass to processes you trust.
     *
     * Returns a new Bundle rather than modifying an existing one to avoid key collisions, the
     * returned Bundle should be treated as a standalone object.
     *
     * @param bitmap to convert to a bundle
     * @return a Bundle representing the bitmap, should only be parsed by
     *         [bundleToHardwareBitmap]
     */
    @JvmStatic
    fun hardwareBitmapToBundle(bitmap: Bitmap): Bundle {
        if (bitmap.config != Bitmap.Config.HARDWARE) {
            throw IllegalArgumentException("Passed bitmap must have hardware config, found: ${bitmap.config}")
        }
        // Bitmap assumes SRGB for null color space
        val colorSpace = bitmap.colorSpace?.let { ParcelableColorSpace(it) }
            ?: ParcelableColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        val bundle = Bundle()
        bundle.putParcelable(KEY_BUFFER, bitmap.hardwareBuffer)
        bundle.putParcelable(KEY_COLOR_SPACE, colorSpace)
        return bundle
    }

    /**
     * Extracts the Bitmap added to a Bundle with [hardwareBitmapToBundle].
     *
     * This Bitmap contains the HardwareBuffer from the original caller, be careful passing this
     * Bitmap on to any other source.
     *
     * @param bundle containing the bitmap
     * @return a hardware Bitmap
     */
    fun bundleToHardwareBitmap(bundle: Bundle): Bitmap {
        if (!bundle.containsKey(KEY_BUFFER) || !bundle.containsKey(KEY_COLOR_SPACE)) {
            throw IllegalArgumentException("Bundle does not contain a hardware bitmap")
        }
        val buffer: HardwareBuffer = bundle.getParcelable(KEY_BUFFER)!!
        val colorSpace: ParcelableColorSpace = bundle.getParcelable(KEY_COLOR_SPACE)!!
        return Bitmap.wrapHardwareBuffer(Objects.requireNonNull(buffer), colorSpace.colorSpace)
    }
}
