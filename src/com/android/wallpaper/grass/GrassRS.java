/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.wallpaper.grass;

import android.content.res.Resources;
import android.renderscript.Sampler;
import static android.renderscript.ProgramFragment.EnvMode.*;
import static android.renderscript.ProgramStore.DepthFunc.*;
import static android.renderscript.ProgramStore.BlendSrcFunc;
import static android.renderscript.ProgramStore.BlendDstFunc;
import android.renderscript.RenderScript;
import android.renderscript.ProgramFragment;
import android.renderscript.ProgramStore;
import android.renderscript.Allocation;
import android.renderscript.ProgramVertex;
import static android.renderscript.Element.*;
import static android.util.MathUtils.*;
import android.renderscript.ScriptC;
import android.renderscript.Type;
import android.renderscript.Dimension;
import static android.renderscript.Sampler.Value.*;
import com.android.launcher2.R;

import java.util.TimeZone;

class GrassRS {
    private static final int RSID_STATE = 0;
    private static final int RSID_STATE_FRAMECOUNT = 0;
    private static final int RSID_STATE_BLADES_COUNT = 1;
    private static final int RSID_STATE_WIDTH = 2;
    private static final int RSID_STATE_HEIGHT = 3;

    private static final int TEXTURES_COUNT = 5;

    private static final int RSID_BLADES = 1;
    private static final int BLADES_COUNT = 200;
    private static final int BLADE_STRUCT_FIELDS_COUNT = 12;
    private static final int BLADE_STRUCT_ANGLE = 0;
    private static final int BLADE_STRUCT_SIZE = 1;
    private static final int BLADE_STRUCT_XPOS = 2;
    private static final int BLADE_STRUCT_YPOS = 3;
    private static final int BLADE_STRUCT_OFFSET = 4;
    private static final int BLADE_STRUCT_SCALE = 5;
    private static final int BLADE_STRUCT_LENGTHX = 6;
    private static final int BLADE_STRUCT_LENGTHY = 7;
    private static final int BLADE_STRUCT_HARDNESS = 8;
    private static final int BLADE_STRUCT_H = 9;
    private static final int BLADE_STRUCT_S = 10;
    private static final int BLADE_STRUCT_B = 11;

    private Resources mResources;
    private RenderScript mRS;

    private final int mWidth;
    private final int mHeight;

    private ScriptC mScript;
    private Sampler mSampler;
    private Sampler mNightSampler;
    private ProgramFragment mPfBackground;
    private ProgramFragment mPfNight;
    private ProgramStore mPfsBackground;
    private ProgramVertex mPvBackground;
    private ProgramVertex.MatrixAllocation mPvOrthoAlloc;

    private Allocation[] mTextures;

    private Allocation mState;
    private Allocation mBlades;

    public GrassRS(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public void init(RenderScript rs, Resources res) {
        mRS = rs;
        mResources = res;
        initRS();
    }

    public void destroy() {
        mScript.destroy();
        mSampler.destroy();
        mPfBackground.destroy();
        mPfsBackground.destroy();
        mPvBackground.destroy();
        mPvOrthoAlloc.mAlloc.destroy();
        for (Allocation a : mTextures) {
            a.destroy();
        }
        mState.destroy();
        mBlades.destroy();
        mNightSampler.destroy();
        mPfNight.destroy();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        } finally {
            super.finalize();
        }
    }

    private void initRS() {
        createProgramVertex();
        createProgramFragmentStore();
        createProgramFragment();
        createScriptStructures();
        loadTextures();

        ScriptC.Builder sb = new ScriptC.Builder(mRS);
        sb.setScript(mResources, R.raw.grass);
        sb.setRoot(true);
        mScript = sb.create();
        mScript.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        mScript.setTimeZone(TimeZone.getDefault().getID());

        mScript.bindAllocation(mState, RSID_STATE);
        mScript.bindAllocation(mBlades, RSID_BLADES);

        mRS.contextBindRootScript(mScript);
    }

    private void createScriptStructures() {
        final int[] data = new int[4];
        mState = Allocation.createSized(mRS, USER_I32, data.length);
        data[RSID_STATE_FRAMECOUNT] = 0;
        data[RSID_STATE_BLADES_COUNT] = BLADES_COUNT;
        data[RSID_STATE_WIDTH] = mWidth;
        data[RSID_STATE_HEIGHT] = mHeight;
        mState.data(data);

        final float[] blades = new float[BLADES_COUNT * BLADE_STRUCT_FIELDS_COUNT];
        mBlades = Allocation.createSized(mRS, USER_FLOAT, blades.length);
        for (int i = 0; i < blades.length; i+= BLADE_STRUCT_FIELDS_COUNT) {
            createBlade(blades, i);
        }
        mBlades.data(blades);
    }

