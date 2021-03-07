package ch.deletescape.lawnchair.compose.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ch.deletescape.lawnchair.sharedprefs.LawnchairPreferences
import com.android.launcher3.R
import com.android.launcher3.Utilities

data class IconPackInfo(val name: String, val packageName: String, val icon: Drawable)

@Composable
fun IconPackSettings() {
    val context = LocalContext.current

    val sharedPref: SharedPreferences by lazy { Utilities.getPrefs(context) }
    val dbSelectedIconPackPackageName by lazy { sharedPref.getString(LawnchairPreferences.ICON_PACK_PACKAGE, "")!! }

    var selectedIconPackPackageName by remember { mutableStateOf(dbSelectedIconPackPackageName) }

    fun getIconPacks(): MutableMap<String, IconPackInfo> {
        val pm = context.packageManager
        val iconPacks: MutableMap<String, IconPackInfo> = HashMap()
        val list: MutableList<ResolveInfo> = pm.queryIntentActivities(Intent("com.novalauncher.THEME"), 0)

        list.addAll(pm.queryIntentActivities(Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0))
        list.addAll(pm.queryIntentActivities(Intent("com.dlto.atom.launcher.THEME"), 0))
        list.addAll(
            pm.queryIntentActivities(Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0)
        )

        iconPacks["system"] =
            IconPackInfo("System Icons", "", AppCompatResources.getDrawable(context, R.drawable.ic_launcher_home)!!)

        for (info in list) {
            iconPacks[info.activityInfo.packageName] = IconPackInfo(
                info.loadLabel(pm).toString(),
                info.activityInfo.packageName,
                info.loadIcon(pm)
            )
        }

        return iconPacks
    }

    Box {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(getIconPacks().values.toList()) { iconPack ->
                IconPackListItem(
                    iconPack,
                    selectedIconPackPackageName,
                    onSelectionChange = { selectedIconPackPackageName = it },
                    sharedPref
                )
            }
        }
    }
}

@Composable
fun IconPackListItem(
    iconPack: IconPackInfo,
    selectedIconPackPackageName: String,
    onSelectionChange: (String) -> Unit,
    sharedPref: SharedPreferences
) {
    fun select() {
        onSelectionChange(iconPack.packageName)
        sharedPref.edit().putString(LawnchairPreferences.ICON_PACK_PACKAGE, iconPack.packageName).apply()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { select() }
            .padding(start = 16.dp)
    ) {
        RadioButton(
            selected = iconPack.packageName == selectedIconPackPackageName,
            { select() })
        Image(
            iconPack.icon.toBitmap().asImageBitmap(),
            iconPack.name,
            modifier = Modifier
                .padding(start = 32.dp)
                .width(36.dp)
                .height(36.dp)
        )
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = iconPack.name,
            style = MaterialTheme.typography.subtitle1
        )
    }
}