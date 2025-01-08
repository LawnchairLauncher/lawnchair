package app.lawnchair.ui.popup

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.preferences2.PreferenceManager2.Companion.getInstance
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking

object LauncherOptionsPopup {
    const val DEFAULT_ORDER = "-lock|-edit_mode|+wallpaper|+widgets|+home_settings|-sys_settings"

    /**
     * Returns the list of supported actions
     */
    fun getLauncherOptions(
        launcher: Launcher?,
        onLockToggle: (View) -> Boolean,
        onStartSystemSettings: (View) -> Boolean,
        onStartEditMode: (View) -> Boolean,
        onStartWallpaperPicker: (View) -> Boolean,
        onStartWidgetsMenu: (View) -> Boolean,
        onStartHomeSettings: (View) -> Boolean,
    ): ArrayList<OptionItem> {
        val prefs2 = getInstance(launcher!!)
        val lockHomeScreen = prefs2.lockHomeScreen.firstBlocking()
        val optionOrder = prefs2.launcherPopupOrder.firstBlocking()

        if (lockHomeScreen) {
            prefs2.launcherPopupOrder.setBlocking(
                optionOrder.split("|")
                    .joinToString("|") { item ->
                        when (item) {
                            "+edit_mode" -> "-edit_mode"
                            "+widgets" -> "-widgets"
                            else -> item
                        }
                    },
            )
        }

        val wallpaperResString =
            if (Utilities.existsStyleWallpapers(launcher)) R.string.styles_wallpaper_button_text else R.string.wallpapers
        val wallpaperResDrawable =
            if (Utilities.existsStyleWallpapers(launcher)) R.drawable.ic_palette else R.drawable.ic_wallpaper

        val optionsList = mapOf(
            "lock" to OptionItem(
                launcher,
                if (lockHomeScreen) R.string.home_screen_unlock else R.string.home_screen_lock,
                if (lockHomeScreen) R.drawable.ic_lock_open else R.drawable.ic_lock,
                LauncherEvent.IGNORE,
                onLockToggle,
            ),
            "sys_settings" to OptionItem(
                launcher,
                R.string.system_settings,
                R.drawable.ic_setting,
                LauncherEvent.IGNORE,
                onStartSystemSettings,
            ),
            "edit_mode" to OptionItem(
                launcher,
                R.string.edit_home_screen,
                R.drawable.enter_home_gardening_icon,
                LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                onStartEditMode,
            ),
            "wallpaper" to OptionItem(
                launcher,
                wallpaperResString,
                wallpaperResDrawable,
                LauncherEvent.IGNORE,
                onStartWallpaperPicker,
            ),
            "widgets" to OptionItem(
                launcher,
                R.string.widget_button_text,
                R.drawable.ic_widget,
                LauncherEvent.LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS,
                onStartWidgetsMenu,
            ),
            "home_settings" to OptionItem(
                launcher,
                R.string.settings_button_text,
                R.drawable.ic_home_screen,
                LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                onStartHomeSettings,
            ),
        )

        val options = ArrayList<OptionItem>()
        optionOrder.split("|").forEach { item ->
            val (identifier, isEnabled) = when {
                item.startsWith("+") -> item.drop(1) to true
                item.startsWith("-") -> item.drop(1) to false
                else -> item to true // Default to enabled if no prefix
            }
            if (isEnabled) {
                optionsList[identifier]?.let { option ->
                    options.add(option)
                }
            }
        }

        return options
    }

    fun getMetadataForOption(identifier: String): LauncherOptionMetadata {
        return when (identifier) {
            "lock" -> LauncherOptionMetadata(
                label = R.string.home_screen_lock,
                icon = R.drawable.ic_lock,
            )
            "sys_settings" -> LauncherOptionMetadata(
                label = R.string.system_settings,
                icon = R.drawable.ic_setting,
            )
            "edit_mode" -> LauncherOptionMetadata(
                label = R.string.edit_home_screen,
                icon = R.drawable.enter_home_gardening_icon,
            )
            "wallpaper" -> LauncherOptionMetadata(
                label = R.string.styles_wallpaper_button_text,
                icon = R.drawable.ic_palette,
            )
            "widgets" -> LauncherOptionMetadata(
                label = R.string.widget_button_text,
                icon = R.drawable.ic_widget,
            )
            "home_settings" -> LauncherOptionMetadata(
                label = R.string.settings_button_text,
                icon = R.drawable.ic_home_screen,
            )
            else -> throw IllegalArgumentException("invalid popup option")
        }
    }
}

data class LauncherOptionPopupItem(
    val identifier: String,
    var isEnabled: Boolean,
)

data class LauncherOptionMetadata(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
)

fun String.toLauncherOptions(): List<LauncherOptionPopupItem> {
    return this.split("|").map { item ->
        val (identifier, isEnabled) = when {
            item.startsWith("+") -> item.drop(1) to true
            item.startsWith("-") -> item.drop(1) to false
            else -> item to true // Default to enabled if no prefix
        }
        LauncherOptionPopupItem(identifier, isEnabled)
    }
}

fun List<LauncherOptionPopupItem>.toOptionOrderString(): String {
    return this.joinToString("|") {
        if (it.isEnabled) "+${it.identifier}" else "-${it.identifier}"
    }
}
