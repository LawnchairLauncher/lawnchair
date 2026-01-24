package app.lawnchair.util

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import com.android.launcher3.Utilities

private const val TAG = "Compatibility"

val isOnePlusStock = checkOnePlusStock()

val isNothingStock = checkNothingStock()

val isGoogle = checkGoogle()

val isSamsung = checkSamsungStock()

val isGestureNavContractCompatible = checkGestureNavContract()

private fun checkOnePlusStock(): Boolean = when {
    getSystemProperty("ro.rom.version", "")
        .contains(Regex("Oxygen OS|Hydrogen OS|O2_BETA|H2_BETA")) -> true

    getSystemProperty("ro.oxygen.version", "").isNotEmpty() -> true

    getSystemProperty("ro.hydrogen.version", "").isNotEmpty() -> true

    else -> false
}

private fun checkNothingStock(): Boolean = when {
    getSystemProperty("ro.nothing.version.id", "").isNotEmpty() -> true
    getSystemProperty("ro.build.nothing.version", "").isNotEmpty() -> true
    getSystemProperty("ro.build.nothing.feature.base", "").isNotEmpty() -> true
    else -> false
}

private fun checkGoogle(): Boolean = if (Utilities.ATLEAST_S) {
    when {
        Build.BRAND.contains("google", true) &&
            Build.SOC_MODEL.contains("tensor", true) &&
            Build.SOC_MANUFACTURER.contains("google", true) -> true

        else -> false
    }
} else {
    when {
        Build.BRAND.contains("google", true) &&
            Build.MANUFACTURER.contains("google", true) &&
            Build.FINGERPRINT.contains("pixel", true) &&
            Build.PRODUCT.contains("pixel", true) -> true

        else -> false
    }
}

private fun checkSamsungStock(): Boolean = when {
    getSystemProperty("ro.build.version.oneui", "").isNotEmpty() -> true
    getSystemProperty("ro.config.knox", "").isNotEmpty() -> true
    getSystemProperty("ro.build.PDA", "").isNotEmpty() -> true
    else -> false
}

private fun checkGestureNavContract(): Boolean = when {
    checkGoogle() -> true
    checkNothingStock() -> true
    else -> false
}

fun getSystemProperty(property: String, defaultValue: String): String {
    try {
        @SuppressLint("PrivateApi")
        val value = Class.forName("android.os.SystemProperties")
            .getDeclaredMethod("get", String::class.java)
            .apply { isAccessible }
            .invoke(null, property) as String
        if (value.isNotEmpty()) {
            return value
        }
    } catch (_: Exception) {
        Log.d(TAG, "Unable to read system properties")
    }
    return defaultValue
}
