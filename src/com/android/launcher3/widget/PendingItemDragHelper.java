/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.RemoteViews;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.LivePreviewWidgetCell;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.icons.LauncherIcons;

/**
 * Extension of {@link DragPreviewProvider} with logic specific to pending widgets/shortcuts
 * dragged from the widget tray.
 */
public class PendingItemDragHelper extends DragPreviewProvider {

    private static final float MAX_WIDGET_SCALE = 1.25f;

    private final PendingAddItemInfo mAddInfo;
    private int[] mEstimatedCellSize;

    private RemoteViews mPreview;

    public PendingItemDragHelper(View view) {
        super(view);
        mAddInfo = (PendingAddItemInfo) view.getTag();
    }

    public void setPreview(RemoteViews preview) {
        mPreview = preview;
    }

    /**
     * Starts the drag for the pending item associated with the view.
     *
     * @param previewBounds The bounds where the image was displayed,
     *                      {@link WidgetImageView#getBitmapBounds()}
     * @param previewBitmapWidth The actual width of the bitmap displayed in the view.
     * @param previewViewWidth The width of {@link WidgetImageView} displaying the preview
     * @param screenPos Position of {@link WidgetImageView} on the screen
     */
    public void startDrag(Rect previewBounds, int previewBitmapWidth, int previewViewWidth,
            Point screenPos, DragSource source, DragOptions options) {
        final Launcher launcher = Launcher.getLauncher(mView.getContext());
        LauncherAppState app = LauncherAppState.getInstance(launcher);

        Bitmap preview = null;
        final float scale;
        final Point dragOffset;
        final Rect dragRegion;

        mEstimatedCellSize = launcher.getWorkspace().estimateItemSize(mAddInfo);

        if (mAddInfo instanceof PendingAddWidgetInfo) {
            PendingAddWidgetInfo createWidgetInfo = (PendingAddWidgetInfo) mAddInfo;

            int maxWidth = Math.min((int) (previewBitmapWidth * MAX_WIDGET_SCALE), mEstimatedCellSize[0]);

            int[] previewSizeBeforeScale = new int[1];

            if (mPreview != null) {
                preview = LivePreviewWidgetCell.generateFromRemoteViews(launcher, mPreview,
                        createWidgetInfo.info, maxWidth, previewSizeBeforeScale);
            }
            if (preview == null) {
                preview = app.getWidgetCache().generateWidgetPreview(
                        launcher, createWidgetInfo.info, maxWidth, null, previewSizeBeforeScale);
            }

            if (previewSizeBeforeScale[0] < previewBitmapWidth) {
                // The icon has extra padding around it.
                int padding = (previewBitmapWidth - previewSizeBeforeScale[0]) / 2;
                if (previewBitmapWidth > previewViewWidth) {
                    padding = padding * previewViewWidth / previewBitmapWidth;
                }

                previewBounds.left += padding;
                previewBounds.right -= padding;
            }
            scale = previewBounds.width() / (float) preview.getWidth();
            launcher.getDragController().addDragListener(new WidgetHostViewLoader(launcher, mView));

            dragOffset = null;
            dragRegion = null;
        } else {
            PendingAddShortcutInfo createShortcutInfo = (PendingAddShortcutInfo) mAddInfo;
            Drawable icon = createShortcutInfo.activityInfo.getFullResIcon(app.getIconCache());
            LauncherIcons li = LauncherIcons.obtain(launcher);
            preview = li.createScaledBitmapWithoutShadow(icon, 0);
            li.recycle();
            scale = ((float) launcher.getDeviceProfile().iconSizePx) / preview.getWidth();

            dragOffset = new Point(previewPadding / 2, previewPadding / 2);

            // Create a preview same as the workspace cell size and draw the icon at the
            // appropriate position.
            DeviceProfile dp = launcher.getDeviceProfile();
            int iconSize = dp.iconSizePx;

            int padding = launcher.getResources()
                    .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
            previewBounds.left += padding;
            previewBounds.top += padding;

            dragRegion = new Rect();
            dragRegion.left = (mEstimatedCellSize[0] - iconSize) / 2;
            dragRegion.right = dragRegion.left + iconSize;
            dragRegion.top = (mEstimatedCellSize[1]
                    - iconSize - dp.iconTextSizePx - dp.iconDrawablePaddingPx) / 2;
            dragRegion.bottom = dragRegion.top + iconSize;
        }

        // Since we are not going through the workspace for starting the drag, set drag related
        // information on the workspace before starting the drag.
        launcher.getWorkspace().prepareDragWithProvider(this);

        int dragLayerX = screenPos.x + previewBounds.left
                + (int) ((scale * preview.getWidth() - preview.getWidth()) / 2);
        int dragLayerY = screenPos.y + previewBounds.top
                + (int) ((scale * preview.getHeight() - preview.getHeight()) / 2);

        // Start the drag
        launcher.getDragController().startDrag(preview, dragLayerX, dragLayerY, source, mAddInfo,
                dragOffset, dragRegion, scale, scale, options);
    }

    @Override
    protected Bitmap convertPreviewToAlphaBitmap(Bitmap preview) {
        if (mAddInfo instanceof PendingAddShortcutInfo || mEstimatedCellSize == null) {
            return super.convertPreviewToAlphaBitmap(preview);
        }

        int w = mEstimatedCellSize[0];
        int h = mEstimatedCellSize[1];
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        Rect src = new Rect(0, 0, preview.getWidth(), preview.getHeight());

        float scaleFactor = Math.min((w - blurSizeOutline) / (float) preview.getWidth(),
                (h - blurSizeOutline) / (float) preview.getHeight());
        int scaledWidth = (int) (scaleFactor * preview.getWidth());
        int scaledHeight = (int) (scaleFactor * preview.getHeight());
        Rect dst = new Rect(0, 0, scaledWidth, scaledHeight);

        // center the image
        dst.offset((w - scaledWidth) / 2, (h - scaledHeight) / 2);
        new Canvas(b).drawBitmap(preview, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
        return b;
    }
}
