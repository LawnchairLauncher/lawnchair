package com.android.launcher3.responsive

import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import com.android.launcher3.R
import com.android.launcher3.util.ResourceHelper

data class SizeSpec(
    val fixedSize: Float,
    val ofAvailableSpace: Float,
    val ofRemainderSpace: Float,
    val matchWorkspace: Boolean
) {

    fun isValid(): Boolean {
        // All attributes are empty
        if (fixedSize < 0f && ofAvailableSpace <= 0f && ofRemainderSpace <= 0f && !matchWorkspace) {
            Log.e(TAG, "SizeSpec#isValid - all attributes are empty")
            return false
        }

        // More than one attribute is filled
        val attrCount =
            (if (fixedSize > 0) 1 else 0) +
                (if (ofAvailableSpace > 0) 1 else 0) +
                (if (ofRemainderSpace > 0) 1 else 0) +
                (if (matchWorkspace) 1 else 0)
        if (attrCount > 1) {
            Log.e(TAG, "SizeSpec#isValid - more than one attribute is filled")
            return false
        }

        // Values should be between 0 and 1
        if (ofAvailableSpace !in 0f..1f || ofRemainderSpace !in 0f..1f) {
            Log.e(TAG, "SizeSpec#isValid - values should be between 0 and 1")
            return false
        }

        // Invalid fixed size
        if (fixedSize < 0f) {
            Log.e(TAG, "SizeSpec#isValid - values should be bigger or equal to zero.")
            return false
        }

        return true
    }

    companion object {
        private const val TAG = "WorkspaceSpecs::SizeSpec"
        private fun getValue(a: TypedArray, index: Int): Float {
            return when (a.getType(index)) {
                TypedValue.TYPE_DIMENSION -> a.getDimensionPixelSize(index, 0).toFloat()
                TypedValue.TYPE_FLOAT -> a.getFloat(index, 0f)
                else -> 0f
            }
        }

        fun create(resourceHelper: ResourceHelper, attrs: AttributeSet): SizeSpec {
            val styledAttrs = resourceHelper.obtainStyledAttributes(attrs, R.styleable.SizeSpec)

            val fixedSize = getValue(styledAttrs, R.styleable.SizeSpec_fixedSize)
            val ofAvailableSpace = getValue(styledAttrs, R.styleable.SizeSpec_ofAvailableSpace)
            val ofRemainderSpace = getValue(styledAttrs, R.styleable.SizeSpec_ofRemainderSpace)
            val matchWorkspace = styledAttrs.getBoolean(R.styleable.SizeSpec_matchWorkspace, false)

            styledAttrs.recycle()

            return SizeSpec(fixedSize, ofAvailableSpace, ofRemainderSpace, matchWorkspace)
        }
    }
}