    private void createBlade(float[] blades, int index) {
        //noinspection PointlessArithmeticExpression
        blades[index + BLADE_STRUCT_ANGLE] = 0.0f;
        blades[index + BLADE_STRUCT_SIZE] = random(4.0f) + 4.0f;
        blades[index + BLADE_STRUCT_XPOS] = random(mWidth);
        blades[index + BLADE_STRUCT_YPOS] = mHeight;
        blades[index + BLADE_STRUCT_OFFSET] = random(0.2f) - 0.1f;
        blades[index + BLADE_STRUCT_SCALE] = random(0.6f) + 0.2f;
        blades[index + BLADE_STRUCT_LENGTHX] = random(4.5f) + 3.0f;
        blades[index + BLADE_STRUCT_LENGTHY] = random(5.5f) + 2.0f;
        blades[index + BLADE_STRUCT_HARDNESS] = random(1.0f) + 0.2f;
        blades[index + BLADE_STRUCT_H] = random(0.02f) + 0.2f;
        blades[index + BLADE_STRUCT_S] = random(0.22f) + 0.78f;
        blades[index + BLADE_STRUCT_B] = random(0.65f) + 0.35f;
    }

    private void loadTextures() {
        mTextures = new Allocation[TEXTURES_COUNT];

        final Allocation[] textures = mTextures;
        textures[0] = loadTexture(R.drawable.night, "TNight");
        textures[1] = loadTexture(R.drawable.sunrise, "TSunrise");
        textures[2] = loadTexture(R.drawable.sky, "TSky");
        textures[3] = loadTexture(R.drawable.sunset, "TSunset");
        textures[4] = generateTextureAlpha(4, 1, new int[] { 0x00FFFF00 }, "TAa");

        final int count = textures.length;
        for (int i = 0; i < count; i++) {
            final Allocation texture = textures[i];
            texture.uploadToTexture(0);
        }
    }

    private Allocation generateTextureAlpha(int width, int height, int[] data, String name) {
        final Type.Builder builder = new Type.Builder(mRS, A_8);
        builder.add(Dimension.X, width);
        builder.add(Dimension.Y, height);
        
        final Allocation allocation = Allocation.createTyped(mRS, builder.create());
        allocation.data(data);
        allocation.setName(name);
        return allocation;
    }

    private Allocation loadTexture(int id, String name) {
        final Allocation allocation = Allocation.createFromBitmapResource(mRS, mResources,
                id, RGB_565, false);
        allocation.setName(name);
        return allocation;
    }

    private void createProgramFragment() {
        Sampler.Builder bs = new Sampler.Builder(mRS);
        bs.setMin(LINEAR);
        bs.setMag(LINEAR);
        bs.setWrapS(CLAMP);
        bs.setWrapT(CLAMP);
        mSampler = bs.create();

        ProgramFragment.Builder b;
        b = new ProgramFragment.Builder(mRS, null, null);
        b.setTexEnable(true, 0);
        b.setTexEnvMode(REPLACE, 0);
        mPfBackground = b.create();
        mPfBackground.setName("PFBackground");
        mPfBackground.bindSampler(mSampler, 0);

        bs = new Sampler.Builder(mRS);
        bs.setMin(LINEAR);
        bs.setMag(LINEAR);
        bs.setWrapS(WRAP);
        bs.setWrapT(WRAP);
        mNightSampler = bs.create();

        b = new ProgramFragment.Builder(mRS, null, null);
        b.setTexEnable(true, 0);
        b.setTexEnvMode(REPLACE, 0);
        mPfNight = b.create();
        mPfNight.setName("PFNight");
        mPfNight.bindSampler(mNightSampler, 0);
    }

    private void createProgramFragmentStore() {
        ProgramStore.Builder b;
        b = new ProgramStore.Builder(mRS, null, null);

        b.setDepthFunc(ALWAYS);
        b.setBlendFunc(BlendSrcFunc.SRC_ALPHA, BlendDstFunc.ONE_MINUS_SRC_ALPHA);
        b.setDitherEnable(true);
        b.setDepthMask(false);
        mPfsBackground = b.create();
        mPfsBackground.setName("PFSBackground");
    }

    private void createProgramVertex() {
        mPvOrthoAlloc = new ProgramVertex.MatrixAllocation(mRS);
        mPvOrthoAlloc.setupOrthoWindow(mWidth, mHeight);

        ProgramVertex.Builder pvb = new ProgramVertex.Builder(mRS, null, null);
        pvb.setTextureMatrixEnable(true);
        mPvBackground = pvb.create();
        mPvBackground.bindAllocation(mPvOrthoAlloc);
        mPvBackground.setName("PVBackground");
    }
}
