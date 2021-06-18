package app.lawnchair.ui.util

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import com.android.launcher3.R

val Context.systemAccentColor: Int
    get() {
        val typedValue = TypedValue()
        val contextWrapper = ContextThemeWrapper(this, R.style.AppTheme)
        contextWrapper.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }