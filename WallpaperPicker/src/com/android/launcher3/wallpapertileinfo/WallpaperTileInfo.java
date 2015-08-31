package com.android.launcher3.wallpapertileinfo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.gallery3d.common.BitmapCropTask;
import com.android.gallery3d.common.Utils;
import com.android.launcher3.R;
import com.android.launcher3.WallpaperPickerActivity;

public abstract class WallpaperTileInfo {

    protected View mView;

    public void onClick(WallpaperPickerActivity a) {}

    public void onSave(WallpaperPickerActivity a) {}

    public void onDelete(WallpaperPickerActivity a) {}

    public boolean isSelectable() { return false; }

    public boolean isNamelessWallpaper() { return false; }

    public void onIndexUpdated(CharSequence label) {
        if (isNamelessWallpaper()) {
            mView.setContentDescription(label);
        }
    }

    public abstract View createView(Context context, LayoutInflater inflator, ViewGroup parent);

    protected static Point getDefaultThumbSize(Resources res) {
        return new Point(res.getDimensionPixelSize(R.dimen.wallpaperThumbnailWidth),
                res.getDimensionPixelSize(R.dimen.wallpaperThumbnailHeight));

    }

    protected static Bitmap createThumbnail(Context context, Uri uri, byte[] imageBytes,
            Resources res, int resId, int rotation, boolean leftAligned) {
        Point size = getDefaultThumbSize(context.getResources());
        int width = size.x;
        int height = size.y;

        BitmapCropTask cropTask;
        if (uri != null) {
            cropTask = new BitmapCropTask(
                    context, uri, null, rotation, width, height, false, true, null);
        } else if (imageBytes != null) {
            cropTask = new BitmapCropTask(
                    imageBytes, null, rotation, width, height, false, true, null);
        }  else {
            cropTask = new BitmapCropTask(
                    context, res, resId, null, rotation, width, height, false, true, null);
        }
        Point bounds = cropTask.getImageBounds();
        if (bounds == null || bounds.x == 0 || bounds.y == 0) {
            return null;
        }

        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(rotation);
        float[] rotatedBounds = new float[] { bounds.x, bounds.y };
        rotateMatrix.mapPoints(rotatedBounds);
        rotatedBounds[0] = Math.abs(rotatedBounds[0]);
        rotatedBounds[1] = Math.abs(rotatedBounds[1]);

        RectF cropRect = Utils.getMaxCropRect(
                (int) rotatedBounds[0], (int) rotatedBounds[1], width, height, leftAligned);
        cropTask.setCropBounds(cropRect);

        if (cropTask.cropBitmap()) {
            return cropTask.getCroppedBitmap();
        } else {
            return null;
        }
    }

}