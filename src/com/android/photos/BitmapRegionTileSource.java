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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.glrenderer.BitmapTexture;
import com.android.photos.views.TiledImageRenderer;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

interface SimpleBitmapRegionDecoder {
    int getWidth();
    int getHeight();
    Bitmap decodeRegion(Rect wantRegion, BitmapFactory.Options options);
}

class SimpleBitmapRegionDecoderWrapper implements SimpleBitmapRegionDecoder {
    BitmapRegionDecoder mDecoder;
    private SimpleBitmapRegionDecoderWrapper(BitmapRegionDecoder decoder) {
        mDecoder = decoder;
    }
    public static SimpleBitmapRegionDecoderWrapper newInstance(
            String pathName, boolean isShareable) {
        try {
            BitmapRegionDecoder d = BitmapRegionDecoder.newInstance(pathName, isShareable);
            if (d != null) {
                return new SimpleBitmapRegionDecoderWrapper(d);
            }
        } catch (IOException e) {
            Log.w("BitmapRegionTileSource", "getting decoder failed for path " + pathName, e);
            return null;
        }
        return null;
    }
    public static SimpleBitmapRegionDecoderWrapper newInstance(
            InputStream is, boolean isShareable) {
        try {
            BitmapRegionDecoder d = BitmapRegionDecoder.newInstance(is, isShareable);
            if (d != null) {
                return new SimpleBitmapRegionDecoderWrapper(d);
            }
        } catch (IOException e) {
            Log.w("BitmapRegionTileSource", "getting decoder failed", e);
            return null;
        }
        return null;
    }
    public int getWidth() {
        return mDecoder.getWidth();
    }
    public int getHeight() {
        return mDecoder.getHeight();
    }
    public Bitmap decodeRegion(Rect wantRegion, BitmapFactory.Options options) {
        return mDecoder.decodeRegion(wantRegion, options);
    }
}

