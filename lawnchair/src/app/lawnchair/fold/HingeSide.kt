package app.lawnchair.fold

/**
 * Side of the hinge as seen when looking at the cover screen of a folded book-style foldable, for
 * the current display rotation.
 *
 * The half of the inner screen opposite the hinge is the fixed half (it stays in the hand while
 * unfolding); the other half is the moving half. With [LEFT] the fixed half is the right half of
 * the inner screen, with [RIGHT] it is the left half.
 */
enum class HingeSide { LEFT, RIGHT, UNKNOWN }
