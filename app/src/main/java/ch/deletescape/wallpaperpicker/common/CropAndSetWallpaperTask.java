/*
 * Copyright (C) 2015 The Android Open Source Project
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
package ch.deletescape.wallpaperpicker.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import ch.deletescape.lawnchair.R;

public class CropAndSetWallpaperTask extends AsyncTask<Integer, Void, Boolean> {

    public interface OnBitmapCroppedHandler {
        void onBitmapCropped(byte[] imageBytes);
    }

    public interface OnEndCropHandler {
        void run(boolean cropSucceeded);
    }

    private static final int DEFAULT_COMPRESS_QUALITY = 90;
    private static final String TAG = "CropAndSetWallpaperTask";

    private final InputStreamProvider mStreamProvider;
    private final Context mContext;

    private final RectF mCropBounds;
    private int mOutWidth, mOutHeight;
    private int mRotation;
    private CropAndSetWallpaperTask.OnEndCropHandler mOnEndCropHandler;
    private CropAndSetWallpaperTask.OnBitmapCroppedHandler mOnBitmapCroppedHandler;

    public CropAndSetWallpaperTask(InputStreamProvider streamProvider, Context context,
                                   RectF cropBounds, int rotation, int outWidth, int outHeight,
                                   OnEndCropHandler onEndCropHandler) {
        mStreamProvider = streamProvider;
        mContext = context;

        mCropBounds = cropBounds;
        mRotation = rotation;
        mOutWidth = outWidth;
        mOutHeight = outHeight;
        mOnEndCropHandler = onEndCropHandler;
    }

    public void setOnBitmapCropped(CropAndSetWallpaperTask.OnBitmapCroppedHandler handler) {
        mOnBitmapCroppedHandler = handler;
    }

    public boolean cropBitmap(int whichWallpaper) {
        Bitmap crop = mStreamProvider.readCroppedBitmap(
                mCropBounds, mOutWidth, mOutHeight, mRotation);
        if (crop == null) {
            return false;
        }

        boolean failure = false;
        // Compress to byte array
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
        if (crop.compress(CompressFormat.JPEG, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
            // Set the wallpaper
            try {
                byte[] outByteArray = tmpOut.toByteArray();
                WallpaperManagerCompat.getInstance(mContext).setStream(
                        new ByteArrayInputStream(outByteArray),
                        null, true, whichWallpaper);
                if (mOnBitmapCroppedHandler != null) {
                    mOnBitmapCroppedHandler.onBitmapCropped(outByteArray);
                }
            } catch (IOException e) {
                Log.w(TAG, "cannot write stream to wallpaper", e);
                failure = true;
            }
        } else {
            Log.w(TAG, "cannot compress bitmap");
            failure = true;
        }
        return !failure; // True if any of the operations failed
    }

    @Override
    protected Boolean doInBackground(Integer... whichWallpaper) {
        return cropBitmap(whichWallpaper[0]);
    }

    @Override
    protected void onPostExecute(Boolean cropSucceeded) {
        if (!cropSucceeded) {
            Toast.makeText(mContext, R.string.wallpaper_set_fail, Toast.LENGTH_SHORT).show();
        }
        if (mOnEndCropHandler != null) {
            mOnEndCropHandler.run(cropSucceeded);
        }
    }
}