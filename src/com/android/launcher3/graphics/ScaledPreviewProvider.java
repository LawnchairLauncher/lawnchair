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

package com.android.launcher3.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import com.android.launcher3.HolographicOutlineHelper;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.graphics.DragPreviewProvider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extension of {@link DragPreviewProvider} which generates bitmaps scaled to the default icon size
 */
public class ScaledPreviewProvider extends DragPreviewProvider {

    public ScaledPreviewProvider(View v) {
        super(v);
    }

    @Override
    public Bitmap createDragOutline(Canvas canvas) {
        if (mView instanceof TextView) {
            Bitmap b = drawScaledPreview(canvas);

            final int outlineColor = mView.getResources().getColor(R.color.outline_color);
            HolographicOutlineHelper.obtain(mView.getContext())
                    .applyExpensiveOutlineWithBlur(b, canvas, outlineColor, outlineColor);
            canvas.setBitmap(null);
            return b;
        }
        return super.createDragOutline(canvas);
    }

    @Override
    public Bitmap createDragBitmap(Canvas canvas) {
        if (mView instanceof TextView) {
            Bitmap b = drawScaledPreview(canvas);
            canvas.setBitmap(null);
            return b;

        } else {
            return super.createDragBitmap(canvas);
        }
    }

    private Bitmap drawScaledPreview(Canvas canvas) {
        Drawable d = Workspace.getTextViewIcon((TextView) mView);
        Rect bounds = getDrawableBounds(d);

        int size = Launcher.getLauncher(mView.getContext()).getDeviceProfile().iconSizePx;

        final Bitmap b = Bitmap.createBitmap(
                size + DRAG_BITMAP_PADDING,
                size + DRAG_BITMAP_PADDING,
                Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(DRAG_BITMAP_PADDING / 2, DRAG_BITMAP_PADDING / 2);
        canvas.scale(((float) size) / bounds.width(), ((float) size) / bounds.height(), 0, 0);
        canvas.translate(bounds.left, bounds.top);
        d.draw(canvas);
        canvas.restore();
        return b;
    }
}
