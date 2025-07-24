package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.util.checkAndRequestFilesPermission
import com.android.launcher3.R

@Composable
fun AskForFileAccessPermission(
    adapter: PreferenceAdapter<Boolean>,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheetContent(
        modifier = modifier,
        title = {
            Text(text = "File access required")
        },
        text = {
            Text(text = stringResource(id = R.string.warn_files_read_permission_content))
        },
        content = {
            SwitchPreference(
                adapter = adapter,
                label = stringResource(id = R.string.do_not_ask_again),
            )
        },
        buttons = {
            OutlinedButton(
                onClick = onDismissRequest,
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    onDismissRequest()
                    checkAndRequestFilesPermission(context, PreferenceManager.getInstance(context))
                },
            ) {
                Text(text = stringResource(id = R.string.grant_requested_permissions))
            }
        },
    )
}
