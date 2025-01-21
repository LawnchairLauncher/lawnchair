package app.lawnchair.ui.preferences.components.folder

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.destinations.Action
import app.lawnchair.ui.util.BottomSheetHandler
import com.android.launcher3.R

@Composable
fun FolderBottomSheet(
    label: String,
    title: String,
    onTitleChange: (String) -> Unit,
    onAction: (Action) -> Unit,
    action: Action,
    defaultTitle: String,
    bottomSheetHandler: BottomSheetHandler,
    modifier: Modifier = Modifier,
) {
    bottomSheetHandler.show {
        ModalBottomSheetContent(
            title = { Text(label) },
            buttons = {
                OutlinedButton(onClick = {
                    onAction(Action.CANCEL)
                }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        when (action) {
                            Action.ADD -> onAction(Action.SAVE)
                            Action.EDIT -> onAction(Action.UPDATE)
                            else -> {}
                        }
                    },
                ) {
                    Text(text = stringResource(id = R.string.apply_label))
                }
            },
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    onTitleChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                trailingIcon = {
                    if (title != defaultTitle) {
                        ClickableIcon(
                            painter = painterResource(id = R.drawable.ic_undo),
                            onClick = { onTitleChange(defaultTitle) },
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.large,
                label = { Text(text = stringResource(id = R.string.label)) },
                isError = title.isEmpty(),
            )
        }
    }
}
