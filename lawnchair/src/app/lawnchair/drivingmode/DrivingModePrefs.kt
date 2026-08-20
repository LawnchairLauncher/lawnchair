package app.lawnchair.drivingmode

import android.content.Context

/**
 * Plain SharedPreferences store for driving mode — deliberately independent
 * of Lawnchair's DataStore-based PreferenceManager2, since this is a small,
 * self-contained feature with no need for that machinery.
 */
object DrivingModePrefs {
    private const val PREFS_NAME = "driving_mode_prefs"
    private const val KEY_TARGET_DEVICE = "target_device_address"

    fun getTargetDeviceAddress(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TARGET_DEVICE, null)
    }

    fun setTargetDeviceAddress(context: Context, address: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TARGET_DEVICE, address).apply()
    }
}
