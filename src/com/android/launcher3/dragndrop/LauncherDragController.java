/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.dragndrop;

import static com.android.launcher3.AbstractFloatingView.TYPE_DISCOVERY_BOUNCE;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.widget.util.WidgetDragScaleUtils;

/**
 * Drag controller for Launcher activity
 */
public class LauncherDragController extends DragController<Launcher> {

    private static final boolean PROFILE_DRAWING_DURING_DRAG = false;
    private final FlingToDeleteHelper mFlingToDeleteHelper;

    public LauncherDragController(Launcher launcher) {
        super(launcher);
        mFlingToDeleteHelper = new FlingToDeleteHelper(launcher);
    }

    @Override
    protected DragView startDrag(
            @Nullable Drawable drawable,
            @Nullable View view,
            DraggableView originalView,
            int dragLayerX,
            int dragLayerY,
            DragSource source,
            ItemInfo dragInfo,
            Rect dragRegion,
            float initialDragViewScale,
            float dragViewScaleOnDrop,
            DragOptions options) {
        if (PROFILE_DRAWING_DURING_DRAG) {
            android.os.Debug.startMethodTracing("Launcher");
        }

        mActivity.hideKeyboard();
        AbstractFloatingView.closeOpenViews(mActivity, false, TYPE_DISCOVERY_BOUNCE);

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

        final float scalePx;
        if (originalView.getViewType() == DraggableView.DRAGGABLE_WIDGET) {
            scalePx = mIsInPreDrag ? 0f : getWidgetDragScalePx(drawable, view, dragInfo);
        } else {
            scalePx = mIsInPreDrag ? res.getDimensionPixelSize(R.dimen.pre_drag_view_scale) : 0f;
        }
        final DragView dragView = mDragObject.dragView = drawable != null
                ? new LauncherDragView(
                mActivity,
                drawable,
                registrationX,
                registrationY,
                initialDragViewScale,
                dragViewScaleOnDrop,
                scalePx)
                : new LauncherDragView(
                        mActivity,
                        view,
                        view.getMeasuredWidth(),
                        view.getMeasuredHeight(),
                        registrationX,
                        registrationY,
                        initialDragViewScale,
                        dragViewScaleOnDrop,
                        scalePx);
        dragView.setItemInfo(dragInfo);
        mDragObject.dragComplete = false;

        mDragObject.xOffset = mMotionDown.x - (dragLayerX + dragRegionLeft);
        mDragObject.yOffset = mMotionDown.y - (dragLayerY + dragRegionTop);

        mDragDriver = DragDriver.create(this, mOptions, mFlingToDeleteHelper::recordMotionEvent);
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

        if (!mIsInPreDrag) {
            callOnDragStart();
        } else if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragStart(mDragObject);
        }

        handleMoveEvent(mLastTouch.x, mLastTouch.y);

        if (!isItemPinnable()
                || (!mActivity.isTouchInProgress() && options.simulatedDndStartPoint == null)) {
            // If it is an internal drag and the touch is already complete, cancel immediately
            MAIN_EXECUTOR.post(this::cancelDrag);
        }
        return dragView;
    }


    /**
     * Returns the scale in terms of pixels (to be applied on width) to scale the preview
     * during drag and drop.
     */
    @VisibleForTesting
    float getWidgetDragScalePx(@Nullable Drawable drawable, @Nullable View view,
            ItemInfo dragInfo) {
        float draggedViewWidthPx = 0;
        float draggedViewHeightPx = 0;

        if (view != null) {
            draggedViewWidthPx = view.getMeasuredWidth();
            draggedViewHeightPx = view.getMeasuredHeight();
        } else if (drawable != null) {
            draggedViewWidthPx = drawable.getIntrinsicWidth();
            draggedViewHeightPx = drawable.getIntrinsicHeight();
        }

        return WidgetDragScaleUtils.getWidgetDragScalePx(mActivity, mActivity.getDeviceProfile(),
                draggedViewWidthPx, draggedViewHeightPx, dragInfo);
    }

    @Override
    protected void exitDrag() {
        if (!mActivity.isInState(EDIT_MODE)) {
            mActivity.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
        }
    }

    @Override
    protected boolean endWithFlingAnimation() {
        Runnable flingAnimation = mFlingToDeleteHelper.getFlingAnimation(mDragObject, mOptions);
        if (flingAnimation != null) {
            drop(mFlingToDeleteHelper.getDropTarget(), flingAnimation);
            return true;
        }
        return super.endWithFlingAnimation();
    }

    @Override
    protected void endDrag() {
        super.endDrag();
        mFlingToDeleteHelper.releaseVelocityTracker();
    }

    @Override
    protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
        mActivity.getDragLayer().mapCoordInSelfToDescendant(mActivity.getWorkspace(),
                dropCoordinates);
        return mActivity.getWorkspace();
    }
}
