/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.graphics;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Workspace;

/**
 * Scrim drawn during SpringLoaded State (ie. Drag and Drop). Darkens the workspace except for
 * the focused CellLayout.
 */
public class WorkspaceDragScrim extends Scrim {

    private final Rect mHighlightRect = new Rect();

    private Workspace mWorkspace;

    public WorkspaceDragScrim(View view) {
        super(view);
        onExtractedColorsChanged(mWallpaperColorInfo);
    }

    /**
     * Set the workspace that this scrim is acting on
     * @param workspace
     */
    public void setWorkspace(Workspace workspace)  {
        mWorkspace = workspace;
        mWorkspace.setWorkspaceDragScrim(this);
    }

    /**
     * Cut out the focused paged of the Workspace and then draw the scrim
     * @param canvas
     */
    public void draw(Canvas canvas) {
        // Draw the background below children.
        if (mScrimAlpha > 0) {
            // Update the scroll position first to ensure scrim cutout is in the right place.
            mWorkspace.computeScrollWithoutInvalidation();
            CellLayout currCellLayout = mWorkspace.getCurrentDragOverlappingLayout();
            canvas.save();
            if (currCellLayout != null && currCellLayout != mLauncher.getHotseat()) {
                // Cut a hole in the darkening scrim on the page that should be highlighted, if any.
                mLauncher.getDragLayer()
                        .getDescendantRectRelativeToSelf(currCellLayout, mHighlightRect);
                canvas.clipRect(mHighlightRect, Region.Op.DIFFERENCE);
            }

            super.draw(canvas);
            canvas.restore();
        }
    }

}
