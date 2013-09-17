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

import android.animation.LayoutTransition;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
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

import com.android.photos.BitmapRegionTileSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WallpaperPickerActivity extends WallpaperCropActivity {
    static final String TAG = "Launcher.WallpaperPickerActivity";

    private static final int IMAGE_PICK = 5;
    private static final int PICK_WALLPAPER_THIRD_PARTY_ACTIVITY = 6;
    private static final String TEMP_WALLPAPER_TILES = "TEMP_WALLPAPER_TILES";

    private ArrayList<Drawable> mBundledWallpaperThumbs;
    private ArrayList<Integer> mBundledWallpaperResIds;
    private Resources mWallpaperResources;

    private View mSelectedThumb;
    private boolean mIgnoreNextTap;
    private OnClickListener mThumbnailOnClickListener;

    private LinearLayout mWallpapersView;

    private ActionMode.Callback mActionModeCallback;
    private ActionMode mActionMode;

    private View.OnLongClickListener mLongClickListener;

    ArrayList<Uri> mTempWallpaperTiles = new ArrayList<Uri>();
    private SavedWallpaperImages mSavedImages;

    private static class ThumbnailMetaData {
        public boolean mLaunchesGallery;
        public Uri mWallpaperUri;
        public int mSavedWallpaperDbId;
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
                if (mActionMode != null) {
                    // When CAB is up, clicking toggles the item instead
                    if (v.isLongClickable()) {
                        mLongClickListener.onLongClick(v);
                    }
                    return;
                }
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
                } else if (meta.mWallpaperUri != null) {
                    mCropView.setTileSource(new BitmapRegionTileSource(WallpaperPickerActivity.this,
                            meta.mWallpaperUri, 1024, 0), null);
                    mCropView.setTouchEnabled(true);
                } else if (meta.mSavedWallpaperDbId != 0) {
                    String imageFilename = mSavedImages.getImageFilename(meta.mSavedWallpaperDbId);
                    File file = new File(getFilesDir(), imageFilename);
                    mCropView.setTileSource(new BitmapRegionTileSource(WallpaperPickerActivity.this,
                            file.getAbsolutePath(), 1024, 0), null);
                    mCropView.moveToLeft();
                    mCropView.setTouchEnabled(false);
                } else if (meta.mWallpaperResId != 0) {
                    BitmapRegionTileSource source = new BitmapRegionTileSource(mWallpaperResources,
                            WallpaperPickerActivity.this, meta.mWallpaperResId, 1024, 0);
                    mCropView.setTileSource(source, null);
                    Point wallpaperSize = WallpaperCropActivity.getDefaultWallpaperSize(
                            getResources(), getWindowManager());
                    RectF crop = WallpaperCropActivity.getMaxCropRect(
                            source.getImageWidth(), source.getImageHeight(),
                            wallpaperSize.x, wallpaperSize.y, false);
                    mCropView.setScale(wallpaperSize.x / crop.width());
                    mCropView.setTouchEnabled(false);
                }
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

        // Populate the built-in wallpapers
        findBundledWallpapers();
        mWallpapersView = (LinearLayout) findViewById(R.id.wallpaper_list);
        ImageAdapter ia = new ImageAdapter(this, mBundledWallpaperThumbs);
        populateWallpapersFromAdapter(mWallpapersView, ia, mBundledWallpaperResIds, true, false);

        // Populate the saved wallpapers
        mSavedImages = new SavedWallpaperImages(this);
        mSavedImages.loadThumbnailsAndImageIdList();
        ArrayList<Drawable> savedWallpaperThumbs = mSavedImages.getThumbnails();
        ArrayList<Integer > savedWallpaperIds = mSavedImages.getImageIds();
        ia = new ImageAdapter(this, savedWallpaperThumbs);
        populateWallpapersFromAdapter(mWallpapersView, ia, savedWallpaperIds, false, true);

        // Add a tile for the Gallery
        FrameLayout galleryThumbnail = (FrameLayout) getLayoutInflater().
                inflate(R.layout.wallpaper_picker_gallery_item, mWallpapersView, false);
        setWallpaperItemPaddingToZero(galleryThumbnail);
        mWallpapersView.addView(galleryThumbnail, 0);

        // Make its background the last photo taken on external storage
        Bitmap lastPhoto = getThumbnailOfLastPhoto();
        if (lastPhoto != null) {
            ImageView galleryThumbnailBg =
                    (ImageView) galleryThumbnail.findViewById(R.id.wallpaper_image);
            galleryThumbnailBg.setImageBitmap(getThumbnailOfLastPhoto());
        }

        ThumbnailMetaData meta = new ThumbnailMetaData();
        meta.mLaunchesGallery = true;
        galleryThumbnail.setTag(meta);
        galleryThumbnail.setOnClickListener(mThumbnailOnClickListener);

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
                        ThumbnailMetaData meta = (ThumbnailMetaData) mSelectedThumb.getTag();
                        if (meta.mLaunchesGallery) {
                            // shouldn't be selected, but do nothing
                        } else if (meta.mWallpaperUri != null) {
                            boolean finishActivityWhenDone = true;
                            OnBitmapCroppedHandler h = new OnBitmapCroppedHandler() {
                                public void onBitmapCropped(byte[] imageBytes) {
                                    Bitmap thumb = createThumbnail(null, imageBytes, true);
                                    mSavedImages.writeImage(thumb, imageBytes);
                                }
                            };
                            cropImageAndSetWallpaper(meta.mWallpaperUri, h, finishActivityWhenDone);
                        } else if (meta.mSavedWallpaperDbId != 0) {
                            boolean finishActivityWhenDone = true;
                            String imageFilename =
                                    mSavedImages.getImageFilename(meta.mSavedWallpaperDbId);
                            setWallpaper(imageFilename, finishActivityWhenDone);
                        } else if (meta.mWallpaperResId != 0) {
                            boolean finishActivityWhenDone = true;
                            cropImageAndSetWallpaper(mWallpaperResources,
                                    meta.mWallpaperResId, finishActivityWhenDone);
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
                            ThumbnailMetaData meta = (ThumbnailMetaData) c.getTag();
                            mSavedImages.deleteImage(meta.mSavedWallpaperDbId);
                            viewsToRemove.add(c);
                        }
                    }
                    for (View v : viewsToRemove) {
                        mWallpapersView.removeView(v);
                    }
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

    protected Bitmap getThumbnailOfLastPhoto() {
        Cursor cursor = MediaStore.Images.Media.query(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.ImageColumns._ID,
                    MediaStore.Images.ImageColumns.DATE_TAKEN},
                null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT 1");
        Bitmap thumb = null;
        if (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            thumb = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(),
                    id, MediaStore.Images.Thumbnails.MINI_KIND, null);
        }
        cursor.close();
        return thumb;
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

    private void populateWallpapersFromAdapter(ViewGroup parent, ImageAdapter ia,
            ArrayList<Integer> imageIds, boolean imagesAreResources, boolean addLongPressHandler) {
        for (int i = 0; i < ia.getCount(); i++) {
            FrameLayout thumbnail = (FrameLayout) ia.getView(i, null, parent);
            parent.addView(thumbnail, i);

            ThumbnailMetaData meta = new ThumbnailMetaData();
            if (imagesAreResources) {
                meta.mWallpaperResId = imageIds.get(i);
            } else {
                meta.mSavedWallpaperDbId = imageIds.get(i);
            }
            thumbnail.setTag(meta);
            if (addLongPressHandler) {
                addLongPressHandler(thumbnail);
            }
            thumbnail.setOnClickListener(mThumbnailOnClickListener);
            if (i == 0) {
                mThumbnailOnClickListener.onClick(thumbnail);
            }
        }
    }

    private Bitmap createThumbnail(Uri uri, byte[] imageBytes, boolean leftAligned) {
        Resources res = getResources();
        int width = res.getDimensionPixelSize(R.dimen.wallpaperThumbnailWidth);
        int height = res.getDimensionPixelSize(R.dimen.wallpaperThumbnailHeight);

        BitmapCropTask cropTask;
        if (uri != null) {
            cropTask = new BitmapCropTask(uri, null, width, height, false, true, null);
        } else {
            cropTask = new BitmapCropTask(imageBytes, null, width, height, false, true, null);
        }
        Point bounds = cropTask.getImageBounds();
        if (bounds == null) {
            return null;
        }

        RectF cropRect = WallpaperCropActivity.getMaxCropRect(
                bounds.x, bounds.y, width, height, leftAligned);
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
        Bitmap thumb = createThumbnail(uri, null, false);
        if (thumb != null) {
            image.setImageBitmap(thumb);
            Drawable thumbDrawable = image.getDrawable();
            thumbDrawable.setDither(true);
        } else {
            Log.e(TAG, "Error loading thumbnail for uri=" + uri);
        }
        mWallpapersView.addView(pickedImageThumbnail, 1);

        ThumbnailMetaData meta = new ThumbnailMetaData();
        meta.mWallpaperUri = uri;
        pickedImageThumbnail.setTag(meta);
        pickedImageThumbnail.setOnClickListener(mThumbnailOnClickListener);
        mThumbnailOnClickListener.onClick(pickedImageThumbnail);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_PICK && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            addTemporaryWallpaperTile(uri);
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

    private void addLongPressHandler(View v) {
        v.setOnLongClickListener(mLongClickListener);
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

    private void findBundledWallpapers() {
        mBundledWallpaperThumbs = new ArrayList<Drawable>(24);
        mBundledWallpaperResIds = new ArrayList<Integer>(24);

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
                    mBundledWallpaperThumbs.add(resources.getDrawable(thumbRes));
                    mBundledWallpaperResIds.add(res);
                    // Log.d(TAG, "add: [" + packageName + "]: " + extra + " (" + res + ")");
                }
            } else {
                Log.e(TAG, "Couldn't find wallpaper " + extra);
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

    private static class ImageAdapter extends BaseAdapter implements ListAdapter, SpinnerAdapter {
        private LayoutInflater mLayoutInflater;
        private ArrayList<Drawable> mThumbs;

        ImageAdapter(Activity activity, ArrayList<Drawable> thumbs) {
            mLayoutInflater = activity.getLayoutInflater();
            mThumbs = thumbs;
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
