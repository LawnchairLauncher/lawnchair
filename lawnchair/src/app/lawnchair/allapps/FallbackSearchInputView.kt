package app.lawnchair.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.ColorTokens
import app.lawnchair.util.EditTextExtensions.setCursorColor
import app.lawnchair.util.EditTextExtensions.setTextSelectHandleColor
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.allapps.AllAppsContainerView

class FallbackSearchInputView(context: Context, attrs: AttributeSet?) : ExtendedEditText(context, attrs) {

    private var appsView: AllAppsContainerView? = null

    init {
        val accentColor = ColorTokens.ColorAccent.resolveColor(context)
        setCursorColor(accentColor)
        setTextSelectHandleColor(accentColor)
        highlightColor = ColorUtils.setAlphaComponent(accentColor, 82)
    }

    fun initialize(appsView: AllAppsContainerView) {
        this.appsView = appsView
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        this.appsView?.requestFocus()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.RIGHT or Gravity.CENTER
        }
    }
}
