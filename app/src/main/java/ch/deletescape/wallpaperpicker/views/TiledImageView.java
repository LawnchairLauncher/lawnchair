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

package ch.deletescape.wallpaperpicker.views;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.widget.FrameLayout;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ch.deletescape.wallpaperpicker.glrenderer.BasicTexture;
import ch.deletescape.wallpaperpicker.glrenderer.GLES20Canvas;
import ch.deletescape.wallpaperpicker.views.TiledImageRenderer.TileSource;

/**
 * Shows an image using {@link TiledImageRenderer} using either {@link GLSurfaceView}.
 */
public class TiledImageView extends FrameLayout {

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

    // -------------------------
    // Guarded by mLock
    // -------------------------
    protected final Object mLock = new Object();
    protected ImageRendererWrapper mRenderer;

    public TiledImageView(Context context) {
        this(context, null);
    }

    public TiledImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRenderer = new ImageRendererWrapper();
        mRenderer.image = new TiledImageRenderer(this);
        mGLSurfaceView = new GLSurfaceView(context);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mGLSurfaceView.setRenderer(new TileRenderer());
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        addView(mGLSurfaceView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // need to update inner view's visibility because it seems like we're causing it to draw
        // from {@link #dispatchDraw} or {@link #invalidate} even if we are invisible.
        mGLSurfaceView.setVisibility(visibility);
    }

    public void destroy() {
        mGLSurfaceView.queueEvent(mFreeTextures);
    }

    private Runnable mFreeTextures = new Runnable() {

        @Override
        public void run() {
            mRenderer.image.freeTextures();
        }
    };

    public void setTileSource(TileSource source, Runnable isReadyCallback) {
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

    public TileSource getTileSource() {
        return mRenderer.source;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
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
    public void invalidate() {
        invalOnVsync();
    }

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
}
