package app.lawnchair.fold

import android.os.Build
import androidx.annotation.RequiresApi

/**
 * DEEP (not built yet; it plays WINDOW meanwhile and the settings show it as "coming soon").
 * Parallax by depth: wallpaper (`WallpaperManager.setWallpaperOffsets`) < widgets < icons,
 * offsets ∝ sin(rotation) × depth; icons gain a soft shadow while tilted.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DeepFoldStyle : WindowFoldStyle()
