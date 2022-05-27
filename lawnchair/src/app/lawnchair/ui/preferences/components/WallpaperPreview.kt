package app.lawnchair.ui.preferences.components

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.util.scaleDownToDisplaySize
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.permissions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val wallpaperDrawable by produceState<Drawable?>(initialValue = null) {
        value = when {
            wallpaperInfo != null -> wallpaperInfo.loadThumbnail(context.packageManager)
            permissionState.status.isGranted -> {
                withContext(Dispatchers.IO) {
                    wallpaperManager.drawable?.let {
                        val size = Size(it.intrinsicWidth, it.intrinsicHeight).scaleDownToDisplaySize(context)
                        val bitmap = it.toBitmap(size.width, size.height)
                        BitmapDrawable(context.resources, bitmap)
                    }
                }
            }
            else -> null
        }
    }

    if (!permissionState.status.isGranted) {
        SideEffect {
            permissionState.launchPermissionRequest()
        }
    }

    return wallpaperDrawable
}
