/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.states.RotationHelper.REQUEST_LOCK;
import static com.android.launcher3.states.RotationHelper.REQUEST_NONE;

import android.content.ClipDescription;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.states.InternalStateHandler;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.widget.PendingItemDragHelper;

import java.util.UUID;

/**
 * {@link DragSource} for handling drop from a different window.
 */
public abstract class BaseItemDragListener extends InternalStateHandler implements
        View.OnDragListener, DragSource, DragOptions.PreDragCondition {

    private static final String TAG = "BaseItemDragListener";

    private static final String MIME_TYPE_PREFIX = "com.android.launcher3.drag_and_drop/";
    public static final String EXTRA_PIN_ITEM_DRAG_LISTENER = "pin_item_drag_listener";

    // Position of preview relative to the touch location
    private final Rect mPreviewRect;

    private final int mPreviewBitmapWidth;
    private final int mPreviewViewWidth;

    // Randomly generated id used to verify the drag event.
    private final String mId;

    protected Launcher mLauncher;
    private DragController mDragController;
    private long mDragStartTime;

    public BaseItemDragListener(Rect previewRect, int previewBitmapWidth, int previewViewWidth) {
        mPreviewRect = previewRect;
        mPreviewBitmapWidth = previewBitmapWidth;
        mPreviewViewWidth = previewViewWidth;
        mId = UUID.randomUUID().toString();
    }

    public String getMimeType() {
        return MIME_TYPE_PREFIX + mId;
    }

    @Override
    public boolean init(Launcher launcher, boolean alreadyOnHome) {
        AbstractFloatingView.closeAllOpenViews(launcher, alreadyOnHome);
        launcher.getStateManager().goToState(NORMAL, alreadyOnHome /* animated */);
        launcher.getDragLayer().setOnDragListener(this);
        launcher.getRotationHelper().setStateHandlerRequest(REQUEST_LOCK);

        mLauncher = launcher;
        mDragController = launcher.getDragController();
        return false;
    }

    @Override
    public boolean onDrag(View view, DragEvent event) {
        if (mLauncher == null || mDragController == null) {
            postCleanup();
            return false;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            if (onDragStart(event)) {
                return true;
            } else {
                postCleanup();
                return false;
            }
        }
        return mDragController.onDragEvent(mDragStartTime, event);
    }

    protected boolean onDragStart(DragEvent event) {
        return onDragStart(event, this);
    }

    protected boolean onDragStart(DragEvent event, DragOptions.PreDragCondition preDragCondition) {
        ClipDescription desc =  event.getClipDescription();
        if (desc == null || !desc.hasMimeType(getMimeType())) {
            Log.e(TAG, "Someone started a dragAndDrop before us.");
            return false;
        }

        Point downPos = new Point((int) event.getX(), (int) event.getY());
        DragOptions options = new DragOptions();
        options.systemDndStartPoint = downPos;
        options.preDragCondition = preDragCondition;

        // We use drag event position as the screenPos for the preview image. Since mPreviewRect
        // already includes the view position relative to the drag event on the source window,
        // and the absolute position (position relative to the screen) of drag event is same
        // across windows, using drag position here give a good estimate for relative position
        // to source window.
        createDragHelper().startDrag(new Rect(mPreviewRect),
                mPreviewBitmapWidth, mPreviewViewWidth, downPos, this, options);
        mDragStartTime = SystemClock.uptimeMillis();
        return true;
    }

    protected abstract PendingItemDragHelper createDragHelper();

    @Override
    public boolean shouldStartDrag(double distanceDragged) {
        // Stay in pre-drag mode, if workspace is locked.
        return !mLauncher.isWorkspaceLocked();
    }

    @Override
    public void onPreDragStart(DragObject dragObject) {
        // The predrag starts when the workspace is not yet loaded. In some cases we set
        // the dragLayer alpha to 0 to have a nice fade-in animation. But that will prevent the
        // dragView from being visible. Instead just skip the fade-in animation here.
        mLauncher.getDragLayer().setAlpha(1);

        dragObject.dragView.setColor(
                mLauncher.getResources().getColor(R.color.delete_target_hover_tint));
    }

    @Override
    public void onPreDragEnd(DragObject dragObject, boolean dragStarted) {
        if (dragStarted) {
            dragObject.dragView.setColor(0);
        }
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {
        postCleanup();
    }

    protected void postCleanup() {
        clearReference();
        if (mLauncher != null) {
            // Remove any drag params from the launcher intent since the drag operation is complete.
            Intent newIntent = new Intent(mLauncher.getIntent());
            newIntent.removeExtra(EXTRA_PIN_ITEM_DRAG_LISTENER);
            mLauncher.setIntent(newIntent);
        }

        new Handler(Looper.getMainLooper()).post(this::removeListener);
    }

    public void removeListener() {
        if (mLauncher != null) {
            mLauncher.getRotationHelper().setStateHandlerRequest(REQUEST_NONE);
            mLauncher.getDragLayer().setOnDragListener(null);
        }
    }
}
