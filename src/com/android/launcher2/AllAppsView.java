/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import java.io.Writer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.lang.Float;

import android.renderscript.RSSurfaceView;
import android.renderscript.RenderScript;

import android.renderscript.RenderScript;
import android.renderscript.ProgramVertex;
import android.renderscript.Element;
import android.renderscript.Allocation;
import android.renderscript.Script;
import android.renderscript.ScriptC;
import android.renderscript.ProgramFragment;
import android.renderscript.ProgramStore;
import android.renderscript.Sampler;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.graphics.PixelFormat;


public class AllAppsView extends RSSurfaceView {
    public AllAppsView(Context context) {
        super(context);
        setFocusable(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
    }

    public AllAppsView(Context context, AttributeSet attrs) {
        this(context);
    }

    public AllAppsView(Context context, AttributeSet attrs, int defStyle) {
        this(context);
    }

    private RenderScript mRS;
    private RolloRS mRender;

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);

        mRS = createRenderScript();
        mRender = new RolloRS();
        mRender.init(mRS, getResources(), w, h);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        // break point at here
        // this method doesn't work when 'extends View' include 'extends ScrollView'.
        return super.onKeyDown(keyCode, event);
    }

    boolean mControlMode = false;
    boolean mZoomMode = false;
    boolean mFlingMode = false;
    float mFlingX = 0;
    float mFlingY = 0;
    float mColumn = -1;
    float mOldColumn;
    float mZoom = 1;

    int mIconCount = 29;
    int mRows = 4;
    int mColumns = (mIconCount + mRows - 1) / mRows;

    float mMaxZoom = ((float)mColumns) / 3.f;


    void setColumn(boolean clamp)
    {
        //Log.e("rs", " col = " + Float.toString(mColumn));
        float c = mColumn;
        if(c > (mColumns -2)) {
            c = (mColumns -2);
        }
        if(c < 0) {
            c = 0;
        }
        mRender.setPosition(c);
        if(clamp) {
            mColumn = c;
        }
    }

    void computeSelection(float x, float y)
    {
        float col = mColumn + (x - 0.5f) * 4 + 1.25f;
        int iCol = (int)(col + 0.25f);

        float row = (y / 0.8f) * mRows;
        int iRow = (int)(row - 0.5f);

        mRender.setSelected(iCol * mRows + iRow);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        boolean ret = true;
        int act = ev.getAction();
        if (act == ev.ACTION_UP) {
            ret = false;
        }

        float nx = ev.getX() / getWidth();
        float ny = ev.getY() / getHeight();

        //Log.e("rs", "width=" + Float.toString(getWidth()));
        //Log.e("rs", "height=" + Float.toString(getHeight()));

        mRender.setTouch(ret);

        if((ny > 0.85f) || mControlMode) {
            mFlingMode = false;

            // Projector control
            if((nx > 0.2f) && (nx < 0.8f) || mControlMode) {
                if(act != ev.ACTION_UP) {
                    float zoom = mMaxZoom;
                    if(mControlMode) {
                        if(!mZoomMode) {
                            zoom = 1.f;
                        }
                        float dx = nx - mFlingX;

                        if((ny < 0.9) && mZoomMode) {
                            zoom = mMaxZoom - ((0.9f - ny) * 10.f);
                            if(zoom < 1) {
                                zoom = 1;
                                mZoomMode = false;
                            }
                            mOldColumn = mColumn;
                        }
                        mColumn += dx * 4;// * zoom;
                        if(zoom > 1.01f) {
                            mColumn += (mZoom - zoom) * (nx - 0.5f) * 4 * zoom;
                        }
                    } else {
                        mOldColumn = mColumn;
                        mColumn = ((float)mColumns) / 2;
                        mControlMode = true;
                        mZoomMode = true;
                    }
                    mZoom = zoom;
                    mFlingX = nx;
                    mRender.setZoom(zoom);
                    if(mZoom < 1.01f) {
                        computeSelection(nx, ny);
                    }
                } else {
                    mControlMode = false;
                    mColumn = mOldColumn;
                    mRender.setZoom(1.f);
                    mRender.setSelected(-1);
                }
            } else {
                // Do something with corners here....
            }
            setColumn(true);

        } else {
            // icon control
            if(act != ev.ACTION_UP) {
                if(mFlingMode) {
                    mColumn += (mFlingX - nx) * 4;
                    setColumn(true);
                }
                mFlingMode = true;
                mFlingX = nx;
                mFlingY = ny;
            } else {
                mFlingMode = false;
                mColumn = (float)(java.lang.Math.floor(mColumn * 0.25f + 0.3f) * 4.f) + 1.f;
                setColumn(true);
            }
        }


        return ret;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev)
    {
        float x = ev.getX();
        float y = ev.getY();
        //Float tx = new Float(x);
        //Float ty = new Float(y);
        //Log.e("rs", "tbe " + tx.toString() + ", " + ty.toString());


        return true;
    }

    public class RolloRS {
        //public static final int STATE_SELECTED_ID = 0;
        public static final int STATE_DONE = 1;
        //public static final int STATE_PRESSURE = 2;
        public static final int STATE_ZOOM = 3;
        //public static final int STATE_WARP = 4;
        public static final int STATE_ORIENTATION = 5;
        public static final int STATE_SELECTION = 6;
        public static final int STATE_FIRST_VISIBLE = 7;
        public static final int STATE_COUNT = 8;
        public static final int STATE_TOUCH = 9;


        public RolloRS() {
        }

        public void init(RenderScript rs, Resources res, int width, int height) {
            mRS = rs;
            mRes = res;
            mWidth = width;
            mHeight = height;
            initNamed();
            initRS();
        }

        public void setPosition(float column) {
            mAllocStateBuf[STATE_FIRST_VISIBLE] = (int)(column * (-20));
            mAllocState.data(mAllocStateBuf);
        }

        public void setTouch(boolean touch) {
            mAllocStateBuf[STATE_TOUCH] = touch ? 1 : 0;
            mAllocState.data(mAllocStateBuf);
        }

        public void setZoom(float z) {
            //Log.e("rs", "zoom " + Float.toString(z));

            mAllocStateBuf[STATE_ZOOM] = (int)(z * 1000.f);
            mAllocState.data(mAllocStateBuf);
        }

        public void setSelected(int index) {
            //Log.e("rs",  "setSelected " + Integer.toString(index));

            mAllocStateBuf[STATE_SELECTION] = index;
            mAllocStateBuf[STATE_DONE] = 1;
            mAllocState.data(mAllocStateBuf);
        }

        private int mWidth;
        private int mHeight;

        private Resources mRes;
        private RenderScript mRS;
        private Script mScript;
        private Sampler mSampler;
        private Sampler mSamplerText;
        private ProgramStore mPSBackground;
        private ProgramStore mPSText;
        private ProgramFragment mPFImages;
        private ProgramFragment mPFText;
        private ProgramVertex mPV;
        private ProgramVertex.MatrixAllocation mPVAlloc;
        private ProgramVertex mPVOrtho;
        private ProgramVertex.MatrixAllocation mPVOrthoAlloc;
        private Allocation[] mIcons;
        private Allocation[] mLabels;

        private int[] mAllocStateBuf;
        private Allocation mAllocState;

        private int[] mAllocIconIDBuf;
        private Allocation mAllocIconID;

        private int[] mAllocLabelIDBuf;
        private Allocation mAllocLabelID;

        private int[] mAllocScratchBuf;
        private Allocation mAllocScratch;

        private void initNamed() {
            Sampler.Builder sb = new Sampler.Builder(mRS);
            sb.setMin(Sampler.Value.LINEAR);//_MIP_LINEAR);
            sb.setMag(Sampler.Value.LINEAR);
            sb.setWrapS(Sampler.Value.CLAMP);
            sb.setWrapT(Sampler.Value.CLAMP);
            mSampler = sb.create();

            sb.setMin(Sampler.Value.NEAREST);
            sb.setMag(Sampler.Value.NEAREST);
            mSamplerText = sb.create();


            ProgramFragment.Builder bf = new ProgramFragment.Builder(mRS, null, null);
            bf.setTexEnable(true, 0);
            bf.setTexEnvMode(ProgramFragment.EnvMode.MODULATE, 0);
            mPFImages = bf.create();
            mPFImages.setName("PF");
            mPFImages.bindSampler(mSampler, 0);

            bf.setTexEnvMode(ProgramFragment.EnvMode.MODULATE, 0);
            mPFText = bf.create();
            mPFText.setName("PFText");
            mPFText.bindSampler(mSamplerText, 0);

            ProgramStore.Builder bs = new ProgramStore.Builder(mRS, null, null);
            bs.setDepthFunc(ProgramStore.DepthFunc.LESS);
            bs.setDitherEnable(false);
            bs.setDepthMask(true);
            bs.setBlendFunc(ProgramStore.BlendSrcFunc.SRC_ALPHA,
                            ProgramStore.BlendDstFunc.ONE_MINUS_SRC_ALPHA);
            mPSBackground = bs.create();
            mPSBackground.setName("PFS");

            bs.setDepthFunc(ProgramStore.DepthFunc.ALWAYS);
            bs.setDepthMask(false);
            bs.setBlendFunc(ProgramStore.BlendSrcFunc.SRC_ALPHA,
                            ProgramStore.BlendDstFunc.ONE_MINUS_SRC_ALPHA);
            mPSText = bs.create();
            mPSText.setName("PFSText");

            mPVAlloc = new ProgramVertex.MatrixAllocation(mRS);
            mPVAlloc.setupProjectionNormalized(mWidth, mHeight);

            ProgramVertex.Builder pvb = new ProgramVertex.Builder(mRS, null, null);
            mPV = pvb.create();
            mPV.setName("PV");
            mPV.bindAllocation(mPVAlloc);

            mPVOrthoAlloc = new ProgramVertex.MatrixAllocation(mRS);
            mPVOrthoAlloc.setupOrthoWindow(mWidth, mHeight);

            pvb.setTextureMatrixEnable(true);
            mPVOrtho = pvb.create();
            mPVOrtho.setName("PVOrtho");
            mPVOrtho.bindAllocation(mPVOrthoAlloc);

            mRS.contextBindProgramVertex(mPV);

            mAllocScratchBuf = new int[32];
            mAllocScratch = Allocation.createSized(mRS,
                Element.USER_I32, mAllocScratchBuf.length);
            mAllocScratch.data(mAllocScratchBuf);

            Log.e("rs", "Done loading named");



            {
                mIcons = new Allocation[29];
                mAllocIconIDBuf = new int[mIcons.length];
                mAllocIconID = Allocation.createSized(mRS,
                    Element.USER_I32, mAllocIconIDBuf.length);

                mLabels = new Allocation[29];
                mAllocLabelIDBuf = new int[mLabels.length];
                mAllocLabelID = Allocation.createSized(mRS,
                    Element.USER_I32, mLabels.length);

                Element ie8888 = Element.RGBA_8888;

                for (int i=0; i<mIcons.length; i++) {
                    mIcons[i] = Allocation.createFromBitmapResource(
                            mRS, mRes, R.raw.maps, ie8888, true);
                    mLabels[i] = makeTextBitmap("Maps");
                }

                for(int ct=0; ct < mIcons.length; ct++) {
                    mIcons[ct].uploadToTexture(0);
                    mLabels[ct].uploadToTexture(0);
                    mAllocIconIDBuf[ct] = mIcons[ct].getID();
                    mAllocLabelIDBuf[ct] = mLabels[ct].getID();
                }
                mAllocIconID.data(mAllocIconIDBuf);
                mAllocLabelID.data(mAllocLabelIDBuf);
            }

        }

        Allocation makeTextBitmap(String t) {
            Bitmap b = Bitmap.createBitmap(128, 32, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(20);
            p.setColor(0xffffffff);
            c.drawText(t, 2, 26, p);
            return Allocation.createFromBitmap(mRS, b, Element.RGBA_8888, true);
        }


        private void initRS() {
            ScriptC.Builder sb = new ScriptC.Builder(mRS);
            sb.setScript(mRes, R.raw.rollo);
            //sb.setScript(mRes, R.raw.rollo2);
            sb.setRoot(true);
            mScript = sb.create();
            mScript.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            mAllocStateBuf = new int[] {0, 0, 0, 8, 0, 0, -1, 0, mAllocIconIDBuf.length, 0, 0};
            mAllocState = Allocation.createSized(mRS,
                Element.USER_I32, mAllocStateBuf.length);
            mScript.bindAllocation(mAllocState, 0);
            mScript.bindAllocation(mAllocIconID, 1);
            mScript.bindAllocation(mAllocScratch, 2);
            mScript.bindAllocation(mAllocLabelID, 3);
            setPosition(0);
            setZoom(1);

            //RenderScript.File f = mRS.fileOpen("/sdcard/test.a3d");

            mRS.contextBindRootScript(mScript);
        }
    }

}



