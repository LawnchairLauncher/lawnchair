/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    val notificationDotsEnabled: State<Boolean>
    val drawerOpacity: State<Float>
    val coloredBackgroundLightness: State<Float>

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
    fun setDrawerOpacity(drawerOpacity: Float)
    fun setColoredBackgroundLightness(coloredBackgroundLightness: Float)

    fun getIconPacks(): MutableMap<String, IconPackInfo>
}