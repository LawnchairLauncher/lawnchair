package com.android.launcher3.folder;

/**
 * Layout rule for icons shown on a closed folder preview.
 *
 * {@link #MAX_NUM_ITEMS_IN_PREVIEW} is the absolute ceiling (3×3). The active preview count
 * (4 or 9) comes from the user preference and is applied via {@link #init}.
 */
public class ClippedFolderIconLayoutRule {

    /** Absolute maximum icons that can appear in a folder preview (3×3). */
    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 9;
    /** Default preview count when no preference is available (2×2). */
    public static final int DEFAULT_NUM_ITEMS_IN_PREVIEW = 4;
    public static final int DEFAULT_PREVIEW_GRID_SIDE = 2;

    private static final int MIN_NUM_ITEMS_FOR_SCALE = 2;

    public static final float MIN_SCALE = 0.44f;
    public static final float MAX_SCALE = 0.51f;
    /** Scale used when the preview grid is 3×3 so nine icons fit without overlap. */
    public static final float GRID_3X3_SCALE = 0.31f;

    private static final float MAX_RADIUS_DILATION = 0.25f;
    // The max amount of overlap the preview items can go outside of the background bounds.
    public static final float ICON_OVERLAP_FACTOR = 1 + (MAX_RADIUS_DILATION / 2f);

    public static final int EXIT_INDEX = -2;
    public static final int ENTER_INDEX = -3;

    private final float[] mTmpPoint = new float[2];

    private float mAvailableSpace;
    private float mIconSize;
    private boolean mIsRtl;
    private float mBaselineIconScale;
    private int mActivePreviewCount = DEFAULT_NUM_ITEMS_IN_PREVIEW;
    private int mPreviewGridSide = DEFAULT_PREVIEW_GRID_SIDE;

    /**
     * Initialize the layout rule.
     */
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl,
            int numFolderColumns) {
        init(availableSpace, intrinsicIconSize, rtl, numFolderColumns,
                DEFAULT_NUM_ITEMS_IN_PREVIEW, DEFAULT_PREVIEW_GRID_SIDE);
    }

    /**
     * Initialize the layout rule with an active preview grid from preferences.
     *
     * @param activePreviewCount number of icons shown on the closed folder (4 or 9)
     * @param previewGridSide    grid side length (2 or 3)
     */
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl,
            int numFolderColumns, int activePreviewCount, int previewGridSide) {
        mAvailableSpace = availableSpace;
        mIconSize = intrinsicIconSize;
        mIsRtl = rtl;
        mBaselineIconScale = availableSpace / intrinsicIconSize;
        mActivePreviewCount = Math.min(Math.max(activePreviewCount, MIN_NUM_ITEMS_FOR_SCALE),
                MAX_NUM_ITEMS_IN_PREVIEW);
        mPreviewGridSide = Math.max(previewGridSide, DEFAULT_PREVIEW_GRID_SIDE);
    }

    /** Active number of icons shown in the closed-folder preview. */
    public int getActivePreviewItemCount() {
        return mActivePreviewCount;
    }

    /** Side length of the active preview grid (2 or 3). */
    public int getPreviewGridSide() {
        return mPreviewGridSide;
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

        if (index == EXIT_INDEX) {
            // Past the trailing column on the top row.
            getGridPosition(0, mPreviewGridSide, totalScale, mTmpPoint);
        } else if (index == ENTER_INDEX) {
            // Past the trailing column on the bottom preview row.
            getGridPosition(mPreviewGridSide - 1, mPreviewGridSide, totalScale, mTmpPoint);
        } else if (index >= mActivePreviewCount) {
            // Items beyond those displayed in the preview are animated to the center
            mTmpPoint[0] = mTmpPoint[1] = mAvailableSpace / 2 - (mIconSize * totalScale) / 2;
        } else {
            getPosition(index, totalScale, mTmpPoint);
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
     * Indices below the active preview count use the closed-folder preview grid (reading order);
     * higher indices collapse to the preview center.
     */
    public PreviewItemDrawingParams computeSpringAnimationItemParams(int index, int numItemsInPage,
            int page, PreviewItemDrawingParams params) {
        int previewCount = Math.min(Math.max(numItemsInPage, 1), mActivePreviewCount);
        float totalScale = scaleForItem(previewCount, 0);
        float transX;
        float transY;

        if (index < mActivePreviewCount) {
            getPosition(index, totalScale, mTmpPoint);
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
    private void getPosition(int index, float iconScale, float[] result) {
        getGridPosition(index / mPreviewGridSide, index % mPreviewGridSide, iconScale, result);
    }

    /**
     * Calculate Scale for Preview Icons based on current page and number of items in page.
     * @param numItems number of items in page
     * @param page current page of Folder
     * @return scale for icons in Folder
     */
    public float scaleForItem(int numItems, int page) {
        float scale;
        if (mPreviewGridSide >= 3) {
            // Keep single/dual icons a bit larger; everything else uses the tight 3×3 scale.
            scale = numItems <= 2 ? MAX_SCALE : GRID_3X3_SCALE;
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
