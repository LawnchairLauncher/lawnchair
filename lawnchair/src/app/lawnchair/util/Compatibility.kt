package app.lawnchair.util

import android.annotation.SuppressLint
import android.text.TextUtils
import android.util.Log
import java.lang.Exception

private const val TAG = "Compatibility"

val isOnePlusStock = !TextUtils.isEmpty(getSystemProperty("ro.oxygen.version", ""))
        || !TextUtils.isEmpty(getSystemProperty("ro.hydrogen.version", ""))
        || getSystemProperty("ro.rom.version", "").contains("Oxygen OS")
        || getSystemProperty("ro.rom.version", "").contains("Hydrogen OS")

@SuppressLint("PrivateApi")
fun getSystemProperty(property: String, defaultValue: String): String {
    try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getter = clazz.getDeclaredMethod("get", String::class.java)
        val value = getter.invoke(null, property) as String
        if (!TextUtils.isEmpty(value)) {
            return value
        }
    } catch (e: Exception) {
        Log.d(TAG, "Unable to read system properties")
    }
    return defaultValue
}
