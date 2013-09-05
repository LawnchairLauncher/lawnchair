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
import android.content.ComponentName;
import android.content.Intent;
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
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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

    private ArrayList<Drawable> mThumbs;
    private ArrayList<Integer> mImages;
    private Resources mWallpaperResources;

    private View mSelectedThumb;
    private boolean mIgnoreNextTap;
    private OnClickListener mThumbnailOnClickListener;

    private static class ThumbnailMetaData {
        public boolean mLaunchesGallery;
        public Uri mGalleryImageUri;
        public int mWallpaperResId;
    }

    // called by onCreate; this is subclassed to overwrite WallpaperCropActivity
    protected void init() {
        setContentView(R.layout.wallpaper_picker);

        mCropView = (CropView) findViewById(R.id.cropView);
        final View wallpaperStrip = findViewById(R.id.wallpaper_strip);
        mCropView.setTouchCallback(new CropView.TouchCallback() {
            LauncherViewPropertyAnimator mAnim;
            public void onTouchDown() {
                if (mAnim != null) {
                    mAnim.cancel();
                }
                if (wallpaperStrip.getTranslationY() == 0) {
                    mIgnoreNextTap = true;
                }
                mAnim = new LauncherViewPropertyAnimator(wallpaperStrip);
                mAnim.translationY(wallpaperStrip.getHeight())
                    .setInterpolator(new DecelerateInterpolator(0.75f));
                mAnim.start();
            }
            public void onTap() {
                boolean ignoreTap = mIgnoreNextTap;
                mIgnoreNextTap = false;
                if (!ignoreTap) {
                    if (mAnim != null) {
                        mAnim.cancel();
                    }
                    mAnim = new LauncherViewPropertyAnimator(wallpaperStrip);
                    mAnim.translationY(0).setInterpolator(new DecelerateInterpolator(0.75f));
                    mAnim.start();
                }
            }
        });

        mThumbnailOnClickListener = new OnClickListener() {
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
                    Point wallpaperSize = WallpaperCropActivity.getDefaultWallpaperSize(
                            getResources(), getWindowManager());
                    RectF crop = WallpaperCropActivity.getMaxCropRect(
                            source.getImageWidth(), source.getImageHeight(),
                            wallpaperSize.x, wallpaperSize.y);
                    mCropView.setScale(wallpaperSize.x / crop.width());
                    mCropView.setTouchEnabled(false);
                }
            }
        };

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
                            boolean finishActivityWhenDone = true;
                            cropImageAndSetWallpaper(meta.mGalleryImageUri, finishActivityWhenDone);
                        } else if (meta.mWallpaperResId != 0) {
                            boolean finishActivityWhenDone = true;
                            cropImageAndSetWallpaper(mWallpaperResources,
                                    meta.mWallpaperResId, finishActivityWhenDone);
                        }
                    }
                });
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

            RectF cropRect = WallpaperCropActivity.getMaxCropRect(
                    bounds.x, bounds.y, width, height);
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
