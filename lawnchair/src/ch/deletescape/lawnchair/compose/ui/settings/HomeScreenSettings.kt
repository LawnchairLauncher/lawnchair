package ch.deletescape.lawnchair.compose.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.launcher3.LauncherFiles.SHARED_PREFERENCES_KEY
import com.android.launcher3.R

@Composable
fun HomeScreenSettings() {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    val dbPrefAddIconToHome = sharedPref.getBoolean("pref_add_icon_to_home", false)
    var addIconToHome by remember { mutableStateOf(dbPrefAddIconToHome) }

    Column {
        SwitchSetting(
            checked = addIconToHome,
            onCheckedChange = {
                addIconToHome = it
                sharedPref.edit().putBoolean("pref_add_icon_to_home", it).apply()
            },
            title = stringResource(id = R.string.auto_add_shortcuts_label)
        )
    }
}
