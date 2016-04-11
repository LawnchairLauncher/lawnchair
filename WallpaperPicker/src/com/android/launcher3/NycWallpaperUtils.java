package com.android.launcher3;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.AsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class used to help set lockscreen wallpapers on N+.
 */
public class NycWallpaperUtils {
    public static final int FLAG_SET_SYSTEM = 1 << 0; // TODO: use WallpaperManager.FLAG_SET_SYSTEM
    public static final int FLAG_SET_LOCK = 1 << 1; // TODO: use WallpaperManager.FLAG_SET_LOCK

    /**
     * Calls cropTask.execute(), once the user has selected which wallpaper to set. On pre-N
     * devices, the prompt is not displayed since there is no API to set the lockscreen wallpaper.
     */
    public static void executeCropTaskAfterPrompt(
            Context context, final AsyncTask<Integer, ?, ?> cropTask,
            DialogInterface.OnCancelListener onCancelListener) {
        if (Utilities.isNycOrAbove()) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.wallpaper_instructions)
                    .setItems(R.array.which_wallpaper_options, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int selectedItemIndex) {
                            int whichWallpaper;
                            if (selectedItemIndex == 0) {
                                whichWallpaper = FLAG_SET_SYSTEM;
                            } else if (selectedItemIndex == 1) {
                                whichWallpaper = FLAG_SET_LOCK;
                            } else {
                                whichWallpaper = FLAG_SET_SYSTEM | FLAG_SET_LOCK;
                            }
                            cropTask.execute(whichWallpaper);
                        }
                    })
                    .setOnCancelListener(onCancelListener)
                    .show();
        } else {
            cropTask.execute(FLAG_SET_SYSTEM);
        }
    }

    public static void setStream(Context context, final InputStream data, Rect visibleCropHint,
            boolean allowBackup, int whichWallpaper) throws IOException {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
        try {
            // TODO: use mWallpaperManager.setStream(data, visibleCropHint, allowBackup, which)
            // without needing reflection.
            Method setStream = WallpaperManager.class.getMethod("setStream", InputStream.class,
                    Rect.class, boolean.class, int.class);
            setStream.invoke(wallpaperManager, data, visibleCropHint, allowBackup, whichWallpaper);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            // Fall back to previous implementation (set system)
            wallpaperManager.setStream(data);
        }
    }

    public static void clear(Context context, int whichWallpaper) throws IOException {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
        try {
            // TODO: use mWallpaperManager.clear(whichWallpaper) without needing reflection.
            Method clear = WallpaperManager.class.getMethod("clear", int.class);
            clear.invoke(wallpaperManager, whichWallpaper);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Fall back to previous implementation (clear system)
            wallpaperManager.clear();
        }
    }
}