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

package com.android.photos.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.v4.util.LongSparseArray;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pools.Pool;
import android.util.Pools.SynchronizedPool;
import android.view.View;
import android.view.WindowManager;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.UploadedTexture;

/**
 * Handles laying out, decoding, and drawing of tiles in GL
 */
public class TiledImageRenderer {
    public static final int SIZE_UNKNOWN = -1;

    private static final String TAG = "TiledImageRenderer";
    private static final int UPLOAD_LIMIT = 1;

    /*
     *  This is the tile state in the CPU side.
     *  Life of a Tile:
     *      ACTIVATED (initial state)
     *              --> IN_QUEUE - by queueForDecode()
     *              --> RECYCLED - by recycleTile()
     *      IN_QUEUE --> DECODING - by decodeTile()
     *               --> RECYCLED - by recycleTile)
     *      DECODING --> RECYCLING - by recycleTile()
     *               --> DECODED  - by decodeTile()
     *               --> DECODE_FAIL - by decodeTile()
     *      RECYCLING --> RECYCLED - by decodeTile()
     *      DECODED --> ACTIVATED - (after the decoded bitmap is uploaded)
     *      DECODED --> RECYCLED - by recycleTile()
     *      DECODE_FAIL -> RECYCLED - by recycleTile()
     *      RECYCLED --> ACTIVATED - by obtainTile()
     */
    private static final int STATE_ACTIVATED = 0x01;
    private static final int STATE_IN_QUEUE = 0x02;
    private static final int STATE_DECODING = 0x04;
    private static final int STATE_DECODED = 0x08;
    private static final int STATE_DECODE_FAIL = 0x10;
    private static final int STATE_RECYCLING = 0x20;
    private static final int STATE_RECYCLED = 0x40;

    private static Pool<Bitmap> sTilePool = new SynchronizedPool<Bitmap>(64);

    // TILE_SIZE must be 2^N
    private int mTileSize;

    private TileSource mModel;
    private BasicTexture mPreview;
    protected int mLevelCount;  // cache the value of mScaledBitmaps.length

    // The mLevel variable indicates which level of bitmap we should use.
    // Level 0 means the original full-sized bitmap, and a larger value means
    // a smaller scaled bitmap (The width and height of each scaled bitmap is
    // half size of the previous one). If the value is in [0, mLevelCount), we
    // use the bitmap in mScaledBitmaps[mLevel] for display, otherwise the value
    // is mLevelCount
    private int mLevel = 0;

    private int mOffsetX;
    private int mOffsetY;

    private int mUploadQuota;
    private boolean mRenderComplete;

    private final RectF mSourceRect = new RectF();
    private final RectF mTargetRect = new RectF();

    private final LongSparseArray<Tile> mActiveTiles = new LongSparseArray<Tile>();

    // The following three queue are guarded by mQueueLock
    private final Object mQueueLock = new Object();
    private final TileQueue mRecycledQueue = new TileQueue();
    private final TileQueue mUploadQueue = new TileQueue();
    private final TileQueue mDecodeQueue = new TileQueue();

    // The width and height of the full-sized bitmap
    protected int mImageWidth = SIZE_UNKNOWN;
    protected int mImageHeight = SIZE_UNKNOWN;

    protected int mCenterX;
    protected int mCenterY;
    protected float mScale;
    protected int mRotation;

    private boolean mLayoutTiles;

    // Temp variables to avoid memory allocation
    private final Rect mTileRange = new Rect();
    private final Rect mActiveRange[] = {new Rect(), new Rect()};

    private TileDecoder mTileDecoder;
    private boolean mBackgroundTileUploaded;

    private int mViewWidth, mViewHeight;
    private View mParent;

    /**
     * Interface for providing tiles to a {@link TiledImageRenderer}
     */
    public static interface TileSource {

        /**
         * If the source does not care about the tile size, it should use
         * {@link TiledImageRenderer#suggestedTileSize(Context)}
         */
        public int getTileSize();
        public int getImageWidth();
        public int getImageHeight();
        public int getRotation();

