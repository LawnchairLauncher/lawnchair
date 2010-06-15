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

package com.android.launcher2;

import android.renderscript.*;
import android.content.res.Resources;
import android.util.Log;

public class ScriptC_Allapps extends ScriptC {
    // Constructor
    public  ScriptC_Allapps(RenderScript rs, Resources resources, int id, boolean isRoot) {
        super(rs, resources, id, isRoot);
    }

    private final static int mExportVarIdx_COLUMNS_PER_PAGE_PORTRAIT = 0;
    private int mExportVar_COLUMNS_PER_PAGE_PORTRAIT;
    public void set_COLUMNS_PER_PAGE_PORTRAIT(int v) {
        mExportVar_COLUMNS_PER_PAGE_PORTRAIT = v;
        setVar(mExportVarIdx_COLUMNS_PER_PAGE_PORTRAIT, v);
    }

    public int get_COLUMNS_PER_PAGE_PORTRAIT() {
        return mExportVar_COLUMNS_PER_PAGE_PORTRAIT;
    }

    private final static int mExportVarIdx_ROWS_PER_PAGE_PORTRAIT = 1;
    private int mExportVar_ROWS_PER_PAGE_PORTRAIT;
    public void set_ROWS_PER_PAGE_PORTRAIT(int v) {
        mExportVar_ROWS_PER_PAGE_PORTRAIT = v;
        setVar(mExportVarIdx_ROWS_PER_PAGE_PORTRAIT, v);
    }

    public int get_ROWS_PER_PAGE_PORTRAIT() {
        return mExportVar_ROWS_PER_PAGE_PORTRAIT;
    }

    private final static int mExportVarIdx_COLUMNS_PER_PAGE_LANDSCAPE = 2;
    private int mExportVar_COLUMNS_PER_PAGE_LANDSCAPE;
    public void set_COLUMNS_PER_PAGE_LANDSCAPE(int v) {
        mExportVar_COLUMNS_PER_PAGE_LANDSCAPE = v;
        setVar(mExportVarIdx_COLUMNS_PER_PAGE_LANDSCAPE, v);
    }

    public int get_COLUMNS_PER_PAGE_LANDSCAPE() {
        return mExportVar_COLUMNS_PER_PAGE_LANDSCAPE;
    }

    private final static int mExportVarIdx_ROWS_PER_PAGE_LANDSCAPE = 3;
    private int mExportVar_ROWS_PER_PAGE_LANDSCAPE;
    public void set_ROWS_PER_PAGE_LANDSCAPE(int v) {
        mExportVar_ROWS_PER_PAGE_LANDSCAPE = v;
        setVar(mExportVarIdx_ROWS_PER_PAGE_LANDSCAPE, v);
    }

    public int get_ROWS_PER_PAGE_LANDSCAPE() {
        return mExportVar_ROWS_PER_PAGE_LANDSCAPE;
    }

    private final static int mExportVarIdx_gNewPositionX = 4;
    private float mExportVar_gNewPositionX;
    public void set_gNewPositionX(float v) {
        mExportVar_gNewPositionX = v;
        setVar(mExportVarIdx_gNewPositionX, v);
    }

    public float get_gNewPositionX() {
        return mExportVar_gNewPositionX;
    }

    private final static int mExportVarIdx_gNewTouchDown = 5;
    private int mExportVar_gNewTouchDown;
    public void set_gNewTouchDown(int v) {
        mExportVar_gNewTouchDown = v;
        setVar(mExportVarIdx_gNewTouchDown, v);
    }

    public int get_gNewTouchDown() {
        return mExportVar_gNewTouchDown;
    }

    private final static int mExportVarIdx_gFlingVelocity = 6;
    private float mExportVar_gFlingVelocity;
    public void set_gFlingVelocity(float v) {
        mExportVar_gFlingVelocity = v;
        setVar(mExportVarIdx_gFlingVelocity, v);
    }

    public float get_gFlingVelocity() {
        return mExportVar_gFlingVelocity;
    }

    private final static int mExportVarIdx_gIconCount = 7;
    private int mExportVar_gIconCount;
    public void set_gIconCount(int v) {
        mExportVar_gIconCount = v;
        setVar(mExportVarIdx_gIconCount, v);
    }

    public int get_gIconCount() {
        return mExportVar_gIconCount;
    }

    private final static int mExportVarIdx_gSelectedIconIndex = 8;
    private int mExportVar_gSelectedIconIndex;
    public void set_gSelectedIconIndex(int v) {
        mExportVar_gSelectedIconIndex = v;
        setVar(mExportVarIdx_gSelectedIconIndex, v);
    }

    public int get_gSelectedIconIndex() {
        return mExportVar_gSelectedIconIndex;
    }

    private final static int mExportVarIdx_gSelectedIconTexture = 9;
    private Allocation mExportVar_gSelectedIconTexture;
    public void set_gSelectedIconTexture(Allocation v) {
        mExportVar_gSelectedIconTexture = v;
        setVar(mExportVarIdx_gSelectedIconTexture, (v == null) ? 0 : v.getID());
    }

    public Allocation get_gSelectedIconTexture() {
        return mExportVar_gSelectedIconTexture;
    }

    private final static int mExportVarIdx_gZoomTarget = 10;
    private float mExportVar_gZoomTarget;
    public void set_gZoomTarget(float v) {
        mExportVar_gZoomTarget = v;
        setVar(mExportVarIdx_gZoomTarget, v);
    }

    public float get_gZoomTarget() {
        return mExportVar_gZoomTarget;
    }

