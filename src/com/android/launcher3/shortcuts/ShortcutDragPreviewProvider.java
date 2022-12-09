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

package com.android.launcher3.shortcuts;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.views.ActivityContext;

/**
 * Extension of {@link DragPreviewProvider} which generates bitmaps scaled to the default icon size.
 */
public class ShortcutDragPreviewProvider extends DragPreviewProvider {

    private final Point mPositionShift;

    public ShortcutDragPreviewProvider(View icon, Point shift) {
        super(icon);
        mPositionShift = shift;
    }

    @Override
    public Drawable createDrawable() {
        int size = ActivityContext.lookupContext(mView.getContext())
                .getDeviceProfile().iconSizePx;
        return new FastBitmapDrawable(
                BitmapRenderer.createHardwareBitmap(
                        size + blurSizeOutline,
                        size + blurSizeOutline,
                        (c) -> drawDragViewOnBackground(c, size)));
    }

    private void drawDragViewOnBackground(Canvas canvas, float size) {
        Drawable d = mView.getBackground();
        Rect bounds = getDrawableBounds(d);

        canvas.translate(blurSizeOutline / 2, blurSizeOutline / 2);
        canvas.scale(size / bounds.width(), size / bounds.height(), 0, 0);
        canvas.translate(bounds.left, bounds.top);
        d.draw(canvas);
    }

    @Override
    public float getScaleAndPosition(Drawable preview, int[] outPos) {
        ActivityContext context = ActivityContext.lookupContext(mView.getContext());
        int iconSize = getDrawableBounds(mView.getBackground()).width();
        float scale = context.getDragLayer().getLocationInDragLayer(mView, outPos);

        int iconLeft = mView.getPaddingStart();
        if (Utilities.isRtl(mView.getResources())) {
            iconLeft = mView.getWidth() - iconSize - iconLeft;
        }

        outPos[0] += Math.round(
                scale * iconLeft + (scale * iconSize - preview.getIntrinsicWidth()) / 2
                        + mPositionShift.x);
        outPos[1] += Math.round((scale * mView.getHeight() - preview.getIntrinsicHeight()) / 2
                + mPositionShift.y);
        float size = context.getDeviceProfile().iconSizePx;
        return scale * iconSize / size;
    }
}
