package com.android.launcher3.wallpapertileinfo;

import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import com.android.gallery3d.common.Utils;
import com.android.launcher3.WallpaperCropActivity.CropViewScaleAndOffsetProvider;
import com.android.launcher3.WallpaperPickerActivity;
import com.android.launcher3.util.WallpaperUtils;
import com.android.photos.BitmapRegionTileSource;
import com.android.photos.BitmapRegionTileSource.BitmapSource;
import com.android.photos.views.TiledImageRenderer.TileSource;

public class ResourceWallpaperInfo extends DrawableThumbWallpaperInfo {

    private final Resources mResources;
    private final int mResId;

    public ResourceWallpaperInfo(Resources res, int resId, Drawable thumb) {
        super(thumb);
        mResources = res;
        mResId = resId;
    }

    @Override
    public void onClick(final WallpaperPickerActivity a) {
        a.setWallpaperButtonEnabled(false);
        final BitmapRegionTileSource.ResourceBitmapSource bitmapSource =
                new BitmapRegionTileSource.ResourceBitmapSource(mResources, mResId, a);
        a.setCropViewTileSource(bitmapSource, false, true, new CropViewScaleAndOffsetProvider() {

            @Override
            public float getScale(TileSource src) {
                Point wallpaperSize = WallpaperUtils.getDefaultWallpaperSize(
                        a.getResources(), a.getWindowManager());
                RectF crop = Utils.getMaxCropRect(
                        src.getImageWidth(), src.getImageHeight(),
                        wallpaperSize.x, wallpaperSize.y, false);
                return wallpaperSize.x / crop.width();
            }

            @Override
            public float getParallaxOffset() {
                return a.getWallpaperParallaxOffset();
            }
        }, new Runnable() {

            @Override
            public void run() {
                if (bitmapSource.getLoadingState() == BitmapSource.State.LOADED) {
                    a.setWallpaperButtonEnabled(true);
                }
            }
        });
    }

    @Override
    public void onSave(WallpaperPickerActivity a) {
        a.cropImageAndSetWallpaper(mResources, mResId);
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public boolean isNamelessWallpaper() {
        return true;
    }
}