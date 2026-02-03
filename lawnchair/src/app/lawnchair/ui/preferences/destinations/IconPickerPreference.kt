package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.icons.iconpack.CustomIconPack
import app.lawnchair.icons.iconpack.IconPack
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.filter
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupDescription
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceSearchScaffold
import app.lawnchair.ui.preferences.components.layout.verticalGridItems
import app.lawnchair.ui.util.LazyGridLayout
import app.lawnchair.ui.util.resultSender
import app.lawnchair.util.requireSystemService
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun IconPickerPreference(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconPack = remember {
        IconPackProvider.INSTANCE.get(context).getIconPackOrSystem(packageName)
    }
    if (iconPack == null) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        SideEffect {
            backDispatcher?.onBackPressed()
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    val onClickItem = resultSender<IconPickerItem>()

    val pickerComponent = remember {
        val launcherApps: LauncherApps = context.requireSystemService()
        launcherApps
            .getActivityList(iconPack.packPackageName, Process.myUserHandle()).firstOrNull()?.componentName
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val icon = it.data?.getParcelableExtra<Intent.ShortcutIconResource>(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
        ) ?: return@rememberLauncherForActivityResult
        val entry = (iconPack as CustomIconPack).createFromExternalPicker(icon) ?: return@rememberLauncherForActivityResult
        onClickItem(entry)
    }

    PreferenceSearchScaffold(
        value = searchQuery,
        modifier = modifier,
        onValueChange = { searchQuery = it },
        placeholder = {
            Text(
                text = iconPack.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            if (pickerComponent != null) {
                OverflowMenu {
                    DropdownMenuItem(onClick = {
                        val intent = Intent("com.novalauncher.THEME")
                            .addCategory("com.novalauncher.category.CUSTOM_ICON_PICKER")
                            .setComponent(pickerComponent)
                        pickerLauncher.launch(intent)
                        hideMenu()
                    }, text = {
                        Text(text = stringResource(id = R.string.icon_pack_external_picker))
                    })
                }
            }
        },
    ) {
        val scaffoldPadding = it

        IconPickerGrid(
            scaffoldPadding = scaffoldPadding,
            iconPack = iconPack,
            searchQuery = searchQuery,
            onClickItem = onClickItem,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconPickerGrid(
    scaffoldPadding: PaddingValues,
    iconPack: IconPack,
    searchQuery: String,
    modifier: Modifier = Modifier,
    onClickItem: (item: IconPickerItem) -> Unit,
) {
    var loadFailed by remember { mutableStateOf(false) }
    val categoriesFlow = remember {
        iconPack.getAllIcons()
            .catch { loadFailed = true }
    }
    val categories by categoriesFlow.collectAsStateWithLifecycle(emptyList())
    val filteredCategories by remember(searchQuery) {
        derivedStateOf {
            categories.asSequence()
                .map { it.filter(searchQuery) }
                .filter { it.items.isNotEmpty() }
                .toList()
        }
    }

    val density = LocalDensity.current
    val gridLayout = remember {
        LazyGridLayout(
            minWidth = 56.dp,
            gapWidth = 16.dp,
            density = density,
        )
    }
    val numColumns by gridLayout.numColumns
    PreferenceLazyColumn(scaffoldPadding, modifier = modifier.then(gridLayout.onSizeChanged())) {
        if (numColumns != 0) {
            filteredCategories.forEach { category ->
                stickyHeader {
                    Text(
                        text = category.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                verticalGridItems(
                    modifier = Modifier
                        .padding(horizontal = 8.dp),
                    items = category.items,
                    numColumns = numColumns,
                ) { _, item ->
                    IconPreview(
                        iconPack = iconPack,
                        iconItem = item,
                    ) {
                        onClickItem(item)
                    }
                }
            }
        }
        if (loadFailed) {
            item {
                PreferenceGroupDescription(
                    description = stringResource(id = R.string.icon_picker_load_failed),
                )
            }
        }
    }
}

@Composable
fun IconPreview(
    iconPack: IconPack,
    iconItem: IconPickerItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val drawable by produceState<Drawable?>(initialValue = null, iconPack, iconItem) {
        launch(Dispatchers.IO) {
            value = iconPack.getIcon(iconItem.toIconEntry(), 0)
        }
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Image(
            painter = rememberDrawablePainter(drawable),
            contentDescription = iconItem.drawableName,
            modifier = Modifier.aspectRatio(1f),
        )
    }
}
