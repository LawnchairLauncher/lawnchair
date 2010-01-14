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

package com.android.launcher2;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.TableMaskFilter;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.content.res.Resources;
import android.content.Context;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
final class Utilities {
    private static final String TAG = "Launcher.Utilities";

    private static final boolean TEXT_BURN = false;

    private static int sIconWidth = -1;
    private static int sIconHeight = -1;
    private static int sIconTextureWidth = -1;
    private static int sIconTextureHeight = -1;

    private static int sTitleMargin = -1;
    private static float sBlurRadius = -1;
    private static Rect sIconTextureRect;

    private static final Paint sPaint = new Paint();
    private static final Paint sBlurPaint = new Paint();
    private static final Paint sGlowColorPressedPaint = new Paint();
    private static final Paint sGlowColorFocusedPaint = new Paint();
    private static final Paint sEmptyPaint = new Paint();
    private static final Rect sBounds = new Rect();
    private static final Rect sOldBounds = new Rect();
    private static final Canvas sCanvas = new Canvas();

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
            centered.setDensity(bitmap.getDensity());
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
     * @param icon The icon to get a thumbnail of.
     * @param context The application's context.
     *
     * @return A thumbnail for the specified icon or the icon itself if the
     *         thumbnail could not be created.
     */
    static Drawable createIconThumbnail(Drawable icon, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }

            int width = sIconWidth;
            int height = sIconHeight;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int iconWidth = icon.getIntrinsicWidth();
            int iconHeight = icon.getIntrinsicHeight();

            if (iconWidth > 0 && iconHeight > 0) {
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
    }

    static int sColors[] = { 0xffff0000, 0xff00ff00, 0xff0000ff };
    static int sColorIndex = 0;

    /**
     * Returns a bitmap suitable for the all apps view.  The bitmap will be a power
     * of two sized ARGB_8888 bitmap that can be used as a gl texture.
     */
    static Bitmap createAllAppsBitmap(Drawable icon, String title, BubbleText bubble,
            Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }

            int width = sIconWidth;
            int height = sIconHeight;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int sourceWidth = icon.getIntrinsicWidth();
            int sourceHeight = icon.getIntrinsicHeight();

            if (sourceWidth > 0 && sourceWidth > 0) {
                // There are intrinsic sizes.
                if (width < sourceWidth || height < sourceHeight) {
                    // It's too big, scale it down.
                    final float ratio = (float) sourceWidth / sourceHeight;
                    if (sourceWidth > sourceHeight) {
                        height = (int) (width / ratio);
                    } else if (sourceHeight > sourceWidth) {
                        width = (int) (height * ratio);
                    }
                } else if (sourceWidth < width && sourceHeight < height) {
                    // It's small, use the size they gave us.
                    width = sourceWidth;
                    height = sourceHeight;
                }
            }

            // no intrinsic size --> use default size
            final int textureWidth = sIconTextureWidth;
            final int textureHeight = sIconTextureHeight;

            final Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = sIconTextureRect.top;

            if (false) {
                // draw a big box for the icon for debugging
                canvas.drawColor(sColors[sColorIndex]);
                if (++sColorIndex >= sColors.length) sColorIndex = 0;
                Paint debugPaint = new Paint();
                debugPaint.setColor(0xffcccc00);
                canvas.drawRect(left, top, left+width, top+height, debugPaint);
            }

            sOldBounds.set(icon.getBounds());
            icon.setBounds(left, top, left+width, top+height);
            icon.draw(canvas);
            icon.setBounds(sOldBounds);

            if (title != null) {
                bubble.drawText(canvas, title);
            }

