/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.sesame

import android.content.Context
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.globalsearch.providers.SesameSearchProvider
import com.android.launcher3.BuildConfig
import ninja.sesame.lib.bridge.v1.SesameFrontend

object Sesame {
    const val PACKAGE = "ninja.sesame.app.edge"
    const val EXTRA_TAG = "ch.deletescape.lawnchair.QUINOA"
    const val ACTION_OPEN_SETTINGS = "ninja.sesame.app.action.OPEN_SETTINGS"

    @JvmStatic
    val SEARCH_PROVIDER_CLASS: String = SesameSearchProvider::class.java.name

    @JvmStatic
    fun isAvailable(context: Context) = BuildConfig.FEATURE_QUINOA &&
                                        SesameFrontend.isConnected() &&
                                        SesameFrontend.getIntegrationState(context)
    @JvmStatic
    var showShortcuts by LawnchairPreferences.getInstanceNoCreate().BooleanPref("pref_sesame_show_shortcuts", true)
}