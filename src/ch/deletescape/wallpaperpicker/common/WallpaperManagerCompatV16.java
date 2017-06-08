package ch.deletescape.wallpaperpicker.common;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;

public class WallpaperManagerCompatV16 extends WallpaperManagerCompat {
    protected WallpaperManager mWallpaperManager;

    public WallpaperManagerCompatV16(Context context) {
        mWallpaperManager = WallpaperManager.getInstance(context.getApplicationContext());
    }

    @Override
    public void setStream(InputStream data, Rect visibleCropHint, boolean allowBackup,
                          int whichWallpaper) throws IOException {
        mWallpaperManager.setStream(data);
    }

    @Override
    public void clear(int whichWallpaper) throws IOException {
        mWallpaperManager.clear();
    }
}
