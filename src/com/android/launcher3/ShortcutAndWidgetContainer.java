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

import static android.view.MotionEvent.ACTION_DOWN;

import static com.android.launcher3.CellLayout.FOLDER;
import static com.android.launcher3.CellLayout.HOTSEAT;
import static com.android.launcher3.CellLayout.WORKSPACE;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_WIDGET_CENTERING;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Trace;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.NavigableAppWidgetHostView;

public class ShortcutAndWidgetContainer extends ViewGroup implements FolderIcon.FolderIconParent {
    static final String TAG = "ShortcutAndWidgetContainer";

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private final int[] mTmpCellXY = new int[2];

    @ContainerType
    private final int mContainerType;
    private final WallpaperManager mWallpaperManager;

    private int mCellWidth;
    private int mCellHeight;
    private Point mBorderSpace;

    private int mCountX;
    private int mCountY;

    private final ActivityContext mActivity;
    private boolean mInvertIfRtl = false;

    public ShortcutAndWidgetContainer(Context context, @ContainerType int containerType) {
        super(context);
        mActivity = ActivityContext.lookupContext(context);
        mWallpaperManager = WallpaperManager.getInstance(context);
        mContainerType = containerType;
        setClipChildren(false);
    }

    public void setCellDimensions(int cellWidth, int cellHeight, int countX, int countY,
            Point borderSpace) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mCountX = countX;
        mCountY = countY;
        mBorderSpace = borderSpace;
    }

    public View getChildAt(int cellX, int cellY) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();

            if ((lp.getCellX() <= cellX) && (cellX < lp.getCellX() + lp.cellHSpan)
                    && (lp.getCellY() <= cellY) && (cellY < lp.getCellY() + lp.cellVSpan)) {
                return child;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();

        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSpecSize, heightSpecSize);

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child);
            }
        }
    }

    /**
     * Adds view to Layout a new position and it does not trigger a layout request.
     * For more information check documentation for
     * {@code ViewGroup#addViewInLayout(View, int, LayoutParams, boolean)}
     *
     * @param child view to be added
     * @return true if the child was added, false otherwise
     */
    public boolean addViewInLayout(View child, LayoutParams layoutParams) {
        return super.addViewInLayout(child, -1, layoutParams, true);
    }

    public void setupLp(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (child instanceof NavigableAppWidgetHostView) {
            DeviceProfile profile = mActivity.getDeviceProfile();
            final PointF appWidgetScale = profile.getAppWidgetScale((ItemInfo) child.getTag());
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    appWidgetScale.x, appWidgetScale.y, mBorderSpace, profile.widgetPadding);
        } else {
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    mBorderSpace);
        }
    }

    // Set whether or not to invert the layout horizontally if the layout is in RTL mode.
    public void setInvertIfRtl(boolean invert) {
        mInvertIfRtl = invert;
    }

    public int getCellContentHeight() {
        return Math.min(getMeasuredHeight(),
                mActivity.getDeviceProfile().getCellContentHeight(mContainerType));
    }

    public void measureChild(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        final DeviceProfile dp = mActivity.getDeviceProfile();

        if (child instanceof NavigableAppWidgetHostView) {
            final PointF appWidgetScale = dp.getAppWidgetScale((ItemInfo) child.getTag());
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    appWidgetScale.x, appWidgetScale.y, mBorderSpace, dp.widgetPadding);
        } else {
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    mBorderSpace);
            // Center the icon/folder
            int cHeight = getCellContentHeight();
            int cellPaddingY =
                    dp.cellYPaddingPx >= 0 && mContainerType == WORKSPACE
                            ? dp.cellYPaddingPx
                            : (int) Math.max(0, ((lp.height - cHeight) / 2f));

            // No need to add padding when cell layout border spacing is present.
            boolean noPaddingX =
                    (dp.cellLayoutBorderSpacePx.x > 0 && mContainerType == WORKSPACE)
                            || (dp.folderCellLayoutBorderSpacePx.x > 0 && mContainerType == FOLDER)
                            || (dp.hotseatBorderSpace > 0 && mContainerType == HOTSEAT);
            int cellPaddingX = noPaddingX
                    ? 0
                    : mContainerType == WORKSPACE
                            ? dp.workspaceCellPaddingXPx
                            : (int) (dp.edgeMarginPx / 2f);
            child.setPadding(cellPaddingX, cellPaddingY, cellPaddingX, 0);
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
        Trace.beginSection("ShortcutAndWidgetConteiner#onLayout");
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                layoutChild(child);
            }
        }
        Trace.endSection();
    }

    /**
     * Core logic to layout a child for this ViewGroup.
     */
    public void layoutChild(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (child instanceof NavigableAppWidgetHostView) {
            NavigableAppWidgetHostView nahv = (NavigableAppWidgetHostView) child;

            // Scale and center the widget to fit within its cells.
            DeviceProfile profile = mActivity.getDeviceProfile();
            final PointF appWidgetScale = profile.getAppWidgetScale((ItemInfo) child.getTag());
            float scaleX = appWidgetScale.x;
            float scaleY = appWidgetScale.y;

            nahv.setScaleToFit(Math.min(scaleX, scaleY));
            nahv.getTranslateDelegate().setTranslation(INDEX_WIDGET_CENTERING,
                    -(lp.width - (lp.width * scaleX)) / 2.0f,
                    -(lp.height - (lp.height * scaleY)) / 2.0f);
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


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == ACTION_DOWN && getAlpha() == 0) {
            // Dont let children handle touch, if we are not visible.
            return true;
        }
        return super.onInterceptTouchEvent(ev);
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

    @Override
    public void drawFolderLeaveBehindForIcon(FolderIcon child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        // While the folder is open, the position of the icon cannot change.
        lp.canReorder = false;
        if (mContainerType == HOTSEAT) {
            CellLayout cl = (CellLayout) getParent();
            cl.setFolderLeaveBehindCell(lp.getCellX(), lp.getCellY());
        }
    }

    @Override
    public void clearFolderLeaveBehind(FolderIcon child) {
        ((CellLayoutLayoutParams) child.getLayoutParams()).canReorder = true;
        if (mContainerType == HOTSEAT) {
            CellLayout cl = (CellLayout) getParent();
            cl.clearFolderLeaveBehind();
        }
    }
}
