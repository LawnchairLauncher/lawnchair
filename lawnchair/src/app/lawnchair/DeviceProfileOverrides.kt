package app.lawnchair

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstBlocking
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.InvariantDeviceProfile.INDEX_LANDSCAPE
import com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_LANDSCAPE
import com.android.launcher3.InvariantDeviceProfile.INDEX_TWO_PANEL_PORTRAIT
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import com.patrykmichalik.opto.core.firstBlocking

class DeviceProfileOverrides(context: Context) : SafeCloseable {
    private val prefs = PreferenceManager.getInstance(context)
    private val preferenceManager2 = PreferenceManager2.getInstance(context)

    private val predefinedGrids = InvariantDeviceProfile.parseAllGridOptions(context)
        .map { option ->
            val gridInfo = DBGridInfo(
                numHotseatColumns = option.numHotseatIcons,
                numRows = option.numRows,
                numColumns = option.numColumns,
            )
            gridInfo to option.name
        }

    fun getGridInfo() = DBGridInfo(prefs)

    fun getGridInfo(gridName: String) = predefinedGrids
        .first { it.second == gridName }
        .first

    fun getGridName(gridInfo: DBGridInfo): String {
        val match = predefinedGrids
            .firstOrNull { it.first.numRows >= gridInfo.numRows && it.first.numColumns >= gridInfo.numColumns }
            ?: predefinedGrids.last()
        return match.second
    }

    fun getCurrentGridName() = getGridName(getGridInfo())

    fun setCurrentGrid(gridName: String) {
        val gridInfo = getGridInfo(gridName)
        prefs.workspaceRows.set(gridInfo.numRows)
        prefs.workspaceColumns.set(gridInfo.numColumns)
        prefs.hotseatColumns.set(gridInfo.numHotseatColumns)
    }

    fun getOverrides(defaultGrid: InvariantDeviceProfile.GridOption) = Options(
        prefs = prefs,
        prefs2 = preferenceManager2,
        defaultGrid = defaultGrid,
    )

    fun getTextFactors() = TextFactors(preferenceManager2)
    override fun close() {
        TODO("Not yet implemented")
    }

    data class DBGridInfo(
        val numHotseatColumns: Int,
        val numRows: Int,
        val numColumns: Int,
    ) {
        val dbFile get() = "launcher_${numRows}_${numColumns}_$numHotseatColumns.db"

        constructor(prefs: PreferenceManager) : this(
            numHotseatColumns = prefs.hotseatColumns.get(),
            numRows = prefs.workspaceRows.get(),
            numColumns = prefs.workspaceColumns.get(),
        )
    }

    data class Options(
        val numAllAppsColumns: Int,
        val numFolderRows: Int,
        val numFolderColumns: Int,

        val iconSizeFactor: Float,
        val allAppsIconSizeFactor: Float,

        val enableTaskbarOnPhone: Boolean,
    ) {
        constructor(
            prefs: PreferenceManager,
            prefs2: PreferenceManager2,
            defaultGrid: InvariantDeviceProfile.GridOption,
        ) : this(
            numAllAppsColumns = prefs2.drawerColumns.firstBlocking(gridOption = defaultGrid),
            numFolderRows = prefs.folderRows.get(defaultGrid),
            numFolderColumns = prefs2.folderColumns.firstBlocking(gridOption = defaultGrid),

            iconSizeFactor = prefs2.homeIconSizeFactor.firstBlocking(),
            allAppsIconSizeFactor = prefs2.drawerIconSizeFactor.firstBlocking(),

            enableTaskbarOnPhone = prefs2.enableTaskbarOnPhone.firstBlocking(),
        )

        fun applyUi(idp: InvariantDeviceProfile) {
            // apply grid size
            idp.numAllAppsColumns = numAllAppsColumns
            idp.numDatabaseAllAppsColumns = numAllAppsColumns
            idp.numFolderRows[INDEX_DEFAULT] = numFolderRows
            idp.numFolderColumns[INDEX_DEFAULT] = numFolderColumns

            // apply icon and text size
            idp.iconSize[INDEX_DEFAULT] *= iconSizeFactor
            idp.iconSize[INDEX_LANDSCAPE] *= iconSizeFactor
            idp.iconSize[INDEX_TWO_PANEL_PORTRAIT] *= iconSizeFactor
            idp.iconSize[INDEX_TWO_PANEL_LANDSCAPE] *= iconSizeFactor

            idp.allAppsIconSize[INDEX_DEFAULT] *= allAppsIconSizeFactor
            idp.allAppsIconSize[INDEX_LANDSCAPE] *= allAppsIconSizeFactor
            idp.allAppsIconSize[INDEX_TWO_PANEL_PORTRAIT] *= allAppsIconSizeFactor
            idp.allAppsIconSize[INDEX_TWO_PANEL_LANDSCAPE] *= allAppsIconSizeFactor
        }
    }

    data class TextFactors(
        val iconTextSizeFactor: Float,
        val allAppsIconTextSizeFactor: Float,
        val iconFolderTextSizeFactor: Float,
    ) {
        constructor(
            prefs2: PreferenceManager2,
        ) : this(
            enableIconText = prefs2.showIconLabelsOnHomeScreen.firstBlocking(),
            iconTextSizeFactor = prefs2.homeIconLabelSizeFactor.firstBlocking(),
            enableIconTextFolder = prefs2.showIconLabelsOnHomeScreenFolder.firstBlocking(),
            iconFolderTextSizeFactor = prefs2.homeIconLabelFolderSizeFactor.firstBlocking(),
            enableAllAppsIconText = prefs2.showIconLabelsInDrawer.firstBlocking(),
            allAppsIconTextSizeFactor = prefs2.drawerIconLabelSizeFactor.firstBlocking(),
        )

        constructor(
            enableIconText: Boolean,
            iconTextSizeFactor: Float,
            enableIconTextFolder: Boolean,
            iconFolderTextSizeFactor: Float,
            enableAllAppsIconText: Boolean,
            allAppsIconTextSizeFactor: Float,
        ) : this(
            iconTextSizeFactor = if (enableIconText) iconTextSizeFactor else 0f,
            allAppsIconTextSizeFactor = if (enableAllAppsIconText) allAppsIconTextSizeFactor else 0f,
            iconFolderTextSizeFactor = if (enableIconTextFolder) iconFolderTextSizeFactor else 0f,
        )
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::DeviceProfileOverrides)
    }
}
