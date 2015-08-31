package com.android.launcher3.wallpapertileinfo;

import android.annotation.TargetApi;
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

import com.android.gallery3d.common.BitmapUtils;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.Utilities;
import com.android.launcher3.WallpaperCropActivity.CropViewScaleProvider;
import com.android.launcher3.WallpaperPickerActivity;
import com.android.photos.views.TiledImageRenderer.TileSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class DefaultWallpaperInfo extends DrawableThumbWallpaperInfo {

    private static final String TAG = "DefaultWallpaperInfo";

    public DefaultWallpaperInfo(Drawable thumb) {
        super(thumb);
    }

    @Override
    public void onClick(WallpaperPickerActivity a) {
        a.setCropViewTileSource(null, false, false, new CropViewScaleProvider() {

            @Override
            public float getScale(TileSource src) {
                return 1f;
            }
        }, null);
    }

    @Override
    public void onSave(WallpaperPickerActivity a) {
        try {
            WallpaperManager.getInstance(a.getContext()).clear();
            a.setResult(Activity.RESULT_OK);
        } catch (IOException e) {
            Log.w(TAG, "Setting wallpaper to default threw exception", e);
        }
        a.finish();
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
        return Utilities.ATLEAST_KITKAT
                ? getDefaultWallpaper(context) : getPreKKDefaultWallpaperInfo(context);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
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

    private static ResourceWallpaperInfo getPreKKDefaultWallpaperInfo(Context context) {
        Resources sysRes = Resources.getSystem();
        Resources res = context.getResources();

        int resId = sysRes.getIdentifier("default_wallpaper", "drawable", "android");

        File defaultThumbFile = getDefaultThumbFile(context);
        Bitmap thumb = null;
        boolean defaultWallpaperExists = false;
        if (defaultThumbFile.exists()) {
            thumb = BitmapFactory.decodeFile(defaultThumbFile.getAbsolutePath());
            defaultWallpaperExists = true;
        } else {
            int rotation = BitmapUtils.getRotationFromExif(res, resId, context);
            thumb = createThumbnail(context, null, null, sysRes, resId, rotation, false);
            if (thumb != null) {
                defaultWallpaperExists = saveDefaultWallpaperThumb(context, thumb);
            }
        }
        if (defaultWallpaperExists) {
            return new ResourceWallpaperInfo(sysRes, resId, new BitmapDrawable(res, thumb));
        }
        return null;
    }

    private static File getDefaultThumbFile(Context context) {
        return new File(context.getFilesDir(), Build.VERSION.SDK_INT
                + "_" + LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL);
    }

    private static boolean saveDefaultWallpaperThumb(Context c, Bitmap b) {
        // Delete old thumbnails.
        new File(c.getFilesDir(), LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL_OLD).delete();
        new File(c.getFilesDir(), LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL).delete();

        for (int i = Build.VERSION_CODES.JELLY_BEAN; i < Build.VERSION.SDK_INT; i++) {
            new File(c.getFilesDir(), i + "_" + LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL).delete();
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