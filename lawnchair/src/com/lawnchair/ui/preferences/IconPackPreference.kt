package com.lawnchair.ui.preferences

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
fun IconPackPreference(interactor: PreferenceInteractor) {
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
    activeIconPackPackageName: String,
    onSelectionChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onSelectionChange(iconPack.packageName) }
            .padding(start = 16.dp, end = 16.dp)
    ) {
        RadioButton(
            selected = iconPack.packageName == activeIconPackPackageName,
            { onSelectionChange(iconPack.packageName) })
        Image(
            iconPack.icon.toBitmap().asImageBitmap(),
            null,
            modifier = Modifier
                .padding(start = 32.dp)
                .width(36.dp)
                .height(36.dp)
        )
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = iconPack.name,
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onBackground
        )
    }
}