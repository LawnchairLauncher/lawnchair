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
import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.HolographicOutlineHelper;
import com.android.launcher3.Launcher;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.Workspace;
import com.android.launcher3.graphics.DragPreviewProvider;

/**
 * Extension of {@link DragPreviewProvider} with logic specific to pending widgets/shortcuts
 * dragged from the widget tray.
 */
public class PendingItemPreviewProvider extends DragPreviewProvider {

    private final PendingAddItemInfo mAddInfo;
    private final Bitmap mPreviewBitmap;

    public PendingItemPreviewProvider(View view, PendingAddItemInfo addInfo, Bitmap preview) {
        super(view);
        mAddInfo = addInfo;
        mPreviewBitmap = preview;
    }

    @Override
    public Bitmap createDragOutline(Canvas canvas) {
        Workspace workspace = Launcher.getLauncher(mView.getContext()).getWorkspace();
        int[] size = workspace.estimateItemSize(mAddInfo, false);

        int w = size[0];
        int h = size[1];
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        canvas.setBitmap(b);

        Rect src = new Rect(0, 0, mPreviewBitmap.getWidth(), mPreviewBitmap.getHeight());
        float scaleFactor = Math.min((w - DRAG_BITMAP_PADDING) / (float) mPreviewBitmap.getWidth(),
                (h - DRAG_BITMAP_PADDING) / (float) mPreviewBitmap.getHeight());
        int scaledWidth = (int) (scaleFactor * mPreviewBitmap.getWidth());
        int scaledHeight = (int) (scaleFactor * mPreviewBitmap.getHeight());
        Rect dst = new Rect(0, 0, scaledWidth, scaledHeight);

        // center the image
        dst.offset((w - scaledWidth) / 2, (h - scaledHeight) / 2);

        canvas.drawBitmap(mPreviewBitmap, src, dst, null);

        // Don't clip alpha values for the drag outline if we're using the default widget preview
        boolean clipAlpha = !(mAddInfo instanceof PendingAddWidgetInfo &&
                (((PendingAddWidgetInfo) mAddInfo).previewImage == 0));
        HolographicOutlineHelper.obtain(mView.getContext())
                .applyExpensiveOutlineWithBlur(b, canvas, clipAlpha);
        canvas.setBitmap(null);

        return b;
    }
}
