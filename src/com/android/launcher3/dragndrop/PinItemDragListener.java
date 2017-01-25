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

import android.content.ClipDescription;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;

import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.compat.PinItemRequestCompat;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingItemPreviewProvider;

import java.util.UUID;

/**
 * {@link DragSource} for handling drop from from a different window. This object is initialized
 * in the source window and is passed on to the Launcher activity as an Intent extra.
 */
public class PinItemDragListener
        implements Parcelable, View.OnDragListener, DragSource, DragOptions.PreDragCondition {

    private static final String TAG = "PinItemDragListener";

    private static final String MIME_TYPE_PREFIX = "com.android.launcher3.drag_and_drop/";
    public static final String EXTRA_PIN_ITEM_DRAG_LISTENER = "pin_item_drag_listener";

    private final PinItemRequestCompat mRequest;

    // Position of preview relative to the touch location
    private final Rect mPreviewRect;

    // Randomly generated id used to verify the drag event.
    private final String mId;

    private Launcher mLauncher;
    private DragController mDragController;
    private long mDragStartTime;

    public PinItemDragListener(PinItemRequestCompat request, Rect previewRect) {
        mRequest = request;
        mPreviewRect = previewRect;
        mId = UUID.randomUUID().toString();
    }

    private PinItemDragListener(Parcel parcel) {
        mRequest = PinItemRequestCompat.CREATOR.createFromParcel(parcel);
        mPreviewRect = Rect.CREATOR.createFromParcel(parcel);
        mId = parcel.readString();
    }

    public String getMimeType() {
        return MIME_TYPE_PREFIX + mId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        mRequest.writeToParcel(parcel, i);
        mPreviewRect.writeToParcel(parcel, i);
        parcel.writeString(mId);
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
        mDragController = launcher.getDragController();
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

    private boolean onDragStart(DragEvent event) {
        if (!mRequest.isValid()) {
            return false;
        }
        ClipDescription desc =  event.getClipDescription();
        if (desc == null || !desc.hasMimeType(getMimeType())) {
            Log.e(TAG, "Someone started a dragAndDrop before us.");
            return false;
        }

        final PendingAddItemInfo item;
        final Bitmap preview;

        Point dragShift = new Point(mPreviewRect.left, mPreviewRect.top);
        if (mRequest.getRequestType() == PinItemRequestCompat.REQUEST_TYPE_SHORTCUT) {
            item = new PendingAddShortcutInfo(
                    new PinShortcutRequestActivityInfo(mRequest, mLauncher));

            ShortcutInfoCompat compat = new ShortcutInfoCompat(mRequest.getShortcutInfo());
            Bitmap icon = LauncherIcons.createShortcutIcon(compat, mLauncher, false /* badged */);

            // Create a preview same as the workspace cell size and draw the icon at the
            // appropriate position.
            int[] size = mLauncher.getWorkspace().estimateItemSize(item, true, false);
            preview = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(preview);
            DeviceProfile dp = mLauncher.getDeviceProfile();
            c.drawBitmap(icon, (size[0] - icon.getWidth()) / 2,
                    (size[1] - icon.getHeight() - dp.iconTextSizePx - dp.iconDrawablePaddingPx) / 2,
                    new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            PendingAddWidgetInfo info = new PendingAddWidgetInfo(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(
                            mLauncher, mRequest.getAppWidgetProviderInfo(mLauncher)));
            int[] size = mLauncher.getWorkspace().estimateItemSize(info, true, false);

            float minScale = 1.25f;
            int maxWidth = Math.min((int) (mPreviewRect.width() * minScale), size[0]);
            int[] previewSizeBeforeScale = new int[1];
            preview = LauncherAppState.getInstance(mLauncher).getWidgetCache()
                    .generateWidgetPreview(mLauncher, info.info, maxWidth, null,
                            previewSizeBeforeScale);

            dragShift.offset(
                    (mPreviewRect.width() - preview.getWidth()) / 2,
                    (mPreviewRect.height() - preview.getHeight()) / 2);
            item = info;
        }

        PendingItemPreviewProvider previewProvider =
                new PendingItemPreviewProvider(new View(mLauncher), item, preview);

        // Since we are not going through the workspace for starting the drag, set drag related
        // information on the workspace before starting the drag.
        mLauncher.getWorkspace().prepareDragWithProvider(previewProvider);

        Point downPos = new Point((int) event.getX(), (int) event.getY());
        DragOptions options = new DragOptions();
        options.systemDndStartPoint = downPos;
        options.preDragCondition = this;

        int x = downPos.x + dragShift.x;
        int y = downPos.y + dragShift.y;
        mDragController.startDrag(
                preview, x, y, this, item, null, null, 1f, options);
        mDragStartTime = SystemClock.uptimeMillis();
        return true;
    }

    @Override
    public boolean shouldStartDrag(double distanceDragged) {
        // Stay in pre-drag mode, if workspace is locked.
        return !mLauncher.isWorkspaceLocked();
    }

    @Override
    public void onPreDragStart(DropTarget.DragObject dragObject) {
        // The predrag starts when the workspace is not yet loaded. In some cases we set
        // the dragLayer alpha to 0 to have a nice fade-in animation. But that will prevent the
        // dragView from being visible. Instead just skip the fade-in animation here.
        mLauncher.getDragLayer().setAlpha(1);

        dragObject.dragView.setColor(
                mLauncher.getResources().getColor(R.color.delete_target_hover_tint));
    }

    @Override
    public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
        if (dragStarted) {
            dragObject.dragView.setColor(0);
        }
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return false;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 1f;
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget) && !(target instanceof Folder))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragModeDelayed(true,
                    Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        }
        postCleanup();
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target,
            LauncherLogProto.Target targetParent) {
        // TODO: We should probably log something
    }

    private void postCleanup() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                removeListener();
            }
        });
    }

    public void removeListener() {
        if (mLauncher != null) {
            mLauncher.getDragLayer().setOnDragListener(null);
        }
    }

    public static final Parcelable.Creator<PinItemDragListener> CREATOR =
            new Parcelable.Creator<PinItemDragListener>() {
                public PinItemDragListener createFromParcel(Parcel source) {
                    return new PinItemDragListener(source);
                }

                public PinItemDragListener[] newArray(int size) {
                    return new PinItemDragListener[size];
                }
            };
}
