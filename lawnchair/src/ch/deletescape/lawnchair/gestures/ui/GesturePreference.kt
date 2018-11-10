/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.gestures.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.gestures.*
import com.android.launcher3.R

class GesturePreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    var value = ""
    var defaultValue = ""

    private val blankGestureHandler = BlankGestureHandler(context, null)
    private val handler get() = GestureController.createGestureHandler(context, value, blankGestureHandler)
    internal var isSwipeUp = false

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.GesturePreference)
        val className = ta.getString(R.styleable.GesturePreference_gestureClass) ?: ""
        when (className) {
            NavSwipeUpGesture::class.java.name -> isSwipeUp = true
        }

        ta.recycle()
    }

    override fun onAttached() {
        super.onAttached()

        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDetached() {
        super.onDetached()

        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        if (key == this.key) {
            value = getPersistedString(defaultValue)
            notifyChanged()
        }
    }

    override fun getSummary() = handler.displayName

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        value = if (restorePersistedValue) {
            getPersistedString(defaultValue as String?) ?: ""
        } else {
            defaultValue as String? ?: ""
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): String? {
        defaultValue = a.getString(index)
        return defaultValue
    }

//    override fun onClick() {
//        context.startActivity(Intent(context, SelectGestureHandlerActivity::class.java).apply {
//            putExtra("title", title)
//            putExtra("key", key)
//            putExtra("value", value)
//        })
//    }

    override fun getDialogLayoutResource() = R.layout.dialog_preference_recyclerview
}
