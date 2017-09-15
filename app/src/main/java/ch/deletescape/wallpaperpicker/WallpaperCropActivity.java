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

package ch.deletescape.wallpaperpicker;

import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Toast;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import ch.deletescape.lawnchair.R;
import ch.deletescape.wallpaperpicker.BitmapRegionTileSource.BitmapSource;
import ch.deletescape.wallpaperpicker.BitmapRegionTileSource.BitmapSource.InBitmapProvider;
import ch.deletescape.wallpaperpicker.common.CropAndSetWallpaperTask;
import ch.deletescape.wallpaperpicker.common.DialogUtils;
import ch.deletescape.wallpaperpicker.common.InputStreamProvider;
import ch.deletescape.wallpaperpicker.common.Utils;
import ch.deletescape.wallpaperpicker.views.TiledImageRenderer.TileSource;

public class WallpaperCropActivity extends Activity implements Handler.Callback {
    private static final String LOGTAG = "WallpaperCropActivity";

    private static final int MSG_LOAD_IMAGE = 1;

    protected CropView mCropView;
    protected View mProgressView;
    protected View mSetWallpaperButton;

    private HandlerThread mLoaderThread;
    private Handler mLoaderHandler;
    private LoadRequest mCurrentLoadRequest;
    private byte[] mTempStorageForDecoding = new byte[16 * 1024];
    // A weak-set of reusable bitmaps
    private final Set<Bitmap> mReusableBitmaps =
            Collections.newSetFromMap(new WeakHashMap<Bitmap, Boolean>());

    private final DialogInterface.OnCancelListener mOnDialogCancelListener =
            new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    showActionBarAndTiles();
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLoaderThread = new HandlerThread("wallpaper_loader");
        mLoaderThread.start();
        mLoaderHandler = new Handler(mLoaderThread.getLooper(), this);

        init();
    }

    protected void init() {
        setContentView(R.layout.wallpaper_cropper);

        mCropView = findViewById(R.id.cropView);
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
                        actionBar.hide();
                        // Never fade on finish because we return to the app that started us (e.g.
                        // Photos), not the home screen.
                        cropImageAndSetWallpaper(imageUri, null, false /* shouldFadeOutOnFinish */);
                    }
                });
        mSetWallpaperButton = findViewById(R.id.set_wallpaper_button);

        // Load image in background
        final BitmapRegionTileSource.InputStreamSource bitmapSource =
                new BitmapRegionTileSource.InputStreamSource(this, imageUri);
        mSetWallpaperButton.setEnabled(false);
        Runnable onLoad = new Runnable() {
            @Override
            public void run() {
                if (bitmapSource.getLoadingState() != BitmapSource.State.LOADED) {
                    Toast.makeText(WallpaperCropActivity.this, R.string.wallpaper_load_fail,
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

                req.result = new BitmapRegionTileSource(WallpaperCropActivity.this, req.src,
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

    public boolean isActivityDestroyed() {
        return isDestroyed();
    }

    private void addReusableBitmap(TileSource src) {
        synchronized (mReusableBitmaps) {
            if (src instanceof BitmapRegionTileSource) {
                Bitmap preview = ((BitmapRegionTileSource) src).getBitmap();
                if (preview != null && preview.isMutable()) {
                    mReusableBitmaps.add(preview);
                }
            }
        }
    }

    public DialogInterface.OnCancelListener getOnDialogCancelListener() {
        return mOnDialogCancelListener;
    }

    private void showActionBarAndTiles() {
        getActionBar().show();
        View wallpaperStrip = findViewById(R.id.wallpaper_strip);
        if (wallpaperStrip != null) {
            wallpaperStrip.setVisibility(View.VISIBLE);
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
                TileSource src = req.result;
                Point wallpaperSize = WallpaperUtils.getDefaultWallpaperSize(
                        getResources(), getWindowManager());
                RectF crop = Utils.getMaxCropRect(src.getImageWidth(), src.getImageHeight(),
                        wallpaperSize.x, wallpaperSize.y, false /* leftAligned */);
                mCropView.setScale(req.scaleAndOffsetProvider.getScale());
                mCropView.setParallaxOffset(req.scaleAndOffsetProvider.getParallaxOffset(), crop);
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
            @Override
            public void run() {
                if (mCurrentLoadRequest == req) {
                    mProgressView.setVisibility(View.VISIBLE);
                }
            }
        }, 1000);
    }

    public void cropImageAndSetWallpaper(Uri uri,
                                         CropAndSetWallpaperTask.OnBitmapCroppedHandler onBitmapCroppedHandler,
                                         boolean shouldFadeOutOnFinish) {
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
        float cropScale = mCropView.getWidth() / cropRect.width();

        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(cropRotation);
        float[] rotatedInSize = new float[]{inSize.x, inSize.y};
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
        float extraSpace = ltr ? rotatedInSize[0] - cropRect.right : cropRect.left;
        // Cap the amount of extra width
        float maxExtraSpace = defaultWallpaperSize.x / cropScale - cropRect.width();
        extraSpace = Math.min(extraSpace, maxExtraSpace);

        if (ltr) {
            cropRect.right += extraSpace;
        } else {
            cropRect.left -= extraSpace;
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

        final int outWidth = Math.round(cropRect.width() * cropScale);
        final int outHeight = Math.round(cropRect.height() * cropScale);
        CropAndFinishHandler onEndCrop = new CropAndFinishHandler(
                shouldFadeOutOnFinish);

        CropAndSetWallpaperTask cropTask = new CropAndSetWallpaperTask(
                InputStreamProvider.fromUri(this, uri), this,
                cropRect, cropRotation, outWidth, outHeight, onEndCrop) {
            @Override
            protected void onPreExecute() {
                // Give some feedback so user knows something is happening.
                mProgressView.setVisibility(View.VISIBLE);
            }
        };
        if (onBitmapCroppedHandler != null) {
            cropTask.setOnBitmapCropped(onBitmapCroppedHandler);
        }
        DialogUtils.executeCropTaskAfterPrompt(this, cropTask, getOnDialogCancelListener());
    }

    public void setBoundsAndFinish(boolean overrideTransition) {
        setResult(Activity.RESULT_OK);
        finish();
        if (overrideTransition) {
            overridePendingTransition(0, R.anim.fade_out);
        }
    }

    public class CropAndFinishHandler implements CropAndSetWallpaperTask.OnEndCropHandler {
        private boolean mShouldFadeOutOnFinish;

        /**
         * @param shouldFadeOutOnFinish Whether the wallpaper picker should override the default
         *                              exit animation to fade out instead. This should only be set to true if the wallpaper
         *                              preview will exactly match the actual wallpaper on the page we are returning to.
         */
        public CropAndFinishHandler(boolean shouldFadeOutOnFinish) {
            mShouldFadeOutOnFinish = shouldFadeOutOnFinish;
        }

        @Override
        public void run(boolean cropSucceeded) {
            setBoundsAndFinish(cropSucceeded && mShouldFadeOutOnFinish);
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
        float getScale();

        float getParallaxOffset();
    }
}
