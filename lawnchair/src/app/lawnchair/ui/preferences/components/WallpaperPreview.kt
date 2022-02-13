package app.lawnchair.ui.preferences.components

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.permissions.*

@Composable
fun WallpaperPreview(modifier: Modifier = Modifier) {
    val painter = rememberDrawablePainter(wallpaperDrawable())
    Image(
        painter = painter,
        contentDescription = "",
        modifier = modifier,
        contentScale = ContentScale.FillHeight
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun wallpaperDrawable(): Drawable? {
    val context = LocalContext.current
    val wallpaperManager = remember { WallpaperManager.getInstance(context) }
    val wallpaperInfo = wallpaperManager.wallpaperInfo
    val permissionState = rememberPermissionState(android.Manifest.permission.READ_EXTERNAL_STORAGE)

    return when {
        wallpaperInfo != null -> remember { wallpaperInfo.loadThumbnail(context.packageManager) }
        permissionState.status.isGranted -> remember { wallpaperManager.drawable }
        permissionState.status.isGranted.not() -> {
            SideEffect { permissionState.launchPermissionRequest() }
            null
        }
        else -> null
    }
}
