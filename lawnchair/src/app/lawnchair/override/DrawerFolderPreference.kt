package app.lawnchair.override

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.launcher
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R
import kotlinx.coroutines.launch

/**
 * Lets the user choose which app drawer folders an app belongs to, from the Customize sheet.
 *
 * An app can belong to several folders. Changes are saved immediately; the drawer refreshes
 * on its own because [app.lawnchair.allapps.LawnchairAlphabeticalAppsList] collects the
 * folders flow.
 */
@Composable
fun DrawerFolderPreference(
    componentKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val folderService = remember { FolderService.INSTANCE.get(context) }
    val folders by remember { folderService.getFoldersFlow() }.collectAsState(initial = null)
    // Writes must survive the sheet being dismissed, so don't tie them to the composition.
    val scope = context.launcher.lifecycleScope

    val appFolders = folders.orEmpty().filter { componentKey in it.itemComponentKeys }
    var newFolderTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val defaultFolderTitle = stringResource(R.string.my_folder_label)

    // Last selection per folder id, shown until the sheet closes. The folders flow only updates
    // after the write, so without this a quick second tap would repeat the first action.
    val pendingSelection = remember { mutableStateMapOf<Int, Boolean>() }

    val setInFolder = { folderId: Int, inFolder: Boolean ->
        pendingSelection[folderId] = inFolder
        scope.launch {
            if (inFolder) {
                folderService.addItemToFolder(folderId, componentKey)
            } else {
                folderService.removeItemFromFolder(folderId, componentKey)
            }
        }
        Unit
    }

    PreferenceGroup(
        modifier = modifier,
        heading = stringResource(R.string.app_drawer_folder_settings),
        description = stringResource(R.string.drawer_folder_min_apps_note),
        showDescription = appFolders.any { it.itemComponentKeys.size < 2 },
    ) {
        // Bounded so a long folder list scrolls instead of pushing the sheet off screen.
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            folders.orEmpty().forEach { folder ->
                val checked = pendingSelection[folder.id] ?: (folder in appFolders)
                FolderOption(
                    title = folder.title,
                    checked = checked,
                    onClick = {
                        // Read at tap time: two taps can land before the next recomposition.
                        val selected = pendingSelection[folder.id] ?: checked
                        setInFolder(folder.id, !selected)
                    },
                )
            }
            if (newFolderTitle == null) {
                PreferenceTemplate(
                    title = { Text(text = stringResource(R.string.add_folder)) },
                    startWidget = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = { newFolderTitle = defaultFolderTitle },
                )
            }
        }
    }
    newFolderTitle?.let { title ->
        NewFolderInput(
            title = title,
            onTitleChange = { newFolderTitle = it },
            onCancel = { newFolderTitle = null },
            onCreate = {
                val trimmed = title.trim()
                if (trimmed.isNotEmpty()) {
                    scope.launch { folderService.createFolderWithItem(trimmed, componentKey) }
                    newFolderTitle = null
                }
            },
        )
    }
}

@Composable
private fun FolderOption(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceTemplate(
        title = { Text(text = title) },
        endWidget = {
            // The whole row handles the click.
            Checkbox(checked = checked, onCheckedChange = null)
        },
        onClick = onClick,
    )
}

@Composable
private fun NewFolderInput(
    title: String,
    onTitleChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = stringResource(R.string.label)) },
            isError = title.isBlank(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            OutlinedButton(onClick = onCancel) {
                Text(text = stringResource(android.R.string.cancel))
            }
            Button(onClick = onCreate, enabled = title.isNotBlank()) {
                Text(text = stringResource(R.string.add_folder))
            }
        }
    }
}
