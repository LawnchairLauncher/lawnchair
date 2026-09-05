package app.lawnchair.override

import android.app.Activity
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.BlankActivity
import app.lawnchair.gestures.type.GestureType
import app.lawnchair.launcher
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.components.AppGesturePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.navigation.SelectIcon
import app.lawnchair.ui.util.addIfNotNull
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch

@Composable
fun CustomizeDialog(
    icon: Drawable,
    title: String,
    onTitleChange: (String) -> Unit,
    defaultTitle: String,
    launchSelectIcon: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth(),
    ) {
        val iconPainter = rememberDrawablePainter(drawable = icon)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 24.dp)
                .clip(MaterialTheme.shapes.small)
                .addIfNotNull(launchSelectIcon) {
                    clickable(onClick = it)
                }
                .padding(all = 8.dp),
        ) {
            Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
            )
            if (launchSelectIcon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(12.dp),
                    )
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
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
        content?.invoke()
    }
}

/**
 * Shared body of the customize dialogs: the icon preview, the label field, and the write-back of a
 * changed label when the sheet is dismissed. [content] holds the options specific to what is being
 * customized.
 */
@Composable
private fun CustomizeOverrideDialog(
    icon: Drawable,
    defaultTitle: String,
    componentKey: ComponentKey,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pickerLabel: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val prefs = preferenceManager()
    val context = LocalContext.current
    var title by remember {
        mutableStateOf(prefs.customAppName[componentKey] ?: defaultTitle)
    }
    val launcherAppState = LauncherAppState.getInstance(context)

    val route = SelectIcon(componentKey.toString(), pickerLabel)

    val scope = rememberCoroutineScope()
    val openIconPicker = {
        val intent = PreferenceActivity.createIntent(context, route)
        scope.launch {
            val result = BlankActivity.startBlankActivityForResult(context as Activity, intent)
            if (result.resultCode == Activity.RESULT_OK) {
                onClose()
            }
        }
        Unit
    }

    DisposableEffect(Unit) {
        onDispose {
            val previousTitle = prefs.customAppName[componentKey]
            val newTitle = if (title != defaultTitle) title else null
            if (newTitle != previousTitle) {
                prefs.customAppName[componentKey] = newTitle
                val model = launcherAppState.model
                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
            }
        }
    }
    CustomizeDialog(
        icon = icon,
        title = title,
        onTitleChange = { title = it },
        defaultTitle = defaultTitle,
        launchSelectIcon = openIconPicker,
        modifier = modifier,
        content = content,
    )
}

/**
 * Customize dialog for a pinned deep shortcut, such as a web app pinned by a browser.
 *
 * A shortcut has no drawer entry to hide and no per-app gestures, so only the icon and the label
 * are offered here.
 */
@Composable
fun CustomizeShortcutDialog(
    icon: Drawable,
    defaultTitle: String,
    componentKey: ComponentKey,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    CustomizeOverrideDialog(
        icon = icon,
        defaultTitle = defaultTitle,
        componentKey = componentKey,
        modifier = modifier,
        onClose = onClose,
        // The picker cannot name a shortcut from its key alone, so hand it the shortcut's own name.
        pickerLabel = defaultTitle,
    )
}

/**
 * Customize dialog for an app: everything [CustomizeOverrideDialog] offers, plus hiding the app
 * from the drawer.
 */
@Composable
fun CustomizeAppDialog(
    icon: Drawable,
    defaultTitle: String,
    componentKey: ComponentKey,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val preferenceManager2 = preferenceManager2()
    val showComponentNames by preferenceManager2.showComponentNames.asState()
    val hiddenApps by preferenceManager2.hiddenApps.asState()
    val adapter = preferenceManager2.hiddenApps.getAdapter()
    val context = LocalContext.current

    CustomizeOverrideDialog(
        icon = icon,
        defaultTitle = defaultTitle,
        componentKey = componentKey,
        modifier = modifier,
        onClose = onClose,
    ) {
        PreferenceGroup(
            description = componentKey.componentName.flattenToString(),
            showDescription = showComponentNames,
        ) {
            val stringKey = componentKey.toString()
            SwitchPreference(
                checked = hiddenApps.contains(stringKey),
                label = stringResource(id = R.string.hide_from_drawer),
                onCheckedChange = { newValue ->
                    val newSet = hiddenApps.toMutableSet()
                    if (newValue) newSet.add(stringKey) else newSet.remove(stringKey)
                    adapter.onChange(newSet)
                },
            )
        }
        if (context.launcher.stateManager.state != LauncherState.ALL_APPS) {
            PreferenceGroup(heading = stringResource(R.string.gestures_label)) {
                listOf(
                    GestureType.SWIPE_UP,
                    GestureType.SWIPE_DOWN,
                    GestureType.SWIPE_LEFT,
                    GestureType.SWIPE_RIGHT,
                ).map { gestureType ->
                    AppGesturePreference(
                        componentKey,
                        gestureType,
                        stringResource(id = gestureType.labelResId),
                    )
                }
            }
        }
    }
}
