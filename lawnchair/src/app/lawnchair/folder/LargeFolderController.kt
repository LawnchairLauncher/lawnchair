/*
 * Copyright 2026 Lawnchair
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

package app.lawnchair.folder

import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.views.OptionsPopupView

/**
 * Large folders, similar to the ones of ColorOS / OxygenOS / Nothing launcher: a desktop folder
 * spanning several cells whose apps can be launched directly (see
 * [FolderIcon.isLargeFolderMode]).
 *
 * The long-press popup grows / shrinks square folders (1x1 → 2x2 → … → 5x5) and opens the
 * per-folder settings, which also allow any rows x columns (e.g. 1x5 or 5x1) and choosing
 * whether apps that don't fit open the folder or scroll inside the tile.
 */
object LargeFolderController {

    /** Returns true if [icon] is a desktop folder that can be made large. */
    @JvmStatic
    fun canResize(icon: FolderIcon): Boolean {
        val info = icon.mInfo ?: return false
        return info.container == CONTAINER_DESKTOP && icon.parent?.parent is CellLayout
    }

    /** Largest span supported by the grid on each axis, (columns, rows). */
    fun getMaxSpan(icon: FolderIcon): Point {
        val cellLayout = icon.parent?.parent as? CellLayout
        val maxX = minOf(FolderInfo.LARGE_FOLDER_MAX_SPAN, cellLayout?.countX ?: 1)
        val maxY = minOf(FolderInfo.LARGE_FOLDER_MAX_SPAN, cellLayout?.countY ?: 1)
        return Point(maxX, maxY)
    }

