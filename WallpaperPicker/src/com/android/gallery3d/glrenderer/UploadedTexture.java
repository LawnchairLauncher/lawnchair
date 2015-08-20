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

package com.android.gallery3d.glrenderer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.opengl.GLUtils;
import android.util.Pair;

import com.android.gallery3d.common.Utils;

import java.util.HashMap;

// UploadedTextures use a Bitmap for the content of the texture.
//
// Subclasses should implement onGetBitmap() to provide the Bitmap and
// implement onFreeBitmap(mBitmap) which will be called when the Bitmap
// is not needed anymore.
//
// isContentValid() is meaningful only when the isLoaded() returns true.
// It means whether the content needs to be updated.
//
// The user of this class should call recycle() when the texture is not
// needed anymore.
//
// By default an UploadedTexture is opaque (so it can be drawn faster without
// blending). The user or subclass can override it using setOpaque().
public abstract class UploadedTexture extends BasicTexture {

    // To prevent keeping allocation the borders, we store those used borders here.
    // Since the length will be power of two, it won't use too much memory.
    private static HashMap<BorderKey, Bitmap> sBorderLines = new HashMap<BorderKey, Bitmap>();

    private static class BorderKey extends Pair<Config, Integer> {
        public BorderKey(Config config, boolean vertical, int length) {
            super(config, vertical ? length : -length);
        }
    }

    private boolean mContentValid = true;
    protected Bitmap mBitmap;

    protected UploadedTexture() {
        super(null, 0, STATE_UNLOADED);
    }

    private static Bitmap getBorderLine(boolean vertical, Config config, int length) {
        BorderKey key = new BorderKey(config, vertical, length);
        Bitmap bitmap = sBorderLines.get(key);
        if (bitmap == null) {
            bitmap = vertical
                    ? Bitmap.createBitmap(1, length, config)
                    : Bitmap.createBitmap(length, 1, config);
            sBorderLines.put(key, bitmap);
        }
        return bitmap;
    }

    private Bitmap getBitmap() {
        if (mBitmap == null) {
            mBitmap = onGetBitmap();
            int w = mBitmap.getWidth();
            int h = mBitmap.getHeight();
            if (mWidth == UNSPECIFIED) {
                setSize(w, h);
            }
        }
        return mBitmap;
    }

    private void freeBitmap() {
        Utils.assertTrue(mBitmap != null);
        onFreeBitmap(mBitmap);
        mBitmap = null;
    }

    @Override
    public int getWidth() {
        if (mWidth == UNSPECIFIED) getBitmap();
        return mWidth;
    }

    @Override
    public int getHeight() {
        if (mWidth == UNSPECIFIED) getBitmap();
        return mHeight;
    }

    protected abstract Bitmap onGetBitmap();

    protected abstract void onFreeBitmap(Bitmap bitmap);

    protected void invalidateContent() {
        if (mBitmap != null) freeBitmap();
        mContentValid = false;
        mWidth = UNSPECIFIED;
        mHeight = UNSPECIFIED;
    }

    /**
     * Whether the content on GPU is valid.
     */
    public boolean isContentValid() {
        return isLoaded() && mContentValid;
    }

    /**
     * Updates the content on GPU's memory.
     * @param canvas
     */
    public void updateContent(GLCanvas canvas) {
        if (!isLoaded()) {
            uploadToCanvas(canvas);
        } else if (!mContentValid) {
            Bitmap bitmap = getBitmap();
            int format = GLUtils.getInternalFormat(bitmap);
            int type = GLUtils.getType(bitmap);
            canvas.texSubImage2D(this, 0, 0, bitmap, format, type);
            freeBitmap();
            mContentValid = true;
        }
    }

    private void uploadToCanvas(GLCanvas canvas) {
        Bitmap bitmap = getBitmap();
        if (bitmap != null) {
            try {
                int bWidth = bitmap.getWidth();
                int bHeight = bitmap.getHeight();
                int texWidth = getTextureWidth();
                int texHeight = getTextureHeight();

                Utils.assertTrue(bWidth <= texWidth && bHeight <= texHeight);

                // Upload the bitmap to a new texture.
                mId = canvas.getGLId().generateTexture();
                canvas.setTextureParameters(this);

                if (bWidth == texWidth && bHeight == texHeight) {
                    canvas.initializeTexture(this, bitmap);
                } else {
                    int format = GLUtils.getInternalFormat(bitmap);
                    int type = GLUtils.getType(bitmap);
                    Config config = bitmap.getConfig();

                    canvas.initializeTextureSize(this, format, type);
                    canvas.texSubImage2D(this, 0, 0, bitmap, format, type);

                    // Right border
                    if (bWidth < texWidth) {
                        Bitmap line = getBorderLine(true, config, texHeight);
                        canvas.texSubImage2D(this, bWidth, 0, line, format, type);
                    }

                    // Bottom border
                    if (bHeight < texHeight) {
                        Bitmap line = getBorderLine(false, config, texWidth);
                        canvas.texSubImage2D(this, 0, bHeight, line, format, type);
                    }
                }
            } finally {
                freeBitmap();
            }
            // Update texture state.
            setAssociatedCanvas(canvas);
            mState = STATE_LOADED;
            mContentValid = true;
        } else {
            mState = STATE_ERROR;
            throw new RuntimeException("Texture load fail, no bitmap");
        }
    }

    @Override
    protected boolean onBind(GLCanvas canvas) {
        updateContent(canvas);
        return isContentValid();
    }

    @Override
    public void recycle() {
        super.recycle();
        if (mBitmap != null) freeBitmap();
    }
}
