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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher.R;

public class CustomizePagedView extends PagedView
    implements View.OnLongClickListener, View.OnClickListener,
                DragSource, ActionMode.Callback {

    public enum CustomizationType {
        WidgetCustomization,
        ShortcutCustomization,
        WallpaperCustomization,
        ApplicationCustomization
    }

    private static final String TAG = "CustomizeWorkspace";
    private static final boolean DEBUG = false;

    private Launcher mLauncher;
    private DragController mDragController;
    private PackageManager mPackageManager;

    private CustomizationType mCustomizationType;

    // The layout used to emulate the workspace in resolve the cell dimensions of a widget
    private PagedViewCellLayout mWorkspaceWidgetLayout;

    // The mapping between the pages and the widgets that will be laid out on them
    private ArrayList<ArrayList<AppWidgetProviderInfo>> mWidgetPages;

    // The max dimensions for the ImageView we use for displaying a widget
    private int mMaxWidgetWidth;

    // The max number of widget cells to take a "page" of widgets
    private int mMaxWidgetsCellHSpan;

    // The size of the items on the wallpaper tab
    private int mWallpaperCellHSpan;

    // The max dimensions for the ImageView we use for displaying a wallpaper
    private int mMaxWallpaperWidth;

    // The raw sources of data for each of the different tabs of the customization page
    private List<AppWidgetProviderInfo> mWidgetList;
    private List<ResolveInfo> mShortcutList;
    private List<ResolveInfo> mWallpaperList;
    private List<ApplicationInfo> mApps;

    private static final int sMinWidgetCellHSpan = 2;
    private static final int sMaxWidgetCellHSpan = 4;

    private int mChoiceModeTitleText;

    // The scale factor for widget previews inside the widget drawer
    private static final float sScaleFactor = 0.75f;

    private final Canvas mCanvas = new Canvas();
    private final LayoutInflater mInflater;

    public CustomizePagedView(Context context) {
        this(context, null, 0);
    }

    public CustomizePagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomizePagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a;
        a = context.obtainStyledAttributes(attrs, R.styleable.CustomizePagedView, defStyle, 0);
        mWallpaperCellHSpan = a.getInt(R.styleable.CustomizePagedView_wallpaperCellSpanX, 4);
        mMaxWidgetsCellHSpan = a.getInt(R.styleable.CustomizePagedView_widgetCellCountX, 8);
        a.recycle();
        a = context.obtainStyledAttributes(attrs, R.styleable.PagedView, defStyle, 0);
        mCellCountX = a.getInt(R.styleable.PagedView_cellCountX, 7);
        mCellCountY = a.getInt(R.styleable.PagedView_cellCountY, 4);
        a.recycle();

        mCustomizationType = CustomizationType.WidgetCustomization;
        mWidgetPages = new ArrayList<ArrayList<AppWidgetProviderInfo>>();
        mWorkspaceWidgetLayout = new PagedViewCellLayout(context);
        mInflater = LayoutInflater.from(context);

        setVisibility(View.GONE);
        setSoundEffectsEnabled(false);
        setupWorkspaceLayout();
    }

    @Override
    protected void init() {
        super.init();
        mCenterPagesVertically = false;
    }

    public void setLauncher(Launcher launcher) {
        Context context = getContext();
        mLauncher = launcher;
        mPackageManager = context.getPackageManager();
    }

    /**
     * Sets the list of applications that launcher has loaded.
     */
    public void setApps(ArrayList<ApplicationInfo> list) {
        mApps = list;
        Collections.sort(mApps, LauncherModel.APP_NAME_COMPARATOR);

        // Update the widgets/shortcuts to reflect changes in the set of available apps
        invalidatePageDataAndIconCache();
    }

    /**
     * Convenience function to add new items to the set of applications that were previously loaded.
     * Called by both updateApps() and setApps().
     */
    private void addAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // we add it in place, in alphabetical order
        final int count = list.size();
        for (int i = 0; i < count; ++i) {
            final ApplicationInfo info = list.get(i);
            final int index = Collections.binarySearch(mApps, info, LauncherModel.APP_NAME_COMPARATOR);
            if (index < 0) {
                mApps.add(-(index + 1), info);
            }
        }
    }

    /**
     * Adds new applications to the loaded list, and notifies the paged view to update itself.
     */
    public void addApps(ArrayList<ApplicationInfo> list) {
        addAppsWithoutInvalidate(list);

        // Update the widgets/shortcuts to reflect changes in the set of available apps
        invalidatePageDataAndIconCache();
    }

    /**
     * Convenience function to remove items to the set of applications that were previously loaded.
     * Called by both updateApps() and removeApps().
     */
    private void removeAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // loop through all the apps and remove apps that have the same component
        final int length = list.size();
        for (int i = 0; i < length; ++i) {
            final ApplicationInfo info = list.get(i);
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex > -1) {
                mApps.remove(removeIndex);
                mPageViewIconCache.removeOutline(info);
            }
        }
    }

    /**
     * Removes applications from the loaded list, and notifies the paged view to update itself.
     */
    public void removeApps(ArrayList<ApplicationInfo> list) {
        removeAppsWithoutInvalidate(list);

        // Update the widgets/shortcuts to reflect changes in the set of available apps
        invalidatePageDataAndIconCache();
    }

    /**
     * Updates a set of applications from the loaded list, and notifies the paged view to update
     * itself.
     */
    public void updateApps(ArrayList<ApplicationInfo> list) {
        // We remove and re-add the updated applications list because it's properties may have
        // changed (ie. the title), and this will ensure that the items will be in their proper
        // place in the list.
        removeAppsWithoutInvalidate(list);
        addAppsWithoutInvalidate(list);

        // Update the widgets/shortcuts to reflect changes in the set of available apps
        invalidatePageDataAndIconCache();
    }

    /**
     * Convenience function to find matching ApplicationInfos for removal.
     */
    private int findAppByComponent(List<ApplicationInfo> list, ApplicationInfo item) {
        ComponentName removeComponent = item.intent.getComponent();
        final int length = list.size();
        for (int i = 0; i < length; ++i) {
            ApplicationInfo info = list.get(i);
            if (info.intent.getComponent().equals(removeComponent)) {
                return i;
            }
        }
        return -1;
    }

    public void update() {
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

        // get the list of shortcuts
        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        mShortcutList = mPackageManager.queryIntentActivities(shortcutsIntent, 0);
        Collections.sort(mShortcutList, resolveInfoComparator);

        // get the list of wallpapers
        Intent wallpapersIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
        mWallpaperList = mPackageManager.queryIntentActivities(wallpapersIntent, 0);
        Collections.sort(mWallpaperList, resolveInfoComparator);

        invalidatePageDataAndIconCache();
    }

    private void invalidatePageDataAndIconCache() {
        // Reset the icon cache
        mPageViewIconCache.clear();

        // Refresh all the tabs
        invalidatePageData();
    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void setCustomizationFilter(CustomizationType filterType) {
        mCustomizationType = filterType;
        setCurrentPage(0);
        updateCurrentPageScroll();
        invalidatePageData();

        // End the current choice mode so that we don't carry selections across tabs
        endChoiceMode();
    }

    @Override
    public void onDropCompleted(View target, boolean success) {
        mLauncher.getWorkspace().onDragStopped();
    }

    @Override
    public void onClick(View v) {
        if (!v.isInTouchMode()) {
            return;
        }

        // On certain pages, we allow single tap to mark items as selected so that they can be
        // dropped onto the mini workspaces
        boolean enterChoiceMode = false;
        switch (mCustomizationType) {
        case WidgetCustomization:
            mChoiceModeTitleText = R.string.cab_widget_selection_text;
            enterChoiceMode = true;
            break;
        case ApplicationCustomization:
            mChoiceModeTitleText = R.string.cab_app_selection_text;
            enterChoiceMode = true;
            break;
        case ShortcutCustomization:
            mChoiceModeTitleText = R.string.cab_shortcut_selection_text;
            enterChoiceMode = true;
            break;
        default:
            break;
        }

        if (enterChoiceMode) {
            final ItemInfo itemInfo = (ItemInfo) v.getTag();

            Workspace w = mLauncher.getWorkspace();
            int currentWorkspaceScreen = mLauncher.getCurrentWorkspaceScreen();
            final CellLayout cl = (CellLayout)w.getChildAt(currentWorkspaceScreen);

            animateClickFeedback(v, new Runnable() {
                @Override
                public void run() {
                    mLauncher.addExternalItemToScreen(itemInfo, cl);
                }
            });
            return;
        }

        // Otherwise, we just handle the single click here
        switch (mCustomizationType) {
        case WallpaperCustomization:
            // animate some feedback to the long press
            final View clickView = v;
            animateClickFeedback(v, new Runnable() {
                @Override
                public void run() {
                    // add the shortcut
                    ResolveInfo info = (ResolveInfo) clickView.getTag();
                    Intent createWallpapersIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
                    ComponentName name = new ComponentName(info.activityInfo.packageName,
                            info.activityInfo.name);
                    createWallpapersIntent.setComponent(name);
                    mLauncher.processWallpaper(createWallpapersIntent);
                }
            });
            break;
        default:
            break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (!v.isInTouchMode()) {
            return false;
        }

        // End the current choice mode before we start dragging anything
        if (isChoiceMode(CHOICE_MODE_SINGLE)) {
            endChoiceMode();
        }

        PendingAddItemInfo createItemInfo;
        switch (mCustomizationType) {
        case WidgetCustomization:
            // Get the icon as the drag representation
            final LinearLayout l = (LinearLayout) v;
            final Drawable icon = ((ImageView) l.findViewById(R.id.widget_preview)).getDrawable();
            Bitmap b = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            icon.draw(c);
            PendingAddWidgetInfo createWidgetInfo = (PendingAddWidgetInfo) v.getTag();

            mLauncher.getWorkspace().onDragStartedWithItemMinSize(
                    createWidgetInfo.minWidth, createWidgetInfo.minHeight);
            mDragController.startDrag(v, b, this, createWidgetInfo, DragController.DRAG_ACTION_COPY,
                    null);

            // Cleanup the icon
            b.recycle();
            return true;
        case ShortcutCustomization:
            createItemInfo = (PendingAddItemInfo) v.getTag();
            mDragController.startDrag(
                    v, this, createItemInfo, DragController.DRAG_ACTION_COPY, null);
            mLauncher.getWorkspace().onDragStartedWithItemSpans(1, 1);
            return true;
        case ApplicationCustomization:
            // Pick up the application for dropping
            ApplicationInfo app = (ApplicationInfo) v.getTag();
            app = new ApplicationInfo(app);

            mDragController.startDrag(v, this, app, DragController.DRAG_ACTION_COPY);
            mLauncher.getWorkspace().onDragStartedWithItemSpans(1, 1);
            return true;
        }
        return false;
    }

    /**
     * Pre-processes the layout of the different widget pages.
     * @return the number of pages of widgets that we have
     */
    private int relayoutWidgets() {
        if (mWidgetList.isEmpty()) return 0;

        // create a new page for the first set of widgets
        ArrayList<AppWidgetProviderInfo> newPage = new ArrayList<AppWidgetProviderInfo>();
        mWidgetPages.clear();
        mWidgetPages.add(newPage);

        // do this until we have no more widgets to lay out
        final int maxNumCellsPerRow = mMaxWidgetsCellHSpan;
        final int widgetCount = mWidgetList.size();
        int numCellsInRow = 0;
        for (int i = 0; i < widgetCount; ++i) {
            final AppWidgetProviderInfo info = mWidgetList.get(i);

            // determine the size of the current widget
            int cellSpanX = Math.max(sMinWidgetCellHSpan, Math.min(sMaxWidgetCellHSpan,
                    mWorkspaceWidgetLayout.estimateCellHSpan(info.minWidth)));

            // create a new page if necessary
            if ((numCellsInRow + cellSpanX) > maxNumCellsPerRow) {
                numCellsInRow = 0;
                newPage = new ArrayList<AppWidgetProviderInfo>();
                mWidgetPages.add(newPage);
            }

            // add the item to the current page
            newPage.add(info);
            numCellsInRow += cellSpanX;
        }

        return mWidgetPages.size();
    }

    /**
     * Helper function to draw a drawable to the specified canvas with the specified bounds.
     */
    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h) {
        if (bitmap != null) mCanvas.setBitmap(bitmap);
        mCanvas.save();
        d.setBounds(x, y, x+w, y+h);
        d.draw(mCanvas);
        mCanvas.restore();
    }

    /**
     * This method will extract the preview image specified by the wallpaper source provider (if it
     * exists) otherwise, it will try to generate a default image preview.
     */
    private Drawable getWallpaperPreview(ResolveInfo info) {
        // To be implemented later: resolving the up-to-date wallpaper thumbnail

        final int minDim = mWorkspaceWidgetLayout.estimateCellWidth(1);
        final int dim = mWorkspaceWidgetLayout.estimateCellWidth(mWallpaperCellHSpan);
        Resources resources = mLauncher.getResources();

        // Create a new bitmap to hold the widget preview
        int width = (int) (dim * sScaleFactor);
        int height = (int) (dim * sScaleFactor);
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        final Drawable background = resources.getDrawable(R.drawable.default_widget_preview);
        renderDrawableToBitmap(background, bitmap, 0, 0, width, height);

        // Draw the icon flush left
        try {
            final IconCache iconCache =
                ((LauncherApplication) mLauncher.getApplication()).getIconCache();
            Drawable icon = new FastBitmapDrawable(Utilities.createIconBitmap(
                    iconCache.getFullResIcon(info, mPackageManager), mContext));

            final int iconSize = minDim / 2;
            final int offset = iconSize / 4;
            renderDrawableToBitmap(icon, null, offset, offset, iconSize, iconSize);
        } catch (Resources.NotFoundException e) {
            // if we can't find the icon, then just don't draw it
        }

        Drawable drawable = new FastBitmapDrawable(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        return drawable;
    }

    /**
     * This method will extract the preview image specified by the widget developer (if it exists),
     * otherwise, it will try to generate a default image preview with the widget's package icon.
     * @return the drawable that will be used and sized in the ImageView to represent the widget
     */
    private Drawable getWidgetPreview(AppWidgetProviderInfo info) {
        final PackageManager packageManager = mPackageManager;
        String packageName = info.provider.getPackageName();
        Drawable drawable = null;
        if (info.previewImage != 0) {
            drawable = packageManager.getDrawable(packageName, info.previewImage, null);
            if (drawable == null) {
                Log.w(TAG, "Can't load icon drawable 0x" + Integer.toHexString(info.icon)
                        + " for provider: " + info.provider);
            }
        }

        // If we don't have a preview image, create a default one
        final int minDim = mWorkspaceWidgetLayout.estimateCellWidth(1);
        final int maxDim = mWorkspaceWidgetLayout.estimateCellWidth(3);
        if (drawable == null) {
            Resources resources = mLauncher.getResources();

            // Create a new bitmap to hold the widget preview
            int width = (int) (Math.max(minDim, Math.min(maxDim, info.minWidth)) * sScaleFactor);
            int height = (int) (Math.max(minDim, Math.min(maxDim, info.minHeight)) * sScaleFactor);
            final Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            final Drawable background = resources.getDrawable(R.drawable.default_widget_preview);
            renderDrawableToBitmap(background, bitmap, 0, 0, width, height);

            // Draw the icon flush left
            try {
                Drawable icon = null;
                if (info.icon > 0) {
                    icon = packageManager.getDrawable(packageName, info.icon, null);
                }
                if (icon == null) {
                    icon = resources.getDrawable(R.drawable.ic_launcher_application);
                }

                final int iconSize = minDim / 2;
                final int offset = iconSize / 4;
                renderDrawableToBitmap(icon, null, offset, offset, iconSize, iconSize);
            } catch (Resources.NotFoundException e) {
                // if we can't find the icon, then just don't draw it
            }

            drawable = new FastBitmapDrawable(bitmap);
        } else {
            // Scale down the preview if necessary
            final float imageWidth = drawable.getIntrinsicWidth();
            final float imageHeight = drawable.getIntrinsicHeight();
            final float aspect = (float) imageWidth / imageHeight;
            final int scaledWidth =
                (int) (Math.max(minDim, Math.min(maxDim, imageWidth)) * sScaleFactor);
            final int scaledHeight =
                (int) (Math.max(minDim, Math.min(maxDim, imageHeight)) * sScaleFactor);
            int width;
            int height;
            if (aspect >= 1.0f) {
                width = scaledWidth;
                height = (int) (((float) scaledWidth / imageWidth) * imageHeight);
            } else {
                height = scaledHeight;
                width = (int) (((float) scaledHeight / imageHeight) * imageWidth);
            }

            final Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            renderDrawableToBitmap(drawable, bitmap, 0, 0, width, height);

            drawable = new FastBitmapDrawable(bitmap);
        }
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        return drawable;
    }

    private void setupPage(PagedViewCellLayout layout) {
        layout.setCellCount(mCellCountX, mCellCountY);
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop, mPageLayoutPaddingRight,
                mPageLayoutPaddingBottom);
        layout.setGap(mPageLayoutWidthGap, mPageLayoutHeightGap);
    }

    private void setupWorkspaceLayout() {
        mWorkspaceWidgetLayout.setCellCount(mCellCountX, mCellCountY);
        mWorkspaceWidgetLayout.setPadding(20, 10, 20, 0);

        mMaxWidgetWidth = mWorkspaceWidgetLayout.estimateCellWidth(sMaxWidgetCellHSpan);
        mMaxWallpaperWidth = mWorkspaceWidgetLayout.estimateCellWidth(mWallpaperCellHSpan);
    }

    private void syncWidgetPages() {
        if (mWidgetList == null) return;

        // we need to repopulate with the LinearLayout layout for the widget pages
        removeAllViews();
        int numPages = relayoutWidgets();
        for (int i = 0; i < numPages; ++i) {
            LinearLayout layout = new PagedViewExtendedLayout(getContext());
            layout.setGravity(Gravity.CENTER_HORIZONTAL);
            layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                    mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

            // Temporary change to prevent the last page from being too small (and items bleeding
            // onto it).  We can remove this once we properly fix the fading algorithm
            if (i < numPages - 1) {
                addView(layout, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
            } else {
                addView(layout, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
            }
        }
    }

    private void syncWidgetPageItems(int page) {
        // ensure that we have the right number of items on the pages
        LinearLayout layout = (LinearLayout) getChildAt(page);
        final ArrayList<AppWidgetProviderInfo> list = mWidgetPages.get(page);
        final int count = list.size();
        layout.removeAllViews();
        for (int i = 0; i < count; ++i) {
            final AppWidgetProviderInfo info = (AppWidgetProviderInfo) list.get(i);
            final PendingAddWidgetInfo createItemInfo = new PendingAddWidgetInfo(info, null, null);

            LinearLayout l = (LinearLayout) mInflater.inflate(
                    R.layout.customize_paged_view_widget, layout, false);
            l.setTag(createItemInfo);
            l.setOnClickListener(this);
            l.setOnLongClickListener(this);

            final Drawable icon = getWidgetPreview(info);

            int[] spans = CellLayout.rectToCell(getResources(), info.minWidth, info.minHeight, null);
            final int hSpan = spans[0];
            final int vSpan = spans[1];

            ImageView image = (ImageView) l.findViewById(R.id.widget_preview);
            image.setMaxWidth(mMaxWidgetWidth);
            image.setImageDrawable(icon);
            TextView name = (TextView) l.findViewById(R.id.widget_name);
            name.setText(info.label);
            TextView dims = (TextView) l.findViewById(R.id.widget_dims);
            dims.setText(mContext.getString(R.string.widget_dims_format, hSpan, vSpan));

            layout.addView(l);
        }
    }

    private void syncWallpaperPages() {
        if (mWallpaperList == null) return;

        // We need to repopulate the LinearLayout for the wallpaper pages
        removeAllViews();
        int numPages = (int) Math.ceil((float) (mWallpaperList.size() * mWallpaperCellHSpan) /
                mMaxWidgetsCellHSpan);
        for (int i = 0; i < numPages; ++i) {
            LinearLayout layout = new PagedViewExtendedLayout(getContext());
            layout.setGravity(Gravity.CENTER_HORIZONTAL);
            layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                    mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

            // Temporary change to prevent the last page from being too small (and items bleeding
            // onto it).  We can remove this once we properly fix the fading algorithm
            if (i < numPages - 1) {
                addView(layout, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
            } else {
                addView(layout, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
            }
        }
    }

    private void syncWallpaperPageItems(int page) {
        // Load the items on to the pages
        LinearLayout layout = (LinearLayout) getChildAt(page);
        layout.removeAllViews();
        final int count = mWallpaperList.size();
        for (int i = 0; i < count; ++i) {
            final ResolveInfo info = mWallpaperList.get(i);

            LinearLayout l = (LinearLayout) mInflater.inflate(
                    R.layout.customize_paged_view_wallpaper, layout, false);
            l.setTag(info);
            l.setOnClickListener(this);

            final Drawable icon = getWallpaperPreview(info);

            ImageView image = (ImageView) l.findViewById(R.id.wallpaper_preview);
            image.setMaxWidth(mMaxWidgetWidth);
            image.setImageDrawable(icon);
            TextView name = (TextView) l.findViewById(R.id.wallpaper_name);
            name.setText(info.loadLabel(mPackageManager));

            layout.addView(l);
        }
    }

    private void syncListPages(List<ResolveInfo> list) {
        // we need to repopulate with PagedViewCellLayouts
        removeAllViews();

        // ensure that we have the right number of pages
        int numPages = (int) Math.ceil((float) list.size() / (mCellCountX * mCellCountY));
        for (int i = 0; i < numPages; ++i) {
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
            PendingAddItemInfo createItemInfo = new PendingAddItemInfo();

            PagedViewIcon icon = (PagedViewIcon) mInflater.inflate(
                    R.layout.customize_paged_view_item, layout, false);
            icon.applyFromResolveInfo(info, mPackageManager, mPageViewIconCache,
                    ((LauncherApplication)mLauncher.getApplication()).getIconCache());
            switch (mCustomizationType) {
            case WallpaperCustomization:
                icon.setOnClickListener(this);
                break;
            case ShortcutCustomization:
                createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
                createItemInfo.componentName = new ComponentName(info.activityInfo.packageName,
                        info.activityInfo.name);
                icon.setTag(createItemInfo);
                icon.setOnClickListener(this);
                icon.setOnLongClickListener(this);
                break;
            default:
                break;
            }

            final int index = i - startIndex;
            final int x = index % mCellCountX;
            final int y = index / mCellCountX;
            setupPage(layout);
            layout.addViewToCellLayout(icon, -1, i, new PagedViewCellLayout.LayoutParams(x,y, 1,1));
        }
    }

    private void syncAppPages() {
        if (mApps == null) return;

        // We need to repopulate with PagedViewCellLayouts
        removeAllViews();

        // Ensure that we have the right number of pages
        int numPages = (int) Math.ceil((float) mApps.size() / (mCellCountX * mCellCountY));
        for (int i = 0; i < numPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
            setupPage(layout);
            addView(layout);
        }
    }

    private void syncAppPageItems(int page) {
        if (mApps == null) return;

        // ensure that we have the right number of items on the pages
        int numCells = mCellCountX * mCellCountY;
        int startIndex = page * numCells;
        int endIndex = Math.min(startIndex + numCells, mApps.size());
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);
        // TODO: we can optimize by just re-applying to existing views
        layout.removeAllViews();
        for (int i = startIndex; i < endIndex; ++i) {
            final ApplicationInfo info = mApps.get(i);
            PagedViewIcon icon = (PagedViewIcon) mInflater.inflate(
                    R.layout.all_apps_paged_view_application, layout, false);
            icon.applyFromApplicationInfo(info, mPageViewIconCache, true);
            icon.setOnClickListener(this);
            icon.setOnLongClickListener(this);

            final int index = i - startIndex;
            final int x = index % mCellCountX;
            final int y = index / mCellCountX;
            setupPage(layout);
            layout.addViewToCellLayout(icon, -1, i, new PagedViewCellLayout.LayoutParams(x,y, 1,1));
        }
    }

    @Override
    public void syncPages() {
        boolean centerPagedViewCellLayouts = false;
        switch (mCustomizationType) {
        case WidgetCustomization:
            syncWidgetPages();
            break;
        case ShortcutCustomization:
            syncListPages(mShortcutList);
            centerPagedViewCellLayouts = true;
            break;
        case WallpaperCustomization:
            syncWallpaperPages();
            break;
        case ApplicationCustomization:
            syncAppPages();
            centerPagedViewCellLayouts = false;
            break;
        default:
            removeAllViews();
            setCurrentPage(0);
            break;
        }

        // only try and center the page if there is one page
        final int childCount = getChildCount();
        if (centerPagedViewCellLayouts) {
            if (childCount == 1) {
                PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(0);
                layout.enableCenteredContent(true);
            } else {
                for (int i = 0; i < childCount; ++i) {
                    PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
                    layout.enableCenteredContent(false);
                }
            }
        }

        // bound the current page
        setCurrentPage(Math.max(0, Math.min(childCount - 1, getCurrentPage())));
    }

    @Override
    public void syncPageItems(int page) {
        switch (mCustomizationType) {
        case WidgetCustomization:
            syncWidgetPageItems(page);
            break;
        case ShortcutCustomization:
            syncListPageItems(page, mShortcutList);
            break;
        case WallpaperCustomization:
            syncWallpaperPageItems(page);
            break;
        case ApplicationCustomization:
            syncAppPageItems(page);
            break;
        }
    }

    @Override
    protected int getAssociatedLowerPageBound(int page) {
        return 0;
    }
    @Override
    protected int getAssociatedUpperPageBound(int page) {
        return getChildCount();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        mode.setTitle(mChoiceModeTitleText);
        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        endChoiceMode();
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }
}
