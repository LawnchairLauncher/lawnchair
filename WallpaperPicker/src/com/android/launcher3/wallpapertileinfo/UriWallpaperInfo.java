package com.android.launcher3.wallpapertileinfo;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.gallery3d.common.BitmapCropTask;
import com.android.gallery3d.common.BitmapUtils;
import com.android.launcher3.R;
import com.android.launcher3.WallpaperPickerActivity;
import com.android.photos.BitmapRegionTileSource;
import com.android.photos.BitmapRegionTileSource.BitmapSource;

public class UriWallpaperInfo extends DrawableThumbWallpaperInfo {

    private static final String TAG = "UriWallpaperInfo";

    public final Uri mUri;

    public UriWallpaperInfo(Uri uri) {
        super(null);
        mUri = uri;
    }

    @Override
    public void onClick(final WallpaperPickerActivity a) {
        a.setWallpaperButtonEnabled(false);
        final BitmapRegionTileSource.UriBitmapSource bitmapSource =
                new BitmapRegionTileSource.UriBitmapSource(a.getContext(), mUri);
        a.setCropViewTileSource(bitmapSource, true, false, null, new Runnable() {

            @Override
            public void run() {
                if (bitmapSource.getLoadingState() == BitmapSource.State.LOADED) {
                    a.selectTile(mView);
                    a.setWallpaperButtonEnabled(true);
                } else {
                    ViewGroup parent = (ViewGroup) mView.getParent();
                    if (parent != null) {
                        parent.removeView(mView);
                        Toast.makeText(a.getContext(), R.string.image_load_fail,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    @Override
    public void onSave(final WallpaperPickerActivity a) {
        BitmapCropTask.OnBitmapCroppedHandler h = new BitmapCropTask.OnBitmapCroppedHandler() {
            public void onBitmapCropped(byte[] imageBytes) {
                // rotation is set to 0 since imageBytes has already been correctly rotated
                Bitmap thumb = createThumbnail(a, null, imageBytes, null, 0, 0, true);
                a.getSavedImages().writeImage(thumb, imageBytes);
            }
        };
        a.cropImageAndSetWallpaper(mUri, h);
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public boolean isNamelessWallpaper() {
        return true;
    }

    public void loadThumbnaleAsync(final WallpaperPickerActivity activity) {
        mView.setVisibility(View.GONE);
        new AsyncTask<Void, Void, Bitmap>() {
            protected Bitmap doInBackground(Void...args) {
                try {
                    int rotation = BitmapUtils.getRotationFromExif(activity, mUri);
                    return createThumbnail(activity, mUri, null, null, 0, rotation, false);
                } catch (SecurityException securityException) {
                    if (activity.isActivityDestroyed()) {
                        // Temporarily granted permissions are revoked when the activity
                        // finishes, potentially resulting in a SecurityException here.
                        // Even though {@link #isDestroyed} might also return true in different
                        // situations where the configuration changes, we are fine with
                        // catching these cases here as well.
                        cancel(false);
                    } else {
                        // otherwise it had a different cause and we throw it further
                        throw securityException;
                    }
                    return null;
                }
            }
            protected void onPostExecute(Bitmap thumb) {
                if (!isCancelled() && thumb != null) {
                    setThumb(new BitmapDrawable(activity.getResources(), thumb));
                    mView.setVisibility(View.VISIBLE);
                } else {
                    Log.e(TAG, "Error loading thumbnail for uri=" + mUri);
                }
            }
        }.execute();
    }
}