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

package ch.deletescape.lawnchair.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import ch.deletescape.lawnchair.HolographicOutlineHelper;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAppWidgetHostView;
import ch.deletescape.lawnchair.PreloadIconDrawable;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Workspace;
import ch.deletescape.lawnchair.folder.FolderIcon;

/**
 * A utility class to generate preview bitmap for dragging.
 */
public class DragPreviewProvider {

    public static final int DRAG_BITMAP_PADDING = 2;

    private final Rect mTempRect = new Rect();

    protected final View mView;

    // The padding added to the drag view during the preview generation.
    public final int previewPadding;

    protected final int blurSizeOutline;

    public Bitmap gerenatedDragOutline;

    public DragPreviewProvider(View view) {
        mView = view;
        blurSizeOutline = view.getContext().getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);
        if (mView instanceof TextView) {
            Drawable d = Workspace.getTextViewIcon((TextView) mView);
            Rect bounds = getDrawableBounds(d);
            previewPadding = DRAG_BITMAP_PADDING - bounds.left - bounds.top;
        } else {
            previewPadding = DRAG_BITMAP_PADDING;
        }
    }

    /**
     * Draws the {@link #mView} into the given {@param destCanvas}.
     */
    private void drawDragView(Canvas destCanvas) {
        destCanvas.save();
        if (this.mView instanceof TextView) {
            Drawable textViewIcon = Workspace.getTextViewIcon((TextView) this.mView);
            Rect drawableBounds = getDrawableBounds(textViewIcon);
            destCanvas.translate((float) ((this.blurSizeOutline / 2) - drawableBounds.left), (float) ((this.blurSizeOutline / 2) - drawableBounds.top));
            textViewIcon.draw(destCanvas);
        } else {
            boolean z;
            Rect rect = this.mTempRect;
            this.mView.getDrawingRect(rect);
            if ((this.mView instanceof FolderIcon) && ((FolderIcon) this.mView).getTextVisible()) {
                ((FolderIcon) this.mView).setTextVisible(false);
                z = true;
            } else {
                z = false;
            }
            destCanvas.translate((float) ((-this.mView.getScrollX()) + (this.blurSizeOutline / 2)), (float) ((-this.mView.getScrollY()) + (this.blurSizeOutline / 2)));
            destCanvas.clipRect(rect, Op.REPLACE);
            this.mView.draw(destCanvas);
            if (z) {
                ((FolderIcon) this.mView).setTextVisible(true);
            }
        }
        destCanvas.restore();
    }

    /**
     * Returns a new bitmap to show when the {@link #mView} is being dragged around.
     * Responsibility for the bitmap is transferred to the caller.
     */
    public Bitmap createDragBitmap(Canvas canvas) {
        float f = 1.0f;
        int width = this.mView.getWidth();
        int height = this.mView.getHeight();
        if (this.mView instanceof TextView) {
            Rect drawableBounds = getDrawableBounds(Workspace.getTextViewIcon((TextView) this.mView));
            width = drawableBounds.width();
            height = drawableBounds.height();
        } else if (this.mView instanceof LauncherAppWidgetHostView) {
            f = ((LauncherAppWidgetHostView) this.mView).getScaleToFit();
            width = (int) (((float) this.mView.getWidth()) * f);
            height = (int) (((float) this.mView.getHeight()) * f);
        }
        Bitmap createBitmap = Bitmap.createBitmap(width + this.blurSizeOutline, height + this.blurSizeOutline, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(createBitmap);
        canvas.save();
        canvas.scale(f, f);
        drawDragView(canvas);
        canvas.restore();
        canvas.setBitmap(null);
        return createBitmap;
    }

    public final void generateDragOutline(Canvas canvas) {
        gerenatedDragOutline = createDragOutline(canvas);
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    public Bitmap createDragOutline(Canvas canvas) {
        float f = 1.0f;
        int width = this.mView.getWidth();
        int height = this.mView.getHeight();
        if (this.mView instanceof LauncherAppWidgetHostView) {
            f = ((LauncherAppWidgetHostView) this.mView).getScaleToFit();
            width = (int) Math.floor((double) (((float) this.mView.getWidth()) * f));
            height = (int) Math.floor((double) (((float) this.mView.getHeight()) * f));
        }
        Bitmap createBitmap = Bitmap.createBitmap(width + this.blurSizeOutline, height + this.blurSizeOutline, Bitmap.Config.ALPHA_8);
        canvas.setBitmap(createBitmap);
        canvas.save();
        canvas.scale(f, f);
        drawDragView(canvas);
        canvas.restore();
        HolographicOutlineHelper.obtain(this.mView.getContext()).applyExpensiveOutlineWithBlur(createBitmap, canvas);
        canvas.setBitmap(null);
        return createBitmap;
    }

    protected static Rect getDrawableBounds(Drawable d) {
        Rect bounds = new Rect();
        d.copyBounds(bounds);
        if (bounds.width() == 0 || bounds.height() == 0) {
            bounds.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        } else {
            bounds.offsetTo(0, 0);
        }
        if (d instanceof PreloadIconDrawable) {
            int inset = -((PreloadIconDrawable) d).getOutset();
            bounds.inset(inset, inset);
        }
        return bounds;
    }

    public float getScaleAndPosition(Bitmap preview, int[] outPos) {
        float scaleToFit;
        float locationInDragLayer = Launcher.getLauncher(this.mView.getContext()).getDragLayer().getLocationInDragLayer(this.mView, outPos);
        if (this.mView instanceof LauncherAppWidgetHostView) {
            scaleToFit = locationInDragLayer / ((LauncherAppWidgetHostView) this.mView).getScaleToFit();
        } else {
            scaleToFit = locationInDragLayer;
        }
        outPos[0] = Math.round(((float) outPos[0]) - ((((float) preview.getWidth()) - ((((float) this.mView.getWidth()) * scaleToFit) * this.mView.getScaleX())) / 2.0f));
        outPos[1] = Math.round((((float) outPos[1]) - (((1.0f - scaleToFit) * ((float) preview.getHeight())) / 2.0f)) - ((float) (this.previewPadding / 2)));
        return scaleToFit;
    }
}
