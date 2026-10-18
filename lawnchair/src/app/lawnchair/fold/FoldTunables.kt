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

    // SIMPLE

    /** Opacity of the fixed half (the page you were on) as the new layout starts to fade in. */
    const val SIMPLE_FIXED_ALPHA = 0.55f

    // WINDOW

    /** Virtual tilt of the moving half at progress 0 (decoupled from the real hinge angle). */
    const val WINDOW_MAX_TILT_DEGREES = 55f

    /** Eye position across the hinge, as a fraction of the half width, over the fixed half. */
    const val WINDOW_EYE_OFFSET = 0.5f

    /** Eye distance as a multiple of the larger screen side. */
    const val WINDOW_EYE_DISTANCE = 2.2f

    /** Blur radius at the free edge of the moving half at progress 0. */
    const val WINDOW_MAX_BLUR_DP = 24f

    /** Blur radius of the fixed half (the page you were on) at progress 0. */
    const val WINDOW_FIXED_BLUR_DP = 12f

    /** Progress at which the fixed half is sharp again, before the moving half. */
    const val WINDOW_FIXED_SHARP_AT = 0.8f

    /** Scale of the whole screen at progress 0; it settles to 1 about the fixed half's outer edge. */
    const val WINDOW_SETTLE_SCALE = 1.06f

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

    // BOOK

    /** Angle of the moving half about the hinge at progress 0, its free edge away from you. */
    const val BOOK_MOVING_DEGREES = 78f

    /** Angle of the fixed half (the page you were on) about the hinge at progress 0. */
    const val BOOK_FIXED_DEGREES = 30f

    /** Eye distance in front of the middle of the hinge, as a multiple of a half's width. */
    const val BOOK_EYE_DISTANCE = 3f

    /** Darkening at the hinge at progress 0, clear at the outer edge: the moving half, the fixed half. */
    const val BOOK_MOVING_SHADE = 0.6f
    const val BOOK_FIXED_SHADE = 0.36f

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
