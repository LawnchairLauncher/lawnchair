package app.lawnchair.fold

/**
 * Every number the fold transition engine and its styles depend on, in one place.
 *
 * The hinge angles were measured on the emulator only; on a real device they may need tuning.
 * The values are read when a transition starts, so they can also be changed at runtime while
 * debugging.
 */
object FoldTunables {

    // Hinge to progress mapping

    /** Hinge angle in degrees at which the inner display turns on. Maps to progress 0. */
    var innerDisplayOnAngle = 30f

    /** Hinge angle in degrees treated as fully open. Maps to progress 1. */
    var fullyOpenAngle = 170f

    /** How far, in degrees, the hinge has to close from its resting angle before a fold starts on the inner display. */
    var closingThresholdAngle = 20f

    /** After a fold or unfold, how long to wait for the hinge to move before the timer drives the transition. */
    var hingeWaitMillis = 150L

    /** If the hinge stops moving for this long mid-gesture, the transition settles to the open state. */
    var hingeIdleMillis = 600L

    /** Time constant of the low-pass filter that smooths hinge angle samples. */
    var hingeSmoothingMillis = 50f

    // Timer-driven progress (no sensor, sensor off, or no events)

    var unfoldDurationMillis = 400L
    var foldDurationMillis = 320L

    /** Upper bound to settle from a stopped hinge back to the open state. */
    var settleDurationMillis = 300L

    /** Crossfade used instead of motion when the system animator duration scale is 0. */
    var reducedMotionDurationMillis = 150L

    /** Safety net: a transition never runs longer than this. */
    var maxTransitionMillis = 8000L

    /** After the progress ends, how long a style may keep moving until it settles (see FoldStyle.isSettled). */
    var maxSettleMillis = 1200L

    // Debug replay

    /** Pause between folding and opening again in the "fold" replay. */
    var replayHoldMillis = 250L

    /** Slow motion factor of the slow replays. */
    var replaySlowFactor = 5f

    // SIMPLE

    /** Distance the newly revealed half slides out of the hinge. */
    var simpleTranslateDp = 32f

    // WINDOW

    /**
     * True: the content stays still in space and the moving half is a window onto it (the design).
     * False: the content is painted on the swinging half, like a door. For comparing only.
     */
    var windowFixedPlane = true

    /** Virtual tilt of the moving half at progress 0 (decoupled from the real hinge angle). */
    var windowMaxTiltDegrees = 55f

    /** Eye position across the hinge, as a fraction of the half width, over the fixed half. */
    var windowEyeOffset = 0.5f

    /** Eye distance as a multiple of the larger screen side. */
    var windowEyeDistance = 2.2f

    /** Blur radius at the free edge of the moving half at progress 0. */
    var windowMaxBlurDp = 24f

    /** Blur radius of the fixed half at progress 0. */
    var windowFixedBlurDp = 4f

    /** Darkening at the free edge of the moving half at progress 0 (0..1). */
    var windowMaxShade = 0.55f

    /** Number of pre-built blur radii; more levels mean smaller steps between frames. */
    var windowBlurLevels = 12

    /** Radius of the middle blur band, as a fraction of the full radius. */
    var windowBlurBandSplit = 0.35f
}
