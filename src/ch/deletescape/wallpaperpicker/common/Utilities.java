package ch.deletescape.wallpaperpicker.common;

import android.app.WallpaperManager;

public class Utilities {

    public static boolean isAtLeastN() {
        // TODO: replace this with a more final implementation.
        try {
            WallpaperManager.class.getMethod("getWallpaperFile", int.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
