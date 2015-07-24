/**
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
package com.android.gallery3d.common;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class BitmapCropTask extends AsyncTask<Void, Void, Boolean> {

    public interface OnBitmapCroppedHandler {
        public void onBitmapCropped(byte[] imageBytes);
    }

    private static final int DEFAULT_COMPRESS_QUALITY = 90;
    private static final String LOGTAG = "BitmapCropTask";

    Uri mInUri = null;
    Context mContext;
    String mInFilePath;
    byte[] mInImageBytes;
    int mInResId = 0;
    RectF mCropBounds = null;
    int mOutWidth, mOutHeight;
    int mRotation;
    boolean mSetWallpaper;
    boolean mSaveCroppedBitmap;
    Bitmap mCroppedBitmap;
    Runnable mOnEndRunnable;
    Resources mResources;
    BitmapCropTask.OnBitmapCroppedHandler mOnBitmapCroppedHandler;
    boolean mNoCrop;

    public BitmapCropTask(Context c, String filePath,
            RectF cropBounds, int rotation, int outWidth, int outHeight,
            boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
        mContext = c;
        mInFilePath = filePath;
        init(cropBounds, rotation,
                outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
    }

    public BitmapCropTask(byte[] imageBytes,
            RectF cropBounds, int rotation, int outWidth, int outHeight,
            boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
        mInImageBytes = imageBytes;
        init(cropBounds, rotation,
                outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
    }

    public BitmapCropTask(Context c, Uri inUri,
            RectF cropBounds, int rotation, int outWidth, int outHeight,
            boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
        mContext = c;
        mInUri = inUri;
        init(cropBounds, rotation,
                outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
    }

    public BitmapCropTask(Context c, Resources res, int inResId,
            RectF cropBounds, int rotation, int outWidth, int outHeight,
            boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
        mContext = c;
        mInResId = inResId;
        mResources = res;
        init(cropBounds, rotation,
                outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
    }

    private void init(RectF cropBounds, int rotation, int outWidth, int outHeight,
            boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
        mCropBounds = cropBounds;
        mRotation = rotation;
        mOutWidth = outWidth;
        mOutHeight = outHeight;
        mSetWallpaper = setWallpaper;
        mSaveCroppedBitmap = saveCroppedBitmap;
        mOnEndRunnable = onEndRunnable;
    }

    public void setOnBitmapCropped(BitmapCropTask.OnBitmapCroppedHandler handler) {
        mOnBitmapCroppedHandler = handler;
    }

    public void setNoCrop(boolean value) {
        mNoCrop = value;
    }

    public void setOnEndRunnable(Runnable onEndRunnable) {
        mOnEndRunnable = onEndRunnable;
    }

    // Helper to setup input stream
    private InputStream regenerateInputStream() {
        if (mInUri == null && mInResId == 0 && mInFilePath == null && mInImageBytes == null) {
            Log.w(LOGTAG, "cannot read original file, no input URI, resource ID, or " +
                    "image byte array given");
        } else {
            try {
                if (mInUri != null) {
                    return new BufferedInputStream(
                            mContext.getContentResolver().openInputStream(mInUri));
                } else if (mInFilePath != null) {
                    return mContext.openFileInput(mInFilePath);
                } else if (mInImageBytes != null) {
                    return new BufferedInputStream(new ByteArrayInputStream(mInImageBytes));
                } else {
                    return new BufferedInputStream(mResources.openRawResource(mInResId));
                }
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "cannot read file: " + mInUri.toString(), e);
            }
        }
        return null;
    }

    public Point getImageBounds() {
        InputStream is = regenerateInputStream();
        if (is != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            Utils.closeSilently(is);
            if (options.outWidth != 0 && options.outHeight != 0) {
                return new Point(options.outWidth, options.outHeight);
            }
        }
        return null;
    }

    public void setCropBounds(RectF cropBounds) {
        mCropBounds = cropBounds;
    }

    public Bitmap getCroppedBitmap() {
        return mCroppedBitmap;
    }
    public boolean cropBitmap() {
        boolean failure = false;


        WallpaperManager wallpaperManager = null;
        if (mSetWallpaper) {
            wallpaperManager = WallpaperManager.getInstance(mContext.getApplicationContext());
        }


        if (mSetWallpaper && mNoCrop) {
            try {
                InputStream is = regenerateInputStream();
                if (is != null) {
                    wallpaperManager.setStream(is);
                    Utils.closeSilently(is);
                }
            } catch (IOException e) {
                Log.w(LOGTAG, "cannot write stream to wallpaper", e);
                failure = true;
            }
            return !failure;
        } else {
            // Find crop bounds (scaled to original image size)
            Rect roundedTrueCrop = new Rect();
            Matrix rotateMatrix = new Matrix();
            Matrix inverseRotateMatrix = new Matrix();

            Point bounds = getImageBounds();
            if (mRotation > 0) {
                rotateMatrix.setRotate(mRotation);
                inverseRotateMatrix.setRotate(-mRotation);

                mCropBounds.roundOut(roundedTrueCrop);
                mCropBounds = new RectF(roundedTrueCrop);

                if (bounds == null) {
                    Log.w(LOGTAG, "cannot get bounds for image");
                    failure = true;
                    return false;
                }

                float[] rotatedBounds = new float[] { bounds.x, bounds.y };
                rotateMatrix.mapPoints(rotatedBounds);
                rotatedBounds[0] = Math.abs(rotatedBounds[0]);
                rotatedBounds[1] = Math.abs(rotatedBounds[1]);

                mCropBounds.offset(-rotatedBounds[0]/2, -rotatedBounds[1]/2);
                inverseRotateMatrix.mapRect(mCropBounds);
                mCropBounds.offset(bounds.x/2, bounds.y/2);

            }

            mCropBounds.roundOut(roundedTrueCrop);

            if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
                Log.w(LOGTAG, "crop has bad values for full size image");
                failure = true;
                return false;
            }

            // See how much we're reducing the size of the image
            int scaleDownSampleSize = Math.max(1, Math.min(roundedTrueCrop.width() / mOutWidth,
                    roundedTrueCrop.height() / mOutHeight));
            // Attempt to open a region decoder
            BitmapRegionDecoder decoder = null;
            InputStream is = null;
            try {
                is = regenerateInputStream();
                if (is == null) {
                    Log.w(LOGTAG, "cannot get input stream for uri=" + mInUri.toString());
                    failure = true;
                    return false;
                }
                decoder = BitmapRegionDecoder.newInstance(is, false);
                Utils.closeSilently(is);
            } catch (IOException e) {
                Log.w(LOGTAG, "cannot open region decoder for file: " + mInUri.toString(), e);
            } finally {
               Utils.closeSilently(is);
               is = null;
            }

            Bitmap crop = null;
            if (decoder != null) {
                // Do region decoding to get crop bitmap
                BitmapFactory.Options options = new BitmapFactory.Options();
                if (scaleDownSampleSize > 1) {
                    options.inSampleSize = scaleDownSampleSize;
                }
                crop = decoder.decodeRegion(roundedTrueCrop, options);
                decoder.recycle();
            }

            if (crop == null) {
                // BitmapRegionDecoder has failed, try to crop in-memory
                is = regenerateInputStream();
                Bitmap fullSize = null;
                if (is != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (scaleDownSampleSize > 1) {
                        options.inSampleSize = scaleDownSampleSize;
                    }
                    fullSize = BitmapFactory.decodeStream(is, null, options);
                    Utils.closeSilently(is);
                }
                if (fullSize != null) {
                    // Find out the true sample size that was used by the decoder
                    scaleDownSampleSize = bounds.x / fullSize.getWidth();
                    mCropBounds.left /= scaleDownSampleSize;
                    mCropBounds.top /= scaleDownSampleSize;
                    mCropBounds.bottom /= scaleDownSampleSize;
                    mCropBounds.right /= scaleDownSampleSize;
                    mCropBounds.roundOut(roundedTrueCrop);

                    // Adjust values to account for issues related to rounding
                    if (roundedTrueCrop.width() > fullSize.getWidth()) {
                        // Adjust the width
                        roundedTrueCrop.right = roundedTrueCrop.left + fullSize.getWidth();
                    }
                    if (roundedTrueCrop.right > fullSize.getWidth()) {
                        // Adjust the left value
                        int adjustment = roundedTrueCrop.left -
                                Math.max(0, roundedTrueCrop.right - roundedTrueCrop.width());
                        roundedTrueCrop.left -= adjustment;
                        roundedTrueCrop.right -= adjustment;
                    }
                    if (roundedTrueCrop.height() > fullSize.getHeight()) {
                        // Adjust the height
                        roundedTrueCrop.bottom = roundedTrueCrop.top + fullSize.getHeight();
                    }
                    if (roundedTrueCrop.bottom > fullSize.getHeight()) {
                        // Adjust the top value
                        int adjustment = roundedTrueCrop.top -
                                Math.max(0, roundedTrueCrop.bottom - roundedTrueCrop.height());
                        roundedTrueCrop.top -= adjustment;
                        roundedTrueCrop.bottom -= adjustment;
                    }

                    crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left,
                            roundedTrueCrop.top, roundedTrueCrop.width(),
                            roundedTrueCrop.height());
                }
            }

            if (crop == null) {
                Log.w(LOGTAG, "cannot decode file: " + mInUri.toString());
                failure = true;
                return false;
            }
            if (mOutWidth > 0 && mOutHeight > 0 || mRotation > 0) {
                float[] dimsAfter = new float[] { crop.getWidth(), crop.getHeight() };
                rotateMatrix.mapPoints(dimsAfter);
                dimsAfter[0] = Math.abs(dimsAfter[0]);
                dimsAfter[1] = Math.abs(dimsAfter[1]);

                if (!(mOutWidth > 0 && mOutHeight > 0)) {
                    mOutWidth = Math.round(dimsAfter[0]);
                    mOutHeight = Math.round(dimsAfter[1]);
                }

                RectF cropRect = new RectF(0, 0, dimsAfter[0], dimsAfter[1]);
                RectF returnRect = new RectF(0, 0, mOutWidth, mOutHeight);

                Matrix m = new Matrix();
                if (mRotation == 0) {
                    m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
                } else {
                    Matrix m1 = new Matrix();
                    m1.setTranslate(-crop.getWidth() / 2f, -crop.getHeight() / 2f);
                    Matrix m2 = new Matrix();
                    m2.setRotate(mRotation);
                    Matrix m3 = new Matrix();
                    m3.setTranslate(dimsAfter[0] / 2f, dimsAfter[1] / 2f);
                    Matrix m4 = new Matrix();
                    m4.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);

                    Matrix c1 = new Matrix();
                    c1.setConcat(m2, m1);
                    Matrix c2 = new Matrix();
                    c2.setConcat(m4, m3);
                    m.setConcat(c2, c1);
                }

                Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
                        (int) returnRect.height(), Bitmap.Config.ARGB_8888);
                if (tmp != null) {
                    Canvas c = new Canvas(tmp);
                    Paint p = new Paint();
                    p.setFilterBitmap(true);
                    c.drawBitmap(crop, m, p);
                    crop = tmp;
                }
            }

            if (mSaveCroppedBitmap) {
                mCroppedBitmap = crop;
            }

            // Compress to byte array
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
            if (crop.compress(CompressFormat.JPEG, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
                // If we need to set to the wallpaper, set it
                if (mSetWallpaper && wallpaperManager != null) {
                    try {
                        byte[] outByteArray = tmpOut.toByteArray();
                        wallpaperManager.setStream(new ByteArrayInputStream(outByteArray));
                        if (mOnBitmapCroppedHandler != null) {
                            mOnBitmapCroppedHandler.onBitmapCropped(outByteArray);
                        }
                    } catch (IOException e) {
                        Log.w(LOGTAG, "cannot write stream to wallpaper", e);
                        failure = true;
                    }
                }
            } else {
                Log.w(LOGTAG, "cannot compress bitmap");
                failure = true;
            }
        }
        return !failure; // True if any of the operations failed
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return cropBitmap();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (mOnEndRunnable != null) {
            mOnEndRunnable.run();
        }
    }
}