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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewDebug;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.FocusHelper.PagedFolderKeyEventListener;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace.ItemOperator;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class FolderPagedView extends PagedView {

    private static final String TAG = "FolderPagedView";

    private static final boolean ALLOW_FOLDER_SCROLL = true;

    private static final int REORDER_ANIMATION_DURATION = 230;
    private static final int START_VIEW_REORDER_DELAY = 30;
    private static final float VIEW_REORDER_DELAY_FACTOR = 0.9f;

    /**
     * Fraction of the width to scroll when showing the next page hint.
     */
    private static final float SCROLL_HINT_FRACTION = 0.07f;

    private static final int[] sTmpArray = new int[2];

    public final boolean mIsRtl;

    private final LayoutInflater mInflater;
    private final ViewGroupFocusHelper mFocusIndicatorHelper;

    @Thunk final ArrayMap<View, Runnable> mPendingAnimations = new ArrayMap<>();

    @ViewDebug.ExportedProperty(category = "launcher")
    private final int mMaxCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    private final int mMaxCountY;
    @ViewDebug.ExportedProperty(category = "launcher")
    private final int mMaxItemsPerPage;

    private int mAllocatedContentSize;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountY;

    private Folder mFolder;
    private PagedFolderKeyEventListener mKeyListener;

    private PageIndicator mPageIndicator;

    public FolderPagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        InvariantDeviceProfile profile = LauncherAppState.getIDP(context);
        mMaxCountX = profile.numFolderColumns;
        mMaxCountY = profile.numFolderRows;

        mMaxItemsPerPage = mMaxCountX * mMaxCountY;

        mInflater = LayoutInflater.from(context);

        mIsRtl = Utilities.isRtl(getResources());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
        mKeyListener = new PagedFolderKeyEventListener(folder);
        mPageIndicator = folder.findViewById(R.id.folder_page_indicator);
        initParentViews(folder);
    }

    /**
     * Calculates the grid size such that {@param count} items can fit in the grid.
     * The grid size is calculated such that countY <= countX and countX = ceil(sqrt(count)) while
     * maintaining the restrictions of {@link #mMaxCountX} &amp; {@link #mMaxCountY}.
     */
    public static void calculateGridSize(int count, int countX, int countY, int maxCountX,
            int maxCountY, int maxItemsPerPage, int[] out) {
        boolean done;
        int gridCountX = countX;
        int gridCountY = countY;

        if (count >= maxItemsPerPage) {
            gridCountX = maxCountX;
            gridCountY = maxCountY;
            done = true;
        } else {
            done = false;
        }

        while (!done) {
            int oldCountX = gridCountX;
            int oldCountY = gridCountY;
            if (gridCountX * gridCountY < count) {
                // Current grid is too small, expand it
                if ((gridCountX <= gridCountY || gridCountY == maxCountY)
                        && gridCountX < maxCountX) {
                    gridCountX++;
                } else if (gridCountY < maxCountY) {
                    gridCountY++;
                }
                if (gridCountY == 0) gridCountY++;
            } else if ((gridCountY - 1) * gridCountX >= count && gridCountY >= gridCountX) {
                gridCountY = Math.max(0, gridCountY - 1);
            } else if ((gridCountX - 1) * gridCountY >= count) {
                gridCountX = Math.max(0, gridCountX - 1);
            }
            done = gridCountX == oldCountX && gridCountY == oldCountY;
        }

        out[0] = gridCountX;
        out[1] = gridCountY;
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     */
    public void setupContentDimensions(int count) {
        mAllocatedContentSize = count;
        calculateGridSize(count, mGridCountX, mGridCountY, mMaxCountX, mMaxCountY, mMaxItemsPerPage,
                sTmpArray);
        mGridCountX = sTmpArray[0];
        mGridCountY = sTmpArray[1];

        // Update grid size
        for (int i = getPageCount() - 1; i >= 0; i--) {
            getPageAt(i).setGridSize(mGridCountX, mGridCountY);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mFocusIndicatorHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    /**
     * Binds items to the layout.
     * @return list of items that could not be bound, probably because we hit the max size limit.
     */
    public ArrayList<ShortcutInfo> bindItems(ArrayList<ShortcutInfo> items) {
        ArrayList<View> icons = new ArrayList<>();
        ArrayList<ShortcutInfo> extra = new ArrayList<>();

        for (ShortcutInfo item : items) {
            if (!ALLOW_FOLDER_SCROLL && icons.size() >= mMaxItemsPerPage) {
                extra.add(item);
            } else {
                icons.add(createNewView(item));
            }
        }
        arrangeChildren(icons, icons.size(), false);
        return extra;
    }

    public void allocateSpaceForRank(int rank) {
        ArrayList<View> views = new ArrayList<>(mFolder.getItemsInRankOrder());
        views.add(rank, null);
        arrangeChildren(views, views.size(), false);
    }

    private ArrayList<View> createListWithViewAtPos(ArrayList<View> list, View v, int position) {
        ArrayList<View> newList = new ArrayList<>(list.size() + 1);
        newList.addAll(list);
        newList.add(position, v);
        return newList;
    }

    /**
     * Create space for a new item and returns the rank for that item.
     * Also sets the current page to the last page.
     */
    public int allocateRankForNewItem() {
        ArrayList<View> rankOrder = mFolder.getItemsInRankOrder();
        int rank = rankOrder.size();

        ArrayList<View> views = createListWithViewAtPos(rankOrder, null, rank);
        arrangeChildren(views, views.size(), false);

        setCurrentPage(rank / mMaxItemsPerPage);
        return rank;
    }

    public View createAndAddViewForRank(ShortcutInfo item, int rank) {
        View icon = createNewView(item);
        allocateSpaceForRank(rank);
        addViewForRank(icon, item, rank);
        return icon;
    }

    /**
     * Adds the {@param view} to the layout based on {@param rank} and updated the position
     * related attributes. It assumes that {@param item} is already attached to the view.
     */
    public void addViewForRank(View view, ShortcutInfo item, int rank) {
        updateShortcutInfoWithRank(item, rank);

        ArrayList<View> views = createListWithViewAtPos(mFolder.getItemsInRankOrder(), view, rank);
        arrangeChildren(views, views.size(), false);
    }

    /**
     * Similar to {@link #addViewForRank(View, ShortcutInfo, int)}}, but specific to real time
     * reorder.
     *
     * The difference here is that during real time reorder, we are moving the Views in a contained
     * order.
     */
    public void addViewForRankDuringReorder(View view, ShortcutInfo item, int rank) {
        updateShortcutInfoWithRank(item, rank);

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();
        lp.cellX = item.cellX;
        lp.cellY = item.cellY;

        int pageNo = rank / mMaxItemsPerPage;
        getPageAt(pageNo).addViewToCellLayout(
                view, -1, mFolder.mLauncher.getViewIdForItem(item), lp, true);
    }

    /**
     * Similar to {@link #addViewForRank(View, ShortcutInfo, int)}, but specific to drag and drop.
     *
     * The difference is that we handle the drag and drop by adjusting the reading order of the
     * children, rather than based on their rank.
     */
    public void addViewForRankDuringDragAndDrop(View view, ShortcutInfo item, int readingRank) {
        updateShortcutInfoWithRank(item, readingRank);

        ArrayList<View> views = createListWithViewAtPos(mFolder.getItemsInReadingOrder(), view,
                readingRank);

        int numItems = views.size();
        ArrayList<View> rankOrder = new ArrayList<>(numItems);
        for (int i = 0; i < numItems; ++i) {
            rankOrder.add(views.get(getReadingOrderPosForRank(i)));
        }

        arrangeChildren(rankOrder, numItems, false);
    }

    private void updateShortcutInfoWithRank(ShortcutInfo info, int rank) {
        info.rank = rank;
        getCellXYPositionForRank(rank, sTmpArray);
        info.cellX = sTmpArray[0];
        info.cellY = sTmpArray[1];
    }

    @SuppressLint("InflateParams")
    public View createNewView(ShortcutInfo item) {
        final BubbleTextView textView = (BubbleTextView) mInflater.inflate(
                R.layout.folder_application, null, false);
        textView.applyFromShortcutInfo(item);
        textView.setHapticFeedbackEnabled(false);
        textView.setOnClickListener(mFolder);
        textView.setOnLongClickListener(mFolder);
        textView.setOnFocusChangeListener(mFocusIndicatorHelper);
        textView.setOnKeyListener(mKeyListener);

        textView.setLayoutParams(new CellLayout.LayoutParams(
                item.cellX, item.cellY, item.spanX, item.spanY));
        return textView;
    }

    @Override
    public CellLayout getPageAt(int index) {
        return (CellLayout) getChildAt(index);
    }

    public CellLayout getCurrentCellLayout() {
        return getPageAt(getNextPage());
    }

    private CellLayout createAndAddNewPage() {
        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        CellLayout page = (CellLayout) mInflater.inflate(R.layout.folder_page, this, false);
        page.setCellDimensions(grid.folderCellWidthPx, grid.folderCellHeightPx);
        page.getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        page.setInvertIfRtl(true);
        page.setGridSize(mGridCountX, mGridCountY);

        addView(page, -1, generateDefaultLayoutParams());
        return page;
    }

    @Override
    protected int getChildGap() {
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
        mPageIndicator.setScroll(l, mMaxScrollX);
    }

    /**
     * Updates position and rank of all the children in the view.
     * It essentially removes all views from all the pages and then adds them again in appropriate
     * page.
     *
     * @param rankOrderedList the rank-ordered list of children.
     * @param itemCount if greater than the total children count, empty spaces are left
     * at the end, otherwise it is ignored.
     *
     */
    public void arrangeChildren(ArrayList<View> rankOrderedList, int itemCount) {
        arrangeChildren(rankOrderedList, itemCount, true);
    }

    @SuppressLint("RtlHardcoded")
    private void arrangeChildren(ArrayList<View> rankOrderedList, int itemCount,
            boolean saveChanges) {
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

        FolderIconPreviewVerifier verifier = new FolderIconPreviewVerifier(
                Launcher.getLauncher(getContext()).getDeviceProfile().inv);
        rank = 0;
        for (int i = 0; i < itemCount; i++) {
            View v = rankOrderedList.size() > i ? rankOrderedList.get(i) : null;
            if (currentPage == null || position >= mMaxItemsPerPage) {
                // Next page
                if (pageItr.hasNext()) {
                    currentPage = pageItr.next();
                } else {
                    currentPage = createAndAddNewPage();
                }
                position = 0;
            }

            if (v != null) {
                CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
                getCellXYPositionForRank(rank, sTmpArray);
                newX = sTmpArray[0];
                newY = sTmpArray[1];

                ItemInfo info = (ItemInfo) v.getTag();
                if (info.cellX != newX || info.cellY != newY || info.rank != rank) {
                    info.cellX = newX;
                    info.cellY = newY;
                    info.rank = rank;
                    if (saveChanges) {
                        mFolder.mLauncher.getModelWriter().addOrMoveItemInDatabase(info,
                                mFolder.mInfo.id, 0, info.cellX, info.cellY);
                    }
                }
                lp.cellX = info.cellX;
                lp.cellY = info.cellY;
                currentPage.addViewToCellLayout(
                        v, -1, mFolder.mLauncher.getViewIdForItem(info), lp, true);

                if (verifier.isItemInPreview(rank) && v instanceof BubbleTextView) {
                    ((BubbleTextView) v).verifyHighRes();
                }
            }

            rank ++;
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

    public int getItemCount() {
        int lastPageIndex = getChildCount() - 1;
        if (lastPageIndex < 0) {
            // If there are no pages, nothing has yet been added to the folder.
            return 0;
        }
        return getPageAt(lastPageIndex).getShortcutsAndWidgets().getChildCount()
                + lastPageIndex * mMaxItemsPerPage;
    }

    /**
     * @return the rank of the cell nearest to the provided pixel position.
     */
    public int findNearestArea(int pixelX, int pixelY) {
        int pageIndex = getNextPage();
        CellLayout page = getPageAt(pageIndex);
        page.findNearestArea(pixelX, pixelY, 1, 1, sTmpArray);
        if (mFolder.isLayoutRtl()) {
            sTmpArray[0] = page.getCountX() - sTmpArray[0] - 1;
        }
        return Math.min(mAllocatedContentSize - 1,
                pageIndex * mMaxItemsPerPage + sTmpArray[1] * mGridCountX + sTmpArray[0]);
    }

    public boolean isFull() {
        return !ALLOW_FOLDER_SCROLL && getItemCount() >= mMaxItemsPerPage;
    }

    public View getFirstItem() {
        if (getChildCount() < 1) {
            return null;
        }
        ShortcutAndWidgetContainer currContainer = getCurrentCellLayout().getShortcutsAndWidgets();
        if (mGridCountX > 0) {
            return currContainer.getChildAt(0, 0);
        } else {
            return currContainer.getChildAt(0);
        }
    }

    public View getLastItem() {
        if (getChildCount() < 1) {
            return null;
        }
        ShortcutAndWidgetContainer currContainer = getCurrentCellLayout().getShortcutsAndWidgets();
        int lastRank = currContainer.getChildCount() - 1;
        if (mGridCountX > 0) {
            return currContainer.getChildAt(lastRank % mGridCountX, lastRank / mGridCountX);
        } else {
            return currContainer.getChildAt(lastRank);
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
            mScroller.setInterpolator(new DecelerateInterpolator());
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
        int p = rank / mMaxItemsPerPage;
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
                Drawable d = icon.getCompoundDrawables()[1];
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
        completePendingPageChanges();
        int delay = 0;
        float delayAmount = START_VIEW_REORDER_DELAY;

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
                    addViewForRankDuringReorder(v, (ShortcutInfo) v.getTag(), moveStart);
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
                            addViewForRankDuringReorder(v, (ShortcutInfo) v.getTag(), newRank);
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
            if (v != null) {
                ((ItemInfo) v.getTag()).rank -= direction;
            }
            if (page.animateChildToPosition(v, i % mGridCountX, i / mGridCountX,
                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                delay += delayAmount;
                delayAmount *= VIEW_REORDER_DELAY_FACTOR;
            }
        }
    }

    public int itemsPerPage() {
        return mMaxItemsPerPage;
    }

    /**
     * Returns the reading order position for a given rank.
     *
     * ie. For the permutation below, rank 0 returns 0, rank 1 returns 1, rank 4 returns 2,
     *                                rank 2 returns 3, rank 3 returns 4, rank 5 returns 5.
     *
     *     R0 R1 R4
     *     R2 R3 R5
     */
    public int getReadingOrderPosForRank(int rank) {
        if (rank >= mMaxItemsPerPage) {
            return rank;
        }

        getCellXYPositionForRank(rank, sTmpArray);
        return sTmpArray[0] + (mGridCountX * sTmpArray[1]);
    }

    /**
     * Returns the cell XY position for a Folder item with the given rank.
     */
    public void getCellXYPositionForRank(int rank, int[] outXY) {
        boolean onFirstPage = rank < mMaxItemsPerPage;

        if (onFirstPage && mGridCountX == 3) {
            outXY[0] = FolderPermutation.THREE_COLS[rank][0];
            outXY[1] = FolderPermutation.THREE_COLS[rank][1];
        } else if (onFirstPage && mGridCountX == 4) {
            outXY[0] = FolderPermutation.FOUR_COLS[rank][0];
            outXY[1] = FolderPermutation.FOUR_COLS[rank][1];
        } else if (onFirstPage && mGridCountX == 5) {
            outXY[0] = FolderPermutation.FIVE_COLS[rank][0];
            outXY[1] = FolderPermutation.FIVE_COLS[rank][1];
        } else {
            outXY[0] = (rank % mMaxItemsPerPage) % mGridCountX;
            outXY[1] = (rank % mMaxItemsPerPage) / mGridCountX;
        }
    }

    /**
     * Provides the mapping between a folder item's rank and its cell location, based on the
     * number of columns.
     *
     * We use this mapping, rather than the regular reading order, to preserve the items in the
     * upper left quadrant of the Folder. This allows a smooth transition between the FolderIcon
     * and the opened Folder.
     *
     * TODO: We will replace these hard coded tables with an algorithm b/62986680
     */
    private static class FolderPermutation {
        /**
         *  R0 R1 R4
         *  R2 R3 R5
         *  R6 R7 R8
         */
        static final int[][] THREE_COLS = new int[][] {
                {0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 0}, {2, 1}, {0, 2}, {1, 2}, {2, 2}
        };

        /**
         * R0  R1  R4  R6
         * R2  R3  R5  R7
         * R8  R9  R10 R11
         * R12 R13 R14 R15
         */
        static final int[][] FOUR_COLS = new int[][] {
                {0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 0}, {2, 1}, {3, 0}, {3, 1}, {0, 2}, {1, 2},
                {2, 2}, {3, 2}, {0, 3}, {1, 3}, {2, 3}, {3, 3}
        };

        /**
         * R0  R1  R4  R6  R12
         * R2  R3  R5  R7  R13
         * R8  R9  R10 R11 R14
         * R15 R16 R17 R18 R19
         * R20 R21 R22 R23 R24
         */
        static final int[][] FIVE_COLS = new int[][] {
                {0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 0}, {2, 1}, {3, 0}, {3, 1}, {0, 2}, {1, 2},
                {2, 2}, {3, 2}, {4, 0}, {4, 1}, {4, 2}, {0, 3}, {1, 3}, {2, 3}, {3, 3}, {4, 3},
                {0, 4}, {1, 4}, {2, 4}, {3, 4}, {4, 4}
        };
    }
}
