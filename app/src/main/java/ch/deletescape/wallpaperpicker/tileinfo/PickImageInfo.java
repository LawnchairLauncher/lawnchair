package ch.deletescape.wallpaperpicker.tileinfo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.Process;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import ch.deletescape.lawnchair.R;
import ch.deletescape.wallpaperpicker.WallpaperPickerActivity;

public class PickImageInfo extends WallpaperTileInfo {

    @Override
    public void onClick(WallpaperPickerActivity a) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
        a.startActivityForResultSafely(intent, WallpaperPickerActivity.IMAGE_PICK);
    }

    @Override
    public View createView(Context context, LayoutInflater inflator, ViewGroup parent) {
        mView = inflator.inflate(R.layout.wallpaper_picker_image_picker_item, parent, false);

        // Make its background the last photo taken on external storage
        Bitmap lastPhoto = getThumbnailOfLastPhoto(context);
        if (lastPhoto != null) {
            ImageView galleryThumbnailBg =
                    mView.findViewById(R.id.wallpaper_image);
            galleryThumbnailBg.setImageBitmap(lastPhoto);
            int colorOverlay = context.getResources().getColor(R.color.wallpaper_picker_translucent_gray);
            galleryThumbnailBg.setColorFilter(colorOverlay, PorterDuff.Mode.SRC_ATOP);
        }

        mView.setTag(this);
        return mView;
    }

    private Bitmap getThumbnailOfLastPhoto(Context context) {
        boolean canReadExternalStorage = context.checkPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE, Process.myPid(), Process.myUid()) ==
                PackageManager.PERMISSION_GRANTED;

        if (!canReadExternalStorage) {
            // MediaStore.Images.Media.EXTERNAL_CONTENT_URI requires
            // the READ_EXTERNAL_STORAGE permission
            return null;
        }

        Cursor cursor = MediaStore.Images.Media.query(context.getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.ImageColumns._ID,
                        MediaStore.Images.ImageColumns.DATE_TAKEN},
                null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT 1");

        Bitmap thumb = null;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                thumb = MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(),
                        id, MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
            cursor.close();
        }
        return thumb;
    }
}