            return bitmap;
        }
    }

    static Bitmap extractIconFromTexture(Bitmap src, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }
            final Bitmap bitmap = Bitmap.createBitmap(sIconWidth, sIconHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            Rect r = new Rect(0, 0, sIconWidth, sIconHeight);
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(src, sIconTextureRect, r, sEmptyPaint);

            return bitmap;
        }
    }

    static void drawSelectedAllAppsBitmap(Canvas dest, Bitmap destBitmap,
            int destWidth, int destHeight, boolean pressed, Bitmap src) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                // We can't have gotten to here without src being initialized, which
                // comes from this file already.  So just assert.
                //initStatics(context);
                throw new RuntimeException("Assertion failed: Utilities not initialized");
            }

            dest.drawColor(0, PorterDuff.Mode.CLEAR);
            dest.drawBitmap(src, sIconTextureRect, sIconTextureRect, sEmptyPaint);

            int[] xy = new int[2];
            Bitmap mask = destBitmap.extractAlpha(sBlurPaint, xy);

            float px = (destWidth - src.getWidth()) / 2;
            float py = (destHeight - src.getHeight()) / 2;
            dest.drawColor(0, PorterDuff.Mode.CLEAR);
            dest.drawBitmap(mask, px + xy[0], py + xy[1],
                    pressed ? sGlowColorPressedPaint : sGlowColorFocusedPaint);

            mask.recycle();
        }
    }

    /**
     * Returns a Bitmap representing the thumbnail of the specified Bitmap.
     * The size of the thumbnail is defined by the dimension
     * android.R.dimen.launcher_application_icon_size.
     *
     * @param bitmap The bitmap to get a thumbnail of.
     * @param context The application's context.
     *
     * @return A thumbnail for the specified bitmap or the bitmap itself if the
     *         thumbnail could not be created.
     */
    static Bitmap createBitmapThumbnail(Bitmap bitmap, Context context) {
        synchronized (sCanvas) { // we share the statics :-(
            if (sIconWidth == -1) {
                initStatics(context);
            }

            int width = sIconWidth;
            int height = sIconHeight;

            final int bitmapWidth = bitmap.getWidth();
            final int bitmapHeight = bitmap.getHeight();

            if (width > 0 && height > 0) {
                if (width < bitmapWidth || height < bitmapHeight) {
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
                } else if (bitmapWidth < width || bitmapHeight < height) {
                    final Bitmap.Config c = Bitmap.Config.ARGB_8888;
                    final Bitmap thumb = Bitmap.createBitmap(sIconWidth, sIconHeight, c);
                    final Canvas canvas = sCanvas;
                    final Paint paint = sPaint;
                    canvas.setBitmap(thumb);
                    paint.setDither(false);
                    paint.setFilterBitmap(true);
                    canvas.drawBitmap(bitmap, (sIconWidth - bitmapWidth) / 2,
                            (sIconHeight - bitmapHeight) / 2, paint);
                    return thumb;
                }
            }

            return bitmap;
        }
    }

    private static void initStatics(Context context) {
        final Resources resources = context.getResources();
        final DisplayMetrics metrics = resources.getDisplayMetrics();
        final float density = metrics.density;

        sIconWidth = sIconHeight = (int) resources.getDimension(android.R.dimen.app_icon_size);
        sIconTextureWidth = sIconTextureHeight = roundToPow2(sIconWidth);

        sTitleMargin = (int)(1 * density);
        sBlurRadius = 5 * density;
        final int left = (sIconTextureWidth-sIconWidth)/2;
        final int top = (int)(sBlurRadius) + 1;
        sIconTextureRect = new Rect(left, top, left+sIconWidth, top+sIconHeight);

        sBlurPaint.setMaskFilter(new BlurMaskFilter(5 * density, BlurMaskFilter.Blur.NORMAL));
        sGlowColorPressedPaint.setColor(0xffffc300);
        sGlowColorPressedPaint.setMaskFilter(TableMaskFilter.CreateClipTable(0, 30));
        sGlowColorFocusedPaint.setColor(0xffff8e00);
        sGlowColorFocusedPaint.setMaskFilter(TableMaskFilter.CreateClipTable(0, 30));
    }

    static class BubbleText {
        private static final int MAX_LINES = 2;
        private TextPaint mTextPaint;

        private float mBubblePadding;
        private RectF mBubbleRect = new RectF();

        private float mTextWidth;
        private int mLeading;
        private int mFirstLineY;
        private int mLineHeight;

        private int mBitmapWidth;
        private int mBitmapHeight;

        BubbleText(Context context) {
            synchronized (sCanvas) { // we share the statics :-(
                if (sIconWidth == -1) {
                    initStatics(context);
                }
                final Resources resources = context.getResources();

                final float scale = resources.getDisplayMetrics().density;

                final float paddingLeft = 5.0f * scale;
                final float paddingRight = 5.0f * scale;
                final float cellWidth = resources.getDimension(R.dimen.workspace_cell_width);
                final float bubbleWidth = cellWidth - paddingLeft - paddingRight;
                mBubblePadding = 3.0f * scale;

                RectF bubbleRect = mBubbleRect;
                bubbleRect.left = 0;
                bubbleRect.top = 0;
                bubbleRect.right = (int)(bubbleWidth+0.5f);

                mTextWidth = bubbleWidth - mBubblePadding - mBubblePadding;

                Paint rectPaint = new Paint();
                rectPaint.setColor(0xff000000);
                rectPaint.setAntiAlias(true);

                TextPaint textPaint = mTextPaint = new TextPaint();
                textPaint.setTypeface(Typeface.DEFAULT);
                textPaint.setTextSize(13*scale);
                //textPaint.setColor(0xff00ff00);
                textPaint.setColor(0xffffffff);
                textPaint.setAntiAlias(true);
                if (TEXT_BURN) {
                    textPaint.setShadowLayer(8, 0, 0, 0xff000000);
                }

                final int iconTop = (int)(sBlurRadius) + 1;
                final int iconBottom = iconTop + sIconHeight;

                float ascent = -textPaint.ascent();
                float descent = textPaint.descent();
                float leading = -1.0f;//(ascent+descent) * 0.1f;
                mLeading = (int)(leading + 0.5f);
                mFirstLineY = (int)(iconBottom + sTitleMargin + ascent + 0.5f);
                mLineHeight = (int)(leading + ascent + descent + 0.5f);

                mBitmapWidth = roundToPow2((int)(mBubbleRect.width() + 0.5f));
                mBitmapHeight = roundToPow2((int)((MAX_LINES * mLineHeight) + leading + 0.5f));

                mBubbleRect.offsetTo((mBitmapWidth-mBubbleRect.width())/2, 0);

                if (false) {
                    Log.d(TAG, "mBitmapWidth=" + mBitmapWidth + " mBitmapHeight="
                            + mBitmapHeight + " w=" + ((int)(mBubbleRect.width() + 0.5f))
                            + " h=" + ((int)((MAX_LINES * mLineHeight) + leading + 0.5f)));
                }
            }
        }

        void drawText(Canvas c, String text) {
            StaticLayout layout = new StaticLayout(text, mTextPaint, (int)mTextWidth,
                    Alignment.ALIGN_CENTER, 1, 0, true);
            int lineCount = layout.getLineCount();
            if (lineCount > MAX_LINES) {
                lineCount = MAX_LINES;
            }
            //if (!TEXT_BURN && lineCount > 0) {
                //RectF bubbleRect = mBubbleRect;
                //bubbleRect.bottom = height(lineCount);
                //c.drawRoundRect(bubbleRect, mCornerRadius, mCornerRadius, mRectPaint);
            //}
            for (int i=0; i<lineCount; i++) {
                //int x = (int)((mBubbleRect.width() - layout.getLineMax(i)) / 2.0f);
                //int y = mFirstLineY + (i * mLineHeight);
                int x = (int)(mBubbleRect.left
                        + ((mBubbleRect.width() - layout.getLineMax(i)) / 2.0f));
                int y = mFirstLineY + (i * mLineHeight);
                c.drawText(text.substring(layout.getLineStart(i), layout.getLineEnd(i)),
                        x, y, mTextPaint);
            }
        }

        private int height(int lineCount) {
            return (int)((lineCount * mLineHeight) + mLeading + mLeading + 0.0f);
        }

        int getBubbleWidth() {
            return (int)(mBubbleRect.width() + 0.5f);
        }

        int getMaxBubbleHeight() {
            return height(MAX_LINES);
        }

        int getBitmapWidth() {
            return mBitmapWidth;
        }

        int getBitmapHeight() {
            return mBitmapHeight;
        }
    }

    /** Only works for positive numbers. */
    static int roundToPow2(int n) {
        int orig = n;
        n >>= 1;
        int mask = 0x8000000;
        while (mask != 0 && (n & mask) == 0) {
            mask >>= 1;
        }
        while (mask != 0) {
            n |= mask;
            mask >>= 1;
        }
        n += 1;
        if (n != orig) {
            n <<= 1;
        }
        return n;
    }
}
