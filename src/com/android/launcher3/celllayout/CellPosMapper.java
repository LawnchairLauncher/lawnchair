/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3.celllayout;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.data.ItemInfo;

import java.util.Objects;

/**
 * Class for mapping between model position and presenter position.
 */
public class CellPosMapper {

    public static final CellPosMapper DEFAULT = new CellPosMapper(false, -1);
    private final boolean mHasVerticalHotseat;
    private final int mNumOfHotseat;

    public CellPosMapper(boolean hasVerticalHotseat, int numOfHotseat) {
        mHasVerticalHotseat = hasVerticalHotseat;
        mNumOfHotseat = numOfHotseat;
    }

    /**
     * Maps the position in model to the position in view
     */
    public CellPos mapModelToPresenter(ItemInfo info) {
        return new CellPos(info.cellX, info.cellY, info.screenId);
    }

    /**
     * Maps the position in view to the position in model
     */
    public CellPos mapPresenterToModel(int presenterX, int presenterY, int presenterScreen,
            int container) {
        if (container == Favorites.CONTAINER_HOTSEAT) {
            presenterScreen = mHasVerticalHotseat
                    ? mNumOfHotseat - presenterY - 1 : presenterX;
        }
        return new CellPos(presenterX, presenterY, presenterScreen);
    }

    /**
     * Cell mapper which maps two panels into a single layout
     */
    public static class TwoPanelCellPosMapper extends CellPosMapper {

        private final int mColumnCount;

        public TwoPanelCellPosMapper(int columnCount) {
            super(false, -1);
            mColumnCount = columnCount;
        }

        /**
         * Maps the position in model to the position in view
         */
        public CellPos mapModelToPresenter(ItemInfo info) {
            if (info.container != CONTAINER_DESKTOP || (info.screenId % 2) == 0) {
                return super.mapModelToPresenter(info);
            }
            return new CellPos(info.cellX + mColumnCount, info.cellY, info.screenId - 1);
        }

        @Override
        public CellPos mapPresenterToModel(int presenterX, int presenterY, int presenterScreen,
                int container) {
            if (container == CONTAINER_DESKTOP && (presenterScreen % 2) == 0
                    && presenterX >= mColumnCount) {
                return new CellPos(presenterX - mColumnCount, presenterY, presenterScreen + 1);
            }
            return super.mapPresenterToModel(presenterX, presenterY, presenterScreen, container);
        }
    }

    /**
     * Utility class to indicate the position of a cell
     */
    public static class CellPos {
        public final int cellX;
        public final int cellY;
        public final int screenId;

        public CellPos(int cellX, int cellY, int screenId) {
            this.cellX = cellX;
            this.cellY = cellY;
            this.screenId = screenId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CellPos)) return false;
            CellPos cellPos = (CellPos) o;
            return cellX == cellPos.cellX && cellY == cellPos.cellY && screenId == cellPos.screenId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(cellX, cellY, screenId);
        }

        @Override
        public String toString() {
            return "CellPos{"
                    + "cellX=" + cellX
                    + ", cellY=" + cellY
                    + ", screenId=" + screenId + '}';
        }
    }
}
