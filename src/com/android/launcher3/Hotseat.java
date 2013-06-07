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

package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.R;

import java.util.ArrayList;

public class Hotseat extends FrameLayout {
    @SuppressWarnings("unused")
    private static final String TAG = "Hotseat";

    private Launcher mLauncher;
    private CellLayout mContent;

    private int mCellCountX;
    private int mCellCountY;
    private int mAllAppsButtonRank;

    private boolean mTransposeLayoutWithOrientation;
    private boolean mIsLandscape;

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
    public boolean isAllAppsButtonRank(int rank) {
        return false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mCellCountX < 0) mCellCountX = LauncherModel.getCellCountX();
        if (mCellCountY < 0) mCellCountY = LauncherModel.getCellCountY();
        mContent = (CellLayout) findViewById(R.id.layout);
        mContent.setGridSize(mCellCountX, mCellCountY);
        mContent.setIsHotseat(true);

        resetLayout();
    }

    void resetLayout() {
        mContent.removeAllViewsInLayout();
    }

    void addAllAppsFolder(IconCache iconCache, ArrayList<ApplicationInfo> allApps,
            ArrayList<ComponentName> onWorkspace, Launcher launcher) {
        FolderInfo fi = new FolderInfo();

        fi.cellX = getCellXFromOrder(mAllAppsButtonRank);
        fi.cellY = getCellYFromOrder(mAllAppsButtonRank);
        fi.spanX = 1;
        fi.spanY = 1;
        fi.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        fi.screen = mAllAppsButtonRank;
        fi.itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        fi.title = "All Apps";
        LauncherModel.addItemToDatabase(launcher, fi, fi.container, fi.screen, fi.cellX,
                fi.cellY, false);
        FolderIcon folder = FolderIcon.fromXml(R.layout.folder_icon, launcher,
                getLayout(), fi, iconCache);

        CellLayout.LayoutParams lp = new CellLayout.LayoutParams(fi.cellX,fi.cellY,1,1);
        mContent.addViewToCellLayout(folder, -1, 0, lp, true);

        for (ApplicationInfo info: allApps) {
            ComponentName cn = info.intent.getComponent();
            if (!onWorkspace.contains(cn)) {
                System.out.println("Adding to all apps: " + info.intent);
                ShortcutInfo si = info.makeShortcut();
                fi.add(si);
            }
        }
    }

    void addAppsToAllAppsFolder(ArrayList<ApplicationInfo> apps) {
        View v = mContent.getChildAt(getCellXFromOrder(mAllAppsButtonRank), getCellYFromOrder(mAllAppsButtonRank));
        FolderIcon fi = null;

        if (v instanceof FolderIcon) {
            fi = (FolderIcon) v;
        } else {
            return;
        }

        FolderInfo info = fi.getFolderInfo();
        for (ApplicationInfo a: apps) {
            ComponentName cn = a.intent.getComponent();
            ShortcutInfo si = a.makeShortcut();
            info.add(si);
        }
    }
}
