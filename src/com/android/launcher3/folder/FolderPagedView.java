/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.folder;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.ViewCache;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ClipPathView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class FolderPagedView extends PagedView<PageIndicatorDots> implements ClipPathView {

    private static final String TAG = "FolderPagedView";

    private static final int REORDER_ANIMATION_DURATION = 230;
    private static final int START_VIEW_REORDER_DELAY = 30;
    private static final float VIEW_REORDER_DELAY_FACTOR = 0.9f;

    /**
     * Fraction of the width to scroll when showing the next page hint.
     */
    private static final float SCROLL_HINT_FRACTION = 0.07f;

    private static final int[] sTmpArray = new int[2];

    public final boolean mIsRtl;

    private final ViewGroupFocusHelper mFocusIndicatorHelper;

    @Thunk final ArrayMap<View, Runnable> mPendingAnimations = new ArrayMap<>();

    private final FolderGridOrganizer mOrganizer;
    private final ViewCache mViewCache;

    private int mAllocatedContentSize;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountY;

    private Folder mFolder;

    private Path mClipPath;

    // If the views are attached to the folder or not. A folder should be bound when its
    // animating or is open.
    private boolean mViewsBound = false;

    public FolderPagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ActivityContext activityContext = ActivityContext.lookupContext(context);
        DeviceProfile profile = activityContext.getDeviceProfile();
        mOrganizer = new FolderGridOrganizer(profile);

        mIsRtl = Utilities.isRtl(getResources());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
        mViewCache = activityContext.getViewCache();
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
        mPageIndicator = folder.findViewById(R.id.folder_page_indicator);
        initParentViews(folder);
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     */
    private void setupContentDimensions(int count) {
        mAllocatedContentSize = count;
        mOrganizer.setContentSize(count);
        mGridCountX = mOrganizer.getCountX();
        mGridCountY = mOrganizer.getCountY();

        // Update grid size
        for (int i = getPageCount() - 1; i >= 0; i--) {
            getPageAt(i).setGridSize(mGridCountX, mGridCountY);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mClipPath != null) {
            int count = canvas.save();
            canvas.clipPath(mClipPath);
            mFocusIndicatorHelper.draw(canvas);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(count);
        } else {
            mFocusIndicatorHelper.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }

    /**
     * Binds items to the layout.
     */
    public void bindItems(List<WorkspaceItemInfo> items) {
        if (mViewsBound) {
            unbindItems();
        }
        arrangeChildren(items.stream().map(this::createNewView).collect(Collectors.toList()));
        mViewsBound = true;
    }

    /**
     * Removes all the icons from the folder
     */
    public void unbindItems() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            CellLayout page = (CellLayout) getChildAt(i);
            ShortcutAndWidgetContainer container = page.getShortcutsAndWidgets();
            for (int j = container.getChildCount() - 1; j >= 0; j--) {
                container.getChildAt(j).setVisibility(View.VISIBLE);
                mViewCache.recycleView(R.layout.folder_application, container.getChildAt(j));
            }
            page.removeAllViews();
            mViewCache.recycleView(R.layout.folder_page, page);
        }
        removeAllViews();
        mViewsBound = false;
    }

    /**
     * Returns true if the icons are bound to the folder
     */
    public boolean areViewsBound() {
        return mViewsBound;
    }

    /**
     * Creates and adds an icon corresponding to the provided rank
     * @return the created icon
     */
    public View createAndAddViewForRank(WorkspaceItemInfo item, int rank) {
        View icon = createNewView(item);
        if (!mViewsBound) {
            return icon;
        }
        ArrayList<View> views = new ArrayList<>(mFolder.getIconsInReadingOrder());
        views.add(rank, icon);
        arrangeChildren(views);
        return icon;
    }

    /**
     * Adds the {@param view} to the layout based on {@param rank} and updated the position
     * related attributes. It assumes that {@param item} is already attached to the view.
     */
    public void addViewForRank(View view, WorkspaceItemInfo item, int rank) {
        int pageNo = rank / mOrganizer.getMaxItemsPerPage();

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) view.getLayoutParams();
        lp.setCellXY(mOrganizer.getPosForRank(rank));
        getPageAt(pageNo).addViewToCellLayout(view, -1, item.getViewId(), lp, true);
    }

    @SuppressLint("InflateParams")
    public View createNewView(WorkspaceItemInfo item) {
        if (item == null) {
            return null;
        }
        final BubbleTextView textView = mViewCache.getView(
                R.layout.folder_application, getContext(), null);
        textView.applyFromWorkspaceItem(item);
        textView.setOnClickListener(mFolder.mActivityContext.getItemOnClickListener());
        textView.setOnLongClickListener(mFolder);
        textView.setOnFocusChangeListener(mFocusIndicatorHelper);
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) textView.getLayoutParams();
        if (lp == null) {
            textView.setLayoutParams(new CellLayoutLayoutParams(
                    item.cellX, item.cellY, item.spanX, item.spanY));
        } else {
            lp.setCellX(item.cellX);
            lp.setCellY(item.cellY);
            lp.cellHSpan = lp.cellVSpan = 1;
        }
        return textView;
    }

    @Nullable
    @Override
    public CellLayout getPageAt(int index) {
        return (CellLayout) getChildAt(index);
    }

    @Nullable
    public CellLayout getCurrentCellLayout() {
        return getPageAt(getNextPage());
    }

    private CellLayout createAndAddNewPage() {
        DeviceProfile grid = mFolder.mActivityContext.getDeviceProfile();
        CellLayout page = mViewCache.getView(R.layout.folder_page, getContext(), this);
        page.setCellDimensions(grid.folderCellWidthPx, grid.folderCellHeightPx);
        page.getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        page.setInvertIfRtl(true);
        page.setGridSize(mGridCountX, mGridCountY);

        addView(page, -1, generateDefaultLayoutParams());
        return page;
    }

    @Override
    protected int getChildGap(int fromIndex, int toIndex) {
        return getPaddingLeft() + getPaddingRight();
    }

    public void setFixedSize(int width, int height) {
        width -= (getPaddingLeft() + getPaddingRight());
        height -= (getPaddingTop() + getPaddingBottom());
        for (int i = getChildCount() - 1; i >= 0; i --) {
            ((CellLayout) getChildAt(i)).setFixedSize(width, height);
        }
    }

    public void removeItem(View v) {
        for (int i = getChildCount() - 1; i >= 0; i --) {
            getPageAt(i).removeView(v);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mMaxScroll > 0) mPageIndicator.setScroll(l, mMaxScroll);
    }

    /**
     * Updates position and rank of all the children in the view.
     * It essentially removes all views from all the pages and then adds them again in appropriate
     * page.
     *
     * @param list the ordered list of children.
     */
    @SuppressLint("RtlHardcoded")
    public void arrangeChildren(List<View> list) {
        int itemCount = list.size();
        ArrayList<CellLayout> pages = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout page = (CellLayout) getChildAt(i);
            page.removeAllViews();
            pages.add(page);
        }
        mOrganizer.setFolderInfo(mFolder.getInfo());
        setupContentDimensions(itemCount);

        Iterator<CellLayout> pageItr = pages.iterator();
        CellLayout currentPage = null;

        int position = 0;
        int rank = 0;

        for (int i = 0; i < itemCount; i++) {
            View v = list.size() > i ? list.get(i) : null;
            if (currentPage == null || position >= mOrganizer.getMaxItemsPerPage()) {
                // Next page
                if (pageItr.hasNext()) {
                    currentPage = pageItr.next();
                } else {
                    currentPage = createAndAddNewPage();
                }
                position = 0;
            }

            if (v != null) {
                CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
                ItemInfo info = (ItemInfo) v.getTag();
                lp.setCellXY(mOrganizer.getPosForRank(rank));
                currentPage.addViewToCellLayout(v, -1, info.getViewId(), lp, true);

                if (mOrganizer.isItemInPreview(rank) && v instanceof BubbleTextView) {
                    ((BubbleTextView) v).verifyHighRes();
                }
            }

            rank++;
            position++;
        }

        // Remove extra views.
        boolean removed = false;
        while (pageItr.hasNext()) {
            removeView(pageItr.next());
            removed = true;
        }
        if (removed) {
            setCurrentPage(0);
        }

        setEnableOverscroll(getPageCount() > 1);

        // Update footer
        mPageIndicator.setVisibility(getPageCount() > 1 ? View.VISIBLE : View.GONE);
        // Set the gravity as LEFT or RIGHT instead of START, as START depends on the actual text.
        mFolder.mFolderName.setGravity(getPageCount() > 1 ?
                (mIsRtl ? Gravity.RIGHT : Gravity.LEFT) : Gravity.CENTER_HORIZONTAL);
    }

    public int getDesiredWidth() {
        return getPageCount() > 0 ?
                (getPageAt(0).getDesiredWidth() + getPaddingLeft() + getPaddingRight()) : 0;
    }

    public int getDesiredHeight()  {
        return  getPageCount() > 0 ?
                (getPageAt(0).getDesiredHeight() + getPaddingTop() + getPaddingBottom()) : 0;
    }

    /**
     * @return the rank of the cell nearest to the provided pixel position.
     */
    public int findNearestArea(int pixelX, int pixelY) {
        int pageIndex = getNextPage();
        CellLayout page = getPageAt(pageIndex);
        page.findNearestAreaIgnoreOccupied(pixelX, pixelY, 1, 1, sTmpArray);
        if (mFolder.isLayoutRtl()) {
            sTmpArray[0] = page.getCountX() - sTmpArray[0] - 1;
        }
        return Math.min(mAllocatedContentSize - 1,
                pageIndex * mOrganizer.getMaxItemsPerPage()
                        + sTmpArray[1] * mGridCountX + sTmpArray[0]);
    }

    public View getFirstItem() {
        return getViewInCurrentPage(c -> 0);
    }

    public View getLastItem() {
        return getViewInCurrentPage(c -> c.getChildCount() - 1);
    }

    private View getViewInCurrentPage(ToIntFunction<ShortcutAndWidgetContainer> rankProvider) {
        if (getChildCount() < 1 || getCurrentCellLayout() == null) {
            return null;
        }
        ShortcutAndWidgetContainer container = getCurrentCellLayout().getShortcutsAndWidgets();
        int rank = rankProvider.applyAsInt(container);
        if (mGridCountX > 0) {
            return container.getChildAt(rank % mGridCountX, rank / mGridCountX);
        } else {
            return container.getChildAt(rank);
        }
    }

    /**
     * Iterates over all its items in a reading order.
     * @return the view for which the operator returned true.
     */
    public View iterateOverItems(ItemOperator op) {
        for (int k = 0 ; k < getChildCount(); k++) {
            CellLayout page = getPageAt(k);
            for (int j = 0; j < page.getCountY(); j++) {
                for (int i = 0; i < page.getCountX(); i++) {
                    View v = page.getChildAt(i, j);
                    if ((v != null) && op.evaluate((ItemInfo) v.getTag(), v)) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    public String getAccessibilityDescription() {
        return getContext().getString(R.string.folder_opened, mGridCountX, mGridCountY);
    }

    /**
     * Sets the focus on the first visible child.
     */
    public void setFocusOnFirstChild() {
        CellLayout currentCellLayout = getCurrentCellLayout();
        if (currentCellLayout == null) {
            return;
        }
        View firstChild = currentCellLayout.getChildAt(0, 0);
        if (firstChild == null) {
            return;
        }
        firstChild.requestFocus();
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        if (mFolder != null) {
            mFolder.updateTextViewFocus();
        }
    }

    /**
     * Scrolls the current view by a fraction
     */
    public void showScrollHint(int direction) {
        float fraction = (direction == Folder.SCROLL_LEFT) ^ mIsRtl
                ? -SCROLL_HINT_FRACTION : SCROLL_HINT_FRACTION;
        int hint = (int) (fraction * getWidth());
        int scroll = getScrollForPage(getNextPage()) + hint;
        int delta = scroll - getScrollX();
        if (delta != 0) {
            mScroller.startScroll(getScrollX(), 0, delta, 0, Folder.SCROLL_HINT_DURATION);
            invalidate();
        }
    }

    public void clearScrollHint() {
        if (getScrollX() != getScrollForPage(getNextPage())) {
            snapToPage(getNextPage());
        }
    }

    /**
     * Finish animation all the views which are animating across pages
     */
    public void completePendingPageChanges() {
        if (!mPendingAnimations.isEmpty()) {
            ArrayMap<View, Runnable> pendingViews = new ArrayMap<>(mPendingAnimations);
            for (Map.Entry<View, Runnable> e : pendingViews.entrySet()) {
                e.getKey().animate().cancel();
                e.getValue().run();
            }
        }
    }

    public boolean rankOnCurrentPage(int rank) {
        int p = rank / mOrganizer.getMaxItemsPerPage();
        return p == getNextPage();
    }

    @Override
    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        // Ensure that adjacent pages have high resolution icons
        verifyVisibleHighResIcons(getCurrentPage() - 1);
        verifyVisibleHighResIcons(getCurrentPage() + 1);
    }

    /**
     * Ensures that all the icons on the given page are of high-res
     */
    public void verifyVisibleHighResIcons(int pageNo) {
        CellLayout page = getPageAt(pageNo);
        if (page != null) {
            ShortcutAndWidgetContainer parent = page.getShortcutsAndWidgets();
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                BubbleTextView icon = ((BubbleTextView) parent.getChildAt(i));
                icon.verifyHighRes();
                // Set the callback back to the actual icon, in case
                // it was captured by the FolderIcon
                Drawable d = icon.getIcon();
                if (d != null) {
                    d.setCallback(icon);
                }
            }
        }
    }

    public int getAllocatedContentSize() {
        return mAllocatedContentSize;
    }

    /**
     * Reorders the items such that the {@param empty} spot moves to {@param target}
     */
    public void realTimeReorder(int empty, int target) {
        if (!mViewsBound) {
            return;
        }
        completePendingPageChanges();
        int delay = 0;
        float delayAmount = START_VIEW_REORDER_DELAY;

        // Animation only happens on the current page.
        int pageToAnimate = getNextPage();
        int maxItemsPerPage = mOrganizer.getMaxItemsPerPage();

        int pageT = target / maxItemsPerPage;
        int pagePosT = target % maxItemsPerPage;

        if (pageT != pageToAnimate) {
            Log.e(TAG, "Cannot animate when the target cell is invisible");
        }
        int pagePosE = empty % maxItemsPerPage;
        int pageE = empty / maxItemsPerPage;

        int startPos, endPos;
        int moveStart, moveEnd;
        int direction;

        if (target == empty) {
            // No animation
            return;
        } else if (target > empty) {
            // Items will move backwards to make room for the empty cell.
            direction = 1;

            // If empty cell is in a different page, move them instantly.
            if (pageE < pageToAnimate) {
                moveStart = empty;
                // Instantly move the first item in the current page.
                moveEnd = pageToAnimate * maxItemsPerPage;
                // Animate the 2nd item in the current page, as the first item was already moved to
                // the last page.
                startPos = 0;
            } else {
                moveStart = moveEnd = -1;
                startPos = pagePosE;
            }

            endPos = pagePosT;
        } else {
            // The items will move forward.
            direction = -1;

            if (pageE > pageToAnimate) {
                // Move the items immediately.
                moveStart = empty;
                // Instantly move the last item in the current page.
                moveEnd = (pageToAnimate + 1) * maxItemsPerPage - 1;

                // Animations start with the second last item in the page
                startPos = maxItemsPerPage - 1;
            } else {
                moveStart = moveEnd = -1;
                startPos = pagePosE;
            }

            endPos = pagePosT;
        }

        // Instant moving views.
        while (moveStart != moveEnd) {
            int rankToMove = moveStart + direction;
            int p = rankToMove / maxItemsPerPage;
            int pagePos = rankToMove % maxItemsPerPage;
            int x = pagePos % mGridCountX;
            int y = pagePos / mGridCountX;

            final CellLayout page = getPageAt(p);
            final View v = page.getChildAt(x, y);
            if (v != null) {
                if (pageToAnimate != p) {
                    page.removeView(v);
                    addViewForRank(v, (WorkspaceItemInfo) v.getTag(), moveStart);
                } else {
                    // Do a fake animation before removing it.
                    final int newRank = moveStart;
                    final float oldTranslateX = v.getTranslationX();

                    Runnable endAction = new Runnable() {

                        @Override
                        public void run() {
                            mPendingAnimations.remove(v);
                            v.setTranslationX(oldTranslateX);
                            ((CellLayout) v.getParent().getParent()).removeView(v);
                            addViewForRank(v, (WorkspaceItemInfo) v.getTag(), newRank);
                        }
                    };
                    v.animate()
                        .translationXBy((direction > 0 ^ mIsRtl) ? -v.getWidth() : v.getWidth())
                        .setDuration(REORDER_ANIMATION_DURATION)
                        .setStartDelay(0)
                        .withEndAction(endAction);
                    mPendingAnimations.put(v, endAction);
                }
            }
            moveStart = rankToMove;
        }

        if ((endPos - startPos) * direction <= 0) {
            // No animation
            return;
        }

        CellLayout page = getPageAt(pageToAnimate);
        for (int i = startPos; i != endPos; i += direction) {
            int nextPos = i + direction;
            View v = page.getChildAt(nextPos % mGridCountX, nextPos / mGridCountX);
            if (page.animateChildToPosition(v, i % mGridCountX, i / mGridCountX,
                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                delay += delayAmount;
                delayAmount *= VIEW_REORDER_DELAY_FACTOR;
            }
        }
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        return AbstractFloatingView.getTopOpenViewWithType(mFolder.mActivityContext,
                TYPE_ALL & ~TYPE_FOLDER) == null;
    }

    public int itemsPerPage() {
        return mOrganizer.getMaxItemsPerPage();
    }

    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }
}
