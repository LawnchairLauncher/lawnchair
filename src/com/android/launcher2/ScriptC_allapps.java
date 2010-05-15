
package com.android.launcher2;

import android.content.res.Resources;
import android.renderscript.*;
import android.util.Log;

public class ScriptC_allapps
    extends android.renderscript.ScriptC
{
    public ScriptC_allapps(RenderScript rs, Resources resources, int id, boolean isRoot) {
        super(rs, resources, id, isRoot);
    }

    private int mField_COLUMNS_PER_PAGE_PORTRAIT;
    public void set_COLUMNS_PER_PAGE_PORTRAIT(int v) {
        mField_COLUMNS_PER_PAGE_PORTRAIT = v;
        setVar(0, v);
    }

    private int mField_ROWS_PER_PAGE_PORTRAIT;
    public void set_ROWS_PER_PAGE_PORTRAIT(int v) {
        mField_ROWS_PER_PAGE_PORTRAIT = v;
        setVar(1, v);
    }

    private int mField_COLUMNS_PER_PAGE_LANDSCAPE;
    public void set_COLUMNS_PER_PAGE_LANDSCAPE(int v) {
        mField_COLUMNS_PER_PAGE_LANDSCAPE = v;
        setVar(2, v);
    }

    private int mField_ROWS_PER_PAGE_LANDSCAPE;
    public void set_ROWS_PER_PAGE_LANDSCAPE(int v) {
        mField_ROWS_PER_PAGE_LANDSCAPE = v;
        setVar(3, v);
    }


    private float mField_gNewPositionX;
    public void set_gNewPositionX(float v) {
        mField_gNewPositionX = v;
        setVar(4, v);
    }

    private int mField_gNewTouchDown;
    public void set_gNewTouchDown(int v) {
        mField_gNewTouchDown = v;
        setVar(5, v);
    }

    private float mField_gFlingVelocity;
    public void set_gFlingVelocity(float v) {
        mField_gFlingVelocity = v;
        setVar(6, v);
    }

    private int mField_gIconCount;
    public void set_gIconCount(int v) {
        mField_gIconCount = v;
        setVar(7, v);
    }
    public int get_gIconCount() {
        return mField_gIconCount;
    }

    private int mField_gSelectedIconIndex;
    public void set_gSelectedIconIndex(int v) {
        mField_gSelectedIconIndex = v;
        setVar(8, v);
    }
    public int get_gSelectedIconIndex() {
        return mField_gSelectedIconIndex;
    }

    private Allocation mField_gSelectedIconTexture;
    public void set_gSelectedIconTexture(Allocation v) {
        mField_gSelectedIconTexture = v;
        setVar(9, v.getID());
    }

    private float mField_gZoomTarget;
    public void set_gZoomTarget(float v) {
        mField_gZoomTarget = v;
        setVar(10, v);
    }

    private Allocation mField_gHomeButton;
    public void set_gHomeButton(Allocation v) {
        mField_gHomeButton = v;
        setVar(11, v.getID());
    }

    private float mField_gTargetPos;
    public void set_gTargetPos(float v) {
        mField_gTargetPos = v;
        setVar(12, v);
    }


    private ProgramFragment mField_gPFTexNearest;
    public void set_gPFTexNearest(ProgramFragment v) {
        mField_gPFTexNearest = v;
        setVar(13, v.getID());
    }

    private ProgramFragment mField_gPFTexMip;
    public void set_gPFTexMip(ProgramFragment v) {
        mField_gPFTexMip = v;
        setVar(14, v.getID());
    }

    private ProgramFragment mField_gPFTexMipAlpha;
    public void set_gPFTexMipAlpha(ProgramFragment v) {
        mField_gPFTexMipAlpha = v;
        setVar(15, v.getID());
    }

    private ProgramVertex mField_gPVCurve;
    public void set_gPVCurve(ProgramVertex v) {
        mField_gPVCurve = v;
        setVar(16, v.getID());
    }

    private ProgramStore mField_gPS;
    public void set_gPS(ProgramStore v) {
        mField_gPS = v;
        setVar(17, v.getID());
    }

    private SimpleMesh mField_gSMCell;
    public void set_gSMCell(SimpleMesh v) {
        mField_gSMCell = v;
        setVar(18, v.getID());
    }


    // binds
    private Allocation mField_gIconIDs;
    public void bind_gIconIDs(Allocation f) {
        mField_gIconIDs = f;
        bindAllocation(f, 19);
    }
    public Allocation get_gIconIDs() {
        return mField_gIconIDs;
    }

    private Allocation mField_gLabelIDs;
    public void bind_gLabelIDs(Allocation f) {
        mField_gLabelIDs = f;
        bindAllocation(f, 20);
    }
    public Allocation get_gLabelIDs() {
        return mField_gLabelIDs;
    }

    private ScriptField_VpConsts mField_vpConstants;
    public void bind_vpConstants(ScriptField_VpConsts f) {
        android.util.Log.e("rs", "bind_vpConstants " + f);
        mField_vpConstants = f;
        if (f == null) {
            bindAllocation(null, 21);
        } else {
            bindAllocation(f.getAllocation(), 21);
        }
    }
    public ScriptField_VpConsts get_vpConstants() {
        return mField_vpConstants;
    }



//#pragma rs export_func(resetHWWar, move, moveTo, setZoom, fling)

    public void invokable_Move() {
        //android.util.Log.e("rs", "invokable_Move");
        invoke(7);  // verified
    }
    public void invokable_MoveTo() {
        //android.util.Log.e("rs", "invokable_MoveTo");
        invoke(8);  // verified
    }
    public void invokable_Fling() {
        //android.util.Log.e("rs", "invokable_Fling");
        invoke(2);
    }
    //public void invokable_ResetWAR() {
        //android.util.Log.e("rs", "invokable_WAR");
        //invoke(9);  // verified
    //}
    public void invokable_SetZoom() {
        //android.util.Log.e("rs", "invokable_SetZoom");
        invoke(12);
    }
}


