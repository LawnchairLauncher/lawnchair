package app.lawnchair.util

import android.annotation.SuppressLint
import android.text.TextUtils
import android.util.Log

private const val TAG = "Compatibility"

val isOnePlusStock = checkOnePlusStock()

private fun checkOnePlusStock(): Boolean {
    val roRomVersion = getSystemProperty("ro.rom.version", "")
    if (roRomVersion.contains(Regex("Oxygen OS|Hydrogen OS|O2_BETA|H2_BETA"))) {
        return true
    }
    if (getSystemProperty("ro.oxygen.version", "").isNotEmpty()) {
        return true
    }
    if (getSystemProperty("ro.hydrogen.version", "").isNotEmpty()) {
        return true
    }
    return false
}

@SuppressLint("PrivateApi")
fun getSystemProperty(property: String, defaultValue: String): String {
    try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getter = clazz.getDeclaredMethod("get", String::class.java)
        val value = getter.invoke(null, property) as String
        if (!TextUtils.isEmpty(value)) {
            return value
        }
    } catch (_: Exception) {
        Log.d(TAG, "Unable to read system properties")
    }
    return defaultValue
}
