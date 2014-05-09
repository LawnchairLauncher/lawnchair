/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.photos;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.glrenderer.BitmapTexture;
import com.android.photos.views.TiledImageRenderer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link com.android.photos.views.TiledImageRenderer.TileSource} using
 * {@link BitmapRegionDecoder} to wrap a local file
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class BitmapRegionTileSource implements TiledImageRenderer.TileSource {

    private static final String TAG = "BitmapRegionTileSource";

    private static final boolean REUSE_BITMAP =
            Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN;
    private static final int GL_SIZE_LIMIT = 2048;
    // This must be no larger than half the size of the GL_SIZE_LIMIT
    // due to decodePreview being allowed to be up to 2x the size of the target
    private static final int MAX_PREVIEW_SIZE = 1024;

    BitmapRegionDecoder mDecoder;
    int mWidth;
    int mHeight;
    int mTileSize;
    private BasicTexture mPreview;
    private final int mRotation;

    // For use only by getTile
    private Rect mWantRegion = new Rect();
    private Rect mOverlapRegion = new Rect();
    private BitmapFactory.Options mOptions;
    private Canvas mCanvas;

    public BitmapRegionTileSource(Context context, String path, int previewSize, int rotation) {
        this(null, context, path, null, 0, previewSize, rotation, false);
    }

    public BitmapRegionTileSource(Resources res, Context context, String path, int previewSize, int rotation, boolean assetPath) {
        this(res, context, path, null, 0, previewSize, rotation, assetPath);
    }

    public BitmapRegionTileSource(Context context, Uri uri, int previewSize, int rotation) {
        this(null, context, null, uri, 0, previewSize, rotation, false);
    }

    public BitmapRegionTileSource(Resources res,
            Context context, int resId, int previewSize, int rotation) {
        this(res, context, null, null, resId, previewSize, rotation, false);
    }

    private BitmapRegionTileSource(Resources res,
            Context context, String path, Uri uri, int resId, int previewSize, int rotation, boolean assetPath) {
        mTileSize = TiledImageRenderer.suggestedTileSize(context);
        mRotation = rotation;
        try {
            if (path != null && !assetPath) {
                mDecoder = BitmapRegionDecoder.newInstance(path, true);
            } else if (path != null  && res != null && assetPath) {
                AssetManager am = res.getAssets();
                String[] pathImages = am.list(path);
                if (pathImages == null || pathImages.length == 0) {
                    throw new IOException("did not find any images in path: " + path);
                }
                InputStream is = am.open(path + File.separator + pathImages[0]);
                BufferedInputStream bis = new BufferedInputStream(is);
                mDecoder = BitmapRegionDecoder.newInstance(bis, true);
            } else if (uri != null) {
                InputStream is = context.getContentResolver().openInputStream(uri);
                BufferedInputStream bis = new BufferedInputStream(is);
                mDecoder = BitmapRegionDecoder.newInstance(bis, true);
            } else {
                InputStream is = res.openRawResource(resId);
                BufferedInputStream bis = new BufferedInputStream(is);
                mDecoder = BitmapRegionDecoder.newInstance(bis, true);
            }
            mWidth = mDecoder.getWidth();
            mHeight = mDecoder.getHeight();
        } catch (IOException e) {
            Log.w("BitmapRegionTileSource", "ctor failed", e);
        }
        mOptions = new BitmapFactory.Options();
        mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        mOptions.inPreferQualityOverSpeed = true;
        mOptions.inTempStorage = new byte[16 * 1024];
        if (previewSize != 0) {
            previewSize = Math.min(previewSize, MAX_PREVIEW_SIZE);
            // Although this is the same size as the Bitmap that is likely already
            // loaded, the lifecycle is different and interactions are on a different
            // thread. Thus to simplify, this source will decode its own bitmap.
            Bitmap preview = decodePreview(res, context, path, uri, resId, previewSize, assetPath);
            if (preview.getWidth() <= GL_SIZE_LIMIT && preview.getHeight() <= GL_SIZE_LIMIT) {
                mPreview = new BitmapTexture(preview);
            } else {
                Log.w(TAG, String.format(
                        "Failed to create preview of apropriate size! "
                        + " in: %dx%d, out: %dx%d",
                        mWidth, mHeight,
                        preview.getWidth(), preview.getHeight()));
            }
        }
    }

    @Override
    public int getTileSize() {
        return mTileSize;
    }

    @Override
    public int getImageWidth() {
        return mWidth;
    }

    @Override
    public int getImageHeight() {
        return mHeight;
    }

    @Override
    public BasicTexture getPreview() {
        return mPreview;
    }

    @Override
    public int getRotation() {
        return mRotation;
    }

    @Override
    public Bitmap getTile(int level, int x, int y, Bitmap bitmap) {
        int tileSize = getTileSize();
        if (!REUSE_BITMAP) {
            return getTileWithoutReusingBitmap(level, x, y, tileSize);
        }

        int t = tileSize << level;
        mWantRegion.set(x, y, x + t, y + t);

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
        }

        mOptions.inSampleSize = (1 << level);
        mOptions.inBitmap = bitmap;

        try {
            bitmap = mDecoder.decodeRegion(mWantRegion, mOptions);
        } finally {
            if (mOptions.inBitmap != bitmap && mOptions.inBitmap != null) {
                mOptions.inBitmap = null;
            }
        }

        if (bitmap == null) {
            Log.w("BitmapRegionTileSource", "fail in decoding region");
        }
        return bitmap;
    }

    private Bitmap getTileWithoutReusingBitmap(
            int level, int x, int y, int tileSize) {

        int t = tileSize << level;
        mWantRegion.set(x, y, x + t, y + t);

        mOverlapRegion.set(0, 0, mWidth, mHeight);

        mOptions.inSampleSize = (1 << level);
        Bitmap bitmap = mDecoder.decodeRegion(mOverlapRegion, mOptions);

        if (bitmap == null) {
            Log.w(TAG, "fail in decoding region");
        }

        if (mWantRegion.equals(mOverlapRegion)) {
            return bitmap;
        }

        Bitmap result = Bitmap.createBitmap(tileSize, tileSize, Config.ARGB_8888);
        if (mCanvas == null) {
            mCanvas = new Canvas();
        }
        mCanvas.setBitmap(result);
        mCanvas.drawBitmap(bitmap,
                (mOverlapRegion.left - mWantRegion.left) >> level,
                (mOverlapRegion.top - mWantRegion.top) >> level, null);
        mCanvas.setBitmap(null);
        return result;
    }

    /**
     * Note that the returned bitmap may have a long edge that's longer
     * than the targetSize, but it will always be less than 2x the targetSize
     */
    private Bitmap decodePreview(
            Resources res, Context context, String file, Uri uri, int resId, int targetSize, boolean assetPath) {
        float scale = (float) targetSize / Math.max(mWidth, mHeight);
        mOptions.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        mOptions.inJustDecodeBounds = false;

        Bitmap result = null;
        if (file != null  && res != null && assetPath) {
            try {
                AssetManager am = res.getAssets();
                String[] pathImages = am.list(file);
                if (pathImages == null || pathImages.length == 0) {
                    throw new IOException("did not find any images in path: " + file);
                }
                InputStream is = am.open(file + File.separator + pathImages[0]);
                BufferedInputStream bis = new BufferedInputStream(is);
                result = BitmapFactory.decodeStream(bis, null, mOptions);
            } catch (IOException e) {
                Log.w("BitmapRegionTileSource", "getting preview failed", e);
            }
        } else if (file != null) {
            result = BitmapFactory.decodeFile(file, mOptions);
        } else if (uri != null) {
            try {
                InputStream is = context.getContentResolver().openInputStream(uri);
                BufferedInputStream bis = new BufferedInputStream(is);
                result = BitmapFactory.decodeStream(bis, null, mOptions);
            } catch (IOException e) {
                Log.w("BitmapRegionTileSource", "getting preview failed", e);
            }
        } else {
            result = BitmapFactory.decodeResource(res, resId, mOptions);
        }
        if (result == null) {
            return null;
        }

        // We need to resize down if the decoder does not support inSampleSize
        // or didn't support the specified inSampleSize (some decoders only do powers of 2)
        scale = (float) targetSize / (float) (Math.max(result.getWidth(), result.getHeight()));

        if (scale <= 0.5) {
            result = BitmapUtils.resizeBitmapByScale(result, scale, true);
        }
        return ensureGLCompatibleBitmap(result);
    }

    private static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() != null) {
            return bitmap;
        }
        Bitmap newBitmap = bitmap.copy(Config.ARGB_8888, false);
        bitmap.recycle();
        return newBitmap;
    }
}
