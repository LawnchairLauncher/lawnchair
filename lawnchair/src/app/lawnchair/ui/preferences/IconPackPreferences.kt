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
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.preferenceGroupItems
import com.android.launcher3.R

data class IconPackInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

@ExperimentalAnimationApi
fun NavGraphBuilder.iconPackGraph(route: String) {
    preferenceGraph(route, { IconPackPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun IconPackPreferences() {
    val interactor = LocalPreferenceInteractor.current
    val iconPacks = remember { interactor.getIconPacks() }
    var iconPackPackage by preferenceManager().iconPackPackage.getAdapter()

    PreferenceLayoutLazyColumn(label = stringResource(id = R.string.icon_pack)) {
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = true,
            dividerStartIndent = 40.dp
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = { iconPackPackage = iconPack.packageName },
            ) {
                RadioButton(
                    selected = iconPackPackage == iconPack.packageName,
                    onClick = null
                )
            }
        }
    }
}
