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

package ch.deletescape.wallpaperpicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import ch.deletescape.wallpaperpicker.common.ExifOrientation;
import ch.deletescape.wallpaperpicker.common.InputStreamProvider;
import ch.deletescape.wallpaperpicker.common.Utils;
import ch.deletescape.wallpaperpicker.glrenderer.BasicTexture;
import ch.deletescape.wallpaperpicker.glrenderer.BitmapTexture;
import ch.deletescape.wallpaperpicker.views.TiledImageRenderer;

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
 * A {@link ch.deletescape.wallpaperpicker.views.TiledImageRenderer.TileSource} using
 * {@link BitmapRegionDecoder} to wrap a local file
 */
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

        public enum State {NOT_LOADED, LOADED, ERROR_LOADING}

        private State mState = State.NOT_LOADED;

        /**
         * Returns whether loading was successful.
         */
        public boolean loadInBackground(InBitmapProvider bitmapProvider) {
            mRotation = getExifRotation();
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
                opts.inSampleSize = Utils.computeSampleSizeLarger(scale);
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

        public abstract int getExifRotation();

        public abstract SimpleBitmapRegionDecoder loadBitmapRegionDecoder();

        public abstract Bitmap loadPreviewBitmap(BitmapFactory.Options options);

        public interface InBitmapProvider {
            Bitmap forPixelCount(int count);
        }
    }

    public static class InputStreamSource extends BitmapSource {
        private final InputStreamProvider mStreamProvider;
        private final Context mContext;

        public InputStreamSource(Context context, Uri uri) {
            this(InputStreamProvider.fromUri(context, uri), context);
        }

        public InputStreamSource(InputStreamProvider streamProvider, Context context) {
            mStreamProvider = streamProvider;
            mContext = context;
        }

        @Override
        public SimpleBitmapRegionDecoder loadBitmapRegionDecoder() {
            try {
                InputStream is = mStreamProvider.newStreamNotNull();
                SimpleBitmapRegionDecoder regionDecoder =
                        SimpleBitmapRegionDecoderWrapper.newInstance(is, false);
                Utils.closeSilently(is);
                if (regionDecoder == null) {
                    is = mStreamProvider.newStreamNotNull();
                    regionDecoder = DumbBitmapRegionDecoder.newInstance(is);
                    Utils.closeSilently(is);
                }
                return regionDecoder;
            } catch (IOException e) {
                Log.e("InputStreamSource", "Failed to load stream", e);
                return null;
            }
        }

        @Override
        public int getExifRotation() {
            return mStreamProvider.getRotationFromExif(mContext);
        }

        @Override
        public Bitmap loadPreviewBitmap(BitmapFactory.Options options) {
            try {
                InputStream is = mStreamProvider.newStreamNotNull();
                Bitmap b = BitmapFactory.decodeStream(is, null, options);
                Utils.closeSilently(is);
                return b;
            } catch (IOException | OutOfMemoryError e) {
                Log.e("InputStreamSource", "Failed to load stream", e);
                return null;
            }
        }
    }

    public static class FilePathBitmapSource extends InputStreamSource {
        private String mPath;

        public FilePathBitmapSource(File file, Context context) {
            super(context, Uri.fromFile(file));
            mPath = file.getAbsolutePath();
        }

        @Override
        public int getExifRotation() {
            return ExifOrientation.readRotation(mPath);
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
