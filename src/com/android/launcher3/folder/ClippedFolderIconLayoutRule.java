package com.android.launcher3.folder;

import com.android.launcher3.Flags;

/**
 * Layout rule for icons shown on a closed folder preview.
 *
 * {@link #MAX_NUM_ITEMS_IN_PREVIEW} is the absolute ceiling (3×3). The active preview count
 * (4 or 9) comes from the user preference and is applied via {@link #init}.
 * When circular layout is enabled ("Default"), positions follow Launcher3's circle/triangle model.
 */
public class ClippedFolderIconLayoutRule {

    /** Absolute maximum icons that can appear in a folder preview (3×3). */
    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 9;
    /** Default preview count when no preference is available (2×2). */
    public static final int DEFAULT_NUM_ITEMS_IN_PREVIEW = 4;
    public static final int DEFAULT_PREVIEW_GRID_SIDE = 2;
    /** Cap used by Launcher3's circular preview model. */
    private static final int CIRCULAR_MAX_NUM_ITEMS_IN_PREVIEW = 4;

    private static final int MIN_NUM_ITEMS_FOR_SCALE = 2;
    private static final int MIN_NUM_ITEMS_IN_PREVIEW = 2;

    public static final float MIN_SCALE = 0.44f;
    public static final float MAX_SCALE = 0.51f;
    /** Scale used when the preview grid is 3×3 so nine icons fit without overlap. */
    public static final float GRID_3X3_SCALE = 0.31f;

    private static final float MAX_RADIUS_DILATION = 0.25f;
    // The max amount of overlap the preview items can go outside of the background bounds.
    public static final float ICON_OVERLAP_FACTOR = 1 + (MAX_RADIUS_DILATION / 2f);
    private static final float ITEM_RADIUS_SCALE_FACTOR = 1.15f;
    private static final float ITEM_RADIUS_SCALE_FACTOR_SHAPES = 1.2f;

    public static final int EXIT_INDEX = -2;
    public static final int ENTER_INDEX = -3;

    private final float[] mTmpPoint = new float[2];

    private float mAvailableSpace;
    private float mRadius;
    private float mIconSize;
    private boolean mIsRtl;
    private float mBaselineIconScale;
    private int mNumFolderColumns;
    private int mActivePreviewCount = DEFAULT_NUM_ITEMS_IN_PREVIEW;
    private int mPreviewGridSide = DEFAULT_PREVIEW_GRID_SIDE;
    private boolean mUseCircularLayout;

