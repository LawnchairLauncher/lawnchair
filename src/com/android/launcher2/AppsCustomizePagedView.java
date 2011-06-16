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
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
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
    AsyncTaskPageData(int p, ArrayList<Object> l, int cw, int ch, AsyncTaskCallback bgR,
            AsyncTaskCallback postR) {
        page = p;
        items = l;
        generatedImages = new ArrayList<Bitmap>();
        cellWidth = cw;
        cellHeight = ch;
        doInBackgroundCallback = bgR;
        postExecuteCallback = postR;
    }
    int page;
    ArrayList<Object> items;
    ArrayList<Bitmap> sourceImages;
    ArrayList<Bitmap> generatedImages;
    int cellWidth;
    int cellHeight;
    AsyncTaskCallback doInBackgroundCallback;
    AsyncTaskCallback postExecuteCallback;
}

/**
 * A generic template for an async task used in AppsCustomize.
 */
class AppsCustomizeAsyncTask extends AsyncTask<AsyncTaskPageData, Void, AsyncTaskPageData> {
    AppsCustomizeAsyncTask(int p, AppsCustomizePagedView.ContentType t) {
        page = p;
        pageContentType = t;
        threadPriority = Process.THREAD_PRIORITY_DEFAULT;
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

    // Content
    private ContentType mContentType;
    private ArrayList<ApplicationInfo> mApps;
    private List<Object> mWidgets;

    // Caching
    private Canvas mCanvas;
    private Drawable mDefaultWidgetBackground;
    private IconCache mIconCache;

    // Dimens
    private int mContentWidth;
    private int mMaxWidgetSpan, mMinWidgetSpan;
    private int mWidgetWidthGap, mWidgetHeightGap;
    private int mWidgetCountX, mWidgetCountY;
    private final int mWidgetPreviewIconPaddedDimension;
    private final float sWidgetPreviewIconPaddingPercentage = 0.25f;
    private PagedViewCellLayout mWidgetSpacingLayout;

    // Previews & outlines
    ArrayList<AppsCustomizeAsyncTask> mRunningTasks;
    private HolographicOutlineHelper mHolographicOutlineHelper;

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
        mDefaultWidgetBackground = resources.getDrawable(R.drawable.default_widget_preview);

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
        int iconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size);
        mWidgetPreviewIconPaddedDimension =
            (int) (iconSize * (1 + (2 * sWidgetPreviewIconPaddingPercentage)));
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
    protected void onWallpaperTap(android.view.MotionEvent ev) {
        mLauncher.showWorkspace(true);
    }

    /**
     * This differs from isDataReady as this is the test done if isDataReady is not set.
     */
    private boolean testDataReady() {
        // We only do this test once, and we default to the Applications page, so we only really
        // have to wait for there to be apps.
        return !mApps.isEmpty();
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
        mWidgetCountX = Math.max(1, (int) Math.round(mCellCountX / 2f));
        mWidgetCountY = Math.max(1, (int) Math.round(mCellCountY / 3f));
        mContentWidth = mWidgetSpacingLayout.getContentWidth();

        invalidatePageData();
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
        // Get the list of widgets and shortcuts
        mWidgets.clear();
        List<AppWidgetProviderInfo> widgets =
            AppWidgetManager.getInstance(mLauncher).getInstalledProviders();
        Collections.sort(widgets,
                new LauncherModel.WidgetAndShortcutNameComparator(mPackageManager));
        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        List<ResolveInfo> shortcuts = mPackageManager.queryIntentActivities(shortcutsIntent, 0);
        Collections.sort(shortcuts,
                new LauncherModel.WidgetAndShortcutNameComparator(mPackageManager));
        mWidgets.addAll(widgets);
        mWidgets.addAll(shortcuts);

        // The next layout pass will trigger data-ready if both widgets and apps are set, so request
        // a layout to do this test and invalidate the page data when ready.
        if (testDataReady()) requestLayout();
    }

    @Override
    public void onClick(View v) {
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
        // Make a copy of the ApplicationInfo
        ApplicationInfo appInfo = new ApplicationInfo((ApplicationInfo) v.getTag());

        // Compose the drag image (top compound drawable, index is 1)
        final TextView tv = (TextView) v;
        final Drawable icon = tv.getCompoundDrawables()[1];
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(),
                Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(b);
        mCanvas.save();
        mCanvas.translate((v.getWidth() - icon.getIntrinsicWidth()) / 2, v.getPaddingTop());
        icon.draw(mCanvas);
        mCanvas.restore();

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
            int[] spanXY = CellLayout.rectToCell(getResources(),
                    createWidgetInfo.minWidth, createWidgetInfo.minHeight, null);
            createItemInfo.spanX = spanXY[0];
            createItemInfo.spanY = spanXY[1];

            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            renderDrawableToBitmap(preview, b, 0, 0, w, h, 1, 1);
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


        if (v instanceof PagedViewIcon) {
            beginDraggingApplication(v);
        } else if (v instanceof PagedViewWidget) {
            beginDraggingWidget(v);
        }

        // Go into spring loaded mode
        int currentPageIndex = mLauncher.getWorkspace().getCurrentPage();
        CellLayout currentPage = (CellLayout) mLauncher.getWorkspace().getChildAt(currentPageIndex);
        mLauncher.enterSpringLoadedDragMode(currentPage);
        return true;
    }
    private void endDragging(boolean success) {
        mLauncher.exitSpringLoadedDragMode();
        mLauncher.getWorkspace().onDragStopped(success);
        mLauncher.unlockScreenOrientation();

    }

    /*
     * DragSource implementation
     */
    @Override
    public void onDragViewVisible() {}
    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {
        endDragging(success);

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
        setCurrentPage(0);
        invalidatePageData();
    }

