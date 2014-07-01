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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.View;
import android.widget.FrameLayout;

import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.glrenderer.GLES20Canvas;
import com.android.photos.views.TiledImageRenderer.TileSource;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Shows an image using {@link TiledImageRenderer} using either {@link GLSurfaceView}
 * or {@link BlockingGLTextureView}.
 */
public class TiledImageView extends FrameLayout {

    private static final boolean USE_TEXTURE_VIEW = false;
    private static final boolean IS_SUPPORTED =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    private static final boolean USE_CHOREOGRAPHER =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;

    private BlockingGLTextureView mTextureView;
    private GLSurfaceView mGLSurfaceView;
    private boolean mInvalPending = false;
    private FrameCallback mFrameCallback;

    protected static class ImageRendererWrapper {
        // Guarded by locks
        public float scale;
        public int centerX, centerY;
        public int rotation;
        public TileSource source;
        Runnable isReadyCallback;

        // GL thread only
        TiledImageRenderer image;
    }

    private float[] mValues = new float[9];

    // -------------------------
    // Guarded by mLock
    // -------------------------
    protected Object mLock = new Object();
    protected ImageRendererWrapper mRenderer;

    public static boolean isTilingSupported() {
        return IS_SUPPORTED;
    }

    public TiledImageView(Context context) {
        this(context, null);
    }

