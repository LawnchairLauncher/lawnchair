package app.lawnchair.ui.preferences

import androidx.compose.runtime.State

interface PreferenceInteractor {
    val iconPackPackage: State<String>
    val allowRotation: State<Boolean>
    val wrapAdaptiveIcons: State<Boolean>
    val addIconToHome: State<Boolean>
    val hotseatColumns: State<Float>
    val workspaceColumns: State<Float>
    val workspaceRows: State<Float>
    val folderColumns: State<Float>
    val folderRows: State<Float>
    val iconSizeFactor: State<Float>
    val textSizeFactor: State<Float>
    val allAppsIconSizeFactor: State<Float>
    val allAppsTextSizeFactor: State<Float>
    val allAppsColumns: State<Float>
    val allowEmptyPages: State<Boolean>
    val makeColoredBackgrounds: State<Boolean>
    val notificationDotsEnabled: State<Boolean>
    val drawerOpacity: State<Float>

    fun setIconPackPackage(iconPackPackage: String)
    fun setAllowRotation(allowRotation: Boolean)
    fun setWrapAdaptiveIcons(wrapAdaptiveIcons: Boolean)
    fun setAddIconToHome(addIconToHome: Boolean)
    fun setHotseatColumns(hotseatColumns: Float)
    fun setWorkspaceColumns(workspaceColumns: Float)
    fun setWorkspaceRows(workspaceRows: Float)
    fun setFolderColumns(folderColumns: Float)
    fun setFolderRows(folderRows: Float)
    fun setIconSizeFactor(iconSizeFactor: Float)
    fun setTextSizeFactor(textSizeFactor: Float)
    fun setAllAppsIconSizeFactor(allAppsIconSizeFactor: Float)
    fun setAllAppsTextSizeFactor(allAppsTextSizeFactor: Float)
    fun setAllAppsColumns(allAppsColumns: Float)
    fun setAllowEmptyPages(allowEmptyPages: Boolean)
    fun setMakeColoredBackgrounds(makeColoredBackgrounds: Boolean)
    fun setDrawerOpacity(drawerOpacity: Float)


    fun getIconPacks(): MutableMap<String, IconPackInfo>
}