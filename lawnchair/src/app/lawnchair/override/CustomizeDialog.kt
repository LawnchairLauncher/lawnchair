package app.lawnchair.override

import android.graphics.drawable.Drawable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.launcher
import app.lawnchair.preferences.customPreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.Routes
import app.lawnchair.ui.preferences.components.ClickableIcon
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@ExperimentalMaterialApi
@Composable
fun CustomizeDialog(
    icon: Drawable,
    title: String,
    onTitleChange: (String) -> Unit,
    defaultTitle: String,
    launchSelectIcon: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth()
    ) {
        val iconPainter = rememberDrawablePainter(drawable = icon)
        Image(
            painter = iconPainter,
            contentDescription = "",
            modifier = Modifier
                .padding(vertical = 32.dp)
                .size(54.dp)
                .align(Alignment.CenterHorizontally)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    launchSelectIcon()
                }
        )
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
    componentKey: ComponentKey
) {
    val prefs = preferenceManager()
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }

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
                    componentKey.user
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
        launchSelectIcon = {
            if (prefs.enableIconSelection.get()) {
                val destination = "/${Routes.SELECT_ICON}/$componentKey/"
                context.startActivity(PreferenceActivity.createIntent(context, destination))
            }
        }
    ) {
        PreferenceGroup {
            val stringKey = componentKey.toString()
            var hiddenApps by prefs.hiddenAppSet.getAdapter()
            val adapter = customPreferenceAdapter(
                value = hiddenApps.contains(stringKey),
                onValueChange = { isHidden ->
                    val newSet = hiddenApps.toMutableSet()
                    if (isHidden) {
                        newSet.add(stringKey)
                    } else {
                        newSet.remove(stringKey)
                    }
                    hiddenApps = newSet
                }
            )
            SwitchPreference(
                adapter = adapter,
                label = stringResource(id = R.string.hide_from_drawer)
            )
        }
    }
}
