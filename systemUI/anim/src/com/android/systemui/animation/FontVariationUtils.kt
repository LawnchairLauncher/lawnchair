package com.android.systemui.animation

private const val TAG_WGHT = "wght"
private const val TAG_WDTH = "wdth"
private const val TAG_OPSZ = "opsz"
private const val TAG_ROND = "ROND"

class FontVariationUtils {
    private var mWeight = -1
    private var mWidth = -1
    private var mOpticalSize = -1
    private var mRoundness = -1
    private var isUpdated = false

    /*
     * generate fontVariationSettings string, used for key in typefaceCache in TextAnimator
     * the order of axes should align to the order of parameters
     * if every axis remains unchanged, return ""
     */
    fun updateFontVariation(
        weight: Int = -1,
        width: Int = -1,
        opticalSize: Int = -1,
        roundness: Int = -1
    ): String {
        isUpdated = false
        if (weight >= 0 && mWeight != weight) {
            isUpdated = true
            mWeight = weight
        }
        if (width >= 0 && mWidth != width) {
            isUpdated = true
            mWidth = width
        }
        if (opticalSize >= 0 && mOpticalSize != opticalSize) {
            isUpdated = true
            mOpticalSize = opticalSize
        }

        if (roundness >= 0 && mRoundness != roundness) {
            isUpdated = true
            mRoundness = roundness
        }
        var resultString = ""
        if (mWeight >= 0) {
            resultString += "'$TAG_WGHT' $mWeight"
        }
        if (mWidth >= 0) {
            resultString += (if (resultString.isBlank()) "" else ", ") + "'$TAG_WDTH' $mWidth"
        }
        if (mOpticalSize >= 0) {
            resultString += (if (resultString.isBlank()) "" else ", ") + "'$TAG_OPSZ' $mOpticalSize"
        }
        if (mRoundness >= 0) {
            resultString += (if (resultString.isBlank()) "" else ", ") + "'$TAG_ROND' $mRoundness"
        }
        return if (isUpdated) resultString else ""
    }
}
