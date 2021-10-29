package app.lawnchair

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.MainThreadInitializedObject

class DeviceProfileOverrides(context: Context) {
    private val prefs = PreferenceManager.getInstance(context)

    fun getOverrides(defaultGrid: InvariantDeviceProfile.GridOption) = Options(prefs, defaultGrid)

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
            defaultGrid: InvariantDeviceProfile.GridOption,
        ) : this(
            numHotseatColumns = prefs.hotseatColumns.get(defaultGrid),
            numRows = prefs.workspaceRows.get(defaultGrid),
            numColumns = prefs.workspaceColumns.get(defaultGrid),
            numAllAppsColumns = prefs.allAppsColumns.get(defaultGrid),
            numFolderRows = prefs.folderRows.get(defaultGrid),
            numFolderColumns = prefs.folderColumns.get(defaultGrid),

            iconSizeFactor = prefs.iconSizeFactor.get(),
            enableIconText = prefs.showHomeLabels.get(),
            iconTextSizeFactor = prefs.textSizeFactor.get(),

            allAppsIconSizeFactor = prefs.allAppsIconSizeFactor.get(),
            enableAllAppsIconText = prefs.allAppsIconLabels.get(),
            allAppsIconTextSizeFactor = prefs.allAppsTextSizeFactor.get()
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
