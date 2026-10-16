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

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;

import java.util.Objects;

import app.lawnchair.fold.FoldDockLayout;
import app.lawnchair.fold.FoldItemSets;

/**
 * Class for mapping between model position and presenter position.
 */
public class CellPosMapper {

    public static final CellPosMapper DEFAULT = new CellPosMapper(false, -1, 1, 1);
    private final boolean mHasVerticalHotseat;
    private final int mNumOfHotseat;
    private final int mNumHotseatRows;
    @Nullable
    private final FoldDockLayout mFoldDock;
    // LC-Note: whether the inner item set of "Independent" is shown, see FoldItemSets.
    private final boolean mInnerItemSet;

    public CellPosMapper(boolean hasVerticalHotseat, int numOfHotseat) {
        this(hasVerticalHotseat, numOfHotseat, 1, 1);
    }

    public CellPosMapper(boolean hasVerticalHotseat, int numOfHotseat, int numHotseatRows,
            int numHotseatPages) {
        this(hasVerticalHotseat, numOfHotseat, numHotseatRows, numHotseatPages, null);
    }

    /**
     * LC-Note: takes the dock layout of an unfolded foldable too.
     *
     * @param foldDock the dock layout of an unfolded foldable, which maps the first row's ranks
     *                 to cells (see {@link FoldDockLayout}), or null.
     */
    public CellPosMapper(boolean hasVerticalHotseat, int numOfHotseat, int numHotseatRows,
            int numHotseatPages, @Nullable FoldDockLayout foldDock) {
        this(hasVerticalHotseat, numOfHotseat, numHotseatRows, numHotseatPages, foldDock, false);
    }

    /**
     * @param innerItemSet whether the inner item set is shown, see
     *                     {@link app.lawnchair.fold.FoldItemSets}.
     */
    public CellPosMapper(boolean hasVerticalHotseat, int numOfHotseat, int numHotseatRows,
            int numHotseatPages, @Nullable FoldDockLayout foldDock, boolean innerItemSet) {
        mHasVerticalHotseat = hasVerticalHotseat;
        mNumOfHotseat = numOfHotseat;
        mNumHotseatRows = Math.max(1, numHotseatRows);
        mFoldDock = foldDock;
        mInnerItemSet = innerItemSet;
    }

    /**
     * LC-Note: whether the inner item set of "Independent" is shown (FoldItemSets). The one
     * answer on the launcher side, built from DeviceProfile#isInnerItemSet.
     */
    public boolean isInnerItemSet() {
        return mInnerItemSet;
    }

    /**
     * LC-Note: page or dock rank of a desktop or dock item within the shown item set (the rank
     * the {@code Hotseat} order helpers expect).
     */
    public int toPresenterScreenId(ItemInfo info) {
        if (mInnerItemSet
                && (info.container == CONTAINER_DESKTOP || info.container == CONTAINER_HOTSEAT)) {
            return FoldItemSets.toPresenterScreenId(info.screenId);
        }
        return info.screenId;
    }

    /** LC-Note: the reverse of {@link #toPresenterScreenId}. */
    private int toModelScreenId(int screenId, int container) {
        if (mInnerItemSet
                && (container == CONTAINER_DESKTOP || container == CONTAINER_HOTSEAT)) {
            return FoldItemSets.toModelScreenId(screenId, true);
        }
        return screenId;
    }

    /** LC-Note: returns the dock layout of an unfolded foldable, or null. */
    @Nullable
    public FoldDockLayout getFoldDockLayout() {
        return mFoldDock;
    }

    private static boolean isHotseatContainer(int container) {
        return container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION;
    }

    /**
     * Maps the position in model to the position in view.
     * Hotseat model screenId is a flat rank; presenter screenId is the dock page index.
     */
    public CellPos mapModelToPresenter(ItemInfo info) {
        int screenId = toPresenterScreenId(info);
        if (isHotseatContainer(info.container) && mNumOfHotseat > 0) {
            if (mHasVerticalHotseat) {
                if (mFoldDock != null && mFoldDock.isSide()) {
                    // LC-Note: foldable side dock, see FoldDockLayout#rankToCellY
                    return new CellPos(0, mFoldDock.rankToCellY(screenId), 0);
                }
                return new CellPos(0, mNumOfHotseat - screenId - 1, 0);
            }
            int slotsPerPage = Math.max(1, mNumOfHotseat * mNumHotseatRows);
            int page = screenId / slotsPerPage;
            int local = screenId % slotsPerPage;
            int cellX = local % mNumOfHotseat;
            int cellY = local / mNumOfHotseat;
            if (mFoldDock != null && page == 0 && cellY == 0) {
                cellX = mFoldDock.rankToCellX(cellX);
            }
            return new CellPos(cellX, cellY, page);
        }
        return new CellPos(info.cellX, info.cellY, screenId);
    }