    public TiledImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!IS_SUPPORTED) {
            return;
        }

        mRenderer = new ImageRendererWrapper();
        mRenderer.image = new TiledImageRenderer(this);
        View view;
        if (USE_TEXTURE_VIEW) {
            mTextureView = new BlockingGLTextureView(context);
            mTextureView.setRenderer(new TileRenderer());
            view = mTextureView;
        } else {
            mGLSurfaceView = new GLSurfaceView(context);
            mGLSurfaceView.setEGLContextClientVersion(2);
            mGLSurfaceView.setRenderer(new TileRenderer());
            mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            view = mGLSurfaceView;
        }
        addView(view, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        //setTileSource(new ColoredTiles());
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // need to update inner view's visibility because it seems like we're causing it to draw
        // from {@link #dispatchDraw} or {@link #invalidate} even if we are invisible.
        if (USE_TEXTURE_VIEW) {
            mTextureView.setVisibility(visibility);
        } else {
            mGLSurfaceView.setVisibility(visibility);
        }
    }

    public void destroy() {
        if (!IS_SUPPORTED) {
            return;
        }
        if (USE_TEXTURE_VIEW) {
            mTextureView.destroy();
        } else {
            mGLSurfaceView.queueEvent(mFreeTextures);
        }
    }

    private Runnable mFreeTextures = new Runnable() {

        @Override
        public void run() {
            mRenderer.image.freeTextures();
        }
    };

    public void onPause() {
        if (!IS_SUPPORTED) {
            return;
        }
        if (!USE_TEXTURE_VIEW) {
            mGLSurfaceView.onPause();
        }
    }

    public void onResume() {
        if (!IS_SUPPORTED) {
            return;
        }
        if (!USE_TEXTURE_VIEW) {
            mGLSurfaceView.onResume();
        }
    }

    public void setTileSource(TileSource source, Runnable isReadyCallback) {
        if (!IS_SUPPORTED) {
            return;
        }
        synchronized (mLock) {
            mRenderer.source = source;
            mRenderer.isReadyCallback = isReadyCallback;
            mRenderer.centerX = source != null ? source.getImageWidth() / 2 : 0;
            mRenderer.centerY = source != null ? source.getImageHeight() / 2 : 0;
            mRenderer.rotation = source != null ? source.getRotation() : 0;
            mRenderer.scale = 0;
            updateScaleIfNecessaryLocked(mRenderer);
        }
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!IS_SUPPORTED) {
            return;
        }
        synchronized (mLock) {
            updateScaleIfNecessaryLocked(mRenderer);
        }
    }

    private void updateScaleIfNecessaryLocked(ImageRendererWrapper renderer) {
        if (renderer == null || renderer.source == null
                || renderer.scale > 0 || getWidth() == 0) {
            return;
        }
        renderer.scale = Math.min(
                (float) getWidth() / (float) renderer.source.getImageWidth(),
                (float) getHeight() / (float) renderer.source.getImageHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!IS_SUPPORTED) {
            return;
        }
        if (USE_TEXTURE_VIEW) {
            mTextureView.render();
        }
        super.dispatchDraw(canvas);
    }

    @SuppressLint("NewApi")
    @Override
    public void setTranslationX(float translationX) {
        if (!IS_SUPPORTED) {
            return;
        }
        super.setTranslationX(translationX);
    }

    @Override
    public void invalidate() {
        if (!IS_SUPPORTED) {
            return;
        }
        if (USE_TEXTURE_VIEW) {
            super.invalidate();
            mTextureView.invalidate();
        } else {
            if (USE_CHOREOGRAPHER) {
                invalOnVsync();
            } else {
                mGLSurfaceView.requestRender();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void invalOnVsync() {
        if (!mInvalPending) {
            mInvalPending = true;
            if (mFrameCallback == null) {
                mFrameCallback = new FrameCallback() {
                    @Override
                    public void doFrame(long frameTimeNanos) {
                        mInvalPending = false;
                        mGLSurfaceView.requestRender();
                    }
                };
            }
            Choreographer.getInstance().postFrameCallback(mFrameCallback);
        }
    }

    private RectF mTempRectF = new RectF();
    public void positionFromMatrix(Matrix matrix) {
        if (!IS_SUPPORTED) {
            return;
        }
        if (mRenderer.source != null) {
            final int rotation = mRenderer.source.getRotation();
            final boolean swap = !(rotation % 180 == 0);
            final int width = swap ? mRenderer.source.getImageHeight()
                    : mRenderer.source.getImageWidth();
            final int height = swap ? mRenderer.source.getImageWidth()
                    : mRenderer.source.getImageHeight();
            mTempRectF.set(0, 0, width, height);
            matrix.mapRect(mTempRectF);
            matrix.getValues(mValues);
            int cx = width / 2;
            int cy = height / 2;
            float scale = mValues[Matrix.MSCALE_X];
            int xoffset = Math.round((getWidth() - mTempRectF.width()) / 2 / scale);
            int yoffset = Math.round((getHeight() - mTempRectF.height()) / 2 / scale);
            if (rotation == 90 || rotation == 180) {
                cx += (mTempRectF.left / scale) - xoffset;
            } else {
                cx -= (mTempRectF.left / scale) - xoffset;
            }
            if (rotation == 180 || rotation == 270) {
                cy += (mTempRectF.top / scale) - yoffset;
            } else {
                cy -= (mTempRectF.top / scale) - yoffset;
            }
            mRenderer.scale = scale;
            mRenderer.centerX = swap ? cy : cx;
            mRenderer.centerY = swap ? cx : cy;
            invalidate();
        }
    }

    private class TileRenderer implements Renderer {

        private GLES20Canvas mCanvas;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mCanvas = new GLES20Canvas();
            BasicTexture.invalidateAllTextures();
            mRenderer.image.setModel(mRenderer.source, mRenderer.rotation);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            mCanvas.setSize(width, height);
            mRenderer.image.setViewSize(width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            mCanvas.clearBuffer();
            Runnable readyCallback;
            synchronized (mLock) {
                readyCallback = mRenderer.isReadyCallback;
                mRenderer.image.setModel(mRenderer.source, mRenderer.rotation);
                mRenderer.image.setPosition(mRenderer.centerX, mRenderer.centerY,
                        mRenderer.scale);
            }
            boolean complete = mRenderer.image.draw(mCanvas);
            if (complete && readyCallback != null) {
                synchronized (mLock) {
                    // Make sure we don't trample on a newly set callback/source
                    // if it changed while we were rendering
                    if (mRenderer.isReadyCallback == readyCallback) {
                        mRenderer.isReadyCallback = null;
                    }
                }
                if (readyCallback != null) {
                    post(readyCallback);
                }
            }
        }

    }

    @SuppressWarnings("unused")
    private static class ColoredTiles implements TileSource {
        private static final int[] COLORS = new int[] {
            Color.RED,
            Color.BLUE,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.MAGENTA,
            Color.WHITE,
        };

        private Paint mPaint = new Paint();
        private Canvas mCanvas = new Canvas();

        @Override
        public int getTileSize() {
            return 256;
        }

        @Override
        public int getImageWidth() {
            return 16384;
        }

        @Override
        public int getImageHeight() {
            return 8192;
        }

        @Override
        public int getRotation() {
            return 0;
        }

        @Override
        public Bitmap getTile(int level, int x, int y, Bitmap bitmap) {
            int tileSize = getTileSize();
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(tileSize, tileSize,
                        Bitmap.Config.ARGB_8888);
            }
            mCanvas.setBitmap(bitmap);
            mCanvas.drawColor(COLORS[level]);
            mPaint.setColor(Color.BLACK);
            mPaint.setTextSize(20);
            mPaint.setTextAlign(Align.CENTER);
            mCanvas.drawText(x + "x" + y, 128, 128, mPaint);
            tileSize <<= level;
            x /= tileSize;
            y /= tileSize;
            mCanvas.drawText(x + "x" + y + " @ " + level, 128, 30, mPaint);
            mCanvas.setBitmap(null);
            return bitmap;
        }

        @Override
        public BasicTexture getPreview() {
            return null;
        }
    }
}
