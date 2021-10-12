package app.lawnchair.ui.preferences.components

import android.app.WallpaperManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.drawablepainter.rememberDrawablePainter

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

@Composable
fun wallpaperDrawable(): Drawable? {
    val context = LocalContext.current
    return remember {
        val wallpaperManager = WallpaperManager.getInstance(context)
        wallpaperManager.wallpaperInfo?.loadThumbnail(context.packageManager) ?: wallpaperManager.drawable
    }
}
