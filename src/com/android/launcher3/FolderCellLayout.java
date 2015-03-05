package com.android.launcher3;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.android.launcher3.Workspace.ItemOperator;

import java.util.ArrayList;

public class FolderCellLayout extends CellLayout implements Folder.FolderContent {

    private static final int REORDER_ANIMATION_DURATION = 230;
    private static final int START_VIEW_REORDER_DELAY = 30;
    private static final float VIEW_REORDER_DELAY_FACTOR = 0.9f;

    private static final int[] sTempPosArray = new int[2];

    private final FolderKeyEventListener mKeyListener = new FolderKeyEventListener();
    private final LayoutInflater mInflater;
    private final IconCache mIconCache;

    private final int mMaxCountX;
    private final int mMaxCountY;
    private final int mMaxNumItems;

    // Indicates the last number of items used to set up the grid size
    private int mAllocatedContentSize;

    private Folder mFolder;
    private FocusIndicatorView mFocusIndicatorView;

    public FolderCellLayout(Context context) {
        this(context, null);
    }

    public FolderCellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FolderCellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        mMaxCountX = (int) grid.numColumns;
        mMaxCountY = (int) grid.numRows;
        mMaxNumItems = mMaxCountX * mMaxCountY;

        mInflater = LayoutInflater.from(context);
        mIconCache = app.getIconCache();

