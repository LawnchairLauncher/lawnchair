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
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.util.Log;
import ch.deletescape.lawnchair.NonAdaptiveIconDrawable;
import ch.deletescape.lawnchair.iconpack.AdaptiveIconCompat;
import ch.deletescape.lawnchair.iconpack.LawnchairIconProvider;
import com.android.launcher3.*;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.Provider;
import com.android.launcher3.util.Themes;

import static android.graphics.Paint.DITHER_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;
import static com.android.launcher3.graphics.ShadowGenerator.BLUR_FACTOR;

/**
 * Helper methods for generating various launcher icons
 */
public class LauncherIcons implements AutoCloseable {

    private static final int DEFAULT_WRAPPER_BACKGROUND = Color.WHITE;

    public static final Object sPoolSync = new Object();
    private static LauncherIcons sPool;

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
    public static LauncherIcons obtain(Context context) {
        synchronized (sPoolSync) {
            if (sPool != null) {
                LauncherIcons m = sPool;
                sPool = m.next;
                m.next = null;
                if (m.mWrapperIcon != null && !m.mWrapperIcon.isMaskValid()) {
                    m.mWrapperIcon = null;
                    m.mNormalizer.onAdaptiveShapeChanged();
                }
                return m;
            }
        }
        return new LauncherIcons(context);
    }

    /**
     * Recycles a LauncherIcons that may be in-use.
     */
    public void recycle() {
        synchronized (sPoolSync) {
            // Clear any temporary state variables
            mWrapperBackgroundColor = DEFAULT_WRAPPER_BACKGROUND;

            next = sPool;
            sPool = this;
        }
    }

    @Override
    public void close() {
        recycle();
    }

    private final Rect mOldBounds = new Rect();
    private final Context mContext;
    private final Canvas mCanvas;
    private final PackageManager mPm;

    private final int mFillResIconDpi;
    private final int mIconBitmapSize;

    private IconNormalizer mNormalizer;
    private ShadowGenerator mShadowGenerator;

    private AdaptiveIconCompat mWrapperIcon;
    private int mWrapperBackgroundColor = DEFAULT_WRAPPER_BACKGROUND;

    private IconProvider iconProvider;

    // sometimes we store linked lists of these things
    private LauncherIcons next;

    private LauncherIcons(Context context) {
        mContext = context.getApplicationContext();
        mPm = mContext.getPackageManager();

        iconProvider = IconProvider.newInstance(context);

        InvariantDeviceProfile idp = LauncherAppState.getIDP(mContext);
        mFillResIconDpi = idp.fillResIconDpi;
        mIconBitmapSize = idp.iconBitmapSize;

        mCanvas = new Canvas();
        mCanvas.setDrawFilter(new PaintFlagsDrawFilter(DITHER_FLAG, FILTER_BITMAP_FLAG));
    }

    public ShadowGenerator getShadowGenerator() {
        if (mShadowGenerator == null) {
            mShadowGenerator = new ShadowGenerator(mContext);
        }
        return mShadowGenerator;
    }

    public IconNormalizer getNormalizer() {
        if (mNormalizer == null) {
            mNormalizer = new IconNormalizer(mContext);
        }
        return mNormalizer;
    }

    /**
     * Returns a bitmap suitable for the all apps view. If the package or the resource do not
     * exist, it returns null.
     */
    public BitmapInfo createIconBitmap(ShortcutIconResource iconRes) {
        try {
            Resources resources = mPm.getResourcesForApplication(iconRes.packageName);
            if (resources != null) {
                final int id = resources.getIdentifier(iconRes.resourceName, null, null);
                Drawable drawable = resources.getDrawableForDensity(id, mFillResIconDpi);
                if (drawable != null) {
                    drawable = AdaptiveIconCompat.wrap(drawable);
                }
                // do not stamp old legacy shortcuts as the app may have already forgotten about it
                return createBadgedIconBitmap(
                        drawable,
                        Process.myUserHandle() /* only available on primary user */,
                        0 /* do not apply legacy treatment */);
            }
        } catch (Exception e) {
            // Icon not found.
        }
        return null;
    }

    /**
     * Returns a bitmap which is of the appropriate size to be displayed as an icon
     */
    public BitmapInfo createIconBitmap(Bitmap icon) {
        if (mIconBitmapSize == icon.getWidth() && mIconBitmapSize == icon.getHeight()) {
            return BitmapInfo.fromBitmap(icon);
        }
        return BitmapInfo.fromBitmap(
                createIconBitmap(new BitmapDrawable(mContext.getResources(), icon), 1f));
    }

    /**
     * Returns a bitmap suitable for displaying as an icon at various launcher UIs like all apps
     * view or workspace. The icon is badged for {@param user}.
     * The bitmap is also visually normalized with other icons.
     */
    public BitmapInfo createBadgedIconBitmap(Drawable icon, UserHandle user, int iconAppTargetSdk) {
        return createBadgedIconBitmap(icon, user, iconAppTargetSdk, false, null);
    }

