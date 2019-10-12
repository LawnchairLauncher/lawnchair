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
import ch.deletescape.lawnchair.dpToPx
import ch.deletescape.lawnchair.globalsearch.providers.SesameSearchProvider
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.util.diff.diff
import com.android.launcher3.BuildConfig
import com.android.launcher3.graphics.IconShapeOverride
import com.google.android.apps.nexuslauncher.qsb.AbstractQsbLayout
import ninja.sesame.lib.bridge.v1.SesameFrontend
import ninja.sesame.lib.bridge.v1_1.LookFeelKeys
import ninja.sesame.lib.bridge.v1_2.LookFeelOnChange
import kotlin.math.roundToInt

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
            "opa_assistant",
            "pref_searchbarRadius"
    )

    private lateinit var context: Context

    private val isSesameSearch get() = context.lawnchairPrefs.searchProvider == SesameSearchProvider::class.java.name

    @JvmStatic
    fun setupSync(context: Context) {
        this.context = context
        if (context.lawnchairPrefs.syncLookNFeelWithSesame) {
            setListeners(context)
            val bundle = SesameFrontend.getLookFeelPreferences()
            if (bundle != null) {
                SesameFrontend.setLookFeelOnChangeListener(LookFeelSync(context, bundle) {
                    if (it) {
                        setListeners(context)
                    } else {
                        unsetListeners(context)
                    }
                })
            }
        } else {
            unsetListeners(context)
            SesameFrontend.setLookFeelOnChangeListener(null)
        }
    }

    private fun setListeners(context: Context) {
        ColorEngine.getInstance(context).addColorChangeListeners(this, *syncedColors)
        context.lawnchairPrefs.addOnPreferenceChangeListener(this, *syncedPrefs)
    }

    private fun unsetListeners(context: Context) {
        ColorEngine.getInstance(context).removeColorChangeListeners(this, *syncedColors)
        context.lawnchairPrefs.removeOnPreferenceChangeListener(this, *syncedPrefs)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        when (resolveInfo.key) {
            ColorEngine.Resolvers.HOTSEAT_QSB_BG -> if (isSesameSearch) {
                LookAndFeel[LookFeelKeys.SEARCH_BAR_COLOR] = resolveInfo.color
            }
        }
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        when (key) {
            IconShapeOverride.KEY_PREFERENCE -> {
                LookAndFeel[LookFeelKeys.SEARCH_CORNER_RADIUS] = AbstractQsbLayout.getCornerRadius(context, dpToPx(24f)).roundToInt()
            }
            "pref_searchbarRadius" -> {
                LookAndFeel[LookFeelKeys.SEARCH_CORNER_RADIUS] = AbstractQsbLayout.getCornerRadius(context, dpToPx(24f)).roundToInt()
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

    class LookFeelSync(private val context: Context, private var previous: Bundle, private val setUnsetListeners: (enable: Boolean) -> Unit): LookFeelOnChange {
        private val prefs = context.lawnchairPrefs
        private val colors = ColorEngine.getInstance(context)

        override fun onChange(bundle: Bundle) {
            // Pause listeners
            setUnsetListeners(false)

            val diff = previous diff bundle
            for (key in diff.changed) when (key) {
                LookFeelKeys.SEARCH_HAS_ASSISTANT_ICON -> prefs.showVoiceSearchIcon = LookAndFeel[key] as Boolean
                LookFeelKeys.SEARCH_BAR_COLOR -> colors.setColor(ColorEngine.Resolvers.HOTSEAT_QSB_BG, LookAndFeel[key] as Int)
                LookFeelKeys.SEARCH_ICON_COLOR -> prefs.sesameIconColor = LookAndFeel[key] as Int
                LookFeelKeys.SEARCH_CORNER_RADIUS -> prefs.searchBarRadius = (LookAndFeel[LookFeelKeys.SEARCH_CORNER_RADIUS] as Int).toFloat()
            }
            previous = bundle

            // Unpause listeners
            setUnsetListeners(true)
        }
    }
}