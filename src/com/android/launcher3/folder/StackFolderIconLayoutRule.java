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

package com.android.launcher3.folder;

public class StackFolderIconLayoutRule implements FolderIcon.PreviewLayoutRule {

    static final int MAX_NUM_ITEMS_IN_PREVIEW = 3;

    // The degree to which the item in the back of the stack is scaled [0...1]
    // (0 means it's not scaled at all, 1 means it's scaled to nothing)
    private static final float PERSPECTIVE_SCALE_FACTOR = 0.35f;

    // The amount of vertical spread between items in the stack [0...1]
    private static final float PERSPECTIVE_SHIFT_FACTOR = 0.18f;

    private float mBaselineIconScale;
    private int mBaselineIconSize;
    private int mAvailableSpaceInPreview;
    private float mMaxPerspectiveShift;

    @Override
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl) {
        mAvailableSpaceInPreview = availableSpace;

        // cos(45) = 0.707  + ~= 0.1) = 0.8f
        int adjustedAvailableSpace = (int) ((mAvailableSpaceInPreview / 2) * (1 + 0.8f));

        int unscaledHeight = (int) (intrinsicIconSize * (1 + PERSPECTIVE_SHIFT_FACTOR));

        mBaselineIconScale = (1.0f * adjustedAvailableSpace / unscaledHeight);

        mBaselineIconSize = (int) (intrinsicIconSize * mBaselineIconScale);
        mMaxPerspectiveShift = mBaselineIconSize * PERSPECTIVE_SHIFT_FACTOR;
    }

    @Override
    public PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        float scale = scaleForItem(index, curNumItems);

        index = MAX_NUM_ITEMS_IN_PREVIEW - index - 1;
        float r = (index * 1.0f) / (MAX_NUM_ITEMS_IN_PREVIEW - 1);

        float offset = (1 - r) * mMaxPerspectiveShift;
        float scaledSize = scale * mBaselineIconSize;
        float scaleOffsetCorrection = (1 - scale) * mBaselineIconSize;

        // We want to imagine our coordinates from the bottom left, growing up and to the
        // right. This is natural for the x-axis, but for the y-axis, we have to invert things.
        float transY = mAvailableSpaceInPreview - (offset + scaledSize + scaleOffsetCorrection);
        float transX = (mAvailableSpaceInPreview - scaledSize) / 2;
        float totalScale = mBaselineIconScale * scale;
        final float overlayAlpha = (80 * (1 - r)) / 255f;

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.update(transX, transY, totalScale);
            params.overlayAlpha = overlayAlpha;
        }
        return params;
    }

    @Override
    public int maxNumItems() {
        return MAX_NUM_ITEMS_IN_PREVIEW;
    }

    @Override
    public float getIconSize() {
        return mBaselineIconSize;
    }

    @Override
    public float scaleForItem(int index, int numItems) {
        // Scale is determined by the position of the icon in the preview.
        index = MAX_NUM_ITEMS_IN_PREVIEW - index - 1;
        float r = (index * 1.0f) / (MAX_NUM_ITEMS_IN_PREVIEW - 1);
        return (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));
    }

    @Override
    public boolean clipToBackground() {
        return false;
    }

    @Override
    public boolean hasEnterExitIndices() {
        return false;
    }

    @Override
    public int getExitIndex() {
        throw new RuntimeException("hasEnterExitIndices not supported");
    }

    @Override
    public int getEnterIndex() {
        throw new RuntimeException("hasEnterExitIndices not supported");
    }
}
