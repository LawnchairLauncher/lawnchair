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

package ch.deletescape.lawnchair.colors.resolvers

import android.graphics.Color
import android.support.annotation.Keep
import android.support.v4.graphics.ColorUtils
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.colors.WallpaperColorResolver
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.sensors.BrightnessManager
import ch.deletescape.lawnchair.theme.ThemeManager
import com.android.launcher3.R
import com.google.android.apps.nexuslauncher.qsb.HotseatQsbWidget
import kotlin.math.max
import kotlin.math.min

@Keep
class DockQsbAutoResolver(config: Config) : ColorEngine.ColorResolver(config), LawnchairPreferences.OnPreferenceChangeListener, BrightnessManager.OnBrightnessChangeListener {

    override val themeAware = true
    private val isDark get() = ThemeManager.getInstance(engine.context).isDark
    private val lightResolver = DockQsbLightResolver(Config("DockQsbAutoResolver@Light", engine, { _, _ ->
        if (!isDark) notifyChanged()
    }))
    private val darkResolver = DockQsbDarkResolver(Config("DockQsbAutoResolver@Dark", engine, { _, _ ->
        if (isDark) notifyChanged()
    }))
    private val prefs = context.lawnchairPrefs
    private var brightness = 1f

    override fun startListening() {
        super.startListening()
        if (prefs.brightnessTheme) {
            BrightnessManager.getInstance(context).addListener(this)
        }
    }

    override fun onBrightnessChanged(illuminance: Float) {
        brightness = min(max(illuminance - 2f, 0f), 35f) / 35
        notifyChanged()
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        notifyChanged()
    }

    override fun stopListening() {
        super.stopListening()
        BrightnessManager.getInstance(context).removeListener(this)
    }

    override fun resolveColor() = if (prefs.brightnessTheme) {
        ColorUtils.blendARGB(Color.BLACK, Color.WHITE, brightness)
    } else if (isDark) darkResolver.resolveColor() else lightResolver.resolveColor()

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_based)
}

@Keep
class DockQsbLightResolver(config: Config) : WallpaperColorResolver(config), LawnchairPreferences.OnPreferenceChangeListener {

    override fun startListening() {
        super.startListening()
        LawnchairPreferences.getInstanceNoCreate().addOnPreferenceChangeListener(this, HotseatQsbWidget.KEY_DOCK_COLORED_GOOGLE)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (!force) notifyChanged()
    }

    override fun stopListening() {
        super.stopListening()
        LawnchairPreferences.getInstanceNoCreate().removeOnPreferenceChangeListener(this, HotseatQsbWidget.KEY_DOCK_COLORED_GOOGLE)
    }

    override fun resolveColor() = engine.context.resources.getColor(
            if (HotseatQsbWidget.isGoogleColored(context))
                R.color.qsb_background_hotseat_white
            else
                R.color.qsb_background_hotseat_default
    )

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_light)
}

@Keep
class DockQsbDarkResolver(config: Config) : ColorEngine.ColorResolver(config) {

    override fun resolveColor() = engine.context.resources.getColor(R.color.qsb_background_hotseat_dark)

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_dark)
}