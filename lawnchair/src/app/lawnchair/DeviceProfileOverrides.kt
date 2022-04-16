package app.lawnchair

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstBlocking
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.MainThreadInitializedObject
import com.patrykmichalik.preferencemanager.firstBlocking

class DeviceProfileOverrides(context: Context) {
    private val prefs = PreferenceManager.getInstance(context)
    private val preferenceManager2 = PreferenceManager2.getInstance(context)

    fun getOverrides(defaultGrid: InvariantDeviceProfile.GridOption) =
        Options(
            prefs = prefs,
            preferenceManager2 = preferenceManager2,
            defaultGrid = defaultGrid,
        )

    data class Options(
        var numHotseatColumns: Int,
        var numRows: Int,
        var numColumns: Int,
        var numAllAppsColumns: Int,
        var numFolderRows: Int,
        var numFolderColumns: Int,

        var iconSizeFactor: Float,
        var enableIconText: Boolean,
        var iconTextSizeFactor: Float,

        var allAppsIconSizeFactor: Float,
        var enableAllAppsIconText: Boolean,
        var allAppsIconTextSizeFactor: Float,

        val dbFile: String = "launcher_${numRows}_${numColumns}_${numHotseatColumns}.db"
    ) {

        constructor(
            prefs: PreferenceManager,
            preferenceManager2: PreferenceManager2,
            defaultGrid: InvariantDeviceProfile.GridOption,
        ) : this(
            numHotseatColumns = prefs.hotseatColumns.get(defaultGrid),
            numRows = prefs.workspaceRows.get(defaultGrid),
            numColumns = prefs.workspaceColumns.get(defaultGrid),
            numAllAppsColumns = preferenceManager2.drawerColumns.firstBlocking(gridOption = defaultGrid),
            numFolderRows = prefs.folderRows.get(defaultGrid),
            numFolderColumns = preferenceManager2.folderColumns.firstBlocking(gridOption = defaultGrid),

            iconSizeFactor = preferenceManager2.homeIconSizeFactor.firstBlocking(),
            enableIconText = preferenceManager2.showIconLabelsOnHomeScreen.firstBlocking(),
            iconTextSizeFactor = preferenceManager2.homeIconLabelSizeFactor.firstBlocking(),

            allAppsIconSizeFactor = preferenceManager2.drawerIconSizeFactor.firstBlocking(),
            enableAllAppsIconText = preferenceManager2.showIconLabelsInDrawer.firstBlocking(),
            allAppsIconTextSizeFactor = preferenceManager2.drawerIconLabelSizeFactor.firstBlocking(),
        )

        fun apply(idp: InvariantDeviceProfile) {
            // apply grid size
            idp.numShownHotseatIcons = numHotseatColumns
            idp.numDatabaseHotseatIcons = numHotseatColumns
            idp.numRows = numRows
            idp.numColumns = numColumns
            idp.numAllAppsColumns = numAllAppsColumns
            idp.numDatabaseAllAppsColumns = numAllAppsColumns
            idp.numFolderRows = numFolderRows
            idp.numFolderColumns = numFolderColumns

            // apply icon and text size
            idp.iconSize *= iconSizeFactor
            idp.iconTextSize *= (if (enableIconText) iconTextSizeFactor else 0f)
            idp.allAppsIconSize *= allAppsIconSizeFactor
            idp.allAppsIconTextSize *= (if (enableAllAppsIconText) allAppsIconTextSizeFactor else 0f)

            idp.dbFile = dbFile
        }
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::DeviceProfileOverrides)
    }
}
