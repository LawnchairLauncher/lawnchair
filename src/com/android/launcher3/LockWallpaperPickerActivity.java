/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import android.animation.Animator;
import android.animation.LayoutTransition;
import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.ThemesContract.ThemesColumns;
import android.util.Log;
import android.util.Pair;
import android.view.ActionMode;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;

import com.android.gallery3d.common.Utils;
import com.android.launcher3.WallpaperCropActivity.BitmapCropTask;
import com.android.launcher3.WallpaperCropActivity.OnBitmapCroppedHandler;
import com.android.photos.BitmapRegionTileSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class LockWallpaperPickerActivity extends WallpaperCropActivity {
    static final String TAG = LockWallpaperPickerActivity.class.getSimpleName();

    public static final int IMAGE_PICK = 5;
    public static final int PICK_WALLPAPER_THIRD_PARTY_ACTIVITY = 6;
    public static final int PICK_LIVE_WALLPAPER = 7;
    private static final String TEMP_WALLPAPER_TILES = "TEMP_KEYGUARD_WALLPAPER_TILES";

    private View mSelectedThumb;
    private boolean mIgnoreNextTap;
    private OnClickListener mThumbnailOnClickListener;

    private LinearLayout mWallpapersView;
    private View mWallpaperStrip;

    private ActionMode.Callback mActionModeCallback;
    private ActionMode mActionMode;

    private View.OnLongClickListener mLongClickListener;

    ArrayList<Uri> mTempWallpaperTiles = new ArrayList<Uri>();
    private SavedWallpaperImages mSavedImages;

    public static class PickImageInfo extends WallpaperTileInfo {
        @Override
        public void onClick(WallpaperCropActivity a) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            Utilities.startActivityForResultSafely(((LockWallpaperPickerActivity)a), intent, IMAGE_PICK);
        }
    }

    public static class UserDesktopWallpaperInfo extends WallpaperTileInfo {
        @Override
        public void onClick(WallpaperCropActivity a) {
            WallpaperManager am = WallpaperManager.getInstance(a);
            am.clearKeyguardWallpaper();
            a.setResult(RESULT_OK);
            a.finish();
        }
    }

    public static class UriWallpaperInfo extends WallpaperTileInfo {
        private Uri mUri;
        public UriWallpaperInfo(Uri uri) {
            mUri = uri;
        }
        @Override
        public void onClick(WallpaperCropActivity a) {
            CropView v = a.getCropView();
            int rotation = WallpaperCropActivity.getRotationFromExif(a, mUri);
            v.setTileSource(new BitmapRegionTileSource(a, mUri, 1024, rotation), null);
            v.setTouchEnabled(true);
        }
        @Override
        public void onSave(final WallpaperCropActivity a) {
            boolean finishActivityWhenDone = true;
            OnBitmapCroppedHandler h = new OnBitmapCroppedHandler() {
                public void onBitmapCropped(byte[] imageBytes) {
                    Point thumbSize = getDefaultThumbnailSize(a.getResources());
                    Bitmap thumb = createThumbnail(
                            thumbSize, null, null, imageBytes, null, 0, 0, true);
                    a.getSavedImages().writeImage(thumb, imageBytes);
                }
            };
            ((LockWallpaperPickerActivity)a).cropImageAndSetWallpaper(mUri, h, finishActivityWhenDone);
        }
        @Override
        public boolean isSelectable() {
            return true;
        }
        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }
    }

    /**
     * For themes which have regular wallpapers
     */
    public static class ThemeWallpaperInfo extends WallpaperTileInfo {
        String mPackageName;
        boolean mIsLegacy;
        Drawable mThumb;
        Context mContext;

        public ThemeWallpaperInfo(Context context, String packageName, boolean legacy, Drawable thumb) {
            this.mContext = context;
            this.mPackageName = packageName;
            this.mIsLegacy = legacy;
            this.mThumb = thumb;
        }

        @Override
        public void onClick(WallpaperCropActivity a) {
            CropView v = a.getCropView();
            try {
                BitmapRegionTileSource source = null;
                if (mIsLegacy) {
                    final PackageManager pm = a.getPackageManager();
                    PackageInfo pi = pm.getPackageInfo(mPackageName, 0);
                    Resources res = a.getPackageManager().getResourcesForApplication(mPackageName);
                    int resId = pi.legacyThemeInfos[0].wallpaperResourceId;

                    int rotation = WallpaperCropActivity.getRotationFromExif(res, resId);
                    source = new BitmapRegionTileSource(
                            res, a, resId, 1024, rotation);
                } else {
                    Resources res = a.getPackageManager().getResourcesForApplication(mPackageName);
                    if (res == null) {
                        return;
                    }

                    int rotation = 0;
                    source = new BitmapRegionTileSource(
                            res, a, "wallpapers", 1024, rotation, true);
                }
                v.setTileSource(source, null);
                v.setTouchEnabled(true);
            } catch (NameNotFoundException e) {
            }
        }

        @Override
        public void onSave(WallpaperCropActivity a) {
            ((LockWallpaperPickerActivity)a).cropImageAndSetWallpaper(
                    "wallpapers",
                    mPackageName,
                    mIsLegacy,
                    true);
        }

        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }

        @Override
        public boolean isSelectable() {
            return true;
        }
    }

    /**
     * For themes that have LOCKSCREEN wallpapers
     */
    public static class ThemeLockWallpaperInfo extends WallpaperTileInfo {
        String mPackageName;
        Drawable mThumb;
        Context mContext;

        public ThemeLockWallpaperInfo(Context context, String packageName, Drawable thumb) {
            this.mContext = context;
            this.mPackageName = packageName;
            this.mThumb = thumb;
        }

        @Override
        public void onClick(WallpaperCropActivity a) {
            CropView v = a.getCropView();
            try {
                BitmapRegionTileSource source = null;
                Resources res = a.getPackageManager().getResourcesForApplication(mPackageName);
                if (res == null) {
                    return;
                }

                int rotation = 0;
                source = new BitmapRegionTileSource(
                        res, a, "lockscreen", 1024, rotation, true);
                v.setTileSource(source, null);
                v.setTouchEnabled(true);
            } catch (NameNotFoundException e) {
            }
        }

        @Override
        public void onSave(WallpaperCropActivity a) {
            ((LockWallpaperPickerActivity)a).cropImageAndSetWallpaper(
                    "lockscreen",
                    mPackageName,
                    false,
                    true);
        }

        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }

        @Override
        public boolean isSelectable() {
            return true;
        }
    }

    public static class ResourceWallpaperInfo extends WallpaperTileInfo {
        private Resources mResources;
        private int mResId;
        private Drawable mThumb;

        public ResourceWallpaperInfo(Resources res, int resId, Drawable thumb) {
            mResources = res;
            mResId = resId;
            mThumb = thumb;
        }
        @Override
        public void onClick(WallpaperCropActivity a) {
            int rotation = WallpaperCropActivity.getRotationFromExif(mResources, mResId);
            BitmapRegionTileSource source = new BitmapRegionTileSource(
                    mResources, a, mResId, 1024, rotation);
            CropView v = a.getCropView();
            v.setTileSource(source, null);
            Point wallpaperSize = WallpaperCropActivity.getDefaultWallpaperSize(
                    a.getResources(), a.getWindowManager());
            RectF crop = WallpaperCropActivity.getMaxCropRect(
                    source.getImageWidth(), source.getImageHeight(),
                    wallpaperSize.x, wallpaperSize.y, false);
            v.setScale(wallpaperSize.x / crop.width());
            v.setTouchEnabled(false);
        }
        @Override
        public void onSave(WallpaperCropActivity a) {
            boolean finishActivityWhenDone = true;
            ((LockWallpaperPickerActivity)a).cropImageAndSetWallpaper(mResources, mResId, finishActivityWhenDone);
        }
        @Override
        public boolean isSelectable() {
            return true;
        }
        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }
    }

    protected void cropImageAndSetWallpaper(String path, String packageName, final boolean legacy,
            final boolean finishActivityWhenDone) {

        Point outSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(outSize);

        final int outWidth = outSize.x;
        final int outHeight = outSize.y;
        Runnable onEndCrop = new Runnable() {
            public void run() {
                if (finishActivityWhenDone) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        };

        RectF cropRect = new RectF(mCropView.getCrop());
        BitmapCropTask cropTask = null;
        try {
            if (legacy) {
                final PackageManager pm = getPackageManager();
                PackageInfo pi = pm.getPackageInfo(packageName, 0);
                Resources res = getPackageManager().getResourcesForApplication(packageName);
                int resId = pi.legacyThemeInfos[0].wallpaperResourceId;
                cropTask = new BitmapCropTask(this, res, resId,
                        cropRect, 0, outWidth, outHeight, true, false, onEndCrop);
            } else {
                Resources res = getPackageManager().getResourcesForApplication(packageName);
                if (res == null) {
                    return;
                }
                cropTask = new BitmapCropTask(this, res, path, cropRect,
                        0, outWidth, outHeight, true, false, onEndCrop);
            }
        } catch (NameNotFoundException e) {
            return;
        }

        if (cropTask != null) {
            cropTask.execute();
        }
    }

    @Override
    protected void cropImageAndSetWallpaper(Uri uri,
            OnBitmapCroppedHandler onBitmapCroppedHandler, final boolean finishActivityWhenDone) {
        // Get the crop
        boolean ltr = mCropView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;

        Point minDims = new Point();
        Point maxDims = new Point();
        Display d = getWindowManager().getDefaultDisplay();
        d.getCurrentSizeRange(minDims, maxDims);

        Point displaySize = new Point();
        d.getSize(displaySize);

        int maxDim = Math.max(maxDims.x, maxDims.y);
        final int minDim = Math.min(minDims.x, minDims.y);
        int defaultWallpaperWidth;
        if (isScreenLarge(getResources())) {
            defaultWallpaperWidth = (int) (maxDim *
                    wallpaperTravelToScreenWidthRatio(maxDim, minDim));
        } else {
            defaultWallpaperWidth = Math.max((int)
                    (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
        }

        boolean isPortrait = displaySize.x < displaySize.y;
        int portraitHeight;
        if (isPortrait) {
            portraitHeight = mCropView.getHeight();
        } else {
            // TODO: how to actually get the proper portrait height?
            // This is not quite right:
            portraitHeight = Math.max(maxDims.x, maxDims.y);
        }
        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Point realSize = new Point();
            d.getRealSize(realSize);
            portraitHeight = Math.max(realSize.x, realSize.y);
        }
        // Get the crop
        RectF cropRect = mCropView.getCrop();
        int cropRotation = mCropView.getImageRotation();
        float cropScale = mCropView.getWidth() / (float) cropRect.width();

        Point inSize = mCropView.getSourceDimensions();
        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(cropRotation);
        float[] rotatedInSize = new float[] { inSize.x, inSize.y };
        rotateMatrix.mapPoints(rotatedInSize);
        rotatedInSize[0] = Math.abs(rotatedInSize[0]);
        rotatedInSize[1] = Math.abs(rotatedInSize[1]);

        // ADJUST CROP WIDTH
        // Extend the crop all the way to the right, for parallax
        // (or all the way to the left, in RTL)
        float extraSpace = ltr ? rotatedInSize[0] - cropRect.right : cropRect.left;
        // Cap the amount of extra width
        float maxExtraSpace = defaultWallpaperWidth / cropScale - cropRect.width();
        extraSpace = Math.min(extraSpace, maxExtraSpace);

        if (ltr) {
            cropRect.right += extraSpace;
        } else {
            cropRect.left -= extraSpace;
        }

        // ADJUST CROP HEIGHT
        if (isPortrait) {
            cropRect.bottom = cropRect.top + portraitHeight / cropScale;
        } else { // LANDSCAPE
            float extraPortraitHeight =
                    portraitHeight / cropScale - cropRect.height();
            float expandHeight =
                    Math.min(Math.min(rotatedInSize[1] - cropRect.bottom, cropRect.top),
                            extraPortraitHeight / 2);
            cropRect.top -= expandHeight;
            cropRect.bottom += expandHeight;
        }
        final int outWidth = (int) Math.round(cropRect.width() * cropScale);
        final int outHeight = (int) Math.round(cropRect.height() * cropScale);

        Runnable onEndCrop = new Runnable() {
            public void run() {
                updateWallpaperDimensions(outWidth, outHeight);
                if (finishActivityWhenDone) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        };
        BitmapCropTask cropTask = new BitmapCropTask(this, uri,
                cropRect, cropRotation, outWidth, outHeight, true, false, onEndCrop);
        if (onBitmapCroppedHandler != null) {
            cropTask.setOnBitmapCropped(onBitmapCroppedHandler);
        }
        cropTask.execute();
    }

    @Override
    protected void setWallpaper(String filePath, final boolean finishActivityWhenDone) {
        int rotation = getRotationFromExif(filePath);
        BitmapCropTask cropTask = new BitmapCropTask(
                this, filePath, null, rotation, 0, 0, true, false, null);
        final Point bounds = cropTask.getImageBounds();
        Runnable onEndCrop = new Runnable() {
            public void run() {
                updateWallpaperDimensions(bounds.x, bounds.y);
                if (finishActivityWhenDone) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        };
        cropTask.setOnEndRunnable(onEndCrop);
        cropTask.setNoCrop(true);
        cropTask.execute();
    }

    protected static class BitmapCropTask extends AsyncTask<Void, Void, Boolean> {
        Uri mInUri = null;
        Context mContext;
        String mInFilePath;
        byte[] mInImageBytes;
        int mInResId = 0;
        InputStream mInStream;
        RectF mCropBounds = null;
        int mOutWidth, mOutHeight;
        int mRotation;
        String mOutputFormat = "jpg"; // for now
        boolean mSetWallpaper;
        boolean mSaveCroppedBitmap;
        Bitmap mCroppedBitmap;
        Runnable mOnEndRunnable;
        Resources mResources;
        OnBitmapCroppedHandler mOnBitmapCroppedHandler;
        boolean mNoCrop;
        boolean mImageFromAsset;

        public BitmapCropTask(Context c, Resources res , String assetPath,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mResources = res;
            mInFilePath = assetPath;
            mImageFromAsset = true;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(Context c, String filePath,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInFilePath = filePath;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(byte[] imageBytes,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mInImageBytes = imageBytes;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(Context c, Uri inUri,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInUri = inUri;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(Context c, Resources res, int inResId,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInResId = inResId;
            mResources = res;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        private void init(RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mCropBounds = cropBounds;
            mRotation = rotation;
            mOutWidth = outWidth;
            mOutHeight = outHeight;
            mSetWallpaper = setWallpaper;
            mSaveCroppedBitmap = saveCroppedBitmap;
            mOnEndRunnable = onEndRunnable;
        }

        public void setOnBitmapCropped(OnBitmapCroppedHandler handler) {
            mOnBitmapCroppedHandler = handler;
        }

        public void setNoCrop(boolean value) {
            mNoCrop = value;
        }

        public void setOnEndRunnable(Runnable onEndRunnable) {
            mOnEndRunnable = onEndRunnable;
        }

        // Helper to setup input stream
        private void regenerateInputStream() {
            if (mInUri == null && mInResId == 0 && mInFilePath == null && mInImageBytes == null && !mImageFromAsset) {
                Log.w(TAG, "cannot read original file, no input URI, resource ID, or " +
                        "image byte array given");
            } else {
                Utils.closeSilently(mInStream);
                try {
                    if (mImageFromAsset) {
                        AssetManager am = mResources.getAssets();
                        String[] pathImages = am.list(mInFilePath);
                        if (pathImages == null || pathImages.length == 0) {
                            throw new IOException("did not find any images in path: " + mInFilePath);
                        }
                        InputStream is = am.open(mInFilePath + File.separator + pathImages[0]);
                        mInStream = new BufferedInputStream(is);
                    } else if (mInUri != null) {
                        mInStream = new BufferedInputStream(
                                mContext.getContentResolver().openInputStream(mInUri));
                    } else if (mInFilePath != null) {
                        mInStream = mContext.openFileInput(mInFilePath);
                    } else if (mInImageBytes != null) {
                        mInStream = new BufferedInputStream(
                                new ByteArrayInputStream(mInImageBytes));
                    } else {
                        mInStream = new BufferedInputStream(
                                mResources.openRawResource(mInResId));
                    }
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "cannot read file: " + mInUri.toString(), e);
                } catch (IOException e) {
                    Log.w(TAG, "cannot read file: " + mInUri.toString(), e);
                }
            }
        }

        public Point getImageBounds() {
            regenerateInputStream();
            if (mInStream != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(mInStream, null, options);
                if (options.outWidth != 0 && options.outHeight != 0) {
                    return new Point(options.outWidth, options.outHeight);
                }
            }
            return null;
        }

        public void setCropBounds(RectF cropBounds) {
            mCropBounds = cropBounds;
        }

        public Bitmap getCroppedBitmap() {
            return mCroppedBitmap;
        }
        public boolean cropBitmap() {
            boolean failure = false;

            regenerateInputStream();

            WallpaperManager wallpaperManager = null;
            if (mSetWallpaper) {
                wallpaperManager = WallpaperManager.getInstance(mContext.getApplicationContext());
            }
            if (mSetWallpaper && mNoCrop && mInStream != null) {
                try {
                    wallpaperManager.setKeyguardStream(mInStream);
                } catch (IOException e) {
                    Log.w(TAG, "cannot write stream to wallpaper", e);
                    failure = true;
                }
                return !failure;
            }
            if (mInStream != null) {
                // Find crop bounds (scaled to original image size)
                Rect roundedTrueCrop = new Rect();
                Matrix rotateMatrix = new Matrix();
                Matrix inverseRotateMatrix = new Matrix();
                if (mRotation > 0) {
                    rotateMatrix.setRotate(mRotation);
                    inverseRotateMatrix.setRotate(-mRotation);

                    mCropBounds.roundOut(roundedTrueCrop);
                    mCropBounds = new RectF(roundedTrueCrop);

                    Point bounds = getImageBounds();

                    float[] rotatedBounds = new float[] { bounds.x, bounds.y };
                    rotateMatrix.mapPoints(rotatedBounds);
                    rotatedBounds[0] = Math.abs(rotatedBounds[0]);
                    rotatedBounds[1] = Math.abs(rotatedBounds[1]);

                    mCropBounds.offset(-rotatedBounds[0]/2, -rotatedBounds[1]/2);
                    inverseRotateMatrix.mapRect(mCropBounds);
                    mCropBounds.offset(bounds.x/2, bounds.y/2);

                    regenerateInputStream();
                }

                mCropBounds.roundOut(roundedTrueCrop);

                if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
                    Log.w(TAG, "crop has bad values for full size image");
                    failure = true;
                    return false;
                }

                // See how much we're reducing the size of the image
                int scaleDownSampleSize = Math.min(roundedTrueCrop.width() / mOutWidth,
                        roundedTrueCrop.height() / mOutHeight);

                // Attempt to open a region decoder
                BitmapRegionDecoder decoder = null;
                try {
                    decoder = BitmapRegionDecoder.newInstance(mInStream, true);
                } catch (IOException e) {
                    Log.w(TAG, "cannot open region decoder for file: " + mInUri.toString(), e);
                }

                Bitmap crop = null;
                if (decoder != null) {
                    // Do region decoding to get crop bitmap
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (scaleDownSampleSize > 1) {
                        options.inSampleSize = scaleDownSampleSize;
                    }
                    crop = decoder.decodeRegion(roundedTrueCrop, options);
                    decoder.recycle();
                }

                if (crop == null) {
                    // BitmapRegionDecoder has failed, try to crop in-memory
                    regenerateInputStream();
                    Bitmap fullSize = null;
                    if (mInStream != null) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        if (scaleDownSampleSize > 1) {
                            options.inSampleSize = scaleDownSampleSize;
                        }
                        fullSize = BitmapFactory.decodeStream(mInStream, null, options);
                    }
                    if (fullSize != null) {
                        mCropBounds.left /= scaleDownSampleSize;
                        mCropBounds.top /= scaleDownSampleSize;
                        mCropBounds.bottom /= scaleDownSampleSize;
                        mCropBounds.right /= scaleDownSampleSize;
                        mCropBounds.roundOut(roundedTrueCrop);

                        crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left,
                                roundedTrueCrop.top, roundedTrueCrop.width(),
                                roundedTrueCrop.height());
                    }
                }

                if (crop == null) {
                    Log.w(TAG, "cannot decode file: " + mInUri.toString());
                    failure = true;
                    return false;
                }
                if (mOutWidth > 0 && mOutHeight > 0 || mRotation > 0) {
                    float[] dimsAfter = new float[] { crop.getWidth(), crop.getHeight() };
                    rotateMatrix.mapPoints(dimsAfter);
                    dimsAfter[0] = Math.abs(dimsAfter[0]);
                    dimsAfter[1] = Math.abs(dimsAfter[1]);

                    if (!(mOutWidth > 0 && mOutHeight > 0)) {
                        mOutWidth = Math.round(dimsAfter[0]);
                        mOutHeight = Math.round(dimsAfter[1]);
                    }

                    RectF cropRect = new RectF(0, 0, dimsAfter[0], dimsAfter[1]);
                    RectF returnRect = new RectF(0, 0, mOutWidth, mOutHeight);

                    Matrix m = new Matrix();
                    if (mRotation == 0) {
                        m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
                    } else {
                        Matrix m1 = new Matrix();
                        m1.setTranslate(-crop.getWidth() / 2f, -crop.getHeight() / 2f);
                        Matrix m2 = new Matrix();
                        m2.setRotate(mRotation);
                        Matrix m3 = new Matrix();
                        m3.setTranslate(dimsAfter[0] / 2f, dimsAfter[1] / 2f);
                        Matrix m4 = new Matrix();
                        m4.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);

                        Matrix c1 = new Matrix();
                        c1.setConcat(m2, m1);
                        Matrix c2 = new Matrix();
                        c2.setConcat(m4, m3);
                        m.setConcat(c2, c1);
                    }

                    Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
                            (int) returnRect.height(), Bitmap.Config.ARGB_8888);
                    if (tmp != null) {
                        Canvas c = new Canvas(tmp);
                        Paint p = new Paint();
                        p.setFilterBitmap(true);
                        c.drawBitmap(crop, m, p);
                        crop = tmp;
                    }
                }

                if (mSaveCroppedBitmap) {
                    mCroppedBitmap = crop;
                }

                // Get output compression format
                CompressFormat cf =
                        convertExtensionToCompressFormat(getFileExtension(mOutputFormat));

                // Compress to byte array
                ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
                if (crop.compress(cf, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
                    // If we need to set to the wallpaper, set it
                    if (mSetWallpaper && wallpaperManager != null) {
                        try {
                            byte[] outByteArray = tmpOut.toByteArray();
                            wallpaperManager.setKeyguardStream(new ByteArrayInputStream(outByteArray));
                            if (mOnBitmapCroppedHandler != null) {
                                mOnBitmapCroppedHandler.onBitmapCropped(outByteArray);
                            }
                        } catch (IOException e) {
                            Log.w(TAG, "cannot write stream to wallpaper", e);
                            failure = true;
                        }
                    }
                } else {
                    Log.w(TAG, "cannot compress bitmap");
                    failure = true;
                }
            } else {
                Log.w(TAG, "could not complete crop task because input stream is null");
            }
            return !failure; // True if any of the operations failed
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return cropBitmap();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mOnEndRunnable != null) {
                mOnEndRunnable.run();
            }
        }
    }

    @Override
    protected void setWallpaperStripYOffset(int offset) {
        mWallpaperStrip.setPadding(0, 0, 0, offset);
    }

    // called by onCreate; this is subclassed to overwrite WallpaperCropActivity
    protected void init() {
        setContentView(R.layout.wallpaper_picker);

        mCropView = (CropView) findViewById(R.id.cropView);
        mWallpaperStrip = findViewById(R.id.wallpaper_strip);
        mCropView.setTouchCallback(new CropView.TouchCallback() {
            LauncherViewPropertyAnimator mAnim;
            @Override
            public void onTouchDown() {
                if (mAnim != null) {
                    mAnim.cancel();
                }
                if (mWallpaperStrip.getAlpha() == 1f) {
                    mIgnoreNextTap = true;
                }
                mAnim = new LauncherViewPropertyAnimator(mWallpaperStrip);
                mAnim.alpha(0f)
                     .setDuration(150)
                     .addListener(new Animator.AnimatorListener() {
                         public void onAnimationStart(Animator animator) { }
                         public void onAnimationEnd(Animator animator) {
                             mWallpaperStrip.setVisibility(View.INVISIBLE);
                         }
                         public void onAnimationCancel(Animator animator) { }
                         public void onAnimationRepeat(Animator animator) { }
                     });
                mAnim.setInterpolator(new AccelerateInterpolator(0.75f));
                mAnim.start();
            }
            @Override
            public void onTouchUp() {
                mIgnoreNextTap = false;
            }
            @Override
            public void onTap() {
                boolean ignoreTap = mIgnoreNextTap;
                mIgnoreNextTap = false;
                if (!ignoreTap) {
                    if (mAnim != null) {
                        mAnim.cancel();
                    }
                    mWallpaperStrip.setVisibility(View.VISIBLE);
                    mAnim = new LauncherViewPropertyAnimator(mWallpaperStrip);
                    mAnim.alpha(1f)
                         .setDuration(150)
                         .setInterpolator(new DecelerateInterpolator(0.75f));
                    mAnim.start();
                }
            }
        });

        mThumbnailOnClickListener = new OnClickListener() {
            public void onClick(View v) {
                if (mActionMode != null) {
                    // When CAB is up, clicking toggles the item instead
                    if (v.isLongClickable()) {
                        mLongClickListener.onLongClick(v);
                    }
                    return;
                }
                WallpaperTileInfo info = (WallpaperTileInfo) v.getTag();
                if (info.isSelectable()) {
                    if (mSelectedThumb != null) {
                        mSelectedThumb.setSelected(false);
                        mSelectedThumb = null;
                    }
                    mSelectedThumb = v;
                    v.setSelected(true);
                    // TODO: Remove this once the accessibility framework and
                    // services have better support for selection state.
                    v.announceForAccessibility(
                            getString(R.string.announce_selection, v.getContentDescription()));
                }
                info.onClick(LockWallpaperPickerActivity.this);
            }
        };
        mLongClickListener = new View.OnLongClickListener() {
            // Called when the user long-clicks on someView
            public boolean onLongClick(View view) {
                CheckableFrameLayout c = (CheckableFrameLayout) view;
                c.toggle();

                if (mActionMode != null) {
                    mActionMode.invalidate();
                } else {
                    // Start the CAB using the ActionMode.Callback defined below
                    mActionMode = startActionMode(mActionModeCallback);
                    int childCount = mWallpapersView.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        mWallpapersView.getChildAt(i).setSelected(false);
                    }
                }
                return true;
            }
        };

        mWallpapersView = (LinearLayout) findViewById(R.id.wallpaper_list);

        // Add a tile for the Gallery
        LinearLayout masterWallpaperList = (LinearLayout) findViewById(R.id.master_wallpaper_list);
        FrameLayout pickImageTile = (FrameLayout) getLayoutInflater().
                inflate(R.layout.wallpaper_picker_image_picker_item, masterWallpaperList, false);
        setWallpaperItemPaddingToZero(pickImageTile);
        masterWallpaperList.addView(pickImageTile, 0);

        // Add tile for clear image
        FrameLayout clearImageTile = (FrameLayout) getLayoutInflater().
                inflate(R.layout.wallpaper_picker_clear, masterWallpaperList, false);
        setWallpaperItemPaddingToZero(clearImageTile);
        masterWallpaperList.addView(clearImageTile, 0);

        // theme LOCKSCREEN wallpapers
        ArrayList<ThemeLockWallpaperInfo> themeLockWallpapers = findThemeLockWallpapers();
        ThemeLockWallpapersAdapter tla = new ThemeLockWallpapersAdapter(this, themeLockWallpapers);
        populateWallpapersFromAdapter(mWallpapersView, tla, false, true);

        // theme wallpapers
        ArrayList<ThemeWallpaperInfo> themeWallpapers = findThemeWallpapers();
        ThemeWallpapersAdapter ta = new ThemeWallpapersAdapter(this, themeWallpapers);
        populateWallpapersFromAdapter(mWallpapersView, ta, false, true);

        // Populate the saved wallpapers
        mSavedImages = new SavedWallpaperImages(this);
        mSavedImages.loadThumbnailsAndImageIdList();
        populateWallpapersFromAdapter(mWallpapersView, mSavedImages, true, true);

        // Make its background the last photo taken on external storage
        Bitmap lastPhoto = getThumbnailOfLastPhoto();
        if (lastPhoto != null) {
            ImageView galleryThumbnailBg =
                    (ImageView) pickImageTile.findViewById(R.id.wallpaper_image);
            galleryThumbnailBg.setImageBitmap(getThumbnailOfLastPhoto());
            int colorOverlay = getResources().getColor(R.color.wallpaper_picker_translucent_gray);
            galleryThumbnailBg.setColorFilter(colorOverlay, PorterDuff.Mode.SRC_ATOP);
        }

        PickImageInfo pickImageInfo = new PickImageInfo();
        pickImageTile.setTag(pickImageInfo);
        pickImageInfo.setView(pickImageTile);
        pickImageTile.setOnClickListener(mThumbnailOnClickListener);
        pickImageInfo.setView(pickImageTile);

        UserDesktopWallpaperInfo clearImageInfo = new UserDesktopWallpaperInfo();
        clearImageTile.setTag(clearImageInfo);
        clearImageInfo.setView(clearImageTile);
        clearImageTile.setOnClickListener(mThumbnailOnClickListener);
        clearImageInfo.setView(clearImageTile);

        updateTileIndices();

        // Update the scroll for RTL
        initializeScrollForRtl();

        // Create smooth layout transitions for when items are deleted
        final LayoutTransition transitioner = new LayoutTransition();
        transitioner.setDuration(200);
        transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
        mWallpapersView.setLayoutTransition(transitioner);

        // Action bar
        // Show the custom action bar view
        final ActionBar actionBar = getActionBar();
        actionBar.setCustomView(R.layout.actionbar_set_wallpaper);
        actionBar.getCustomView().setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mSelectedThumb != null) {
                            WallpaperTileInfo info = (WallpaperTileInfo) mSelectedThumb.getTag();
                            info.onSave(LockWallpaperPickerActivity.this);
                        }
                    }
                });

        // CAB for deleting items
        mActionModeCallback = new ActionMode.Callback() {
            // Called when the action mode is created; startActionMode() was called
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate a menu resource providing context menu items
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.cab_delete_wallpapers, menu);
                return true;
            }

            private int numCheckedItems() {
                int childCount = mWallpapersView.getChildCount();
                int numCheckedItems = 0;
                for (int i = 0; i < childCount; i++) {
                    CheckableFrameLayout c = (CheckableFrameLayout) mWallpapersView.getChildAt(i);
                    if (c.isChecked()) {
                        numCheckedItems++;
                    }
                }
                return numCheckedItems;
            }

            // Called each time the action mode is shown. Always called after onCreateActionMode,
            // but may be called multiple times if the mode is invalidated.
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                int numCheckedItems = numCheckedItems();
                if (numCheckedItems == 0) {
                    mode.finish();
                    return true;
                } else {
                    mode.setTitle(getResources().getQuantityString(
                            R.plurals.number_of_items_selected, numCheckedItems, numCheckedItems));
                    return true;
                }
            }

            // Called when the user selects a contextual menu item
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_delete) {
                    int childCount = mWallpapersView.getChildCount();
                    ArrayList<View> viewsToRemove = new ArrayList<View>();
                    for (int i = 0; i < childCount; i++) {
                        CheckableFrameLayout c =
                                (CheckableFrameLayout) mWallpapersView.getChildAt(i);
                        if (c.isChecked()) {
                            WallpaperTileInfo info = (WallpaperTileInfo) c.getTag();
                            info.onDelete(LockWallpaperPickerActivity.this);
                            viewsToRemove.add(c);
                        }
                    }
                    for (View v : viewsToRemove) {
                        mWallpapersView.removeView(v);
                    }
                    updateTileIndices();
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                } else {
                    return false;
                }
            }

            // Called when the user exits the action mode
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                int childCount = mWallpapersView.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    CheckableFrameLayout c = (CheckableFrameLayout) mWallpapersView.getChildAt(i);
                    c.setChecked(false);
                }
                mSelectedThumb.setSelected(true);
                mActionMode = null;
            }
        };
    }

    private void initializeScrollForRtl() {
        final HorizontalScrollView scroll =
                (HorizontalScrollView) findViewById(R.id.wallpaper_scroll_container);

        if (scroll.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            final ViewTreeObserver observer = scroll.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    LinearLayout masterWallpaperList =
                            (LinearLayout) findViewById(R.id.master_wallpaper_list);
                    scroll.scrollTo(masterWallpaperList.getWidth(), 0);
                    scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }

    public boolean enableRotation() {
        return super.enableRotation() || Launcher.sForceEnableRotation;
    }

    protected Bitmap getThumbnailOfLastPhoto() {
        Cursor cursor = MediaStore.Images.Media.query(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.ImageColumns._ID,
                    MediaStore.Images.ImageColumns.DATE_TAKEN},
                null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT 1");
        Bitmap thumb = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(0);
                thumb = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(),
                        id, MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
            cursor.close();
        }
        return thumb;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void onStop() {
        super.onStop();
        mWallpaperStrip = findViewById(R.id.wallpaper_strip);
        if (mWallpaperStrip.getAlpha() < 1f) {
            mWallpaperStrip.setAlpha(1f);
            mWallpaperStrip.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        mWallpapersView.removeAllViews();
        super.onDestroy();
    }

    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(TEMP_WALLPAPER_TILES, mTempWallpaperTiles);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        ArrayList<Uri> uris = savedInstanceState.getParcelableArrayList(TEMP_WALLPAPER_TILES);
        for (Uri uri : uris) {
            addTemporaryWallpaperTile(uri);
        }
    }

    private void populateWallpapersFromAdapter(ViewGroup parent, BaseAdapter adapter,
            boolean addLongPressHandler, boolean selectFirstTile) {
        for (int i = 0; i < adapter.getCount(); i++) {
            FrameLayout thumbnail = (FrameLayout) adapter.getView(i, null, parent);
            parent.addView(thumbnail, i);
            WallpaperTileInfo info = (WallpaperTileInfo) adapter.getItem(i);
            thumbnail.setTag(info);
            info.setView(thumbnail);
            if (addLongPressHandler) {
                addLongPressHandler(thumbnail);
            }
            thumbnail.setOnClickListener(mThumbnailOnClickListener);
            if (i == 0 && selectFirstTile) {
                mThumbnailOnClickListener.onClick(thumbnail);
            }
        }
    }

    private void updateTileIndices() {
        LinearLayout masterWallpaperList = (LinearLayout) findViewById(R.id.master_wallpaper_list);
        final int childCount = masterWallpaperList.getChildCount();
        final Resources res = getResources();

        // Do two passes; the first pass gets the total number of tiles
        int numTiles = 0;
        for (int passNum = 0; passNum < 2; passNum++) {
            int tileIndex = 0;
            for (int i = 0; i < childCount; i++) {
                View child = masterWallpaperList.getChildAt(i);
                LinearLayout subList;

                int subListStart;
                int subListEnd;
                if (child.getTag() instanceof WallpaperTileInfo) {
                    subList = masterWallpaperList;
                    subListStart = i;
                    subListEnd = i + 1;
                } else { // if (child instanceof LinearLayout) {
                    subList = (LinearLayout) child;
                    subListStart = 0;
                    subListEnd = subList.getChildCount();
                }

                for (int j = subListStart; j < subListEnd; j++) {
                    WallpaperTileInfo info = (WallpaperTileInfo) subList.getChildAt(j).getTag();
                    if (info.isNamelessWallpaper()) {
                        if (passNum == 0) {
                            numTiles++;
                        } else {
                            CharSequence label = res.getString(
                                    R.string.wallpaper_accessibility_name, ++tileIndex, numTiles);
                            info.onIndexUpdated(label);
                        }
                    }
                }
            }
        }
    }

    private static Point getDefaultThumbnailSize(Resources res) {
        return new Point(res.getDimensionPixelSize(R.dimen.wallpaperThumbnailWidth),
                res.getDimensionPixelSize(R.dimen.wallpaperThumbnailHeight));
    }

    private static Bitmap createThumbnail(Point size, Context context, Uri uri, byte[] imageBytes,
            Resources res, int resId, int rotation, boolean leftAligned) {
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

        RectF cropRect = WallpaperCropActivity.getMaxCropRect(
                (int) rotatedBounds[0], (int) rotatedBounds[1], width, height, leftAligned);
        cropTask.setCropBounds(cropRect);

        if (cropTask.cropBitmap()) {
            return cropTask.getCroppedBitmap();
        } else {
            return null;
        }
    }

    private void addTemporaryWallpaperTile(Uri uri) {
        mTempWallpaperTiles.add(uri);
        // Add a tile for the image picked from Gallery
        FrameLayout pickedImageThumbnail = (FrameLayout) getLayoutInflater().
                inflate(R.layout.wallpaper_picker_item, mWallpapersView, false);
        setWallpaperItemPaddingToZero(pickedImageThumbnail);

        // Load the thumbnail
        ImageView image = (ImageView) pickedImageThumbnail.findViewById(R.id.wallpaper_image);
        Point defaultSize = getDefaultThumbnailSize(this.getResources());
        int rotation = WallpaperCropActivity.getRotationFromExif(this, uri);
        Bitmap thumb = createThumbnail(defaultSize, this, uri, null, null, 0, rotation, false);
        if (thumb != null) {
            image.setImageBitmap(thumb);
            Drawable thumbDrawable = image.getDrawable();
            thumbDrawable.setDither(true);
        } else {
            Log.e(TAG, "Error loading thumbnail for uri=" + uri);
        }
        mWallpapersView.addView(pickedImageThumbnail, 0);

        UriWallpaperInfo info = new UriWallpaperInfo(uri);
        pickedImageThumbnail.setTag(info);
        info.setView(pickedImageThumbnail);
        addLongPressHandler(pickedImageThumbnail);
        updateTileIndices();
        pickedImageThumbnail.setOnClickListener(mThumbnailOnClickListener);
        mThumbnailOnClickListener.onClick(pickedImageThumbnail);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_PICK && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                addTemporaryWallpaperTile(uri);
            }
        } else if (requestCode == PICK_WALLPAPER_THIRD_PARTY_ACTIVITY) {
            setResult(RESULT_OK);
            finish();
        }
    }

    static void setWallpaperItemPaddingToZero(FrameLayout frameLayout) {
        frameLayout.setPadding(0, 0, 0, 0);
        frameLayout.setForeground(new ZeroPaddingDrawable(frameLayout.getForeground()));
    }

    private void addLongPressHandler(View v) {
        v.setOnLongClickListener(mLongClickListener);
    }

    private ArrayList<ThemeWallpaperInfo> findThemeWallpapers() {
        ArrayList<ThemeWallpaperInfo> themeWallpapers =
                new ArrayList<ThemeWallpaperInfo>();
        ContentResolver cr = getContentResolver();
        String[] projection = { ThemesColumns.PKG_NAME, ThemesColumns.IS_LEGACY_THEME };
        String selection = ThemesColumns.MODIFIES_LAUNCHER + "=? AND " +
                ThemesColumns.PKG_NAME + "!=?";
        String[] selectoinArgs = {"1", "default"};
        String sortOrder = null;
        Cursor c = cr.query(ThemesColumns.CONTENT_URI, projection, selection,
                selectoinArgs, sortOrder);
        if (c != null) {
            Bitmap bmp;
            while (c.moveToNext()) {
                String pkgName = c.getString(c.getColumnIndexOrThrow(ThemesColumns.PKG_NAME));
                boolean isLegacy = c.getInt(c.getColumnIndexOrThrow(
                        ThemesColumns.IS_LEGACY_THEME)) == 1;
                    bmp = getThemeWallpaper(this, "wallpapers", pkgName, isLegacy, true /* thumb*/);
                    themeWallpapers.add(
                            new ThemeWallpaperInfo(this, pkgName, isLegacy,
                                    new BitmapDrawable(getResources(), bmp)));
                    if (bmp != null) {
                        Log.d("", String.format("Loaded bitmap of size %dx%d for %s",
                                bmp.getWidth(), bmp.getHeight(), pkgName));
                    }
            }
            c.close();
        }
        return themeWallpapers;
    }

    private ArrayList<ThemeLockWallpaperInfo> findThemeLockWallpapers() {
        ArrayList<ThemeLockWallpaperInfo> themeWallpapers =
                new ArrayList<ThemeLockWallpaperInfo>();
        ContentResolver cr = getContentResolver();
        String[] projection = { ThemesColumns.PKG_NAME };
        String selection = ThemesColumns.MODIFIES_LOCKSCREEN + "=? AND " +
                ThemesColumns.PKG_NAME + "!=?";
        String[] selectoinArgs = {"1", "default"};
        String sortOrder = null;
        Cursor c = cr.query(ThemesColumns.CONTENT_URI, projection, selection,
                selectoinArgs, sortOrder);
        if (c != null) {
            Bitmap bmp;
            while (c.moveToNext()) {
                String pkgName = c.getString(c.getColumnIndexOrThrow(ThemesColumns.PKG_NAME));
                    bmp = getThemeWallpaper(this, "lockscreen", pkgName, false, true /* thumb*/);
                    themeWallpapers.add(
                            new ThemeLockWallpaperInfo(this, pkgName,
                                    new BitmapDrawable(getResources(), bmp)));
                    if (bmp != null) {
                        Log.d("", String.format("Loaded bitmap of size %dx%d for %s",
                                bmp.getWidth(), bmp.getHeight(), pkgName));
                    }
            }
            c.close();
        }
        return themeWallpapers;
    }

    public Pair<ApplicationInfo, Integer> getWallpaperArrayResourceId() {
        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher3; Resources needs the real package name,
        // com.android.launcher3. So we ask Resources for what it thinks the
        // package name should be.
        final String packageName = getResources().getResourcePackageName(R.array.wallpapers);
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return new Pair<ApplicationInfo, Integer>(info, R.array.wallpapers);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public CropView getCropView() {
        return mCropView;
    }

    public SavedWallpaperImages getSavedImages() {
        return mSavedImages;
    }

    static class ZeroPaddingDrawable extends LevelListDrawable {
        public ZeroPaddingDrawable(Drawable d) {
            super();
            addLevel(0, 0, d);
            setLevel(0);
        }

        @Override
        public boolean getPadding(Rect padding) {
            padding.set(0, 0, 0, 0);
            return true;
        }
    }

    private static class ThemeLockWallpapersAdapter extends BaseAdapter implements ListAdapter {
        private LayoutInflater mLayoutInflater;
        private ArrayList<ThemeLockWallpaperInfo> mWallpapers;

        ThemeLockWallpapersAdapter(Activity activity, ArrayList<ThemeLockWallpaperInfo> wallpapers) {
            mLayoutInflater = activity.getLayoutInflater();
            mWallpapers = wallpapers;
        }

        public int getCount() {
            return mWallpapers.size();
        }

        public ThemeLockWallpaperInfo getItem(int position) {
            return mWallpapers.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Drawable thumb = mWallpapers.get(position).mThumb;
            if (thumb == null) {
                Log.e(TAG, "Error decoding thumbnail for wallpaper #" + position);
            }
            return createImageTileView(mLayoutInflater, position, convertView, parent, thumb);
        }
    }

    private static class ThemeWallpapersAdapter extends BaseAdapter implements ListAdapter {
        private LayoutInflater mLayoutInflater;
        private ArrayList<ThemeWallpaperInfo> mWallpapers;

        ThemeWallpapersAdapter(Activity activity, ArrayList<ThemeWallpaperInfo> wallpapers) {
            mLayoutInflater = activity.getLayoutInflater();
            mWallpapers = wallpapers;
        }

        public int getCount() {
            return mWallpapers.size();
        }

        public ThemeWallpaperInfo getItem(int position) {
            return mWallpapers.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Drawable thumb = mWallpapers.get(position).mThumb;
            if (thumb == null) {
                Log.e(TAG, "Error decoding thumbnail for wallpaper #" + position);
            }
            return createImageTileView(mLayoutInflater, position, convertView, parent, thumb);
        }
    }

    public static View createImageTileView(LayoutInflater layoutInflater, int position,
            View convertView, ViewGroup parent, Drawable thumb) {
        View view;

        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.wallpaper_picker_item, parent, false);
        } else {
            view = convertView;
        }

        setWallpaperItemPaddingToZero((FrameLayout) view);

        ImageView image = (ImageView) view.findViewById(R.id.wallpaper_image);

        if (thumb != null) {
            image.setImageDrawable(thumb);
            thumb.setDither(true);
        }

        return view;
    }

    private static Bitmap getThemeWallpaper(Context context, String path, String pkgName,
            boolean legacyTheme, boolean thumb) {
        if (legacyTheme) {
            return getLegacyThemeWallpaper(context, pkgName, thumb);
        }
        InputStream is = null;
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(pkgName);
            if (res == null) {
                return null;
            }

            AssetManager am = res.getAssets();
            String[] wallpapers = am.list(path);
            if (wallpapers == null || wallpapers.length == 0) {
                return null;
            }
            is = am.open(path + File.separator + wallpapers[0]);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, bounds);
            if ((bounds.outWidth == -1) || (bounds.outHeight == -1))
                return null;

            int originalSize = (bounds.outHeight > bounds.outWidth) ? bounds.outHeight
                    : bounds.outWidth;
            Point outSize;

            if (thumb) {
                outSize = getDefaultThumbnailSize(context.getResources());
            } else {
                outSize = getDefaultWallpaperSize(res, ((Activity) context).getWindowManager());
            }
            int thumbSampleSize = (outSize.y > outSize.x) ? outSize.y : outSize.x;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = originalSize / thumbSampleSize;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (IOException e) {
            return null;
        } catch (NameNotFoundException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static Bitmap getLegacyThemeWallpaper(Context context, String pkgName, boolean thumb) {
        try {
            final PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkgName, 0);
            Resources res = context.getPackageManager().getResourcesForApplication(pkgName);

            if (pi == null || res == null) {
                return null;
            }
            int resId = pi.legacyThemeInfos[0].wallpaperResourceId;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, resId, opts);
            if ((opts.outWidth == -1) || (opts.outHeight == -1))
                return null;

            int originalSize = (opts.outHeight > opts.outWidth) ? opts.outHeight
                    : opts.outWidth;
            Point outSize;
            if (thumb) {
                outSize = getDefaultThumbnailSize(context.getResources());
            } else {
                outSize = getDefaultWallpaperSize(res, ((Activity) context).getWindowManager());
            }
            int thumbSampleSize = (outSize.y > outSize.x) ? outSize.y : outSize.x;

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = originalSize / thumbSampleSize;

            return BitmapFactory.decodeResource(res, resId, opts);
        } catch (NameNotFoundException e) {
            return null;
        } catch (OutOfMemoryError e1) {
            return null;
        }
    }
}
