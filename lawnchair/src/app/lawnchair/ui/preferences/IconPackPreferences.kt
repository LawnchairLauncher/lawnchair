/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavGraphBuilder
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.util.Meta
import app.lawnchair.util.pageMeta
import app.lawnchair.util.preferences.getAdapter
import app.lawnchair.util.preferences.preferenceManager
import com.android.launcher3.R

data class IconPackInfo(val name: String, val packageName: String, val icon: Drawable)

@ExperimentalAnimationApi
fun NavGraphBuilder.iconPackGraph(route: String) {
    preferenceGraph(route, { IconPackPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun IconPackPreferences() {
    val interactor = LocalPreferenceInteractor.current
    val iconPacks = remember { interactor.getIconPacks() }
    val iconPackPackage = preferenceManager().iconPackPackage.getAdapter()

    pageMeta.provide(Meta(title = stringResource(id = R.string.icon_pack)))
    PreferenceLayout {
        PreferenceGroup(isFirstChild = true) {
            // TODO: Use `LazyColumn` if possible.
            Column(Modifier.fillMaxWidth()) {
                iconPacks.forEach { iconPack ->
                    IconPackListItem(
                        iconPack,
                        iconPackPackage.state.value,
                        onSelectionChange = iconPackPackage::onChange,
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