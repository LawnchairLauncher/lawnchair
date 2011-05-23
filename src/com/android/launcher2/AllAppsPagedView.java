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

import com.android.launcher.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Checkable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/**
 * An implementation of PagedView that populates the pages of the workspace
 * with all of the user's applications.
 */
public class AllAppsPagedView extends PagedViewWithDraggableItems implements AllAppsView,
    View.OnClickListener, DragSource, DropTarget {

    private static final String TAG = "AllAppsPagedView";

    private Launcher mLauncher;
    private DragController mDragController;

    // preserve compatibility with 3D all apps:
    //    0.0 -> hidden
    //    1.0 -> shown and opaque
    //    intermediate values -> partially shown & partially opaque
    private float mZoom;

    // set of all applications
    private ArrayList<ApplicationInfo> mApps;
    private ArrayList<ApplicationInfo> mFilteredApps;

    // the types of applications to filter
    static final int ALL_APPS_FLAG = -1;
    private int mAppFilter = ALL_APPS_FLAG;

    private final LayoutInflater mInflater;
    private boolean mAllowHardwareLayerCreation;

    private int mPageContentWidth;

    private int mLastMeasureWidth = -1;
    private int mLastMeasureHeight = -1;

    private int mMaxCellCountY;

    public AllAppsPagedView(Context context) {
        this(context, null);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedView, defStyle, 0);
        mInflater = LayoutInflater.from(context);
        mApps = new ArrayList<ApplicationInfo>();
        mFilteredApps = new ArrayList<ApplicationInfo>();
        a.recycle();
        setSoundEffectsEnabled(false);

        final Resources r = context.getResources();
        setDragSlopeThreshold(
                r.getInteger(R.integer.config_allAppsDrawerDragSlopeThreshold) / 100.0f);

        // Create a dummy page and set it up to find out the content width (used by our parent)
        PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
        setupPage(layout);
        mPageContentWidth = layout.getContentWidth();
        mMaxCellCountY = r.getInteger(R.integer.all_apps_view_maxCellCountY);
    }

    @Override
    protected void init() {
        super.init();
        mCenterPagesVertically = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        if (mLastMeasureWidth != width || mLastMeasureHeight != height) {
            // Create a dummy page and set it up to find out the content width (used by our parent)
            PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
            setupPage(layout);
            mPageContentWidth = layout.getContentWidth();

            mCellCountX = determineCellCountX(width, layout);
            mCellCountY = determineCellCountY(height, layout);
            mLastMeasureWidth = width;
            mLastMeasureHeight = height;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mFirstLayout) {
            invalidatePageData();

            // invalidatePageData() is what causes the child pages to be created. We need the
            // children to be measured before layout, so force a new measure here.
            measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    private int determineCellCountX(int availableWidth, PagedViewCellLayout layout) {
        int cellCountX = 0;
        final int cellWidth = layout.getCellWidth();

        // Subtract padding for current page and adjacent pages
        availableWidth -= mPageLayoutPaddingLeft * 2 + mPageLayoutPaddingRight * 2;

        availableWidth -= cellWidth; // Assume at least one column
        cellCountX = 1 + availableWidth / (cellWidth + mPageLayoutWidthGap);
        availableWidth = availableWidth % (cellWidth + mPageLayoutWidthGap);

        // Ensures that we show at least 30% of the holo icons on each side
        final int minLeftoverWidth = (int) (cellWidth * 0.6f);

        // Reserve room for the holo outlines
        if (cellCountX <= 4) {
            // When we're really tight on space, just pack the icons a bit closer together
            final int missingWidth = minLeftoverWidth - availableWidth;
            if (missingWidth > 0) {
                mPageLayoutWidthGap -= Math.ceil(missingWidth * 1.0f / (cellCountX - 1));
            }
        } else {
            if (cellCountX >= 8) {
                // Carve out a few extra columns for very large widths
                cellCountX = (int) (cellCountX * 0.9f);
            } else if (availableWidth < minLeftoverWidth) {
                cellCountX -= 1;
            }
        }
        return cellCountX;
    }

    private int determineCellCountY(int availableHeight, PagedViewCellLayout layout) {
        final int cellHeight = layout.getCellHeight();
        final int screenHeight = mLauncher.getResources().getDisplayMetrics().heightPixels;

        availableHeight -= mPageLayoutPaddingTop + mPageLayoutPaddingBottom;
        availableHeight -= cellHeight; // Assume at least one row
        availableHeight -= screenHeight * 0.16f;
        if (availableHeight > 0) {
            return Math.min(mMaxCellCountY,
                    1 + availableHeight / (cellHeight + mPageLayoutHeightGap));
        }
        return 0;
    }

    int getCellCountX() {
        return mCellCountX;
    }

    int getCellCountY() {
        return mCellCountY;
    }

    void allowHardwareLayerCreation() {
        // This is called after the first time we launch into All Apps. Before that point,
        // there's no need for hardware layers here since there's a hardware layer set on the
        // parent, AllAppsTabbed, during the AllApps transition -- creating hardware layers here
        // before the animation is done slows down the animation
        if (mAllowHardwareLayerCreation) {
            return;
        }
        mAllowHardwareLayerCreation = true;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            PagedViewCellLayout page = (PagedViewCellLayout) getChildAt(i);
            page.allowHardwareLayerCreation();
        }
    }

    @Override
    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
        mLauncher.setAllAppsPagedView(this);
    }

    @Override
    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void setAppFilter(int filterType) {
        mAppFilter = filterType;
        if (mApps != null) {
            mFilteredApps = rebuildFilteredApps(mApps);
            setCurrentPage(0);
            invalidatePageData();
        }
    }

    @Override
    public void zoom(float zoom, boolean animate) {
        mZoom = zoom;
        cancelLongPress();

        if (isVisible()) {
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(),
                        R.anim.all_apps_2d_fade_in));
            } else {
                onAnimationEnd();
            }
        } else {
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(),
                        R.anim.all_apps_2d_fade_out));
            } else {
                onAnimationEnd();
            }
        }
    }

    protected void onAnimationEnd() {
        if (!isVisible()) {
            mZoom = 0.0f;

            endChoiceMode();
        } else {
            mZoom = 1.0f;
        }

        if (mLauncher != null)
            mLauncher.zoomed(mZoom);
    }

    private int getChildIndexForGrandChild(View v) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            final Page layout = (Page) getChildAt(i);
            if (layout.indexOfChildOnPage(v) > -1) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onClick(View v) {
        // if we are already in a choice mode, then just change the selection
        if (v instanceof Checkable) {
            if (!isChoiceMode(CHOICE_MODE_NONE)) {
                Checkable c = (Checkable) v;
                if (isChoiceMode(CHOICE_MODE_SINGLE)) {
                    // Uncheck all the other grandchildren, and toggle the clicked one
                    boolean wasChecked = c.isChecked();
                    resetCheckedGrandchildren();
                    c.setChecked(!wasChecked);
                } else {
                    c.toggle();
                }
                if (getCheckedGrandchildren().size() == 0) {
                    endChoiceMode();
                }

                return;
            }
        }

        // otherwise continue and launch the application
        int childIndex = getChildIndexForGrandChild(v);
        if (childIndex == getCurrentPage()) {
            final ApplicationInfo app = (ApplicationInfo) v.getTag();

            // animate some feedback to the click
            animateClickFeedback(v, new Runnable() {
                @Override
                public void run() {
                    mLauncher.startActivitySafely(app.intent, app);
                }
            });

            endChoiceMode();
        }
    }

    private void setupDragMode(ApplicationInfo info) {
        mLauncher.getWorkspace().shrink(Workspace.ShrinkState.BOTTOM_VISIBLE);

        // Only show the uninstall button if the app is uninstallable.
        if ((info.flags & ApplicationInfo.DOWNLOADED_FLAG) != 0) {
            DeleteZone allAppsDeleteZone = (DeleteZone)
                    mLauncher.findViewById(R.id.all_apps_delete_zone);
            allAppsDeleteZone.setDragAndDropEnabled(true);

            if ((info.flags & ApplicationInfo.UPDATED_SYSTEM_APP_FLAG) != 0) {
                allAppsDeleteZone.setText(R.string.delete_zone_label_all_apps_system_app);
            } else {
                allAppsDeleteZone.setText(R.string.delete_zone_label_all_apps);
            }
        }

        ApplicationInfoDropTarget allAppsInfoButton =
                (ApplicationInfoDropTarget) mLauncher.findViewById(R.id.all_apps_info_target);
        allAppsInfoButton.setDragAndDropEnabled(true);
    }

    private void tearDownDragMode() {
        post(new Runnable() {
            // Once the drag operation has fully completed, hence the post, we want to disable the
            // deleteZone and the appInfoButton in all apps, and re-enable the instance which
            // live in the workspace
            public void run() {
                DeleteZone allAppsDeleteZone =
                        (DeleteZone) mLauncher.findViewById(R.id.all_apps_delete_zone);
                // if onDestroy was called on Launcher, we might have already deleted the
                // all apps delete zone / info button, so check if they are null
                if (allAppsDeleteZone != null) allAppsDeleteZone.setDragAndDropEnabled(false);

                ApplicationInfoDropTarget allAppsInfoButton =
                    (ApplicationInfoDropTarget) mLauncher.findViewById(R.id.all_apps_info_target);
                if (allAppsInfoButton != null) allAppsInfoButton.setDragAndDropEnabled(false);
            }
        });
        resetCheckedGrandchildren();
        mDragController.removeDropTarget(this);
    }

    @Override
    protected boolean beginDragging(View v) {
        if (!v.isInTouchMode()) return false;
        if (!super.beginDragging(v)) return false;

        ApplicationInfo app = (ApplicationInfo) v.getTag();
        app = new ApplicationInfo(app);

        // Start drag mode after the item is selected
        setupDragMode(app);

        // get icon (top compound drawable, index is 1)
        final TextView tv = (TextView) v;
        final Drawable icon = tv.getCompoundDrawables()[1];
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate((v.getWidth() - icon.getIntrinsicWidth()) / 2, v.getPaddingTop());
        icon.draw(c);

        // We toggle the checked state _after_ we create the view for the drag in case toggling the
        // checked state changes the view's look
        if (v instanceof Checkable) {
            // In preparation for drag, we always reset the checked grand children regardless of
            // what choice mode we are in
            resetCheckedGrandchildren();

            // Toggle the selection on the dragged app
            Checkable checkable = (Checkable) v;

            // Note: we toggle the checkable state to actually cause an alpha fade for the duration
            // of the drag of the item.  (The fade-in will occur when all checked states are
            // disabled when dragging ends)
            checkable.toggle();
        }

        // Start the drag
        mLauncher.lockScreenOrientation();
        mLauncher.getWorkspace().onDragStartedWithItemSpans(1, 1, b);
        mDragController.startDrag(v, b, this, app, DragController.DRAG_ACTION_COPY, null);
        b.recycle();
        return true;
    }

    @Override
    public void onDragViewVisible() {
    }

    @Override
    public void onDropCompleted(View target, Object dragInfo, boolean success) {
        // close the choice action mode if we have a proper drop
        if (target != this) {
            endChoiceMode();
        }
        tearDownDragMode();
        mLauncher.getWorkspace().onDragStopped(success);
        mLauncher.unlockScreenOrientation();
    }

    int getPageContentWidth() {
        return mPageContentWidth;
    }

    @Override
    public boolean isVisible() {
        return mZoom > 0.001f;
    }

    @Override
    public boolean isAnimating() {
        return (getAnimation() != null);
    }

    private ArrayList<ApplicationInfo> rebuildFilteredApps(ArrayList<ApplicationInfo> apps) {
        ArrayList<ApplicationInfo> filteredApps = new ArrayList<ApplicationInfo>();
        if (mAppFilter == ALL_APPS_FLAG) {
            return apps;
        } else {
            final int length = apps.size();
            for (int i = 0; i < length; ++i) {
                ApplicationInfo info = apps.get(i);
                if ((info.flags & mAppFilter) > 0) {
                    filteredApps.add(info);
                }
            }
            Collections.sort(filteredApps, LauncherModel.APP_INSTALL_TIME_COMPARATOR);
        }
        return filteredApps;
    }

    @Override
    public void setApps(ArrayList<ApplicationInfo> list) {
        mApps = list;
        Collections.sort(mApps, LauncherModel.APP_NAME_COMPARATOR);
        mFilteredApps = rebuildFilteredApps(mApps);
        mPageViewIconCache.retainAllApps(list);
        invalidatePageData();
    }

    private void addAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // we add it in place, in alphabetical order
        final int count = list.size();
        for (int i = 0; i < count; ++i) {
            final ApplicationInfo info = list.get(i);
            final int index = Collections.binarySearch(mApps, info, LauncherModel.APP_NAME_COMPARATOR);
            if (index < 0) {
                mApps.add(-(index + 1), info);
            } else {
                mApps.add(index, info);
            }
        }
        mFilteredApps = rebuildFilteredApps(mApps);
    }
    @Override
    public void addApps(ArrayList<ApplicationInfo> list) {
        addAppsWithoutInvalidate(list);
        invalidatePageData();
    }

    private void removeAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // End the choice mode if any of the items in the list that are being removed are
        // currently selected
        ArrayList<Checkable> checkedList = getCheckedGrandchildren();
        HashSet<ApplicationInfo> checkedAppInfos = new HashSet<ApplicationInfo>();
        for (Checkable checked : checkedList) {
            PagedViewIcon icon = (PagedViewIcon) checked;
            checkedAppInfos.add((ApplicationInfo) icon.getTag());
        }
        for (ApplicationInfo info : list) {
            if (checkedAppInfos.contains(info)) {
                endChoiceMode();
                break;
            }
        }

        // Loop through all the apps and remove apps that have the same component
        final int length = list.size();
        for (int i = 0; i < length; ++i) {
            final ApplicationInfo info = list.get(i);
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex > -1) {
                mApps.remove(removeIndex);
                mPageViewIconCache.removeOutline(new PagedViewIconCache.Key(info));
            }
        }
        mFilteredApps = rebuildFilteredApps(mApps);
    }

    @Override
    public void removeApps(ArrayList<ApplicationInfo> list) {
        removeAppsWithoutInvalidate(list);
        invalidatePageData();
    }

    @Override
    public void updateApps(ArrayList<ApplicationInfo> list) {
        removeAppsWithoutInvalidate(list);
        addAppsWithoutInvalidate(list);
        invalidatePageData();
    }

    private int findAppByComponent(ArrayList<ApplicationInfo> list, ApplicationInfo item) {
        if (item != null && item.intent != null) {
            ComponentName removeComponent = item.intent.getComponent();
            final int length = list.size();
            for (int i = 0; i < length; ++i) {
                ApplicationInfo info = list.get(i);
                if (info.intent.getComponent().equals(removeComponent)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void dumpState() {
        ApplicationInfo.dumpApplicationInfoList(TAG, "mApps", mApps);
    }

    @Override
    public void surrender() {
        // do nothing?
    }

    private void setupPage(PagedViewCellLayout layout) {
        layout.setCellCount(mCellCountX, mCellCountY);
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop, mPageLayoutPaddingRight,
                mPageLayoutPaddingBottom);
        layout.setGap(mPageLayoutWidthGap, mPageLayoutHeightGap);
    }

    @Override
    public void syncPages() {
        if (mCellCountX <= 0 || mCellCountY <= 0) {
            // We don't know our size yet, which means we haven't calculated cell count x/y;
            // onMeasure will call us once we figure out our size
            return;
        }
        // ensure that we have the right number of pages (min of 1, since we have placeholders)
        int numPages = Math.max(1,
                (int) Math.ceil((float) mFilteredApps.size() / (mCellCountX * mCellCountY)));
        int curNumPages = getChildCount();
        // remove any extra pages after the "last" page
        int extraPageDiff = curNumPages - numPages;
        for (int i = 0; i < extraPageDiff; ++i) {
            removeViewAt(numPages);
        }
        // add any necessary pages
        for (int i = curNumPages; i < numPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
            if (mAllowHardwareLayerCreation) {
                layout.allowHardwareLayerCreation();
            }
            setupPage(layout);
            addView(layout);
        }

        // bound the current page
        setCurrentPage(Math.max(0, Math.min(numPages - 1, getCurrentPage())));
    }

    @Override
    public void syncPageItems(int page) {
        // Ensure that we have the right number of items on the pages
        final int cellsPerPage = mCellCountX * mCellCountY;
        final int startIndex = page * cellsPerPage;
        final int endIndex = Math.min(startIndex + cellsPerPage, mFilteredApps.size());
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);

        if (!mFilteredApps.isEmpty()) {
            int curNumPageItems = layout.getPageChildCount();
            int numPageItems = endIndex - startIndex;

            // If we were previously an empty page, then restart anew
            boolean wasEmptyPage = false;
            if (curNumPageItems == 1) {
                View icon = layout.getChildOnPageAt(0);
                if (icon.getTag() == null) {
                    wasEmptyPage = true;
                }
            }

            if (wasEmptyPage) {
                // Remove all the previous items
                curNumPageItems = 0;
                layout.removeAllViewsOnPage();
            } else {
                // Remove any extra items
                int extraPageItemsDiff = curNumPageItems - numPageItems;
                for (int i = 0; i < extraPageItemsDiff; ++i) {
                    layout.removeViewOnPageAt(numPageItems);
                }
            }

            // Add any necessary items
            for (int i = curNumPageItems; i < numPageItems; ++i) {
                TextView text = (TextView) mInflater.inflate(
                        R.layout.all_apps_paged_view_application, layout, false);
                text.setOnClickListener(this);
                text.setOnLongClickListener(this);
                text.setOnTouchListener(this);

                layout.addViewToCellLayout(text, -1, i,
                    new PagedViewCellLayout.LayoutParams(0, 0, 1, 1));
            }

            // Actually reapply to the existing text views
            final int numPages = getPageCount();
            for (int i = startIndex; i < endIndex; ++i) {
                final int index = i - startIndex;
                final ApplicationInfo info = mFilteredApps.get(i);
                PagedViewIcon icon = (PagedViewIcon) layout.getChildOnPageAt(index);
                icon.applyFromApplicationInfo(info, mPageViewIconCache, true, (numPages > 1));

                PagedViewCellLayout.LayoutParams params =
                    (PagedViewCellLayout.LayoutParams) icon.getLayoutParams();
                params.cellX = index % mCellCountX;
                params.cellY = index / mCellCountX;
            }

            // Default to left-aligned icons
            layout.enableCenteredContent(false);
        } else {
            // There are no items, so show the user a small message
            TextView icon = (TextView) mInflater.inflate(
                    R.layout.all_apps_no_items_placeholder, layout, false);
            switch (mAppFilter) {
            case ApplicationInfo.DOWNLOADED_FLAG:
                icon.setText(mContext.getString(R.string.all_apps_no_downloads));
                break;
            default: break;
            }

            // Center-align the message
            layout.enableCenteredContent(true);
            layout.removeAllViewsOnPage();
            layout.addViewToCellLayout(icon, -1, 0,
                    new PagedViewCellLayout.LayoutParams(0, 0, 4, 1));
        }
        layout.createHardwareLayers();
    }

    /*
     * We don't actually use AllAppsPagedView as a drop target... it's only used to intercept a drop
     * to the workspace.
     */
    @Override
    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return false;
    }
    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        return null;
    }
    @Override
    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {}
    @Override
    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {}
    @Override
    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {}
    @Override
    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {}

    public boolean isDropEnabled() {
        return true;
    }
}
