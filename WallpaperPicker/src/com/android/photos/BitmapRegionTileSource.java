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
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.opengl.GLUtils;
import android.os.Build;
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

    private static final int GL_SIZE_LIMIT = 2048;
    // This must be no larger than half the size of the GL_SIZE_LIMIT
    // due to decodePreview being allowed to be up to 2x the size of the target
    private static final int MAX_PREVIEW_SIZE = GL_SIZE_LIMIT / 2;

    public static abstract class BitmapSource {
        private SimpleBitmapRegionDecoder mDecoder;
        private Bitmap mPreview;
        private int mRotation;
        public enum State { NOT_LOADED, LOADED, ERROR_LOADING };
        private State mState = State.NOT_LOADED;

        /** Returns whether loading was successful. */
        public boolean loadInBackground(InBitmapProvider bitmapProvider) {
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

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                opts.inPreferQualityOverSpeed = true;

                float scale = (float) MAX_PREVIEW_SIZE / Math.max(width, height);
                opts.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
                opts.inJustDecodeBounds = false;
                opts.inMutable = true;

                if (bitmapProvider != null) {
                    int expectedPixles = (width / opts.inSampleSize) * (height / opts.inSampleSize);
                    Bitmap reusableBitmap = bitmapProvider.forPixelCount(expectedPixles);
                    if (reusableBitmap != null) {
                        // Try loading with reusable bitmap
                        opts.inBitmap = reusableBitmap;
                        try {
                            mPreview = loadPreviewBitmap(opts);
                        } catch (IllegalArgumentException e) {
                            Log.d(TAG, "Unable to reuse bitmap", e);
                            opts.inBitmap = null;
                            mPreview = null;
                        }
                    }
                }
                if (mPreview == null) {
                    mPreview = loadPreviewBitmap(opts);
                }
                if (mPreview == null) {
                    mState = State.ERROR_LOADING;
                    return false;
                }

                // Verify that the bitmap can be used on GL surface
                try {
                    GLUtils.getInternalFormat(mPreview);
                    GLUtils.getType(mPreview);
                    mState = State.LOADED;
                } catch (IllegalArgumentException e) {
                    Log.d(TAG, "Image cannot be rendered on a GL surface", e);
                    mState = State.ERROR_LOADING;
                }
                return mState == State.LOADED;
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

        public int getRotation() {
            return mRotation;
        }

        public abstract boolean readExif(ExifInterface ei);
        public abstract SimpleBitmapRegionDecoder loadBitmapRegionDecoder();
        public abstract Bitmap loadPreviewBitmap(BitmapFactory.Options options);

        public interface InBitmapProvider {
            Bitmap forPixelCount(int count);
        }
    }

    public static class FilePathBitmapSource extends BitmapSource {
        private String mPath;
        public FilePathBitmapSource(String path) {
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
            } catch (NullPointerException e) {
                Log.w("BitmapRegionTileSource", "reading exif failed", e);
                return false;
            } catch (IOException e) {
                Log.w("BitmapRegionTileSource", "getting decoder failed", e);
                return false;
            }
        }
    }

    public static class UriBitmapSource extends BitmapSource {
        private Context mContext;
        private Uri mUri;
        public UriBitmapSource(Context context, Uri uri) {
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
            }
        }
        @Override
        public Bitmap loadPreviewBitmap(BitmapFactory.Options options) {
            try {
                InputStream is = regenerateInputStream();
                Bitmap b = BitmapFactory.decodeStream(is, null, options);
                Utils.closeSilently(is);
                return b;
            } catch (FileNotFoundException | OutOfMemoryError e) {
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
                Log.d("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return false;
            } catch (IOException e) {
                Log.d("BitmapRegionTileSource", "Failed to load URI " + mUri, e);
                return false;
            } catch (NullPointerException e) {
                Log.d("BitmapRegionTileSource", "Failed to read EXIF for URI " + mUri, e);
                return false;
            } finally {
                Utils.closeSilently(is);
            }
        }
    }

    public static class ResourceBitmapSource extends BitmapSource {
        private Resources mRes;
        private int mResId;
        public ResourceBitmapSource(Resources res, int resId) {
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
    private BitmapFactory.Options mOptions;

    public BitmapRegionTileSource(Context context, BitmapSource source, byte[] tempStorage) {
        mTileSize = TiledImageRenderer.suggestedTileSize(context);
        mRotation = source.getRotation();
        mDecoder = source.getBitmapRegionDecoder();
        if (mDecoder != null) {
            mWidth = mDecoder.getWidth();
            mHeight = mDecoder.getHeight();
            mOptions = new BitmapFactory.Options();
            mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            mOptions.inPreferQualityOverSpeed = true;
            mOptions.inTempStorage = tempStorage;

            Bitmap preview = source.getPreviewBitmap();
            if (preview != null &&
                    preview.getWidth() <= GL_SIZE_LIMIT && preview.getHeight() <= GL_SIZE_LIMIT) {
                    mPreview = new BitmapTexture(preview);
            } else {
                Log.w(TAG, String.format(
                        "Failed to create preview of apropriate size! "
                        + " in: %dx%d, out: %dx%d",
                        mWidth, mHeight,
                        preview == null ? -1 : preview.getWidth(),
                        preview == null ? -1 : preview.getHeight()));
            }
        }
    }

    public Bitmap getBitmap() {
        return mPreview instanceof BitmapTexture ? ((BitmapTexture) mPreview).getBitmap() : null;
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
}
