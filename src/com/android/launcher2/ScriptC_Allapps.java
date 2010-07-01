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

    private final static int mExportVarIdx_gIconCount = 4;
    private int mExportVar_gIconCount;
    public void set_gIconCount(int v) {
        mExportVar_gIconCount = v;
        setVar(mExportVarIdx_gIconCount, v);
    }

    public int get_gIconCount() {
        return mExportVar_gIconCount;
    }

    private final static int mExportVarIdx_gSelectedIconIndex = 5;
    private int mExportVar_gSelectedIconIndex;
    public void set_gSelectedIconIndex(int v) {
        mExportVar_gSelectedIconIndex = v;
        setVar(mExportVarIdx_gSelectedIconIndex, v);
    }

    public int get_gSelectedIconIndex() {
        return mExportVar_gSelectedIconIndex;
    }

    private final static int mExportVarIdx_gSelectedIconTexture = 6;
    private Allocation mExportVar_gSelectedIconTexture;
    public void set_gSelectedIconTexture(Allocation v) {
        mExportVar_gSelectedIconTexture = v;
        setVar(mExportVarIdx_gSelectedIconTexture, (v == null) ? 0 : v.getID());
    }

    public Allocation get_gSelectedIconTexture() {
        return mExportVar_gSelectedIconTexture;
    }

    private final static int mExportVarIdx_gHomeButton = 7;
    private Allocation mExportVar_gHomeButton;
    public void set_gHomeButton(Allocation v) {
        mExportVar_gHomeButton = v;
        setVar(mExportVarIdx_gHomeButton, (v == null) ? 0 : v.getID());
    }

    public Allocation get_gHomeButton() {
        return mExportVar_gHomeButton;
    }

    private final static int mExportVarIdx_gPFTexNearest = 8;
    private ProgramFragment mExportVar_gPFTexNearest;
    public void set_gPFTexNearest(ProgramFragment v) {
        mExportVar_gPFTexNearest = v;
        setVar(mExportVarIdx_gPFTexNearest, (v == null) ? 0 : v.getID());
    }

    public ProgramFragment get_gPFTexNearest() {
        return mExportVar_gPFTexNearest;
    }

    private final static int mExportVarIdx_gPFTexMip = 9;
    private ProgramFragment mExportVar_gPFTexMip;
    public void set_gPFTexMip(ProgramFragment v) {
        mExportVar_gPFTexMip = v;
        setVar(mExportVarIdx_gPFTexMip, (v == null) ? 0 : v.getID());
    }

    public ProgramFragment get_gPFTexMip() {
        return mExportVar_gPFTexMip;
    }

    private final static int mExportVarIdx_gPFTexMipAlpha = 10;
    private ProgramFragment mExportVar_gPFTexMipAlpha;
    public void set_gPFTexMipAlpha(ProgramFragment v) {
        mExportVar_gPFTexMipAlpha = v;
        setVar(mExportVarIdx_gPFTexMipAlpha, (v == null) ? 0 : v.getID());
    }

    public ProgramFragment get_gPFTexMipAlpha() {
        return mExportVar_gPFTexMipAlpha;
    }

    private final static int mExportVarIdx_gPVCurve = 11;
    private ProgramVertex mExportVar_gPVCurve;
    public void set_gPVCurve(ProgramVertex v) {
        mExportVar_gPVCurve = v;
        setVar(mExportVarIdx_gPVCurve, (v == null) ? 0 : v.getID());
    }

    public ProgramVertex get_gPVCurve() {
        return mExportVar_gPVCurve;
    }

    private final static int mExportVarIdx_gPS = 12;
    private ProgramStore mExportVar_gPS;
    public void set_gPS(ProgramStore v) {
        mExportVar_gPS = v;
        setVar(mExportVarIdx_gPS, (v == null) ? 0 : v.getID());
    }

    public ProgramStore get_gPS() {
        return mExportVar_gPS;
    }

    private final static int mExportVarIdx_gSMCell = 13;
    private Mesh mExportVar_gSMCell;
    public void set_gSMCell(Mesh v) {
        mExportVar_gSMCell = v;
        setVar(mExportVarIdx_gSMCell, (v == null) ? 0 : v.getID());
    }

    public Mesh get_gSMCell() {
        return mExportVar_gSMCell;
    }

    private final static int mExportVarIdx_gIconIDs = 14;
    private Allocation mExportVar_gIconIDs;
    public void bind_gIconIDs(Allocation v) {
        mExportVar_gIconIDs = v;
        if(v == null) bindAllocation(null, mExportVarIdx_gIconIDs);
        else bindAllocation(v, mExportVarIdx_gIconIDs);
    }

    public Allocation get_gIconIDs() {
        return mExportVar_gIconIDs;
    }

    private final static int mExportVarIdx_gLabelIDs = 15;
    private Allocation mExportVar_gLabelIDs;
    public void bind_gLabelIDs(Allocation v) {
        mExportVar_gLabelIDs = v;
        if(v == null) bindAllocation(null, mExportVarIdx_gLabelIDs);
        else bindAllocation(v, mExportVarIdx_gLabelIDs);
    }

    public Allocation get_gLabelIDs() {
        return mExportVar_gLabelIDs;
    }

    private final static int mExportVarIdx_vpConstants = 16;
    private ScriptField_VpConsts mExportVar_vpConstants;
    public void bind_vpConstants(ScriptField_VpConsts v) {
        mExportVar_vpConstants = v;
        if(v == null) bindAllocation(null, mExportVarIdx_vpConstants);
        else bindAllocation(v.getAllocation(), mExportVarIdx_vpConstants);
    }

    public ScriptField_VpConsts get_vpConstants() {
        return mExportVar_vpConstants;
    }

    private final static int mExportVarIdx_gTargetPos = 17;
    private float mExportVar_gTargetPos;
    public void set_gTargetPos(float v) {
        mExportVar_gTargetPos = v;
        setVar(mExportVarIdx_gTargetPos, v);
    }

    public float get_gTargetPos() {
        return mExportVar_gTargetPos;
    }

    private final static int mExportFuncIdx_move = 0;
    public void invoke_move(float newPos) {
        FieldPacker move_fp = new FieldPacker(4);
        move_fp.addF32(newPos);
        invoke(mExportFuncIdx_move, move_fp);
    }

    private final static int mExportFuncIdx_moveTo = 1;
    public void invoke_moveTo(float targetPos) {
        FieldPacker moveTo_fp = new FieldPacker(4);
        moveTo_fp.addF32(targetPos);
        invoke(mExportFuncIdx_moveTo, moveTo_fp);
    }

    private final static int mExportFuncIdx_setZoom = 2;
    public void invoke_setZoom(float z, int animate) {
        FieldPacker setZoom_fp = new FieldPacker(8);
        setZoom_fp.addF32(z);
        setZoom_fp.addI32(animate);
        invoke(mExportFuncIdx_setZoom, setZoom_fp);
    }

    private final static int mExportFuncIdx_fling = 3;
    public void invoke_fling(float newPos, float vel) {
        FieldPacker fling_fp = new FieldPacker(8);
        fling_fp.addF32(newPos);
        fling_fp.addF32(vel);
        invoke(mExportFuncIdx_fling, fling_fp);
    }

}