        /**
         * Return a Preview image if available. This will be used as the base layer
         * if higher res tiles are not yet available
         */
        public BasicTexture getPreview();

        /**
         * The tile returned by this method can be specified this way: Assuming
         * the image size is (width, height), first take the intersection of (0,
         * 0) - (width, height) and (x, y) - (x + tileSize, y + tileSize). If
         * in extending the region, we found some part of the region is outside
         * the image, those pixels are filled with black.
         *
         * If level > 0, it does the same operation on a down-scaled version of
         * the original image (down-scaled by a factor of 2^level), but (x, y)
         * still refers to the coordinate on the original image.
         *
         * The method would be called by the decoder thread.
         */
        public Bitmap getTile(int level, int x, int y, Bitmap reuse);
    }

    public static int suggestedTileSize(Context context) {
        return isHighResolution(context) ? 512 : 256;
    }

    private static boolean isHighResolution(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels > 2048 ||  metrics.widthPixels > 2048;
    }

    public TiledImageRenderer(View parent) {
        mParent = parent;
        mTileDecoder = new TileDecoder();
        mTileDecoder.start();
    }

    public int getViewWidth() {
        return mViewWidth;
    }

    public int getViewHeight() {
        return mViewHeight;
    }

    private void invalidate() {
        mParent.postInvalidate();
    }

    public void setModel(TileSource model, int rotation) {
        if (mModel != model) {
            mModel = model;
            notifyModelInvalidated();
        }
        if (mRotation != rotation) {
            mRotation = rotation;
            mLayoutTiles = true;
        }
    }

    private void calculateLevelCount() {
        if (mPreview != null) {
            mLevelCount = Math.max(0, Utils.ceilLog2(
                mImageWidth / (float) mPreview.getWidth()));
        } else {
            int levels = 1;
            int maxDim = Math.max(mImageWidth, mImageHeight);
            int t = mTileSize;
            while (t < maxDim) {
                t <<= 1;
                levels++;
            }
            mLevelCount = levels;
        }
    }

    public void notifyModelInvalidated() {
        invalidateTiles();
        if (mModel == null) {
            mImageWidth = 0;
            mImageHeight = 0;
            mLevelCount = 0;
            mPreview = null;
        } else {
            mImageWidth = mModel.getImageWidth();
            mImageHeight = mModel.getImageHeight();
            mPreview = mModel.getPreview();
            mTileSize = mModel.getTileSize();
            calculateLevelCount();
        }
        mLayoutTiles = true;
    }

    public void setViewSize(int width, int height) {
        mViewWidth = width;
        mViewHeight = height;
    }

    public void setPosition(int centerX, int centerY, float scale) {
        if (mCenterX == centerX && mCenterY == centerY
                && mScale == scale) {
            return;
        }
        mCenterX = centerX;
        mCenterY = centerY;
        mScale = scale;
        mLayoutTiles = true;
    }

