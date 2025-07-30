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

package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.invariantDeviceProfile
import app.lawnchair.ui.preferences.components.layout.Chip
import app.lawnchair.ui.preferences.components.layout.NestedScrollStretch
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.theme.preferenceGroupColor
import app.lawnchair.util.Constants
import app.lawnchair.util.getThemedIconPacksInstalled
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch

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
    ),
    ;

    companion object {
        fun getForSettings(
            themedIcons: Boolean,
            drawerThemedIcons: Boolean,
        ) = entries.find {
            it.themedIcons == themedIcons && it.drawerThemedIcons == drawerThemedIcons
        } ?: Off
    }
}

@Composable
fun IconPackPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val context = LocalContext.current

    val iconPackAdapter = prefs.iconPackPackage.getAdapter()
    val themedIconPackAdapter = prefs.themedIconPackPackage.getAdapter()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs.drawerThemedIcons.getAdapter()
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scrollState = rememberScrollState()
    val drawerThemedIconsEnabled = drawerThemedIconsAdapter.state.value
    val tintIconpack = prefs.tintIconPackBackgrounds.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.icon_style_label),
        modifier = modifier,
        isExpandedScreen = true,
        scrollState = if (isPortrait) null else scrollState,
    ) {
        if (isPortrait) {
            Column(
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WithWallpaper { wallpaper ->
                    DummyLauncherBox(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 8.dp)
                            .clip(MaterialTheme.shapes.large),
                    ) {
                        WallpaperPreview(
                            wallpaper = wallpaper,
                            modifier = Modifier.fillMaxSize(),
                        )
                        key(iconPackAdapter.state.value, themedIconPackAdapter.state.value, themedIconsAdapter.state.value, tintIconpack.state.value) {
                            DummyLauncherLayout(
                                idp = invariantDeviceProfile(),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        Column {
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { 2 },
            )

            val scope = rememberCoroutineScope()
            val scrollToPage =
                { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }

            Row(
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Chip(
                    label = stringResource(id = R.string.icon_pack),
                    onClick = { scrollToPage(0) },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 0,
                )
                Chip(
                    label = stringResource(id = R.string.themed_icon_pack),
                    onClick = { scrollToPage(1) },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 1,
                )
            }

            Spacer(Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                Column {
                    when (page) {
                        0 -> {
                            IconPackGrid(
                                adapter = iconPackAdapter,
                                false,
                            )
                            PreferenceGroup {
                                SwitchPreference(
                                    adapter = prefs.tintIconPackBackgrounds.getAdapter(),
                                    label = stringResource(id = R.string.themed_icon_pack_tint),
                                )
                            }
                        }

                        1 -> {
                            val packageManager = context.packageManager

                            val themedIconsAvailable = packageManager
                                .getThemedIconPacksInstalled(LocalContext.current)
                                .any { packageManager.isPackageInstalled(it) } ||
                                packageManager
                                    .isPackageInstalled(Constants.LAWNICONS_PACKAGE_NAME)

                            if (themedIconsAvailable && themedIconsAdapter.state.value) {
                                IconPackGrid(
                                    adapter = themedIconPackAdapter,
                                    true,
                                )
                            }
                            PreferenceGroup {
                                ListPreference(
                                    enabled = themedIconsAvailable,
                                    label = stringResource(id = R.string.themed_icon_title),
                                    entries = ThemedIconsState.entries.map {
                                        ListPreferenceEntry(
                                            value = it,
                                            label = { stringResource(id = it.labelResourceId) },
                                        )
                                    },
                                    value = ThemedIconsState.getForSettings(
                                        themedIcons = themedIconsAdapter.state.value,
                                        drawerThemedIcons = drawerThemedIconsEnabled,
                                    ),
                                    onValueChange = {
                                        themedIconsAdapter.onChange(newValue = it.themedIcons)
                                        drawerThemedIconsAdapter.onChange(newValue = it.drawerThemedIcons)

                                        iconPackAdapter.onChange(newValue = iconPackAdapter.state.value)
                                        themedIconPackAdapter.onChange(newValue = themedIconPackAdapter.state.value)
                                    },
                                    description = if (themedIconsAvailable.not()) {
                                        stringResource(id = R.string.lawnicons_not_installed_description)
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IconPackGrid(
    adapter: PreferenceAdapter<String>,
    isThemedIconPack: Boolean,
    modifier: Modifier = Modifier,
) {
    val preferenceInteractor = LocalPreferenceInteractor.current

    val iconPacks by preferenceInteractor.iconPacks.collectAsStateWithLifecycle()
    val themedIconPacks by preferenceInteractor.themedIconPacks.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val padding = 12.dp

    val iconPacksLocal = if (isThemedIconPack) {
        themedIconPacks.filter { it.packageName != "" }
    } else {
        iconPacks
    }

    val selectedPack = adapter.state.value
    LaunchedEffect(selectedPack) {
        val selectedIndex = iconPacksLocal.indexOfFirst { it.packageName == selectedPack }
        if (selectedIndex != -1) {
            lazyListState.animateScrollToItem(selectedIndex)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
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
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .fillMaxWidth(),
            ) {
                items(iconPacksLocal, { it.packageName }) { item ->
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
        } else {
            break
        }
    }
    return iconPackItemWidth
}

@Composable
fun IconPackItem(
    item: IconPackInfo,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else preferenceGroupColor(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                modifier = Modifier
                    .size(42.dp)
                    .padding(bottom = 8.dp),
                painter = rememberDrawablePainter(drawable = item.icon),
                contentDescription = null,
            )
            Text(
                text = item.name,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
