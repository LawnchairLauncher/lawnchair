package app.lawnchair.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import app.lawnchair.theme.drawable.DrawableTokens
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
    }

    fun initialize(appsView: AllAppsContainerView) {
        this.appsView = appsView
    }

    private fun updateBackground() {
        val showBackground = shown && !isFocused
        background = if (showBackground) bg else null
    }

    override fun show() {
        super.show()
        shown = true
    }

    override fun hide() {
        super.hide()
        shown = false
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
