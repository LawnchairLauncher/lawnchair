/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.cyanogenmod.trebuchet.R;

public class Hotseat extends FrameLayout {
    private Launcher mLauncher;
    private CellLayout mContent;

    private int mCellCountX;
    private int mCellCountY;
    private int mAllAppsButtonRank;

    private boolean mTransposeLayoutWithOrientation;
    private boolean mIsLandscape;

    private static final int DEFAULT_CELL_COUNT_X = 5;
    private static final int DEFAULT_CELL_COUNT_Y = 1;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Hotseat, defStyle, 0);
        Resources r = context.getResources();
        mCellCountX = a.getInt(R.styleable.Hotseat_cellCountX, -1);
        mCellCountY = a.getInt(R.styleable.Hotseat_cellCountY, -1);
        mAllAppsButtonRank = r.getInteger(R.integer.hotseat_all_apps_index);
        mTransposeLayoutWithOrientation = 
                r.getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        mIsLandscape = context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
    }

    public void setup(Launcher launcher) {
        mLauncher = launcher;
        setOnKeyListener(new HotseatIconKeyEventListener());
    }

    CellLayout getLayout() {
        return mContent;
    }

    private boolean hasVerticalHotseat() {
        return (mIsLandscape && mTransposeLayoutWithOrientation);
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        return hasVerticalHotseat() ? (mContent.getCountY() - y - 1) : x;
    }
    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        return hasVerticalHotseat() ? 0 : rank;
    }
    int getCellYFromOrder(int rank) {
        return hasVerticalHotseat() ? (mContent.getCountY() - (rank + 1)) : 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mCellCountX < 0) mCellCountX = DEFAULT_CELL_COUNT_X;
        if (mCellCountY < 0) mCellCountY = DEFAULT_CELL_COUNT_Y;
        mContent = (CellLayout) findViewById(R.id.layout);
        mContent.setGridSize(mCellCountX, mCellCountY);
        mContent.setIsHotseat(true);

        resetLayout();
    }

    void resetLayout() {
        mContent.removeAllViewsInLayout();
    }
}
