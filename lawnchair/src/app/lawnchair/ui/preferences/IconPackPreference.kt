package app.lawnchair.ui.preferences

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.R

data class IconPackInfo(val name: String, val packageName: String, val icon: Drawable)

@ExperimentalAnimationApi
@Composable
fun IconPackPreference(interactor: PreferenceInteractor) {
    val iconPacks = interactor.getIconPacks().values.toList()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroup(isFirstChild = true) {
            // TODO: Use `LazyColumn` if possible.
            Column(Modifier.fillMaxWidth()) {
                iconPacks.forEach { iconPack ->
                    IconPackListItem(
                        iconPack,
                        interactor.iconPackPackage.value,
                        onSelectionChange = { interactor.setIconPackPackage(it) },
                        showDivider = iconPacks.last().packageName != iconPack.packageName
                    )
                }
            }
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun IconPackListItem(
    iconPack: IconPackInfo,
    activeIconPackPackageName: String,
    onSelectionChange: (String) -> Unit,
    showDivider: Boolean = true
) {
    PreferenceTemplate(
        height = 52.dp,
        showDivider = showDivider
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onSelectionChange(iconPack.packageName) }
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Image(
                iconPack.icon.toBitmap().asImageBitmap(),
                null,
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = iconPack.name,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = iconPack.packageName == activeIconPackPackageName) {
                Image(
                    painter = painterResource(id = R.drawable.ic_tick),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.primary)
                )
            }
        }
    }
}