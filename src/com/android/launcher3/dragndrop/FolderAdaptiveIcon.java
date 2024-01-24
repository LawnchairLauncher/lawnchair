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
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Picture;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.views.ActivityContext;

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
            ActivityContext activity, int folderId, Point size) {
        Preconditions.assertNonUiThread();

        // assume square
        if (size.x != size.y) {
            return null;
        }
        int requestedSize = size.x;

        // Only use the size actually needed for drawing the folder icon
        int drawingSize = activity.getDeviceProfile().folderIconSizePx;
        int foregroundSize = Math.max(requestedSize, drawingSize);
        float shift = foregroundSize - requestedSize;

        Picture background = new Picture();
        Picture foreground = new Picture();
        Picture badge = new Picture();

        Canvas bgCanvas = background.beginRecording(requestedSize, requestedSize);
        Canvas badgeCanvas = badge.beginRecording(requestedSize, requestedSize);

        Canvas fgCanvas = foreground.beginRecording(foregroundSize, foregroundSize);
        fgCanvas.translate(shift, shift);

        // Do not clip the folder drawing since the icon previews extend outside the background.
        Path mask = new Path();
        mask.addRect(-shift, -shift, requestedSize + shift, requestedSize + shift,
                Direction.CCW);

        // Initialize the actual draw commands on the UI thread to avoid race conditions with
        // FolderIcon draw pass
        try {
            MAIN_EXECUTOR.submit(() -> {
                FolderIcon icon = activity.findFolderIcon(folderId);
                if (icon == null) {
                    throw new IllegalArgumentException("Folder not found with id: " + folderId);
                }
                initLayersOnUiThread(icon, requestedSize, bgCanvas, fgCanvas, badgeCanvas);
            }).get();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create folder icon", e);
            return null;
        } finally {
            background.endRecording();
            foreground.endRecording();
            badge.endRecording();
        }

        // Only convert foreground to a bitmap as it can contain multiple draw commands. Other
        // layers either draw a nothing or a single draw call.
        Bitmap fgBitmap = Bitmap.createBitmap(foreground);
        Paint foregroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Do not use PictureDrawable as it moves the picture to the canvas bounds, whereas we want
        // to draw it at (0,0)
        return new FolderAdaptiveIcon(
                new BitmapRendererDrawable(c -> c.drawPicture(background)),
                new BitmapRendererDrawable(
                        c -> c.drawBitmap(fgBitmap, -shift, -shift, foregroundPaint)),
                new BitmapRendererDrawable(c -> c.drawPicture(badge)),
                mask);
    }

    @UiThread
    private static void initLayersOnUiThread(FolderIcon icon, int size,
            Canvas backgroundCanvas, Canvas foregroundCanvas, Canvas badgeCanvas) {
        icon.getPreviewBounds(sTmpRect);
        final int previewSize = sTmpRect.width();

        PreviewBackground bg = icon.getFolderBackground();
        final int margin = (size - previewSize) / 2;
        final float previewShiftX = -sTmpRect.left + margin;
        final float previewShiftY = -sTmpRect.top + margin;

        // Initialize badge, which consists of the outline stroke, shadow and dot; these
        // must be rendered above the foreground
        badgeCanvas.save();
        badgeCanvas.translate(previewShiftX, previewShiftY);
        icon.drawDot(badgeCanvas);
        badgeCanvas.restore();

        // Draw foreground
        foregroundCanvas.save();
        foregroundCanvas.translate(previewShiftX, previewShiftY);
        icon.getPreviewItemManager().draw(foregroundCanvas);
        foregroundCanvas.restore();

        // Draw background
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(bg.getBgColor());
        bg.drawShadow(backgroundCanvas);
        backgroundCanvas.drawCircle(size / 2f, size / 2f, bg.getRadius(), backgroundPaint);
        bg.drawBackgroundStroke(backgroundCanvas);
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

    private static class BitmapRendererDrawable extends Drawable {

        private final BitmapRenderer mRenderer;

        BitmapRendererDrawable(BitmapRenderer renderer) {
            mRenderer = renderer;
        }

        @Override
        public void draw(Canvas canvas) {
            mRenderer.draw(canvas);
        }

        @Override
        public void setAlpha(int i) { }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {  }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public ConstantState getConstantState() {
            return new MyConstantState(mRenderer);
        }

        private static class MyConstantState extends ConstantState {
            private final BitmapRenderer mRenderer;

            MyConstantState(BitmapRenderer renderer) {
                mRenderer = renderer;
            }

            @Override
            public Drawable newDrawable() {
                return new BitmapRendererDrawable(mRenderer);
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        }
    }
}
