package app.lawnchair.fold

import com.android.app.animation.Interpolators

/**
 * Every number the fold transition engine and its styles depend on, in one place.
 *
 * The hinge angles were measured on the emulator only; on a real device they may need tuning.
 */
object FoldTunables {

    // Hinge to progress mapping

    /** Hinge angle in degrees at which the inner display turns on. Maps to progress 0. */
    const val INNER_DISPLAY_ON_ANGLE = 30f

    /** Hinge angle in degrees treated as fully open. Maps to progress 1. */
    const val FULLY_OPEN_ANGLE = 170f

    /** How far, in degrees, the hinge has to close from its resting angle before a fold starts on the inner display. */
    const val CLOSING_THRESHOLD_ANGLE = 20f

    /** After a fold or unfold, how long to wait for the hinge to move before the timer drives the transition. */
    const val HINGE_WAIT_MILLIS = 150L

    /** If the hinge stops moving for this long mid-gesture, the transition settles to the open state. */
    const val HINGE_IDLE_MILLIS = 600L

    /** Time constant of the low-pass filter that smooths hinge angle samples. */
    const val HINGE_SMOOTHING_MILLIS = 50f

    // Timer-driven progress (no sensor, sensor off, or no events)

    const val UNFOLD_DURATION_MILLIS = 400L
    const val FOLD_DURATION_MILLIS = 320L

    /** Upper bound to settle from a stopped hinge back to the open state. */
    const val SETTLE_DURATION_MILLIS = 300L

    /** Crossfade used instead of motion when the system animator duration scale is 0. */
    const val REDUCED_MOTION_DURATION_MILLIS = 150L

    /** Safety net: a transition never runs longer than this. */
    const val MAX_TRANSITION_MILLIS = 8000L

    /** After the progress ends, how long a style may keep moving until it settles (see FoldStyle.isSettled). */
    const val MAX_SETTLE_MILLIS = 1200L

    // SIMPLE

    /** Distance the newly revealed half slides out of the hinge. */
    const val SIMPLE_TRANSLATE_DP = 32f

    // WINDOW

    /** Virtual tilt of the moving half at progress 0 (decoupled from the real hinge angle). */
    const val WINDOW_MAX_TILT_DEGREES = 55f

    /** Eye position across the hinge, as a fraction of the half width, over the fixed half. */
    const val WINDOW_EYE_OFFSET = 0.5f

    /** Eye distance as a multiple of the larger screen side. */
    const val WINDOW_EYE_DISTANCE = 2.2f

    /** Blur radius at the free edge of the moving half at progress 0. */
    const val WINDOW_MAX_BLUR_DP = 24f

    /** Blur radius of the fixed half at progress 0. */
    const val WINDOW_FIXED_BLUR_DP = 4f

    /** Darkening at the free edge of the moving half at progress 0 (0..1). */
    const val WINDOW_MAX_SHADE = 0.55f

    /** Number of pre-built blur radii; more levels mean smaller steps between frames. */
    const val WINDOW_BLUR_LEVELS = 12

    /** Radius of the middle blur band, as a fraction of the full radius. */
    const val WINDOW_BLUR_BAND_SPLIT = 0.35f

    // WAVE

    /** Width of the band of light. */
    const val WAVE_BAND_WIDTH_DP = 120f

    /** Peak opacity of the band (screen-blended over the moving half). */
    const val WAVE_BAND_ALPHA = 0.45f

    /** How high an icon rises as the band passes. */
    const val WAVE_LIFT_DP = 14f

    /** Distance behind the band over which a lifted icon settles back. */
    const val WAVE_SETTLE_DP = 120f

    /** Peak opacity of an icon's glow. */
    const val WAVE_GLOW_ALPHA = 0.6f

    /** Opacity of the moving half ahead of the band (not revealed yet). */
    const val WAVE_DIM_ALPHA = 0.15f

    // MASS

    /** Displacement of an icon at the hinge at full speed; the free edge gets 1.3x. */
    const val MASS_MAX_DISPLACEMENT_DP = 24f

    /** Progress per second (in real time, before the slow-replay scale) that counts as full speed. */
    const val MASS_REFERENCE_VELOCITY = 3f

    /** Share of the effect for icons on the fixed half (and the cover screen). */
    const val MASS_FIXED_HALF_FACTOR = 0.4f

    /** Spring stiffness (androidx SpringForce units) and damping ratio of each icon. */
    const val MASS_STIFFNESS = 380f
    const val MASS_DAMPING_RATIO = 0.42f

    // DEEP

    /** Virtual tilt of the moving half at progress 0. */
    const val DEEP_MAX_TILT_DEGREES = 50f

    /** Shift of an icon (depth 1) toward the hinge at full tilt. */
    const val DEEP_MAX_SHIFT_DP = 40f

    /** Depth of widgets and of the wallpaper, relative to icons. */
    const val DEEP_WIDGET_DEPTH = 0.6f
    const val DEEP_WALLPAPER_DEPTH = 0.25f

    /** Wallpaper offset (0..1 units) per unit of depth at full tilt. */
    const val DEEP_WALLPAPER_SHIFT = 0.08f

    /** Elevation of an icon at full tilt (its soft shadow). */
    const val DEEP_MAX_ELEVATION_DP = 10f

    // App drawer

    /** BIG_ICONS: upper bound of the icon and label scale over the cover's (read when the grid loads). */
    const val DRAWER_BIG_ICONS_MAX_SCALE = 1.6f

    /** EXPAND "river": delay between two apps, at the nominal unfold duration (compressed for long lists). */
    const val DRAWER_RIVER_STAGGER_MILLIS = 16f

    /** EXPAND "river": how far the path of an app bulges. */
    const val DRAWER_RIVER_ARC_DP = 24f

    /** Share of the progress the last item waits before it moves: the river's last app, the book's bottom row. */
    const val DRAWER_MAX_DELAY = 0.5f

    /** Curve of the drawer transitions: the river, the bar, the header, the zoom and the book. */
    val DRAWER_EASE = Interpolators.EMPHASIZED_DECELERATE
}