    public boolean isContentType(ContentType type) {
        return (mContentType == type);
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
        int widthSpec = MeasureSpec.makeMeasureSpec(getPageContentWidth(), MeasureSpec.AT_MOST);
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
    public void syncAppsPageItems(int page) {
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

        prepareGenerateHoloOutlinesTask(page, items, images);
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
    /**
     * Creates and executes a new AsyncTask to load a page of widget previews.
     */
    private void prepareLoadWidgetPreviewsTask(int page, ArrayList<Object> widgets,
            int cellWidth, int cellHeight) {
        // Prune all tasks that are no longer needed
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int taskPage = task.page;
            if (taskPage < getAssociatedLowerPageBound(mCurrentPage) ||
                    taskPage > getAssociatedUpperPageBound(mCurrentPage)) {
                task.cancel(false);
                iter.remove();
            } else {
                task.setThreadPriority(getThreadPriorityForPage(taskPage));
            }
        }

        AsyncTaskPageData pageData = new AsyncTaskPageData(page, widgets, cellWidth, cellHeight,
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    // Ensure that this task starts running at the correct priority
                    task.syncThreadPriority();

                    // Load each of the widget/shortcut previews
                    ArrayList<Object> items = data.items;
                    ArrayList<Bitmap> images = data.generatedImages;
                    int count = items.size();
                    int cellWidth = data.cellWidth;
                    int cellHeight = data.cellHeight;
                    for (int i = 0; i < count && !task.isCancelled(); ++i) {
                        // Before work on each item, ensure that this task is running at the correct
                        // priority
                        task.syncThreadPriority();

                        Object rawInfo = items.get(i);
                        if (rawInfo instanceof AppWidgetProviderInfo) {
                            AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
                            int[] cellSpans = CellLayout.rectToCell(getResources(),
                                    info.minWidth, info.minHeight, null);
                            images.add(getWidgetPreview(info, cellSpans[0],cellSpans[1],
                                    cellWidth, cellHeight));
                        } else if (rawInfo instanceof ResolveInfo) {
                            // Fill in the shortcuts information
                            ResolveInfo info = (ResolveInfo) rawInfo;
                            images.add(getShortcutPreview(info, cellWidth, cellHeight));
                        }
                    }
                }
            },
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    mRunningTasks.remove(task);
                    if (task.page > getPageCount()) return;
                    if (task.pageContentType != mContentType) return;
                    onSyncWidgetPageItems(data);
                }
        });

        // Ensure that the task is appropriately prioritized and runs in parallel
        AppsCustomizeAsyncTask t = new AppsCustomizeAsyncTask(page, mContentType);
        t.setThreadPriority(getThreadPriorityForPage(page));
        t.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, pageData);
        mRunningTasks.add(t);
    }
    /**
     * Creates and executes a new AsyncTask to load the outlines for a page of content.
     */
    private void prepareGenerateHoloOutlinesTask(int page, ArrayList<Object> items,
            ArrayList<Bitmap> images) {
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

                        images.add(outline);
                    }
                }
            },
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    mRunningTasks.remove(task);
                    if (task.page > getPageCount()) return;
                    if (task.pageContentType != mContentType) return;
                    onHolographicPageItemsLoaded(data);
                }
            });

        // Ensure that the outline task always runs in the background, serially
        AppsCustomizeAsyncTask t =
            new AppsCustomizeAsyncTask(page, mContentType);
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
    private synchronized void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x, int y, int w, int h,
            float scaleX, float scaleY) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            c.scale(scaleX, scaleY);
            Rect oldBounds = d.copyBounds();
            d.setBounds(x, y, x + w, y + h);
            d.draw(c);
            d.setBounds(oldBounds); // Restore the bounds
        }
    }
    private Bitmap getShortcutPreview(ResolveInfo info, int cellWidth,
            int cellHeight) {
        Resources resources = mLauncher.getResources();
        int iconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size);
        // We only need to make it wide enough so as not allow the preview to be scaled
        int expectedWidth = cellWidth;
        int expectedHeight = mWidgetPreviewIconPaddedDimension;

        // Render the icon
        Bitmap preview = Bitmap.createBitmap(expectedWidth, expectedHeight, Config.ARGB_8888);
        Drawable icon = mIconCache.getFullResIcon(info, mPackageManager);
        renderDrawableToBitmap(icon, preview, 0, 0, iconSize, iconSize, 1f, 1f);
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
            int iconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size);

            // Specify the dimensions of the bitmap
            if (info.minWidth >= info.minHeight) {
                expectedWidth = cellWidth;
                expectedHeight = mWidgetPreviewIconPaddedDimension;
            } else {
                // Note that in vertical widgets, we might not have enough space due to the text
                // label, so be conservative and use the width as a height bound
                expectedWidth = mWidgetPreviewIconPaddedDimension;
                expectedHeight = cellWidth;
            }

            preview = Bitmap.createBitmap(expectedWidth, expectedHeight, Config.ARGB_8888);
            renderDrawableToBitmap(mDefaultWidgetBackground, preview, 0, 0, expectedWidth,
                    expectedHeight, 1f,1f);

            // Draw the icon in the top left corner
            try {
                Drawable icon = null;
                if (info.icon > 0) icon = mPackageManager.getDrawable(packageName, info.icon, null);
                if (icon == null) icon = resources.getDrawable(R.drawable.ic_launcher_application);

                int offset = (int) (iconSize * sWidgetPreviewIconPaddingPercentage);
                renderDrawableToBitmap(icon, preview, offset, offset, iconSize, iconSize, 1f, 1f);
            } catch (Resources.NotFoundException e) {}
        }
        return preview;
    }
    public void syncWidgetPages() {
        // Ensure that we have the right number of pages
        Context context = getContext();
        int numWidgetsPerPage = mWidgetCountX * mWidgetCountY;
        int numPages = (int) Math.ceil(mWidgets.size() / (float) numWidgetsPerPage);
        for (int i = 0; i < numPages; ++i) {
            PagedViewGridLayout layout = new PagedViewGridLayout(context, mWidgetCountX,
                    mWidgetCountY);
            setupPage(layout);
            addView(layout);
        }
    }
    public void syncWidgetPageItems(int page) {
        // Calculate the dimensions of each cell we are giving to each widget
        ArrayList<Object> widgets = new ArrayList<Object>();
        int cellWidth = ((mWidgetSpacingLayout.getContentWidth()
                - mPageLayoutPaddingLeft - mPageLayoutPaddingRight
                - ((mWidgetCountX - 1) * mWidgetWidthGap)) / mWidgetCountX);
        int cellHeight = ((mWidgetSpacingLayout.getContentHeight()
                - mPageLayoutPaddingTop - mPageLayoutPaddingBottom
                - ((mWidgetCountY - 1) * mWidgetHeightGap)) / mWidgetCountY);

        int numWidgetsPerPage = mWidgetCountX * mWidgetCountY;
        int offset = page * numWidgetsPerPage;
        for (int i = offset; i < Math.min(offset + numWidgetsPerPage, mWidgets.size()); ++i) {
            widgets.add(mWidgets.get(i));
        }

        prepareLoadWidgetPreviewsTask(page, widgets, cellWidth, cellHeight);
    }
    private void onSyncWidgetPageItems(AsyncTaskPageData data) {
        int page = data.page;
        PagedViewGridLayout layout = (PagedViewGridLayout) getChildAt(page);
        layout.removeAllViews();

        ArrayList<Object> items = data.items;
        int count = items.size();
        int cellWidth = data.cellWidth;
        int cellHeight = data.cellHeight;
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
            int ix = i % mWidgetCountX;
            int iy = i / mWidgetCountX;
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    new GridLayout.Group(iy, 1, GridLayout.LEFT),
                    new GridLayout.Group(ix, 1, GridLayout.TOP));
            lp.width = cellWidth;
            lp.height = cellHeight;
            if (ix > 0) lp.leftMargin = mWidgetWidthGap;
            if (iy > 0) lp.topMargin = mWidgetHeightGap;
            layout.addView(widget, lp);
        }

        invalidate();
        forceUpdateAdjacentPagesAlpha();
        prepareGenerateHoloOutlinesTask(data.page, data.items, data.generatedImages);
    }
    private void onHolographicPageItemsLoaded(AsyncTaskPageData data) {
        // Invalidate early to short-circuit children invalidates
        invalidate();

        int page = data.page;
        ViewGroup layout = (ViewGroup) getChildAt(page);
        if (layout instanceof PagedViewCellLayout) {
            PagedViewCellLayout cl = (PagedViewCellLayout) layout;
            int count = cl.getPageChildCount();
            for (int i = 0; i < count; ++i) {
                PagedViewIcon icon = (PagedViewIcon) cl.getChildOnPageAt(i);
                icon.setHolographicOutline(data.generatedImages.get(i));
            }
        } else {
            int count = layout.getChildCount();
            for (int i = 0; i < count; ++i) {
                View v = layout.getChildAt(i);
                ((PagedViewWidget) v).setHolographicOutline(data.generatedImages.get(i));
            }
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

        // The next layout pass will trigger data-ready if both widgets and apps are set, so request
        // a layout to do this test and invalidate the page data when ready.
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
            tabs.setCurrentTabByTag(tabs.getTabTagForContentType(ContentType.Applications));
        } else {
            setCurrentPage(0);
            invalidatePageData();
        }
    }
    @Override
    public void dumpState() {
        // TODO: Dump information related to current list of Applications, Widgets, etc.
        ApplicationInfo.dumpApplicationInfoList(LOG_TAG, "mApps", mApps);
        dumpAppWidgetProviderInfoList(LOG_TAG, "mWidgets", mWidgets);
    }
    private void dumpAppWidgetProviderInfoList(String tag, String label,
            List<Object> list) {
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

    @Override
    protected int getPageWidthForScrollingIndicator() {
        return getPageContentWidth();
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
}
