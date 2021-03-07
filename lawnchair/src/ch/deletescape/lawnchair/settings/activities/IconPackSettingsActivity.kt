package ch.deletescape.lawnchair.settings.activities

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ch.deletescape.lawnchair.compose.ui.theme.LawnchairTheme
import ch.deletescape.lawnchair.sharedprefs.LawnchairPreferences
import com.android.launcher3.R
import com.android.launcher3.Utilities

class IconPackSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                Surface(
                    color = MaterialTheme.colors.background,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    IconPackSettings()
                }
            }
        }
    }

    data class IconPackInfo(val name: String, val packageName: String, val icon: Drawable)

    private fun getIconPacks(): MutableMap<String, IconPackInfo> {
        val pm = this.packageManager
        val iconPacks: MutableMap<String, IconPackInfo> = HashMap()
        val list: MutableList<ResolveInfo> = pm.queryIntentActivities(Intent("com.novalauncher.THEME"), 0)

        list.addAll(pm.queryIntentActivities(Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0))
        list.addAll(pm.queryIntentActivities(Intent("com.dlto.atom.launcher.THEME"), 0))
        list.addAll(pm.queryIntentActivities(Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0))

        // TODO: Ensure ‘System Icons’ entry is first in list.
        iconPacks["system"] = IconPackInfo(
            "System Icons",
            "",
            AppCompatResources.getDrawable(this, R.drawable.ic_launcher_home)!!
        )

        for (info in list) {
            iconPacks[info.activityInfo.packageName] = IconPackInfo(
                info.loadLabel(pm).toString(),
                info.activityInfo.packageName,
                info.loadIcon(pm)
            )
        }

        return iconPacks
    }

    private val sharedPref: SharedPreferences by lazy { Utilities.getPrefs(this) }
    private val dbSelectedIconPackPackageName by lazy { sharedPref.getString(LawnchairPreferences.ICON_PACK_PACKAGE, "")!! }

    @Composable
    fun IconPackSettings() {
        var selectedIconPackPackageName by remember { mutableStateOf(dbSelectedIconPackPackageName) }

        Column {
            TopAppBar(
                title = { Text(text = "Icon Packs") },
                backgroundColor = MaterialTheme.colors.background,
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .height(40.dp)
                            .width(40.dp)
                            .clip(shape = RoundedCornerShape(20.dp))
                            .clickable { finish() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            "Back",
                            modifier = Modifier,
                            MaterialTheme.colors.onBackground
                        )
                    }
                }
            )
            IconPackList(
                getIconPacks(),
                selectedIconPackPackageName,
                onSelectionChange = { selectedIconPackPackageName = it })
        }
    }

    @Composable
    fun IconPackList(
        iconPacks: MutableMap<String, IconPackInfo>,
        selectedIconPackPackageName: String,
        modifier: Modifier = Modifier,
        onSelectionChange: (String) -> Unit
    ) {
        Box(modifier) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(iconPacks.values.toList()) { iconPack ->
                    IconPackListItem(iconPack, selectedIconPackPackageName, onSelectionChange)
                }
            }
        }
    }

    @Composable
    fun IconPackListItem(
        iconPack: IconPackInfo,
        selectedIconPackPackageName: String,
        onSelectionChange: (String) -> Unit
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
                style = MaterialTheme.typography.body1
            )
        }
    }

}