    public BitmapInfo createBadgedIconBitmap(Drawable icon, UserHandle user, int iconAppTargetSdk,
            boolean isInstantApp) {
        return createBadgedIconBitmap(icon, user, iconAppTargetSdk, isInstantApp, null);
    }

    /**
     * Returns a bitmap suitable for displaying as an icon at various launcher UIs like all apps
     * view or workspace. The icon is badged for {@param user}.
     * The bitmap is also visually normalized with other icons.
     */
    public BitmapInfo createBadgedIconBitmap(Drawable icon, UserHandle user, int iconAppTargetSdk,
            boolean isInstantApp, float [] scale) {
        if (scale == null) {
            scale = new float[1];
        }
        icon = normalizeAndWrapToAdaptiveIcon(icon, iconAppTargetSdk, null, scale);
        Bitmap bitmap = createIconBitmap(icon, scale[0]);
        if (icon instanceof AdaptiveIconCompat) {
            mCanvas.setBitmap(bitmap);
            getShadowGenerator().recreateIcon(Bitmap.createBitmap(bitmap), mCanvas);
            mCanvas.setBitmap(null);
        }

        final Bitmap result;
        if (user != null && !Process.myUserHandle().equals(user)) {
            BitmapDrawable drawable = new FixedSizeBitmapDrawable(bitmap);
            Drawable badged = mPm.getUserBadgedIcon(drawable, user);
            if (badged instanceof BitmapDrawable) {
                result = ((BitmapDrawable) badged).getBitmap();
            } else {
                result = createIconBitmap(badged, 1f);
            }
        } else if (isInstantApp) {
            badgeWithDrawable(bitmap, mContext.getDrawable(R.drawable.ic_instant_app_badge));
            result = bitmap;
        } else {
            result = bitmap;
        }
        return BitmapInfo.fromBitmap(result);
    }

    /**
     * Creates a normalized bitmap suitable for the all apps view. The bitmap is also visually
     * normalized with other icons and has enough spacing to add shadow.
     */
    public Bitmap createScaledBitmapWithoutShadow(Drawable icon, int iconAppTargetSdk) {
        RectF iconBounds = new RectF();
        float[] scale = new float[1];
        icon = normalizeAndWrapToAdaptiveIcon(icon, iconAppTargetSdk, iconBounds, scale);
        return createIconBitmap(icon,
                Math.min(scale[0], ShadowGenerator.getScaleForBounds(iconBounds)));
    }

    /**
     * Sets the background color used for wrapped adaptive icon
     */
    public void setWrapperBackgroundColor(int color) {
        mWrapperBackgroundColor = (Color.alpha(color) < 255) ? DEFAULT_WRAPPER_BACKGROUND : color;
    }

    private Drawable normalizeAndWrapToAdaptiveIcon(Drawable icon, int iconAppTargetSdk,
            RectF outIconBounds, float[] outScale) {
        float scale = 1f;
        if (Utilities.ATLEAST_OREO) {
            boolean[] outShape = new boolean[1];
            if (mWrapperIcon == null || !mWrapperIcon.isMaskValid()) {
                mWrapperIcon = LawnchairIconProvider.getAdaptiveIconDrawableWrapper(mContext);
            }
            AdaptiveIconCompat dr = (AdaptiveIconCompat) mWrapperIcon;
            dr.setBounds(0, 0, 1, 1);
            scale = getNormalizer().getScale(icon, outIconBounds, dr.getIconMask(), outShape);
            if (!outShape[0] && (icon instanceof NonAdaptiveIconDrawable)) {
                FixedScaleDrawable fsd = ((FixedScaleDrawable) dr.getForeground());
                fsd.setDrawable(icon);
                fsd.setScale(scale);
                icon = dr;
                scale = getNormalizer().getScale(icon, outIconBounds, null, null);

                ((ColorDrawable) dr.getBackground()).setColor(mWrapperBackgroundColor);
            }
        } else {
            scale = getNormalizer().getScale(icon, outIconBounds, null, null);
        }

        outScale[0] = scale;
        return icon;
    }

    /**
     * Adds the {@param badge} on top of {@param target} using the badge dimensions.
     */
    public void badgeWithDrawable(Bitmap target, Drawable badge) {
        mCanvas.setBitmap(target);
        badgeWithDrawable(mCanvas, badge);
        mCanvas.setBitmap(null);
    }

    /**
     * Adds the {@param badge} on top of {@param target} using the badge dimensions.
     */
    private void badgeWithDrawable(Canvas target, Drawable badge) {
        int badgeSize = mContext.getResources().getDimensionPixelSize(R.dimen.profile_badge_size);
        badge.setBounds(mIconBitmapSize - badgeSize, mIconBitmapSize - badgeSize,
                mIconBitmapSize, mIconBitmapSize);
        badge.draw(target);
    }

