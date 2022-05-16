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

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.Constants
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

data class IconPackInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

fun NavGraphBuilder.iconPackGraph(route: String) {
    preferenceGraph(route, { IconPackPreferences() })
}

@Composable
fun IconPackPreferences() {
    val prefs = preferenceManager()
    val iconPackAdapter = prefs.iconPackPackage.getAdapter()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.icon_pack),
        scrollState = null
    ) {
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GridOverridesPreview(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(top = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    iconPackAdapter.state.value
                    themedIconsAdapter.state.value
                }
            }
        }
        Column(Modifier.weight(0.4f)) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp)
            ) {
                IconPackGrid(iconPackAdapter)
            }
            PreferenceGroup {
                val themedIconsAvailable = LocalContext.current.packageManager
                    .isPackageInstalled(Constants.LAWNICONS_PACKAGE_NAME)
                SwitchPreference(
                    adapter = themedIconsAdapter,
                    label = stringResource(id = R.string.themed_icon_title),
                    enabled = themedIconsAvailable,
                    description = if (!themedIconsAvailable) stringResource(id = R.string.lawnicons_not_installed_description) else null,
                )
            }
        }
    }
}

@Composable
fun IconPackGrid(adapter: PreferenceAdapter<String>) {
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsState()

    NestedScrollStretch {
        val state = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(iconPacks, { _, item -> item.packageName }) { index, item ->
                val wasSelected = remember { mutableStateOf(false) }
                val selected = item.packageName == adapter.state.value
                LaunchedEffect(selected) {
                    if (wasSelected.value != selected) {
                        wasSelected.value = selected
                        if (selected) {
                            state.animateScrollToItem(index)
                        }
                    }
                }
                IconPackItem(item = item, selected = selected) {
                    adapter.onChange(item.packageName)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackItem(
    item: IconPackInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 1.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier
                    .size(56.dp)
                    .padding(bottom = 8.dp),
                painter = rememberDrawablePainter(drawable = item.icon),
                contentDescription = null
            )
            Text(
                text = item.name,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
