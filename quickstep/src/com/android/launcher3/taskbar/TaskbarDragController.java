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
package com.android.launcher3.taskbar;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragDriver;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ClipDescriptionCompat;
import com.android.systemui.shared.system.LauncherAppsCompat;

/**
 * Handles long click on Taskbar items to start a system drag and drop operation.
 */
public class TaskbarDragController extends DragController<TaskbarActivityContext>  {

    private final int mDragIconSize;
    private final int[] mTempXY = new int[2];

    // Where the initial touch was relative to the dragged icon.
    private int mRegistrationX;
    private int mRegistrationY;

    private boolean mIsSystemDragInProgress;

    public TaskbarDragController(TaskbarActivityContext activity) {
        super(activity);
        Resources resources = mActivity.getResources();
        mDragIconSize = resources.getDimensionPixelSize(R.dimen.taskbar_icon_drag_icon_size);
    }

    /**
     * Attempts to start a system drag and drop operation for the given View, using its tag to
     * generate the ClipDescription and Intent.
     * @return Whether {@link View#startDragAndDrop} started successfully.
     */
    protected boolean startDragOnLongClick(View view) {
        if (!(view instanceof BubbleTextView)) {
            return false;
        }

        BubbleTextView btv = (BubbleTextView) view;

        mActivity.setTaskbarWindowFullscreen(true);
        view.post(() -> {
            startInternalDrag(btv);
            btv.setVisibility(INVISIBLE);
        });
        return true;
    }

    private void startInternalDrag(BubbleTextView btv) {
        float iconScale = 1f;
        Drawable icon = btv.getIcon();
        if (icon instanceof FastBitmapDrawable) {
            iconScale = ((FastBitmapDrawable) icon).getAnimatedScale();
        }

        // Clear the pressed state if necessary
        btv.clearFocus();
        btv.setPressed(false);
        btv.clearPressedBackground();

        final DragPreviewProvider previewProvider = new DragPreviewProvider(btv);
        final Drawable drawable = previewProvider.createDrawable();
        final float scale = previewProvider.getScaleAndPosition(drawable, mTempXY);
        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        Rect dragRect = new Rect();
        btv.getSourceVisualDragBounds(dragRect);
        dragLayerY += dragRect.top;

        DragOptions dragOptions = new DragOptions();
        dragOptions.preDragCondition = new DragOptions.PreDragCondition() {
            private DragView mDragView;

            @Override
            public boolean shouldStartDrag(double distanceDragged) {
                return mDragView != null && mDragView.isAnimationFinished();
            }

            @Override
            public void onPreDragStart(DropTarget.DragObject dragObject) {
                mDragView = dragObject.dragView;
            }

            @Override
            public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                mDragView = null;
            }
        };
        // TODO: open popup/pre-drag
        // PopupContainerWithArrow popupContainer = PopupContainerWithArrow.showForIcon(view);
        // if (popupContainer != null) {
        //     dragOptions.preDragCondition = popupContainer.createPreDragCondition();
        // }

