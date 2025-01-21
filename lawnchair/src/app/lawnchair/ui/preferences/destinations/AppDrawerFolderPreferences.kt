@file:Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")

package app.lawnchair.ui.preferences.destinations

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.factory.ViewModelFactory
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.launcher
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.calculateAlpha
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.folder.FolderBottomSheet
import app.lawnchair.ui.preferences.components.layout.LoadingScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.Routes
import app.lawnchair.ui.util.BottomSheetHandler
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo

@Composable
fun AppDrawerFolderPreferences(
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current

    val onFolderSettingsClick: () -> Unit = {
        navController.navigate(route = Routes.APP_DRAWER_FOLDER)
    }

    PreferenceGroup(
        heading = stringResource(id = R.string.app_drawer_folder),
        modifier = modifier,
    ) {
        ClickablePreference(
            label = stringResource(R.string.app_drawer_folder_settings),
            modifier = Modifier,
            onClick = onFolderSettingsClick,
        )
    }
}

@Composable
fun DrawerFolderPreferences(
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val launcher = LawnchairLauncher.instance?.launcher
    if (launcher == null) return
    val viewModel: FolderViewModel = viewModel(factory = ViewModelFactory(launcher) { FolderViewModel(it) })

    val folders by viewModel.folders.collectAsState()
    val currentTitle by viewModel.currentTitle.collectAsState()
    val action by viewModel.action.collectAsState()

    val bottomSheetHandler = bottomSheetHandler
    var folderInfoHolder by remember { mutableStateOf<FolderInfo?>(null) }

    if (action == Action.RESET || action == Action.SETTLE) {
        folderInfoHolder = null
        viewModel.setAction(Action.DEFAULT)
    }

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    LoadingScreen(obj = folders, modifier = modifier.fillMaxWidth()) { items ->
        PreferenceLayoutLazyColumn(
            modifier = Modifier.fillMaxSize(),
            label = stringResource(id = R.string.app_drawer_folder),
            backArrowVisible = true,
            isExpandedScreen = LocalIsExpandedScreen.current,
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { viewModel.setAction(Action.ADD) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp, start = 14.dp, end = 14.dp),
                    ) {
                        Text(text = stringResource(id = R.string.add_label))
                    }
                }
                if (items.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_list_note),
                        modifier = Modifier
                            .width(300.dp)
                            .padding(all = 16.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            preferenceGroupItems(items, isFirstChild = true, dividerStartIndent = 40.dp) { index, folderInfo ->
                key(folderInfo.id) {
                    FolderRowItem(
                        folderInfo = folderInfo,
                        action = action,
                        onAction = { item, fi ->
                            folderInfoHolder = fi
                            if (item in listOf(Action.EDIT, Action.DELETE, Action.OPEN)) {
                                viewModel.setAction(item)
                            }
                        },
                    )
                }
            }
        }
    }

    if (action != Action.SETTLE && (action == Action.EDIT || action == Action.ADD)) {
        FolderBottomSheet(
            label = if (action == Action.ADD) stringResource(id = R.string.add_folder) else stringResource(id = R.string.edit_folder),
            title = currentTitle,
            onTitleChange = { newTitle -> viewModel.updateCurrentTitle(newTitle) },
            onAction = { item ->
                viewModel.setAction(item)
            },
            action = action,
            defaultTitle = currentTitle,
            bottomSheetHandler = bottomSheetHandler,
        )
    }

    HandleActions(
        launcher = launcher,
        action = action,
        folderInfoHolder = folderInfoHolder,
        currentTitle = currentTitle,
        navController = navController,
        viewModel = viewModel,
        bottomSheetHandler = bottomSheetHandler,
    )
}

