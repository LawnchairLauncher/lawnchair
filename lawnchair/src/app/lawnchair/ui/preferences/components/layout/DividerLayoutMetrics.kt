package app.lawnchair.ui.preferences.components.layout

/** Empty/single-row groups have no divider; skipped dividers consume no layout height. */
internal fun dividerCount(childCount: Int, skip: Int): Int = (childCount - 1 - skip.coerceAtLeast(0)).coerceAtLeast(0)
