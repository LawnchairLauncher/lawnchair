package app.lawnchair.override

import android.app.Activity
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.launcher
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.Routes
import app.lawnchair.ui.preferences.components.ClickableIcon
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.ui.util.addIfNotNull
import app.lawnchair.util.ifNotNull
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.patrykmichalik.preferencemanager.state
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun CustomizeDialog(
    icon: Drawable,
    title: String,
    onTitleChange: (String) -> Unit,
    defaultTitle: String,
    launchSelectIcon: (() -> Unit)?,
    content: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth()
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
                contentDescription = "",
                modifier = Modifier.size(54.dp),
            )
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
                        onClick = { onTitleChange(defaultTitle) }
                    )
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12F),
                textColor = MaterialTheme.colors.onSurface
            ),
            shape = MaterialTheme.shapes.large,
            label = { Text(text = stringResource(id = R.string.label)) },
            isError = title.isEmpty()
        )
        content?.invoke()
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalAnimationApi::class)
@ExperimentalMaterialApi
@Composable
fun CustomizeAppDialog(
    icon: Drawable,
    defaultTitle: String,
    componentKey: ComponentKey,
    onClose: () -> Unit
) {
    val prefs = preferenceManager()
    val preferenceManager2 = preferenceManager2()
    val coroutineScope = rememberCoroutineScope()
    val enableIconSelection by preferenceManager2.enableIconSelection.state()
    val showComponentNames by preferenceManager2.showComponentNames.state()
    val hiddenApps by preferenceManager2.hiddenApps.state()
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        onClose()
    }

    val openIconPicker = {
        val destination = "/${Routes.SELECT_ICON}/$componentKey/"
        request.launch(PreferenceActivity.createIntent(context, destination))
    }

    DisposableEffect(key1 = null) {
        title = prefs.customAppName[componentKey] ?: defaultTitle
        onDispose {
            val previousTitle = prefs.customAppName[componentKey]
            val newTitle = title.ifEmpty { null }
            if (newTitle != previousTitle) {
                prefs.customAppName[componentKey] = newTitle
                val las = LauncherAppState.getInstance(context)
                val idp = las.invariantDeviceProfile
                las.iconCache.updateIconsForPkg(
                    componentKey.componentName.packageName,
                    componentKey.user,
                )
                context.launcher.onIdpChanged(idp)
            }
        }
    }
    CustomizeDialog(
        icon = icon,
        title = title,
        onTitleChange = { title = it },
        defaultTitle = defaultTitle,
        launchSelectIcon = if (enableIconSelection == true) openIconPicker else null,
    ) {
        ifNotNull(showComponentNames, hiddenApps) {
            val collectedShowComponentNames = it[0] as Boolean
            val collectedHiddenApps = it[1] as Set<String>
            PreferenceGroup(
                description = componentKey.componentName.flattenToString(),
                showDescription = collectedShowComponentNames,
            ) {
                val stringKey = componentKey.toString()
                SwitchPreference(
                    checked = collectedHiddenApps.contains(stringKey),
                    label = stringResource(id = R.string.hide_from_drawer),
                    onCheckedChange = { newValue ->
                        val newSet = collectedHiddenApps.toMutableSet()
                        if (newValue) newSet.add(stringKey) else newSet.remove(stringKey)
                        coroutineScope.launch {
                            preferenceManager2.hiddenApps.set(value = newSet)
                        }
                    },
                )
            }
        }
    }
}
