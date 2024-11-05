/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragDriver;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.ItemInfo;

/**
 * Drag controller for Secondary Launcher activity
 */
public class SecondaryDragController extends DragController<SecondaryDisplayLauncher> {

    private static final boolean PROFILE_DRAWING_DURING_DRAG = false;

    public SecondaryDragController(SecondaryDisplayLauncher secondaryLauncher) {
        super(secondaryLauncher);
    }

    @Override
    protected DragView startDrag(@Nullable Drawable drawable, @Nullable View view,
            DraggableView originalView, int dragLayerX, int dragLayerY, DragSource source,
            ItemInfo dragInfo, Rect dragRegion, float initialDragViewScale,
            float dragViewScaleOnDrop, DragOptions options) {
        if (PROFILE_DRAWING_DURING_DRAG) {
            android.os.Debug.startMethodTracing("Launcher");
        }
        mActivity.hideKeyboard();

        mOptions = options;
        if (mOptions.simulatedDndStartPoint != null) {
            mLastTouch.x = mMotionDown.x = mOptions.simulatedDndStartPoint.x;
            mLastTouch.y = mMotionDown.y = mOptions.simulatedDndStartPoint.y;
        }

        final int registrationX = mMotionDown.x - dragLayerX;
        final int registrationY = mMotionDown.y - dragLayerY;

        final int dragRegionLeft = dragRegion == null ? 0 : dragRegion.left;
        final int dragRegionTop = dragRegion == null ? 0 : dragRegion.top;

        mLastDropTarget = null;

        mDragObject = new DropTarget.DragObject(mActivity.getApplicationContext());
        mDragObject.originalView = originalView;

        mIsInPreDrag = mOptions.preDragCondition != null
                && !mOptions.preDragCondition.shouldStartDrag(0);

        final Resources res = mActivity.getResources();
        final float scaleDps = mIsInPreDrag
                ? res.getDimensionPixelSize(R.dimen.pre_drag_view_scale) : 0f;

        final DragView dragView = mDragObject.dragView = drawable != null
                ? new SecondaryDragView(
                mActivity,
                drawable,
                registrationX,
                registrationY,
                initialDragViewScale,
                dragViewScaleOnDrop,
                scaleDps)
                : new SecondaryDragView(
                        mActivity,
                        view,
                        view.getMeasuredWidth(),
                        view.getMeasuredHeight(),
                        registrationX,
                        registrationY,
                        initialDragViewScale,
                        dragViewScaleOnDrop,
                        scaleDps);
        dragView.setItemInfo(dragInfo);
        mDragObject.dragComplete = false;

        mDragObject.xOffset = mMotionDown.x - (dragLayerX + dragRegionLeft);
        mDragObject.yOffset = mMotionDown.y - (dragLayerY + dragRegionTop);

        mDragDriver = DragDriver.create(this, mOptions, ev -> {
        });
        if (!mOptions.isAccessibleDrag) {
            mDragObject.stateAnnouncer = DragViewStateAnnouncer.createFor(dragView);
        }

        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;
        mDragObject.originalDragInfo = mDragObject.dragInfo.makeShallowCopy();

        if (mOptions.preDragCondition != null) {
            dragView.setHasDragOffset(mOptions.preDragCondition.getDragOffset().x != 0 ||
                    mOptions.preDragCondition.getDragOffset().y != 0);
        }

        if (dragRegion != null) {
            dragView.setDragRegion(new Rect(dragRegion));
        }

        mActivity.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        dragView.show(mLastTouch.x, mLastTouch.y);
        mDistanceSinceScroll = 0;

        if (!isItemPinnable()) {
            MAIN_EXECUTOR.post(this:: cancelDrag);
        }

        if (!mIsInPreDrag) {
            callOnDragStart();
        } else if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragStart(mDragObject);
        }

        handleMoveEvent(mLastTouch.x, mLastTouch.y);
        return dragView;
    }

    @Override
    protected void exitDrag() { }

    @Override
    protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
        DropTarget target = new DropTarget() {
            @Override
            public boolean isDropEnabled() {
                return true;
            }

            @Override
            public void onDrop(DragObject dragObject, DragOptions options) {
                ((SecondaryDragLayer) mActivity.getDragLayer()).getPinnedAppsAdapter().addPinnedApp(
                        dragObject.dragInfo);
                dragObject.dragView.remove();
            }

            @Override
            public void onDragEnter(DragObject dragObject) {
                if (getDistanceDragged() > mActivity.getResources().getDimensionPixelSize(
                        R.dimen.drag_distanceThreshold)) {
                    mActivity.showAppDrawer(false);
                    AbstractFloatingView.closeAllOpenViews(mActivity);
                }
            }

            @Override
            public void onDragOver(DragObject dragObject) { }

            @Override
            public void onDragExit(DragObject dragObject) { }

            @Override
            public boolean acceptDrop(DragObject dragObject) {
                return true;
            }

            @Override
            public void prepareAccessibilityDrop() { }

            @Override
            public void getHitRectRelativeToDragLayer(Rect outRect) { }
        };
        return target;
    }
}
