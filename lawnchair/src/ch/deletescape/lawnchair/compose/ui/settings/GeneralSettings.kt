package ch.deletescape.lawnchair.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import ch.deletescape.lawnchair.sharedprefs.LawnchairPreferences
import com.android.launcher3.R
import com.android.launcher3.Utilities

@Composable
fun GeneralSettings(navController: NavController) {
    val context = LocalContext.current
    val sharedPref = Utilities.getPrefs(context)

    val dbPrefAllowRotation = sharedPref.getBoolean("pref_allowRotation", true)
    var allowRotation by remember { mutableStateOf(dbPrefAllowRotation) }

    val dbGenerateAdaptiveIconsForIconPacks = sharedPref.getBoolean(LawnchairPreferences.WRAP_ADAPTIVE_ICONS, false)
    var generateAdaptiveIconsForIconPacks by remember { mutableStateOf(dbGenerateAdaptiveIconsForIconPacks) }

    Column {
        SwitchSetting(
            checked = allowRotation,
            onCheckedChange = {
                allowRotation = it
                sharedPref.edit().putBoolean("pref_allowRotation", it).apply()
            },
            title = stringResource(id = R.string.home_screen_rotation_label),
            subtitle = stringResource(id = R.string.home_screen_rotaton_description)
        )
        SwitchSetting(
            checked = generateAdaptiveIconsForIconPacks,
            onCheckedChange = {
                generateAdaptiveIconsForIconPacks = it
                sharedPref.edit().putBoolean(LawnchairPreferences.WRAP_ADAPTIVE_ICONS, it).apply()
            },
            title = stringResource(id = R.string.make_icon_packs_adaptive_label),
            subtitle = stringResource(id = R.string.make_icon_packs_adaptive_description)
        )
        NavActionSetting(
            title = stringResource(id = R.string.icon_pack),
            navController = navController,
            destination = Screen.IconPackSettings.route
        )
    }
}