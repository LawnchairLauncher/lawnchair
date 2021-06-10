package app.lawnchair

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.MainThreadInitializedObject

class DeviceProfileOverrides(context: Context) {
    private val prefs = PreferenceManager.getInstance(context)

    fun apply(idp: InvariantDeviceProfile, defaultGrid: InvariantDeviceProfile.GridOption) {
        // set db file to use
        val dbNameSuffix = if (prefs.currentDbSlot.get() == "a") "" else "_b"
        idp.dbFile = idp.dbFile + dbNameSuffix

        // apply grid size
        idp.numHotseatIcons = prefs.hotseatColumns.get(defaultGrid)
        idp.numRows = prefs.workspaceRows.get(defaultGrid)
        idp.numColumns = prefs.workspaceColumns.get(defaultGrid)
        idp.numAllAppsColumns = prefs.allAppsColumns.get(defaultGrid)
        idp.numFolderRows = prefs.folderRows.get(defaultGrid)
        idp.numFolderColumns = prefs.folderColumns.get(defaultGrid)

        // apply icon and text size
        idp.iconSize *= prefs.iconSizeFactor.get()
        idp.iconTextSize *= prefs.textSizeFactor.get()
        idp.allAppsIconSize *= prefs.allAppsIconSizeFactor.get()
        idp.allAppsIconTextSize *= prefs.allAppsTextSizeFactor.get()
    }

    companion object {
        @JvmStatic
        val INSTANCE = MainThreadInitializedObject(::DeviceProfileOverrides)
    }
}
