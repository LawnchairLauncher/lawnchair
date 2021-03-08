package ch.deletescape.lawnchair.compose.ui.settings

import androidx.compose.runtime.State

interface SettingsInteractor {
    val iconPackPackage: State<String>
    val allowRotation: State<Boolean>
    val wrapAdaptiveIcons: State<Boolean>
    val addIconToHome: State<Boolean>

    fun setIconPackPackage(iconPackPackage: String)
    fun setAllowRotation(allowRotation: Boolean)
    fun setWrapAdaptiveIcons(wrapAdaptiveIcons: Boolean)
    fun setAddIconToHome(addIconToHome: Boolean)

    fun getIconPacks(): MutableMap<String, IconPackInfo>
}