        setCellDimensions(grid.folderCellWidthPx, grid.folderCellHeightPx);
        getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        setInvertIfRtl(true);
    }

    @Override
    public void setFolder(Folder folder) {
        mFolder = folder;
        mFocusIndicatorView = (FocusIndicatorView) folder.findViewById(R.id.focus_indicator);
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     * The grid size is calculated such that countY <= countX and countX = ceil(sqrt(count)) while
     * maintaining the restrictions of {@link #mMaxCountX} &amp; {@link #mMaxCountY}.
     */
    private void setupContentDimensions(int count) {
        mAllocatedContentSize = count;
        int countX = getCountX();
        int countY = getCountY();
        boolean done = false;

        while (!done) {
            int oldCountX = countX;
            int oldCountY = countY;
            if (countX * countY < count) {
                // Current grid is too small, expand it
                if ((countX <= countY || countY == mMaxCountY) && countX < mMaxCountX) {
                    countX++;
                } else if (countY < mMaxCountY) {
                    countY++;
                }
                if (countY == 0) countY++;
            } else if ((countY - 1) * countX >= count && countY >= countX) {
                countY = Math.max(0, countY - 1);
            } else if ((countX - 1) * countY >= count) {
                countX = Math.max(0, countX - 1);
            }
            done = countX == oldCountX && countY == oldCountY;
        }
        setGridSize(countX, countY);
    }

    @Override
    public ArrayList<ShortcutInfo> bindItems(ArrayList<ShortcutInfo> items) {
        ArrayList<ShortcutInfo> extra = new ArrayList<ShortcutInfo>();
        setupContentDimensions(Math.min(items.size(), mMaxNumItems));

        int countX = getCountX();
        int rank = 0;
        for (ShortcutInfo item : items) {
            if (rank >= mMaxNumItems) {
                extra.add(item);
                continue;
            }

            item.rank = rank;
            item.cellX = rank % countX;
            item.cellY = rank / countX;
            addNewView(item);
            rank++;
        }
        return extra;
    }

    @Override
    public int allocateNewLastItemRank() {
        int rank = getItemCount();
        mFolder.rearrangeChildren(rank + 1);
        return rank;
    }

    @Override
    public View createAndAddViewForRank(ShortcutInfo item, int rank) {
        updateItemXY(item, rank);
        return addNewView(item);
    }

    @Override
    public void addViewForRank(View view, ShortcutInfo item, int rank) {
        updateItemXY(item, rank);
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();
        lp.cellX = item.cellX;
        lp.cellY = item.cellY;
        addViewToCellLayout(view, -1, mFolder.mLauncher.getViewIdForItem(item), lp, true);
    }

    /**
     * Updates the item cellX and cellY position
     */
    private void updateItemXY(ShortcutInfo item, int rank) {
        item.rank = rank;
        int countX = getCountX();
        item.cellX = rank % countX;
        item.cellY = rank / countX;
    }

    private View addNewView(ShortcutInfo item) {
        final BubbleTextView textView = (BubbleTextView) mInflater.inflate(
                R.layout.folder_application, getShortcutsAndWidgets(), false);
        textView.applyFromShortcutInfo(item, mIconCache, false);
        textView.setOnClickListener(mFolder);
        textView.setOnLongClickListener(mFolder);
        textView.setOnFocusChangeListener(mFocusIndicatorView);
        textView.setOnKeyListener(mKeyListener);

        CellLayout.LayoutParams lp = new CellLayout.LayoutParams(
                item.cellX, item.cellY, item.spanX, item.spanY);
        addViewToCellLayout(textView, -1, mFolder.mLauncher.getViewIdForItem(item), lp, true);
        return textView;
    }

    /**
     * Refer {@link #findNearestArea(int, int, int, int, View, boolean, int[])}
     */
    @Override
    public int findNearestArea(int pixelX, int pixelY) {
        findNearestArea(pixelX, pixelY, 1, 1, null, false, sTempPosArray);
        if (mFolder.isLayoutRtl()) {
            sTempPosArray[0] = getCountX() - sTempPosArray[0] - 1;
        }

        // Convert this position to rank.
        return Math.min(mAllocatedContentSize - 1,
                sTempPosArray[1] * getCountX() + sTempPosArray[0]);
    }

    @Override
    public boolean isFull() {
        return getItemCount() >= mMaxNumItems;
    }

    @Override
    public int getItemCount() {
        return getShortcutsAndWidgets().getChildCount();
    }

    @Override
    public void arrangeChildren(ArrayList<View> list, int itemCount) {
        setupContentDimensions(itemCount);
        removeAllViews();

        int newX, newY;
        int rank = 0;
        int countX = getCountX();
        for (View v : list) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
            newX = rank % countX;
            newY = rank / countX;
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
            addViewToCellLayout(v, -1, mFolder.mLauncher.getViewIdForItem(info), lp, true);
        }
    }

    @Override
    public View iterateOverItems(ItemOperator op) {
        for (int j = 0; j < getCountY(); j++) {
            for (int i = 0; i < getCountX(); i++) {
                View v = getChildAt(i, j);
                if ((v != null) && op.evaluate((ItemInfo) v.getTag(), v, this)) {
                    return v;
                }
            }
        }
        return null;
    }

    @Override
    public String getAccessibilityDescription() {
        return String.format(getContext().getString(R.string.folder_opened),
                getCountX(), getCountY());
    }

    @Override
    public void setFocusOnFirstChild() {
        View firstChild = getChildAt(0, 0);
        if (firstChild != null) {
            firstChild.requestFocus();
        }
    }

    @Override
    public View getLastItem() {
        int lastRank = getShortcutsAndWidgets().getChildCount() - 1;
        return getShortcutsAndWidgets().getChildAt(lastRank % getCountX(), lastRank / getCountX());
    }

    @Override
    public void realTimeReorder(int empty, int target) {
        boolean wrap;
        int startX;
        int endX;
        int startY;
        int delay = 0;
        float delayAmount = START_VIEW_REORDER_DELAY;

        int countX = getCountX();
        int emptyX = empty % getCountX();
        int emptyY = empty / countX;

        int targetX = target % countX;
        int targetY = target / countX;

        if (target > empty) {
            wrap = emptyX == countX - 1;
            startY = wrap ? emptyY + 1 : emptyY;
            for (int y = startY; y <= targetY; y++) {
                startX = y == emptyY ? emptyX + 1 : 0;
                endX = y < targetY ? countX - 1 : targetX;
                for (int x = startX; x <= endX; x++) {
                    View v = getChildAt(x,y);
                    if (animateChildToPosition(v, emptyX, emptyY,
                            REORDER_ANIMATION_DURATION, delay, true, true)) {
                        emptyX = x;
                        emptyY = y;
                        delay += delayAmount;
                        delayAmount *= VIEW_REORDER_DELAY_FACTOR;
                    }
                }
            }
        } else {
            wrap = emptyX == 0;
            startY = wrap ? emptyY - 1 : emptyY;
            for (int y = startY; y >= targetY; y--) {
                startX = y == emptyY ? emptyX - 1 : countX - 1;
                endX = y > targetY ? 0 : targetX;
                for (int x = startX; x >= endX; x--) {
                    View v = getChildAt(x,y);
                    if (animateChildToPosition(v, emptyX, emptyY,
                            REORDER_ANIMATION_DURATION, delay, true, true)) {
                        emptyX = x;
                        emptyY = y;
                        delay += delayAmount;
                        delayAmount *= VIEW_REORDER_DELAY_FACTOR;
                    }
                }
            }
        }
    }
}
