package app.lawnchair.allapps

import android.animation.ValueAnimator
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
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.AllAppsContainerView

class FallbackSearchInputView(context: Context, attrs: AttributeSet?) : ExtendedEditText(context, attrs) {

    private var appsView: AllAppsContainerView? = null
    private val bg = DrawableTokens.SearchInputFg.resolve(context)
    private val bgAlphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 300 }
    private var bgVisible = true
    private var bgAlpha = 1f

    init {
        background = bg

        val accentColor = ColorTokens.ColorAccent.resolveColor(context)
        setCursorColor(accentColor)
        setTextSelectHandleColor(accentColor)
        highlightColor = ColorUtils.setAlphaComponent(accentColor, 82)

        bgAlphaAnimator.addUpdateListener { updateBgAlpha() }
    }

    private fun updateBgAlpha() {
        val fraction = bgAlphaAnimator.animatedFraction
        bg.alpha = (Utilities.mapRange(fraction, 0f, bgAlpha) * 255).toInt()
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

    fun setBackgroundVisibility(visible: Boolean, maxAlpha: Float) {
        if (bgVisible != visible) {
            bgVisible = visible
            bgAlpha = maxAlpha
            if (visible) {
                bgAlphaAnimator.start()
            } else {
                bgAlphaAnimator.reverse()
            }
        } else if (bgAlpha != maxAlpha && !bgAlphaAnimator.isRunning && visible) {
            bgAlpha = maxAlpha
            bgAlphaAnimator.setCurrentFraction(maxAlpha)
            updateBgAlpha()
        }
    }

    fun getBackgroundVisibility(): Boolean {
        return bgVisible
    }
}
