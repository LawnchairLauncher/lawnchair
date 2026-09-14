package com.android.launcher3.folder;

import com.android.launcher3.Flags;

public class ClippedFolderIconLayoutRule {

    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 4;
    private static final int MIN_NUM_ITEMS_IN_PREVIEW = 2;

    public static final float MIN_SCALE = 0.44f;
    public static final float MAX_SCALE = 0.51f;

    // TODO: figure out exact radius for different icons
    private static final float MAX_RADIUS_DILATION = 0.25f;
    // The max amount of overlap the preview items can go outside of the background bounds.
    public static final float ICON_OVERLAP_FACTOR = 1 + (MAX_RADIUS_DILATION / 2f);
    private static final float ITEM_RADIUS_SCALE_FACTOR = 1.15f;
    private static final float ITEM_RADIUS_SCALE_FACTOR_SHAPES = 1.2f;

    public static final int EXIT_INDEX = -2;
    public static final int ENTER_INDEX = -3;

    private float[] mTmpPoint = new float[2];

    private float mAvailableSpace;
    private float mRadius;
    private float mIconSize;
    private boolean mIsRtl;
    private float mBaselineIconScale;
    private int mNumFolderColumns;

    /**
     * Sora: how a plate covering more than one cell lays its preview out.
     *
     * A one-cell folder huddles its first four apps into a circle, which is the
     * only thing that fits in the space an icon has. Give the folder more cells
     * and there is room to stop huddling: the apps go onto a grid, each at
     * something near the size it has on the workspace. Two cells get three
     * slots, four cells get four, and when the folder holds more apps than
     * there are slots the last slot quarters itself to carry the remainder --
     * so the preview says both what is in the folder and that there is more.
     */
    private static final int MINI_GRID = 2;

    /**
     * The margin round the block of apps and the gap between them, each stated
     * as a fraction of one app.
     *
     * Against the app, never against the plate. What the eye judges is the room
     * beside an icon, not the room beside a folder, so a margin stated against
     * the plate comes out nearly three times wider on a four-cell folder than
     * on a one-cell one.
     *
     * Stating them at all is the point. They used to be whatever was left over
     * once an icon had been centred in its slot, which meant the plate's edge
     * got one inset while the space between two icons got two: the margin was
     * always exactly half the gap, and the icons sat squashed against the
     * sides. These are the proportions the reference uses, the other way
     * round -- a margin a little wider than the gap.
     */
    private static final float PLATE_PADDING_PER_ICON = 0.19f;
    private static final float SLOT_GAP_PER_ICON = 0.15f;
    /**
     * Padding and gap for the 3x3 Default-mode preview on a 1x1 folder,
     * measured off an iOS 18 screenshot. The icons are noticeably smaller
     * than in a multi-cell plate, with wider margins all round.
     */
    private static final float DEFAULT_3x3_PADDING = 0.700f;
    private static final float DEFAULT_3x3_GAP = 0.170f;
    /** The gap inside the block of four, against one of its smaller apps. */
    private static final float MINI_GAP_PER_ICON = 18f / 78f;

    /**
     * How much of its slot the block of four actually fills.
     *
     * Not all of it. Measured off the reference, a slot 192 across holds four
     * apps of 78 with 18 between them -- 174 in all, leaving 9 either side. The
     * block used to be sized to fill the slot exactly, which made each of its
     * apps 86 where the reference has 78: about a ninth too big, and enough that
     * a folder holding more than it can show looked heavier than one that fits.
     */
    private static final float MINI_BLOCK_SPAN = 174f / 192f;

    private boolean mIsDefaultMode;
    private int mPlateWidth;
    private int mPlateHeight;
    private int mSlotCols = 1;
    private int mSlotRows = 1;
    /** The edge of one app. A slot is that plus the gap that follows it. */
    private float mIconSpan;
    private float mGapX;
    private float mGapY;
    private float mGridLeft;
    private float mGridTop;

