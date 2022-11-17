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
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import app.lawnchair.util.getThemedIconPacksInstalled
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

data class IconPackInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
)

enum class ThemedIconsState(
    @StringRes val labelResourceId: Int,
    val themedIcons: Boolean = true,
    val drawerThemedIcons: Boolean = false,
) {
    Off(labelResourceId = R.string.themed_icons_off_label, themedIcons = false),
    Home(labelResourceId = R.string.themed_icons_home_label),
    HomeAndDrawer(
        labelResourceId = R.string.themed_icons_home_and_drawer_label,
        drawerThemedIcons = true,
    );

    companion object {
        fun getForSettings(
            themedIcons: Boolean,
            drawerThemedIcons: Boolean,
        ) = values().find {
            it.themedIcons == themedIcons && it.drawerThemedIcons == drawerThemedIcons
        } ?: Off
    }
}

fun NavGraphBuilder.iconPackGraph(route: String) {
    preferenceGraph(route, { IconPackPreferences() })
}

@Composable
fun IconPackPreferences() {
    val prefs = preferenceManager()
    val iconPackAdapter = prefs.iconPackPackage.getAdapter()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs.drawerThemedIcons.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.icon_style),
        scrollState = null
    ) {
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Column(
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DummyLauncherBox(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(top = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    WallpaperPreview(modifier = Modifier.fillMaxSize())
                    key(iconPackAdapter.state.value, themedIconsAdapter.state.value) {
                        DummyLauncherLayout(
                            idp = invariantDeviceProfile(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        Column {
            PreferenceGroupHeading(heading = stringResource(id = R.string.icon_pack))
            IconPackGrid(
                adapter = iconPackAdapter,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            PreferenceGroup {
                val themedIconsAvailable = LocalContext.current.packageManager
                    .getThemedIconPacksInstalled(LocalContext.current)
                    .any { LocalContext.current.packageManager.isPackageInstalled(it) }
                ListPreference(
                    enabled = themedIconsAvailable,
                    label = stringResource(id = R.string.themed_icon_title),
                    entries = ThemedIconsState.values().map {
                        ListPreferenceEntry(
                            value = it,
                            label = { stringResource(id = it.labelResourceId) },
                        )
                    },
                    value = ThemedIconsState.getForSettings(
                        themedIcons = themedIconsAdapter.state.value,
                        drawerThemedIcons = drawerThemedIconsAdapter.state.value,
                    ),
                    onValueChange = {
                        themedIconsAdapter.onChange(newValue = it.themedIcons)
                        drawerThemedIconsAdapter.onChange(newValue = it.drawerThemedIcons)
                    },
                    description = if (themedIconsAvailable.not()) {
                        stringResource(id = R.string.lawnicons_not_installed_description)
                    } else null,
                )
            }
        }
    }
}

@Composable
fun IconPackGrid(
    adapter: PreferenceAdapter<String>,
    modifier: Modifier,
) {
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsState()
    val lazyListState = rememberLazyListState()
    val padding = 16.dp

    val selectedPack = adapter.state.value
    LaunchedEffect(selectedPack) {
        val selectedIndex = iconPacks.indexOfFirst { it.packageName == selectedPack }
        if (selectedIndex != -1) {
            lazyListState.animateScrollToItem(selectedIndex)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val iconPackItemWidth = getIconPackItemWidth(
            availableWidth = this.maxWidth.value - padding.value,
            minimumWidth = 80f,
            gutterWidth = padding.value,
        )
        NestedScrollStretch {
            LazyRow(
                state = lazyListState,
                horizontalArrangement = Arrangement.spacedBy(space = padding),
                contentPadding = PaddingValues(horizontal = padding),
                modifier = modifier.fillMaxWidth(),
            ) {
                itemsIndexed(iconPacks, { _, item -> item.packageName }) { index, item ->
                    IconPackItem(
                        item = item,
                        selected = item.packageName == adapter.state.value,
                        modifier = Modifier.width(iconPackItemWidth.dp),
                    ) {
                        adapter.onChange(item.packageName)
                    }
                }
            }
        }
    }
}

private fun getIconPackItemWidth(
    availableWidth: Float,
    minimumWidth: Float,
    gutterWidth: Float,
): Float {
    var gutterCount = 2f
    var visibleItemCount = gutterCount + 0.5f
    var iconPackItemWidth = minimumWidth
    while (true) {
        gutterCount += 1f
        visibleItemCount += 1f
        val possibleIconPackItemWidth = (availableWidth - gutterCount * gutterWidth) / visibleItemCount
        if (possibleIconPackItemWidth >= minimumWidth) {
            iconPackItemWidth = possibleIconPackItemWidth
        } else break
    }
    return iconPackItemWidth
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackItem(
    item: IconPackInfo,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier,
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
