package app.lawnchair.ui.preferences.components

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.lawnchair.fold.FoldDualWallpaper
import app.lawnchair.fold.FoldDualWallpaper.Screen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lawnchair's dual wallpaper ([app.lawnchair.fold.FoldDualWallpaperService]): the image of each
 * screen, picked with the system photo picker (no storage permission), and the action that opens
 * the system's live wallpaper chooser on it.
 */
@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun FoldDualWallpaperPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var picking by rememberSaveable { mutableStateOf<Screen?>(null) }
    LifecycleResumeEffect(Unit) {
        // The wallpaper may have been applied or replaced in the system chooser
        refresh++
        onPauseOrDispose { }
    }
    val hasCover = remember(refresh) { FoldDualWallpaper.hasImage(context, Screen.COVER) }
    val hasInner = remember(refresh) { FoldDualWallpaper.hasImage(context, Screen.INNER) }
    val active = remember(refresh) { FoldDualWallpaper.isActive(context) }
    val failed = stringResource(id = R.string.fold_wallpaper_image_failed)
    val chooserFailed = stringResource(id = R.string.fold_wallpaper_chooser_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val screen = picking ?: return@rememberLauncherForActivityResult
        picking = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val stored = withContext(Dispatchers.IO) { FoldDualWallpaper.setImage(context, screen, uri) }
            if (!stored) Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            refresh++
        }
    }

    PreferenceGroup(
        heading = stringResource(id = R.string.fold_dual_wallpaper_label),
        description = stringResource(id = R.string.fold_dual_wallpaper_description),
        modifier = modifier,
    ) {
        for (screen in Screen.entries) {
            val chosen = if (screen == Screen.COVER) hasCover else hasInner
            ClickablePreference(
                label = stringResource(
                    id = if (screen == Screen.COVER) R.string.fold_wallpaper_cover_image else R.string.fold_wallpaper_inner_image,
                ),
                subtitle = stringResource(
                    id = if (chosen) R.string.fold_wallpaper_image_set else R.string.fold_wallpaper_image_not_set,
                ),
                onClick = {
                    picking = screen
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
        ClickablePreference(
            label = stringResource(id = R.string.fold_wallpaper_set_dual),
            subtitle = stringResource(
                id = if (active) R.string.fold_wallpaper_dual_active else R.string.fold_wallpaper_dual_inactive,
            ),
            onClick = {
                if (!FoldDualWallpaper.openChooser(context)) {
                    Toast.makeText(context, chooserFailed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
