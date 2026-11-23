package app.lawnchair.ui.popup

import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.preferences2.PreferenceManager2.Companion.getInstance
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.patrykmichalik.opto.core.setBlocking
import kotlinx.coroutines.flow.first

object LauncherOptionsPopup {
    val DEFAULT_ORDER = listOf(
        LauncherOptionPopupItem("carousel", true),
        LauncherOptionPopupItem("lock", false),
        LauncherOptionPopupItem("edit_mode", false),
        LauncherOptionPopupItem("wallpaper", true),
        LauncherOptionPopupItem("widgets", true),
        LauncherOptionPopupItem("all_apps", true),
        LauncherOptionPopupItem("home_settings", true),
        LauncherOptionPopupItem("sys_settings", false),
        LauncherOptionPopupItem("default_page", false),
    )

    suspend fun restoreMissingPopupOptions(
        launcher: Launcher,
    ) {
        val prefs2 = getInstance(launcher)

        val currentOrder = prefs2.launcherPopupOrder.get().first()
        val currentOptions = currentOrder.toLauncherOptions()
        val existingIds = currentOptions.mapTo(HashSet()) { it.identifier }

        // check for missing items in current options if so, add them
        val missingItems = DEFAULT_ORDER.filter { defaultItem ->
            defaultItem.identifier !in existingIds
        }

        if (missingItems.isNotEmpty()) {
            prefs2.launcherPopupOrder.set(
                (currentOptions + missingItems).toOptionOrderString(),
            )
        }
    }

    /**
     * Returns the list of supported actions
     */
    fun getLauncherOptions(
        launcher: Launcher?,
        onLockToggle: (View) -> Boolean,
        onStartSystemSettings: (View) -> Boolean,
        onStartEditMode: (View) -> Boolean,
        onStartAllApps: (View) -> Boolean,
        onStartWallpaperPicker: (View) -> Boolean,
        onStartWidgetsMenu: (View) -> Boolean,
        onStartHomeSettings: (View) -> Boolean,
    ): ArrayList<OptionItem> {
        val prefs2 = getInstance(launcher!!)
        val lockHomeScreen = prefs2.lockHomeScreen.firstCached()
        val optionOrder = prefs2
            .launcherPopupOrder.firstCached().toLauncherOptions()

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
            "all_apps" to OptionItem(
                launcher,
                R.string.all_apps_button_label,
                R.drawable.ic_apps,
                LauncherEvent.LAUNCHER_ALL_APPS_TAP_OR_LONGPRESS,
                onStartAllApps,
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
                SystemShortcut.Widgets.getDrawableId(),
                LauncherEvent.LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS,
                onStartWidgetsMenu,
            ),
            "enterAllApps" to OptionItem(
                launcher,
                R.string.all_apps_button_label,
                R.drawable.ic_apps,
                LauncherEvent.LAUNCHER_ALL_APPS_TAP_OR_LONGPRESS,
                onStartAllApps,
            ),
            "home_settings" to OptionItem(
                launcher,
                R.string.settings_button_text,
                R.drawable.ic_home_screen,
                LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                onStartHomeSettings,
            ),
            "default_page" to OptionItem(
                launcher,
                R.string.set_default_home_page,
                R.drawable.ic_home_pin,
                LauncherEvent.IGNORE,
                ::setAsDefaultHomePage,
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
            .filter { it.identifier != "default_page" || !launcher.workspace.isCurrentPageDefault }
            .mapNotNull { optionsList[it.identifier] }
            .forEach { options.add(it) }

        return options
    }

    private fun setAsDefaultHomePage(v: View): Boolean {
        val launcher = Launcher.getLauncher(v.context)
        val currentPage = launcher.workspace.getNextPage()
        launcher.workspace.setDefaultPage(currentPage)
        Toast.makeText(launcher, R.string.default_home_page_set, Toast.LENGTH_SHORT).show()
        return true
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
                icon = SystemShortcut.Widgets.getDrawableId(),
            )

            "all_apps" -> LauncherOptionMetadata(
                label = R.string.all_apps_button_label,
                icon = R.drawable.ic_apps,
            )

            "home_settings" -> LauncherOptionMetadata(
                label = R.string.settings_button_text,
                icon = R.drawable.ic_home_screen,
            )

            "default_page" -> LauncherOptionMetadata(
                label = R.string.set_default_home_page,
                icon = R.drawable.ic_home_pin,
            )

            else -> throw IllegalArgumentException("invalid popup option")
        }
    }

    suspend fun migrateLegacyPreferences(
        launcher: Launcher,
    ) {
        val prefs2 = getInstance(launcher)

        if (!prefs2.legacyPopupOptionsMigrated.get().first()) {
            val lockHomeScreenButtonOnPopUp = prefs2.lockHomeScreenButtonOnPopUp.get().first()
            val editHomeScreenButtonOnPopUp = prefs2.editHomeScreenButtonOnPopUp.get().first()
            val showSystemSettingsEntryOnPopUp = prefs2.showSystemSettingsEntryOnPopUp.get().first()

            val optionOrder = prefs2.launcherPopupOrder
            val options = optionOrder.get().first().toLauncherOptions()

            options.forEach { item ->
                when (item.identifier) {
                    "lock" -> item.isEnabled = lockHomeScreenButtonOnPopUp
                    "edit_mode" -> item.isEnabled = editHomeScreenButtonOnPopUp
                    "sys_settings" -> item.isEnabled = showSystemSettingsEntryOnPopUp
                }
            }

            optionOrder.set(options.toOptionOrderString())
            prefs2.legacyPopupOptionsMigrated.set(true)
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
