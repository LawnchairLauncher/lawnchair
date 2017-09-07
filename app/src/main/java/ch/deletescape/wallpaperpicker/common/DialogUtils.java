package ch.deletescape.wallpaperpicker.common;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;

/**
 * Utility class used to show dialogs for things like picking which wallpaper to set.
 */
public class DialogUtils {
    /**
     * Calls cropTask.execute(), once the user has selected which wallpaper to set. On pre-N
     * devices, the prompt is not displayed since there is no API to set the lockscreen wallpaper.
     * <p>
     * TODO: Don't use CropAndSetWallpaperTask on N+, because the new API will handle cropping instead.
     */
    public static void executeCropTaskAfterPrompt(
            Context context, final AsyncTask<Integer, ?, ?> cropTask,
            DialogInterface.OnCancelListener onCancelListener) {
        if (Utilities.ATLEAST_NOUGAT) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.wallpaper_instructions)
                    .setItems(R.array.which_wallpaper_options, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int selectedItemIndex) {
                            int whichWallpaper;
                            if (selectedItemIndex == 0) {
                                whichWallpaper = WallpaperManagerCompat.FLAG_SET_SYSTEM;
                            } else if (selectedItemIndex == 1) {
                                whichWallpaper = WallpaperManagerCompat.FLAG_SET_LOCK;
                            } else {
                                whichWallpaper = WallpaperManagerCompat.FLAG_SET_SYSTEM
                                        | WallpaperManagerCompat.FLAG_SET_LOCK;
                            }
                            cropTask.execute(whichWallpaper);
                        }
                    })
                    .setOnCancelListener(onCancelListener)
                    .show();
        } else {
            cropTask.execute(WallpaperManagerCompat.FLAG_SET_SYSTEM);
        }
    }
}
