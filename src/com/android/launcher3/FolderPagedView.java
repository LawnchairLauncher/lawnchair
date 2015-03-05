/**
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

package com.android.launcher3;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.android.launcher3.FocusHelper.PagedFolderKeyEventListener;
import com.android.launcher3.PageIndicator.PageMarkerResources;
import com.android.launcher3.Workspace.ItemOperator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class FolderPagedView extends PagedView implements Folder.FolderContent {

    private static final String TAG = "FolderPagedView";

    private static final int REORDER_ANIMATION_DURATION = 230;
    private static final int[] sTempPosArray = new int[2];

    // TODO: Remove this restriction
    private static final int MAX_ITEMS_PER_PAGE = 3;

    private final LayoutInflater mInflater;
    private final IconCache mIconCache;
    private final HashMap<View, Runnable> mPageChangingViews = new HashMap<>();

    private final CellLayout mFirstPage;

    final int mMaxCountX;
    final int mMaxCountY;
    final int mMaxItemsPerPage;

    private int mAllocatedContentSize;
    private int mGridCountX;
    private int mGridCountY;

    private Folder mFolder;
    private FocusIndicatorView mFocusIndicatorView;
    private PagedFolderKeyEventListener mKeyListener;

    public FolderPagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LauncherAppState app = LauncherAppState.getInstance();

        mFirstPage = newCellLayout();
        addFullScreenPage(mFirstPage);
        setCurrentPage(0);
        setDataIsReady();

        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        mMaxCountX = Math.min((int) grid.numColumns, MAX_ITEMS_PER_PAGE);
        mMaxCountY = Math.min((int) grid.numRows, MAX_ITEMS_PER_PAGE);
        mMaxItemsPerPage = mMaxCountX * mMaxCountY;

        mInflater = LayoutInflater.from(context);
        mIconCache = app.getIconCache();
    }

    @Override
    public void setFolder(Folder folder) {
        mFolder = folder;
        mFocusIndicatorView = (FocusIndicatorView) folder.findViewById(R.id.focus_indicator);
        mKeyListener = new PagedFolderKeyEventListener(folder);
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     * The grid size is calculated such that countY <= countX and countX = ceil(sqrt(count)) while
     * maintaining the restrictions of {@link #mMaxCountX} &amp; {@link #mMaxCountY}.
     */
    private void setupContentDimensions(int count) {
        mAllocatedContentSize = count;
        boolean done;
        if (count >= mMaxItemsPerPage) {
            mGridCountX = mMaxCountX;
            mGridCountY = mMaxCountY;
            done = true;
        } else {
            mGridCountX = mFirstPage.getCountX();
            mGridCountY = mFirstPage.getCountY();
            done = false;
        }

        while (!done) {
            int oldCountX = mGridCountX;
            int oldCountY = mGridCountY;
            if (mGridCountX * mGridCountY < count) {
                // Current grid is too small, expand it
                if ((mGridCountX <= mGridCountY || mGridCountY == mMaxCountY) && mGridCountX < mMaxCountX) {
                    mGridCountX++;
                } else if (mGridCountY < mMaxCountY) {
                    mGridCountY++;
                }
                if (mGridCountY == 0) mGridCountY++;
            } else if ((mGridCountY - 1) * mGridCountX >= count && mGridCountY >= mGridCountX) {
                mGridCountY = Math.max(0, mGridCountY - 1);
            } else if ((mGridCountX - 1) * mGridCountY >= count) {
                mGridCountX = Math.max(0, mGridCountX - 1);
            }
            done = mGridCountX == oldCountX && mGridCountY == oldCountY;
        }

        setGridSize(mGridCountX, mGridCountY);
    }

    public void setGridSize(int countX, int countY) {
        mGridCountX = countX;
        mGridCountY = countY;
        mFirstPage.setGridSize(mGridCountX, mGridCountY);
        for (int i = getPageCount() - 1; i > 0; i--) {
            getPageAt(i).setGridSize(mGridCountX, mGridCountY);
        }
    }

    @Override
    public ArrayList<ShortcutInfo> bindItems(ArrayList<ShortcutInfo> items) {
        final int count = items.size();

        if (getPageCount() > 1) {
            Log.d(TAG, "Binding items to an non-empty view");
            removeAllViews();
            addView(mFirstPage);
            mFirstPage.removeAllViews();
        }

        setupContentDimensions(count);
        CellLayout page = mFirstPage;
        int pagePosition = 0;
        int rank = 0;

        for (ShortcutInfo item : items) {
            if (pagePosition >= mMaxItemsPerPage) {
                // This page is full, add a new page.
                pagePosition = 0;
                page = newCellLayout();
                addFullScreenPage(page);
            }

            item.cellX = pagePosition % mGridCountX;
            item.cellY = pagePosition / mGridCountX;
            item.rank = rank;
            addNewView(item, page);

            rank++;
            pagePosition++;
        }
        return new ArrayList<ShortcutInfo>();
    }

    /**
     * Create space for a new item at the end, and returns the rank for that item.
     * Also sets the current page to the last page.
     */
    @Override
    public int allocateNewLastItemRank() {
        int rank = getItemCount();
        int total = rank + 1;
        if (rank < mMaxItemsPerPage) {
            // Rearrange the items as the grid size might change.
            mFolder.rearrangeChildren(total);
        } else {
            setupContentDimensions(total);
        }

        // Add a new page if last page is full
        if (getPageAt(getChildCount() - 1).getShortcutsAndWidgets().getChildCount()
                >= mMaxItemsPerPage) {
            addFullScreenPage(newCellLayout());
        }
        setCurrentPage(getChildCount() - 1);
        return rank;
    }

    @Override
    public View createAndAddViewForRank(ShortcutInfo item, int rank) {
        int pageNo = updateItemXY(item, rank);
        CellLayout page = getPageAt(pageNo);
        return addNewView(item, page);
    }

    @Override
    public void addViewForRank(View view, ShortcutInfo item, int rank) {
        int pageNo = updateItemXY(item, rank);
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();
        lp.cellX = item.cellX;
        lp.cellY = item.cellY;
        getPageAt(pageNo).addViewToCellLayout(
                view, -1, mFolder.mLauncher.getViewIdForItem(item), lp, true);
    }

    /**
     * Updates the item cellX and cellY position and return the page number for that item.
     */
    private int updateItemXY(ShortcutInfo item, int rank) {
        item.rank = rank;

        int pagePos = item.rank % mMaxItemsPerPage;
        item.cellX = pagePos % mGridCountX;
        item.cellY = pagePos / mGridCountX;

        return item.rank / mMaxItemsPerPage;
    }

    private View addNewView(ShortcutInfo item, CellLayout target) {
        final BubbleTextView textView = (BubbleTextView) mInflater.inflate(
                R.layout.folder_application, target.getShortcutsAndWidgets(), false);
        textView.applyFromShortcutInfo(item, mIconCache, false);
        textView.setOnClickListener(mFolder);
        textView.setOnLongClickListener(mFolder);
        textView.setOnFocusChangeListener(mFocusIndicatorView);
        textView.setOnKeyListener(mKeyListener);

        CellLayout.LayoutParams lp = new CellLayout.LayoutParams(
                item.cellX, item.cellY, item.spanX, item.spanY);
        target.addViewToCellLayout(
                textView, -1, mFolder.mLauncher.getViewIdForItem(item), lp, true);
        return textView;
    }

    @Override
    public CellLayout getPageAt(int index) {
        return (CellLayout) getChildAt(index);
    }

    public void removeCellLayoutView(View view) {
        for (int i = getChildCount() - 1; i >= 0; i --) {
            getPageAt(i).removeView(view);
        }
    }

    public CellLayout getCurrentCellLayout() {
        return getPageAt(getNextPage());
    }

    @Override
    public void addFullScreenPage(View page) {
        LayoutParams lp = generateDefaultLayoutParams();
        lp.isFullScreenPage = true;
        super.addView(page, -1, lp);
    }

    private CellLayout newCellLayout() {
        DeviceProfile grid = LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();

        CellLayout layout = new CellLayout(getContext());
        layout.setCellDimensions(grid.folderCellWidthPx, grid.folderCellHeightPx);
        layout.getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        layout.setInvertIfRtl(true);

        if (mFirstPage != null) {
            layout.setGridSize(mFirstPage.getCountX(), mFirstPage.getCountY());
        }

        return layout;
    }

    @Override
    public void setFixedSize(int width, int height) {
        for (int i = getChildCount() - 1; i >= 0; i --) {
            ((CellLayout) getChildAt(i)).setFixedSize(width, height);
        }
    }

    @Override
    public void removeItem(View v) {
        for (int i = getChildCount() - 1; i >= 0; i --) {
            getPageAt(i).removeView(v);
        }
    }

    /**
     * Updates position and rank of all the children in the view.
     * It essentially removes all views from all the pages and then adds them again in appropriate
     * page.
     *
     * @param list the ordered list of children.
     * @param itemCount if greater than the total children count, empty spaces are left
     * at the end, otherwise it is ignored.
     *
     */
    @Override
    public void arrangeChildren(ArrayList<View> list, int itemCount) {
        ArrayList<CellLayout> pages = new ArrayList<CellLayout>();
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout page = (CellLayout) getChildAt(i);
            page.removeAllViews();
            pages.add(page);
        }
        setupContentDimensions(itemCount);

        Iterator<CellLayout> pageItr = pages.iterator();
        CellLayout currentPage = null;

        int position = 0;
        int newX, newY, rank;

        rank = 0;
        for (View v : list) {
            if (currentPage == null || position >= mMaxItemsPerPage) {
                // Next page
                if (pageItr.hasNext()) {
                    currentPage = pageItr.next();
                } else {
                    currentPage = newCellLayout();
                    addFullScreenPage(currentPage);
                }
                position = 0;
            }

            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
            newX = position % mGridCountX;
            newY = position / mGridCountX;
            ItemInfo info = (ItemInfo) v.getTag();
            if (info.cellX != newX || info.cellY != newY || info.rank != rank) {
                info.cellX = newX;
                info.cellY = newY;
                info.rank = rank;
                LauncherModel.addOrMoveItemInDatabase(getContext(), info,
                        mFolder.mInfo.id, 0, info.cellX, info.cellY);
            }
            lp.cellX = info.cellX;
            lp.cellY = info.cellY;
            rank ++;
            position++;
            currentPage.addViewToCellLayout(
                    v, -1, mFolder.mLauncher.getViewIdForItem(info), lp, true);
        }

        boolean removed = false;
        while (pageItr.hasNext()) {
            CellLayout layout = pageItr.next();
            if (layout != mFirstPage) {
                removeView(layout);
                removed = true;
            }
        }
        if (removed) {
            setCurrentPage(0);
        }
    }

    @Override
    protected void loadAssociatedPages(int page, boolean immediateAndOnly) { }

    @Override
    public void syncPages() { }

    @Override
    public void syncPageItems(int page, boolean immediate) { }

    public int getDesiredWidth() {
        return mFirstPage.getDesiredWidth();
    }

    public int getDesiredHeight()  {
        return mFirstPage.getDesiredHeight();
    }

    @Override
    public int getItemCount() {
        int lastPage = getChildCount() - 1;
        return getPageAt(lastPage).getShortcutsAndWidgets().getChildCount()
                + lastPage * mMaxItemsPerPage;
    }

    @Override
    public int findNearestArea(int pixelX, int pixelY) {
        int pageIndex = getNextPage();
        CellLayout page = getPageAt(pageIndex);
        page.findNearestArea(pixelX, pixelY, 1, 1, null, false, sTempPosArray);
        if (mFolder.isLayoutRtl()) {
            sTempPosArray[0] = page.getCountX() - sTempPosArray[0] - 1;
        }
        return Math.min(mAllocatedContentSize - 1,
                pageIndex * mMaxItemsPerPage + sTempPosArray[1] * mGridCountX + sTempPosArray[0]);
    }

    @Override
    protected PageMarkerResources getPageIndicatorMarker(int pageIndex) {
        return new PageMarkerResources(R.drawable.ic_pageindicator_current_dark, R.drawable.ic_pageindicator_default_dark);
    }

    @Override
    public boolean isFull() {
        return false;
    }

    @Override
    public View getLastItem() {
        if (getChildCount() < 1) {
            return null;
        }
        ShortcutAndWidgetContainer lastContainer = getCurrentCellLayout().getShortcutsAndWidgets();
        int lastRank = lastContainer.getChildCount() - 1;
        if (mGridCountX > 0) {
            return lastContainer.getChildAt(lastRank % mGridCountX, lastRank / mGridCountX);
        } else {
            return lastContainer.getChildAt(lastRank);
        }
    }

    @Override
    public View iterateOverItems(ItemOperator op) {
        for (int k = 0 ; k < getChildCount(); k++) {
            CellLayout page = getPageAt(k);
            for (int j = 0; j < page.getCountY(); j++) {
                for (int i = 0; i < page.getCountX(); i++) {
                    View v = page.getChildAt(i, j);
                    if ((v != null) && op.evaluate((ItemInfo) v.getTag(), v, this)) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String getAccessibilityDescription() {
        return String.format(getContext().getString(R.string.folder_opened),
                mGridCountX, mGridCountY);
    }

    @Override
    public void setFocusOnFirstChild() {
        View firstChild = getCurrentCellLayout().getChildAt(0, 0);
        if (firstChild != null) {
            firstChild.requestFocus();
        }
    }

    @Override
    protected void notifyPageSwitchListener() {
        super.notifyPageSwitchListener();
        if (mFolder != null) {
            mFolder.updateTextViewFocus();
        }
    }

    @Override
    public void realTimeReorder(int empty, int target) {
        int delay = 0;
        float delayAmount = 30;

        // Animation only happens on the current page.
        int pageToAnimate = getNextPage();

        int pageT = target / mMaxItemsPerPage;
        int pagePosT = target % mMaxItemsPerPage;

        if (pageT != pageToAnimate) {
            Log.e(TAG, "Cannot animate when the target cell is invisible");
        }
        int pagePosE = empty % mMaxItemsPerPage;
        int pageE = empty / mMaxItemsPerPage;

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
                moveEnd = pageToAnimate * mMaxItemsPerPage;
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
                moveEnd = (pageToAnimate + 1) * mMaxItemsPerPage - 1;

                // Animations start with the second last item in the page
                startPos = mMaxItemsPerPage - 1;
            } else {
                moveStart = moveEnd = -1;
                startPos = pagePosE;
            }

            endPos = pagePosT;
        }

        // Instant moving views.
        while (moveStart != moveEnd) {
            int rankToMove = moveStart + direction;
            int p = rankToMove / mMaxItemsPerPage;
            int pagePos = rankToMove % mMaxItemsPerPage;
            int x = pagePos % mGridCountX;
            int y = pagePos / mGridCountX;

            final CellLayout page = getPageAt(p);
            final View v = page.getChildAt(x, y);
            if (v != null) {
                if (pageToAnimate != p) {
                    page.removeView(v);
                    addViewForRank(v, (ShortcutInfo) v.getTag(), moveStart);
                } else {
                    // Do a fake animation before removing it.
                    final int newRank = moveStart;
                    final float oldTranslateX = v.getTranslationX();

                    Runnable endAction = new Runnable() {

                        @Override
                        public void run() {
                            mPageChangingViews.remove(v);
                            v.setTranslationX(oldTranslateX);
                            ((CellLayout) v.getParent().getParent()).removeView(v);
                            addViewForRank(v, (ShortcutInfo) v.getTag(), newRank);
                        }
                    };
                    v.animate()
                        .translationXBy(direction > 0 ? -v.getWidth() : v.getWidth())
                        .setDuration(REORDER_ANIMATION_DURATION)
                        .setStartDelay(0)
                        .withEndAction(endAction);
                    mPageChangingViews.put(v, endAction);
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
            if (v != null) {
                ((ItemInfo) v.getTag()).rank -= direction;
            }
            if (page.animateChildToPosition(v, i % mGridCountX, i / mGridCountX,
                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                delay += delayAmount;
                delayAmount *= 0.9;
            }
        }
    }
}
