package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import com.android.launcher3.InvariantDeviceProfile

@Composable
fun GridOverridesPreview(
    modifier: Modifier = Modifier,
    updateGridOptions: DeviceProfileOverrides.Options.() -> Unit
) {
    DummyLauncherBox(modifier = modifier) {
        WallpaperPreview(modifier = Modifier.fillMaxSize())
        DummyLauncherLayout(
            idp = createPreviewIdp(updateGridOptions),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun createPreviewIdp(updateGridOptions: DeviceProfileOverrides.Options.() -> Unit): InvariantDeviceProfile {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val preferenceManager2 = preferenceManager2()
    val defaultGrid = invariantDeviceProfile().closestProfile

    val newIdp by remember {
        derivedStateOf {
            val options = DeviceProfileOverrides.Options(prefs, preferenceManager2, defaultGrid)
            updateGridOptions(options)
            InvariantDeviceProfile(context, options)
        }
    }
    return newIdp
}
