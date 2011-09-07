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

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.launcher.R;
import com.android.launcher2.DropTarget.DragObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A simple callback interface which also provides the results of the task.
 */
interface AsyncTaskCallback {
    void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data);
}

/**
 * The data needed to perform either of the custom AsyncTasks.
 */
class AsyncTaskPageData {
    enum Type {
        LoadWidgetPreviewData,
        LoadHolographicIconsData
    }

    AsyncTaskPageData(int p, ArrayList<Object> l, ArrayList<Bitmap> si, AsyncTaskCallback bgR,
            AsyncTaskCallback postR) {
        page = p;
        items = l;
        sourceImages = si;
        generatedImages = new ArrayList<Bitmap>();
        cellWidth = cellHeight = -1;
        doInBackgroundCallback = bgR;
        postExecuteCallback = postR;
    }
    AsyncTaskPageData(int p, ArrayList<Object> l, int cw, int ch, int ccx, AsyncTaskCallback bgR,
            AsyncTaskCallback postR) {
        page = p;
        items = l;
        generatedImages = new ArrayList<Bitmap>();
        cellWidth = cw;
        cellHeight = ch;
        cellCountX = ccx;
        doInBackgroundCallback = bgR;
        postExecuteCallback = postR;
    }
    int page;
    ArrayList<Object> items;
    ArrayList<Bitmap> sourceImages;
    ArrayList<Bitmap> generatedImages;
    int cellWidth;
    int cellHeight;
    int cellCountX;
    AsyncTaskCallback doInBackgroundCallback;
    AsyncTaskCallback postExecuteCallback;
}

/**
 * A generic template for an async task used in AppsCustomize.
 */
class AppsCustomizeAsyncTask extends AsyncTask<AsyncTaskPageData, Void, AsyncTaskPageData> {
    AppsCustomizeAsyncTask(int p, AppsCustomizePagedView.ContentType t, AsyncTaskPageData.Type ty) {
        page = p;
        pageContentType = t;
        threadPriority = Process.THREAD_PRIORITY_DEFAULT;
        dataType = ty;
    }
    @Override
    protected AsyncTaskPageData doInBackground(AsyncTaskPageData... params) {
        if (params.length != 1) return null;
        // Load each of the widget previews in the background
        params[0].doInBackgroundCallback.run(this, params[0]);
        return params[0];
    }
    @Override
    protected void onPostExecute(AsyncTaskPageData result) {
        // All the widget previews are loaded, so we can just callback to inflate the page
        result.postExecuteCallback.run(this, result);
    }

    void setThreadPriority(int p) {
        threadPriority = p;
    }
    void syncThreadPriority() {
        Process.setThreadPriority(threadPriority);
    }

    // The page that this async task is associated with
    AsyncTaskPageData.Type dataType;
    int page;
    AppsCustomizePagedView.ContentType pageContentType;
    int threadPriority;
}

