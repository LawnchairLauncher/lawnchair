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

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import com.android.launcher3.Launcher;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.util.Preconditions;

import java.util.concurrent.Callable;

/**
 * {@link AdaptiveIconDrawable} representation of a {@link FolderIcon}
 */
@TargetApi(Build.VERSION_CODES.O)
public class FolderAdaptiveIcon extends AdaptiveIconDrawable {
    private static final String TAG = "FolderAdaptiveIcon";

    private final Drawable mBadge;
    private final Path mMask;

    private FolderAdaptiveIcon(Drawable bg, Drawable fg, Drawable badge, Path mask) {
        super(bg, fg);
        mBadge = badge;
        mMask = mask;
    }

    @Override
    public Path getIconMask() {
        return mMask;
    }

    public Drawable getBadge() {
        return mBadge;
    }

    public static FolderAdaptiveIcon createFolderAdaptiveIcon(
            final Launcher launcher, final long folderId, Point dragViewSize) {
        Preconditions.assertNonUiThread();
        int margin = launcher.getResources()
                .getDimensionPixelSize(R.dimen.blur_size_medium_outline);

        // Allocate various bitmaps on the background thread, because why not!
        final Bitmap badge = Bitmap.createBitmap(
                dragViewSize.x - margin, dragViewSize.y - margin, Bitmap.Config.ARGB_8888);

        // The bitmap for the preview is generated larger than needed to allow for the spring effect
        float sizeScaleFactor = 1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction();
        final Bitmap preview = Bitmap.createBitmap(
                (int) (dragViewSize.x * sizeScaleFactor), (int) (dragViewSize.y * sizeScaleFactor),
                Bitmap.Config.ARGB_8888);

        // Create the actual drawable on the UI thread to avoid race conditions with
        // FolderIcon draw pass
        try {
            return new MainThreadExecutor().submit(new Callable<FolderAdaptiveIcon>() {
                @Override
                public FolderAdaptiveIcon call() throws Exception {
                    FolderIcon icon = launcher.findFolderIcon(folderId);
                    return icon == null ? null : createDrawableOnUiThread(icon, badge, preview);
                }
            }).get();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create folder icon", e);
            return null;
        }
    }

    /**
     * Initializes various bitmaps on the UI thread and returns the final drawable.
     */
    private static FolderAdaptiveIcon createDrawableOnUiThread(FolderIcon icon,
            Bitmap badgeBitmap, Bitmap previewBitmap) {
        Preconditions.assertUIThread();
        float margin = icon.getResources().getDimension(R.dimen.blur_size_medium_outline) / 2;

        Canvas c = new Canvas();
        PreviewBackground bg = icon.getFolderBackground();

        // Initialize badge
        c.setBitmap(badgeBitmap);
        bg.drawShadow(c);
        bg.drawBackgroundStroke(c);
        icon.drawBadge(c);

        // Initialize preview
        float shiftFactor = AdaptiveIconDrawable.getExtraInsetFraction() /
                (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction());
        float previewShiftX = shiftFactor * previewBitmap.getWidth();
        float previewShiftY = shiftFactor * previewBitmap.getHeight();

        c.setBitmap(previewBitmap);
        c.translate(previewShiftX, previewShiftY);
        icon.getPreviewItemManager().draw(c);
        c.setBitmap(null);

        // Initialize mask
        Path mask = new Path();
        Matrix m = new Matrix();
        m.setTranslate(margin, margin);
        bg.getClipPath().transform(m, mask);

        ShiftedBitmapDrawable badge = new ShiftedBitmapDrawable(badgeBitmap, margin, margin);
        ShiftedBitmapDrawable foreground = new ShiftedBitmapDrawable(previewBitmap,
                margin - previewShiftX, margin - previewShiftY);

        return new FolderAdaptiveIcon(new ColorDrawable(bg.getBgColor()), foreground, badge, mask);
    }

    /**
     * A simple drawable which draws a bitmap at a fixed position irrespective of the bounds
     */
    private static class ShiftedBitmapDrawable extends Drawable {

        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Bitmap mBitmap;
        private final float mShiftX;
        private final float mShiftY;

        ShiftedBitmapDrawable(Bitmap bitmap, float shiftX, float shiftY) {
            mBitmap = bitmap;
            mShiftX = shiftX;
            mShiftY = shiftY;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, mShiftX, mShiftY, mPaint);
        }

        @Override
        public void setAlpha(int i) { }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
