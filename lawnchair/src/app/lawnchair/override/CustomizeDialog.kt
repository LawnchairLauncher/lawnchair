package app.lawnchair.override

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.launcher
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.ClickableIcon
import app.lawnchair.util.max
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import com.android.launcher3.LauncherAppState
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues

@ExperimentalMaterialApi
@Composable
fun CustomizeDialog(
    icon: Drawable,
    title: String,
    onTitleChange: (String) -> Unit,
    defaultTitle: String
) {
    val windowInsets = LocalWindowInsets.current
    val imePaddings = rememberInsetsPaddingValues(
        insets = windowInsets.ime,
        applyStart = true, applyEnd = true, applyBottom = true
    )
    val minPaddings = remember { PaddingValues(bottom = 48.dp) }
    Column(
        modifier = Modifier
            .padding(max(imePaddings, minPaddings))
            .navigationBarsOrDisplayCutoutPadding()
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        val iconPainter = rememberDrawablePainter(drawable = icon)
        Image(
            painter = iconPainter,
            contentDescription = "",
            modifier = Modifier
                .padding(top = 16.dp, bottom = 32.dp)
                .size(54.dp)
                .align(Alignment.CenterHorizontally)
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth(),
            placeholder = { Text(defaultTitle) },
            trailingIcon = {
                if (title.isNotEmpty()) {
                    ClickableIcon(
                        imageVector = Icons.Rounded.Clear,
                        onClick = { onTitleChange("") }
                    )
                }
            },
            singleLine = true
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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
        title = prefs.customAppName[componentKey] ?: ""
        onDispose {
            prefs.customAppName[componentKey] = title.ifEmpty { null }
            val las = LauncherAppState.getInstance(context)
            val idp = las.invariantDeviceProfile
            las.iconCache.updateIconsForPkg(componentKey.componentName.packageName, componentKey.user)
            context.launcher.onIdpChanged(idp)
        }
    }
    CustomizeDialog(
        icon = icon,
        title = title,
        onTitleChange = { title = it },
        defaultTitle = defaultTitle
    )
}