    /**
     * Maps the position in view to the position in model
     */
    public CellPos mapPresenterToModel(int presenterX, int presenterY, int presenterScreen,
            int container) {
        if (isHotseatContainer(container)) {
            if (mHasVerticalHotseat && mNumOfHotseat > 0) {
                presenterScreen = mFoldDock != null && mFoldDock.isSide()
                        ? mFoldDock.cellYToRank(presenterY) : mNumOfHotseat - presenterY - 1;
            } else if (!mHasVerticalHotseat && mNumOfHotseat > 0) {
                // presenterScreen is the dock page index (0-based)
                int page = Math.max(0, presenterScreen);
                int slotsPerPage = Math.max(1, mNumOfHotseat * mNumHotseatRows);
                int localX = mFoldDock != null && page == 0 && presenterY == 0
                        ? mFoldDock.cellXToRank(presenterX) : presenterX;
                int localRank = presenterY * mNumOfHotseat + localX;
                presenterScreen = page * slotsPerPage + localRank;
            } else if (!mHasVerticalHotseat) {
                presenterScreen = presenterX;
            }
        }
        return new CellPos(presenterX, presenterY, toModelScreenId(presenterScreen, container));
    }

    /**
     * Cell mapper which maps two panels into a single layout
     */
    public static class TwoPanelCellPosMapper extends CellPosMapper {

        private final int mColumnCount;
        // LC-Note: whether the even page of a pair is on the end half (AOSP: start half).
        private final boolean mEvenPageOnEnd;

        public TwoPanelCellPosMapper(int columnCount) {
            this(columnCount, false, -1, 1, 1, null, false);
        }

        /**
         * LC-Note: also maps the dock like {@link CellPosMapper} (rows, pages, fold dock), and puts
         * the even page of each pair on the end half when {@code evenPageOnEnd}.
         */
        public TwoPanelCellPosMapper(int columnCount, boolean hasVerticalHotseat, int numOfHotseat,
                int numHotseatRows, int numHotseatPages, @Nullable FoldDockLayout foldDock,
                boolean evenPageOnEnd) {
            super(hasVerticalHotseat, numOfHotseat, numHotseatRows, numHotseatPages, foldDock);
            mColumnCount = columnCount;
            mEvenPageOnEnd = evenPageOnEnd;
        }

        /** Returns whether the even page of a pair is on the end half of the wide page. */
        public boolean isEvenPageOnEnd() {
            return mEvenPageOnEnd;
        }

        /**
         * Maps the position in model to the position in view
         */
        public CellPos mapModelToPresenter(ItemInfo info) {
            if (info.container != CONTAINER_DESKTOP
                    || ((info.screenId % 2) == 0 && !mEvenPageOnEnd)) {
                return super.mapModelToPresenter(info);
            }
            boolean isOddPage = (info.screenId % 2) != 0;
            boolean onEndHalf = isOddPage != mEvenPageOnEnd;
            return new CellPos(info.cellX + (onEndHalf ? mColumnCount : 0), info.cellY,
                    isOddPage ? info.screenId - 1 : info.screenId);
        }

        @Override
        public CellPos mapPresenterToModel(int presenterX, int presenterY, int presenterScreen,
                int container) {
            if (container == CONTAINER_DESKTOP && (presenterScreen % 2) == 0) {
                boolean onEndHalf = presenterX >= mColumnCount;
                boolean isOddPage = onEndHalf != mEvenPageOnEnd;
                if (onEndHalf || isOddPage) {
                    return new CellPos(presenterX - (onEndHalf ? mColumnCount : 0), presenterY,
                            isOddPage ? presenterScreen + 1 : presenterScreen);
                }
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
