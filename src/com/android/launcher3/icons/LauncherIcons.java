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

package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Process;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.AppInfo;
import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.util.Themes;

import java.util.function.Supplier;

/**
 * Wrapper class to provide access to {@link BaseIconFactory} and also to provide pool of this class
 * that are threadsafe.
 */
public class LauncherIcons extends BaseIconFactory implements AutoCloseable {

    private static final Object sPoolSync = new Object();
    private static LauncherIcons sPool;
    private static int sPoolId = 0;

    public static LauncherIcons obtain(Context context) {
        return obtain(context, IconShape.getShape().enableShapeDetection());
    }

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
    public static LauncherIcons obtain(Context context, boolean shapeDetection) {
        int poolId;
        synchronized (sPoolSync) {
            if (sPool != null) {
                LauncherIcons m = sPool;
                sPool = m.next;
                m.next = null;
                return m;
            }
            poolId = sPoolId;
        }

        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        return new LauncherIcons(context, idp.fillResIconDpi, idp.iconBitmapSize, poolId,
                shapeDetection);
    }

    public static void clearPool() {
        synchronized (sPoolSync) {
            sPool = null;
            sPoolId++;
        }
    }

    private final int mPoolId;

    private LauncherIcons next;

    private LauncherIcons(Context context, int fillResIconDpi, int iconBitmapSize, int poolId,
            boolean shapeDetection) {
        super(context, fillResIconDpi, iconBitmapSize, shapeDetection);
        mPoolId = poolId;
    }

    /**
     * Recycles a LauncherIcons that may be in-use.
     */
    public void recycle() {
        synchronized (sPoolSync) {
            if (sPoolId != mPoolId) {
                return;
            }
            // Clear any temporary state variables
            clear();

            next = sPool;
            sPool = this;
        }
    }

    @Override
    public void close() {
        recycle();
    }

    // below methods should also migrate to BaseIconFactory
    public BitmapInfo createShortcutIcon(ShortcutInfo shortcutInfo) {
        return createShortcutIcon(shortcutInfo, true /* badged */);
    }

    public BitmapInfo createShortcutIcon(ShortcutInfo shortcutInfo, boolean badged) {
        return createShortcutIcon(shortcutInfo, badged, null);
    }

    public BitmapInfo createShortcutIcon(ShortcutInfo shortcutInfo, boolean badged,
            @Nullable Supplier<ItemInfoWithIcon> fallbackIconProvider) {
        if (FeatureFlags.ENABLE_DEEP_SHORTCUT_ICON_CACHE.get()) {
            return createShortcutIconCached(shortcutInfo, badged, true, fallbackIconProvider);
        } else {
            return createShortcutIconLegacy(shortcutInfo, badged, fallbackIconProvider);
        }
    }

    public BitmapInfo createShortcutIconLegacy(ShortcutInfo shortcutInfo, boolean badged,
            @Nullable Supplier<ItemInfoWithIcon> fallbackIconProvider) {
        Drawable unbadgedDrawable = ShortcutCachingLogic.getIcon(
                mContext, shortcutInfo, mFillResIconDpi);
        IconCache cache = LauncherAppState.getInstance(mContext).getIconCache();
        final Bitmap unbadgedBitmap;
        if (unbadgedDrawable != null) {
            unbadgedBitmap = createScaledBitmapWithoutShadow(unbadgedDrawable, 0);
        } else {
            if (fallbackIconProvider != null) {
                // Fallback icons are already badged and with appropriate shadow
                ItemInfoWithIcon fullIcon = fallbackIconProvider.get();
                if (fullIcon != null && fullIcon.bitmap != null) {
                    return fullIcon.bitmap;
                }
            }
            unbadgedBitmap = cache.getDefaultIcon(Process.myUserHandle()).icon;
        }

        if (!badged) {
            return BitmapInfo.of(unbadgedBitmap, Themes.getColorAccent(mContext));
        }

        final Bitmap unbadgedfinal = unbadgedBitmap;
        final ItemInfoWithIcon badge = getShortcutInfoBadge(shortcutInfo, cache);

        Bitmap icon = BitmapRenderer.createHardwareBitmap(mIconBitmapSize, mIconBitmapSize, (c) -> {
            getShadowGenerator().recreateIcon(unbadgedfinal, c);
            badgeWithDrawable(c, new FastBitmapDrawable(badge.bitmap));
        });
        return BitmapInfo.of(icon, badge.bitmap.color);
    }

    @WorkerThread
    public BitmapInfo createShortcutIconCached(ShortcutInfo shortcutInfo, boolean badged,
            boolean useCache, @Nullable Supplier<ItemInfoWithIcon> fallbackIconProvider) {
        IconCache cache = LauncherAppState.getInstance(mContext).getIconCache();
        final BitmapInfo bitmapInfo;
        if (useCache) {
            bitmapInfo = cache.getDeepShortcutTitleAndIcon(shortcutInfo).bitmap;
        } else {
            bitmapInfo = new ShortcutCachingLogic().loadIcon(mContext, shortcutInfo);
        }
        final Bitmap unbadgedBitmap;
        if (bitmapInfo.icon != null) {
            unbadgedBitmap = bitmapInfo.icon;
        } else {
            if (fallbackIconProvider != null) {
                // Fallback icons are already badged and with appropriate shadow
                ItemInfoWithIcon fullIcon = fallbackIconProvider.get();
                if (fullIcon != null && fullIcon.bitmap != null) {
                    return fullIcon.bitmap;
                }
            }
            unbadgedBitmap = cache.getDefaultIcon(Process.myUserHandle()).icon;
        }

        if (!badged) {
            return BitmapInfo.of(unbadgedBitmap, Themes.getColorAccent(mContext));
        }

        final Bitmap unbadgedfinal = unbadgedBitmap;
        final ItemInfoWithIcon badge = getShortcutInfoBadge(shortcutInfo, cache);

        Bitmap icon = BitmapRenderer.createHardwareBitmap(mIconBitmapSize, mIconBitmapSize, (c) -> {
            getShadowGenerator().recreateIcon(unbadgedfinal, c);
            badgeWithDrawable(c, new FastBitmapDrawable(badge.bitmap));
        });
        return BitmapInfo.of(icon, badge.bitmap.color);
    }

    public ItemInfoWithIcon getShortcutInfoBadge(ShortcutInfo shortcutInfo, IconCache cache) {
        ComponentName cn = shortcutInfo.getActivity();
        String badgePkg = shortcutInfo.getPackage();
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
}
