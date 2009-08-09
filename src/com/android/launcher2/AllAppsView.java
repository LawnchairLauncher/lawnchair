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
    private RenderScript mRS;
    private RolloRS mRollo;

    private int mLastMotionX;

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

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);

        mRS = createRenderScript();
        mRollo = new RolloRS();
        mRollo.init(getResources(), w, h);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        // this method doesn't work when 'extends View' include 'extends ScrollView'.
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        int x = (int)ev.getX();
        int deltaX;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionX = x;
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_OUTSIDE:
                deltaX = x - mLastMotionX;
                mRollo.mState.scrollX += deltaX;
                Log.d(Launcher.LOG_TAG, "updated scrollX=" + mRollo.mState.scrollX);
                mRollo.mState.save();
                mLastMotionX = x;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastMotionX = -10000;
                break;
        }
        return true;
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

        // Allocations ======
        static final int ALLOC_PARAMS = 0;
        static final int ALLOC_STATE = 1;
        static final int ALLOC_SCRATCH = 2;
        static final int ALLOC_ICON_IDS = 3;
        static final int ALLOC_LABEL_IDS = 4;

        private int mWidth;
        private int mHeight;

        private Resources mRes;
        private Script mScript;
        private Sampler mSampler;
        private Sampler mSamplerText;
        private ProgramStore mPSBackground;
        private ProgramStore mPSText;
        private ProgramFragment mPFDebug;
        private ProgramFragment mPFImages;
        private ProgramFragment mPFText;
        private ProgramVertex mPV;
        private ProgramVertex.MatrixAllocation mPVAlloc;
        private ProgramVertex mPVOrtho;
        private ProgramVertex.MatrixAllocation mPVOrthoAlloc;

        private Allocation[] mIcons;
        private int[] mAllocIconIDBuf;
        private Allocation mAllocIconID;

        private Allocation[] mLabels;
        private int[] mAllocLabelIDBuf;
        private Allocation mAllocLabelID;

        private int[] mAllocScratchBuf;
        private Allocation mAllocScratch;

        Params mParams;
        State mState;

        class Params extends IntAllocation {
            Params(RenderScript rs) {
                super(rs);
            }
            @AllocationIndex(0) public int bubbleWidth;
            @AllocationIndex(1) public int bubbleHeight;
            @AllocationIndex(2) public int bubbleBitmapWidth;
            @AllocationIndex(3) public int bubbleBitmapHeight;
        }

        class State extends IntAllocation {
            State(RenderScript rs) {
                super(rs);
            }
            @AllocationIndex(0) public int iconCount;
            @AllocationIndex(1) public int scrollX;
        }

        public RolloRS() {
        }

        public void init(Resources res, int width, int height) {
            mRes = res;
            mWidth = width;
            mHeight = height;
            initGl();
            initData();
            initRs();
        }

        private void initGl() {
            Sampler.Builder sb = new Sampler.Builder(mRS);
            sb.setMin(Sampler.Value.LINEAR);//_MIP_LINEAR);
            sb.setMag(Sampler.Value.LINEAR);
            sb.setWrapS(Sampler.Value.CLAMP);
            sb.setWrapT(Sampler.Value.CLAMP);
            mSampler = sb.create();

            sb.setMin(Sampler.Value.NEAREST);
            sb.setMag(Sampler.Value.NEAREST);
            mSamplerText = sb.create();

            ProgramFragment.Builder dbg = new ProgramFragment.Builder(mRS, null, null);
            mPFDebug = dbg.create();
            mPFDebug.setName("PFDebug");

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
        }
        
        private void initData() {
            final int count = 29;
            mParams = new Params(mRS);
            mState = new State(mRS);

            mIcons = new Allocation[count];
            mAllocIconIDBuf = new int[count];
            mAllocIconID = Allocation.createSized(mRS,
                Element.USER_I32, mAllocIconIDBuf.length);

            mLabels = new Allocation[count];
            mAllocLabelIDBuf = new int[mLabels.length];
            mAllocLabelID = Allocation.createSized(mRS,
                Element.USER_I32, mLabels.length);

            Element ie8888 = Element.RGBA_8888;

            final Utilities.BubbleText bubble = new Utilities.BubbleText(getContext());

            mParams.bubbleWidth = bubble.getBubbleWidth();
            mParams.bubbleHeight = bubble.getMaxBubbleHeight();
            mParams.bubbleBitmapWidth = bubble.getBitmapWidth();
            mParams.bubbleBitmapHeight = bubble.getBitmapHeight();

            for (int i=0; i<count; i++) {
                mIcons[i] = Allocation.createFromBitmapResource(
                        mRS, mRes, R.raw.maps, ie8888, true);
                mLabels[i] = makeTextBitmap(bubble, i%9==0 ? "Google Maps" : "Maps");
            }

            for (int i=0; i<count; i++) {
                mIcons[i].uploadToTexture(0);
                mLabels[i].uploadToTexture(0);
                mAllocIconIDBuf[i] = mIcons[i].getID();
                mAllocLabelIDBuf[i] = mLabels[i].getID();
            }
            mAllocIconID.data(mAllocIconIDBuf);
            mAllocLabelID.data(mAllocLabelIDBuf);

            mState.iconCount = count;

            mParams.save();
            mState.save();
        }

        Allocation makeTextBitmap(Utilities.BubbleText bubble, String label) {
            Bitmap b = bubble.createTextBitmap(label);
            Allocation a = Allocation.createFromBitmap(mRS, b, Element.RGBA_8888, true);
            b.recycle();
            return a;
        }

        private void initRs() {
            ScriptC.Builder sb = new ScriptC.Builder(mRS);
            sb.setScript(mRes, R.raw.rollo);
            sb.setRoot(true);
            mScript = sb.create();
            mScript.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            mScript.bindAllocation(mParams.getAllocation(), ALLOC_PARAMS);
            mScript.bindAllocation(mState.getAllocation(), ALLOC_STATE);
            mScript.bindAllocation(mAllocIconID, ALLOC_ICON_IDS);
            mScript.bindAllocation(mAllocScratch, ALLOC_SCRATCH);
            mScript.bindAllocation(mAllocLabelID, ALLOC_LABEL_IDS);

            mRS.contextBindRootScript(mScript);
        }
    }

}



