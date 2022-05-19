package app.lawnchair

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstBlocking
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.INDEX_ALL_APPS
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject
import com.patrykmichalik.preferencemanager.firstBlocking

class DeviceProfileOverrides(context: Context) {
    private val prefs = PreferenceManager.getInstance(context)
    private val preferenceManager2 = PreferenceManager2.getInstance(context)

    private val predefinedGrids = InvariantDeviceProfile.parseAllGridOptions(context)
        .map { option ->
            val gridInfo = DBGridInfo(
                numHotseatColumns = option.numHotseatIcons,
                numRows = option.numRows,
                numColumns = option.numColumns
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

    fun getOverrides(defaultGrid: InvariantDeviceProfile.GridOption) =
        Options(
            prefs = prefs,
            prefs2 = preferenceManager2,
            defaultGrid = defaultGrid,
        )

    data class DBGridInfo(
        var numHotseatColumns: Int,
        var numRows: Int,
        var numColumns: Int,
    ) {
        val dbFile get() = "launcher_${numRows}_${numColumns}_${numHotseatColumns}.db"

        constructor(prefs: PreferenceManager) : this(
            numHotseatColumns = prefs.hotseatColumns.get(),
            numRows = prefs.workspaceRows.get(),
            numColumns = prefs.workspaceColumns.get(),
        )
    }

    data class Options(
        var numAllAppsColumns: Int,
        var numFolderRows: Int,
        var numFolderColumns: Int,

        var iconSizeFactor: Float,
        var enableIconText: Boolean,
        var iconTextSizeFactor: Float,

        var allAppsIconSizeFactor: Float,
        var enableAllAppsIconText: Boolean,
        var allAppsIconTextSizeFactor: Float,

        var enableTaskbarOnPhone: Boolean,
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
            enableIconText = prefs2.showIconLabelsOnHomeScreen.firstBlocking(),
            iconTextSizeFactor = prefs2.homeIconLabelSizeFactor.firstBlocking(),

            allAppsIconSizeFactor = prefs2.drawerIconSizeFactor.firstBlocking(),
            enableAllAppsIconText = prefs2.showIconLabelsInDrawer.firstBlocking(),
            allAppsIconTextSizeFactor = prefs2.drawerIconLabelSizeFactor.firstBlocking(),

            enableTaskbarOnPhone = prefs2.enableTaskbarOnPhone.firstBlocking()
        )

        fun applyUi(idp: InvariantDeviceProfile) {
            // apply grid size
            idp.numAllAppsColumns = numAllAppsColumns
            idp.numDatabaseAllAppsColumns = numAllAppsColumns
            idp.numFolderRows = numFolderRows
            idp.numFolderColumns = numFolderColumns

            // apply icon and text size
            idp.iconSize[INDEX_DEFAULT] *= iconSizeFactor
            idp.iconTextSize[INDEX_DEFAULT] *= (if (enableIconText) iconTextSizeFactor else 0f)
            idp.iconSize[INDEX_ALL_APPS] *= allAppsIconSizeFactor
            idp.iconTextSize[INDEX_ALL_APPS] *= (if (enableAllAppsIconText) allAppsIconTextSizeFactor else 0f)

            idp.enableTaskbarOnPhone = Utilities.ATLEAST_S_V2 && enableTaskbarOnPhone
        }
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::DeviceProfileOverrides)
    }
}
