package com.android.launcher3.wallpapertileinfo;

import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.android.launcher3.WallpaperPickerActivity;
import com.android.photos.BitmapRegionTileSource;
import com.android.photos.BitmapRegionTileSource.BitmapSource;

import java.io.File;

public class FileWallpaperInfo extends DrawableThumbWallpaperInfo {

    private final File mFile;

    public FileWallpaperInfo(File target, Drawable thumb) {
        super(thumb);
        mFile = target;
    }

    @Override
    public void onClick(final WallpaperPickerActivity a) {
        a.setWallpaperButtonEnabled(false);
        final BitmapRegionTileSource.FilePathBitmapSource bitmapSource =
                new BitmapRegionTileSource.FilePathBitmapSource(mFile.getAbsolutePath());
        a.setCropViewTileSource(bitmapSource, false, true, null, new Runnable() {

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
        a.setWallpaper(Uri.fromFile(mFile));
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