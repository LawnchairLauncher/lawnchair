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
import android.os.Bundle
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.folder.FolderShape
import ch.deletescape.lawnchair.globalsearch.providers.SesameSearchProvider
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.graphics.IconShapeOverride
import ninja.sesame.lib.bridge.v1.SesameFrontend
import ninja.sesame.lib.bridge.v1_1.LookFeelKeys

object Sesame : ColorEngine.OnColorChangeListener, LawnchairPreferences.OnPreferenceChangeListener {
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

    private val syncedColors = arrayOf(
            ColorEngine.Resolvers.ACCENT,
            ColorEngine.Resolvers.HOTSEAT_QSB_BG
    )
    private val syncedPrefs = arrayOf(
            IconShapeOverride.KEY_PREFERENCE,
            "opa_enabled",
            "opa_assistant"
    )

    private lateinit var context: Context

    private val isSesameSearch get() = context.lawnchairPrefs.searchProvider == SesameSearchProvider::class.java.name

    @JvmStatic
    fun setupSync(context: Context) {
        this.context = context
        if (context.lawnchairPrefs.syncLookNFeelWithSesame) {
            ColorEngine.getInstance(context).addColorChangeListeners(this, *syncedColors)
            context.lawnchairPrefs.addOnPreferenceChangeListener(this, *syncedPrefs)
        } else {
            ColorEngine.getInstance(context).removeColorChangeListeners(this, *syncedColors)
            context.lawnchairPrefs.removeOnPreferenceChangeListener(this, *syncedPrefs)
        }
    }

    override fun onColorChange(resolver: String, color: Int, foregroundColor: Int) {
        when (resolver) {
            ColorEngine.Resolvers.HOTSEAT_QSB_BG -> if (isSesameSearch) {
                LookAndFeel[LookFeelKeys.SEARCH_BAR_COLOR] = color
            }
        }
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        when (key) {
            IconShapeOverride.KEY_PREFERENCE -> {
                val edgeRadius = FolderShape.sInstance.mAttrs[R.attr.qsbEdgeRadius]
                if (edgeRadius != null) {
                    LookAndFeel[LookFeelKeys.SEARCH_CORNER_RADIUS] = edgeRadius.getDimension(context.resources.displayMetrics).toInt()
                }
            }
            "opa_enabled" -> if (isSesameSearch) {
                LookAndFeel[LookFeelKeys.SEARCH_HAS_ASSISTANT_ICON] = prefs.showVoiceSearchIcon
            }
            "opa_assistant" -> if (isSesameSearch) {
                LookAndFeel[LookFeelKeys.SEARCH_HAS_ASSISTANT_ICON] = prefs.showVoiceSearchIcon
            }
        }
    }

    object LookAndFeel {
        operator fun set(key: String, value: String?) {
            SesameFrontend.setLookFeelPreferences(Bundle().apply {
                putString(key, value)
            })
        }

        operator fun set(key: String, value: Int) {
            SesameFrontend.setLookFeelPreferences(Bundle().apply {
                putInt(key, value)
            })
        }

        operator fun set(key: String, value: Boolean) {
            SesameFrontend.setLookFeelPreferences(Bundle().apply {
                putBoolean(key, value)
            })
        }

        operator fun get(key: String) = SesameFrontend.getLookFeelPreferences()?.get(key)
    }
}