    private final static int mExportVarIdx_gHomeButton = 11;
    private Allocation mExportVar_gHomeButton;
    public void set_gHomeButton(Allocation v) {
        mExportVar_gHomeButton = v;
        setVar(mExportVarIdx_gHomeButton, (v == null) ? 0 : v.getID());
    }

    public Allocation get_gHomeButton() {
        return mExportVar_gHomeButton;
    }

    private final static int mExportVarIdx_gTargetPos = 12;
    private float mExportVar_gTargetPos;
    public void set_gTargetPos(float v) {
        mExportVar_gTargetPos = v;
        setVar(mExportVarIdx_gTargetPos, v);
    }

    public float get_gTargetPos() {
        return mExportVar_gTargetPos;
    }

    private final static int mExportVarIdx_gPFTexNearest = 13;
    private ProgramFragment mExportVar_gPFTexNearest;
    public void set_gPFTexNearest(ProgramFragment v) {
        mExportVar_gPFTexNearest = v;
        setVar(mExportVarIdx_gPFTexNearest, (v == null) ? 0 : v.getID());
    }

    public ProgramFragment get_gPFTexNearest() {
        return mExportVar_gPFTexNearest;
    }

    private final static int mExportVarIdx_gPFTexMip = 14;
    private ProgramFragment mExportVar_gPFTexMip;
    public void set_gPFTexMip(ProgramFragment v) {
        mExportVar_gPFTexMip = v;
        setVar(mExportVarIdx_gPFTexMip, (v == null) ? 0 : v.getID());
    }

    public ProgramFragment get_gPFTexMip() {
        return mExportVar_gPFTexMip;
    }

    private final static int mExportVarIdx_gPFTexMipAlpha = 15;
    private ProgramFragment mExportVar_gPFTexMipAlpha;
    public void set_gPFTexMipAlpha(ProgramFragment v) {
        mExportVar_gPFTexMipAlpha = v;
        setVar(mExportVarIdx_gPFTexMipAlpha, (v == null) ? 0 : v.getID());
    }

    public ProgramFragment get_gPFTexMipAlpha() {
        return mExportVar_gPFTexMipAlpha;
    }

    private final static int mExportVarIdx_gPVCurve = 16;
    private ProgramVertex mExportVar_gPVCurve;
    public void set_gPVCurve(ProgramVertex v) {
        mExportVar_gPVCurve = v;
        setVar(mExportVarIdx_gPVCurve, (v == null) ? 0 : v.getID());
    }

    public ProgramVertex get_gPVCurve() {
        return mExportVar_gPVCurve;
    }

    private final static int mExportVarIdx_gPS = 17;
    private ProgramStore mExportVar_gPS;
    public void set_gPS(ProgramStore v) {
        mExportVar_gPS = v;
        setVar(mExportVarIdx_gPS, (v == null) ? 0 : v.getID());
    }

    public ProgramStore get_gPS() {
        return mExportVar_gPS;
    }

    private final static int mExportVarIdx_gSMCell = 18;
    private SimpleMesh mExportVar_gSMCell;
    public void set_gSMCell(SimpleMesh v) {
        mExportVar_gSMCell = v;
        setVar(mExportVarIdx_gSMCell, (v == null) ? 0 : v.getID());
    }

    public SimpleMesh get_gSMCell() {
        return mExportVar_gSMCell;
    }

    private final static int mExportVarIdx_gIconIDs = 19;
    private Allocation mExportVar_gIconIDs;
    public void bind_gIconIDs(Allocation v) {
        mExportVar_gIconIDs = v;
        if(v == null) bindAllocation(null, mExportVarIdx_gIconIDs);
        else bindAllocation(v, mExportVarIdx_gIconIDs);
    }

    public Allocation get_gIconIDs() {
        return mExportVar_gIconIDs;
    }

    private final static int mExportVarIdx_gLabelIDs = 20;
    private Allocation mExportVar_gLabelIDs;
    public void bind_gLabelIDs(Allocation v) {
        mExportVar_gLabelIDs = v;
        if(v == null) bindAllocation(null, mExportVarIdx_gLabelIDs);
        else bindAllocation(v, mExportVarIdx_gLabelIDs);
    }

    public Allocation get_gLabelIDs() {
        return mExportVar_gLabelIDs;
    }

    private final static int mExportVarIdx_vpConstants = 21;
    private ScriptField_VpConsts mExportVar_vpConstants;
    public void bind_vpConstants(ScriptField_VpConsts v) {
        mExportVar_vpConstants = v;
        if(v == null) bindAllocation(null, mExportVarIdx_vpConstants);
        else bindAllocation(v.getAllocation(), mExportVarIdx_vpConstants);
    }

    public ScriptField_VpConsts get_vpConstants() {
        return mExportVar_vpConstants;
    }

    private final static int mExportFuncIdx_resetHWWar = 0;
    public void invoke_resetHWWar() {
        invoke(mExportFuncIdx_resetHWWar);
    }

    private final static int mExportFuncIdx_move = 1;
    public void invoke_move() {
        invoke(mExportFuncIdx_move);
    }

    private final static int mExportFuncIdx_moveTo = 2;
    public void invoke_moveTo() {
        invoke(mExportFuncIdx_moveTo);
    }

    private final static int mExportFuncIdx_setZoom = 3;
    public void invoke_setZoom() {
        invoke(mExportFuncIdx_setZoom);
    }

    private final static int mExportFuncIdx_fling = 4;
    public void invoke_fling() {
        invoke(mExportFuncIdx_fling);
    }

}

