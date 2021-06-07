package app.lawnchair

import android.content.Context
import android.util.DisplayMetrics
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.MainThreadInitializedObject

class DeviceProfileOverrides(context: Context) {
    private val prefs = PreferenceManager.getInstance(context)

    fun apply(idp: InvariantDeviceProfile, dm: DisplayMetrics) {
        idp.numHotseatIcons = prefs.hotseatColumns.get(idp)
        idp.numRows = prefs.workspaceRows.get(idp)
        idp.numColumns = prefs.workspaceColumns.get(idp)
        idp.numAllAppsColumns = prefs.allAppsColumns.get(idp)
        idp.numFolderRows = prefs.folderRows.get(idp)
        idp.numFolderColumns = prefs.folderColumns.get(idp)
    }

    companion object {
        @JvmStatic
        val INSTANCE = MainThreadInitializedObject(::DeviceProfileOverrides)
    }
}
