package app.lawnchair.gestures.type

import android.annotation.StringRes
import android.content.Context
import com.android.launcher3.R

enum class GestureType(@StringRes val keyResId: Int, @StringRes val labelResId: Int) {
    SWIPE_UP(R.string.pref_key_swipe_up, R.string.gesture_swipe_up),
    SWIPE_DOWN(R.string.pref_key_swipe_down, R.string.gesture_swipe_down),
    SWIPE_LEFT(R.string.pref_key_swipe_left, R.string.gesture_swipe_left),
    SWIPE_RIGHT(R.string.pref_key_swipe_right, R.string.gesture_swipe_right),
    ;

    companion object {
        fun fromKey(key: String, context: Context): GestureType? {
            return entries.find { context.getString(it.keyResId) == key }
        }
    }
}
