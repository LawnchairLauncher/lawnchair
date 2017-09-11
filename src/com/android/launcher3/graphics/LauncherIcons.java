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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;

import com.android.launcher3.AppInfo;
import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.Provider;

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
                return createIconBitmap(resources.getDrawableForDensity(
                        id, LauncherAppState.getIDP(context).fillResIconDpi), context);
            }
        } catch (Exception e) {
            // Icon not found.
        }
        return null;
    }

    /**
     * Returns a bitmap which is of the appropriate size to be displayed as an icon
     */
    public static Bitmap createIconBitmap(Bitmap icon, Context context) {
        final int iconBitmapSize = LauncherAppState.getIDP(context).iconBitmapSize;
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
            Drawable icon, UserHandle user, Context context, int iconAppTargetSdk) {

        IconNormalizer normalizer;
        float scale = 1f;
        if (!FeatureFlags.LAUNCHER3_DISABLE_ICON_NORMALIZATION) {
            normalizer = IconNormalizer.getInstance(context);
            if (Utilities.ATLEAST_OREO && iconAppTargetSdk >= Build.VERSION_CODES.O) {
                boolean[] outShape = new boolean[1];
                AdaptiveIconDrawable dr = (AdaptiveIconDrawable)
                        context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
                dr.setBounds(0, 0, 1, 1);
                scale = normalizer.getScale(icon, null, dr.getIconMask(), outShape);
                if (FeatureFlags.LEGACY_ICON_TREATMENT &&
                        !outShape[0]){
                    Drawable wrappedIcon = wrapToAdaptiveIconDrawable(context, icon, scale);
                    if (wrappedIcon != icon) {
                        icon = wrappedIcon;
                        scale = normalizer.getScale(icon, null, null, null);
                    }
                }
            } else {
                scale = normalizer.getScale(icon, null, null, null);
            }
        }
        Bitmap bitmap = createIconBitmap(icon, context, scale);
        if (FeatureFlags.ADAPTIVE_ICON_SHADOW && Utilities.ATLEAST_OREO &&
                icon instanceof AdaptiveIconDrawable) {
            bitmap = ShadowGenerator.getInstance(context).recreateIcon(bitmap);
        }
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
    public static Bitmap createScaledBitmapWithoutShadow(Drawable icon, Context context, int iconAppTargetSdk) {
        RectF iconBounds = new RectF();
        IconNormalizer normalizer;
        float scale = 1f;
        if (!FeatureFlags.LAUNCHER3_DISABLE_ICON_NORMALIZATION) {
            normalizer = IconNormalizer.getInstance(context);
            if (Utilities.ATLEAST_OREO && iconAppTargetSdk >= Build.VERSION_CODES.O) {
                boolean[] outShape = new boolean[1];
                AdaptiveIconDrawable dr = (AdaptiveIconDrawable)
                        context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
                dr.setBounds(0, 0, 1, 1);
                scale = normalizer.getScale(icon, iconBounds, dr.getIconMask(), outShape);
                if (Utilities.ATLEAST_OREO && FeatureFlags.LEGACY_ICON_TREATMENT &&
                        !outShape[0]) {
                    Drawable wrappedIcon = wrapToAdaptiveIconDrawable(context, icon, scale);
                    if (wrappedIcon != icon) {
                        icon = wrappedIcon;
                        scale = normalizer.getScale(icon, iconBounds, null, null);
                    }
                }
            } else {
                scale = normalizer.getScale(icon, iconBounds, null, null);
            }

        }
        scale = Math.min(scale, ShadowGenerator.getScaleForBounds(iconBounds));
        return createIconBitmap(icon, context, scale);
    }

    /**
     * Adds a shadow to the provided icon. It assumes that the icon has already been scaled using
     * {@link #createScaledBitmapWithoutShadow(Drawable, Context, int)}
     */
    public static Bitmap addShadowToIcon(Bitmap icon, Context context) {
        return ShadowGenerator.getInstance(context).recreateIcon(icon);
    }

    /**
     * Adds the {@param badge} on top of {@param srcTgt} using the badge dimensions.
     */
    public static Bitmap badgeWithBitmap(Bitmap srcTgt, Bitmap badge, Context context) {
        return badgeWithDrawable(srcTgt, new FastBitmapDrawable(badge), context);
    }

    public static Bitmap badgeWithDrawable(Bitmap srcTgt, Drawable badge, Context context) {
        int badgeSize = context.getResources().getDimensionPixelSize(R.dimen.profile_badge_size);
        synchronized (sCanvas) {
            sCanvas.setBitmap(srcTgt);
            int iconSize = srcTgt.getWidth();
            badge.setBounds(iconSize - badgeSize, iconSize - badgeSize, iconSize, iconSize);
            badge.draw(sCanvas);
            sCanvas.setBitmap(null);
        }
        return srcTgt;
    }

    /**
     * Returns a bitmap suitable for the all apps view.
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context) {
        float scale = 1f;
        if (FeatureFlags.ADAPTIVE_ICON_SHADOW && Utilities.ATLEAST_OREO &&
                icon instanceof AdaptiveIconDrawable) {
            scale = ShadowGenerator.getScaleForBounds(new RectF(0, 0, 0, 0));
        }
        Bitmap bitmap =  createIconBitmap(icon, context, scale);
        if (FeatureFlags.ADAPTIVE_ICON_SHADOW && Utilities.ATLEAST_OREO &&
                icon instanceof AdaptiveIconDrawable) {
            bitmap = ShadowGenerator.getInstance(context).recreateIcon(bitmap);
        }
        return bitmap;
    }

    /**
     * @param scale the scale to apply before drawing {@param icon} on the canvas
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context, float scale) {
        synchronized (sCanvas) {
            final int iconBitmapSize = LauncherAppState.getIDP(context).iconBitmapSize;
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

            Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = (textureHeight-height) / 2;

            sOldBounds.set(icon.getBounds());
            if (Utilities.ATLEAST_OREO && icon instanceof AdaptiveIconDrawable) {
                int offset = Math.max((int)(ShadowGenerator.BLUR_FACTOR * iconBitmapSize),
                        Math.min(left, top));
                int size = Math.max(width, height);
                icon.setBounds(offset, offset, size, size);
            } else {
                icon.setBounds(left, top, left+width, top+height);
            }
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.scale(scale, scale, textureWidth / 2, textureHeight / 2);
            icon.draw(canvas);
            canvas.restore();
            icon.setBounds(sOldBounds);
            canvas.setBitmap(null);

            return bitmap;
        }
    }

    /**
     * If the platform is running O but the app is not providing AdaptiveIconDrawable, then
     * shrink the legacy icon and set it as foreground. Use color drawable as background to
     * create AdaptiveIconDrawable.
     */
    static Drawable wrapToAdaptiveIconDrawable(Context context, Drawable drawable, float scale) {
        if (!(FeatureFlags.LEGACY_ICON_TREATMENT && Utilities.ATLEAST_OREO)) {
            return drawable;
        }

        try {
            if (!(drawable instanceof AdaptiveIconDrawable)) {
                AdaptiveIconDrawable iconWrapper = (AdaptiveIconDrawable)
                        context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
                FixedScaleDrawable fsd = ((FixedScaleDrawable) iconWrapper.getForeground());
                fsd.setDrawable(drawable);
                fsd.setScale(scale);
                return (Drawable) iconWrapper;
            }
        } catch (Exception e) {
            return drawable;
        }
        return drawable;
    }

    public static Bitmap createShortcutIcon(ShortcutInfoCompat shortcutInfo, Context context) {
        return createShortcutIcon(shortcutInfo, context, true /* badged */);
    }

    public static Bitmap createShortcutIcon(ShortcutInfoCompat shortcutInfo, Context context,
            boolean badged) {
        return createShortcutIcon(shortcutInfo, context, badged, null);
    }

    public static Bitmap createShortcutIcon(ShortcutInfoCompat shortcutInfo, Context context,
            final Bitmap fallbackIcon) {
        Provider<Bitmap> fallbackIconProvider = new Provider<Bitmap>() {
            @Override
            public Bitmap get() {
                // If the shortcut is pinned but no longer has an icon in the system,
                // keep the current icon instead of reverting to the default icon.
                return fallbackIcon;
            }
        };
        return createShortcutIcon(shortcutInfo, context, true, fallbackIconProvider);
    }

    public static Bitmap createShortcutIcon(ShortcutInfoCompat shortcutInfo, Context context,
            boolean badged, @Nullable Provider<Bitmap> fallbackIconProvider) {
        LauncherAppState app = LauncherAppState.getInstance(context);
        Drawable unbadgedDrawable = DeepShortcutManager.getInstance(context)
                .getShortcutIconDrawable(shortcutInfo,
                        app.getInvariantDeviceProfile().fillResIconDpi);
        IconCache cache = app.getIconCache();
        Bitmap unbadgedBitmap = null;
        if (unbadgedDrawable != null) {
            unbadgedBitmap = LauncherIcons.createScaledBitmapWithoutShadow(
                    unbadgedDrawable, context, 0);
        } else {
            if (fallbackIconProvider != null) {
                unbadgedBitmap = fallbackIconProvider.get();
            }
            if (unbadgedBitmap == null) {
                unbadgedBitmap = cache.getDefaultIcon(Process.myUserHandle());
            }
        }

        if (!badged) {
            return unbadgedBitmap;
        }
        unbadgedBitmap = LauncherIcons.addShadowToIcon(unbadgedBitmap, context);
        return badgeWithBitmap(unbadgedBitmap, getShortcutInfoBadge(shortcutInfo, cache), context);
    }

    public static Bitmap getShortcutInfoBadge(ShortcutInfoCompat shortcutInfo, IconCache cache) {
        final Bitmap badgeBitmap;
        ComponentName cn = shortcutInfo.getActivity();
        if (cn != null) {
            // Get the app info for the source activity.
            AppInfo appInfo = new AppInfo();
            appInfo.user = shortcutInfo.getUserHandle();
            appInfo.componentName = cn;
            appInfo.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn);
            cache.getTitleAndIcon(appInfo, false);
            badgeBitmap = appInfo.iconBitmap;
        } else {
            PackageItemInfo pkgInfo = new PackageItemInfo(shortcutInfo.getPackage());
            cache.getTitleAndIconForApp(pkgInfo, false);
            badgeBitmap = pkgInfo.iconBitmap;
        }
        return badgeBitmap;
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
