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

import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

public class Hotseat extends PagedView {
    private Launcher mLauncher;
    private CellLayout mContent;

    private int mCellCount;

    private boolean mTransposeLayoutWithOrientation;
    private boolean mIsLandscape;

    private static final int DEFAULT_CELL_COUNT = 5;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mFadeInAdjacentScreens = false;
        mHandleScrollIndicator = true;

        int hotseatPages = PreferencesProvider.Interface.Dock.getNumberPages();
        int defaultPage = PreferencesProvider.Interface.Dock.getDefaultPage(hotseatPages / 2);


        mCurrentPage = defaultPage;

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Hotseat, defStyle, 0);
        mTransposeLayoutWithOrientation =
                context.getResources().getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        mIsLandscape = context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
        mCellCount = a.getInt(R.styleable.Hotseat_cellCount, DEFAULT_CELL_COUNT);
        int cellCount = PreferencesProvider.Interface.Dock.getNumberIcons(0);
        if (cellCount > 0) {
            mCellCount = cellCount;
        }

        mVertical = hasVerticalHotseat();

        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (int i = 0; i < hotseatPages; i++) {
            CellLayout cl = (CellLayout) inflater.inflate(R.layout.hotseat_page, null);
            cl.setIsHotseat(true);
            cl.setGridSize((!mIsLandscape ? mCellCount : 1), (mIsLandscape ? mCellCount : 1));
            addView(cl);
        }

        // No data needed
        setDataIsReady();
    }

    public void setup(Launcher launcher) {
        mLauncher = launcher;
        setOnKeyListener(new HotseatIconKeyEventListener());
    }

    CellLayout getLayout() {
        return (CellLayout) getPageAt(mCurrentPage);
    }

    public boolean hasPage(View view) {
        for (int i = 0; i < getChildCount(); i++) {
            if (view == getChildAt(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVerticalHotseat() {
        return (mIsLandscape && mTransposeLayoutWithOrientation);
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        return hasVerticalHotseat() ? (mCellCount - y - 1) : x;
    }
    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        return hasVerticalHotseat() ? 0 : rank;
    }
    int getCellYFromOrder(int rank) {
        return hasVerticalHotseat() ? (mCellCount - rank - 1) : 0;
    }
    int getScreenFromOrder(int screen) {
        return hasVerticalHotseat() ? (getChildCount() - screen - 1) : screen;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        resetLayout();
    }

    void resetLayout() {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            cl.removeAllViewsInLayout();
        }
    }

    @Override
    public void syncPages() {
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
    }

    @Override
    protected void loadAssociatedPages(int page) {
    }
    @Override
    protected void loadAssociatedPages(int page, boolean immediateAndOnly) {
    }
}
