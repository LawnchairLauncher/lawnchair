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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetHostView;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderIcon;

/**
 * A utility class to generate preview bitmap for dragging.
 */
public class DragPreviewProvider {

    private final Rect mTempRect = new Rect();

    protected final View mView;

    // The padding added to the drag view during the preview generation.
    public final int previewPadding;

    protected final int blurSizeOutline;

    public Bitmap generatedDragOutline;

    public DragPreviewProvider(View view) {
        this(view, view.getContext());
    }

    public DragPreviewProvider(View view, Context context) {
        mView = view;
        blurSizeOutline =
                context.getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);

        if (mView instanceof BubbleTextView) {
            Drawable d = ((BubbleTextView) mView).getIcon();
            Rect bounds = getDrawableBounds(d);
            previewPadding = blurSizeOutline - bounds.left - bounds.top;
        } else {
            previewPadding = blurSizeOutline;
        }
    }

    /**
     * Draws the {@link #mView} into the given {@param destCanvas}.
     */
    private void drawDragView(Canvas destCanvas) {
        destCanvas.save();
        if (mView instanceof BubbleTextView) {
            Drawable d = ((BubbleTextView) mView).getIcon();
            Rect bounds = getDrawableBounds(d);
            destCanvas.translate(blurSizeOutline / 2 - bounds.left,
                    blurSizeOutline / 2 - bounds.top);
            d.draw(destCanvas);
        } else {
            final Rect clipRect = mTempRect;
            mView.getDrawingRect(clipRect);

            boolean textVisible = false;
            if (mView instanceof FolderIcon) {
                // For FolderIcons the text can bleed into the icon area, and so we need to
                // hide the text completely (which can't be achieved by clipping).
                if (((FolderIcon) mView).getTextVisible()) {
                    ((FolderIcon) mView).setTextVisible(false);
                    textVisible = true;
                }
            }
            destCanvas.translate(-mView.getScrollX() + blurSizeOutline / 2,
                    -mView.getScrollY() + blurSizeOutline / 2);
            destCanvas.clipRect(clipRect, Op.REPLACE);
            mView.draw(destCanvas);

            // Restore text visibility of FolderIcon if necessary
            if (textVisible) {
                ((FolderIcon) mView).setTextVisible(true);
            }
        }
        destCanvas.restore();
    }

    /**
     * Returns a new bitmap to show when the {@link #mView} is being dragged around.
     * Responsibility for the bitmap is transferred to the caller.
     */
    public Bitmap createDragBitmap(Canvas canvas) {
        float scale = 1f;
        int width = mView.getWidth();
        int height = mView.getHeight();

        if (mView instanceof BubbleTextView) {
            Drawable d = ((BubbleTextView) mView).getIcon();
            Rect bounds = getDrawableBounds(d);
            width = bounds.width();
            height = bounds.height();
        } else if (mView instanceof LauncherAppWidgetHostView) {
            scale = ((LauncherAppWidgetHostView) mView).getScaleToFit();
            width = (int) (mView.getWidth() * scale);
            height = (int) (mView.getHeight() * scale);
        }

        Bitmap b = Bitmap.createBitmap(width + blurSizeOutline, height + blurSizeOutline,
                Bitmap.Config.ARGB_8888);
        canvas.setBitmap(b);

        canvas.save();
        canvas.scale(scale, scale);
        drawDragView(canvas);
        canvas.restore();

        canvas.setBitmap(null);

        return b;
    }

    public final void generateDragOutline(Canvas canvas) {
        if (FeatureFlags.IS_DOGFOOD_BUILD && generatedDragOutline != null) {
            throw new RuntimeException("Drag outline generated twice");
        }

        generatedDragOutline = createDragOutline(canvas);
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    public Bitmap createDragOutline(Canvas canvas) {
        float scale = 1f;
        int width = mView.getWidth();
        int height = mView.getHeight();

        if (mView instanceof LauncherAppWidgetHostView) {
            scale = ((LauncherAppWidgetHostView) mView).getScaleToFit();
            width = (int) Math.floor(mView.getWidth() * scale);
            height = (int) Math.floor(mView.getHeight() * scale);
        }

        Bitmap b = Bitmap.createBitmap(width + blurSizeOutline, height + blurSizeOutline,
                Bitmap.Config.ALPHA_8);
        canvas.setBitmap(b);

        canvas.save();
        canvas.scale(scale, scale);
        drawDragView(canvas);
        canvas.restore();

        HolographicOutlineHelper.getInstance(mView.getContext())
                .applyExpensiveOutlineWithBlur(b, canvas);

        canvas.setBitmap(null);
        return b;
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

    public float getScaleAndPosition(Bitmap preview, int[] outPos) {
        float scale = Launcher.getLauncher(mView.getContext())
                .getDragLayer().getLocationInDragLayer(mView, outPos);
        if (mView instanceof LauncherAppWidgetHostView) {
            // App widgets are technically scaled, but are drawn at their expected size -- so the
            // app widget scale should not affect the scale of the preview.
            scale /= ((LauncherAppWidgetHostView) mView).getScaleToFit();
        }

        outPos[0] = Math.round(outPos[0] -
                (preview.getWidth() - scale * mView.getWidth() * mView.getScaleX()) / 2);
        outPos[1] = Math.round(outPos[1] - (1 - scale) * preview.getHeight() / 2
                - previewPadding / 2);
        return scale;
    }
}