    // Prepare the tiles we want to use for display.
    //
    // 1. Decide the tile level we want to use for display.
    // 2. Decide the tile levels we want to keep as texture (in addition to
    //    the one we use for display).
    // 3. Recycle unused tiles.
    // 4. Activate the tiles we want.
    private void layoutTiles() {
        if (mViewWidth == 0 || mViewHeight == 0 || !mLayoutTiles) {
            return;
        }
        mLayoutTiles = false;

        // The tile levels we want to keep as texture is in the range
        // [fromLevel, endLevel).
        int fromLevel;
        int endLevel;

        // We want to use a texture larger than or equal to the display size.
        mLevel = Utils.clamp(Utils.floorLog2(1f / mScale), 0, mLevelCount);

        // We want to keep one more tile level as texture in addition to what
        // we use for display. So it can be faster when the scale moves to the
        // next level. We choose the level closest to the current scale.
        if (mLevel != mLevelCount) {
            Rect range = mTileRange;
            getRange(range, mCenterX, mCenterY, mLevel, mScale, mRotation);
            mOffsetX = Math.round(mViewWidth / 2f + (range.left - mCenterX) * mScale);
            mOffsetY = Math.round(mViewHeight / 2f + (range.top - mCenterY) * mScale);
            fromLevel = mScale * (1 << mLevel) > 0.75f ? mLevel - 1 : mLevel;
        } else {
            // Activate the tiles of the smallest two levels.
            fromLevel = mLevel - 2;
            mOffsetX = Math.round(mViewWidth / 2f - mCenterX * mScale);
            mOffsetY = Math.round(mViewHeight / 2f - mCenterY * mScale);
        }

        fromLevel = Math.max(0, Math.min(fromLevel, mLevelCount - 2));
        endLevel = Math.min(fromLevel + 2, mLevelCount);

        Rect range[] = mActiveRange;
        for (int i = fromLevel; i < endLevel; ++i) {
            getRange(range[i - fromLevel], mCenterX, mCenterY, i, mRotation);
        }

        // If rotation is transient, don't update the tile.
        if (mRotation % 90 != 0) {
            return;
        }

        synchronized (mQueueLock) {
            mDecodeQueue.clean();
            mUploadQueue.clean();
            mBackgroundTileUploaded = false;

            // Recycle unused tiles: if the level of the active tile is outside the
            // range [fromLevel, endLevel) or not in the visible range.
            int n = mActiveTiles.size();
            for (int i = 0; i < n; i++) {
                Tile tile = mActiveTiles.valueAt(i);
                int level = tile.mTileLevel;
                if (level < fromLevel || level >= endLevel
                        || !range[level - fromLevel].contains(tile.mX, tile.mY)) {
                    mActiveTiles.removeAt(i);
                    i--;
                    n--;
                    recycleTile(tile);
                }
            }
        }

        for (int i = fromLevel; i < endLevel; ++i) {
            int size = mTileSize << i;
            Rect r = range[i - fromLevel];
            for (int y = r.top, bottom = r.bottom; y < bottom; y += size) {
                for (int x = r.left, right = r.right; x < right; x += size) {
                    activateTile(x, y, i);
                }
            }
        }
        invalidate();
    }

    private void invalidateTiles() {
        synchronized (mQueueLock) {
            mDecodeQueue.clean();
            mUploadQueue.clean();

            // TODO(xx): disable decoder
            int n = mActiveTiles.size();
            for (int i = 0; i < n; i++) {
                Tile tile = mActiveTiles.valueAt(i);
                recycleTile(tile);
            }
            mActiveTiles.clear();
        }
    }

    private void getRange(Rect out, int cX, int cY, int level, int rotation) {
        getRange(out, cX, cY, level, 1f / (1 << (level + 1)), rotation);
    }

    // If the bitmap is scaled by the given factor "scale", return the
    // rectangle containing visible range. The left-top coordinate returned is
    // aligned to the tile boundary.
    //
    // (cX, cY) is the point on the original bitmap which will be put in the
    // center of the ImageViewer.
    private void getRange(Rect out,
            int cX, int cY, int level, float scale, int rotation) {

        double radians = Math.toRadians(-rotation);
        double w = mViewWidth;
        double h = mViewHeight;

        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int width = (int) Math.ceil(Math.max(
                Math.abs(cos * w - sin * h), Math.abs(cos * w + sin * h)));
        int height = (int) Math.ceil(Math.max(
                Math.abs(sin * w + cos * h), Math.abs(sin * w - cos * h)));

        int left = (int) Math.floor(cX - width / (2f * scale));
        int top = (int) Math.floor(cY - height / (2f * scale));
        int right = (int) Math.ceil(left + width / scale);
        int bottom = (int) Math.ceil(top + height / scale);

        // align the rectangle to tile boundary
        int size = mTileSize << level;
        left = Math.max(0, size * (left / size));
        top = Math.max(0, size * (top / size));
        right = Math.min(mImageWidth, right);
        bottom = Math.min(mImageHeight, bottom);

        out.set(left, top, right, bottom);
    }

