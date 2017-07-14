package ch.deletescape.wallpaperpicker.tileinfo;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import ch.deletescape.wallpaperpicker.WallpaperCropActivity.CropViewScaleAndOffsetProvider;
import ch.deletescape.wallpaperpicker.WallpaperFiles;
import ch.deletescape.wallpaperpicker.WallpaperPickerActivity;
import ch.deletescape.wallpaperpicker.common.CropAndSetWallpaperTask;
import ch.deletescape.wallpaperpicker.common.DialogUtils;
import ch.deletescape.wallpaperpicker.common.WallpaperManagerCompat;

public class DefaultWallpaperInfo extends DrawableThumbWallpaperInfo {

    private static final String TAG = "DefaultWallpaperInfo";

    public DefaultWallpaperInfo(Drawable thumb) {
        super(thumb);
    }

    @Override
    public void onClick(WallpaperPickerActivity a) {
        a.setCropViewTileSource(null, false, false, new CropViewScaleAndOffsetProvider() {

            @Override
            public float getScale() {
                return 1f;
            }

            @Override
            public float getParallaxOffset() {
                return 0.5f;
            }
        }, null);
    }

    @Override
    public void onSave(final WallpaperPickerActivity a) {
        CropAndSetWallpaperTask.OnEndCropHandler onEndCropHandler
                = new CropAndSetWallpaperTask.OnEndCropHandler() {
            @Override
            public void run(boolean cropSucceeded) {
                if (cropSucceeded) {
                    a.setResult(Activity.RESULT_OK);
                }
                a.finish();
            }
        };
        CropAndSetWallpaperTask setWallpaperTask = new CropAndSetWallpaperTask(
                null, a, null, -1, -1, -1, onEndCropHandler) {
            @Override
            protected Boolean doInBackground(Integer... params) {
                int whichWallpaper = params[0];
                boolean succeeded;
                if (whichWallpaper == WallpaperManagerCompat.FLAG_SET_LOCK) {
                    succeeded = setDefaultOnLock(a);
                } else {
                    succeeded = clearWallpaper(a, whichWallpaper);
                }
                return succeeded;
            }
        };

        DialogUtils.executeCropTaskAfterPrompt(a, setWallpaperTask, a.getOnDialogCancelListener());
    }

    //TODO: @TargetApi(Build.VERSION_CODES.N)
    private boolean setDefaultOnLock(WallpaperPickerActivity a) {
        boolean succeeded = true;
        try {
            Bitmap defaultWallpaper = ((BitmapDrawable) WallpaperManager.getInstance(
                    a.getApplicationContext()).getBuiltInDrawable()).getBitmap();
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
            if (defaultWallpaper.compress(Bitmap.CompressFormat.PNG, 100, tmpOut)) {
                byte[] outByteArray = tmpOut.toByteArray();
                WallpaperManagerCompat.getInstance(a.getApplicationContext())
                        .setStream(new ByteArrayInputStream(outByteArray), null,
                                true, WallpaperManagerCompat.FLAG_SET_LOCK);
            }
        } catch (IOException e) {
            Log.w(TAG, "Setting wallpaper to default threw exception", e);
            succeeded = false;
        }
        return succeeded;
    }

    private boolean clearWallpaper(WallpaperPickerActivity a, int whichWallpaper) {
        boolean succeeded = true;
        try {
            WallpaperManagerCompat.getInstance(a.getApplicationContext()).clear(whichWallpaper);
        } catch (IOException e) {
            Log.w(TAG, "Setting wallpaper to default threw exception", e);
            succeeded = false;
        } catch (SecurityException e) {
            // Happens on Samsung S6, for instance:
            // "Permission denial: writing to settings requires android.permission.WRITE_SETTINGS"
            Log.w(TAG, "Setting wallpaper to default threw exception", e);
            // In this case, clearing worked even though the exception was thrown afterwards.
            succeeded = true;
        }
        return succeeded;
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public boolean isNamelessWallpaper() {
        return true;
    }

    /**
     * @return the system default wallpaper tile or null
     */
    public static WallpaperTileInfo get(Context context) {
        return getDefaultWallpaper(context);
    }

    private static DefaultWallpaperInfo getDefaultWallpaper(Context context) {
        File defaultThumbFile = getDefaultThumbFile(context);
        Bitmap thumb = null;
        boolean defaultWallpaperExists = false;
        Resources res = context.getResources();

        if (defaultThumbFile.exists()) {
            thumb = BitmapFactory.decodeFile(defaultThumbFile.getAbsolutePath());
            defaultWallpaperExists = true;
        } else {
            Point defaultThumbSize = getDefaultThumbSize(res);
            Drawable wallpaperDrawable = WallpaperManager.getInstance(context).getBuiltInDrawable(
                    defaultThumbSize.x, defaultThumbSize.y, true, 0.5f, 0.5f);
            if (wallpaperDrawable != null) {
                thumb = Bitmap.createBitmap(
                        defaultThumbSize.x, defaultThumbSize.y, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(thumb);
                wallpaperDrawable.setBounds(0, 0, defaultThumbSize.x, defaultThumbSize.y);
                wallpaperDrawable.draw(c);
                c.setBitmap(null);
            }
            if (thumb != null) {
                defaultWallpaperExists = saveDefaultWallpaperThumb(context, thumb);
            }
        }
        if (defaultWallpaperExists) {
            return new DefaultWallpaperInfo(new BitmapDrawable(res, thumb));
        }
        return null;
    }

    private static File getDefaultThumbFile(Context context) {
        return new File(context.getFilesDir(), Build.VERSION.SDK_INT
                + "_" + WallpaperFiles.DEFAULT_WALLPAPER_THUMBNAIL);
    }

    private static boolean saveDefaultWallpaperThumb(Context c, Bitmap b) {
        // Delete old thumbnails.
        new File(c.getFilesDir(), WallpaperFiles.DEFAULT_WALLPAPER_THUMBNAIL_OLD).delete();
        new File(c.getFilesDir(), WallpaperFiles.DEFAULT_WALLPAPER_THUMBNAIL).delete();

        for (int i = Build.VERSION_CODES.JELLY_BEAN; i < Build.VERSION.SDK_INT; i++) {
            new File(c.getFilesDir(), i + "_" + WallpaperFiles.DEFAULT_WALLPAPER_THUMBNAIL).delete();
        }
        File f = getDefaultThumbFile(c);
        try {
            f.createNewFile();
            FileOutputStream thumbFileStream = c.openFileOutput(f.getName(), Context.MODE_PRIVATE);
            b.compress(Bitmap.CompressFormat.JPEG, 95, thumbFileStream);
            thumbFileStream.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error while writing bitmap to file " + e);
            f.delete();
            return false;
        }
    }
}