    /**
     * @param scale the scale to apply before drawing {@param icon} on the canvas
     */
    public Bitmap createIconBitmap(Drawable icon, float scale) {
        int width = mIconBitmapSize;
        int height = mIconBitmapSize;

        if (icon instanceof PaintDrawable) {
            PaintDrawable painter = (PaintDrawable) icon;
            painter.setIntrinsicWidth(width);
            painter.setIntrinsicHeight(height);
        } else if (icon instanceof BitmapDrawable) {
            // Ensure the bitmap has a density.
            BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
            Bitmap bitmap = bitmapDrawable.getBitmap();
            if (bitmap != null && bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                bitmapDrawable.setTargetDensity(mContext.getResources().getDisplayMetrics());
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
        int textureWidth = mIconBitmapSize;
        int textureHeight = mIconBitmapSize;

        Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(bitmap);

        final int left = (textureWidth-width) / 2;
        final int top = (textureHeight-height) / 2;

        mOldBounds.set(icon.getBounds());
        if (icon instanceof AdaptiveIconCompat) {
            int offset = Math.max((int) Math.ceil(BLUR_FACTOR * textureWidth), Math.max(left, top));
            int size = Math.max(width, height);
            icon.setBounds(offset, offset, size - offset, size - offset);
        } else {
            icon.setBounds(left, top, left+width, top+height);
        }
        int count = mCanvas.save();
        mCanvas.scale(scale, scale, textureWidth / 2, textureHeight / 2);
        icon.draw(mCanvas);
        try {
            mCanvas.restoreToCount(count);
        } catch (Exception e) {
            Log.e("LauncherIcons", "This shouldn't be happening really", e);
        }
        icon.setBounds(mOldBounds);
        mCanvas.setBitmap(null);

        return bitmap;
    }

    public BitmapInfo createShortcutIcon(ShortcutInfoCompat shortcutInfo) {
        return createShortcutIcon(shortcutInfo, true /* badged */,  null);
    }

    public BitmapInfo createShortcutIcon(ShortcutInfoCompat shortcutInfo, boolean badged) {
        return createShortcutIcon(shortcutInfo, badged, null);
    }

    public BitmapInfo createShortcutIcon(ShortcutInfoCompat shortcutInfo,
            boolean badged, @Nullable Provider<Bitmap> fallbackIconProvider) {
        IconCache cache = LauncherAppState.getInstance(mContext).getIconCache();
        BitmapInfo result = createShortcutIconPlain(shortcutInfo, fallbackIconProvider);

        final Bitmap unbadgedfinal = result.icon;
        final ItemInfoWithIcon badge;
        if (badged) {
            badge = getShortcutInfoBadge(shortcutInfo, cache);
            result.color = badge.iconColor;
        } else {
            badge = null;
        }

        result.icon = BitmapRenderer.createHardwareBitmap(mIconBitmapSize, mIconBitmapSize, (c) -> {
            getShadowGenerator().recreateIcon(unbadgedfinal, c);
            if (badge != null) {
                badgeWithDrawable(c, new FastBitmapDrawable(badge));
            }
        });
        return result;
    }

    public BitmapInfo createShortcutIconPlain(ShortcutInfoCompat shortcutInfo) {
        return createShortcutIconPlain(shortcutInfo, null);
    }

    public BitmapInfo createShortcutIconPlain(ShortcutInfoCompat shortcutInfo,
            @Nullable Provider<Bitmap> fallbackIconProvider) {
        Drawable unbadgedDrawable;
        if (iconProvider instanceof LawnchairIconProvider) {
            unbadgedDrawable = ((LawnchairIconProvider) iconProvider).getIcon(shortcutInfo, mFillResIconDpi);
        } else {
            unbadgedDrawable = DeepShortcutManager.getInstance(mContext)
                    .getShortcutIconDrawable(shortcutInfo, mFillResIconDpi);
        }
        IconCache cache = LauncherAppState.getInstance(mContext).getIconCache();

        final Bitmap unbadgedBitmap;
        if (unbadgedDrawable != null) {
            unbadgedBitmap = createScaledBitmapWithoutShadow(unbadgedDrawable, 0);
        } else {
            if (fallbackIconProvider != null) {
                // Fallback icons are already badged and with appropriate shadow
                Bitmap fullIcon = fallbackIconProvider.get();
                if (fullIcon != null) {
                    return createIconBitmap(fullIcon);
                }
            }
            unbadgedBitmap = cache.getDefaultIcon(Process.myUserHandle()).icon;
        }

        BitmapInfo result = new BitmapInfo();
        result.color = Themes.getColorAccent(mContext);
        result.icon = unbadgedBitmap;
        return result;
    }

    public ItemInfoWithIcon getShortcutInfoBadge(ShortcutInfoCompat shortcutInfo, IconCache cache) {
        ComponentName cn = shortcutInfo.getActivity();
        String badgePkg = shortcutInfo.getBadgePackage(mContext);
        boolean hasBadgePkgSet = !badgePkg.equals(shortcutInfo.getPackage());
        if (cn != null && !hasBadgePkgSet) {
            // Get the app info for the source activity.
            AppInfo appInfo = new AppInfo();
            appInfo.user = shortcutInfo.getUserHandle();
            appInfo.componentName = cn;
            appInfo.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn);
            cache.getTitleAndIcon(appInfo, false);
            return appInfo;
        } else {
            PackageItemInfo pkgInfo = new PackageItemInfo(badgePkg);
            cache.getTitleAndIconForApp(pkgInfo, false);
            return pkgInfo;
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
