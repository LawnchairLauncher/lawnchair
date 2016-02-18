package com.android.launcher3.folder;

import android.graphics.Path;
import android.graphics.Point;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;

public class ClippedFolderIconLayoutRule implements FolderIcon.PreviewLayoutRule {

    static final int MAX_NUM_ITEMS_IN_PREVIEW = 4;
    private static final int MIN_NUM_ITEMS_IN_PREVIEW = 2;

    final float MIN_SCALE = 0.48f;
    final float MAX_SCALE = 0.58f;
    final float MAX_RADIUS_DILATION = 0.15f;

    private float[] mTmpPoint = new float[2];

    private float mAvailableSpace;
    private float mRadius;
    private float mIconSize;
    private boolean mIsRtl;
    private Path mClipPath = new Path();

    @Override
    public void init(int availableSpace, int intrinsicIconSize, boolean rtl) {
        mAvailableSpace = availableSpace;
        mRadius = 0.66f * availableSpace;
        mIconSize = intrinsicIconSize;
        mIsRtl = rtl;

        // We make the clip radius just slightly smaller than the background drawable
        // TODO(adamcohen): this is hacky, needs cleanup (likely through programmatic drawing).
        int clipRadius = (int) mAvailableSpace / 2 - 1;

        mClipPath.addCircle(mAvailableSpace / 2, mAvailableSpace / 2, clipRadius, Path.Direction.CW);
    }

    @Override
    public FolderIcon.PreviewItemDrawingParams computePreviewItemDrawingParams(int index,
            int curNumItems, FolderIcon.PreviewItemDrawingParams params) {

        getPosition(index, curNumItems, mTmpPoint);

        float transX = mTmpPoint[0];
        float transY = mTmpPoint[1];
        float totalScale = scaleForNumItems(curNumItems);
        float overlayAlpha = 0;

        if (params == null) {
            params = new FolderIcon.PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.transX = transX;
            params.transY = transY;
            params.scale = totalScale;
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

        float halfIconSize = (mIconSize * scaleForNumItems(curNumItems)) / 2;

        // Map the location along the circle, and offset the coordinates to represent the center
        // of the icon, and to be based from the top / left of the preview area. The y component
        // is inverted to match the coordinate system.
        result[0] = mAvailableSpace / 2 + (float) (radius * Math.cos(theta) / 2) - halfIconSize;
        result[1] = mAvailableSpace / 2 + (float) (- radius * Math.sin(theta) / 2) - halfIconSize;

    }

    private float scaleForNumItems(int numItems) {
        if (numItems <= 2) {
            return MAX_SCALE;
        } else if (numItems == 3) {
            return (MAX_SCALE + MIN_SCALE) / 2;
        } else {
            return MIN_SCALE;
        }
    }

    @Override
    public int numItems() {
        return MAX_NUM_ITEMS_IN_PREVIEW;
    }

    @Override
    public Path getClipPath() {
        return mClipPath;
    }

}
