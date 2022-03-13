package app.lawnchair.ui.preferences.components

import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.LauncherPreviewManager
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.util.lifecycleState
import com.android.launcher3.InvariantDeviceProfile

@Composable
fun GridOverridesPreview(
    modifier: Modifier = Modifier,
    updateGridOptions: DeviceProfileOverrides.Options.() -> Unit
) {
    val context = LocalContext.current
    val idp = remember { InvariantDeviceProfile.INSTANCE.get(context) }
    val dp = idp.getDeviceProfile(context)
    val ratio = dp.widthPx.toFloat() / dp.heightPx.toFloat()

    val previewView = previewOverrideOptions(idp, updateGridOptions)
    Box(
        modifier = modifier
            .aspectRatio(ratio, matchHeightConstraintsFirst = true)
            .background(Color.Black)
    ) {
        WallpaperPreview(modifier = Modifier.fillMaxSize())
        Crossfade(targetState = previewView) {
            val view = it
            AndroidView(
                factory = { context ->
                    view ?: View(context)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun previewOverrideOptions(
    idp: InvariantDeviceProfile,
    updateGridOptions: DeviceProfileOverrides.Options.() -> Unit
): View? {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val preferenceManager2 = preferenceManager2()
    val defaultGrid = idp.closestProfile
    val lifecycleState = lifecycleState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        return null
    }
    val previewManager = remember { LauncherPreviewManager(context) }
    val previewView by remember {
        derivedStateOf {
            val options = DeviceProfileOverrides.Options(prefs, preferenceManager2, defaultGrid)
            updateGridOptions(options)
            previewManager.createPreviewView(options)
        }
    }
    return previewView
}
