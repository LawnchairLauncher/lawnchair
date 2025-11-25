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
    val DEFAULT_ORDER = listOf(
        LauncherOptionPopupItem("carousel", true),
        LauncherOptionPopupItem("lock", false),
        LauncherOptionPopupItem("edit_mode", false),
        LauncherOptionPopupItem("wallpaper", true),
        LauncherOptionPopupItem("widgets", true),
        LauncherOptionPopupItem("home_settings", true),
        LauncherOptionPopupItem("sys_settings", false),
    )

    fun restoreMissingPopupOptions(
        launcher: Launcher,
    ) {
        val prefs2 = getInstance(launcher)

        val currentOrder = prefs2.launcherPopupOrder.firstBlocking()
        val currentOptions = currentOrder.toLauncherOptions()

        // check for missing items in current options; if so, add them
        val missingItems = DEFAULT_ORDER.filter { defaultItem ->
            defaultItem.identifier !in currentOptions.map { it.identifier }
        }

        prefs2.launcherPopupOrder.setBlocking(
            (missingItems + currentOptions).toOptionOrderString(),
        )
    }

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
        val optionOrder = prefs2
            .launcherPopupOrder.firstBlocking().toLauncherOptions()

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
        optionOrder
            .filter {
                (it.isEnabled && it.identifier != "carousel")
            }
            .filter {
                if (lockHomeScreen) {
                    it.identifier != "edit_mode" && it.identifier != "widgets"
                } else {
                    true
                }
            }
            .mapNotNull { optionsList[it.identifier] }
            .forEach { options.add(it) }

        return options
    }

    fun getMetadataForOption(identifier: String): LauncherOptionMetadata {
        return when (identifier) {
            "carousel" -> LauncherOptionMetadata(
                label = R.string.wallpaper_quick_picker,
                icon = R.drawable.ic_wallpaper,
                isCarousel = true,
            )

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

    fun migrateLegacyPreferences(
        launcher: Launcher,
    ) {
        val prefs2 = getInstance(launcher)

        val lockHomeScreenButtonOnPopUp = prefs2.lockHomeScreenButtonOnPopUp.firstBlocking()
        val editHomeScreenButtonOnPopUp = prefs2.editHomeScreenButtonOnPopUp.firstBlocking()
        val showSystemSettingsEntryOnPopUp = prefs2.showSystemSettingsEntryOnPopUp.firstBlocking()

        val optionOrder = prefs2.launcherPopupOrder
        val legacyPopupOptionsMigrated = prefs2.legacyPopupOptionsMigrated.firstBlocking()

        if (!legacyPopupOptionsMigrated) {
            prefs2.legacyPopupOptionsMigrated.setBlocking(true)

            val options = optionOrder.firstBlocking().toLauncherOptions()

            options.forEachIndexed { index, item ->
                if (item.identifier == "lock") {
                    options[index].isEnabled = lockHomeScreenButtonOnPopUp
                }
                if (item.identifier == "edit_mode") {
                    options[index].isEnabled = editHomeScreenButtonOnPopUp
                }
                if (item.identifier == "sys_settings") {
                    options[index].isEnabled = showSystemSettingsEntryOnPopUp
                }
            }

            optionOrder.setBlocking(options.toOptionOrderString())
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
    val isCarousel: Boolean = false,
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
