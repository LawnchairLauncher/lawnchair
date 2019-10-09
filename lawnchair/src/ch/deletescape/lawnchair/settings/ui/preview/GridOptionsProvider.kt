/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.settings.ui.preview

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import ch.deletescape.lawnchair.comparing
import ch.deletescape.lawnchair.then
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.GridOptionsProvider.BITMAP_WRITER
import java.io.FileNotFoundException

class GridOptionsProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        checkCallingPackage()

        if (KEY_LIST_OPTIONS != uri.path) {
            return null
        }
        val cursor = MatrixCursor(
                arrayOf(KEY_NAME, KEY_ROWS, KEY_COLS, KEY_PREVIEW_COUNT, KEY_IS_DEFAULT))
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        for (gridOption in getGridOptions()) {
            cursor.newRow()
                    .add(KEY_NAME, gridOption.name)
                    .add(KEY_ROWS, gridOption.numRows)
                    .add(KEY_COLS, gridOption.numColumns)
                    .add(KEY_PREVIEW_COUNT, 1)
                    .add(KEY_IS_DEFAULT,
                         idp.numColumns == gridOption.numColumns && idp.numRows == gridOption.numRows)
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.size > 0 && KEY_PREVIEW == segments[0]) {
            MIME_TYPE_PNG
        } else "vnd.android.cursor.dir/launcher_grid"
    }

    override fun insert(uri: Uri, initialValues: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int {
        checkCallingPackage()

        if (KEY_DEFAULT_GRID != uri.path) {
            return 0
        }

        val customGrid: CustomGridOption
        try {
            customGrid = createOption(values!!.getAsString(KEY_NAME))
        } catch (e: Exception) {
            return 0
        }

        val provider = CustomGridProvider.getInstance(context!!)
        if (provider.numRows != customGrid.numRows) {
            provider.numRows = customGrid.numRows
        }
        if (provider.numColumns != customGrid.numColumns) {
            provider.numColumns = customGrid.numColumns
        }
        if (provider.numHotseatIcons != customGrid.numHotseatColumns) {
            provider.numHotseatIcons = customGrid.numHotseatColumns
        }

        return 1
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        checkCallingPackage()

        val segments = uri.pathSegments
        if (segments.size < 2 || KEY_PREVIEW != segments[0]) {
            throw FileNotFoundException("Invalid preview url")
        }
        val profileName = segments[1]
        if (TextUtils.isEmpty(profileName)) {
            throw FileNotFoundException("Invalid preview url")
        }
        val customGrid = createOption(profileName)

        try {
            return openPipeHelper(uri, MIME_TYPE_PNG, null,
                                  CustomGridProvider.getInstance(context!!).renderPreview(customGrid), BITMAP_WRITER)
        } catch (e: Exception) {
            throw FileNotFoundException(e.message)
        }
    }

    private fun getGridOptions(): Collection<CustomGridOption> {
        val provider = CustomGridProvider.getInstance(context!!)
        val currentGrid = CustomGridOption(provider.numRows, provider.numColumns, provider.numHotseatIcons)
        val grids = (3..6).map { CustomGridOption(it, it, it) }.toSet() + currentGrid
        return grids.sortedWith(CUSTOM_GRID_COMPARATOR)
    }

    @Throws(FileNotFoundException::class)
    private fun createOption(name: String): CustomGridOption {
        val numRows: Int
        val numColumns: Int
        val numHotseatColumns: Int
        try {
            val gridSize = name.split(",")
            numRows = gridSize[0].toInt()
            numColumns = gridSize[1].toInt()
            numHotseatColumns = gridSize[2].toInt()
        } catch (e: Exception) {
            throw FileNotFoundException("Invalid preview url")
        }
        return CustomGridOption(numRows, numColumns, numHotseatColumns)
    }

    private fun checkCallingPackage() {
        require(Utilities.isSystemApp(context!!.packageManager, callingPackage))
    }

    private data class CustomGridOption(val numRows: Int, val numColumns: Int, val numHotseatColumns: Int)
        : InvariantDeviceProfile.GridCustomizer {

        val name = "$numRows,$numColumns,$numHotseatColumns"

        init {
            require(numRows in CUSTOM_SIZE_RANGE)
            require(numColumns in CUSTOM_SIZE_RANGE)
            require(numHotseatColumns in CUSTOM_SIZE_RANGE)
        }

        override fun customizeGrid(grid: InvariantDeviceProfile.GridOverrides) {
            grid.numRows = numRows
            grid.numColumns = numColumns
            grid.numHotseatIcons = numHotseatColumns
        }
    }

    companion object {

        private const val KEY_NAME = "name"
        private const val KEY_ROWS = "rows"
        private const val KEY_COLS = "cols"
        private const val KEY_PREVIEW_COUNT = "preview_count"
        private const val KEY_IS_DEFAULT = "is_default"

        private const val KEY_LIST_OPTIONS = "/list_options"
        private const val KEY_DEFAULT_GRID = "/default_grid"

        private const val KEY_PREVIEW = "preview"
        private const val MIME_TYPE_PNG = "image/png"

        private val CUSTOM_SIZE_RANGE = 3..20
        private val CUSTOM_GRID_COMPARATOR = comparing<CustomGridOption, Int> { it.numColumns }
                .then { it.numRows }
    }
}
