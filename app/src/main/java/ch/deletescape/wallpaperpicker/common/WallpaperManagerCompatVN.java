package ch.deletescape.wallpaperpicker.common;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;

public class WallpaperManagerCompatVN extends WallpaperManagerCompatV16 {
    public WallpaperManagerCompatVN(Context context) {
        super(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void setStream(final InputStream data, Rect visibleCropHint, boolean allowBackup,
                          int whichWallpaper) throws IOException {
        mWallpaperManager.setStream(data, visibleCropHint, allowBackup, whichWallpaper);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void clear(int whichWallpaper) throws IOException {
        mWallpaperManager.clear(whichWallpaper);
    }
}
