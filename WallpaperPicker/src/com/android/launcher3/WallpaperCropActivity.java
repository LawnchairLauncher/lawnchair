/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Toast;

import com.android.gallery3d.common.BitmapCropTask;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.launcher3.base.BaseActivity;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.WallpaperUtils;
import com.android.photos.BitmapRegionTileSource;
import com.android.photos.BitmapRegionTileSource.BitmapSource;
import com.android.photos.BitmapRegionTileSource.BitmapSource.InBitmapProvider;
import com.android.photos.views.TiledImageRenderer.TileSource;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class WallpaperCropActivity extends BaseActivity implements Handler.Callback {
    private static final String LOGTAG = "Launcher3.CropActivity";

    private static final int MSG_LOAD_IMAGE = 1;

    protected CropView mCropView;
    protected View mProgressView;
    protected View mSetWallpaperButton;

    private HandlerThread mLoaderThread;
    private Handler mLoaderHandler;
    @Thunk LoadRequest mCurrentLoadRequest;
    private byte[] mTempStorageForDecoding = new byte[16 * 1024];
    // A weak-set of reusable bitmaps
    @Thunk Set<Bitmap> mReusableBitmaps =
            Collections.newSetFromMap(new WeakHashMap<Bitmap, Boolean>());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLoaderThread = new HandlerThread("wallpaper_loader");
        mLoaderThread.start();
        mLoaderHandler = new Handler(mLoaderThread.getLooper(), this);

        init();
        if (!enableRotation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    protected void init() {
        setContentView(R.layout.wallpaper_cropper);

        mCropView = (CropView) findViewById(R.id.cropView);
        mProgressView = findViewById(R.id.loading);

        Intent cropIntent = getIntent();
        final Uri imageUri = cropIntent.getData();

        if (imageUri == null) {
            Log.e(LOGTAG, "No URI passed in intent, exiting WallpaperCropActivity");
            finish();
            return;
        }

        // Action bar
        // Show the custom action bar view
        final ActionBar actionBar = getActionBar();
        actionBar.setCustomView(R.layout.actionbar_set_wallpaper);
        actionBar.getCustomView().setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        cropImageAndSetWallpaper(imageUri, null);
                    }
                });
        mSetWallpaperButton = findViewById(R.id.set_wallpaper_button);

        // Load image in background
        final BitmapRegionTileSource.UriBitmapSource bitmapSource =
                new BitmapRegionTileSource.UriBitmapSource(getContext(), imageUri);
        mSetWallpaperButton.setEnabled(false);
        Runnable onLoad = new Runnable() {
            public void run() {
                if (bitmapSource.getLoadingState() != BitmapSource.State.LOADED) {
                    Toast.makeText(getContext(), R.string.wallpaper_load_fail,
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    mSetWallpaperButton.setEnabled(true);
                }
            }
        };
        setCropViewTileSource(bitmapSource, true, false, null, onLoad);
    }

    @Override
    public void onDestroy() {
        if (mCropView != null) {
            mCropView.destroy();
        }
        if (mLoaderThread != null) {
            mLoaderThread.quit();
        }
        super.onDestroy();
    }

    /**
     * This is called on {@link #mLoaderThread}
     */
    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_LOAD_IMAGE) {
            final LoadRequest req = (LoadRequest) msg.obj;
            final boolean loadSuccess;

            if (req.src == null) {
                Drawable defaultWallpaper = WallpaperManager.getInstance(this)
                        .getBuiltInDrawable(mCropView.getWidth(), mCropView.getHeight(),
                                false, 0.5f, 0.5f);

                if (defaultWallpaper == null) {
                    loadSuccess = false;
                    Log.w(LOGTAG, "Null default wallpaper encountered.");
                } else {
                    loadSuccess = true;
                    req.result = new DrawableTileSource(this,
                            defaultWallpaper, DrawableTileSource.MAX_PREVIEW_SIZE);
                }
            } else {
                try {
                    req.src.loadInBackground(new InBitmapProvider() {

                        @Override
                        public Bitmap forPixelCount(int count) {
                            Bitmap bitmapToReuse = null;
                            // Find the smallest bitmap that satisfies the pixel count limit
                            synchronized (mReusableBitmaps) {
                                int currentBitmapSize = Integer.MAX_VALUE;
                                for (Bitmap b : mReusableBitmaps) {
                                    int bitmapSize = b.getWidth() * b.getHeight();
                                    if ((bitmapSize >= count) && (bitmapSize < currentBitmapSize)) {
                                        bitmapToReuse = b;
                                        currentBitmapSize = bitmapSize;
                                    }
                                }

                                if (bitmapToReuse != null) {
                                    mReusableBitmaps.remove(bitmapToReuse);
                                }
                            }
                            return bitmapToReuse;
                        }
                    });
                } catch (SecurityException securityException) {
                    if (isActivityDestroyed()) {
                        // Temporarily granted permissions are revoked when the activity
                        // finishes, potentially resulting in a SecurityException here.
                        // Even though {@link #isDestroyed} might also return true in different
                        // situations where the configuration changes, we are fine with
                        // catching these cases here as well.
                        return true;
                    } else {
                        // otherwise it had a different cause and we throw it further
                        throw securityException;
                    }
                }

                req.result = new BitmapRegionTileSource(getContext(), req.src,
                        mTempStorageForDecoding);
                loadSuccess = req.src.getLoadingState() == BitmapSource.State.LOADED;
            }

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (req == mCurrentLoadRequest) {
                        onLoadRequestComplete(req, loadSuccess);
                    } else {
                        addReusableBitmap(req.result);
                    }
                }
            });
            return true;
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public boolean isActivityDestroyed() {
        return Utilities.ATLEAST_JB_MR1 && isDestroyed();
    }

    @Thunk void addReusableBitmap(TileSource src) {
        synchronized (mReusableBitmaps) {
            if (Utilities.ATLEAST_KITKAT && src instanceof BitmapRegionTileSource) {
                Bitmap preview = ((BitmapRegionTileSource) src).getBitmap();
                if (preview != null && preview.isMutable()) {
                    mReusableBitmaps.add(preview);
                }
            }
        }
    }

    protected void onLoadRequestComplete(LoadRequest req, boolean success) {
        mCurrentLoadRequest = null;
        if (success) {
            TileSource oldSrc = mCropView.getTileSource();
            mCropView.setTileSource(req.result, null);
            mCropView.setTouchEnabled(req.touchEnabled);
            if (req.moveToLeft) {
                mCropView.moveToLeft();
            }
            if (req.scaleAndOffsetProvider != null) {
                mCropView.setScale(req.scaleAndOffsetProvider.getScale(req.result));
                mCropView.addParallaxOffset(req.scaleAndOffsetProvider.getParallaxOffset());
            }

            // Free last image
            if (oldSrc != null) {
                // Call yield instead of recycle, as we only want to free GL resource.
                // We can still reuse the bitmap for decoding any other image.
                oldSrc.getPreview().yield();
            }
            addReusableBitmap(oldSrc);
        }
        if (req.postExecute != null) {
            req.postExecute.run();
        }
        mProgressView.setVisibility(View.GONE);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public final void setCropViewTileSource(BitmapSource bitmapSource, boolean touchEnabled,
            boolean moveToLeft, CropViewScaleAndOffsetProvider scaleAndOffsetProvider,
            Runnable postExecute) {
        final LoadRequest req = new LoadRequest();
        req.moveToLeft = moveToLeft;
        req.src = bitmapSource;
        req.touchEnabled = touchEnabled;
        req.postExecute = postExecute;
        req.scaleAndOffsetProvider = scaleAndOffsetProvider;
        mCurrentLoadRequest = req;

        // Remove any pending requests
        mLoaderHandler.removeMessages(MSG_LOAD_IMAGE);
        Message.obtain(mLoaderHandler, MSG_LOAD_IMAGE, req).sendToTarget();

        // We don't want to show the spinner every time we load an image, because that would be
        // annoying; instead, only start showing the spinner if loading the image has taken
        // longer than 1 sec (ie 1000 ms)
        mProgressView.postDelayed(new Runnable() {
            public void run() {
                if (mCurrentLoadRequest == req) {
                    mProgressView.setVisibility(View.VISIBLE);
                }
            }
        }, 1000);
    }


    public boolean enableRotation() {
        return getResources().getBoolean(R.bool.allow_rotation);
    }

    public void setWallpaper(Uri uri) {
        int rotation = BitmapUtils.getRotationFromExif(getContext(), uri);
        BitmapCropTask cropTask = new BitmapCropTask(
                getContext(), uri, null, rotation, 0, 0, true, false, null);
        CropAndFinishRunnable onEndCrop = new CropAndFinishRunnable(cropTask.getImageBounds());
        cropTask.setOnEndRunnable(onEndCrop);
        cropTask.setNoCrop(true);
        cropTask.execute();
    }

    public void cropImageAndSetWallpaper(Resources res, int resId) {
        // crop this image and scale it down to the default wallpaper size for
        // this device
        int rotation = BitmapUtils.getRotationFromExif(res, resId, this);
        Point inSize = mCropView.getSourceDimensions();
        Point outSize = WallpaperUtils.getDefaultWallpaperSize(getResources(),
                getWindowManager());
        RectF crop = Utils.getMaxCropRect(
                inSize.x, inSize.y, outSize.x, outSize.y, false);
        // Passing 0, 0 will cause launcher to revert to using the
        // default wallpaper size
        CropAndFinishRunnable onEndCrop = new CropAndFinishRunnable(new Point(0, 0));
        BitmapCropTask cropTask = new BitmapCropTask(getContext(), res, resId,
                crop, rotation, outSize.x, outSize.y, true, false, onEndCrop);
        cropTask.execute();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void cropImageAndSetWallpaper(Uri uri,
            BitmapCropTask.OnBitmapCroppedHandler onBitmapCroppedHandler) {
        boolean centerCrop = getResources().getBoolean(R.bool.center_crop);
        // Get the crop
        boolean ltr = mCropView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;

        Display d = getWindowManager().getDefaultDisplay();

        Point displaySize = new Point();
        d.getSize(displaySize);
        boolean isPortrait = displaySize.x < displaySize.y;

        Point defaultWallpaperSize = WallpaperUtils.getDefaultWallpaperSize(getResources(),
                getWindowManager());
        // Get the crop
        RectF cropRect = mCropView.getCrop();

        Point inSize = mCropView.getSourceDimensions();

        int cropRotation = mCropView.getImageRotation();
        float cropScale = mCropView.getWidth() / (float) cropRect.width();


        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(cropRotation);
        float[] rotatedInSize = new float[] { inSize.x, inSize.y };
        rotateMatrix.mapPoints(rotatedInSize);
        rotatedInSize[0] = Math.abs(rotatedInSize[0]);
        rotatedInSize[1] = Math.abs(rotatedInSize[1]);


        // due to rounding errors in the cropview renderer the edges can be slightly offset
        // therefore we ensure that the boundaries are sanely defined
        cropRect.left = Math.max(0, cropRect.left);
        cropRect.right = Math.min(rotatedInSize[0], cropRect.right);
        cropRect.top = Math.max(0, cropRect.top);
        cropRect.bottom = Math.min(rotatedInSize[1], cropRect.bottom);

        // ADJUST CROP WIDTH
        // Extend the crop all the way to the right, for parallax
        // (or all the way to the left, in RTL)
        float extraSpace;
        if (centerCrop) {
            extraSpace = 2f * Math.min(rotatedInSize[0] - cropRect.right, cropRect.left);
        } else {
            extraSpace = ltr ? rotatedInSize[0] - cropRect.right : cropRect.left;
        }
        // Cap the amount of extra width
        float maxExtraSpace = defaultWallpaperSize.x / cropScale - cropRect.width();
        extraSpace = Math.min(extraSpace, maxExtraSpace);

        if (centerCrop) {
            cropRect.left -= extraSpace / 2f;
            cropRect.right += extraSpace / 2f;
        } else {
            if (ltr) {
                cropRect.right += extraSpace;
            } else {
                cropRect.left -= extraSpace;
            }
        }

        // ADJUST CROP HEIGHT
        if (isPortrait) {
            cropRect.bottom = cropRect.top + defaultWallpaperSize.y / cropScale;
        } else { // LANDSCAPE
            float extraPortraitHeight =
                    defaultWallpaperSize.y / cropScale - cropRect.height();
            float expandHeight =
                    Math.min(Math.min(rotatedInSize[1] - cropRect.bottom, cropRect.top),
                            extraPortraitHeight / 2);
            cropRect.top -= expandHeight;
            cropRect.bottom += expandHeight;
        }

        final int outWidth = (int) Math.round(cropRect.width() * cropScale);
        final int outHeight = (int) Math.round(cropRect.height() * cropScale);
        CropAndFinishRunnable onEndCrop = new CropAndFinishRunnable(new Point(outWidth, outHeight));

        BitmapCropTask cropTask = new BitmapCropTask(getContext(), uri,
                cropRect, cropRotation, outWidth, outHeight, true, false, onEndCrop);
        if (onBitmapCroppedHandler != null) {
            cropTask.setOnBitmapCropped(onBitmapCroppedHandler);
        }
        cropTask.execute();
    }

    private class CropAndFinishRunnable implements Runnable {
        private final Point mBounds;

        public CropAndFinishRunnable(Point bounds) {
            mBounds = bounds;
        }

        @Override
        public void run() {
            WallpaperUtils.saveWallpaperDimensions(mBounds.x, mBounds.y,
                    WallpaperCropActivity.this);
            setResult(Activity.RESULT_OK);
            finish();
        }
    }

    static class LoadRequest {
        BitmapSource src;
        boolean touchEnabled;
        boolean moveToLeft;
        Runnable postExecute;
        CropViewScaleAndOffsetProvider scaleAndOffsetProvider;

        TileSource result;
    }

    public interface CropViewScaleAndOffsetProvider {
        float getScale(TileSource src);
        float getParallaxOffset();
    }
}
