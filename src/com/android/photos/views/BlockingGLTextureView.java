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
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

/**
 * A TextureView that supports blocking rendering for synchronous drawing
 */
public class BlockingGLTextureView extends TextureView
        implements SurfaceTextureListener {

    private RenderThread mRenderThread;

    public BlockingGLTextureView(Context context) {
        super(context);
        setSurfaceTextureListener(this);
    }

    public void setRenderer(Renderer renderer) {
        if (mRenderThread != null) {
            throw new IllegalArgumentException("Renderer already set");
        }
        mRenderThread = new RenderThread(renderer);
    }

    public void render() {
        mRenderThread.render();
    }

    public void destroy() {
        if (mRenderThread != null) {
            mRenderThread.finish();
            mRenderThread = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
            int height) {
        mRenderThread.setSurface(surface);
        mRenderThread.setSize(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
            int height) {
        mRenderThread.setSize(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mRenderThread != null) {
            mRenderThread.setSurface(null);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        } catch (Throwable t) {
            // Ignore
        }
        super.finalize();
    }

    /**
     * An EGL helper class.
     */

    private static class EglHelper {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final int EGL_OPENGL_ES2_BIT = 4;

        EGL10 mEgl;
        EGLDisplay mEglDisplay;
        EGLSurface mEglSurface;
        EGLConfig mEglConfig;
        EGLContext mEglContext;

        private EGLConfig chooseEglConfig() {
            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = getConfig();
            if (!mEgl.eglChooseConfig(mEglDisplay, configSpec, configs, 1, configsCount)) {
                throw new IllegalArgumentException("eglChooseConfig failed " +
                        GLUtils.getEGLErrorString(mEgl.eglGetError()));
            } else if (configsCount[0] > 0) {
                return configs[0];
            }
            return null;
        }

        private static int[] getConfig() {
            return new int[] {
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
        }

        EGLContext createContext(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig) {
            int[] attribList = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            return egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attribList);
        }

        /**
         * Initialize EGL for a given configuration spec.
         */
        public void start() {
            /*
             * Get an EGL instance
             */
            mEgl = (EGL10) EGLContext.getEGL();

            /*
             * Get to the default display.
             */
            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("eglGetDisplay failed");
            }

            /*
             * We can now initialize EGL for that display
             */
            int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) {
                throw new RuntimeException("eglInitialize failed");
            }
            mEglConfig = chooseEglConfig();

            /*
            * Create an EGL context. We want to do this as rarely as we can, because an
            * EGL context is a somewhat heavy object.
            */
            mEglContext = createContext(mEgl, mEglDisplay, mEglConfig);

            if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) {
                mEglContext = null;
                throwEglException("createContext");
            }

            mEglSurface = null;
        }

        /**
         * Create an egl surface for the current SurfaceTexture surface. If a surface
         * already exists, destroy it before creating the new surface.
         *
         * @return true if the surface was created successfully.
         */
        public boolean createSurface(SurfaceTexture surface) {
            /*
             * Check preconditions.
             */
            if (mEgl == null) {
                throw new RuntimeException("egl not initialized");
            }
            if (mEglDisplay == null) {
                throw new RuntimeException("eglDisplay not initialized");
            }
            if (mEglConfig == null) {
                throw new RuntimeException("mEglConfig not initialized");
            }

            /*
             *  The window size has changed, so we need to create a new
             *  surface.
             */
            destroySurfaceImp();

            /*
             * Create an EGL surface we can render into.
             */
            if (surface != null) {
                mEglSurface = mEgl.eglCreateWindowSurface(mEglDisplay, mEglConfig, surface, null);
            } else {
                mEglSurface = null;
            }

            if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
                int error = mEgl.eglGetError();
                if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                    Log.e("EglHelper", "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
                }
                return false;
            }

            /*
             * Before we can issue GL commands, we need to make sure
             * the context is current and bound to a surface.
             */
            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
                /*
                 * Could not make the context current, probably because the underlying
                 * SurfaceView surface has been destroyed.
                 */
                logEglErrorAsWarning("EGLHelper", "eglMakeCurrent", mEgl.eglGetError());
                return false;
            }

            return true;
        }

        /**
         * Create a GL object for the current EGL context.
         */
        public GL10 createGL() {
            return (GL10) mEglContext.getGL();
        }

        /**
         * Display the current render surface.
         * @return the EGL error code from eglSwapBuffers.
         */
        public int swap() {
            if (!mEgl.eglSwapBuffers(mEglDisplay, mEglSurface)) {
                return mEgl.eglGetError();
            }
            return EGL10.EGL_SUCCESS;
        }

        public void destroySurface() {
            destroySurfaceImp();
        }

        private void destroySurfaceImp() {
            if (mEglSurface != null && mEglSurface != EGL10.EGL_NO_SURFACE) {
                mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_CONTEXT);
                mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
                mEglSurface = null;
            }
        }

        public void finish() {
            if (mEglContext != null) {
                mEgl.eglDestroyContext(mEglDisplay, mEglContext);
                mEglContext = null;
            }
            if (mEglDisplay != null) {
                mEgl.eglTerminate(mEglDisplay);
                mEglDisplay = null;
            }
        }

        private void throwEglException(String function) {
            throwEglException(function, mEgl.eglGetError());
        }

        public static void throwEglException(String function, int error) {
            String message = formatEglError(function, error);
            throw new RuntimeException(message);
        }

        public static void logEglErrorAsWarning(String tag, String function, int error) {
            Log.w(tag, formatEglError(function, error));
        }

        public static String formatEglError(String function, int error) {
            return function + " failed: " + error;
        }

    }

    private static class RenderThread extends Thread {
        private static final int INVALID = -1;
        private static final int RENDER = 1;
        private static final int CHANGE_SURFACE = 2;
        private static final int RESIZE_SURFACE = 3;
        private static final int FINISH = 4;

        private EglHelper mEglHelper = new EglHelper();

        private Object mLock = new Object();
        private int mExecMsgId = INVALID;
        private SurfaceTexture mSurface;
        private Renderer mRenderer;
        private int mWidth, mHeight;

        private boolean mFinished = false;
        private GL10 mGL;

        public RenderThread(Renderer renderer) {
            super("RenderThread");
            mRenderer = renderer;
            start();
        }

        private void checkRenderer() {
            if (mRenderer == null) {
                throw new IllegalArgumentException("Renderer is null!");
            }
        }

        private void checkSurface() {
            if (mSurface == null) {
                throw new IllegalArgumentException("surface is null!");
            }
        }

        public void setSurface(SurfaceTexture surface) {
            // If the surface is null we're being torn down, don't need a
            // renderer then
            if (surface != null) {
                checkRenderer();
            }
            mSurface = surface;
            exec(CHANGE_SURFACE);
        }

        public void setSize(int width, int height) {
            checkRenderer();
            checkSurface();
            mWidth = width;
            mHeight = height;
            exec(RESIZE_SURFACE);
        }

        public void render() {
            checkRenderer();
            if (mSurface != null) {
                exec(RENDER);
                mSurface.updateTexImage();
            }
        }

        public void finish() {
            mSurface = null;
            exec(FINISH);
            try {
                join();
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        private void exec(int msgid) {
            synchronized (mLock) {
                if (mExecMsgId != INVALID) {
                    throw new IllegalArgumentException(
                            "Message already set - multithreaded access?");
                }
                mExecMsgId = msgid;
                mLock.notify();
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }

        private void handleMessageLocked(int what) {
            switch (what) {
            case CHANGE_SURFACE:
                if (mEglHelper.createSurface(mSurface)) {
                    mGL = mEglHelper.createGL();
                    mRenderer.onSurfaceCreated(mGL, mEglHelper.mEglConfig);
                }
                break;
            case RESIZE_SURFACE:
                mRenderer.onSurfaceChanged(mGL, mWidth, mHeight);
                break;
            case RENDER:
                mRenderer.onDrawFrame(mGL);
                mEglHelper.swap();
                break;
            case FINISH:
                mEglHelper.destroySurface();
                mEglHelper.finish();
                mFinished = true;
                break;
            }
        }

        @Override
        public void run() {
            synchronized (mLock) {
                mEglHelper.start();
                while (!mFinished) {
                    while (mExecMsgId == INVALID) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }
                    handleMessageLocked(mExecMsgId);
                    mExecMsgId = INVALID;
                    mLock.notify();
                }
                mExecMsgId = FINISH;
            }
        }
    }
}
