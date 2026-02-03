/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnHoverListener;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.android.launcher3.CellLayout;
import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.List;

/**
 * Helper class to make drag-and-drop in a {@link CellLayout} accessible.
 */
public abstract class DragAndDropAccessibilityDelegate extends ExploreByTouchHelper
        implements OnClickListener, OnHoverListener {
    protected static final int INVALID_POSITION = -1;

    protected final Rect mTempRect = new Rect();
    protected final int[] mTempCords = new int[2];

    protected final CellLayout mView;
    protected final Context mContext;
    protected final ActivityContext mActivityContext;
    protected final LauncherAccessibilityDelegate mDelegate;
    protected final BaseDragLayer<?> mDragLayer;

    public DragAndDropAccessibilityDelegate(CellLayout forView) {
        super(forView);
        mView = forView;
        mContext = mView.getContext();
        mActivityContext = ActivityContext.lookupContext(mContext);
        mDelegate = (LauncherAccessibilityDelegate) mActivityContext.getAccessibilityDelegate();
        mDragLayer = mActivityContext.getDragLayer();
    }

    @Override
    public int getVirtualViewAt(float x, float y) {
        if (x < 0 || y < 0 || x > mView.getMeasuredWidth() || y > mView.getMeasuredHeight()) {
            return INVALID_ID;
        }
        mView.pointToCellExact((int) x, (int) y, mTempCords);

        // Map cell to id
        int id = mTempCords[0] + mTempCords[1] * mView.getCountX();
        return intersectsValidDropTarget(id);
    }

    /**
     * @return the view id of the top left corner of a valid drop region or
     * {@link #INVALID_POSITION} if there is no such valid region.
     */
    protected abstract int intersectsValidDropTarget(int id);

    @Override
    public void getVisibleVirtualViews(List<Integer> virtualViews) {
        // We create a virtual view for each cell of the grid
        // The cell ids correspond to cells in reading order.
        int nCells = mView.getCountX() * mView.getCountY();

        for (int i = 0; i < nCells; i++) {
            if (intersectsValidDropTarget(i) == i) {
                virtualViews.add(i);
            }
        }
    }

    @Override
    public boolean onPerformActionForVirtualView(int viewId, int action, Bundle args) {
        if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && viewId != INVALID_ID) {
            String confirmation = getConfirmationForIconDrop(viewId);
            mDelegate.handleAccessibleDrop(mView, getItemBounds(viewId), confirmation);
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        onPerformActionForVirtualView(getFocusedVirtualView(),
                AccessibilityNodeInfoCompat.ACTION_CLICK, null);
    }

    @Override
    protected void onPopulateEventForVirtualView(int id, AccessibilityEvent event) {
        if (id == INVALID_ID) {
            throw new IllegalArgumentException("Invalid virtual view id");
        }
        event.setContentDescription(mContext.getString(R.string.action_move_here));
    }

    @Override
    public void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat node) {
        if (id == INVALID_ID) {
            throw new IllegalArgumentException("Invalid virtual view id");
        }

        node.setContentDescription(getLocationDescriptionForIconDrop(id));

        Rect itemBounds = getItemBounds(id);
        node.setBoundsInParent(itemBounds);

        // ExploreByTouchHelper does not currently handle view scale.
        // Update BoundsInScreen to appropriate value.
        mTempCords[0] = mTempCords[1] = 0;
        float scale = mDragLayer.getDescendantCoordRelativeToSelf(mView, mTempCords);
        mTempRect.left = mTempCords[0] + (int) (itemBounds.left * scale);
        mTempRect.right = mTempCords[0] + (int) (itemBounds.right * scale);
        mTempRect.top = mTempCords[1] + (int) (itemBounds.top * scale);
        mTempRect.bottom = mTempCords[1] + (int) (itemBounds.bottom * scale);
        node.setBoundsInScreen(mTempRect);

        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        node.setClickable(true);
        node.setFocusable(true);
    }

    @Override
    public boolean onHover(View view, MotionEvent motionEvent) {
        return dispatchHoverEvent(motionEvent);
    }

    /**
     * Returns the target host container
     */
    public View getHost() {
        return mView;
    }

    protected abstract String getLocationDescriptionForIconDrop(int id);

    protected abstract String getConfirmationForIconDrop(int id);

    private Rect getItemBounds(int id) {
        int cellX = id % mView.getCountX();
        int cellY = id / mView.getCountX();
        LauncherAccessibilityDelegate.DragInfo dragInfo = mDelegate.getDragInfo();
        mView.cellToRect(cellX, cellY, dragInfo.info.spanX, dragInfo.info.spanY, mTempRect);
        return mTempRect;
    }
}