package app.lawnchair.config

import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairApp
import com.android.launcher3.config.FeatureFlags.BooleanFlag

class DynamicFlag(
    key: String,
    private val getValue: (Context?) -> Boolean,
    defaultValue: Boolean,
) : BooleanFlag(defaultValue) {

    override fun get(): Boolean {
        return try {
            getValue(LawnchairApp.instance)
        } catch (t: Throwable) {
            Log.d("DynamicFlag", "failed to get value for ", t)
            super.get()
        }
    }
}