    /**
     * Shows a popup next to [icon] with "Large folder" / "Small folder" (one size step up or
     * down, only while possible) and a settings icon. Returns the pre-drag condition to use so
     * that moving the finger still drags the folder (and closes the popup), like the popup of an
     * app icon.
     */
    @JvmStatic
    fun showResizePopup(launcher: Launcher, icon: FolderIcon): DragOptions.PreDragCondition? {
        val info = icon.mInfo
        val size = maxOf(info.spanX, info.spanY)
        val maxSpan = getMaxSpan(icon)
        val maxSquare = minOf(maxSpan.x, maxSpan.y)

        // Shown as a row of round, icon-only buttons.
        val buttons = mutableListOf<OptionsPopupView.OptionItem>()
        if (size + 1 <= maxSquare) {
            buttons += OptionsPopupView.OptionItem(
                launcher,
                R.string.large_folder_enlarge,
                R.drawable.ic_folder_enlarge,
                StatsLogManager.LauncherEvent.IGNORE,
            ) {
                resize(launcher, icon, size + 1, size + 1)
                true
            }
        }
        if (info.spanX > 1 || info.spanY > 1) {
            buttons += OptionsPopupView.OptionItem(
                launcher,
                R.string.large_folder_shrink,
                R.drawable.ic_folder_shrink,
                StatsLogManager.LauncherEvent.IGNORE,
            ) {
                resize(launcher, icon, size - 1, size - 1)
                true
            }
        }
        buttons += OptionsPopupView.OptionItem(
            launcher,
            R.string.large_folder_settings,
            R.drawable.ic_setting,
            StatsLogManager.LauncherEvent.IGNORE,
        ) {
            showSettings(launcher, icon)
            true
        }

        val popup = OptionsPopupView.show<Launcher>(
            launcher,
            getVisualBoundsInDragLayer(launcher, icon),
            emptyList(),
            buttons,
            false,
        ) ?: return null

        val startDragThreshold = launcher.resources
            .getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold)
        return object : DragOptions.PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double): Boolean = distanceDragged > startDragThreshold

            override fun onPreDragStart(dragObject: DropTarget.DragObject?) = Unit

            override fun onPreDragEnd(dragObject: DropTarget.DragObject?, dragStarted: Boolean) {
                if (dragStarted) popup.close(true)
            }
        }
    }

    /** Opens the settings of this one folder. */
    @JvmStatic
    fun showSettings(launcher: Launcher, icon: FolderIcon) {
        ComposeBottomSheet.show(
            context = launcher,
            contentPaddings = PaddingValues(bottom = 64.dp),
        ) {
            LargeFolderSettingsSheet(launcher = launcher, icon = icon)
        }
    }

    /**
     * Resizes the folder to [spanX] x [spanY] cells (1x1 makes it a regular folder again),
     * moving it to the closest free area of its page if needed. Returns false (and tells the
     * user) if there isn't enough room.
     */
    @JvmStatic
    fun resize(launcher: Launcher, icon: FolderIcon, spanX: Int, spanY: Int): Boolean {
        val info = icon.mInfo ?: return false
        val cellLayout = icon.parent?.parent as? CellLayout ?: return false
        val lp = icon.layoutParams as? CellLayoutLayoutParams ?: return false
        val maxSpan = getMaxSpan(icon)
        val newSpanX = spanX.coerceIn(1, maxSpan.x)
        val newSpanY = spanY.coerceIn(1, maxSpan.y)
        if (lp.cellHSpan == newSpanX && lp.cellVSpan == newSpanY) return true

        cellLayout.markCellsAsUnoccupiedForView(icon)
        val target = findPlacement(cellLayout, lp.cellX, lp.cellY, newSpanX, newSpanY)
        if (target == null) {
            cellLayout.markCellsAsOccupiedForView(icon)
            Toast.makeText(launcher, R.string.large_folder_no_space, Toast.LENGTH_SHORT).show()
            return false
        }

        lp.cellX = target.x
        lp.cellY = target.y
        lp.tmpCellX = target.x
        lp.tmpCellY = target.y
        lp.cellHSpan = newSpanX
        lp.cellVSpan = newSpanY
        cellLayout.markCellsAsOccupiedForView(icon)

        val isLarge = newSpanX > 1 || newSpanY > 1
        val wasLarge = info.hasOption(FolderInfo.FLAG_LARGE_FOLDER)
        if (isLarge && !wasLarge) {
            // A folder made large for the first time starts with the global defaults.
            val prefs = PreferenceManager2.getInstance(launcher)
            setOptionSilently(
                info,
                FolderInfo.FLAG_LARGE_FOLDER_SCROLL,
                prefs.largeFolderScrollByDefault.firstCached(),
            )
            setOptionSilently(
                info,
                FolderInfo.FLAG_LARGE_FOLDER_SCROLL_HORIZONTAL,
                prefs.largeFolderScrollHorizontalByDefault.firstCached(),
            )
            setOptionSilently(
                info,
                FolderInfo.FLAG_LARGE_FOLDER_HIDE_SCROLL_BAR,
                !prefs.largeFolderShowScrollBarByDefault.firstCached(),
            )
        }

        val modelWriter = launcher.modelWriter
        val screenId = launcher.workspace.getCellLayoutId(cellLayout)
        modelWriter.modifyItemInDatabase(
            info,
            CONTAINER_DESKTOP,
            screenId,
            target.x,
            target.y,
            newSpanX,
            newSpanY,
        )
        // Writes the whole item, so the options are persisted together with the new span.
        setOptionSilently(info, FolderInfo.FLAG_LARGE_FOLDER, isLarge)
        modelWriter.updateItemInDatabase(info)

        icon.onLargeFolderSpanChanged()
        return true
    }

    /** Sets a large folder option (scroll / scroll direction) and saves it. */
    @JvmStatic
    fun setOption(launcher: Launcher, icon: FolderIcon, option: Int, enabled: Boolean) {
        val info = icon.mInfo ?: return
        info.setOption(option, enabled, launcher.modelWriter)
        icon.onLargeFolderSpanChanged()
    }

    private fun setOptionSilently(info: FolderInfo, option: Int, enabled: Boolean) {
        info.options = if (enabled) info.options or option else info.options and option.inv()
    }

    /** Finds the vacant area closest to (cellX, cellY), preferring that cell. */
    @JvmStatic
    fun findPlacement(
        cellLayout: CellLayout,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ): Point? {
        var best: Point? = null
        var bestDistance = Int.MAX_VALUE
        for (y in 0..cellLayout.countY - spanY) {
            for (x in 0..cellLayout.countX - spanX) {
                if (!cellLayout.isRegionVacant(x, y, spanX, spanY)) continue
                val dx = x - cellX
                val dy = y - cellY
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = Point(x, y)
                }
            }
        }
        return best
    }

    private fun getVisualBoundsInDragLayer(launcher: Launcher, icon: FolderIcon): RectF {
        val viewBounds = Rect()
        val scale = launcher.dragLayer.getDescendantRectRelativeToSelf(icon, viewBounds)
        val visual = Rect()
        icon.getWorkspaceVisualDragBounds(visual)
        return RectF(
            viewBounds.left + visual.left * scale,
            viewBounds.top + visual.top * scale,
            viewBounds.left + visual.right * scale,
            viewBounds.top + visual.bottom * scale,
        )
    }
}
