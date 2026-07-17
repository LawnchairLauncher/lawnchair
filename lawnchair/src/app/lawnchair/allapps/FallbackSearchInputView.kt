package app.lawnchair.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.util.EditTextExtensions.setCursorColor
import app.lawnchair.util.EditTextExtensions.setTextSelectHandleColor
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.allapps.ActivityAllAppsContainerView

class FallbackSearchInputView(context: Context, attrs: AttributeSet?) : ExtendedEditText(context, attrs) {

    private var appsView: ActivityAllAppsContainerView<*>? = null

    init {
        val accentColor = ColorTokens.ColorAccent.resolveColor(context)
        setCursorColor(accentColor)
        setTextSelectHandleColor(accentColor)
        highlightColor = ColorUtils.setAlphaComponent(accentColor, 82)
    }

    fun initialize(appsView: ActivityAllAppsContainerView<*>?) {
        this.appsView = appsView
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        // Prefer the active apps list over appsView itself. appsView has
        // focusable=false and its search container is focusedByDefault, so
        // requestFocus() on appsView would re-focus the search field (e.g. when
        // switching Personal/Work tabs triggers resetSearch).
        val appsView = this.appsView
        val activeList = appsView?.activeRecyclerView
        if (activeList != null) {
            activeList.requestFocus()
        } else {
            appsView?.requestFocus()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.RIGHT or Gravity.CENTER
        }
    }
}
