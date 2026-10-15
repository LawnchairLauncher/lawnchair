package app.lawnchair.fold

import android.os.Build
import androidx.annotation.RequiresApi

/*
 * Phase 3ب styles. Each one is a skeleton that plays WINDOW (the default) until it is built, so a
 * stored value still animates; the settings show them as "coming soon" meanwhile
 * (FoldTransitionStyle.isImplemented). To build one: override the FoldStyle hooks (or extend
 * SplitFoldStyle for the fixed/moving split), flip isImplemented, and keep the rules in FoldStyle.
 */

/**
 * MASS (not built yet). Each icon is a `SpringAnimation` (androidx.dynamicanimation); its
 * displacement target is proportional to the hinge angular velocity × (0.3 + distance from the
 * hinge). Icons on the fixed half get 40% of the effect. Needs the hinge velocity, which the
 * controller can expose next to the progress.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MassFoldStyle : WindowFoldStyle()

/**
 * DEEP (not built yet). Parallax by depth: wallpaper (`WallpaperManager.setWallpaperOffsets`) <
 * widgets < icons, offsets ∝ sin(rotation) × depth; icons gain a soft shadow while tilted.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DeepFoldStyle : WindowFoldStyle()
