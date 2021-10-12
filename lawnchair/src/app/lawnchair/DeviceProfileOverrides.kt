package app.lawnchair

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.MainThreadInitializedObject

class DeviceProfileOverrides(context: Context) {
    private val prefs = PreferenceManager.getInstance(context)

    fun apply(idp: InvariantDeviceProfile, defaultGrid: InvariantDeviceProfile.GridOption) {
        val options = Options(prefs, defaultGrid)

        // apply grid size
        idp.numShownHotseatIcons = options.numHotseatColumns
        idp.numRows = options.numRows
        idp.numColumns = options.numColumns
        idp.numAllAppsColumns = options.numAllAppsColumns
        idp.numFolderRows = options.numFolderRows
        idp.numFolderColumns = options.numFolderColumns

        // apply icon and text size
        idp.iconSize *= options.iconSizeFactor
        idp.iconTextSize *= (if (options.enableIconText) options.iconTextSizeFactor else 0f)
        idp.allAppsIconSize *= options.allAppsIconSizeFactor
        idp.allAppsIconTextSize *= (if (options.enableAllAppsIconText) options.allAppsIconTextSizeFactor else 0f)

        idp.dbFile = "launcher_${idp.numRows}_${idp.numColumns}_${idp.numShownHotseatIcons}.db"
    }

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
        var allAppsIconTextSizeFactor: Float
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
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::DeviceProfileOverrides)
    }
}
