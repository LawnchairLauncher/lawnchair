package ch.deletescape.lawnchair.compose.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

data class IconPackInfo(val name: String, val packageName: String, val icon: Drawable)

@Composable
fun IconPackSettings(interactor: SettingsInteractor) {
    Box {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(interactor.getIconPacks().values.toList()) { iconPack ->
                IconPackListItem(
                    iconPack,
                    interactor.iconPackPackage.value,
                    onSelectionChange = { interactor.setIconPackPackage(it) },
                )
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