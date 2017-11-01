package com.android.launcher3.folder;

import android.view.View;

import com.android.launcher3.config.FeatureFlags;

import java.util.ArrayList;
import java.util.List;

public class ClippedFolderIconLayoutRule implements FolderIcon.PreviewLayoutRule {

    static final int MAX_NUM_ITEMS_IN_PREVIEW = 4;
    private static final int MIN_NUM_ITEMS_IN_PREVIEW = 2;
    private static final int MAX_NUM_ITEMS_PER_ROW = 2;

    final float MIN_SCALE = 0.48f;
    final float MAX_SCALE = 0.58f;
    final float MAX_RADIUS_DILATION = 0.15f;
    final float ITEM_RADIUS_SCALE_FACTOR = 1.33f;

    private float[] mTmpPoint = new float[2];

    private float mAvailableSpace;
    private float mRadius;
    private float mIconSize;
    private boolean mIsRtl;
    private float mBaselineIconScale;

    @Override
    public void init(int availableSpace, int intrinsicIconSize, boolean rtl) {
        mAvailableSpace = availableSpace;
        mRadius = ITEM_RADIUS_SCALE_FACTOR * availableSpace / 2f;
        mIconSize = intrinsicIconSize;
        mIsRtl = rtl;
        mBaselineIconScale = availableSpace / (intrinsicIconSize * 1f);
    }

    @Override
    public FolderIcon.PreviewItemDrawingParams computePreviewItemDrawingParams(int index,
            int curNumItems, FolderIcon.PreviewItemDrawingParams params) {

        float totalScale = scaleForItem(index, curNumItems);
        float transX;
        float transY;
        float overlayAlpha = 0;

        // Items beyond those displayed in the preview are animated to the center
        if (index >= MAX_NUM_ITEMS_IN_PREVIEW) {
            transX = transY = mAvailableSpace / 2 - (mIconSize * totalScale) / 2;
        } else {
            getPosition(index, curNumItems, mTmpPoint);
            transX = mTmpPoint[0];
            transY = mTmpPoint[1];
        }

        if (params == null) {
            params = new FolderIcon.PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.update(transX, transY, totalScale);
            params.overlayAlpha = overlayAlpha;
        }
        return params;
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
            thetaShift = Math.PI / 6;
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

        // We bump the radius up between 0 and MAX_RADIUS_DILATION % as the number of items increase
        float radius = mRadius * (1 + MAX_RADIUS_DILATION * (curNumItems -
                MIN_NUM_ITEMS_IN_PREVIEW) / (MAX_NUM_ITEMS_IN_PREVIEW - MIN_NUM_ITEMS_IN_PREVIEW));
        double theta = theta0 + index * (2 * Math.PI / curNumItems) * direction;

        float halfIconSize = (mIconSize * scaleForItem(index, curNumItems)) / 2;

        // Map the location along the circle, and offset the coordinates to represent the center
        // of the icon, and to be based from the top / left of the preview area. The y component
        // is inverted to match the coordinate system.
        result[0] = mAvailableSpace / 2 + (float) (radius * Math.cos(theta) / 2) - halfIconSize;
        result[1] = mAvailableSpace / 2 + (float) (- radius * Math.sin(theta) / 2) - halfIconSize;

    }

    @Override
    public float scaleForItem(int index, int numItems) {
        // Scale is determined by the number of items in the preview.
        float scale = 1f;
        if (numItems <= 2) {
            scale = MAX_SCALE;
        } else if (numItems == 3) {
            scale = (MAX_SCALE + MIN_SCALE) / 2;
        } else {
            scale = MIN_SCALE;
        }

        return scale * mBaselineIconScale;
    }

    @Override
    public int maxNumItems() {
        return MAX_NUM_ITEMS_IN_PREVIEW;
    }

    @Override
    public boolean clipToBackground() {
        return true;
    }

    @Override
    public List<View> getItemsToDisplay(Folder folder) {
        List<View> items = new ArrayList<>(folder.getItemsInReadingOrder());
        int numItems = items.size();
        if (FeatureFlags.LAUNCHER3_NEW_FOLDER_ANIMATION && numItems > MAX_NUM_ITEMS_IN_PREVIEW) {
            // We match the icons in the preview with the layout of the opened folder (b/27944225),
            // but we still need to figure out how we want to handle updating the preview when the
            // upper left quadrant changes.
            int appsPerRow = folder.mContent.getPageAt(0).getCountX();
            int appsToDelete = appsPerRow - MAX_NUM_ITEMS_PER_ROW;

            // We only display the upper left quadrant.
            while (appsToDelete > 0) {
                items.remove(MAX_NUM_ITEMS_PER_ROW);
                appsToDelete--;
            }
        }
        return items.subList(0, Math.min(numItems, MAX_NUM_ITEMS_IN_PREVIEW));
    }
}
