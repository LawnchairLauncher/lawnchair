package ch.deletescape.wallpaperpicker.tileinfo;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import ch.deletescape.lawnchair.R;
import ch.deletescape.wallpaperpicker.BitmapRegionTileSource;
import ch.deletescape.wallpaperpicker.BitmapRegionTileSource.BitmapSource;
import ch.deletescape.wallpaperpicker.WallpaperPickerActivity;
import ch.deletescape.wallpaperpicker.common.CropAndSetWallpaperTask;
import ch.deletescape.wallpaperpicker.common.InputStreamProvider;

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
        final BitmapRegionTileSource.InputStreamSource bitmapSource =
                new BitmapRegionTileSource.InputStreamSource(a, mUri);
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
                        Toast.makeText(a, R.string.image_load_fail,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    @Override
    public void onSave(final WallpaperPickerActivity a) {
        CropAndSetWallpaperTask.OnBitmapCroppedHandler h =
                new CropAndSetWallpaperTask.OnBitmapCroppedHandler() {
                    public void onBitmapCropped(byte[] imageBytes) {
                        // rotation is set to 0 since imageBytes has already been correctly rotated
                        Bitmap thumb = createThumbnail(
                                InputStreamProvider.fromBytes(imageBytes), a, 0, true);
                        a.getSavedImages().writeImage(thumb, imageBytes);
                    }
                };
        boolean shouldFadeOutOnFinish = a.getWallpaperParallaxOffset() == 0f;
        a.cropImageAndSetWallpaper(mUri, h, shouldFadeOutOnFinish);
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
            protected Bitmap doInBackground(Void... args) {
                try {
                    InputStreamProvider isp = InputStreamProvider.fromUri(activity, mUri);
                    int rotation = isp.getRotationFromExif(activity);
                    return createThumbnail(isp, activity, rotation, false);
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