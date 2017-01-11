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
import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.config.ProviderConfig;

import java.lang.reflect.Method;

/**
 * Helper methods for generating various launcher icons
 */
public class LauncherIcons {

    private static final Rect sOldBounds = new Rect();
    private static final Canvas sCanvas = new Canvas();

    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));
    }


    public static Bitmap createIconBitmap(Cursor c, int iconIndex, Context context) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return createIconBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), context);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns a bitmap suitable for the all apps view. If the package or the resource do not
     * exist, it returns null.
     */
    public static Bitmap createIconBitmap(ShortcutIconResource iconRes, Context context) {
        PackageManager packageManager = context.getPackageManager();
        // the resource
        try {
            Resources resources = packageManager.getResourcesForApplication(iconRes.packageName);
            if (resources != null) {
                final int id = resources.getIdentifier(iconRes.resourceName, null, null);
                return createIconBitmap(
                        resources.getDrawableForDensity(id, LauncherAppState.getInstance()
                                .getInvariantDeviceProfile().fillResIconDpi), context);
            }
        } catch (Exception e) {
            // Icon not found.
        }
        return null;
    }

    private static int getIconBitmapSize() {
        return LauncherAppState.getInstance().getInvariantDeviceProfile().iconBitmapSize;
    }

    /**
     * Returns a bitmap which is of the appropriate size to be displayed as an icon
     */
    public static Bitmap createIconBitmap(Bitmap icon, Context context) {
        final int iconBitmapSize = getIconBitmapSize();
        if (iconBitmapSize == icon.getWidth() && iconBitmapSize == icon.getHeight()) {
            return icon;
        }
        return createIconBitmap(new BitmapDrawable(context.getResources(), icon), context);
    }

    /**
     * Returns a bitmap suitable for the all apps view. The icon is badged for {@param user}.
     * The bitmap is also visually normalized with other icons.
     */
    public static Bitmap createBadgedIconBitmap(
            Drawable icon, UserHandle user, Context context) {
        float scale = FeatureFlags.LAUNCHER3_DISABLE_ICON_NORMALIZATION ?
                1 : IconNormalizer.getInstance().getScale(icon, null);
        Bitmap bitmap = createIconBitmap(icon, context, scale);
        return badgeIconForUser(bitmap, user, context);
    }

    /**
     * Badges the provided icon with the user badge if required.
     */
    public static Bitmap badgeIconForUser(Bitmap icon, UserHandle user, Context context) {
        if (user != null && !Process.myUserHandle().equals(user)) {
            BitmapDrawable drawable = new FixedSizeBitmapDrawable(icon);
            Drawable badged = context.getPackageManager().getUserBadgedIcon(
                    drawable, user);
            if (badged instanceof BitmapDrawable) {
                return ((BitmapDrawable) badged).getBitmap();
            } else {
                return createIconBitmap(badged, context);
            }
        } else {
            return icon;
        }
    }

    /**
     * Creates a normalized bitmap suitable for the all apps view. The bitmap is also visually
     * normalized with other icons and has enough spacing to add shadow.
     */
    public static Bitmap createScaledBitmapWithoutShadow(Drawable icon, Context context) {
        RectF iconBounds = new RectF();
        float scale = FeatureFlags.LAUNCHER3_DISABLE_ICON_NORMALIZATION ?
                1 : IconNormalizer.getInstance().getScale(icon, iconBounds);
        scale = Math.min(scale, ShadowGenerator.getScaleForBounds(iconBounds));
        return createIconBitmap(icon, context, scale);
    }

    /**
     * Adds a shadow to the provided icon. It assumes that the icon has already been scaled using
     * {@link #createScaledBitmapWithoutShadow(Drawable, Context)}
     */
    public static Bitmap addShadowToIcon(Bitmap icon) {
        return ShadowGenerator.getInstance().recreateIcon(icon);
    }

    /**
     * Adds the {@param badge} on top of {@param srcTgt} using the badge dimensions.
     */
    public static Bitmap badgeWithBitmap(Bitmap srcTgt, Bitmap badge, Context context) {
        int badgeSize = context.getResources().getDimensionPixelSize(R.dimen.profile_badge_size);
        synchronized (sCanvas) {
            sCanvas.setBitmap(srcTgt);
            sCanvas.drawBitmap(badge, new Rect(0, 0, badge.getWidth(), badge.getHeight()),
                    new Rect(srcTgt.getWidth() - badgeSize,
                            srcTgt.getHeight() - badgeSize, srcTgt.getWidth(), srcTgt.getHeight()),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            sCanvas.setBitmap(null);
        }
        return srcTgt;
    }

    /**
     * Returns a bitmap suitable for the all apps view.
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context) {
        return createIconBitmap(icon, context, 1.0f /* scale */);
    }

    /**
     * @param scale the scale to apply before drawing {@param icon} on the canvas
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context, float scale) {
        icon = castToMaskableIconDrawable(icon);
        synchronized (sCanvas) {
            final int iconBitmapSize = getIconBitmapSize();

            int width = iconBitmapSize;
            int height = iconBitmapSize;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null && bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int sourceWidth = icon.getIntrinsicWidth();
            int sourceHeight = icon.getIntrinsicHeight();
            if (sourceWidth > 0 && sourceHeight > 0) {
                // Scale the icon proportionally to the icon dimensions
                final float ratio = (float) sourceWidth / sourceHeight;
                if (sourceWidth > sourceHeight) {
                    height = (int) (width / ratio);
                } else if (sourceHeight > sourceWidth) {
                    width = (int) (height * ratio);
                }
            }

            // no intrinsic size --> use default size
            int textureWidth = iconBitmapSize;
            int textureHeight = iconBitmapSize;

            final Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = (textureHeight-height) / 2;

            sOldBounds.set(icon.getBounds());
            icon.setBounds(left, top, left+width, top+height);
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.scale(scale, scale, textureWidth / 2, textureHeight / 2);
            icon.draw(canvas);
            canvas.restore();
            icon.setBounds(sOldBounds);
            canvas.setBitmap(null);

            return bitmap;
        }
    }

    static Drawable castToMaskableIconDrawable(Drawable drawable) {
        if (!(ProviderConfig.IS_DOGFOOD_BUILD && Utilities.isAtLeastO())) {
            return drawable;
        }
        try {
            Class clazz = Class.forName("android.graphics.drawable.MaskableIconDrawable");
            Method method = clazz.getDeclaredMethod("wrap", Drawable.class);
            return (Drawable) method.invoke(null, drawable);
        } catch (Exception e) {
            return drawable;
        }
    }

    /**
     * An extension of {@link BitmapDrawable} which returns the bitmap pixel size as intrinsic size.
     * This allows the badging to be done based on the action bitmap size rather than
     * the scaled bitmap size.
     */
    private static class FixedSizeBitmapDrawable extends BitmapDrawable {

        public FixedSizeBitmapDrawable(Bitmap bitmap) {
            super(null, bitmap);
        }

        @Override
        public int getIntrinsicHeight() {
            return getBitmap().getWidth();
        }

        @Override
        public int getIntrinsicWidth() {
            return getBitmap().getWidth();
        }
    }
}
