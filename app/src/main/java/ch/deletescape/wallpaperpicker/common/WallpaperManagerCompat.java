package ch.deletescape.wallpaperpicker.common;

import android.content.Context;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;

import ch.deletescape.lawnchair.Utilities;

public abstract class WallpaperManagerCompat {
    public static final int FLAG_SET_SYSTEM = 1 << 0; // TODO: use WallpaperManager.FLAG_SET_SYSTEM
    public static final int FLAG_SET_LOCK = 1 << 1; // TODO: use WallpaperManager.FLAG_SET_LOCK

    private static WallpaperManagerCompat sInstance;
    private static final Object sInstanceLock = new Object();

    public static WallpaperManagerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.ATLEAST_NOUGAT) {
                    sInstance = new WallpaperManagerCompatVN(context.getApplicationContext());
                } else {
                    sInstance = new WallpaperManagerCompatV16(context.getApplicationContext());
                }
            }
            return sInstance;
        }
    }

    public abstract void setStream(InputStream stream, Rect visibleCropHint, boolean allowBackup,
                                   int whichWallpaper) throws IOException;

    public abstract void clear(int whichWallpaper) throws IOException;
}
