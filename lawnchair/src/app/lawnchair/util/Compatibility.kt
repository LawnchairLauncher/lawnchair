package app.lawnchair.util

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import com.android.launcher3.Utilities

private const val TAG = "Compatibility"

val isOnePlusStock = checkOnePlusStock()

val isGestureNavContractCompatible = checkGestureNavContract()

private fun checkOnePlusStock(): Boolean = when {
    getSystemProperty("ro.rom.version", "")
        .contains(Regex("Oxygen OS|Hydrogen OS|O2_BETA|H2_BETA")) -> true

    getSystemProperty("ro.oxygen.version", "").isNotEmpty() -> true

    getSystemProperty("ro.hydrogen.version", "").isNotEmpty() -> true

    else -> false
}

private fun checkSamsungStock(): Boolean = when {
    getSystemProperty("ro.build.version.oneui", "").isNotEmpty() -> true
    getSystemProperty("ro.build.PDA", "").isNotEmpty() && getSystemProperty("ro.build.hidden_ver", "").isNotEmpty() -> true
    else -> false
}

private fun checkXiaomiStock(): Boolean = when {
    getSystemProperty("ro.miui.ui.version.name", "").isNotEmpty() -> true
    getSystemProperty("ro.miui.ui.version.code", "").isNotEmpty() -> true
    else -> false
}

private fun checkHuaweiHonorStock(): Boolean = when {
    getSystemProperty("ro.build.hw_emui_api_level", "").isNotEmpty() -> true
    getSystemProperty("ro.config.huawei_smallwindow", "").isNotEmpty() -> true
    else -> false
}

private fun checkOppoStock(): Boolean = when {
    getSystemProperty("ro.oppo.version", "").isNotEmpty() -> true
    getSystemProperty("ro.build.version.opporom", "").isNotEmpty() -> true
    else -> false
}

private fun checkMeizuStock(): Boolean = when {
    getSystemProperty("ro.meizu.build.number", "").isNotEmpty() -> true
    getSystemProperty("ro.meizu.project.id", "").isNotEmpty() -> true
    else -> false
}

private fun checkGestureNavContract(): Boolean = when {
    !Utilities.ATLEAST_Q -> false
    checkOnePlusStock() -> false
    checkSamsungStock() -> false
    checkXiaomiStock() -> false
    checkHuaweiHonorStock() -> false
    checkOppoStock() -> false
    checkMeizuStock() -> false
    else -> true
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
