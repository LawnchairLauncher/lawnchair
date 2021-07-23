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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
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
    private static final Rect sTmpRect = new Rect();

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

        // Create the actual drawable on the UI thread to avoid race conditions with
        // FolderIcon draw pass
        try {
            return MAIN_EXECUTOR.submit(() -> {
                FolderIcon icon = launcher.findFolderIcon(folderId);
                return icon == null ? null : createDrawableOnUiThread(icon, dragViewSize);

            }).get();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create folder icon", e);
            return null;
        }
    }

    private static FolderAdaptiveIcon createDrawableOnUiThread(FolderIcon icon,
                                                               Point dragViewSize) {
        Preconditions.assertUIThread();

        icon.getPreviewBounds(sTmpRect);

        PreviewBackground bg = icon.getFolderBackground();

        // assume square
        assert (dragViewSize.x == dragViewSize.y);
        final int previewSize = sTmpRect.width();

        final int margin = (dragViewSize.x - previewSize) / 2;
        final float previewShiftX = -sTmpRect.left + margin;
        final float previewShiftY = -sTmpRect.top + margin;

        // Initialize badge, which consists of the outline stroke, shadow and dot; these
        // must be rendered above the foreground
        Bitmap badgeBmp = BitmapRenderer.createHardwareBitmap(dragViewSize.x, dragViewSize.y,
                (canvas) -> {
                    canvas.save();
                    canvas.translate(previewShiftX, previewShiftY);
                    bg.drawShadow(canvas);
                    bg.drawBackgroundStroke(canvas);
                    icon.drawDot(canvas);
                    canvas.restore();
                });

        // Initialize mask
        Path mask = new Path();
        Matrix m = new Matrix();
        m.setTranslate(previewShiftX, previewShiftY);
        bg.getClipPath().transform(m, mask);

        Bitmap previewBitmap = BitmapRenderer.createHardwareBitmap(dragViewSize.x, dragViewSize.y,
                (canvas) -> {
                    canvas.save();
                    canvas.translate(previewShiftX, previewShiftY);
                    icon.getPreviewItemManager().draw(canvas);
                    canvas.restore();
                });

        Bitmap bgBitmap = BitmapRenderer.createHardwareBitmap(dragViewSize.x, dragViewSize.y,
                (canvas) -> {
                    Paint p = new Paint();
                    p.setColor(bg.getBgColor());

                    canvas.drawCircle(dragViewSize.x / 2f, dragViewSize.y / 2f, bg.getRadius(), p);
                });

        ShiftedBitmapDrawable badge = new ShiftedBitmapDrawable(badgeBmp, 0, 0);
        ShiftedBitmapDrawable foreground = new ShiftedBitmapDrawable(previewBitmap, 0, 0);
        ShiftedBitmapDrawable background = new ShiftedBitmapDrawable(bgBitmap, 0, 0);

        return new FolderAdaptiveIcon(background, foreground, badge, mask);
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
