package app.lawnchair.ui.preferences.components

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.graphics.drawable.Drawable
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.ui.util.isPlayStoreFlavor
import app.lawnchair.util.FileAccessManager
import app.lawnchair.util.FileAccessState
import app.lawnchair.util.scaleDownToDisplaySize
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ColumnScope.WithWallpaper(
    modifier: Modifier = Modifier,
    displayWallpaperButton: Boolean = true,
    content: @Composable ColumnScope.(wallpaper: Drawable?) -> Unit,
) {
    val context = LocalContext.current
    val fileAccessManager = remember { FileAccessManager.getInstance(context) }
    val wallpaperAccessState by fileAccessManager.wallpaperAccessState.collectAsStateWithLifecycle()
    val allFilesAccessState by fileAccessManager.allFilesAccessState.collectAsStateWithLifecycle()
    val hasPermission = wallpaperAccessState == FileAccessState.Full
    var showPermissionDialog by remember { mutableStateOf(false) }

    val wallpaperDrawable = wallpaperDrawable(hasPermission)

    content(wallpaperDrawable)

    if (displayWallpaperButton && !hasPermission && !isPlayStoreFlavor()) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, top = 8.dp),
        ) {
            FilledTonalButton(
                onClick = { showPermissionDialog = true },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wallpaper,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.show_wallpaper),
                )
            }
        }
    }

    if (showPermissionDialog) {
        WallpaperAccessPermissionDialog(
            managedFilesChecked = allFilesAccessState != FileAccessState.Denied,
            onDismiss = {
                showPermissionDialog = false
            },
            onPermissionRequest = {
                fileAccessManager.refresh()
            },
        )
    }

    LifecycleResumeEffect(Unit) {
        showPermissionDialog = false
        fileAccessManager.refresh()
        onPauseOrDispose { }
    }
}

@Composable
fun WallpaperPreview(
    wallpaper: Drawable?,
    modifier: Modifier = Modifier,
) {
    val painter = rememberDrawablePainter(wallpaper)
    Image(
        painter = painter,
        contentDescription = "",
        modifier = modifier,
        contentScale = ContentScale.FillHeight,
    )
}

@SuppressLint("MissingPermission")
@Composable
fun wallpaperDrawable(
    hasPermission: Boolean,
): Drawable? {
    val context = LocalContext.current
    val wallpaperManager = remember { WallpaperManager.getInstance(context) }
    val wallpaperInfo = wallpaperManager.wallpaperInfo

    val wallpaperDrawable by produceState<Drawable?>(
        key1 = hasPermission,
        initialValue = null,
    ) {
        value = when {
            wallpaperInfo != null -> wallpaperInfo.loadThumbnail(context.packageManager)
            hasPermission -> {
                withContext(Dispatchers.IO) {
                    wallpaperManager.drawable?.let {
                        val size = Size(it.intrinsicWidth, it.intrinsicHeight).scaleDownToDisplaySize(context)
                        val bitmap = it.toBitmap(size.width, size.height)
                        bitmap.toDrawable(context.resources)
                    }
                }
            }
            else -> null
        }
    }

    return wallpaperDrawable
}
