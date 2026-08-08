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
 *
 * Modifications copyright 2026 Lawnchair
 */
package com.android.launcher3.celllayout;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;

import com.android.launcher3.model.data.ItemInfo;

import java.util.Objects;

/**
 * Class for mapping between model position and presenter position.
 */
public class CellPosMapper {

    public static final CellPosMapper DEFAULT = new CellPosMapper(false, -1, 1, 1);
    private final boolean mHasVerticalHotseat;
    private final int mNumOfHotseat;
    private final int mNumHotseatRows;

    public CellPosMapper(boolean hasVerticalHotseat, int numOfHotseat) {
        this(hasVerticalHotseat, numOfHotseat, 1, 1);
    }

    public CellPosMapper(boolean hasVerticalHotseat, int numOfHotseat, int numHotseatRows,
            int numHotseatPages) {
        mHasVerticalHotseat = hasVerticalHotseat;
        mNumOfHotseat = numOfHotseat;
        mNumHotseatRows = Math.max(1, numHotseatRows);
    }

    private static boolean isHotseatContainer(int container) {
        return container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION;
    }

    /**
     * Maps the position in model to the position in view.
     * Hotseat model screenId is a flat rank; presenter screenId is the dock page index.
     */
    public CellPos mapModelToPresenter(ItemInfo info) {
        if (isHotseatContainer(info.container) && mNumOfHotseat > 0) {
            if (mHasVerticalHotseat) {
                return new CellPos(0, mNumOfHotseat - info.screenId - 1, 0);
            }
            int slotsPerPage = Math.max(1, mNumOfHotseat * mNumHotseatRows);
            int page = info.screenId / slotsPerPage;
            int local = info.screenId % slotsPerPage;
            int cellX = local % mNumOfHotseat;
            int cellY = local / mNumOfHotseat;
            return new CellPos(cellX, cellY, page);
        }
        return new CellPos(info.cellX, info.cellY, info.screenId);
    }

    /**
     * Maps the position in view to the position in model
     */
    public CellPos mapPresenterToModel(int presenterX, int presenterY, int presenterScreen,
            int container) {
        if (isHotseatContainer(container)) {
            if (mHasVerticalHotseat && mNumOfHotseat > 0) {
                presenterScreen = mNumOfHotseat - presenterY - 1;
            } else if (!mHasVerticalHotseat && mNumOfHotseat > 0) {
                // presenterScreen is the dock page index (0-based)
                int page = Math.max(0, presenterScreen);
                int slotsPerPage = Math.max(1, mNumOfHotseat * mNumHotseatRows);
                int localRank = presenterY * mNumOfHotseat + presenterX;
                presenterScreen = page * slotsPerPage + localRank;
            } else if (!mHasVerticalHotseat) {
                presenterScreen = presenterX;
            }
        }
        return new CellPos(presenterX, presenterY, presenterScreen);
    }

    /**
     * Cell mapper which maps two panels into a single layout
     */
    public static class TwoPanelCellPosMapper extends CellPosMapper {

        private final int mColumnCount;

        public TwoPanelCellPosMapper(int columnCount) {
            super(false, -1, 1, 1);
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