    /**
     * initialize the layout rule
     */
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl,
            int numFolderColumns) {
        mAvailableSpace = availableSpace;
        mRadius = (
                Flags.enableLauncherIconShapes()
                        ? ITEM_RADIUS_SCALE_FACTOR_SHAPES
                        : ITEM_RADIUS_SCALE_FACTOR) * availableSpace / 2f;
        mIconSize = intrinsicIconSize;
        mIsRtl = rtl;
        mBaselineIconScale = availableSpace / intrinsicIconSize;
        mNumFolderColumns = numFolderColumns;
    }

    /**
     * Tells the rule the shape of the plate it is laying out on.
     *
     * Must be called after {@link #init}, which only ever hears about one cell.
     * A plate the size of that cell keeps the huddle; anything larger switches
     * to the grid.
     */
    public void initPlate(int plateWidth, int plateHeight, int cellSize) {
        initPlate(plateWidth, plateHeight, cellSize, false);
    }

    public void initPlate(int plateWidth, int plateHeight, int cellSize, boolean isDefaultMode) {
        mPlateWidth = plateWidth;
        mPlateHeight = plateHeight;
        mIsDefaultMode = isDefaultMode;

        boolean wide = plateWidth > cellSize;
        boolean tall = plateHeight > cellSize;
        if (wide && tall) {
            mSlotCols = 2;
            mSlotRows = 2;
        } else if (wide) {
            mSlotCols = 3;
            mSlotRows = 1;
        } else if (tall) {
            mSlotCols = 1;
            mSlotRows = 3;
        } else if (isDefaultMode) {
            mSlotCols = 3;
            mSlotRows = 3;
        } else {
            mSlotCols = 1;
            mSlotRows = 1;
            return;
        }

        // Which spacing constants to use. The 3x3 Default-mode preview on a
        // 1x1 folder uses wider margins to match the iOS reference.
        boolean is3x3Default = isDefaultMode && !wide && !tall;
        float padding = is3x3Default ? DEFAULT_3x3_PADDING : PLATE_PADDING_PER_ICON;
        float gap     = is3x3Default ? DEFAULT_3x3_GAP     : SLOT_GAP_PER_ICON;

        // The largest app that still leaves a full margin on all four sides and
        // a full gap between neighbours. Whichever axis runs out of room first
        // sets the size; apps stay square, so the other axis is left with slack
        // to place.
        mIconSpan = Math.min(
                iconSpanForAxis(plateWidth, mSlotCols, padding, gap),
                iconSpanForAxis(plateHeight, mSlotRows, padding, gap));

        float minGap = mIconSpan * gap;
        mGapX = gapAcross(plateWidth, mSlotCols, minGap, padding);
        mGapY = gapAcross(plateHeight, mSlotRows, minGap, padding);
        if (mSlotCols > 1 && mSlotRows > 1) {
            // Two gaps of visibly different widths read as a mistake rather
            // than a choice, so the tighter one wins and the slack goes back to
            // the margin, where a little extra reads as the block being
            // centred. An axis holding a single line has no partner to clash
            // with, which is why the tall and the wide plates may spread
            // instead of centring.
            float even = Math.min(mGapX, mGapY);
            mGapX = even;
            mGapY = even;
        }

        mGridLeft = (plateWidth - blockSpan(mSlotCols, mGapX)) / 2f;
        mGridTop = (plateHeight - blockSpan(mSlotRows, mGapY)) / 2f;
    }

    /** The app size that fits {@code count} of them across {@code plateSpan}. */
    private static float iconSpanForAxis(int plateSpan, int count,
            float padding, float gap) {
        return plateSpan
                / (count + gap * (count - 1) + 2 * padding);
    }

    /**
     * The gap that fills an axis once the margin and the apps have their share.
     *
     * Never tighter than the gap asked for. On the axis that set the app size
     * it comes out at exactly that; on the other it opens up to take the slack,
     * so a folder two cells tall spreads its three apps down the plate instead
     * of huddling them in the middle of it.
     */
    private float gapAcross(int plateSpan, int count, float minGap, float padding) {
        if (count <= 1) {
            return 0f;
        }
        float slack = plateSpan
                - 2 * mIconSpan * padding
                - count * mIconSpan;
        return Math.max(minGap, slack / (count - 1));
    }

    private float blockSpan(int count, float gap) {
        return count * mIconSpan + (count - 1) * gap;
    }

    /** Whether this plate lays its preview on a grid rather than in a huddle. */
    public boolean isPlateGrid() {
        return mSlotCols * mSlotRows > 1;
    }

    public int getSlotCount() {
        return mSlotCols * mSlotRows;
    }

    /**
     * How many of the folder's apps the preview can show.
     *
     * One per slot while they fit. Once they do not, the last slot is given
     * over to a block of four, so the count is every slot but the last, plus
     * those four.
     */
    public int getPreviewItemLimit() {
        int slots = getSlotCount();
        if (slots == 9) {
            return 9;
        }
        return slots <= 1 ? MAX_NUM_ITEMS_IN_PREVIEW : slots - 1 + MINI_GRID * MINI_GRID;
    }

    private PreviewItemDrawingParams computePlateItemParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        float left;
        float top;
        float iconSize;

        int slots = getSlotCount();
        // More apps than slots is what puts the block of four in the last one.
        // With exactly as many as there are slots each app gets one to itself,
        // which is why a four-app folder on four cells shows four whole icons
        // rather than three and a huddle.
        // In 9-slot (3x3) Default mode folders, all 9 slots hold whole icons.
        boolean overflowing = slots != 9 && curNumItems > slots;
        int wholeSlots = overflowing ? slots - 1 : slots;

        if (index < 0 || index >= getPreviewItemLimit()) {
            // The positions the enter and exit animations fly through, or items beyond
            // those displayed in the preview: animated to the center of the plate.
            iconSize = mIconSpan;
            left = (mPlateWidth - iconSize) / 2f;
            top = (mPlateHeight - iconSize) / 2f;
        } else if (index < wholeSlots) {
            iconSize = mIconSpan;
            left = slotLeft(index) + rowShift(index, curNumItems);
            top = slotTop(index) + blockShift(curNumItems);
        } else {
            int inner = Math.min(index - wholeSlots, MINI_GRID * MINI_GRID - 1);
            // The block of four sits centred in the room one app would have had,
            // a little smaller than that room rather than filling it.
            iconSize = mIconSpan * MINI_BLOCK_SPAN / (MINI_GRID + MINI_GAP_PER_ICON);
            float stride = iconSize * (1 + MINI_GAP_PER_ICON);
            float inset = mIconSpan * (1 - MINI_BLOCK_SPAN) / 2f;
            int col = inner % MINI_GRID;
            int row = inner / MINI_GRID;
            if (mIsRtl) {
                col = MINI_GRID - 1 - col;
            }
            left = slotLeft(wholeSlots) + inset + col * stride;
            top = slotTop(wholeSlots) + inset + row * stride;
        }

        float scale = iconSize / mIconSize;
        if (params == null) {
            return new PreviewItemDrawingParams(left, top, scale);
        }
        params.update(left, top, scale);
        return params;
    }

    /**
     * How far down the used rows sit when they do not fill the plate.
     *
     * A folder holding fewer apps than the plate has slots would otherwise
     * stack them against the top and leave the rest of the plate empty -- which
     * is what two apps on four cells looked like: both against the top edge,
     * the whole lower half bare. Centring the rows that are actually used keeps
     * the block in the middle of the plate whatever the folder holds.
     * In Default mode (iOS-like), slots remain fixed at their grid locations.
     */
    private float blockShift(int curNumItems) {
        if (mIsDefaultMode || curNumItems >= getSlotCount()) {
            return 0f;
        }
        int rowsUsed = Math.max(1, (curNumItems + mSlotCols - 1) / mSlotCols);
        return (mSlotRows - rowsUsed) * (mIconSpan + mGapY) / 2f;
    }

    /** The same, across a row that the apps ran out partway through. */
    private float rowShift(int slot, int curNumItems) {
        if (mIsDefaultMode || curNumItems >= getSlotCount()) {
            return 0f;
        }
        int row = slot / mSlotCols;
        int inRow = Math.min(mSlotCols, curNumItems - row * mSlotCols);
        return (mSlotCols - inRow) * (mIconSpan + mGapX) / 2f;
    }

    private float slotLeft(int slot) {
        int col = slot % mSlotCols;
        if (mIsRtl) {
            col = mSlotCols - 1 - col;
        }
        return mGridLeft + col * (mIconSpan + mGapX);
    }

    private float slotTop(int slot) {
        return mGridTop + (slot / mSlotCols) * (mIconSpan + mGapY);
    }

    /**
     * Computes positions for icons in Preview.
     *
     * @param index       index of icon in folder
     * @param curNumItems current number of preview items
     * @param params      params to update for icon
     */
    public PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        if (isPlateGrid()) {
            return computePlateItemParams(index, curNumItems, params);
        }
        float totalScale = scaleForItem(curNumItems, 0);
        float transX;
        float transY;

        if (index == EXIT_INDEX) {
            // 0 1 * <-- Exit position (row 0, col 2)
            // 2 3
            getGridPosition(0, 2, mTmpPoint);
        } else if (index == ENTER_INDEX) {
            // 0 1
            // 2 3 * <-- Enter position (row 1, col 2)
            getGridPosition(1, 2, mTmpPoint);
        } else if (index >= MAX_NUM_ITEMS_IN_PREVIEW) {
            // Items beyond those displayed in the preview are animated to the center
            mTmpPoint[0] = mTmpPoint[1] = mAvailableSpace / 2 - (mIconSize * totalScale) / 2;
        } else {
            getPosition(index, curNumItems, mTmpPoint);
        }

        transX = mTmpPoint[0];
        transY = mTmpPoint[1];

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale);
        } else {
            params.update(transX, transY, totalScale);
        }
        return params;
    }

    /**
     * Computes positions for icons in folder as part of spring animation. Here both preview icons
     * and the rest of the content icons are animated along the same grid.
     *
     * @param index          index of icon in folder
     * @param numItemsInPage current number of items in page
     * @param params         params to update for icon
     */
    public PreviewItemDrawingParams computeSpringAnimationItemParams(int index, int numItemsInPage,
            int page, PreviewItemDrawingParams params) {
        // The folder flies its apps out of the places the preview drew them, so
        // this has to agree with computePreviewItemDrawingParams about where
        // those places are. Left on the huddle, a grown folder opened its apps
        // out of a circle its preview had not used since it was given a second
        // cell.
        if (isPlateGrid() && page == 0) {
            return computePlateItemParams(index, numItemsInPage, params);
        }
        float totalScale = scaleForItem(numItemsInPage, page);
        float transX;
        float transY;

        if (numItemsInPage <= MAX_NUM_ITEMS_IN_PREVIEW) {
            getPosition(index, numItemsInPage, mTmpPoint);
        } else {
            getGridPosition(index / mNumFolderColumns, index % mNumFolderColumns, mTmpPoint);
        }

        transX = mTmpPoint[0];
        transY = mTmpPoint[1];

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale);
        } else {
            params.update(transX, transY, totalScale);
        }
        return params;
    }


    /**
     * Builds a grid based on the positioning of the items when there are
     * {@link #MAX_NUM_ITEMS_IN_PREVIEW} in the preview.
     *
     * Positions in the grid: 0 1  // 0 is row 0, col 1
     * 2 3  // 3 is row 1, col 1`
     */
    private void getGridPosition(int row, int col, float[] result) {
        // We use position 0 and 3 to calculate the x and y distances between items.
        getPosition(0, 4, result);
        float left = result[0];
        float top = result[1];

        getPosition(3, 4, result);
        float dx = result[0] - left;
        float dy = result[1] - top;

        result[0] = left + (col * dx);
        result[1] = top + (row * dy);
    }

    private void getPosition(int index, int curNumItems, float[] result) {
        // The case of two items is homomorphic to the case of one.
        curNumItems = Math.max(curNumItems, 2);

        // We model the preview as a circle of items starting in the appropriate piece of the
        // upper left quadrant (to achieve horizontal and vertical symmetry).
        double theta0 = mIsRtl ? 0 : Math.PI;

        // In RTL we go counterclockwise
        int direction = mIsRtl ? 1 : -1;

        double thetaShift = 0;
        if (curNumItems == 3) {
            thetaShift = Math.PI / 2;
        } else if (curNumItems == 4) {
            thetaShift = Math.PI / 4;
        }
        theta0 += direction * thetaShift;

        // We want the items to appear in reading order. For the case of 1, 2 and 3 items, this
        // is natural for the circular model. With 4 items, however, we need to swap the 3rd and
        // 4th indices to achieve reading order.
        if (curNumItems == 4 && index == 3) {
            index = 2;
        } else if (curNumItems == 4 && index == 2) {
            index = 3;
        }

        float radius = getRadius(curNumItems);
        double theta = theta0 + index * (2 * Math.PI / curNumItems) * direction;
        float halfIconSize = (mIconSize * scaleForItem(curNumItems, 0)) / 2;

        // Map the location along the circle, and offset the coordinates to represent the center
        // of the icon, and to be based from the top / left of the preview area. The y component
        // is inverted to match the coordinate system.
        result[0] = mAvailableSpace / 2 + (float) (radius * Math.cos(theta) / 2) - halfIconSize;
        result[1] = mAvailableSpace / 2 + (float) (-radius * Math.sin(theta) / 2) - halfIconSize;
    }

    private float getRadius(int numItems) {
        float radiusDilation = Flags.enableLauncherIconShapes() ? radiusDilationForItems(numItems)
                : MAX_RADIUS_DILATION;
        if (Flags.enableLauncherIconShapes()) {
            // Just give custom radius weights for each # of icons.
            return mRadius * (1 + radiusDilation);
        } else {
            // Increase radius from 0 up to MAX_RADIUS_DILATION as the number of items increases.
            return mRadius * (1 + radiusDilation * (numItems - MIN_NUM_ITEMS_IN_PREVIEW)
                    / (MAX_NUM_ITEMS_IN_PREVIEW - MIN_NUM_ITEMS_IN_PREVIEW));
        }
    }

    /**
     * Calculate Scale for Preview Icons based on current page and number of items in page.
     * @param numItems number of items in page
     * @param page current page of Folder
     * @return scale for icons in Folder
     */
    public float scaleForItem(int numItems, int page) {
        float scale;
        if (page > 0) {
            scale = MIN_SCALE;
        } else if (numItems <= 3) {
            scale = MAX_SCALE;
        } else {
            scale = MIN_SCALE;
        }
        return scale * mBaselineIconScale;
    }

    private float radiusDilationForItems(int numItems) {
        if (numItems == 3) {
            return 0.15f;
        } else if (numItems == MAX_NUM_ITEMS_IN_PREVIEW) {
            return 0.12f;
        } else {
            return 0;
        }
    }

    public float getIconSize() {
        return mIconSize;
    }
}