    public void freeTextures() {
        mLayoutTiles = true;

        mTileDecoder.finishAndWait();
        synchronized (mQueueLock) {
            mUploadQueue.clean();
            mDecodeQueue.clean();
            Tile tile = mRecycledQueue.pop();
            while (tile != null) {
                tile.recycle();
                tile = mRecycledQueue.pop();
            }
        }

        int n = mActiveTiles.size();
        for (int i = 0; i < n; i++) {
            Tile texture = mActiveTiles.valueAt(i);
            texture.recycle();
        }
        mActiveTiles.clear();
        mTileRange.set(0, 0, 0, 0);

        while (sTilePool.acquire() != null) {}
    }

    public boolean draw(GLCanvas canvas) {
        layoutTiles();
        uploadTiles(canvas);

        mUploadQuota = UPLOAD_LIMIT;
        mRenderComplete = true;

        int level = mLevel;
        int rotation = mRotation;
        int flags = 0;
        if (rotation != 0) {
            flags |= GLCanvas.SAVE_FLAG_MATRIX;
        }

        if (flags != 0) {
            canvas.save(flags);
            if (rotation != 0) {
                int centerX = mViewWidth / 2, centerY = mViewHeight / 2;
                canvas.translate(centerX, centerY);
                canvas.rotate(rotation, 0, 0, 1);
                canvas.translate(-centerX, -centerY);
            }
        }
        try {
            if (level != mLevelCount) {
                int size = (mTileSize << level);
                float length = size * mScale;
                Rect r = mTileRange;

                for (int ty = r.top, i = 0; ty < r.bottom; ty += size, i++) {
                    float y = mOffsetY + i * length;
                    for (int tx = r.left, j = 0; tx < r.right; tx += size, j++) {
                        float x = mOffsetX + j * length;
                        drawTile(canvas, tx, ty, level, x, y, length);
                    }
                }
            } else if (mPreview != null) {
                mPreview.draw(canvas, mOffsetX, mOffsetY,
                        Math.round(mImageWidth * mScale),
                        Math.round(mImageHeight * mScale));
            }
        } finally {
            if (flags != 0) {
                canvas.restore();
            }
        }

        if (mRenderComplete) {
            if (!mBackgroundTileUploaded) {
                uploadBackgroundTiles(canvas);
            }
        } else {
            invalidate();
        }
        return mRenderComplete || mPreview != null;
    }

    private void uploadBackgroundTiles(GLCanvas canvas) {
        mBackgroundTileUploaded = true;
        int n = mActiveTiles.size();
        for (int i = 0; i < n; i++) {
            Tile tile = mActiveTiles.valueAt(i);
            if (!tile.isContentValid()) {
                queueForDecode(tile);
            }
        }
    }

   private void queueForDecode(Tile tile) {
       synchronized (mQueueLock) {
           if (tile.mTileState == STATE_ACTIVATED) {
               tile.mTileState = STATE_IN_QUEUE;
               if (mDecodeQueue.push(tile)) {
                   mQueueLock.notifyAll();
               }
           }
       }
    }

    private void decodeTile(Tile tile) {
        synchronized (mQueueLock) {
            if (tile.mTileState != STATE_IN_QUEUE) {
                return;
            }
            tile.mTileState = STATE_DECODING;
        }
        boolean decodeComplete = tile.decode();
        synchronized (mQueueLock) {
            if (tile.mTileState == STATE_RECYCLING) {
                tile.mTileState = STATE_RECYCLED;
                if (tile.mDecodedTile != null) {
                    sTilePool.release(tile.mDecodedTile);
                    tile.mDecodedTile = null;
                }
                mRecycledQueue.push(tile);
                return;
            }
            tile.mTileState = decodeComplete ? STATE_DECODED : STATE_DECODE_FAIL;
            if (!decodeComplete) {
                return;
            }
            mUploadQueue.push(tile);
        }
        invalidate();
    }

    private Tile obtainTile(int x, int y, int level) {
        synchronized (mQueueLock) {
            Tile tile = mRecycledQueue.pop();
            if (tile != null) {
                tile.mTileState = STATE_ACTIVATED;
                tile.update(x, y, level);
                return tile;
            }
            return new Tile(x, y, level);
        }
    }