/**
 * The Apps/Customize page that displays all the applications, widgets, and shortcuts.
 */
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

    // Save and Restore
    private int mSaveInstanceStateItemIndex = -1;
    private int mRestorePage = -1;

    // Content
    private ContentType mContentType;
    private ArrayList<ApplicationInfo> mApps;
    private ArrayList<Object> mWidgets;

    // Caching
    private Canvas mCanvas;
    private Drawable mDefaultWidgetBackground;
    private IconCache mIconCache;
    private int mDragViewMultiplyColor;

    // Dimens
    private int mContentWidth;
    private int mAppIconSize;
    private int mMaxWidgetSpan, mMinWidgetSpan;
    private int mWidgetCountX, mWidgetCountY;
    private int mWidgetWidthGap, mWidgetHeightGap;
    private final int mWidgetPreviewIconPaddedDimension;
    private final float sWidgetPreviewIconPaddingPercentage = 0.25f;
    private PagedViewCellLayout mWidgetSpacingLayout;

    // Previews & outlines
    ArrayList<AppsCustomizeAsyncTask> mRunningTasks;
    private HolographicOutlineHelper mHolographicOutlineHelper;
    private static final int sPageSleepDelay = 200;

    public AppsCustomizePagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLayoutInflater = LayoutInflater.from(context);
        mPackageManager = context.getPackageManager();
        mContentType = ContentType.Applications;
        mApps = new ArrayList<ApplicationInfo>();
        mWidgets = new ArrayList<Object>();
        mIconCache = ((LauncherApplication) context.getApplicationContext()).getIconCache();
        mHolographicOutlineHelper = new HolographicOutlineHelper();
        mCanvas = new Canvas();
        mRunningTasks = new ArrayList<AppsCustomizeAsyncTask>();

        // Save the default widget preview background
        Resources resources = context.getResources();
        mDefaultWidgetBackground = resources.getDrawable(R.drawable.default_widget_preview_holo);
        mAppIconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size);
        mDragViewMultiplyColor = resources.getColor(R.color.drag_view_multiply_color);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedView, 0, 0);
        // TODO-APPS_CUSTOMIZE: remove these unnecessary attrs after
        mCellCountX = a.getInt(R.styleable.PagedView_cellCountX, 6);
        mCellCountY = a.getInt(R.styleable.PagedView_cellCountY, 4);
        a.recycle();
        a = context.obtainStyledAttributes(attrs, R.styleable.AppsCustomizePagedView, 0, 0);
        mWidgetWidthGap =
            a.getDimensionPixelSize(R.styleable.AppsCustomizePagedView_widgetCellWidthGap, 0);
        mWidgetHeightGap =
            a.getDimensionPixelSize(R.styleable.AppsCustomizePagedView_widgetCellHeightGap, 0);
        mWidgetCountX = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountX, 2);
        mWidgetCountY = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountY, 2);
        a.recycle();
        mWidgetSpacingLayout = new PagedViewCellLayout(getContext());

        // The max widget span is the length N, such that NxN is the largest bounds that the widget
        // preview can be before applying the widget scaling
        mMinWidgetSpan = 1;
        mMaxWidgetSpan = 3;

        // The padding on the non-matched dimension for the default widget preview icons
        // (top + bottom)
        mWidgetPreviewIconPaddedDimension =
            (int) (mAppIconSize * (1 + (2 * sWidgetPreviewIconPaddingPercentage)));
        mFadeInAdjacentScreens = LauncherApplication.isScreenLarge();
    }

    @Override
    protected void init() {
        super.init();
        mCenterPagesVertically = false;

        Context context = getContext();
        Resources r = context.getResources();
        setDragSlopeThreshold(r.getInteger(R.integer.config_appsCustomizeDragSlopeThreshold)/100f);
    }

    @Override
    protected void onUnhandledTap(MotionEvent ev) {
        if (LauncherApplication.isScreenLarge()) {
            // Dismiss AppsCustomize if we tap
            mLauncher.showWorkspace(true);
        }
    }

    /** Returns the item index of the center item on this page so that we can restore to this
     *  item index when we rotate. */
    private int getMiddleComponentIndexOnCurrentPage() {
        int i = -1;
        if (getPageCount() > 0) {
            int currentPage = getCurrentPage();
            switch (mContentType) {
            case Applications: {
                PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(currentPage);
                PagedViewCellLayoutChildren childrenLayout = layout.getChildrenLayout();
                int numItemsPerPage = mCellCountX * mCellCountY;
                int childCount = childrenLayout.getChildCount();
                if (childCount > 0) {
                    i = (currentPage * numItemsPerPage) + (childCount / 2);
                }}
                break;
            case Widgets: {
                PagedViewGridLayout layout = (PagedViewGridLayout) getChildAt(currentPage);
                int numItemsPerPage = mWidgetCountX * mWidgetCountY;
                int childCount = layout.getChildCount();
                if (childCount > 0) {
                    i = (currentPage * numItemsPerPage) + (childCount / 2);
                }}
                break;
            }
        }
        return i;
    }

    /** Get the index of the item to restore to if we need to restore the current page. */
    int getSaveInstanceStateIndex() {
        if (mSaveInstanceStateItemIndex == -1) {
            mSaveInstanceStateItemIndex = getMiddleComponentIndexOnCurrentPage();
        }
        return mSaveInstanceStateItemIndex;
    }

    /** Returns the page in the current orientation which is expected to contain the specified
     *  item index. */
    int getPageForComponent(int index) {
        switch (mContentType) {
        case Applications: {
            int numItemsPerPage = mCellCountX * mCellCountY;
            return (index / numItemsPerPage);
        }
        case Widgets: {
            int numItemsPerPage = mWidgetCountX * mWidgetCountY;
            return (index / numItemsPerPage);
        }}
        return -1;
    }

    /**
     * This differs from isDataReady as this is the test done if isDataReady is not set.
     */
    private boolean testDataReady() {
        // We only do this test once, and we default to the Applications page, so we only really
        // have to wait for there to be apps.
        if (mContentType == AppsCustomizePagedView.ContentType.Widgets) {
            return !mApps.isEmpty() && !mWidgets.isEmpty();
        } else {
            return !mApps.isEmpty();
        }
    }

    /** Restores the page for an item at the specified index */
    void restorePageForIndex(int index) {
        if (index < 0) return;

        int page = getPageForComponent(index);
        if (page > -1) {
            if (getChildCount() > 0) {
                invalidatePageData(page);
            } else {
                mRestorePage = page;
            }
        }
    }

    protected void onDataReady(int width, int height) {
        // Note that we transpose the counts in portrait so that we get a similar layout
        boolean isLandscape = getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
        int maxCellCountX = Integer.MAX_VALUE;
        int maxCellCountY = Integer.MAX_VALUE;
        if (LauncherApplication.isScreenLarge()) {
            maxCellCountX = (isLandscape ? LauncherModel.getCellCountX() :
                LauncherModel.getCellCountY());
            maxCellCountY = (isLandscape ? LauncherModel.getCellCountY() :
                LauncherModel.getCellCountX());
        }

        // Now that the data is ready, we can calculate the content width, the number of cells to
        // use for each page
        mWidgetSpacingLayout.setGap(mPageLayoutWidthGap, mPageLayoutHeightGap);
        mWidgetSpacingLayout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);
        mWidgetSpacingLayout.calculateCellCount(width, height, maxCellCountX, maxCellCountY);
        mCellCountX = mWidgetSpacingLayout.getCellCountX();
        mCellCountY = mWidgetSpacingLayout.getCellCountY();

        // Force a measure to update recalculate the gaps
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        mWidgetSpacingLayout.measure(widthSpec, heightSpec);
        mContentWidth = mWidgetSpacingLayout.getContentWidth();
        invalidatePageData(Math.max(0, mRestorePage));
        mRestorePage = -1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (!isDataReady()) {
            if (testDataReady()) {
                setDataIsReady();
                setMeasuredDimension(width, height);
                onDataReady(width, height);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Removes and returns the ResolveInfo with the specified ComponentName */
    private ResolveInfo removeResolveInfoWithComponentName(List<ResolveInfo> list,
            ComponentName cn) {
        Iterator<ResolveInfo> iter = list.iterator();
        while (iter.hasNext()) {
            ResolveInfo rinfo = iter.next();
            ActivityInfo info = rinfo.activityInfo;
            ComponentName c = new ComponentName(info.packageName, info.name);
            if (c.equals(cn)) {
                iter.remove();
                return rinfo;
            }
        }
        return null;
    }

    public void onPackagesUpdated() {
        // TODO: this isn't ideal, but we actually need to delay here. This call is triggered
        // by a broadcast receiver, and in order for it to work correctly, we need to know that
        // the AppWidgetService has already received and processed the same broadcast. Since there
        // is no guarantee about ordering of broadcast receipt, we just delay here. Ideally,
        // we should have a more precise way of ensuring the AppWidgetService is up to date.
        postDelayed(new Runnable() {
           public void run() {
               updatePackages();
           }
        }, 500);
    }

    public void updatePackages() {
        // Get the list of widgets and shortcuts
        boolean wasEmpty = mWidgets.isEmpty();
        mWidgets.clear();
        List<AppWidgetProviderInfo> widgets =
            AppWidgetManager.getInstance(mLauncher).getInstalledProviders();
        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        List<ResolveInfo> shortcuts = mPackageManager.queryIntentActivities(shortcutsIntent, 0);
        mWidgets.addAll(widgets);
        mWidgets.addAll(shortcuts);
        Collections.sort(mWidgets,
                new LauncherModel.WidgetAndShortcutNameComparator(mPackageManager));

        if (wasEmpty) {
            // The next layout pass will trigger data-ready if both widgets and apps are set, so request
            // a layout to do this test and invalidate the page data when ready.
            if (testDataReady()) requestLayout();
        } else {
            invalidatePageData();
        }
    }

    @Override
    public void onClick(View v) {
        // When we have exited all apps or are in transition, disregard clicks
        if (!mLauncher.isAllAppsCustomizeOpen() ||
                mLauncher.getWorkspace().isSwitchingState()) return;

        if (v instanceof PagedViewIcon) {
            // Animate some feedback to the click
            final ApplicationInfo appInfo = (ApplicationInfo) v.getTag();
            animateClickFeedback(v, new Runnable() {
                @Override
                public void run() {
                    mLauncher.startActivitySafely(appInfo.intent, appInfo);
                }
            });
        } else if (v instanceof PagedViewWidget) {
            // Let the user know that they have to long press to add a widget
            Toast.makeText(getContext(), R.string.long_press_widget_to_add,
                    Toast.LENGTH_SHORT).show();

            // Create a little animation to show that the widget can move
            float offsetY = getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);
            final ImageView p = (ImageView) v.findViewById(R.id.widget_preview);
            AnimatorSet bounce = new AnimatorSet();
            ValueAnimator tyuAnim = ObjectAnimator.ofFloat(p, "translationY", offsetY);
            tyuAnim.setDuration(125);
            ValueAnimator tydAnim = ObjectAnimator.ofFloat(p, "translationY", 0f);
            tydAnim.setDuration(100);
            bounce.play(tyuAnim).before(tydAnim);
            bounce.setInterpolator(new AccelerateInterpolator());
            bounce.start();
        }
    }

    /*
     * PagedViewWithDraggableItems implementation
     */
    @Override
    protected void determineDraggingStart(android.view.MotionEvent ev) {
        // Disable dragging by pulling an app down for now.
    }

    private void beginDraggingApplication(View v) {
        mLauncher.getWorkspace().onDragStartedWithItem(v);
        mLauncher.getWorkspace().beginDragShared(v, this);
    }

    private void beginDraggingWidget(View v) {
        // Get the widget preview as the drag representation
        ImageView image = (ImageView) v.findViewById(R.id.widget_preview);
        PendingAddItemInfo createItemInfo = (PendingAddItemInfo) v.getTag();

        // Compose the drag image
        Bitmap b;
        Drawable preview = image.getDrawable();
        int w = preview.getIntrinsicWidth();
        int h = preview.getIntrinsicHeight();
        if (createItemInfo instanceof PendingAddWidgetInfo) {
            PendingAddWidgetInfo createWidgetInfo = (PendingAddWidgetInfo) createItemInfo;
            int[] spanXY = mLauncher.getSpanForWidget(createWidgetInfo, null);
            createItemInfo.spanX = spanXY[0];
            createItemInfo.spanY = spanXY[1];

            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            renderDrawableToBitmap(preview, b, 0, 0, w, h, 1, 1, mDragViewMultiplyColor);
        } else {
            // Workaround for the fact that we don't keep the original ResolveInfo associated with
            // the shortcut around.  To get the icon, we just render the preview image (which has
            // the shortcut icon) to a new drag bitmap that clips the non-icon space.
            b = Bitmap.createBitmap(mWidgetPreviewIconPaddedDimension,
                    mWidgetPreviewIconPaddedDimension, Bitmap.Config.ARGB_8888);
            mCanvas.setBitmap(b);
            mCanvas.save();
            preview.draw(mCanvas);
            mCanvas.restore();
            mCanvas.drawColor(mDragViewMultiplyColor, PorterDuff.Mode.MULTIPLY);
            mCanvas.setBitmap(null);
            createItemInfo.spanX = createItemInfo.spanY = 1;
        }

        // Start the drag
        mLauncher.lockScreenOrientation();
        mLauncher.getWorkspace().onDragStartedWithItemSpans(createItemInfo.spanX,
                createItemInfo.spanY, b);
        mDragController.startDrag(image, b, this, createItemInfo,
                DragController.DRAG_ACTION_COPY, null);
        b.recycle();
    }
    @Override
    protected boolean beginDragging(View v) {
        if (!super.beginDragging(v)) return false;

        // Go into spring loaded mode (must happen before we startDrag())
        int currentPageIndex = mLauncher.getWorkspace().getCurrentPage();
        CellLayout currentPage = (CellLayout) mLauncher.getWorkspace().getChildAt(currentPageIndex);
        mLauncher.enterSpringLoadedDragMode();

        if (v instanceof PagedViewIcon) {
            beginDraggingApplication(v);
        } else if (v instanceof PagedViewWidget) {
            beginDraggingWidget(v);
        }
        return true;
    }
    private void endDragging(View target, boolean success) {
        mLauncher.getWorkspace().onDragStopped(success);
        if (!success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragMode();
        }
        mLauncher.unlockScreenOrientation();

    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {
        endDragging(target, success);

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = (ItemInfo) d.dragInfo;
                if (layout != null) {
                    layout.calculateSpans(itemInfo);
                    showOutOfSpaceMessage =
                            !layout.findCellForSpan(null, itemInfo.spanX, itemInfo.spanY);
                }
            }
            // TODO-APPS_CUSTOMIZE: We need to handle this for folders as well later.
            if (showOutOfSpaceMessage) {
                mLauncher.showOutOfSpaceMessage();
            }
        }
    }

    public void setContentType(ContentType type) {
        mContentType = type;
        invalidatePageData(0, (type != ContentType.Applications));
    }

    public boolean isContentType(ContentType type) {
        return (mContentType == type);
    }

    public void setCurrentPageToWidgets() {
        invalidatePageData(0);
    }

    /*
     * Apps PagedView implementation
     */
    private void setVisibilityOnChildren(ViewGroup layout, int visibility) {
        int childCount = layout.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            layout.getChildAt(i).setVisibility(visibility);
        }
    }
    private void setupPage(PagedViewCellLayout layout) {
        layout.setCellCount(mCellCountX, mCellCountY);
        layout.setGap(mPageLayoutWidthGap, mPageLayoutHeightGap);
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.  That said, we already know the
        // expected page width, so we can actually optimize by hiding all the TextView-based
        // children that are expensive to measure, and let that happen naturally later.
        setVisibilityOnChildren(layout, View.GONE);
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        layout.setMinimumWidth(getPageContentWidth());
        layout.measure(widthSpec, heightSpec);
        setVisibilityOnChildren(layout, View.VISIBLE);
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
    public void syncAppsPageItems(int page, boolean immediate) {
        // ensure that we have the right number of items on the pages
        int numPages = getPageCount();
        int numCells = mCellCountX * mCellCountY;
        int startIndex = page * numCells;
        int endIndex = Math.min(startIndex + numCells, mApps.size());
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);

        layout.removeAllViewsOnPage();
        ArrayList<Object> items = new ArrayList<Object>();
        ArrayList<Bitmap> images = new ArrayList<Bitmap>();
        for (int i = startIndex; i < endIndex; ++i) {
            ApplicationInfo info = mApps.get(i);
            PagedViewIcon icon = (PagedViewIcon) mLayoutInflater.inflate(
                    R.layout.apps_customize_application, layout, false);
            icon.applyFromApplicationInfo(info, true, mHolographicOutlineHelper);
            icon.setOnClickListener(this);
            icon.setOnLongClickListener(this);
            icon.setOnTouchListener(this);

            int index = i - startIndex;
            int x = index % mCellCountX;
            int y = index / mCellCountX;
            layout.addViewToCellLayout(icon, -1, i, new PagedViewCellLayout.LayoutParams(x,y, 1,1));

            items.add(info);
            images.add(info.iconBitmap);
        }

        // Create the hardware layers
        layout.allowHardwareLayerCreation();
        layout.createHardwareLayers();

        if (mFadeInAdjacentScreens) {
            prepareGenerateHoloOutlinesTask(page, items, images);
        }
    }

    /**
     * Return the appropriate thread priority for loading for a given page (we give the current
     * page much higher priority)
     */
    private int getThreadPriorityForPage(int page) {
        // TODO-APPS_CUSTOMIZE: detect number of cores and set thread priorities accordingly below
        int pageDiff = Math.abs(page - mCurrentPage);
        if (pageDiff <= 0) {
            // return Process.THREAD_PRIORITY_DEFAULT;
            return Process.THREAD_PRIORITY_MORE_FAVORABLE;
        } else if (pageDiff <= 1) {
            // return Process.THREAD_PRIORITY_BACKGROUND;
            return Process.THREAD_PRIORITY_DEFAULT;
        } else {
            // return Process.THREAD_PRIORITY_LOWEST;
            return Process.THREAD_PRIORITY_DEFAULT;
        }
    }
    private int getSleepForPage(int page) {
        int pageDiff = Math.abs(page - mCurrentPage) - 1;
        return Math.max(0, pageDiff * sPageSleepDelay);
    }
    /**
     * Creates and executes a new AsyncTask to load a page of widget previews.
     */
    private void prepareLoadWidgetPreviewsTask(int page, ArrayList<Object> widgets,
            int cellWidth, int cellHeight, int cellCountX) {
        // Prune all tasks that are no longer needed
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int taskPage = task.page;
            if ((taskPage == page) ||
                    taskPage < getAssociatedLowerPageBound(mCurrentPage) ||
                    taskPage > getAssociatedUpperPageBound(mCurrentPage)) {
                task.cancel(false);
                iter.remove();
            } else {
                task.setThreadPriority(getThreadPriorityForPage(taskPage));
            }
        }

        // We introduce a slight delay to order the loading of side pages so that we don't thrash
        final int sleepMs = getSleepForPage(page);
        AsyncTaskPageData pageData = new AsyncTaskPageData(page, widgets, cellWidth, cellHeight,
            cellCountX, new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (Exception e) {}
                    loadWidgetPreviewsInBackground(task, data);
                }
            },
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    mRunningTasks.remove(task);
                    if (task.isCancelled()) return;
                    if (task.page > getPageCount()) return;
                    if (task.pageContentType != mContentType) return;
                    onSyncWidgetPageItems(data);
                }
        });

        // Ensure that the task is appropriately prioritized and runs in parallel
        AppsCustomizeAsyncTask t = new AppsCustomizeAsyncTask(page, mContentType,
                AsyncTaskPageData.Type.LoadWidgetPreviewData);
        t.setThreadPriority(getThreadPriorityForPage(page));
        t.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, pageData);
        mRunningTasks.add(t);
    }
    /**
     * Creates and executes a new AsyncTask to load the outlines for a page of content.
     */
    private void prepareGenerateHoloOutlinesTask(int page, ArrayList<Object> items,
            ArrayList<Bitmap> images) {
        // Prune old tasks for this page
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int taskPage = task.page;
            if ((taskPage == page) &&
                    (task.dataType == AsyncTaskPageData.Type.LoadHolographicIconsData)) {
                task.cancel(false);
                iter.remove();
            }
        }

        AsyncTaskPageData pageData = new AsyncTaskPageData(page, items, images,
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    // Ensure that this task starts running at the correct priority
                    task.syncThreadPriority();

                    ArrayList<Bitmap> images = data.generatedImages;
                    ArrayList<Bitmap> srcImages = data.sourceImages;
                    int count = srcImages.size();
                    Canvas c = new Canvas();
                    for (int i = 0; i < count && !task.isCancelled(); ++i) {
                        // Before work on each item, ensure that this task is running at the correct
                        // priority
                        task.syncThreadPriority();

                        Bitmap b = srcImages.get(i);
                        Bitmap outline = Bitmap.createBitmap(b.getWidth(), b.getHeight(),
                                Bitmap.Config.ARGB_8888);

                        c.setBitmap(outline);
                        c.save();
                        c.drawBitmap(b, 0, 0, null);
                        c.restore();
                        c.setBitmap(null);

                        images.add(outline);
                    }
                }
            },
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    mRunningTasks.remove(task);
                    if (task.isCancelled()) return;
                    if (task.page > getPageCount()) return;
                    if (task.pageContentType != mContentType) return;
                    onHolographicPageItemsLoaded(data);
                }
            });

        // Ensure that the outline task always runs in the background, serially
        AppsCustomizeAsyncTask t =
            new AppsCustomizeAsyncTask(page, mContentType,
                    AsyncTaskPageData.Type.LoadHolographicIconsData);
        t.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        t.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, pageData);
        mRunningTasks.add(t);
    }

    /*
     * Widgets PagedView implementation
     */
    private void setupPage(PagedViewGridLayout layout) {
        layout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        layout.setMinimumWidth(getPageContentWidth());
        layout.measure(widthSpec, heightSpec);
    }
    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h,
            float scaleX, float scaleY) {
        renderDrawableToBitmap(d, bitmap, x, y, w, h, scaleX, scaleY, 0xFFFFFFFF);
    }
    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h,
            float scaleX, float scaleY, int multiplyColor) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            c.scale(scaleX, scaleY);
            Rect oldBounds = d.copyBounds();
            d.setBounds(x, y, x + w, y + h);
            d.draw(c);
            d.setBounds(oldBounds); // Restore the bounds
            if (multiplyColor != 0xFFFFFFFF) {
                c.drawColor(mDragViewMultiplyColor, PorterDuff.Mode.MULTIPLY);
            }
            c.setBitmap(null);
        }
    }
    private Bitmap getShortcutPreview(ResolveInfo info, int cellWidth, int cellHeight) {
        // Render the icon
        Bitmap preview = Bitmap.createBitmap(cellWidth, mAppIconSize, Config.ARGB_8888);
        Drawable icon = mIconCache.getFullResIcon(info, mPackageManager);
        renderDrawableToBitmap(icon, preview, 0, 0, mAppIconSize, mAppIconSize, 1f, 1f);
        return preview;
    }
    private Bitmap getWidgetPreview(AppWidgetProviderInfo info,
            int cellHSpan, int cellVSpan, int cellWidth, int cellHeight) {

        // Calculate the size of the drawable
        cellHSpan = Math.max(mMinWidgetSpan, Math.min(mMaxWidgetSpan, cellHSpan));
        cellVSpan = Math.max(mMinWidgetSpan, Math.min(mMaxWidgetSpan, cellVSpan));
        int expectedWidth = mWidgetSpacingLayout.estimateCellWidth(cellHSpan);
        int expectedHeight = mWidgetSpacingLayout.estimateCellHeight(cellVSpan);

        // Scale down the bitmap to fit the space
        float widgetPreviewScale = (float) cellWidth / expectedWidth;
        expectedWidth = (int) (widgetPreviewScale * expectedWidth);
        expectedHeight = (int) (widgetPreviewScale * expectedHeight);

        // Load the preview image if possible
        String packageName = info.provider.getPackageName();
        Drawable drawable = null;
        Bitmap preview = null;
        if (info.previewImage != 0) {
            drawable = mPackageManager.getDrawable(packageName, info.previewImage, null);
            if (drawable == null) {
                Log.w(LOG_TAG, "Can't load icon drawable 0x" + Integer.toHexString(info.icon)
                        + " for provider: " + info.provider);
            } else {
                // Scale down the preview to the dimensions we want
                int imageWidth = drawable.getIntrinsicWidth();
                int imageHeight = drawable.getIntrinsicHeight();
                float aspect = (float) imageWidth / imageHeight;
                int newWidth = imageWidth;
                int newHeight = imageHeight;
                if (aspect > 1f) {
                    newWidth = expectedWidth;
                    newHeight = (int) (imageHeight * ((float) expectedWidth / imageWidth));
                } else {
                    newHeight = expectedHeight;
                    newWidth = (int) (imageWidth * ((float) expectedHeight / imageHeight));
                }

                preview = Bitmap.createBitmap(newWidth, newHeight, Config.ARGB_8888);
                renderDrawableToBitmap(drawable, preview, 0, 0, newWidth, newHeight, 1f, 1f);
            }
        }

        // Generate a preview image if we couldn't load one
        if (drawable == null) {
            Resources resources = mLauncher.getResources();
            int bitmapWidth;
            int bitmapHeight;

            // Specify the dimensions of the bitmap (since we are using a default preview bg with 
            // the full icon, we only imply the aspect ratio of the widget)
            if (cellHSpan == cellVSpan) {
                bitmapWidth = bitmapHeight = cellWidth;
                expectedWidth = expectedHeight = mWidgetPreviewIconPaddedDimension;
            } else if (cellHSpan >= cellVSpan) {
                bitmapWidth = expectedWidth = cellWidth;
                bitmapHeight = expectedHeight = mWidgetPreviewIconPaddedDimension;
            } else {
                // Note that in vertical widgets, we might not have enough space due to the text
                // label, so be conservative and use the width as a height bound
                bitmapWidth = expectedWidth = mWidgetPreviewIconPaddedDimension;
                bitmapHeight = expectedHeight = cellWidth;
            }

            preview = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Config.ARGB_8888);
            renderDrawableToBitmap(mDefaultWidgetBackground, preview, 0, 0, expectedWidth,
                    expectedHeight, 1f, 1f);

            // Draw the icon in the top left corner
            try {
                Drawable icon = null;
                if (info.icon > 0) icon = mPackageManager.getDrawable(packageName, info.icon, null);
                if (icon == null) icon = resources.getDrawable(R.drawable.ic_launcher_application);

                int offset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
                renderDrawableToBitmap(icon, preview, offset, offset,
                        mAppIconSize, mAppIconSize, 1f, 1f);
            } catch (Resources.NotFoundException e) {}
        }
        return preview;
    }
    public void syncWidgetPages() {
        // Ensure that we have the right number of pages
        Context context = getContext();
        int numPages = (int) Math.ceil(mWidgets.size() /
                (float) (mWidgetCountX * mWidgetCountY));
        for (int j = 0; j < numPages; ++j) {
            PagedViewGridLayout layout = new PagedViewGridLayout(context, mWidgetCountX,
                    mWidgetCountY);
            setupPage(layout);
            addView(layout);
        }
    }
    public void syncWidgetPageItems(int page, boolean immediate) {
        int numItemsPerPage = mWidgetCountX * mWidgetCountY;
        int contentWidth = mWidgetSpacingLayout.getContentWidth();
        int contentHeight = mWidgetSpacingLayout.getContentHeight();

        // Calculate the dimensions of each cell we are giving to each widget
        ArrayList<Object> items = new ArrayList<Object>();
        int cellWidth = ((contentWidth - mPageLayoutPaddingLeft - mPageLayoutPaddingRight
                - ((mWidgetCountX - 1) * mWidgetWidthGap)) / mWidgetCountX);
        int cellHeight = ((contentHeight - mPageLayoutPaddingTop - mPageLayoutPaddingBottom
                - ((mWidgetCountY - 1) * mWidgetHeightGap)) / mWidgetCountY);

        int offset = page * numItemsPerPage;
        for (int i = offset; i < Math.min(offset + numItemsPerPage, mWidgets.size()); ++i) {
            items.add(mWidgets.get(i));
        }

        if (immediate) {
            AsyncTaskPageData data = new AsyncTaskPageData(page, items, cellWidth, cellHeight,
                    mWidgetCountX, null, null);
            loadWidgetPreviewsInBackground(null, data);
            onSyncWidgetPageItems(data);
        } else {
            prepareLoadWidgetPreviewsTask(page, items, cellWidth, cellHeight, mWidgetCountX);
        }
    }
    private void loadWidgetPreviewsInBackground(AppsCustomizeAsyncTask task,
            AsyncTaskPageData data) {
        if (task != null) {
            // Ensure that this task starts running at the correct priority
            task.syncThreadPriority();
        }

        // Load each of the widget/shortcut previews
        ArrayList<Object> items = data.items;
        ArrayList<Bitmap> images = data.generatedImages;
        int count = items.size();
        int cellWidth = data.cellWidth;
        int cellHeight = data.cellHeight;
        for (int i = 0; i < count; ++i) {
            if (task != null) {
                // Ensure we haven't been cancelled yet
                if (task.isCancelled()) break;
                // Before work on each item, ensure that this task is running at the correct
                // priority
                task.syncThreadPriority();
            }

            Object rawInfo = items.get(i);
            if (rawInfo instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
                int[] cellSpans = mLauncher.getSpanForWidget(info, null);
                images.add(getWidgetPreview(info, cellSpans[0],cellSpans[1],
                        cellWidth, cellHeight));
            } else if (rawInfo instanceof ResolveInfo) {
                // Fill in the shortcuts information
                ResolveInfo info = (ResolveInfo) rawInfo;
                images.add(getShortcutPreview(info, cellWidth, cellHeight));
            }
        }
    }
    private void onSyncWidgetPageItems(AsyncTaskPageData data) {
        int page = data.page;
        PagedViewGridLayout layout = (PagedViewGridLayout) getChildAt(page);
        // Only set the column count once we have items
        layout.setColumnCount(layout.getCellCountX());

        ArrayList<Object> items = data.items;
        int count = items.size();
        int cellWidth = data.cellWidth;
        int cellHeight = data.cellHeight;
        int cellCountX = data.cellCountX;
        for (int i = 0; i < count; ++i) {
            Object rawInfo = items.get(i);
            PendingAddItemInfo createItemInfo = null;
            PagedViewWidget widget = (PagedViewWidget) mLayoutInflater.inflate(
                    R.layout.apps_customize_widget, layout, false);
            if (rawInfo instanceof AppWidgetProviderInfo) {
                // Fill in the widget information
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
                createItemInfo = new PendingAddWidgetInfo(info, null, null);
                int[] cellSpans = CellLayout.rectToCell(getResources(),
                        info.minWidth, info.minHeight, null);
                FastBitmapDrawable preview = new FastBitmapDrawable(data.generatedImages.get(i));
                widget.applyFromAppWidgetProviderInfo(info, preview, -1, cellSpans, 
                        mHolographicOutlineHelper);
                widget.setTag(createItemInfo);
            } else if (rawInfo instanceof ResolveInfo) {
                // Fill in the shortcuts information
                ResolveInfo info = (ResolveInfo) rawInfo;
                createItemInfo = new PendingAddItemInfo();
                createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
                createItemInfo.componentName = new ComponentName(info.activityInfo.packageName,
                        info.activityInfo.name);
                FastBitmapDrawable preview = new FastBitmapDrawable(data.generatedImages.get(i));
                widget.applyFromResolveInfo(mPackageManager, info, preview,
                        mHolographicOutlineHelper);
                widget.setTag(createItemInfo);
            }
            widget.setOnClickListener(this);
            widget.setOnLongClickListener(this);
            widget.setOnTouchListener(this);

            // Layout each widget
            int ix = i % cellCountX;
            int iy = i / cellCountX;
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(iy, GridLayout.LEFT),
                    GridLayout.spec(ix, GridLayout.TOP));
            lp.width = cellWidth;
            lp.height = cellHeight;
            lp.setGravity(Gravity.TOP | Gravity.LEFT);
            if (ix > 0) lp.leftMargin = mWidgetWidthGap;
            if (iy > 0) lp.topMargin = mWidgetHeightGap;
            layout.addView(widget, lp);
        }

        invalidate();
        forceUpdateAdjacentPagesAlpha();

        if (mFadeInAdjacentScreens) {
            prepareGenerateHoloOutlinesTask(data.page, data.items, data.generatedImages);
        }
    }
    private void onHolographicPageItemsLoaded(AsyncTaskPageData data) {
        // Invalidate early to short-circuit children invalidates
        invalidate();

        int page = data.page;
        ViewGroup layout = (ViewGroup) getChildAt(page);
        if (layout instanceof PagedViewCellLayout) {
            PagedViewCellLayout cl = (PagedViewCellLayout) layout;
            int count = cl.getPageChildCount();
            if (count != data.generatedImages.size()) return;
            for (int i = 0; i < count; ++i) {
                PagedViewIcon icon = (PagedViewIcon) cl.getChildOnPageAt(i);
                icon.setHolographicOutline(data.generatedImages.get(i));
            }
        } else {
            int count = layout.getChildCount();
            if (count != data.generatedImages.size()) return;
            for (int i = 0; i < count; ++i) {
                View v = layout.getChildAt(i);
                ((PagedViewWidget) v).setHolographicOutline(data.generatedImages.get(i));
            }
        }
    }

    @Override
    public void syncPages() {
        removeAllViews();

        // Remove all background asyc tasks if we are loading content anew
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            task.cancel(false);
            iter.remove();
        }

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
    public void syncPageItems(int page, boolean immediate) {
        switch (mContentType) {
        case Applications:
            syncAppsPageItems(page, immediate);
            break;
        case Widgets:
            syncWidgetPageItems(page, immediate);
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

    @Override
    protected void onPageBeginMoving() {
        /* TO BE ENABLED LATER
        setChildrenDrawnWithCacheEnabled(true);
        for (int i = 0; i < getChildCount(); ++i) {
            View v = getChildAt(i);
            if (v instanceof PagedViewCellLayout) {
                ((PagedViewCellLayout) v).setChildrenDrawingCacheEnabled(true);
            }
        }
        */
        super.onPageBeginMoving();
    }

    @Override
    protected void onPageEndMoving() {
        /* TO BE ENABLED LATER
        for (int i = 0; i < getChildCount(); ++i) {
            View v = getChildAt(i);
            if (v instanceof PagedViewCellLayout) {
                ((PagedViewCellLayout) v).setChildrenDrawingCacheEnabled(false);
            }
        }
        setChildrenDrawnWithCacheEnabled(false);
        */
        super.onPageEndMoving();

        // We reset the save index when we change pages so that it will be recalculated on next
        // rotation
        mSaveInstanceStateItemIndex = -1;
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

        // The next layout pass will trigger data-ready if both widgets and apps are set, so 
        // request a layout to do this test and invalidate the page data when ready.
        if (testDataReady()) requestLayout();
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
        if (mContentType != ContentType.Applications) {
            // Reset to the first page of the Apps pane
            AppsCustomizeTabHost tabs = (AppsCustomizeTabHost)
                    mLauncher.findViewById(R.id.apps_customize_pane);
            tabs.selectAppsTab();
        } else if (getCurrentPage() != 0) {
            invalidatePageData(0);
        }
    }
    @Override
    public void dumpState() {
        // TODO: Dump information related to current list of Applications, Widgets, etc.
        ApplicationInfo.dumpApplicationInfoList(LOG_TAG, "mApps", mApps);
        dumpAppWidgetProviderInfoList(LOG_TAG, "mWidgets", mWidgets);
    }
    private void dumpAppWidgetProviderInfoList(String tag, String label,
            ArrayList<Object> list) {
        Log.d(tag, label + " size=" + list.size());
        for (Object i: list) {
            if (i instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) i;
                Log.d(tag, "   label=\"" + info.label + "\" previewImage=" + info.previewImage
                        + " resizeMode=" + info.resizeMode + " configure=" + info.configure
                        + " initialLayout=" + info.initialLayout
                        + " minWidth=" + info.minWidth + " minHeight=" + info.minHeight);
            } else if (i instanceof ResolveInfo) {
                ResolveInfo info = (ResolveInfo) i;
                Log.d(tag, "   label=\"" + info.loadLabel(mPackageManager) + "\" icon="
                        + info.icon);
            }
        }
    }
    @Override
    public void surrender() {
        // TODO: If we are in the middle of any process (ie. for holographic outlines, etc) we
        // should stop this now.
    }

    /*
     * We load an extra page on each side to prevent flashes from scrolling and loading of the
     * widget previews in the background with the AsyncTasks.
     */
    protected int getAssociatedLowerPageBound(int page) {
        return Math.max(0, page - 2);
    }
    protected int getAssociatedUpperPageBound(int page) {
        final int count = getChildCount();
        return Math.min(page + 2, count - 1);
    }

    @Override
    protected String getCurrentPageDescription() {
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        int stringId = R.string.default_scroll_format;
        switch (mContentType) {
        case Applications:
            stringId = R.string.apps_customize_apps_scroll_format;
            break;
        case Widgets:
            stringId = R.string.apps_customize_widgets_scroll_format;
            break;
        }
        return String.format(mContext.getString(stringId), page + 1, getChildCount());
    }
}
