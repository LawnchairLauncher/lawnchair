package com.android.systemui.shared.regionsampling

/**
 * Enum for whether clock region is dark or light.
 */
enum class RegionDarkness(val isDark: Boolean) {
    DEFAULT(false),
    DARK(true),
    LIGHT(false)
}
