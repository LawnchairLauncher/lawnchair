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

import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.android.photos.BitmapRegionTileSource;

import java.util.ArrayList;
import java.util.List;

public class WallpaperPickerActivity extends WallpaperCropActivity {
    private static final String TAG = "Launcher.WallpaperPickerActivity";

    private static final int IMAGE_PICK = 5;
    private static final int PICK_WALLPAPER_THIRD_PARTY_ACTIVITY = 6;
    private static final float WALLPAPER_SCREENS_SPAN = 2f;

    private ArrayList<Drawable> mThumbs;
    private ArrayList<Integer> mImages;
    private Resources mWallpaperResources;

    private View mSelectedThumb;
    private CropView mCropView;

    private static class ThumbnailMetaData {
        public boolean mLaunchesGallery;
        public Uri mGalleryImageUri;
        public int mWallpaperResId;
    }

    private OnClickListener mThumbnailOnClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (mSelectedThumb != null) {
                mSelectedThumb.setSelected(false);
            }

            ThumbnailMetaData meta = (ThumbnailMetaData) v.getTag();

            if (!meta.mLaunchesGallery) {
                mSelectedThumb = v;
                v.setSelected(true);
            }

            if (meta.mLaunchesGallery) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                Utilities.startActivityForResultSafely(
                        WallpaperPickerActivity.this, intent, IMAGE_PICK);
            } else if (meta.mGalleryImageUri != null) {
                mCropView.setTileSource(new BitmapRegionTileSource(WallpaperPickerActivity.this,
                        meta.mGalleryImageUri, 1024, 0), null);
                mCropView.setTouchEnabled(true);
            } else {
                BitmapRegionTileSource source = new BitmapRegionTileSource(mWallpaperResources,
                        WallpaperPickerActivity.this, meta.mWallpaperResId, 1024, 0);
                mCropView.setTileSource(source, null);
                Point wallpaperSize = getDefaultWallpaperSize(getResources(), getWindowManager());
                RectF crop = getMaxCropRect(source.getImageWidth(), source.getImageHeight(),
                        wallpaperSize.x, wallpaperSize.y);
                mCropView.setScale(wallpaperSize.x / crop.width());
                mCropView.setTouchEnabled(false);
            }
        }
    };

    private RectF getMaxCropRect(int inWidth, int inHeight, int outWidth, int outHeight) {
        RectF cropRect = new RectF();
        // Get a crop rect that will fit this
        if (inWidth / (float) inHeight > outWidth / (float) outHeight) {
             cropRect.top = 0;
             cropRect.bottom = inHeight;
             cropRect.left = (inWidth - (outWidth / (float) outHeight) * inHeight) / 2;
             cropRect.right = inWidth - cropRect.left;
        } else {
            cropRect.left = 0;
            cropRect.right = inWidth;
            cropRect.top = (inHeight - (outHeight / (float) outWidth) * inWidth) / 2;
            cropRect.bottom = inHeight - cropRect.top;
        }
        return cropRect;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_PICK && resultCode == RESULT_OK) {
            Uri uri = data.getData();

            // Add a tile for the image picked from Gallery
            LinearLayout wallpapers = (LinearLayout) findViewById(R.id.wallpaper_list);
            FrameLayout pickedImageThumbnail = (FrameLayout) getLayoutInflater().
                    inflate(R.layout.wallpaper_picker_item, wallpapers, false);
            setWallpaperItemPaddingToZero(pickedImageThumbnail);

            // Load the thumbnail
            ImageView image = (ImageView) pickedImageThumbnail.findViewById(R.id.wallpaper_image);

            Resources res = getResources();
            int width = res.getDimensionPixelSize(R.dimen.wallpaperThumbnailWidth);
            int height = res.getDimensionPixelSize(R.dimen.wallpaperThumbnailHeight);

            BitmapCropTask cropTask =
                    new BitmapCropTask(uri, null, width, height, false, true, null);
            Point bounds = cropTask.getImageBounds();

            RectF cropRect = getMaxCropRect(bounds.x, bounds.y, width, height);
            cropTask.setCropBounds(cropRect);

            if (cropTask.cropBitmap()) {
                image.setImageBitmap(cropTask.getCroppedBitmap());
                Drawable thumbDrawable = image.getDrawable();
                thumbDrawable.setDither(true);
            } else {
                Log.e(TAG, "Error loading thumbnail for uri=" + uri);
            }
            wallpapers.addView(pickedImageThumbnail, 0);

            ThumbnailMetaData meta = new ThumbnailMetaData();
            meta.mGalleryImageUri = uri;
            pickedImageThumbnail.setTag(meta);
            pickedImageThumbnail.setOnClickListener(mThumbnailOnClickListener);
            mThumbnailOnClickListener.onClick(pickedImageThumbnail);
        } else if (requestCode == PICK_WALLPAPER_THIRD_PARTY_ACTIVITY) {
            // No result code is returned; just return
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wallpaper_picker);

        mCropView = (CropView) findViewById(R.id.cropView);

        // Populate the built-in wallpapers
        findWallpapers();

        LinearLayout wallpapers = (LinearLayout) findViewById(R.id.wallpaper_list);
        ImageAdapter ia = new ImageAdapter(this);
        for (int i = 0; i < ia.getCount(); i++) {
            FrameLayout thumbnail = (FrameLayout) ia.getView(i, null, wallpapers);
            wallpapers.addView(thumbnail, i);

            ThumbnailMetaData meta = new ThumbnailMetaData();
            meta.mWallpaperResId = mImages.get(i);
            thumbnail.setTag(meta);
            thumbnail.setOnClickListener(mThumbnailOnClickListener);
            if (i == 0) {
                mThumbnailOnClickListener.onClick(thumbnail);
            }
        }
        // Add a tile for the Gallery
        FrameLayout galleryThumbnail = (FrameLayout) getLayoutInflater().
                inflate(R.layout.wallpaper_picker_gallery_item, wallpapers, false);
        setWallpaperItemPaddingToZero(galleryThumbnail);

        TextView galleryLabel =
                (TextView) galleryThumbnail.findViewById(R.id.wallpaper_item_label);
        galleryLabel.setText(R.string.gallery);
        wallpapers.addView(galleryThumbnail, 0);

        ThumbnailMetaData meta = new ThumbnailMetaData();
        meta.mLaunchesGallery = true;
        galleryThumbnail.setTag(meta);
        galleryThumbnail.setOnClickListener(mThumbnailOnClickListener);

        // Action bar
        // Show the custom action bar view
        final ActionBar actionBar = getActionBar();
        actionBar.setCustomView(R.layout.actionbar_set_wallpaper);
        actionBar.getCustomView().setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ThumbnailMetaData meta = (ThumbnailMetaData) mSelectedThumb.getTag();
                        if (meta.mLaunchesGallery) {
                            // shouldn't be selected, but do nothing
                        } else if (meta.mGalleryImageUri != null) {
                            // Get the crop
                            Point inSize = mCropView.getSourceDimensions();

                            Point minDims = new Point();
                            Point maxDims = new Point();
                            Display d = getWindowManager().getDefaultDisplay();
                            d.getCurrentSizeRange(minDims, maxDims);

                            Point displaySize = new Point();
                            d.getSize(displaySize);

                            int maxDim = Math.max(maxDims.x, maxDims.y);
                            final int minDim = Math.min(minDims.x, minDims.y);
                            int defaultWidth;
                            if (LauncherAppState.isScreenLarge(getResources())) {
                                defaultWidth = (int) (maxDim *
                                        wallpaperTravelToScreenWidthRatio(maxDim, minDim));
                            } else {
                                defaultWidth = Math.max((int)
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
                            float cropScale = mCropView.getWidth() / (float) cropRect.width();

                            // ADJUST CROP WIDTH
                            // Extend the crop all the way to the right, for parallax
                            float extraSpaceToRight = inSize.x - cropRect.right;
                            // Cap the amount of extra width
                            float maxExtraSpace = defaultWidth / cropScale - cropRect.width();
                            extraSpaceToRight = Math.min(extraSpaceToRight, maxExtraSpace);

                            cropRect.right += extraSpaceToRight;

                            // ADJUST CROP HEIGHT
                            if (isPortrait) {
                                cropRect.bottom = cropRect.top + portraitHeight / cropScale;
                            } else { // LANDSCAPE
                                float extraPortraitHeight =
                                        portraitHeight / cropScale - cropRect.height();
                                float expandHeight =
                                        Math.min(Math.min(inSize.y - cropRect.bottom, cropRect.top),
                                                extraPortraitHeight / 2);
                                cropRect.top -= expandHeight;
                                cropRect.bottom += expandHeight;
                            }
                            final int outWidth = (int) Math.round(cropRect.width() * cropScale);
                            final int outHeight = (int) Math.round(cropRect.height() * cropScale);

                            Runnable onEndCrop = new Runnable() {
                                public void run() {
                                    updateWallpaperDimensions(outWidth, outHeight);
                                }
                            };
                            BitmapCropTask cropTask = new BitmapCropTask(meta.mGalleryImageUri,
                                    cropRect, outWidth, outHeight, true, false, onEndCrop);
                            cropTask.execute();
                        } else if (meta.mWallpaperResId != 0) {
                            // crop this image and scale it down to the default wallpaper size for
                            // this device
                            Point inSize = mCropView.getSourceDimensions();
                            Point outSize = getDefaultWallpaperSize(getResources(),
                                    getWindowManager());
                            RectF crop = getMaxCropRect(
                                    inSize.x, inSize.y, outSize.x, outSize.y);
                            Runnable onEndCrop = new Runnable() {
                                public void run() {
                                    // Passing 0, 0 will cause launcher to revert to using the
                                    // default wallpaper size
                                    updateWallpaperDimensions(0, 0);
                                }
                            };
                            BitmapCropTask cropTask = new BitmapCropTask(mWallpaperResources,
                                    meta.mWallpaperResId, crop, outSize.x, outSize.y,
                                    true, false, onEndCrop);
                            cropTask.execute();
                            setResult(Activity.RESULT_OK);
                            finish();
                        }
                    }
                });
    }

    private static void setWallpaperItemPaddingToZero(FrameLayout frameLayout) {
        frameLayout.setPadding(0, 0, 0, 0);
        frameLayout.setForeground(new ZeroPaddingDrawable(frameLayout.getForeground()));
    }

    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (item.getIntent() == null) {
            return super.onMenuItemSelected(featureId, item);
        } else {
            Utilities.startActivityForResultSafely(
                    this, item.getIntent(), PICK_WALLPAPER_THIRD_PARTY_ACTIVITY);
            return true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final Intent pickWallpaperIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> apps =
                pm.queryIntentActivities(pickWallpaperIntent, 0);

        SubMenu sub = menu.addSubMenu("Other\u2026"); // TODO: what's the better way to do this?
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);


        // Get list of image picker intents
        Intent pickImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickImageIntent.setType("image/*");
        final List<ResolveInfo> imagePickerActivities =
                pm.queryIntentActivities(pickImageIntent, 0);
        final ComponentName[] imageActivities = new ComponentName[imagePickerActivities.size()];
        for (int i = 0; i < imagePickerActivities.size(); i++) {
            ActivityInfo activityInfo = imagePickerActivities.get(i).activityInfo;
            imageActivities[i] = new ComponentName(activityInfo.packageName, activityInfo.name);
        }

        outerLoop:
        for (ResolveInfo info : apps) {
            final ComponentName itemComponentName =
                    new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            final String itemPackageName = itemComponentName.getPackageName();
            // Exclude anything from our own package, and the old Launcher
            if (itemPackageName.equals(getPackageName()) ||
                    itemPackageName.equals("com.android.launcher")) {
                continue;
            }
            // Exclude any package that already responds to the image picker intent
            for (ResolveInfo imagePickerActivityInfo : imagePickerActivities) {
                if (itemPackageName.equals(
                        imagePickerActivityInfo.activityInfo.packageName)) {
                    continue outerLoop;
                }
            }
            MenuItem mi = sub.add(info.loadLabel(pm));
            Intent launchIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
            launchIntent.setComponent(itemComponentName);
            mi.setIntent(launchIntent);
            Drawable icon = info.loadIcon(pm);
            if (icon != null) {
                mi.setIcon(icon);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void findWallpapers() {
        mThumbs = new ArrayList<Drawable>(24);
        mImages = new ArrayList<Integer>(24);

        Pair<ApplicationInfo, Integer> r = getWallpaperArrayResourceId();
        if (r != null) {
            try {
                mWallpaperResources = getPackageManager().getResourcesForApplication(r.first);
                addWallpapers(mWallpaperResources, r.first.packageName, r.second);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
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

    private void addWallpapers(Resources resources, String packageName, int listResId) {
        final String[] extras = resources.getStringArray(listResId);
        for (String extra : extras) {
            int res = resources.getIdentifier(extra, "drawable", packageName);
            if (res != 0) {
                final int thumbRes = resources.getIdentifier(extra + "_small",
                        "drawable", packageName);

                if (thumbRes != 0) {
                    mThumbs.add(resources.getDrawable(thumbRes));
                    mImages.add(res);
                    // Log.d(TAG, "add: [" + packageName + "]: " + extra + " (" + res + ")");
                }
            }
        }
    }

    // As a ratio of screen height, the total distance we want the parallax effect to span
    // horizontally
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16/10f;
        final float ASPECT_RATIO_PORTRAIT = 10/16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
            (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
            (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    static private Point getDefaultWallpaperSize(Resources res, WindowManager windowManager) {
        Point minDims = new Point();
        Point maxDims = new Point();
        windowManager.getDefaultDisplay().getCurrentSizeRange(minDims, maxDims);

        int maxDim = Math.max(maxDims.x, maxDims.y);
        final int minDim = Math.min(minDims.x, minDims.y);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Point realSize = new Point();
            windowManager.getDefaultDisplay().getRealSize(realSize);
            maxDim = Math.max(realSize.x, realSize.y);
        }

        // We need to ensure that there is enough extra space in the wallpaper
        // for the intended
        // parallax effects
        final int defaultWidth, defaultHeight;
        if (LauncherAppState.isScreenLarge(res)) {
            defaultWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
            defaultHeight = maxDim;
        } else {
            defaultWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
            defaultHeight = maxDim;
        }
        return new Point(defaultWidth, defaultHeight);
    }

    static public void suggestWallpaperDimension(Resources res,
            final SharedPreferences sharedPrefs,
            WindowManager windowManager,
            final WallpaperManager wallpaperManager) {
        final Point defaultWallpaperSize = getDefaultWallpaperSize(res, windowManager);

        new Thread("suggestWallpaperDimension") {
            public void run() {
                // If we have saved a wallpaper width/height, use that instead
                int savedWidth = sharedPrefs.getInt(WALLPAPER_WIDTH_KEY, defaultWallpaperSize.x);
                int savedHeight = sharedPrefs.getInt(WALLPAPER_HEIGHT_KEY, defaultWallpaperSize.y);
                wallpaperManager.suggestDesiredDimensions(savedWidth, savedHeight);
            }
        }.start();
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

    private class ImageAdapter extends BaseAdapter implements ListAdapter, SpinnerAdapter {
        private LayoutInflater mLayoutInflater;

        ImageAdapter(Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();
        }

        public int getCount() {
            return mThumbs.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView == null) {
                view = mLayoutInflater.inflate(R.layout.wallpaper_picker_item, parent, false);
            } else {
                view = convertView;
            }

            setWallpaperItemPaddingToZero((FrameLayout) view);

            ImageView image = (ImageView) view.findViewById(R.id.wallpaper_image);

            Drawable thumbDrawable = mThumbs.get(position);
            if (thumbDrawable != null) {
                image.setImageDrawable(thumbDrawable);
                thumbDrawable.setDither(true);
            } else {
                Log.e(TAG, "Error decoding thumbnail for wallpaper #" + position);
            }

            return view;
        }
    }
}
