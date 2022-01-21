package app.lawnchair.ui.preferences

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.lawnchair.icons.*
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.ui.util.resultSender
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.insets.ui.LocalScaffoldPadding
import com.google.accompanist.navigation.animation.composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ExperimentalAnimationApi
fun NavGraphBuilder.iconPickerGraph(route: String) {
    preferenceGraph(route, {
        IconPickerPreference(packageName = "")
    }) { subRoute ->
        composable(
            route = subRoute("{packageName}"),
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val packageName = args.getString("packageName")!!
            IconPickerPreference(packageName)
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun IconPickerPreference(packageName: String) {
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
        val launcherApps = context.getSystemService<LauncherApps>()!!
        launcherApps
            .getActivityList(iconPack.packPackageName, Process.myUserHandle()).firstOrNull()?.componentName
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val icon = it.data?.getParcelableExtra<Intent.ShortcutIconResource>(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE) ?: return@rememberLauncherForActivityResult
        val entry = (iconPack as CustomIconPack).createFromExternalPicker(icon) ?: return@rememberLauncherForActivityResult
        onClickItem(entry)
    }

    PreferenceSearchScaffold(
        searchInput = {
            SearchTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text(iconPack.label) },
                singleLine = true
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
                    }) {
                        Text(text = stringResource(id = R.string.icon_pack_external_picker))
                    }
                }
            }
        },
    ) {
        val scaffoldPadding = LocalScaffoldPadding.current
        val innerPadding = remember { MutablePaddingValues() }
        val layoutDirection = LocalLayoutDirection.current
        innerPadding.left = scaffoldPadding.calculateLeftPadding(layoutDirection)
        innerPadding.right = scaffoldPadding.calculateRightPadding(layoutDirection)
        innerPadding.bottom = scaffoldPadding.calculateBottomPadding()

        val topPadding = scaffoldPadding.calculateTopPadding()

        CompositionLocalProvider(LocalScaffoldPadding provides innerPadding) {
            IconPickerGrid(
                iconPack = iconPack,
                searchQuery = searchQuery,
                onClickItem = onClickItem,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = topPadding)
            )
        }
        Spacer(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .height(topPadding)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconPickerGrid(
    iconPack: IconPack,
    searchQuery: String,
    onClickItem: (item: IconPickerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by iconPack.getAllIcons().collectAsState(emptyList())
    val filteredCategories by derivedStateOf {
        categories
            .map { it.filter(searchQuery) }
            .filter { it.items.isNotEmpty() }
    }

    PreferenceLazyColumn(modifier = modifier) {
        filteredCategories.forEach { category ->
            stickyHeader {
                Text(
                    text = category.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            verticalGridItems(
                items = category.items,
                numColumns = 5,
                gap = 16.dp
            ) { _, item ->
                IconPreview(
                    iconPack = iconPack,
                    iconItem = item,
                    onClick = {
                        onClickItem(item)
                    }
                )
            }
        }
    }
}

@Composable
fun IconPreview(
    iconPack: IconPack,
    iconItem: IconPickerItem,
    onClick: () -> Unit
) {
    val drawable by produceState<Drawable?>(initialValue = null, iconPack, iconItem) {
        launch(Dispatchers.IO) {
            value = iconPack.getIcon(iconItem.toIconEntry(), 0)
        }
    }
    Image(
        painter = rememberDrawablePainter(drawable),
        contentDescription = iconItem.drawableName,
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}
