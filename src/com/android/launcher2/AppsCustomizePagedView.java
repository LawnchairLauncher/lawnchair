/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher.R;

public class AppsCustomizePagedView extends PagedViewWithDraggableItems implements
        AllAppsView, View.OnClickListener, DragSource {
    static final String LOG_TAG = "AppsCustomizePagedView";

    /**
     * The different content types that this paged view can show.
     */
    public enum ContentType {
        Applications,
        Widgets
    }

    // Refs
    private Launcher mLauncher;
    private DragController mDragController;
    private final LayoutInflater mLayoutInflater;
    private final PackageManager mPackageManager;

    // Content
    private ContentType mContentType;
    private ArrayList<ApplicationInfo> mApps;
    private List<AppWidgetProviderInfo> mWidgets;
    private List<ResolveInfo> mShortcuts;

    // Dimens
    private int mContentWidth;
    private float mWidgetScale;
    private PagedViewCellLayout mWidgetSpacingLayout;

    public AppsCustomizePagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLayoutInflater = LayoutInflater.from(context);
        mPackageManager = context.getPackageManager();
        mContentType = ContentType.Applications;
        mApps = new ArrayList<ApplicationInfo>();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedView, 0, 0);
        mCellCountX = a.getInt(R.styleable.PagedView_cellCountX, 6);
        mCellCountY = a.getInt(R.styleable.PagedView_cellCountY, 4);
        a.recycle();

        // Create a dummy page and set it up to find out the content width (used by our parent)
        PagedViewCellLayout layout = new PagedViewCellLayout(context);
        setupPage(layout);
        mContentWidth = layout.getContentWidth();

        // Create a dummy page that we can use to approximate the cell dimensions of widgets
        mWidgetSpacingLayout = new PagedViewCellLayout(context);
        mWidgetSpacingLayout.setCellCount(mCellCountX, mCellCountY);
    }

    @Override
    protected void init() {
        super.init();
        mCenterPagesVertically = false;

        Context context = getContext();
        Resources r = context.getResources();
        setDragSlopeThreshold(r.getInteger(R.integer.config_appsCustomizeDragSlopeThreshold)/100f);
    }

    public void onPackagesUpdated() {
        // Get the list of widgets
        mWidgets = AppWidgetManager.getInstance(mLauncher).getInstalledProviders();
        Collections.sort(mWidgets, LauncherModel.WIDGET_NAME_COMPARATOR);

        // Get the list of shortcuts
        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        mShortcuts = mPackageManager.queryIntentActivities(shortcutsIntent, 0);
        Collections.sort(mShortcuts, new LauncherModel.ShortcutNameComparator(mPackageManager));
    }

    @Override
    public void onClick(View v) {
        // Animate some feedback to the click
        final ApplicationInfo appInfo = (ApplicationInfo) v.getTag();
        animateClickFeedback(v, new Runnable() {
            @Override
            public void run() {
                mLauncher.startActivitySafely(appInfo.intent, appInfo);
            }
        });
    }

    /*
     * PagedViewWithDraggableItems implementation
     */
    @Override
    protected void determineDraggingStart(android.view.MotionEvent ev) {
        // Disable dragging by pulling an app down for now.
    }
    @Override
    protected boolean beginDragging(View v) {
        if (!super.beginDragging(v)) return false;

        // Make a copy of the ApplicationInfo
        ApplicationInfo appInfo = new ApplicationInfo((ApplicationInfo) v.getTag());

        // Show the uninstall button if the app is uninstallable.
        if ((appInfo.flags & ApplicationInfo.DOWNLOADED_FLAG) != 0) {
            DeleteZone allAppsDeleteZone = (DeleteZone)
                    mLauncher.findViewById(R.id.all_apps_delete_zone);
            allAppsDeleteZone.setDragAndDropEnabled(true);

            if ((appInfo.flags & ApplicationInfo.UPDATED_SYSTEM_APP_FLAG) != 0) {
                allAppsDeleteZone.setText(R.string.delete_zone_label_all_apps_system_app);
            } else {
                allAppsDeleteZone.setText(R.string.delete_zone_label_all_apps);
            }
        }

        // Show the info button
        ApplicationInfoDropTarget allAppsInfoButton =
            (ApplicationInfoDropTarget) mLauncher.findViewById(R.id.all_apps_info_target);
        allAppsInfoButton.setDragAndDropEnabled(true);

        // Compose the drag image (top compound drawable, index is 1)
        final TextView tv = (TextView) v;
        final Drawable icon = tv.getCompoundDrawables()[1];
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate((v.getWidth() - icon.getIntrinsicWidth()) / 2, v.getPaddingTop());
        icon.draw(c);

        // Compose the visible rect of the drag image
        Rect dragRect = null;
        if (v instanceof TextView) {
            int iconSize = getResources().getDimensionPixelSize(R.dimen.app_icon_size);
            int top = v.getPaddingTop();
            int left = (b.getWidth() - iconSize) / 2;
            int right = left + iconSize;
            int bottom = top + iconSize;
            dragRect = new Rect(left, top, right, bottom);
        }

        // Start the drag
        mLauncher.lockScreenOrientation();
        mLauncher.getWorkspace().onDragStartedWithItemSpans(1, 1, b);
        mDragController.startDrag(v, b, this, appInfo, DragController.DRAG_ACTION_COPY, dragRect);
        b.recycle();

        // Hide the pane so that the user can drop onto the workspace
        mLauncher.showWorkspace(true);
        return true;
    }
    private void endDragging(boolean success) {
        post(new Runnable() {
            // Once the drag operation has fully completed, hence the post, we want to disable the
            // deleteZone and the appInfoButton in all apps, and re-enable the instance which
            // live in the workspace
            public void run() {
                // if onDestroy was called on Launcher, we might have already deleted the
                // all apps delete zone / info button, so check if they are null
                DeleteZone allAppsDeleteZone =
                        (DeleteZone) mLauncher.findViewById(R.id.all_apps_delete_zone);
                ApplicationInfoDropTarget allAppsInfoButton =
                    (ApplicationInfoDropTarget) mLauncher.findViewById(R.id.all_apps_info_target);

                if (allAppsDeleteZone != null) allAppsDeleteZone.setDragAndDropEnabled(false);
                if (allAppsInfoButton != null) allAppsInfoButton.setDragAndDropEnabled(false);
            }
        });
        mLauncher.getWorkspace().onDragStopped(success);
        mLauncher.unlockScreenOrientation();
    }

    /*
     * DragSource implementation
     */
    @Override
    public void onDragViewVisible() {}
    @Override
    public void onDropCompleted(View target, Object dragInfo, boolean success) {
        endDragging(success);
    }

    public void setContentType(ContentType type) {
        mContentType = type;
        setCurrentPage(0);
        invalidatePageData();
    }

    /*
     * Apps PagedView implementation
     */
    private void setupPage(PagedViewCellLayout layout) {
        layout.setCellCount(mCellCountX, mCellCountY);
        layout.setGap(mPageLayoutWidthGap, mPageLayoutHeightGap);
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

        // We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        layout.measure(widthSpec, heightSpec);
    }
    public void syncAppsPages() {
        // Ensure that we have the right number of pages
        Context context = getContext();
        int numPages = (int) Math.ceil((float) mApps.size() / (mCellCountX * mCellCountY));
        for (int i = 0; i < numPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(context);
            setupPage(layout);
            addView(layout);
        }
    }
    public void syncAppsPageItems(int page) {
        // ensure that we have the right number of items on the pages
        int numPages = getPageCount();
        int numCells = mCellCountX * mCellCountY;
        int startIndex = page * numCells;
        int endIndex = Math.min(startIndex + numCells, mApps.size());
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);
        layout.removeAllViewsOnPage();
        for (int i = startIndex; i < endIndex; ++i) {
            ApplicationInfo info = mApps.get(i);
            PagedViewIcon icon = (PagedViewIcon) mLayoutInflater.inflate(
                    R.layout.apps_customize_application, layout, false);
            icon.applyFromApplicationInfo(info, mPageViewIconCache, true, (numPages > 1));
            icon.setOnClickListener(this);
            icon.setOnLongClickListener(this);
            icon.setOnTouchListener(this);

            int index = i - startIndex;
            int x = index % mCellCountX;
            int y = index / mCellCountX;
            setupPage(layout);
            layout.addViewToCellLayout(icon, -1, i, new PagedViewCellLayout.LayoutParams(x,y, 1,1));
        }
    }
    /*
     * Widgets PagedView implementation
     */
    private void setupPage(PagedViewExtendedLayout layout) {
        layout.setGravity(Gravity.LEFT);
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);
        layout.setMinimumWidth(getPageContentWidth());
    }
    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h,
            float scaleX, float scaleY) {
        Canvas c = new Canvas();
        if (bitmap != null) c.setBitmap(bitmap);
        c.save();
        c.scale(scaleX, scaleY);
        Rect oldBounds = d.copyBounds();
        d.setBounds(x, y, x + w, y + h);
        d.draw(c);
        d.setBounds(oldBounds); // Restore the bounds
        c.restore();
    }
    private FastBitmapDrawable getWidgetPreview(AppWidgetProviderInfo info) {
        // See CustomizePagedView.getWidgetPreview()
        return null;
    }
    public void syncWidgetPages() {
        // Ensure that we have the right number of pages
        Context context = getContext();
        int numPages = mWidgets.size();
        for (int i = 0; i < numPages; ++i) {
            PagedViewExtendedLayout layout = new PagedViewExtendedLayout(context);
            setupPage(layout);
            addView(layout);
        }
    }
    public void syncWidgetPageItems(int page) {
        PagedViewExtendedLayout layout = (PagedViewExtendedLayout) getChildAt(page);
        layout.removeAllViewsOnPage();
        for (int i = 0; i < 1; ++i) {
            AppWidgetProviderInfo info = (AppWidgetProviderInfo) mWidgets.get(page);
            FastBitmapDrawable icon = getWidgetPreview(info);

            ImageView image = new ImageView(getContext());
            image.setBackgroundColor(0x99FF0000);
            image.setImageDrawable(icon);
            layout.addView(image, new PagedViewExtendedLayout.LayoutParams());
        }
    }
    @Override
    public void syncPages() {
        removeAllViews();
        switch (mContentType) {
        case Applications:
            syncAppsPages();
            break;
        case Widgets:
            syncWidgetPages();
            break;
        }
    }
    @Override
    public void syncPageItems(int page) {
        switch (mContentType) {
        case Applications:
            syncAppsPageItems(page);
            break;
        case Widgets:
            syncWidgetPageItems(page);
            break;
        }
    }

    /**
     * Used by the parent to get the content width to set the tab bar to
     * @return
     */
    public int getPageContentWidth() {
        return mContentWidth;
    }

    /*
     * AllAppsView implementation
     */
    @Override
    public void setup(Launcher launcher, DragController dragController) {
        mLauncher = launcher;
        mDragController = dragController;
    }
    @Override
    public void zoom(float zoom, boolean animate) {
        // TODO-APPS_CUSTOMIZE: Call back to mLauncher.zoomed()
    }
    @Override
    public boolean isVisible() {
        return (getVisibility() == VISIBLE);
    }
    @Override
    public boolean isAnimating() {
        return false;
    }
    @Override
    public void setApps(ArrayList<ApplicationInfo> list) {
        mApps = list;
        Collections.sort(mApps, LauncherModel.APP_NAME_COMPARATOR);
        invalidatePageData();
    }
    private void addAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // We add it in place, in alphabetical order
        int count = list.size();
        for (int i = 0; i < count; ++i) {
            ApplicationInfo info = list.get(i);
            int index = Collections.binarySearch(mApps, info, LauncherModel.APP_NAME_COMPARATOR);
            if (index < 0) {
                mApps.add(-(index + 1), info);
            }
        }
    }
    @Override
    public void addApps(ArrayList<ApplicationInfo> list) {
        addAppsWithoutInvalidate(list);
        invalidatePageData();
    }
    private int findAppByComponent(List<ApplicationInfo> list, ApplicationInfo item) {
        ComponentName removeComponent = item.intent.getComponent();
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            ApplicationInfo info = list.get(i);
            if (info.intent.getComponent().equals(removeComponent)) {
                return i;
            }
        }
        return -1;
    }
    private void removeAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // loop through all the apps and remove apps that have the same component
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            ApplicationInfo info = list.get(i);
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex > -1) {
                mApps.remove(removeIndex);
                mPageViewIconCache.removeOutline(new PagedViewIconCache.Key(info));
            }
        }
    }
    @Override
    public void removeApps(ArrayList<ApplicationInfo> list) {
        removeAppsWithoutInvalidate(list);
        invalidatePageData();
    }
    @Override
    public void updateApps(ArrayList<ApplicationInfo> list) {
        // We remove and re-add the updated applications list because it's properties may have
        // changed (ie. the title), and this will ensure that the items will be in their proper
        // place in the list.
        removeAppsWithoutInvalidate(list);
        addAppsWithoutInvalidate(list);
        invalidatePageData();
    }
    @Override
    public void reset() {
        setCurrentPage(0);
        invalidatePageData();
    }
    @Override
    public void dumpState() {
        // TODO: Dump information related to current list of Applications, Widgets, etc.
        ApplicationInfo.dumpApplicationInfoList(LOG_TAG, "mApps", mApps);
        dumpAppWidgetProviderInfoList(LOG_TAG, "mWidgets", mWidgets);
    }
    private void dumpAppWidgetProviderInfoList(String tag, String label,
            List<AppWidgetProviderInfo> list) {
        Log.d(tag, label + " size=" + list.size());
        for (AppWidgetProviderInfo info: list) {
            Log.d(tag, "   label=\"" + info.label + "\" previewImage=" + info.previewImage
                    + " resizeMode=" + info.resizeMode + " configure=" + info.configure
                    + " initialLayout=" + info.initialLayout
                    + " minWidth=" + info.minWidth + " minHeight=" + info.minHeight);
        }
    }
    @Override
    public void surrender() {
        // TODO: If we are in the middle of any process (ie. for holographic outlines, etc) we
        // should stop this now.
    }
}