    private void recycleTile(Tile tile) {
        synchronized (mQueueLock) {
            if (tile.mTileState == STATE_DECODING) {
                tile.mTileState = STATE_RECYCLING;
                return;
            }
            tile.mTileState = STATE_RECYCLED;
            if (tile.mDecodedTile != null) {
                sTilePool.release(tile.mDecodedTile);
                tile.mDecodedTile = null;
            }
            mRecycledQueue.push(tile);
        }
    }

    private void activateTile(int x, int y, int level) {
        long key = makeTileKey(x, y, level);
        Tile tile = mActiveTiles.get(key);
        if (tile != null) {
            if (tile.mTileState == STATE_IN_QUEUE) {
                tile.mTileState = STATE_ACTIVATED;
            }
            return;
        }
        tile = obtainTile(x, y, level);
        mActiveTiles.put(key, tile);
    }

    private Tile getTile(int x, int y, int level) {
        return mActiveTiles.get(makeTileKey(x, y, level));
    }

    private static long makeTileKey(int x, int y, int level) {
        long result = x;
        result = (result << 16) | y;
        result = (result << 16) | level;
        return result;
    }

    private void uploadTiles(GLCanvas canvas) {
        int quota = UPLOAD_LIMIT;
        Tile tile = null;
        while (quota > 0) {
            synchronized (mQueueLock) {
                tile = mUploadQueue.pop();
            }
            if (tile == null) {
                break;
            }
            if (!tile.isContentValid()) {
                if (tile.mTileState == STATE_DECODED) {
                    tile.updateContent(canvas);
                    --quota;
                } else {
                    Log.w(TAG, "Tile in upload queue has invalid state: " + tile.mTileState);
                }
            }
        }
        if (tile != null) {
            invalidate();
        }
    }

    // Draw the tile to a square at canvas that locates at (x, y) and
    // has a side length of length.
    private void drawTile(GLCanvas canvas,
            int tx, int ty, int level, float x, float y, float length) {
        RectF source = mSourceRect;
        RectF target = mTargetRect;
        target.set(x, y, x + length, y + length);
        source.set(0, 0, mTileSize, mTileSize);

        Tile tile = getTile(tx, ty, level);
        if (tile != null) {
            if (!tile.isContentValid()) {
                if (tile.mTileState == STATE_DECODED) {
                    if (mUploadQuota > 0) {
                        --mUploadQuota;
                        tile.updateContent(canvas);
                    } else {
                        mRenderComplete = false;
                    }
                } else if (tile.mTileState != STATE_DECODE_FAIL){
                    mRenderComplete = false;
                    queueForDecode(tile);
                }
            }
            if (drawTile(tile, canvas, source, target)) {
                return;
            }
        }
        if (mPreview != null) {
            int size = mTileSize << level;
            float scaleX = (float) mPreview.getWidth() / mImageWidth;
            float scaleY = (float) mPreview.getHeight() / mImageHeight;
            source.set(tx * scaleX, ty * scaleY, (tx + size) * scaleX,
                    (ty + size) * scaleY);
            canvas.drawTexture(mPreview, source, target);
        }
    }

    private boolean drawTile(
            Tile tile, GLCanvas canvas, RectF source, RectF target) {
        while (true) {
            if (tile.isContentValid()) {
                canvas.drawTexture(tile, source, target);
                return true;
            }

            // Parent can be divided to four quads and tile is one of the four.
            Tile parent = tile.getParentTile();
            if (parent == null) {
                return false;
            }
            if (tile.mX == parent.mX) {
                source.left /= 2f;
                source.right /= 2f;
            } else {
                source.left = (mTileSize + source.left) / 2f;
                source.right = (mTileSize + source.right) / 2f;
            }
            if (tile.mY == parent.mY) {
                source.top /= 2f;
                source.bottom /= 2f;
            } else {
                source.top = (mTileSize + source.top) / 2f;
                source.bottom = (mTileSize + source.bottom) / 2f;
            }
            tile = parent;
        }
    }

