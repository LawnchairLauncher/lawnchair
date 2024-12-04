/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TableRow;

/**
 * A row of {@link WidgetCell}s that can be displayed in a table.
 */
public class WidgetTableRow extends TableRow implements WidgetCell.PreviewReadyListener {
    private int mNumOfReadyCells;
    private int mNumOfCells;
    private int mResizeDelay;

    public WidgetTableRow(Context context) {
        super(context);
    }
    public WidgetTableRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onPreviewAvailable() {
        mNumOfReadyCells++;

        // Once all previews are loaded, find max visible height and adjust the preview containers.
        if (mNumOfReadyCells == mNumOfCells) {
            resize();
        }
    }

    private void resize() {
        int previewHeight = 0;
        // get the maximum height of each widget preview
        for (int i = 0; i < getChildCount(); i++) {
            WidgetCell widgetCell = (WidgetCell) getChildAt(i);
            previewHeight = Math.max(widgetCell.getPreviewContentHeight(), previewHeight);
        }
        if (mResizeDelay > 0) {
            postDelayed(() -> setAlpha(1f), mResizeDelay);
        }
        if (previewHeight > 0) {
            for (int i = 0; i < getChildCount(); i++) {
                WidgetCell widgetCell = (WidgetCell) getChildAt(i);
                widgetCell.setParentAlignedPreviewHeight(previewHeight);
                widgetCell.postDelayed(widgetCell::requestLayout, mResizeDelay);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    /**
     * Sets up the row to display the provided number of numOfCells.
     *
     * @param numOfCells    number of numOfCells in the row
     * @param resizeDelayMs time to wait in millis before making any layout size adjustments e.g. we
     *                      want to wait for list expand collapse animation before resizing the
     *                      cell previews.
     */
    public void setupRow(int numOfCells, int resizeDelayMs) {
        mNumOfCells = numOfCells;
        mNumOfReadyCells = 0;

        mResizeDelay = resizeDelayMs;
        // For delayed resize, reveal contents only after resize is done.
        if (mResizeDelay > 0) {
            setAlpha(0);
        }
    }
}
