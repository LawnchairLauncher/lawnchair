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

import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut

/**
 * The "big items" page: an optional last home screen page (see
 * [com.android.launcher3.WorkspaceLayoutManager.BIG_ITEMS_SCREEN_ID]) meant for widgets and large
 * folders. Folders placed there become large folders, and apps placed there can be enlarged to
 * any size.
 */
object BigItemsPage {

    /** Time for the folder creation / drop animation to finish before enlarging a folder. */
    private const val ENLARGE_DELAY_MS = 600L

    /** Returns true if [info] is an app (or shortcut) of the desktop that spans several cells. */
    @JvmStatic
    fun isBigApp(info: Any?): Boolean = info is WorkspaceItemInfo &&
        info.container == CONTAINER_DESKTOP &&
        (info.spanX > 1 || info.spanY > 1)

    /**
     * Turns an enlarged app back into a regular 1x1 icon (e.g. when it leaves the big items page or
     * goes into a folder). Returns true if it was enlarged.
     */
    @JvmStatic
    fun resetSpan(info: ItemInfo?): Boolean {
        if (info !is WorkspaceItemInfo || (info.spanX <= 1 && info.spanY <= 1)) return false
        info.spanX = 1
        info.spanY = 1
        return true
    }

    /** Makes [icon] a 2x2 large folder once the running drop / creation animation is done. */
    @JvmStatic
    fun enlargeFolderLater(launcher: Launcher, icon: FolderIcon) {
        icon.postDelayed({
            val info = icon.mInfo ?: return@postDelayed
            if (info.isLargeFolder || !icon.isAttachedToWindow) return@postDelayed
            if (!LargeFolderController.canResize(icon)) return@postDelayed
            if (!launcher.workspace.isBigItemsPage(icon.parent?.parent as? CellLayout)) {
                return@postDelayed
            }
            LargeFolderController.resize(launcher, icon, 2, 2)
        }, ENLARGE_DELAY_MS)
    }

    /** Returns true if [view] is an app icon placed on the big items page. */
    @JvmStatic
    fun canResizeApp(launcher: Launcher, view: View?): Boolean {
        if (view !is BubbleTextView) return false
        val info = view.tag as? WorkspaceItemInfo ?: return false
        if (info.container != CONTAINER_DESKTOP) return false
        return launcher.workspace.isBigItemsPage(view.parent?.parent as? CellLayout)
    }

    /**
     * Resizes the app [view] to [spanX] x [spanY] cells, moving it to the closest free area of the
     * page if needed. Returns false (and tells the user) if there isn't enough room.
     */
    @JvmStatic
    fun resizeApp(launcher: Launcher, view: View, spanX: Int, spanY: Int): Boolean {
        val info = view.tag as? ItemInfo ?: return false
        val cellLayout = view.parent?.parent as? CellLayout ?: return false
        val lp = view.layoutParams as? CellLayoutLayoutParams ?: return false
        val newSpanX = spanX.coerceIn(1, cellLayout.countX)
        val newSpanY = spanY.coerceIn(1, cellLayout.countY)
        if (lp.cellHSpan == newSpanX && lp.cellVSpan == newSpanY) return true

        cellLayout.markCellsAsUnoccupiedForView(view)
        val target = LargeFolderController.findPlacement(
            cellLayout,
            lp.cellX,
            lp.cellY,
            newSpanX,
            newSpanY,
        )
        if (target == null) {
            cellLayout.markCellsAsOccupiedForView(view)
            Toast.makeText(launcher, R.string.large_folder_no_space, Toast.LENGTH_SHORT).show()
            return false
        }
        lp.cellX = target.x
        lp.cellY = target.y
        lp.tmpCellX = target.x
        lp.tmpCellY = target.y
        lp.cellHSpan = newSpanX
        lp.cellVSpan = newSpanY
        cellLayout.markCellsAsOccupiedForView(view)

        launcher.modelWriter.modifyItemInDatabase(
            info,
            CONTAINER_DESKTOP,
            launcher.workspace.getCellLayoutId(cellLayout),
            target.x,
            target.y,
            newSpanX,
            newSpanY,
        )
        view.requestLayout()
        view.invalidate()
        return true
    }

    /** Opens the size picker of the app [view]. */
    @JvmStatic
    fun showResizeSheet(launcher: Launcher, view: View) {
        ComposeBottomSheet.show(
            context = launcher,
            contentPaddings = PaddingValues(bottom = 64.dp),
        ) {
            BigAppResizeSheet(launcher = launcher, view = view)
        }
    }

    /** "Resize" entry of the long-press popup of apps placed on the big items page. */
    @JvmField
    val RESIZE = SystemShortcut.Factory<Launcher> { launcher, itemInfo, originalView ->
        if (canResizeApp(launcher, originalView)) {
            ResizeApp(launcher, itemInfo, originalView)
        } else {
            null
        }
    }

    private class ResizeApp(
        launcher: Launcher,
        itemInfo: ItemInfo,
        originalView: View,
    ) : SystemShortcut<Launcher>(
        R.drawable.ic_folder_enlarge,
        R.string.big_app_resize,
        launcher,
        itemInfo,
        originalView,
    ) {
        override fun onClick(v: View) {
            AbstractFloatingView.closeAllOpenViews(mTarget)
            showResizeSheet(mTarget, mOriginalView)
        }
    }
}

@Composable
private fun BigAppResizeSheet(
    launcher: Launcher,
    view: View,
    modifier: Modifier = Modifier,
) {
    val info = view.tag as ItemInfo
    val cellLayout = view.parent?.parent as? CellLayout
    val maxX = cellLayout?.countX ?: 1
    val maxY = cellLayout?.countY ?: 1
    var columns by remember { mutableIntStateOf(info.spanX) }
    var rows by remember { mutableIntStateOf(info.spanY) }

    fun applySize(newColumns: Int, newRows: Int) {
        BigItemsPage.resizeApp(launcher, view, newColumns, newRows)
        // The resize may have failed (no room), so always show the actual size.
        columns = info.spanX
        rows = info.spanY
    }

    Column(
        modifier = modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth(),
    ) {
        Text(
            text = info.title?.toString()?.takeIf { it.isNotBlank() }
                ?: stringResource(id = R.string.big_app_resize),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
        )
        PreferenceGroup(heading = stringResource(id = R.string.large_folder_size_label)) {
            StepperPreference(
                label = stringResource(id = R.string.large_folder_columns),
                value = columns,
                range = 1..maxX,
                onValueChange = { applySize(it, rows) },
            )
            StepperPreference(
                label = stringResource(id = R.string.large_folder_rows),
                value = rows,
                range = 1..maxY,
                onValueChange = { applySize(columns, it) },
            )
        }
    }
}
