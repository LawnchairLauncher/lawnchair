package app.lawnchair.util

import android.annotation.SuppressLint
import android.util.Log

private const val TAG = "Compatibility"

val isOnePlusStock = checkOnePlusStock()

private fun checkOnePlusStock(): Boolean = when {
    getSystemProperty("ro.rom.version", "")
        .contains(Regex("Oxygen OS|Hydrogen OS|O2_BETA|H2_BETA")) -> true
    getSystemProperty("ro.oxygen.version", "").isNotEmpty() -> true
    getSystemProperty("ro.hydrogen.version", "").isNotEmpty() -> true
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