// TODO
@Composable
fun HandleActions(
    action: Action,
    folderInfoHolder: FolderInfo?,
    currentTitle: String,
    navController: NavController,
    viewModel: FolderViewModel,
    bottomSheetHandler: BottomSheetHandler,
    launcher: LawnchairLauncher,
) {
    var loggedAction: String? = null

    val reloadHelper = ReloadHelper(launcher)

    when (action) {
        Action.OPEN -> {
            folderInfoHolder?.id?.let {
                navController.navigate("${Routes.APP_LIST_TO_FOLDER}/$it")
                viewModel.setAction(Action.DEFAULT)
            }
        }
        Action.SAVE -> {
            val folderInfo = FolderInfo().apply { title = currentTitle }
            viewModel.saveFolder(folderInfo)
            bottomSheetHandler.hide()
            reloadHelper.reloadGrid()
            viewModel.setAction(Action.SETTLE)
            loggedAction = "Saved folder: $currentTitle"
        }
        Action.UPDATE -> {
            folderInfoHolder?.apply {
                title = currentTitle
                viewModel.updateFolderInfo(this, false)
            }
            bottomSheetHandler.hide()
            reloadHelper.recreate()
            viewModel.setAction(Action.SETTLE)
            loggedAction = "Updated folder: ${folderInfoHolder?.title}"
        }
        Action.CANCEL -> {
            viewModel.refreshFolders()
            bottomSheetHandler.hide()
            viewModel.setAction(Action.RESET)
            loggedAction = "Cancelled action"
        }
        Action.DELETE -> {
            folderInfoHolder?.let {
                viewModel.deleteFolderInfo(it.id)
            }
            reloadHelper.reloadGrid()
            viewModel.setAction(Action.RESET)
            loggedAction = "Deleted folder: ${folderInfoHolder?.title}"
        }
        Action.RESET -> {
            viewModel.setAction(Action.DEFAULT)
            loggedAction = "Reset action"
        }
        else -> { /*no action*/ }
    }

    loggedAction?.let {
        Log.i("FolderPreferences", it)
    }
}

@Composable
fun FolderRowItem(
    folderInfo: FolderInfo,
    action: Action,
    onAction: (Action, FolderInfo?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> onAction(Action.DELETE, folderInfo)
                SwipeToDismissBoxValue.EndToStart -> onAction(Action.EDIT, folderInfo)
                SwipeToDismissBoxValue.Settled -> { /* no action*/ }
            }
            true
        },
    )

    LaunchedEffect(action) {
        if (action == Action.DEFAULT) {
            Log.d("FolderPreferences", "Action requires state reset ${action.name}")
            state.reset()
        }
    }

    SwipeToDismissBox(
        modifier = modifier,
        state = state,
        backgroundContent = {
            val backgroundColor = when (state.currentValue) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                else -> Color.Transparent
            }

            val alignment = when (state.currentValue) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            val labelText = when (state.currentValue) {
                SwipeToDismissBoxValue.StartToEnd -> stringResource(R.string.delete_label)
                SwipeToDismissBoxValue.EndToStart -> stringResource(R.string.edit_label)
                else -> ""
            }

            val textColor = when (state.currentValue) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onErrorContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onSurface
                else -> Color.Transparent
            }

            Surface(
                modifier = Modifier
                    .alpha(
                        if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
                            1f
                        } else {
                            calculateAlpha(
                                state.progress,
                            )
                        },
                    )
                    .fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                color = backgroundColor,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = alignment,
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(
                    if (state.dismissDirection != SwipeToDismissBoxValue.StartToEnd) {
                        1f
                    } else {
                        calculateAlpha(
                            state.progress,
                        )
                    },
                ),
            color = Color.Transparent,
            shape = MaterialTheme.shapes.large,
            onClick = { onAction(Action.OPEN, folderInfo) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_apps),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = folderInfo.title.toString() + " (" + folderInfo.contents.size + ")",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier,
                )
            }
        }
    }
}

enum class Action {
    ADD,
    EDIT,
    DELETE,
    OPEN,
    CANCEL,
    SETTLE,
    SAVE,
    UPDATE,
    RESET,
    DEFAULT,
}