        startDrag(
                drawable,
                /* view = */ null,
                /* originalView = */ btv,
                dragLayerX,
                dragLayerY,
                (View target, DropTarget.DragObject d, boolean success) -> {} /* DragSource */,
                (WorkspaceItemInfo) btv.getTag(),
                /* dragVisualizeOffset = */ null,
                dragRect,
                scale * iconScale,
                scale,
                dragOptions);
    }

    @Override
    protected DragView startDrag(@Nullable Drawable drawable, @Nullable View view,
            DraggableView originalView, int dragLayerX, int dragLayerY, DragSource source,
            ItemInfo dragInfo, Point dragOffset, Rect dragRegion, float initialDragViewScale,
            float dragViewScaleOnDrop, DragOptions options) {
        mOptions = options;

        mRegistrationX = mMotionDown.x - dragLayerX;
        mRegistrationY = mMotionDown.y - dragLayerY;

        final int dragRegionLeft = dragRegion == null ? 0 : dragRegion.left;
        final int dragRegionTop = dragRegion == null ? 0 : dragRegion.top;

        mLastDropTarget = null;

        mDragObject = new DropTarget.DragObject(mActivity.getApplicationContext());
        mDragObject.originalView = originalView;
        mDragObject.deferDragViewCleanupPostAnimation = false;

        mIsInPreDrag = mOptions.preDragCondition != null
                && !mOptions.preDragCondition.shouldStartDrag(0);

        float scalePx = mDragIconSize - dragRegion.width();
        final DragView dragView = mDragObject.dragView = new TaskbarDragView(
                mActivity,
                drawable,
                mRegistrationX,
                mRegistrationY,
                initialDragViewScale,
                dragViewScaleOnDrop,
                scalePx);
        dragView.setItemInfo(dragInfo);
        mDragObject.dragComplete = false;

        mDragObject.xOffset = mMotionDown.x - (dragLayerX + dragRegionLeft);
        mDragObject.yOffset = mMotionDown.y - (dragLayerY + dragRegionTop);

        mDragDriver = DragDriver.create(this, mOptions, /* secondaryEventConsumer = */ ev -> {});
        if (!mOptions.isAccessibleDrag) {
            mDragObject.stateAnnouncer = DragViewStateAnnouncer.createFor(dragView);
        }

        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;
        mDragObject.originalDragInfo = mDragObject.dragInfo.makeShallowCopy();

        if (dragRegion != null) {
            dragView.setDragRegion(new Rect(dragRegion));
        }

        dragView.show(mLastTouch.x, mLastTouch.y);
        mDistanceSinceScroll = 0;

        if (!mIsInPreDrag) {
            callOnDragStart();
        } else if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragStart(mDragObject);
        }

        handleMoveEvent(mLastTouch.x, mLastTouch.y);

        return dragView;
    }

    @Override
    protected void callOnDragStart() {
        super.callOnDragStart();
        // Pre-drag has ended, start the global system drag.
        AbstractFloatingView.closeAllOpenViews(mActivity);
        startSystemDrag((BubbleTextView) mDragObject.originalView);
    }

    private void startSystemDrag(BubbleTextView btv) {
        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(btv) {

            @Override
            public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
                shadowSize.set(mDragIconSize, mDragIconSize);
                // The registration point was taken before the icon scaled to mDragIconSize, so
                // offset the registration to where the touch is on the new size.
                int offset = (mDragIconSize - btv.getIconSize()) / 2;
                shadowTouchPoint.set(mRegistrationX + offset, mRegistrationY + offset);
            }

            @Override
            public void onDrawShadow(Canvas canvas) {
                canvas.save();
                float scale = (float) mDragIconSize / btv.getIconSize();
                canvas.scale(scale, scale);
                btv.getIcon().draw(canvas);
                canvas.restore();
            }
        };

        Object tag = btv.getTag();
        ClipDescription clipDescription = null;
        Intent intent = null;
        if (tag instanceof WorkspaceItemInfo) {
            WorkspaceItemInfo item = (WorkspaceItemInfo) tag;
            LauncherApps launcherApps = mActivity.getSystemService(LauncherApps.class);
            clipDescription = new ClipDescription(item.title,
                    new String[] {
                            item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                                    ? ClipDescriptionCompat.MIMETYPE_APPLICATION_SHORTCUT
                                    : ClipDescriptionCompat.MIMETYPE_APPLICATION_ACTIVITY
                    });
            intent = new Intent();
            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, item.getIntent().getPackage());
                intent.putExtra(Intent.EXTRA_SHORTCUT_ID, item.getDeepShortcutId());
            } else {
                intent.putExtra(ClipDescriptionCompat.EXTRA_PENDING_INTENT,
                        LauncherAppsCompat.getMainActivityLaunchIntent(launcherApps,
                                item.getIntent().getComponent(), null, item.user));
            }
            intent.putExtra(Intent.EXTRA_USER, item.user);
        } else if (tag instanceof Task) {
            Task task = (Task) tag;
            clipDescription = new ClipDescription(task.titleDescription,
                    new String[] {
                            ClipDescriptionCompat.MIMETYPE_APPLICATION_TASK
                    });
            intent = new Intent();
            intent.putExtra(ClipDescriptionCompat.EXTRA_TASK_ID, task.key.id);
            intent.putExtra(Intent.EXTRA_USER, UserHandle.of(task.key.userId));
        }

        if (clipDescription != null && intent != null) {
            ClipData clipData = new ClipData(clipDescription, new ClipData.Item(intent));
            if (btv.startDragAndDrop(clipData, shadowBuilder, null /* localState */,
                    View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_OPAQUE)) {
                onSystemDragStarted();
            }
        }
    }

    private void onSystemDragStarted() {
        mIsSystemDragInProgress = true;
        mActivity.getDragLayer().setOnDragListener((view, dragEvent) -> {
            switch (dragEvent.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // Return true to tell system we are interested in events, so we get DRAG_ENDED.
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    mIsSystemDragInProgress = false;
                    maybeOnDragEnd();
                    return true;
            }
            return false;
        });
    }

    @Override
    public boolean isDragging() {
        return super.isDragging() || mIsSystemDragInProgress;
    }

    private void maybeOnDragEnd() {
        if (!isDragging()) {
            ((View) mDragObject.originalView).setVisibility(VISIBLE);
        }
    }

    @Override
    protected void callOnDragEnd() {
        super.callOnDragEnd();
        maybeOnDragEnd();
    }

    @Override
    protected float getX(MotionEvent ev) {
        // We will resize to fill the screen while dragging, so use screen coordinates. This ensures
        // we start at the correct position even though touch down is on the smaller DragLayer size.
        return ev.getRawX();
    }

    @Override
    protected float getY(MotionEvent ev) {
        // We will resize to fill the screen while dragging, so use screen coordinates. This ensures
        // we start at the correct position even though touch down is on the smaller DragLayer size.
        return ev.getRawY();
    }

    @Override
    protected Point getClampedDragLayerPos(float x, float y) {
        // No need to clamp, as we will take up the entire screen.
        mTmpPoint.set(Math.round(x), Math.round(y));
        return mTmpPoint;
    }

    @Override
    protected void exitDrag() {
        if (mDragObject != null) {
            mActivity.getDragLayer().removeView(mDragObject.dragView);
        }
    }

    @Override
    public void addDropTarget(DropTarget target) {
        // No-op as Taskbar currently doesn't support any drop targets internally.
        // Note: if we do add internal DropTargets, we'll still need to ignore Folder.
    }

    @Override
    protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
        return null;
    }
}
