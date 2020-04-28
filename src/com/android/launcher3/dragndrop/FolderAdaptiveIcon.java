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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.graphics.ShiftedBitmapDrawable;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.util.Preconditions;

/**
 * {@link AdaptiveIconDrawable} representation of a {@link FolderIcon}
 */
@TargetApi(Build.VERSION_CODES.O)
public class FolderAdaptiveIcon extends AdaptiveIconDrawable {
    private static final String TAG = "FolderAdaptiveIcon";

    private final Drawable mBadge;
    private final Path mMask;
    private final ConstantState mConstantState;

    private FolderAdaptiveIcon(Drawable bg, Drawable fg, Drawable badge, Path mask) {
        super(bg, fg);
        mBadge = badge;
        mMask = mask;

        mConstantState = new MyConstantState(bg.getConstantState(), fg.getConstantState(),
                badge.getConstantState(), mask);
    }

    @Override
    public Path getIconMask() {
        return mMask;
    }

    public Drawable getBadge() {
        return mBadge;
    }

    public static @Nullable FolderAdaptiveIcon createFolderAdaptiveIcon(
            Launcher launcher, int folderId, Point dragViewSize) {
        Preconditions.assertNonUiThread();
        int margin = launcher.getResources()
                .getDimensionPixelSize(R.dimen.blur_size_medium_outline);

        // Allocate various bitmaps on the background thread, because why not!
        int width = dragViewSize.x - margin;
        int height = dragViewSize.y - margin;
        if (width <= 0 || height <= 0) {
            return null;
        }
        final Bitmap badge = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // Create the actual drawable on the UI thread to avoid race conditions with
        // FolderIcon draw pass
        try {
            return MAIN_EXECUTOR.submit(() -> {
                FolderIcon icon = launcher.findFolderIcon(folderId);
                return icon == null ? null : createDrawableOnUiThread(icon, badge, dragViewSize);
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
            Bitmap badgeBitmap, Point dragViewSize) {
        Preconditions.assertUIThread();
        float margin = icon.getResources().getDimension(R.dimen.blur_size_medium_outline) / 2;

        Canvas c = new Canvas();
        PreviewBackground bg = icon.getFolderBackground();

        // Initialize badge
        c.setBitmap(badgeBitmap);
        bg.drawShadow(c);
        bg.drawBackgroundStroke(c);
        icon.drawDot(c);

        // Initialize preview
        final float sizeScaleFactor = 1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction();
        final int previewWidth = (int) (dragViewSize.x * sizeScaleFactor);
        final int previewHeight = (int) (dragViewSize.y * sizeScaleFactor);

        final float shiftFactor = AdaptiveIconDrawable.getExtraInsetFraction() / sizeScaleFactor;
        final float previewShiftX = shiftFactor * previewWidth;
        final float previewShiftY = shiftFactor * previewHeight;

        Bitmap previewBitmap = BitmapRenderer.createHardwareBitmap(previewWidth, previewHeight,
                (canvas) -> {
                    int count = canvas.save();
                    canvas.translate(previewShiftX, previewShiftY);
                    icon.getPreviewItemManager().draw(canvas);
                    canvas.restoreToCount(count);
                });

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

    @Override
    public ConstantState getConstantState() {
        return mConstantState;
    }

    private static class MyConstantState extends ConstantState {
        private final ConstantState mBg;
        private final ConstantState mFg;
        private final ConstantState mBadge;
        private final Path mMask;

        MyConstantState(ConstantState bg, ConstantState fg, ConstantState badge, Path mask) {
            mBg = bg;
            mFg = fg;
            mBadge = badge;
            mMask = mask;
        }

        @Override
        public Drawable newDrawable() {
            return new FolderAdaptiveIcon(mBg.newDrawable(), mFg.newDrawable(),
                    mBadge.newDrawable(), mMask);
        }

        @Override
        public int getChangingConfigurations() {
            return mBg.getChangingConfigurations() & mFg.getChangingConfigurations()
                    & mBadge.getChangingConfigurations();
        }
    }
}