class DumbBitmapRegionDecoder implements SimpleBitmapRegionDecoder {
    Bitmap mBuffer;
    Canvas mTempCanvas;
    Paint mTempPaint;
    private DumbBitmapRegionDecoder(Bitmap b) {
        mBuffer = b;
    }
    public static DumbBitmapRegionDecoder newInstance(String pathName) {
        Bitmap b = BitmapFactory.decodeFile(pathName);
        if (b != null) {
            return new DumbBitmapRegionDecoder(b);
        }
        return null;
    }
    public static DumbBitmapRegionDecoder newInstance(InputStream is) {
        Bitmap b = BitmapFactory.decodeStream(is);
        if (b != null) {
            return new DumbBitmapRegionDecoder(b);
        }
        return null;
    }
    public int getWidth() {
        return mBuffer.getWidth();
    }
    public int getHeight() {
        return mBuffer.getHeight();
    }
    public Bitmap decodeRegion(Rect wantRegion, BitmapFactory.Options options) {
        if (mTempCanvas == null) {
            mTempCanvas = new Canvas();
            mTempPaint = new Paint();
            mTempPaint.setFilterBitmap(true);
        }
        int sampleSize = Math.max(options.inSampleSize, 1);
        Bitmap newBitmap = Bitmap.createBitmap(
                wantRegion.width() / sampleSize,
                wantRegion.height() / sampleSize,
                Bitmap.Config.ARGB_8888);
        mTempCanvas.setBitmap(newBitmap);
        mTempCanvas.save();
        mTempCanvas.scale(1f / sampleSize, 1f / sampleSize);
        mTempCanvas.drawBitmap(mBuffer, -wantRegion.left, -wantRegion.top, mTempPaint);
        mTempCanvas.restore();
        mTempCanvas.setBitmap(null);
        return newBitmap;
    }
}

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
    public static final int MAX_PREVIEW_SIZE = GL_SIZE_LIMIT / 2;

    public static abstract class BitmapSource {
        private SimpleBitmapRegionDecoder mDecoder;
        private Bitmap mPreview;
        private int mPreviewSize;
        private int mRotation;
        public enum State { NOT_LOADED, LOADED, ERROR_LOADING };
        private State mState = State.NOT_LOADED;
        public BitmapSource(int previewSize) {
            mPreviewSize = previewSize;
        }
        public boolean loadInBackground() {
            ExifInterface ei = new ExifInterface();
            if (readExif(ei)) {
                Integer ori = ei.getTagIntValue(ExifInterface.TAG_ORIENTATION);
                if (ori != null) {
                    mRotation = ExifInterface.getRotationForOrientationValue(ori.shortValue());
                }
            }
            mDecoder = loadBitmapRegionDecoder();
            if (mDecoder == null) {
                mState = State.ERROR_LOADING;
                return false;
            } else {
                int width = mDecoder.getWidth();
                int height = mDecoder.getHeight();
                if (mPreviewSize != 0) {
                    int previewSize = Math.min(mPreviewSize, MAX_PREVIEW_SIZE);
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    opts.inPreferQualityOverSpeed = true;

                    float scale = (float) previewSize / Math.max(width, height);
                    opts.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
                    opts.inJustDecodeBounds = false;
                    mPreview = loadPreviewBitmap(opts);
                }
                mState = State.LOADED;
                return true;
            }
        }

        public State getLoadingState() {
            return mState;
        }

        public SimpleBitmapRegionDecoder getBitmapRegionDecoder() {
            return mDecoder;
        }

        public Bitmap getPreviewBitmap() {
            return mPreview;
        }

        public int getPreviewSize() {
            return mPreviewSize;
        }

        public int getRotation() {
            return mRotation;
        }

        public abstract boolean readExif(ExifInterface ei);
        public abstract SimpleBitmapRegionDecoder loadBitmapRegionDecoder();
        public abstract Bitmap loadPreviewBitmap(BitmapFactory.Options options);
    }

    public static class FilePathBitmapSource extends BitmapSource {
        private String mPath;
        public FilePathBitmapSource(String path, int previewSize) {
            super(previewSize);
            mPath = path;
        }
        @Override
        public SimpleBitmapRegionDecoder loadBitmapRegionDecoder() {
            SimpleBitmapRegionDecoder d;
            d = SimpleBitmapRegionDecoderWrapper.newInstance(mPath, true);
            if (d == null) {
                d = DumbBitmapRegionDecoder.newInstance(mPath);
            }
            return d;
        }
        @Override
        public Bitmap loadPreviewBitmap(BitmapFactory.Options options) {
            return BitmapFactory.decodeFile(mPath, options);
        }
        @Override
        public boolean readExif(ExifInterface ei) {
            try {
                ei.readExif(mPath);
                return true;
            } catch (IOException e) {
                Log.w("BitmapRegionTileSource", "getting decoder failed", e);
                return false;
            }
        }
    }

    public static class UriBitmapSource extends BitmapSource {
        private Context mContext;
        private Uri mUri;
        public UriBitmapSource(Context context, Uri uri, int previewSize) {
            super(previewSize);
            mContext = context;
            mUri = uri;
        }
        private InputStream regenerateInputStream() throws FileNotFoundException {
            InputStream is = mContext.getContentResolver().openInputStream(mUri);
            return new BufferedInputStream(is);
        }
        @Override
        public SimpleBitmapRegionDecoder loadBitmapRegionDecoder() {
            try {
                InputStream is = regenerateInputStream();
                SimpleBitmapRegionDecoder regionDecoder =
                        SimpleBitmapRegionDecoderWrapper.newInstance(is, false);
                Utils.closeSilently(is);
                if (regionDecoder == null) {
                    is = regenerateInputStream();
                    regionDecoder = DumbBitmapRegionDecoder.newInstance(is);
                    Utils.closeSilently(is);
                }
                return regionDecoder;
            } catch (FileNotFoundException e) {
                Log.e("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return null;
            } catch (IOException e) {
                Log.e("BitmapRegionTileSource", "Failure while reading URI " + mUri, e);
                return null;
            }
        }
        @Override
        public Bitmap loadPreviewBitmap(BitmapFactory.Options options) {
            try {
                InputStream is = regenerateInputStream();
                Bitmap b = BitmapFactory.decodeStream(is, null, options);
                Utils.closeSilently(is);
                return b;
            } catch (FileNotFoundException e) {
                Log.e("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return null;
            }
        }
        @Override
        public boolean readExif(ExifInterface ei) {
            InputStream is = null;
            try {
                is = regenerateInputStream();
                ei.readExif(is);
                Utils.closeSilently(is);
                return true;
            } catch (FileNotFoundException e) {
                Log.e("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return false;
            } catch (IOException e) {
                Log.e("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return false;
            } finally {
                Utils.closeSilently(is);
            }
        }
    }

    public static class ResourceBitmapSource extends BitmapSource {
        private Resources mRes;
        private int mResId;
        public ResourceBitmapSource(Resources res, int resId, int previewSize) {
            super(previewSize);
            mRes = res;
            mResId = resId;
        }
        private InputStream regenerateInputStream() {
            InputStream is = mRes.openRawResource(mResId);
            return new BufferedInputStream(is);
        }
        @Override
        public SimpleBitmapRegionDecoder loadBitmapRegionDecoder() {
            InputStream is = regenerateInputStream();
            SimpleBitmapRegionDecoder regionDecoder =
                    SimpleBitmapRegionDecoderWrapper.newInstance(is, false);
            Utils.closeSilently(is);
            if (regionDecoder == null) {
                is = regenerateInputStream();
                regionDecoder = DumbBitmapRegionDecoder.newInstance(is);
                Utils.closeSilently(is);
            }
            return regionDecoder;
        }
        @Override
        public Bitmap loadPreviewBitmap(BitmapFactory.Options options) {
            return BitmapFactory.decodeResource(mRes, mResId, options);
        }
        @Override
        public boolean readExif(ExifInterface ei) {
            try {
                InputStream is = regenerateInputStream();
                ei.readExif(is);
                Utils.closeSilently(is);
                return true;
            } catch (IOException e) {
                Log.e("BitmapRegionTileSource", "Error reading resource", e);
                return false;
            }
        }
    }

    SimpleBitmapRegionDecoder mDecoder;
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

    public BitmapRegionTileSource(Context context, BitmapSource source) {
        mTileSize = TiledImageRenderer.suggestedTileSize(context);
        mRotation = source.getRotation();
        mDecoder = source.getBitmapRegionDecoder();
        if (mDecoder != null) {
            mWidth = mDecoder.getWidth();
            mHeight = mDecoder.getHeight();
            mOptions = new BitmapFactory.Options();
            mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            mOptions.inPreferQualityOverSpeed = true;
            mOptions.inTempStorage = new byte[16 * 1024];
            int previewSize = source.getPreviewSize();
            if (previewSize != 0) {
                previewSize = Math.min(previewSize, MAX_PREVIEW_SIZE);
                // Although this is the same size as the Bitmap that is likely already
                // loaded, the lifecycle is different and interactions are on a different
                // thread. Thus to simplify, this source will decode its own bitmap.
                Bitmap preview = decodePreview(source, previewSize);
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
    private Bitmap decodePreview(BitmapSource source, int targetSize) {
        Bitmap result = source.getPreviewBitmap();
        if (result == null) {
            return null;
        }

        // We need to resize down if the decoder does not support inSampleSize
        // or didn't support the specified inSampleSize (some decoders only do powers of 2)
        float scale = (float) targetSize / (float) (Math.max(result.getWidth(), result.getHeight()));

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
