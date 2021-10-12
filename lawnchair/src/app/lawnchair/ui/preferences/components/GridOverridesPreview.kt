package app.lawnchair.ui.preferences.components

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.views.LauncherPreviewView
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

    val previewView by previewOverrideOptions(idp, updateGridOptions)
    Box(
        modifier = modifier
            .aspectRatio(ratio, matchHeightConstraintsFirst = true)
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { FrameLayout(it) },
            modifier = Modifier.fillMaxSize()
        ) { frame ->
            frame.removeAllViews()
            frame.addView(previewView)
        }
    }
}

@Composable
fun previewOverrideOptions(
    idp: InvariantDeviceProfile,
    updateGridOptions: DeviceProfileOverrides.Options.() -> Unit
): State<View> {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val defaultGrid = idp.closestProfile
    return remember {
        derivedStateOf {
            val options = DeviceProfileOverrides.Options(prefs, defaultGrid)
            updateGridOptions(options)
            LauncherPreviewView(context, options)
        }
    }
}
