/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Canvas;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.content.res.Resources;
import android.content.Context;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
final class Utilities {
    private static int sIconWidth = -1;
    private static int sIconHeight = -1;

    private static final Paint sPaint = new Paint();
    private static final Rect sBounds = new Rect();
    private static final Rect sOldBounds = new Rect();
    private static Canvas sCanvas = new Canvas();

    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));
    }

    static Bitmap centerToFit(Bitmap bitmap, int width, int height, Context context) {
        final int bitmapWidth = bitmap.getWidth();
        final int bitmapHeight = bitmap.getHeight();

        if (bitmapWidth < width || bitmapHeight < height) {
            int color = context.getResources().getColor(R.color.window_background);

            Bitmap centered = Bitmap.createBitmap(bitmapWidth < width ? width : bitmapWidth,
                    bitmapHeight < height ? height : bitmapHeight, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(centered);
            canvas.drawColor(color);
            canvas.drawBitmap(bitmap, (width - bitmapWidth) / 2.0f, (height - bitmapHeight) / 2.0f,
                    null);

            bitmap = centered;
        }

        return bitmap;
    }

    /**
     * Returns a Drawable representing the thumbnail of the specified Drawable.
     * The size of the thumbnail is defined by the dimension
     * android.R.dimen.launcher_application_icon_size.
     *
     * This method is not thread-safe and should be invoked on the UI thread only.
     *
     * @param icon The icon to get a thumbnail of.
     * @param context The application's context.
     *
     * @return A thumbnail for the specified icon or the icon itself if the
     *         thumbnail could not be created. 
     */
    static Drawable createIconThumbnail(Drawable icon, Context context) {
        if (sIconWidth == -1) {
            final Resources resources = context.getResources();
            sIconWidth = sIconHeight = (int) resources.getDimension(
                    android.R.dimen.app_icon_size);
        }

        int width = sIconWidth;
        int height = sIconHeight;

        final int iconWidth = icon.getIntrinsicWidth();
        final int iconHeight = icon.getIntrinsicHeight();

        if (icon instanceof PaintDrawable) {
            PaintDrawable painter = (PaintDrawable) icon;
            painter.setIntrinsicWidth(width);
            painter.setIntrinsicHeight(height);
        }

        if (width > 0 && height > 0) {
            if (width < iconWidth || height < iconHeight) {
                final float ratio = (float) iconWidth / iconHeight;

                if (iconWidth > iconHeight) {
                    height = (int) (width / ratio);
                } else if (iconHeight > iconWidth) {
                    width = (int) (height * ratio);
                }

                final Bitmap.Config c = icon.getOpacity() != PixelFormat.OPAQUE ?
                            Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
                final Bitmap thumb = Bitmap.createBitmap(sIconWidth, sIconHeight, c);
                final Canvas canvas = sCanvas;
                canvas.setBitmap(thumb);
                // Copy the old bounds to restore them later
                // If we were to do oldBounds = icon.getBounds(),
                // the call to setBounds() that follows would
                // change the same instance and we would lose the
                // old bounds
                sOldBounds.set(icon.getBounds());
                final int x = (sIconWidth - width) / 2;
                final int y = (sIconHeight - height) / 2;
                icon.setBounds(x, y, x + width, y + height);
                icon.draw(canvas);
                icon.setBounds(sOldBounds);
                icon = new FastBitmapDrawable(thumb);
            } else if (iconWidth < width && iconHeight < height) {
                final Bitmap.Config c = Bitmap.Config.ARGB_8888;
                final Bitmap thumb = Bitmap.createBitmap(sIconWidth, sIconHeight, c);
                final Canvas canvas = sCanvas;
                canvas.setBitmap(thumb);
                sOldBounds.set(icon.getBounds());
                final int x = (width - iconWidth) / 2;
                final int y = (height - iconHeight) / 2;
                icon.setBounds(x, y, x + iconWidth, y + iconHeight);
                icon.draw(canvas);
                icon.setBounds(sOldBounds);
                icon = new FastBitmapDrawable(thumb);
            }
        }

        return icon;
    }

    /**
     * Returns a Bitmap representing the thumbnail of the specified Bitmap.
     * The size of the thumbnail is defined by the dimension
     * android.R.dimen.launcher_application_icon_size.
     *
     * This method is not thread-safe and should be invoked on the UI thread only.
     *
     * @param bitmap The bitmap to get a thumbnail of.
     * @param context The application's context.
     *
     * @return A thumbnail for the specified bitmap or the bitmap itself if the
     *         thumbnail could not be created.
     */
    static Bitmap createBitmapThumbnail(Bitmap bitmap, Context context) {
        if (sIconWidth == -1) {
            final Resources resources = context.getResources();
            sIconWidth = sIconHeight = (int) resources.getDimension(
                    android.R.dimen.app_icon_size);
        }

        int width = sIconWidth;
        int height = sIconHeight;

        final int bitmapWidth = bitmap.getWidth();
        final int bitmapHeight = bitmap.getHeight();

        if (width > 0 && height > 0 && (width < bitmapWidth || height < bitmapHeight)) {
            final float ratio = (float) bitmapWidth / bitmapHeight;

            if (bitmapWidth > bitmapHeight) {
                height = (int) (width / ratio);
            } else if (bitmapHeight > bitmapWidth) {
                width = (int) (height * ratio);
            }

            final Bitmap.Config c = (width == sIconWidth && height == sIconHeight) ?
                    bitmap.getConfig() : Bitmap.Config.ARGB_8888;
            final Bitmap thumb = Bitmap.createBitmap(sIconWidth, sIconHeight, c);
            final Canvas canvas = sCanvas;
            final Paint paint = sPaint;
            canvas.setBitmap(thumb);
            paint.setDither(false);
            paint.setFilterBitmap(true);
            sBounds.set((sIconWidth - width) / 2, (sIconHeight - height) / 2, width, height);
            sOldBounds.set(0, 0, bitmapWidth, bitmapHeight);
            canvas.drawBitmap(bitmap, sOldBounds, sBounds, paint);
            return thumb;
        }

        return bitmap;
    }
}
