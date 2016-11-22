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

package com.android.launcher3;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.support.annotation.IntDef;
import android.view.View;
import android.view.ViewGroup;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ShortcutAndWidgetContainer extends ViewGroup {
    static final String TAG = "ShortcutAndWidgetContainer";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DEFAULT, HOTSEAT, FOLDER})
    public @interface ContainerType{}
    public static final int DEFAULT = 0;
    public static final int HOTSEAT = 1;
    public static final int FOLDER = 2;

    private int mContainerType = DEFAULT;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private final int[] mTmpCellXY = new int[2];

    private final WallpaperManager mWallpaperManager;

    private int mCellWidth;
    private int mCellHeight;

    private int mCountX;

    private Launcher mLauncher;

    private boolean mInvertIfRtl = false;

    public ShortcutAndWidgetContainer(Context context) {
        super(context);
        mLauncher = Launcher.getLauncher(context);
        mWallpaperManager = WallpaperManager.getInstance(context);
    }

    public void setCellDimensions(int cellWidth, int cellHeight, int countX, int countY) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mCountX = countX;
    }

    public View getChildAt(int x, int y) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

            if ((lp.cellX <= x) && (x < lp.cellX + lp.cellHSpan) &&
                    (lp.cellY <= y) && (y < lp.cellY + lp.cellVSpan)) {
                return child;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();

        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecSize =  MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSpecSize, heightSpecSize);

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child);
            }
        }
    }

    public void setupLp(CellLayout.LayoutParams lp) {
        lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX);
    }

    // Set whether or not to invert the layout horizontally if the layout is in RTL mode.
    public void setInvertIfRtl(boolean invert) {
        mInvertIfRtl = invert;
    }

    public void setContainerType(@ContainerType int containerType) {
        mContainerType = containerType;
    }

    int getCellContentHeight() {
        final DeviceProfile grid = mLauncher.getDeviceProfile();
        int cellContentHeight = grid.cellHeightPx;
        if (mContainerType == HOTSEAT) {
            cellContentHeight = grid.hotseatCellHeightPx;
        } else if (mContainerType == FOLDER) {
            cellContentHeight = grid.folderCellHeightPx;
        }
        return Math.min(getMeasuredHeight(), cellContentHeight);
    }

    public void measureChild(View child) {
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();
        if (!lp.isFullscreen) {
            final DeviceProfile profile = mLauncher.getDeviceProfile();

            if (child instanceof LauncherAppWidgetHostView) {
                lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX,
                        profile.appWidgetScale.x, profile.appWidgetScale.y);
                // Widgets have their own padding
            } else {
                lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX);
                // Center the icon/folder
                int cHeight = getCellContentHeight();
                int cellPaddingY = (int) Math.max(0, ((lp.height - cHeight) / 2f));
                int cellPaddingX = (int) (profile.edgeMarginPx / 2f);
                child.setPadding(cellPaddingX, cellPaddingY, cellPaddingX, 0);
            }
        } else {
            lp.x = 0;
            lp.y = 0;
            lp.width = getMeasuredWidth();
            lp.height = getMeasuredHeight();
        }
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        child.measure(childWidthMeasureSpec, childheightMeasureSpec);
    }

    public boolean invertLayoutHorizontally() {
        return mInvertIfRtl && Utilities.isRtl(getResources());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();

                if (child instanceof LauncherAppWidgetHostView) {
                    // Scale and center the widget to fit within its cells.
                    DeviceProfile profile = mLauncher.getDeviceProfile();
                    float scaleX = profile.appWidgetScale.x;
                    float scaleY = profile.appWidgetScale.y;

                    float scale = Math.min(scaleX, scaleY);
                    child.setScaleX(scale);
                    child.setScaleY(scale);

                    child.setTranslationX(-(lp.width - (lp.width * scaleX)) / 2.0f);
                    child.setTranslationY(-(lp.height - (lp.height * scaleY)) / 2.0f);
                }

                int childLeft = lp.x;
                int childTop = lp.y;
                child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);

                if (lp.dropped) {
                    lp.dropped = false;

                    final int[] cellXY = mTmpCellXY;
                    getLocationOnScreen(cellXY);
                    mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                            WallpaperManager.COMMAND_DROP,
                            cellXY[0] + childLeft + lp.width / 2,
                            cellXY[1] + childTop + lp.height / 2, 0, null);
                }
            }
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (child != null) {
            Rect r = new Rect();
            child.getDrawingRect(r);
            requestRectangleOnScreen(r);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }
}