    private class Tile extends UploadedTexture {
        public int mX;
        public int mY;
        public int mTileLevel;
        public Tile mNext;
        public Bitmap mDecodedTile;
        public volatile int mTileState = STATE_ACTIVATED;

        public Tile(int x, int y, int level) {
            mX = x;
            mY = y;
            mTileLevel = level;
        }

        @Override
        protected void onFreeBitmap(Bitmap bitmap) {
            sTilePool.release(bitmap);
        }

        boolean decode() {
            // Get a tile from the original image. The tile is down-scaled
            // by (1 << mTilelevel) from a region in the original image.
            try {
                Bitmap reuse = sTilePool.acquire();
                if (reuse != null && reuse.getWidth() != mTileSize) {
                    reuse = null;
                }
                mDecodedTile = mModel.getTile(mTileLevel, mX, mY, reuse);
            } catch (Throwable t) {
                Log.w(TAG, "fail to decode tile", t);
            }
            return mDecodedTile != null;
        }

        @Override
        protected Bitmap onGetBitmap() {
            Utils.assertTrue(mTileState == STATE_DECODED);

            // We need to override the width and height, so that we won't
            // draw beyond the boundaries.
            int rightEdge = ((mImageWidth - mX) >> mTileLevel);
            int bottomEdge = ((mImageHeight - mY) >> mTileLevel);
            setSize(Math.min(mTileSize, rightEdge), Math.min(mTileSize, bottomEdge));

            Bitmap bitmap = mDecodedTile;
            mDecodedTile = null;
            mTileState = STATE_ACTIVATED;
            return bitmap;
        }

        // We override getTextureWidth() and getTextureHeight() here, so the
        // texture can be re-used for different tiles regardless of the actual
        // size of the tile (which may be small because it is a tile at the
        // boundary).
        @Override
        public int getTextureWidth() {
            return mTileSize;
        }

        @Override
        public int getTextureHeight() {
            return mTileSize;
        }

        public void update(int x, int y, int level) {
            mX = x;
            mY = y;
            mTileLevel = level;
            invalidateContent();
        }

        public Tile getParentTile() {
            if (mTileLevel + 1 == mLevelCount) {
                return null;
            }
            int size = mTileSize << (mTileLevel + 1);
            int x = size * (mX / size);
            int y = size * (mY / size);
            return getTile(x, y, mTileLevel + 1);
        }

        @Override
        public String toString() {
            return String.format("tile(%s, %s, %s / %s)",
                    mX / mTileSize, mY / mTileSize, mLevel, mLevelCount);
        }
    }

    private static class TileQueue {
        private Tile mHead;

        public Tile pop() {
            Tile tile = mHead;
            if (tile != null) {
                mHead = tile.mNext;
            }
            return tile;
        }

        public boolean push(Tile tile) {
            if (contains(tile)) {
                Log.w(TAG, "Attempting to add a tile already in the queue!");
                return false;
            }
            boolean wasEmpty = mHead == null;
            tile.mNext = mHead;
            mHead = tile;
            return wasEmpty;
        }

        private boolean contains(Tile tile) {
            Tile other = mHead;
            while (other != null) {
                if (other == tile) {
                    return true;
                }
                other = other.mNext;
            }
            return false;
        }

        public void clean() {
            mHead = null;
        }
    }

    private class TileDecoder extends Thread {

        public void finishAndWait() {
            interrupt();
            try {
                join();
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for TileDecoder thread to finish!");
            }
        }

        private Tile waitForTile() throws InterruptedException {
            synchronized (mQueueLock) {
                while (true) {
                    Tile tile = mDecodeQueue.pop();
                    if (tile != null) {
                        return tile;
                    }
                    mQueueLock.wait();
                }
            }
        }

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    Tile tile = waitForTile();
                    decodeTile(tile);
                }
            } catch (InterruptedException ex) {
                // We were finished
            }
        }

    }
}
