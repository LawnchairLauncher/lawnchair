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
import com.android.launcher3.InvariantDeviceProfile

@Composable
fun GridOverridesPreview(
    modifier: Modifier = Modifier,
    updateGridOptions: DeviceProfileOverrides.DBGridInfo.() -> DeviceProfileOverrides.DBGridInfo
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
fun createPreviewIdp(updateGridOptions: DeviceProfileOverrides.DBGridInfo.() -> DeviceProfileOverrides.DBGridInfo): InvariantDeviceProfile {
    val context = LocalContext.current
    val prefs = preferenceManager()

    val newIdp by remember {
        derivedStateOf {
            val options = DeviceProfileOverrides.DBGridInfo(prefs)
            InvariantDeviceProfile(context, updateGridOptions(options))
        }
    }
    return newIdp
}
