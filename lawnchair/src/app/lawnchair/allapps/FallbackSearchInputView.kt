package app.lawnchair.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.ColorTokens
import app.lawnchair.theme.drawable.DrawableTokens
import app.lawnchair.util.EditTextExtensions.setCursorColor
import app.lawnchair.util.EditTextExtensions.setTextSelectHandleColor
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.allapps.AllAppsContainerView

class FallbackSearchInputView(context: Context, attrs: AttributeSet?) : ExtendedEditText(context, attrs) {

    private var appsView: AllAppsContainerView? = null
    private var shown = true
        set(value) {
            if (field != value) {
                field = value
                updateBackground()
            }
        }
    private val bg = DrawableTokens.SearchInputFg.resolve(context)

    init {
        background = bg

        val accentColor = ColorTokens.ColorAccent.resolveColor(context)
        setCursorColor(accentColor)
        setTextSelectHandleColor(accentColor)
        highlightColor = ColorUtils.setAlphaComponent(accentColor, 82)
    }

    fun initialize(appsView: AllAppsContainerView) {
        this.appsView = appsView
    }

    private fun updateBackground() {
        val showBackground = shown && !isFocused
        background = if (showBackground) bg else null
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        this.appsView?.requestFocus()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        updateBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.RIGHT or Gravity.CENTER
        }
    }
}
