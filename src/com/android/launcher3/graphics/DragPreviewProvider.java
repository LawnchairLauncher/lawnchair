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

import static com.android.launcher3.BubbleTextView.DISPLAY_SEARCH_RESULT_APP_ROW;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

/**
 * A utility class to generate preview bitmap for dragging.
 */
public class DragPreviewProvider {
    private final Rect mTempRect = new Rect();

    protected final View mView;

    // The padding added to the drag view during the preview generation.
    public final int previewPadding;

    public final int blurSizeOutline;

    public DragPreviewProvider(View view) {
        this(view, view.getContext());
    }

    public DragPreviewProvider(View view, Context context) {
        mView = view;
        blurSizeOutline =
                context.getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);
        previewPadding = blurSizeOutline;
    }

    /**
     * Draws the {@link #mView} into the given {@param destCanvas}.
     */
    protected void drawDragView(Canvas destCanvas, float scale) {
        int saveCount = destCanvas.save();
        destCanvas.scale(scale, scale);

        if (mView instanceof DraggableView) {
            DraggableView dv = (DraggableView) mView;
            try (SafeCloseable t = dv.prepareDrawDragView()) {
                dv.getSourceVisualDragBounds(mTempRect);
                destCanvas.translate(blurSizeOutline / 2 - mTempRect.left,
                        blurSizeOutline / 2 - mTempRect.top);
                mView.draw(destCanvas);
            }
        }
        destCanvas.restoreToCount(saveCount);
    }

    /**
     * Returns a new drawable to show when the {@link #mView} is being dragged around.
     * Responsibility for the drawable is transferred to the caller.
     */
    public Drawable createDrawable() {
        if (mView instanceof LauncherAppWidgetHostView) {
            return null;
        }

        int width = 0;
        int height = 0;
        // Assume scaleX == scaleY, which is always the case for workspace items.
        float scale = mView.getScaleX();
        if (mView instanceof DraggableView) {
            ((DraggableView) mView).getSourceVisualDragBounds(mTempRect);
            width = mTempRect.width();
            height = mTempRect.height();
        } else {
            width = mView.getWidth();
            height = mView.getHeight();
        }

        if (mView instanceof BubbleTextView btv
                && btv.getIconDisplay() == DISPLAY_SEARCH_RESULT_APP_ROW) {
            FastBitmapDrawable icon = ((BubbleTextView) mView).getIcon();
            Drawable drawable = icon.getConstantState().newDrawable();
            float xInset = (float) blurSizeOutline / (float) (width + blurSizeOutline);
            float yInset = (float) blurSizeOutline / (float) (height + blurSizeOutline);
            return new InsetDrawable(drawable, xInset / 2, yInset / 2, xInset / 2, yInset / 2);
        }

        return new FastBitmapDrawable(
                BitmapRenderer.createHardwareBitmap(width + blurSizeOutline,
                        height + blurSizeOutline, (c) -> drawDragView(c, scale)));
    }

    /**
     * Returns the content view if the content should be rendered directly in
     * {@link com.android.launcher3.dragndrop.DragView}. Otherwise, returns null.
     */
    @Nullable
    public View getContentView() {
        if (mView instanceof LauncherAppWidgetHostView) {
            return mView;
        }
        return null;
    }

    protected static Rect getDrawableBounds(Drawable d) {
        Rect bounds = new Rect();
        d.copyBounds(bounds);
        if (bounds.width() == 0 || bounds.height() == 0) {
            bounds.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        } else {
            bounds.offsetTo(0, 0);
        }
        return bounds;
    }

    public float getScaleAndPosition(Drawable preview, int[] outPos) {
        float scale = ActivityContext.lookupContext(mView.getContext())
                .getDragLayer().getLocationInDragLayer(mView, outPos);
        if (mView instanceof LauncherAppWidgetHostView) {
            // App widgets are technically scaled, but are drawn at their expected size -- so the
            // app widget scale should not affect the scale of the preview.
            scale /= ((LauncherAppWidgetHostView) mView).getScaleToFit();
        }

        outPos[0] = Math.round(outPos[0] -
                (preview.getIntrinsicWidth() - scale * mView.getWidth() * mView.getScaleX()) / 2);
        outPos[1] = Math.round(outPos[1] - (1 - scale) * preview.getIntrinsicHeight() / 2
                - previewPadding / 2);
        return scale;
    }

    /** Returns the scale and position of a given view for drag-n-drop. */
    public float getScaleAndPosition(View view, int[] outPos) {
        float scale = ActivityContext.lookupContext(mView.getContext())
                .getDragLayer().getLocationInDragLayer(mView, outPos);
        if (mView instanceof LauncherAppWidgetHostView) {
            // App widgets are technically scaled, but are drawn at their expected size -- so the
            // app widget scale should not affect the scale of the preview.
            scale /= ((LauncherAppWidgetHostView) mView).getScaleToFit();
        }

        outPos[0] = Math.round(outPos[0]
                - (view.getWidth() - scale * mView.getWidth() * mView.getScaleX()) / 2);
        outPos[1] = Math.round(outPos[1] - (1 - scale) * view.getHeight() / 2 - previewPadding / 2);
        return scale;
    }

    protected Bitmap convertPreviewToAlphaBitmap(Bitmap preview) {
        return preview.copy(Bitmap.Config.ALPHA_8, true);
    }
}
