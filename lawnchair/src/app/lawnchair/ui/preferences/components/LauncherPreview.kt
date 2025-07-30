package app.lawnchair.ui.preferences.components

import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import app.lawnchair.LauncherPreviewManager
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.lifecycleState
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState

@Composable
fun DummyLauncherBox(
    modifier: Modifier = Modifier,
    darkText: Boolean = wallpaperSupportsDarkText(),
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val idp = remember { InvariantDeviceProfile.INSTANCE.get(context) }
    val dp = idp.getDeviceProfile(context)
    val ratio = dp.widthPx.toFloat() / dp.heightPx.toFloat()

    Box(modifier = modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)) {
        LawnchairTheme(darkTheme = !darkText) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        content()
    }
}

@Composable
fun DummyLauncherLayout(
    idp: InvariantDeviceProfile,
    modifier: Modifier = Modifier,
) {
    val previewView = createPreviewView(idp)
    Crossfade(targetState = previewView, label = "") {
        val view = it
        AndroidView(
            factory = { context ->
                view ?: View(context)
            },
            modifier = modifier,
        )
    }
}

@Composable
fun wallpaperSupportsDarkText(): Boolean {
    val context = LocalContext.current
    val colorHints = remember {
        WallpaperManagerCompat.INSTANCE[context].wallpaperColors?.colorHints ?: 0
    }
    return (colorHints and WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) != 0
}

@Composable
fun invariantDeviceProfile(): InvariantDeviceProfile {
    val context = LocalContext.current
    return LauncherAppState.getIDP(context)
}

@Composable
fun createPreviewView(idp: InvariantDeviceProfile = invariantDeviceProfile()): View? {
    val context = LocalContext.current
    val lifecycleState = lifecycleState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        return null
    }
    val previewManager = remember { LauncherPreviewManager(context) }
    return remember(idp) { previewManager.createPreviewView(idp) }
}

/**
 * A Modifier that clips a composable to visually show only its bottom portion,
 * defined by the `percentageToShow` (e.g., 0.3f for the bottom 30%).
 *
 * It adjusts the layout bounds of the composable to match this visible percentage
 * and translates the content within those bounds so that the bottom part is visible.
 *
 * @param percentageToShow The fraction of the composable's bottom to show (0.0f to 1.0f).
 *                         0.0f would show nothing, 1.0f would show everything.
 */
fun Modifier.clipToBottomPercentage(percentageToShow: Float): Modifier {
    if (percentageToShow <= 0f) {
        return this.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, 0) {}
        }
    }
    if (percentageToShow >= 1.0f) {
        return this
    }

    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)

            val visibleHeight = (placeable.height * percentageToShow).toInt().coerceAtLeast(0)

            val translationYValue = -(placeable.height - visibleHeight).toFloat()

            layout(placeable.width, visibleHeight) {
                placeable.placeRelative(
                    x = 0,
                    y = translationYValue.toInt(),
                )
            }
        }
        .clipToBounds()
}

fun Modifier.clipToPercentage(percentage: Float): Modifier {
    return this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * percentage).toInt()
            layout(placeable.width, height) {
                placeable.place(0, 0)
            }
        },
    )
}

fun Modifier.clipToVisiblePercentage(percentage: Float): Modifier {
    return this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val totalHeight = placeable.height
            val visibleHeight = (totalHeight * percentage).toInt()
            val offsetY = totalHeight - visibleHeight

            layout(placeable.width, visibleHeight) {
                placeable.place(0, -offsetY)
            }
        },
    )
}
