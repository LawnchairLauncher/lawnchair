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

package ch.deletescape.lawnchair.shortcuts;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.view.View;

import ch.deletescape.lawnchair.BubbleTextView;
import ch.deletescape.lawnchair.HolographicOutlineHelper;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.graphics.DragPreviewProvider;

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
    public Bitmap createDragOutline(Canvas canvas) {
        Bitmap b = drawScaledPreview(canvas, Bitmap.Config.ALPHA_8);

        HolographicOutlineHelper.obtain(mView.getContext())
                .applyExpensiveOutlineWithBlur(b, canvas);
        canvas.setBitmap(null);
        return b;
    }

    @Override
    public Bitmap createDragBitmap(Canvas canvas) {
        Bitmap b = drawScaledPreview(canvas, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(null);
        return b;
    }

    private Bitmap drawScaledPreview(Canvas canvas, Bitmap.Config config) {
        Drawable d = getDrawable();
        Rect bounds = getDrawableBounds(d);

        int size = Launcher.getLauncher(mView.getContext()).getDeviceProfile().iconSizePx;

        final Bitmap b = Bitmap.createBitmap(
                size + DRAG_BITMAP_PADDING,
                size + DRAG_BITMAP_PADDING,
                config);

        canvas.setBitmap(b);
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(DRAG_BITMAP_PADDING / 2, DRAG_BITMAP_PADDING / 2);
        canvas.scale(((float) size) / bounds.width(), ((float) size) / bounds.height(), 0, 0);
        canvas.translate(bounds.left, bounds.top);
        d.draw(canvas);
        canvas.restore();
        return b;
    }

    @Nullable
    private Drawable getDrawable() {
        Drawable d = mView.getBackground();
        if (d == null && mView instanceof BubbleTextView)
            d = ((BubbleTextView) mView).getIcon();
        return d;
    }

    @Override
    public float getScaleAndPosition(Bitmap preview, int[] outPos) {
        Launcher launcher = Launcher.getLauncher(mView.getContext());
        int iconSize = getDrawableBounds(getDrawable()).width();
        float scale = launcher.getDragLayer().getLocationInDragLayer(mView, outPos);

        int iconLeft = mView.getPaddingStart();
        if (Utilities.isRtl(mView.getResources())) {
            iconLeft = mView.getWidth() - iconSize - iconLeft;
        }

        outPos[0] += Math.round(scale * iconLeft + (scale * iconSize - preview.getWidth()) / 2 +
                mPositionShift.x);
        outPos[1] += Math.round((scale * mView.getHeight() - preview.getHeight()) / 2
                + mPositionShift.y);
        float size = launcher.getDeviceProfile().iconSizePx;
        return scale * iconSize / size;
    }
}
