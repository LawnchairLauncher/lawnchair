/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.common;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.exif.ExifInterface;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BitmapUtils {

    private static final String TAG = "BitmapUtils";

    // Find the min x that 1 / x >= scale
    public static int computeSampleSizeLarger(float scale) {
        int initialSize = (int) Math.floor(1f / scale);
        if (initialSize <= 1) return 1;

        return initialSize <= 8
                ? Utils.prevPowerOf2(initialSize)
                : initialSize / 8 * 8;
    }

    public static int getRotationFromExif(Context context, Uri uri) {
        return BitmapUtils.getRotationFromExifHelper(null, 0, context, uri);
    }

    public static int getRotationFromExif(Resources res, int resId) {
        return BitmapUtils.getRotationFromExifHelper(res, resId, null, null);
    }

    private static int getRotationFromExifHelper(Resources res, int resId, Context context, Uri uri) {
        ExifInterface ei = new ExifInterface();
        InputStream is = null;
        BufferedInputStream bis = null;
        try {
            if (uri != null) {
                is = context.getContentResolver().openInputStream(uri);
                bis = new BufferedInputStream(is);
                ei.readExif(bis);
            } else {
                is = res.openRawResource(resId);
                bis = new BufferedInputStream(is);
                ei.readExif(bis);
            }
            Integer ori = ei.getTagIntValue(ExifInterface.TAG_ORIENTATION);
            if (ori != null) {
                return ExifInterface.getRotationForOrientationValue(ori.shortValue());
            }
        } catch (IOException e) {
            Log.w(TAG, "Getting exif data failed", e);
        } catch (NullPointerException e) {
            // Sometimes the ExifInterface has an internal NPE if Exif data isn't valid
            Log.w(TAG, "Getting exif data failed", e);
        } finally {
            Utils.closeSilently(bis);
            Utils.closeSilently(is);
        }
        return 0;
    }
}
