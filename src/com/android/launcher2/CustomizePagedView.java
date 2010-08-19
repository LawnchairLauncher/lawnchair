/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.Region.Op;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.LiveFolders;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.launcher.R;

public class CustomizePagedView extends PagedView
    implements View.OnLongClickListener,
                DragSource {

    public enum CustomizationType {
        WidgetCustomization,
        FolderCustomization,
        ShortcutCustomization,
        WallpaperCustomization
    }

    private static final String TAG = "CustomizeWorkspace";
    private static final boolean DEBUG = false;

    private Launcher mLauncher;
    private DragController mDragController;
    private PackageManager mPackageManager;

    private CustomizationType mCustomizationType;

    private PagedViewCellLayout mTmpWidgetLayout;
    private ArrayList<ArrayList<PagedViewCellLayout.LayoutParams>> mWidgetPages;
    private List<AppWidgetProviderInfo> mWidgetList;
    private List<ResolveInfo> mFolderList;
    private List<ResolveInfo> mShortcutList;

    private int mCellCountX;
    private int mCellCountY;

    private final Canvas mCanvas = new Canvas();
    private final LayoutInflater mInflater;

    public CustomizePagedView(Context context) {
        this(context, null);
    }

    public CustomizePagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCellCountX = 8;
        mCellCountY = 4;
        mCustomizationType = CustomizationType.WidgetCustomization;
        mWidgetPages = new ArrayList<ArrayList<PagedViewCellLayout.LayoutParams>>();
        mTmpWidgetLayout = new PagedViewCellLayout(context);
        mInflater = LayoutInflater.from(context);
        setupPage(mTmpWidgetLayout);
        setVisibility(View.GONE);
        setSoundEffectsEnabled(false);
    }

    public void setLauncher(Launcher launcher) {
        Context context = getContext();
        mLauncher = launcher;
        mPackageManager = context.getPackageManager();
    }

    public void update() {
        Context context = getContext();

        // get the list of widgets
        mWidgetList = AppWidgetManager.getInstance(mLauncher).getInstalledProviders();
        Collections.sort(mWidgetList, new Comparator<AppWidgetProviderInfo>() {
            @Override
            public int compare(AppWidgetProviderInfo object1, AppWidgetProviderInfo object2) {
                return object1.label.compareTo(object2.label);
            }
        });

        Comparator<ResolveInfo> resolveInfoComparator = new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo object1, ResolveInfo object2) {
                return object1.loadLabel(mPackageManager).toString().compareTo(
                        object2.loadLabel(mPackageManager).toString());
            }
        };

        // get the list of live folder intents
        Intent liveFolderIntent = new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER);
        mFolderList = mPackageManager.queryIntentActivities(liveFolderIntent, 0);

        // manually create a separate entry for creating a folder in Launcher
        ResolveInfo folder = new ResolveInfo();
        folder.icon = R.drawable.ic_launcher_folder;
        folder.labelRes = R.string.group_folder;
        folder.resolvePackageName = context.getPackageName();
        mFolderList.add(0, folder);
        Collections.sort(mFolderList, resolveInfoComparator);

        // get the list of shortcuts
        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        mShortcutList = mPackageManager.queryIntentActivities(shortcutsIntent, 0);
        Collections.sort(mShortcutList, resolveInfoComparator);

        invalidatePageData();
    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void setCustomizationFilter(CustomizationType filterType) {
        mCustomizationType = filterType;
        setCurrentScreen(0);
        invalidatePageData();
    }

    @Override
    public void onDropCompleted(View target, boolean success) {
        // do nothing
    }

    @Override
    public boolean onLongClick(View v) {
        if (!v.isInTouchMode()) {
            return false;
        }

        final View animView = v;
        switch (mCustomizationType) {
        case WidgetCustomization:
            AppWidgetProviderInfo appWidgetInfo = (AppWidgetProviderInfo) v.getTag();
            LauncherAppWidgetInfo dragInfo = new LauncherAppWidgetInfo(appWidgetInfo.provider);
            dragInfo.minWidth = appWidgetInfo.minWidth;
            dragInfo.minHeight = appWidgetInfo.minHeight;
            mDragController.startDrag(v, this, dragInfo, DragController.DRAG_ACTION_COPY);
            mLauncher.hideCustomizationDrawer();
            return true;
        case FolderCustomization:
            // animate some feedback to the long press
            animateClickFeedback(v, new Runnable() {
                @Override
                public void run() {
                    // add the folder
                    ResolveInfo resolveInfo = (ResolveInfo) animView.getTag();
                    Intent createFolderIntent = new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER);
                    if (resolveInfo.labelRes == R.string.group_folder) {
                        // Create app shortcuts is a special built-in case of shortcuts
                        createFolderIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                            getContext().getString(R.string.group_folder));
                    } else {
                        ComponentName name = new ComponentName(resolveInfo.activityInfo.packageName,
                                resolveInfo.activityInfo.name);
                        createFolderIntent.setComponent(name);
                    }
                    mLauncher.prepareAddItemFromHomeCustomizationDrawer();
                    mLauncher.addLiveFolder(createFolderIntent);
                }
            });
            return true;
        case ShortcutCustomization:
            // animate some feedback to the long press
            animateClickFeedback(v, new Runnable() {
                @Override
                public void run() {
                    // add the shortcut
                    ResolveInfo info = (ResolveInfo) animView.getTag();
                    Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                    if (info.labelRes == R.string.group_applications) {
                        // Create app shortcuts is a special built-in case of shortcuts
                        createShortcutIntent.putExtra(
                                Intent.EXTRA_SHORTCUT_NAME,getContext().getString(
                                        R.string.group_applications));
                    } else {
                        ComponentName name = new ComponentName(info.activityInfo.packageName, 
                                info.activityInfo.name);
                        createShortcutIntent.setComponent(name);
                    }
                    mLauncher.prepareAddItemFromHomeCustomizationDrawer();
                    mLauncher.processShortcut(createShortcutIntent);
                }
            });
            return true;
        }
        return false;
    }

    private int relayoutWidgets() {
        final int widgetCount = mWidgetList.size();
        if (widgetCount == 0) return 0;

        mWidgetPages.clear();
        ArrayList<PagedViewCellLayout.LayoutParams> page = 
            new ArrayList<PagedViewCellLayout.LayoutParams>();
        mWidgetPages.add(page);
        int rowOffsetX = 0;
        int rowOffsetY = 0;
        int curRowHeight = 0;
        // we only get the cell dims this way for the layout calculations because
        // we know that we aren't going to change the dims when we construct it
        // afterwards
        for (int i = 0; i < widgetCount; ++i) {
            AppWidgetProviderInfo info = mWidgetList.get(i);
            PagedViewCellLayout.LayoutParams params;

            final int cellSpanX = mTmpWidgetLayout.estimateCellHSpan(info.minWidth);
            final int cellSpanY = mTmpWidgetLayout.estimateCellVSpan(info.minHeight);

            if (((rowOffsetX + cellSpanX) <= mCellCountX) &&
                    ((rowOffsetY + cellSpanY) <= mCellCountY)) {
                // just add to end of current row
                params = new PagedViewCellLayout.LayoutParams(rowOffsetX, rowOffsetY,
                        cellSpanX, cellSpanY);

                rowOffsetX += cellSpanX;
                curRowHeight = Math.max(curRowHeight, cellSpanY);
            } else {
                /*
                // fix all the items in this last row to be bottom aligned
                int prevRowOffsetX = rowOffsetX;
                for (int j = page.size() - 1; j >= 0; --j) {
                    PagedViewCellLayout.LayoutParams params = page.get(j);
                    // skip once we get to the previous row
                    if (params.cellX > prevRowOffsetX)
                        break;

                    params.cellY += curRowHeight - params.cellVSpan;
                    prevRowOffsetX = params.cellX;
                }
                */

                // doesn't fit on current row, see if we can start a new row on
                // this page
                if ((rowOffsetY + curRowHeight + cellSpanY) > mCellCountY) {
                    // start a new page and add this item to it
                    page = new ArrayList<PagedViewCellLayout.LayoutParams>();
                    mWidgetPages.add(page);

                    params = new PagedViewCellLayout.LayoutParams(0, 0, cellSpanX, cellSpanY);
                    rowOffsetX = cellSpanX;
                    rowOffsetY = 0;
                    curRowHeight = cellSpanY;
                } else {
                    // add it to the current page on this new row
                    params = new PagedViewCellLayout.LayoutParams(0, rowOffsetY + curRowHeight,
                            cellSpanX, cellSpanY);

                    rowOffsetX = cellSpanX;
                    rowOffsetY += curRowHeight;
                    curRowHeight = cellSpanY;
                }
            }

            params.setTag(info);
            page.add(params);
        }
        return mWidgetPages.size();
    }

    private Drawable getWidgetIcon(PagedViewCellLayout.LayoutParams params, 
            AppWidgetProviderInfo info) {
        PackageManager packageManager = mLauncher.getPackageManager();
        String packageName = info.provider.getPackageName();
        Drawable drawable = null;
        if (info.previewImage != 0) {
            drawable = packageManager.getDrawable(packageName, info.previewImage, null);
            if (drawable == null) {
                Log.w(TAG, "Can't load icon drawable 0x" + Integer.toHexString(info.icon)
                        + " for provider: " + info.provider);
            } else {
                return drawable;
            }
        }

        // If we don't have a preview image, create a default one
        if (drawable == null) {
            Resources resources = mLauncher.getResources();

            // Determine the size the widget will take in the layout
            // Create a new bitmap to hold the widget preview
            int[] dims = mTmpWidgetLayout.estimateCellDimensions(getMeasuredWidth(), 
                    getMeasuredHeight(), params.cellHSpan, params.cellVSpan);
            final int width = dims[0];
            final int height = dims[1] - 35;
            // TEMP
            // TEMP: HACK ABOVE TO GET TEXT TO SHOW
            // TEMP
            Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            mCanvas.setBitmap(bitmap);
            // For some reason, we must re-set the clip rect here, otherwise it will be wrong
            mCanvas.clipRect(0, 0, width, height, Op.REPLACE);

            Drawable background = resources.getDrawable(R.drawable.default_widget_preview);
            background.setBounds(0, 0, width, height);
            background.draw(mCanvas);

            // Draw the icon vertically centered, flush left
            try {
                Rect tmpRect = new Rect();
                Drawable icon = null;
                if (info.icon != 0) {
                    icon = packageManager.getDrawable(packageName, info.icon, null);
                } else {
                    icon = resources.getDrawable(R.drawable.ic_launcher_application);
                }
                background.getPadding(tmpRect);

                final int iconSize = Math.min(
                        Math.min(icon.getIntrinsicWidth(), width - tmpRect.left - tmpRect.right),
                        Math.min(icon.getIntrinsicHeight(), height - tmpRect.top - tmpRect.bottom));
                final int left = (width / 2) - (iconSize / 2);
                final int top = (height / 2) - (iconSize / 2);
                icon.setBounds(new Rect(left, top, left + iconSize, top + iconSize));
                icon.draw(mCanvas);
            } catch (Resources.NotFoundException e) {
                // if we can't find the icon, then just don't draw it
            }

            drawable = new FastBitmapDrawable(bitmap);
        }
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        return drawable;
    }

    private void setupPage(PagedViewCellLayout layout) {
        layout.setCellCount(mCellCountX, mCellCountY);
        layout.setPadding(20, 10, 20, 0);
    }

    private void syncWidgetPages() {
        if (mWidgetList == null) return;

        // calculate the layout for all the widget pages first and ensure that
        // we have the right number of pages
        int numPages = relayoutWidgets();
        int curNumPages = getChildCount();
        // remove any extra pages after the "last" page
        int extraPageDiff = curNumPages - numPages;
        for (int i = 0; i < extraPageDiff; ++i) {
            removeViewAt(numPages);
        }
        // add any necessary pages
        for (int i = curNumPages; i < numPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
            setupPage(layout);
            addView(layout);
        }
    }

    private void syncWidgetPageItems(int page) {
        // ensure that we have the right number of items on the pages
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);
        final ArrayList<PagedViewCellLayout.LayoutParams> list = mWidgetPages.get(page);
        final int count = list.size();
        layout.removeAllViews();
        for (int i = 0; i < count; ++i) {
            PagedViewCellLayout.LayoutParams params = list.get(i);
            AppWidgetProviderInfo info = (AppWidgetProviderInfo) params.getTag();
            final Drawable icon = getWidgetIcon(params, info);
            TextView text = (TextView) mInflater.inflate(R.layout.customize_paged_view_widget, 
                    layout, false);
            text.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
            text.setText(info.label);
            text.setTag(info);
            text.setOnLongClickListener(this);

            layout.addViewToCellLayout(text, -1, mWidgetList.indexOf(info), params);
        }
    }

    private void syncListPages(List<ResolveInfo> list) {
        // ensure that we have the right number of pages
        int numPages = (int) Math.ceil((float) list.size() / (mCellCountX * mCellCountY));
        int curNumPages = getChildCount();
        // remove any extra pages after the "last" page
        int extraPageDiff = curNumPages - numPages;
        for (int i = 0; i < extraPageDiff; ++i) {
            removeViewAt(numPages);
        }
        // add any necessary pages
        for (int i = curNumPages; i < numPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
            setupPage(layout);
            addView(layout);
        }
    }

    private void syncListPageItems(int page, List<ResolveInfo> list) {
        // ensure that we have the right number of items on the pages
        int numCells = mCellCountX * mCellCountY;
        int startIndex = page * numCells;
        int endIndex = Math.min(startIndex + numCells, list.size());
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);
        // TODO: we can optimize by just re-applying to existing views
        layout.removeAllViews();
        for (int i = startIndex; i < endIndex; ++i) {
            ResolveInfo info = list.get(i);
            Drawable image = info.loadIcon(mPackageManager);
            TextView text = (TextView) mInflater.inflate(R.layout.customize_paged_view_item, 
                    layout, false);
            image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
            text.setCompoundDrawablesWithIntrinsicBounds(null, image, null, null);
            text.setText(info.loadLabel(mPackageManager));
            text.setTag(info);
            text.setOnLongClickListener(this);

            final int index = i - startIndex;
            final int x = index % mCellCountX;
            final int y = index / mCellCountX;
            layout.addViewToCellLayout(text, -1, i, new PagedViewCellLayout.LayoutParams(x,y, 1,1));
        }
    }

    private void syncWallpaperPages() {
        // NOT CURRENTLY IMPLEMENTED
        // ensure that we have the right number of pages
        int numPages = 1;
        int curNumPages = getChildCount();
        // remove any extra pages after the "last" page
        int extraPageDiff = curNumPages - numPages;
        for (int i = 0; i < extraPageDiff; ++i) {
            removeViewAt(numPages);
        }
        // add any necessary pages
        for (int i = curNumPages; i < numPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
            setupPage(layout);
            addView(layout);
        }
    }

    private void syncWallpaperPageItems(int page) {
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);
        layout.removeAllViews();

        TextView text = (TextView) mInflater.inflate(
                R.layout.customize_paged_view_wallpaper_placeholder, layout, false);
        // NOTE: this is just place holder text until MikeJurka implements wallpaper picker
        text.setText("Wallpaper customization coming soon!");

        layout.addViewToCellLayout(text, -1, 0, new PagedViewCellLayout.LayoutParams(0, 0, 3, 1));
    }

    @Override
    public void syncPages() {
        switch (mCustomizationType) {
        case WidgetCustomization:
            syncWidgetPages();
            break;
        case FolderCustomization:
            syncListPages(mFolderList);
            break;
        case ShortcutCustomization:
            syncListPages(mShortcutList);
            break;
        case WallpaperCustomization:
            syncWallpaperPages();
            break;
        default:
            removeAllViews();
            setCurrentScreen(0);
            break;
        }

        // only try and center the page if there is one page
        final int childCount = getChildCount();
        if (childCount == 1) {
            PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(0);
            layout.enableCenteredContent(true);
        } else {
            for (int i = 0; i < childCount; ++i) {
                PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
                layout.enableCenteredContent(false);
            }
        }

        // bound the current page
        setCurrentScreen(Math.max(0, Math.min(childCount - 1, getCurrentScreen())));
    }

    @Override
    public void syncPageItems(int page) {
        switch (mCustomizationType) {
        case WidgetCustomization:
            syncWidgetPageItems(page);
            break;
        case FolderCustomization:
            syncListPageItems(page, mFolderList);
            break;
        case ShortcutCustomization:
            syncListPageItems(page, mShortcutList);
            break;
        case WallpaperCustomization:
            syncWallpaperPageItems(page);
            break;
        }
    }
}