    /**
     * Initialize the layout rule.
     */
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl,
            int numFolderColumns) {
        // Prefer circular/triangle placement when no preference is supplied (Launcher3 default).
        init(availableSpace, intrinsicIconSize, rtl, numFolderColumns,
                DEFAULT_NUM_ITEMS_IN_PREVIEW, DEFAULT_PREVIEW_GRID_SIDE, true);
    }

    /**
     * Initialize the layout rule with an active preview grid from preferences.
     *
     * @param activePreviewCount number of icons shown on the closed folder (4 or 9)
     * @param previewGridSide    grid side length (2 or 3)
     * @param useCircularLayout  when true, use Launcher3 circle/triangle positions
     */
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl,
            int numFolderColumns, int activePreviewCount, int previewGridSide,
            boolean useCircularLayout) {
        mAvailableSpace = availableSpace;
        mRadius = (
                Flags.enableLauncherIconShapes()
                        ? ITEM_RADIUS_SCALE_FACTOR_SHAPES
                        : ITEM_RADIUS_SCALE_FACTOR) * availableSpace / 2f;
        mIconSize = intrinsicIconSize;
        mIsRtl = rtl;
        mBaselineIconScale = availableSpace / intrinsicIconSize;
        mNumFolderColumns = numFolderColumns;
        mUseCircularLayout = useCircularLayout;
        if (useCircularLayout) {
            mActivePreviewCount = CIRCULAR_MAX_NUM_ITEMS_IN_PREVIEW;
            mPreviewGridSide = DEFAULT_PREVIEW_GRID_SIDE;
        } else {
            mActivePreviewCount = Math.min(Math.max(activePreviewCount, MIN_NUM_ITEMS_FOR_SCALE),
                    MAX_NUM_ITEMS_IN_PREVIEW);
            mPreviewGridSide = Math.max(previewGridSide, DEFAULT_PREVIEW_GRID_SIDE);
        }
    }

    /** Active number of icons shown in the closed-folder preview. */
    public int getActivePreviewItemCount() {
        return mActivePreviewCount;
    }

    /** Side length of the active preview grid (2 or 3). */
    public int getPreviewGridSide() {
        return mPreviewGridSide;
    }

    /** Whether Launcher3 circular/triangle preview layout is active. */
    public boolean useCircularLayout() {
        return mUseCircularLayout;
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
        float totalScale = scaleForItem(curNumItems, 0);
        float transX;
        float transY;

        if (mUseCircularLayout) {
            if (index == EXIT_INDEX) {
                // 0 1 * <-- Exit position (row 0, col 2)
                // 2 3
                getCircularGridPosition(0, 2, mTmpPoint);
            } else if (index == ENTER_INDEX) {
                // 0 1
                // 2 3 * <-- Enter position (row 1, col 2)
                getCircularGridPosition(1, 2, mTmpPoint);
            } else if (index >= CIRCULAR_MAX_NUM_ITEMS_IN_PREVIEW) {
                mTmpPoint[0] = mTmpPoint[1] = mAvailableSpace / 2 - (mIconSize * totalScale) / 2;
            } else {
                getCircularPosition(index, curNumItems, mTmpPoint);
            }
        } else if (index == EXIT_INDEX) {
            // Past the trailing column on the top row.
            getGridPosition(0, mPreviewGridSide, totalScale, mTmpPoint);
        } else if (index == ENTER_INDEX) {
            // Past the trailing column on the bottom preview row.
            getGridPosition(mPreviewGridSide - 1, mPreviewGridSide, totalScale, mTmpPoint);
        } else if (index >= mActivePreviewCount) {
            // Items beyond those displayed in the preview are animated to the center
            mTmpPoint[0] = mTmpPoint[1] = mAvailableSpace / 2 - (mIconSize * totalScale) / 2;
        } else {
            getGridItemPosition(index, totalScale, mTmpPoint);
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
     * Computes positions for icons in folder as part of spring animation.
     * Indices below the active preview count use the closed-folder preview layout;
     * higher indices collapse to the preview center (grid mode) or follow the open-folder grid
     * (circular mode with more than four items).
     */
    public PreviewItemDrawingParams computeSpringAnimationItemParams(int index, int numItemsInPage,
            int page, PreviewItemDrawingParams params) {
        float totalScale = scaleForItem(
                mUseCircularLayout ? numItemsInPage
                        : Math.min(Math.max(numItemsInPage, 1), mActivePreviewCount),
                page);
        float transX;
        float transY;

        if (mUseCircularLayout) {
            if (numItemsInPage <= CIRCULAR_MAX_NUM_ITEMS_IN_PREVIEW) {
                getCircularPosition(index, numItemsInPage, mTmpPoint);
            } else {
                getCircularGridPosition(index / mNumFolderColumns, index % mNumFolderColumns,
                        mTmpPoint);
            }
        } else if (index < mActivePreviewCount) {
            getGridItemPosition(index, totalScale, mTmpPoint);
        } else {
            mTmpPoint[0] = mTmpPoint[1] = mAvailableSpace / 2 - (mIconSize * totalScale) / 2;
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
     * Places an icon in the preview (or extended) grid.
     *
     * Columns past {@link #mPreviewGridSide} are used for ENTER/EXIT animations.
     * RTL mirrors columns so reading order stays start-to-end.
     */
    private void getGridPosition(int row, int col, float iconScale, float[] result) {
        float iconSize = mIconSize * iconScale;
        float cell = mAvailableSpace / (float) mPreviewGridSide;
        int visualCol = mIsRtl ? (mPreviewGridSide - 1 - col) : col;
        result[0] = visualCol * cell + (cell - iconSize) / 2f;
        result[1] = row * cell + (cell - iconSize) / 2f;
    }

    /** Fills the active preview grid left-to-right / top-to-bottom. */
    private void getGridItemPosition(int index, float iconScale, float[] result) {
        getGridPosition(index / mPreviewGridSide, index % mPreviewGridSide, iconScale, result);
    }

    /**
     * Builds a spacing grid from Launcher3's 4-item circular preview positions.
     */
    private void getCircularGridPosition(int row, int col, float[] result) {
        getCircularPosition(0, 4, result);
        float left = result[0];
        float top = result[1];

        getCircularPosition(3, 4, result);
        float dx = result[0] - left;
        float dy = result[1] - top;

        result[0] = left + (col * dx);
        result[1] = top + (row * dy);
    }

    /** Launcher3 circular/triangle placement for closed-folder previews. */
    private void getCircularPosition(int index, int curNumItems, float[] result) {
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
        if (curNumItems == 3) {
            // Triangle centers sit at -R/2 (top) and +R/4 (bottom). Shift by R/8 so the
            // bbox is vertically centered like the 2x2 square (±R√2/4).
            result[1] += radius / 8f;
        }
    }

    private float getRadius(int numItems) {
        if (numItems == 3) {
            // After the R/8 centering nudge, triangle centers span 3R/4. Match that to the
            // 2x2 square span R√2/2 ⇒ radius3 = radius4 * (2√2 / 3).
            return getRadius(CIRCULAR_MAX_NUM_ITEMS_IN_PREVIEW)
                    * (float) (2.0 * Math.sqrt(2.0) / 3.0);
        }
        float radiusDilation = Flags.enableLauncherIconShapes() ? radiusDilationForItems(numItems)
                : MAX_RADIUS_DILATION;
        if (Flags.enableLauncherIconShapes()) {
            // Just give custom radius weights for each # of icons.
            return mRadius * (1 + radiusDilation);
        } else {
            // Increase radius from 0 up to MAX_RADIUS_DILATION as the number of items increases.
            return mRadius * (1 + radiusDilation * (numItems - MIN_NUM_ITEMS_IN_PREVIEW)
                    / (CIRCULAR_MAX_NUM_ITEMS_IN_PREVIEW - MIN_NUM_ITEMS_IN_PREVIEW));
        }
    }

    private float radiusDilationForItems(int numItems) {
        if (numItems == CIRCULAR_MAX_NUM_ITEMS_IN_PREVIEW) {
            return 0.12f;
        } else {
            return 0;
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
        if (mUseCircularLayout) {
            if (page > 0) {
                scale = MIN_SCALE;
            } else if (numItems <= 2) {
                // 1–2 items stay larger. 3-item previews match 4-item folders (MIN_SCALE);
                // MAX_SCALE at 3 items clips the triangle against the folder circle.
                scale = MAX_SCALE;
            } else {
                scale = MIN_SCALE;
            }
        } else if (mPreviewGridSide >= 3) {
            // Keep a lone icon larger; two+ icons use the tight 3×3 scale so adjacent
            // cells do not overlap (MAX_SCALE is wider than a 1/3 preview cell).
            scale = numItems <= 1 ? MAX_SCALE : GRID_3X3_SCALE;
        } else if (page > 0) {
            scale = MIN_SCALE;
        } else if (numItems <= 2) {
            scale = MAX_SCALE;
        } else {
            scale = MIN_SCALE;
        }
        return scale * mBaselineIconScale;
    }

    public float getIconSize() {
        return mIconSize;
    }
}
