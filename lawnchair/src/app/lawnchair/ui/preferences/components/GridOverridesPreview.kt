package app.lawnchair.ui.preferences.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.preferences.preferenceManager
import com.android.launcher3.InvariantDeviceProfile

@Composable
fun ColumnScope.GridOverridesPreview(
    modifier: Modifier = Modifier,
    expandToAvailableSpace: Boolean = true,
    previewOverrides: DeviceProfileOverrides.PreviewOverrides = DeviceProfileOverrides.PreviewOverrides(),
    updateGridOptions: DeviceProfileOverrides.DBGridInfo.() -> DeviceProfileOverrides.DBGridInfo,
) {
    val previewIdp = createPreviewIdp(updateGridOptions, previewOverrides)
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val previewAspectRatio = remember(previewIdp, isPortrait) {
        val matchingProfile = previewIdp.supportedProfiles.firstOrNull { profile ->
            profile.deviceProperties.isLandscape != isPortrait
        } ?: previewIdp.supportedProfiles.firstOrNull { profile ->
            !profile.deviceProperties.isMultiDisplay
        } ?: previewIdp.supportedProfiles.firstOrNull()

        val width = matchingProfile?.deviceProperties?.widthPx?.toFloat()
        val height = matchingProfile?.deviceProperties?.heightPx?.toFloat()

        if (width == null || height == null || height <= 0f) {
            1f
        } else {
            width / height
        }
    }

    WithWallpaper { wallpaper ->
        BoxWithConstraints(
            modifier = modifier
                .then(if (expandToAvailableSpace) Modifier.weight(1f) else Modifier)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            val fitByWidth = maxHeight == 0.dp || maxWidth == 0.dp || (maxWidth / maxHeight) <= previewAspectRatio
            val previewModifier = if (fitByWidth) {
                Modifier.requiredSize(
                    width = maxWidth,
                    height = maxWidth / previewAspectRatio,
                )
            } else {
                Modifier.requiredSize(
                    width = maxHeight * previewAspectRatio,
                    height = maxHeight,
                )
            }

            DummyLauncherBox(
                modifier = previewModifier.clip(MaterialTheme.shapes.large),
                aspectRatio = previewAspectRatio,
            ) {
                WallpaperPreview(
                    wallpaper = wallpaper,
                    modifier = Modifier.fillMaxSize(),
                )
                DummyLauncherLayout(
                    idp = previewIdp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
fun createPreviewIdp(updateGridOptions: DeviceProfileOverrides.DBGridInfo.() -> DeviceProfileOverrides.DBGridInfo): InvariantDeviceProfile {
    return createPreviewIdp(
        updateGridOptions = updateGridOptions,
        previewOverrides = DeviceProfileOverrides.PreviewOverrides(),
    )
}

@Composable
fun createPreviewIdp(
    updateGridOptions: DeviceProfileOverrides.DBGridInfo.() -> DeviceProfileOverrides.DBGridInfo,
    previewOverrides: DeviceProfileOverrides.PreviewOverrides = DeviceProfileOverrides.PreviewOverrides(),
): InvariantDeviceProfile {
    val context = LocalContext.current
    val prefs = preferenceManager()

    val newIdp by remember(previewOverrides) {
        derivedStateOf {
            val options = DeviceProfileOverrides.DBGridInfo(prefs)
            InvariantDeviceProfile(context, updateGridOptions(options), previewOverrides)
        }
    }
    return newIdp
}
