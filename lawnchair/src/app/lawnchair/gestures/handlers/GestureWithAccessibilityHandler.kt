package app.lawnchair.gestures.handlers

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.lawnchairApp
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.R

object GestureWithAccessibilityHandler {

    fun onTrigger(launcher: LawnchairLauncher, stringAction: Int, action: Int) {
        val app = launcher.lawnchairApp
        if (!app.isAccessibilityServiceBound()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ComposeBottomSheet.show(launcher) {
                ServiceWarningDialog(
                    title = R.string.d2ts_recents_a11y_hint_title,
                    action = stringAction,
                    settingsIntent = intent,
                ) { close(true) }
            }
            return
        }
        app.performGlobalAction(action)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServiceWarningDialog(
    title: Int,
    action: Int,
    settingsIntent: Intent,
    modifier: Modifier = Modifier,
    handleClose: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheetContent(
        modifier = modifier.padding(top = 16.dp),
        title = { Text(text = stringResource(id = title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.generic_a11y_hint,
                        stringResource(action),
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.generic_a11y_disclaimer))
            }
        },
        buttons = {
            OutlinedButton(
                onClick = handleClose,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    context.startActivity(settingsIntent)
                    handleClose()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = R.string.open_permission_settings))
            }
        },
    )
}
