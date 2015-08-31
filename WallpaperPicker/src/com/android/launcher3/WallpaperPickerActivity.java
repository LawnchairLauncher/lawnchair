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
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.wallpapertileinfo.DefaultWallpaperInfo;
import com.android.launcher3.wallpapertileinfo.FileWallpaperInfo;
import com.android.launcher3.wallpapertileinfo.LiveWallpaperInfo;
import com.android.launcher3.wallpapertileinfo.PickImageInfo;
import com.android.launcher3.wallpapertileinfo.ResourceWallpaperInfo;
import com.android.launcher3.wallpapertileinfo.ThirdPartyWallpaperInfo;
import com.android.launcher3.wallpapertileinfo.UriWallpaperInfo;
import com.android.launcher3.wallpapertileinfo.WallpaperTileInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WallpaperPickerActivity extends WallpaperCropActivity
        implements OnClickListener, OnLongClickListener, ActionMode.Callback {
    static final String TAG = "Launcher.WallpaperPickerActivity";

    public static final int IMAGE_PICK = 5;
    public static final int PICK_WALLPAPER_THIRD_PARTY_ACTIVITY = 6;
    private static final String TEMP_WALLPAPER_TILES = "TEMP_WALLPAPER_TILES";
    private static final String SELECTED_INDEX = "SELECTED_INDEX";
    private static final int FLAG_POST_DELAY_MILLIS = 200;

    @Thunk View mSelectedTile;

    @Thunk LinearLayout mWallpapersView;
    @Thunk HorizontalScrollView mWallpaperScrollContainer;
    @Thunk View mWallpaperStrip;

    @Thunk ActionMode mActionMode;

    ArrayList<Uri> mTempWallpaperTiles = new ArrayList<Uri>();
    private SavedWallpaperImages mSavedImages;
    @Thunk int mSelectedIndex = -1;
    /**
     * shows the system wallpaper behind the window and hides the {@link #mCropView} if visible
     * @param visible should the system wallpaper be shown
     */
    protected void setSystemWallpaperVisiblity(final boolean visible) {
        // hide our own wallpaper preview if necessary
        if(!visible) {
            mCropView.setVisibility(View.VISIBLE);
        } else {
            changeWallpaperFlags(visible);
        }
        // the change of the flag must be delayed in order to avoid flickering,
        // a simple post / double post does not suffice here
        mCropView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(!visible) {
                    changeWallpaperFlags(visible);
                } else {
                    mCropView.setVisibility(View.INVISIBLE);
                }
            }
        }, FLAG_POST_DELAY_MILLIS);
    }

    @Thunk void changeWallpaperFlags(boolean visible) {
        int desiredWallpaperFlag = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int currentWallpaperFlag = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (desiredWallpaperFlag != currentWallpaperFlag) {
            getWindow().setFlags(desiredWallpaperFlag,
                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
    }

    @Override
    protected void onLoadRequestComplete(LoadRequest req, boolean success) {
        super.onLoadRequestComplete(req, success);
        if (success) {
            setSystemWallpaperVisiblity(false);
        }
    }

    /**
     * called by onCreate; this is sub-classed to overwrite WallpaperCropActivity
     */
    protected void init() {
        setContentView(R.layout.wallpaper_picker);

        mCropView = (CropView) findViewById(R.id.cropView);
        mCropView.setVisibility(View.INVISIBLE);

        mProgressView = findViewById(R.id.loading);
        mWallpaperScrollContainer = (HorizontalScrollView) findViewById(R.id.wallpaper_scroll_container);
        mWallpaperStrip = findViewById(R.id.wallpaper_strip);
        mCropView.setTouchCallback(new ToggleOnTapCallback(mWallpaperStrip));

        mWallpapersView = (LinearLayout) findViewById(R.id.wallpaper_list);
        // Populate the saved wallpapers
        mSavedImages = new SavedWallpaperImages(getContext());
        populateWallpapers(mWallpapersView, mSavedImages.loadThumbnailsAndImageIdList(), true);

        // Populate the built-in wallpapers
        ArrayList<WallpaperTileInfo> wallpapers = findBundledWallpapers();
        populateWallpapers(mWallpapersView, wallpapers, false);

        // Load live wallpapers asynchronously
        new LiveWallpaperInfo.LoaderTask(this) {

            @Override
            protected void onPostExecute(List<LiveWallpaperInfo> result) {
                populateWallpapers((LinearLayout) findViewById(R.id.live_wallpaper_list),
                        result, false);
                initializeScrollForRtl();
                updateTileIndices();
            }
        }.execute();

        // Populate the third-party wallpaper pickers
        populateWallpapers((LinearLayout) findViewById(R.id.third_party_wallpaper_list),
                ThirdPartyWallpaperInfo.getAll(this), false);

        // Add a tile for the Gallery
        LinearLayout masterWallpaperList = (LinearLayout) findViewById(R.id.master_wallpaper_list);
        masterWallpaperList.addView(
                createTileView(masterWallpaperList, new PickImageInfo(), false), 0);

        // Select the first item; wait for a layout pass so that we initialize the dimensions of
        // cropView or the defaultWallpaperView first
        mCropView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) > 0 && (bottom - top) > 0) {
                    if (mSelectedIndex >= 0 && mSelectedIndex < mWallpapersView.getChildCount()) {
                        onClick(mWallpapersView.getChildAt(mSelectedIndex));
                        setSystemWallpaperVisiblity(false);
                    }
                    v.removeOnLayoutChangeListener(this);
                }
            }
        });

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
                        // Ensure that a tile is slelected and loaded.
                        if (mSelectedTile != null && mCropView.getTileSource() != null) {
                            // Prevent user from selecting any new tile.
                            mWallpaperStrip.setVisibility(View.GONE);
                            actionBar.hide();

                            WallpaperTileInfo info = (WallpaperTileInfo) mSelectedTile.getTag();
                            info.onSave(WallpaperPickerActivity.this);
                        } else {
                            // no tile was selected, so we just finish the activity and go back
                            setResult(Activity.RESULT_OK);
                            finish();
                        }
                    }
                });
        mSetWallpaperButton = findViewById(R.id.set_wallpaper_button);
    }

    /**
     * Called when a wallpaper tile is clicked
     */
    @Override
    public void onClick(View v) {
        if (mActionMode != null) {
            // When CAB is up, clicking toggles the item instead
            if (v.isLongClickable()) {
                onLongClick(v);
            }
            return;
        }
        setWallpaperButtonEnabled(true);
        WallpaperTileInfo info = (WallpaperTileInfo) v.getTag();
        if (info.isSelectable() && v.getVisibility() == View.VISIBLE) {
            selectTile(v);
        }
        info.onClick(this);
    }

    /**
     * Called when a view is long clicked
     */
    @Override
    public boolean onLongClick(View v) {
        CheckableFrameLayout c = (CheckableFrameLayout) v;
        c.toggle();

        if (mActionMode != null) {
            mActionMode.invalidate();
        } else {
            // Start the CAB using the ActionMode.Callback defined below
            mActionMode = startActionMode(this);
            int childCount = mWallpapersView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                mWallpapersView.getChildAt(i).setSelected(false);
            }
        }
        return true;
    }

    public void setWallpaperButtonEnabled(boolean enabled) {
        mSetWallpaperButton.setEnabled(enabled);
    }

    public void selectTile(View v) {
        if (mSelectedTile != null) {
            mSelectedTile.setSelected(false);
            mSelectedTile = null;
        }
        mSelectedTile = v;
        v.setSelected(true);
        mSelectedIndex = mWallpapersView.indexOfChild(v);
        // TODO: Remove this once the accessibility framework and
        // services have better support for selection state.
        v.announceForAccessibility(
                getContext().getString(R.string.announce_selection, v.getContentDescription()));
    }

    @Thunk void initializeScrollForRtl() {
        if (Utilities.isRtl(getResources())) {
            final ViewTreeObserver observer = mWallpaperScrollContainer.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    LinearLayout masterWallpaperList =
                            (LinearLayout) findViewById(R.id.master_wallpaper_list);
                    mWallpaperScrollContainer.scrollTo(masterWallpaperList.getWidth(), 0);
                    mWallpaperScrollContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }

    public void onStop() {
        super.onStop();
        mWallpaperStrip = findViewById(R.id.wallpaper_strip);
        if (mWallpaperStrip.getAlpha() < 1f) {
            mWallpaperStrip.setAlpha(1f);
            mWallpaperStrip.setVisibility(View.VISIBLE);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(TEMP_WALLPAPER_TILES, mTempWallpaperTiles);
        outState.putInt(SELECTED_INDEX, mSelectedIndex);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        ArrayList<Uri> uris = savedInstanceState.getParcelableArrayList(TEMP_WALLPAPER_TILES);
        for (Uri uri : uris) {
            addTemporaryWallpaperTile(uri, true);
        }
        mSelectedIndex = savedInstanceState.getInt(SELECTED_INDEX, -1);
    }

    @Thunk void updateTileIndices() {
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

    private void addTemporaryWallpaperTile(final Uri uri, boolean fromRestore) {

        // Add a tile for the image picked from Gallery, reusing the existing tile if there is one.
        View existingTile = null;
        int indexOfExistingTile = 0;
        for (; indexOfExistingTile < mWallpapersView.getChildCount(); indexOfExistingTile++) {
            View thumbnail = mWallpapersView.getChildAt(indexOfExistingTile);
            Object tag = thumbnail.getTag();
            if (tag instanceof UriWallpaperInfo && ((UriWallpaperInfo) tag).mUri.equals(uri)) {
                existingTile = thumbnail;
                break;
            }
        }
        final View pickedImageThumbnail;
        final UriWallpaperInfo info;
        if (existingTile != null) {
            pickedImageThumbnail = existingTile;
            // Always move the existing wallpaper to the front so user can see it without scrolling.
            mWallpapersView.removeViewAt(indexOfExistingTile);
            mWallpapersView.addView(pickedImageThumbnail, 0);
            info = (UriWallpaperInfo) pickedImageThumbnail.getTag();
        } else {
            // This is the first time this temporary wallpaper has been added
            info = new UriWallpaperInfo(uri);
            pickedImageThumbnail = createTileView(mWallpapersView, info, true);
            mWallpapersView.addView(pickedImageThumbnail, 0);
            mTempWallpaperTiles.add(uri);
        }
        info.loadThumbnaleAsync(this);

        updateTileIndices();
        if (!fromRestore) {
            onClick(existingTile);
        }
    }

    @Thunk void populateWallpapers(ViewGroup parent, List<? extends WallpaperTileInfo> wallpapers,
            boolean addLongPressHandler) {
        for (WallpaperTileInfo info : wallpapers) {
            parent.addView(createTileView(parent, info, addLongPressHandler));
        }
    }

    private View createTileView(ViewGroup parent, WallpaperTileInfo info, boolean addLongPress) {
        View view = info.createView(this, getLayoutInflater(), parent);
        view.setTag(info);

        if (addLongPress) {
            view.setOnLongClickListener(this);

            // Enable stylus button to also trigger long click.
            final StylusEventHelper stylusEventHelper =
                    new StylusEventHelper(new SimpleOnStylusPressListener(view), view);
            view.setOnTouchListener(new View.OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    return stylusEventHelper.onMotionEvent(event);
                }
            });
        }
        view.setOnClickListener(this);
        return view;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                addTemporaryWallpaperTile(uri, false);
            }
        } else if (requestCode == PICK_WALLPAPER_THIRD_PARTY_ACTIVITY
                && resultCode == Activity.RESULT_OK) {
            // Something was set on the third-party activity.
            setResult(Activity.RESULT_OK);
            finish();
        }
    }

    private ArrayList<WallpaperTileInfo> findBundledWallpapers() {
        final PackageManager pm = getContext().getPackageManager();
        final ArrayList<WallpaperTileInfo> bundled = new ArrayList<WallpaperTileInfo>(24);

        Partner partner = Partner.get(pm);
        if (partner != null) {
            final Resources partnerRes = partner.getResources();
            final int resId = partnerRes.getIdentifier(Partner.RES_WALLPAPERS, "array",
                    partner.getPackageName());
            if (resId != 0) {
                addWallpapers(bundled, partnerRes, partner.getPackageName(), resId);
            }

            // Add system wallpapers
            File systemDir = partner.getWallpaperDirectory();
            if (systemDir != null && systemDir.isDirectory()) {
                for (File file : systemDir.listFiles()) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String name = file.getName();
                    int dotPos = name.lastIndexOf('.');
                    String extension = "";
                    if (dotPos >= -1) {
                        extension = name.substring(dotPos);
                        name = name.substring(0, dotPos);
                    }

                    if (name.endsWith("_small")) {
                        // it is a thumbnail
                        continue;
                    }

                    File thumbnail = new File(systemDir, name + "_small" + extension);
                    Bitmap thumb = BitmapFactory.decodeFile(thumbnail.getAbsolutePath());
                    if (thumb != null) {
                        bundled.add(new FileWallpaperInfo(
                                file, new BitmapDrawable(getResources(), thumb)));
                    }
                }
            }
        }

        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher3; Resources needs the real package name,
        // com.android.launcher3. So we ask Resources for what it thinks the
        // package name should be.
        try {
            final String packageName = getResources().getResourcePackageName(R.array.wallpapers);
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            Resources wallpaperRes = getContext().getPackageManager()
                    .getResourcesForApplication(info);
            addWallpapers(bundled, wallpaperRes, info.packageName, R.array.wallpapers);
        } catch (PackageManager.NameNotFoundException e) { }

        if (partner == null || !partner.hideDefaultWallpaper()) {
            // Add an entry for the default wallpaper (stored in system resources)
            WallpaperTileInfo defaultWallpaperInfo = DefaultWallpaperInfo.get(this);
            if (defaultWallpaperInfo != null) {
                bundled.add(0, defaultWallpaperInfo);
            }
        }
        return bundled;
    }

    private void addWallpapers(ArrayList<WallpaperTileInfo> known, Resources res,
            String packageName, int listResId) {
        final String[] extras = res.getStringArray(listResId);
        for (String extra : extras) {
            int resId = res.getIdentifier(extra, "drawable", packageName);
            if (resId != 0) {
                final int thumbRes = res.getIdentifier(extra + "_small", "drawable", packageName);

                if (thumbRes != 0) {
                    ResourceWallpaperInfo wallpaperInfo =
                            new ResourceWallpaperInfo(res, resId, res.getDrawable(thumbRes));
                    known.add(wallpaperInfo);
                    // Log.d(TAG, "add: [" + packageName + "]: " + extra + " (" + res + ")");
                }
            } else {
                Log.e(TAG, "Couldn't find wallpaper " + extra);
            }
        }
    }

    public SavedWallpaperImages getSavedImages() {
        return mSavedImages;
    }

    public void startActivityForResultSafely(Intent intent, int requestCode) {
        Utilities.startActivityForResultSafely(getActivity(), intent, requestCode);
    }

    @Override
    public boolean enableRotation() {
        return super.enableRotation() ||
                getContentResolver().call(LauncherSettings.Settings.CONTENT_URI,
                        LauncherSettings.Settings.METHOD_GET_BOOLEAN,
                        Utilities.ALLOW_ROTATION_PREFERENCE_KEY, new Bundle())
                .getBoolean(LauncherSettings.Settings.EXTRA_VALUE);
    }

    // CAB for deleting items
    /**
     * Called when the action mode is created; startActionMode() was called
     */
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        // Inflate a menu resource providing context menu items
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.cab_delete_wallpapers, menu);
        return true;
    }

    /**
     * Called each time the action mode is shown. Always called after onCreateActionMode,
     * but may be called multiple times if the mode is invalidated.
     */
    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        int childCount = mWallpapersView.getChildCount();
        int numCheckedItems = 0;
        for (int i = 0; i < childCount; i++) {
            CheckableFrameLayout c = (CheckableFrameLayout) mWallpapersView.getChildAt(i);
            if (c.isChecked()) {
                numCheckedItems++;
            }
        }

        if (numCheckedItems == 0) {
            mode.finish();
            return true;
        } else {
            mode.setTitle(getResources().getQuantityString(
                    R.plurals.number_of_items_selected, numCheckedItems, numCheckedItems));
            return true;
        }
    }

    /**
     * Called when the user selects a contextual menu item
     */
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_delete) {
            int childCount = mWallpapersView.getChildCount();
            ArrayList<View> viewsToRemove = new ArrayList<View>();
            boolean selectedTileRemoved = false;
            for (int i = 0; i < childCount; i++) {
                CheckableFrameLayout c =
                        (CheckableFrameLayout) mWallpapersView.getChildAt(i);
                if (c.isChecked()) {
                    WallpaperTileInfo info = (WallpaperTileInfo) c.getTag();
                    info.onDelete(WallpaperPickerActivity.this);
                    viewsToRemove.add(c);
                    if (i == mSelectedIndex) {
                        selectedTileRemoved = true;
                    }
                }
            }
            for (View v : viewsToRemove) {
                mWallpapersView.removeView(v);
            }
            if (selectedTileRemoved) {
                mSelectedIndex = -1;
                mSelectedTile = null;
                setSystemWallpaperVisiblity(true);
            }
            updateTileIndices();
            mode.finish(); // Action picked, so close the CAB
            return true;
        } else {
            return false;
        }
    }

    /**
     * Called when the user exits the action mode
     */
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        int childCount = mWallpapersView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            CheckableFrameLayout c = (CheckableFrameLayout) mWallpapersView.getChildAt(i);
            c.setChecked(false);
        }
        if (mSelectedTile != null) {
            mSelectedTile.setSelected(true);
        }
        mActionMode = null;
    }
}
