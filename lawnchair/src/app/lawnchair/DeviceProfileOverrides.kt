package app.lawnchair

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.MainThreadInitializedObject

class DeviceProfileOverrides(context: Context) {
    private val prefs = PreferenceManager.getInstance(context)

    fun apply(idp: InvariantDeviceProfile, defaultGrid: InvariantDeviceProfile.GridOption) {
        // apply grid size
        idp.numShownHotseatIcons = prefs.hotseatColumns.get(defaultGrid)
        idp.numRows = prefs.workspaceRows.get(defaultGrid)
        idp.numColumns = prefs.workspaceColumns.get(defaultGrid)
        idp.numAllAppsColumns = prefs.allAppsColumns.get(defaultGrid)
        idp.numFolderRows = prefs.folderRows.get(defaultGrid)
        idp.numFolderColumns = prefs.folderColumns.get(defaultGrid)

        // apply icon and text size
        idp.iconSize *= prefs.iconSizeFactor.get()
        idp.iconTextSize *= (if (prefs.showHomeLabels.get()) prefs.textSizeFactor.get() else 0.0F)
        idp.allAppsIconSize *= prefs.allAppsIconSizeFactor.get()
        idp.allAppsIconTextSize *= (if (prefs.allAppsIconLabels.get()) prefs.allAppsTextSizeFactor.get() else 0F)

        idp.dbFile = "launcher_${idp.numRows}_${idp.numColumns}_${idp.numShownHotseatIcons}.db"
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::DeviceProfileOverrides)
    }
}
