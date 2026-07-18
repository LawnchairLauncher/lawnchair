/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.folder

import android.content.Context
import app.lawnchair.LawnchairApp
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.util.Executors

/** Reads the active closed-folder preview grid preference for AOSP callers. */
object FolderPreviewConfig {

    @JvmStatic
    fun getActiveItemCount(context: Context): Int =
        PreferenceManager2.getInstance(context).folderPreviewGridSize.firstCached().itemCount

    @JvmStatic
    fun getGridSide(context: Context): Int =
        PreferenceManager2.getInstance(context).folderPreviewGridSize.firstCached().sideLength

    /**
     * Reapplies the preview grid on workspace/hotseat folder icons and rebinds the app drawer
     * so folder previews update without restarting the launcher.
     */
    @JvmStatic
    fun refreshAllFolderIcons(context: Context) {
        Executors.MAIN_EXECUTOR.execute {
            val launcher = LawnchairApp.launcher ?: return@execute
            launcher.workspace.mapOverItems { _, view ->
                if (view is FolderIcon) {
                    view.refreshPreviewGrid()
                }
                false
            }
            launcher.appsView?.rebindAdaptersForFolderPreviewChange()
        }
    }
}
