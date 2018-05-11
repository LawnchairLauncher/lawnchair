package ch.deletescape.lawnchair.popup

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.anim.PillHeightRevealOutlineProvider

class MainItemView(context: Context, attrs: AttributeSet?, defStyle: Int) : PopupItemView(context, attrs, defStyle) {

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    lateinit var itemContainer: LinearLayout

    override fun onFinishInflate() {
        super.onFinishInflate()
        itemContainer = findViewById(R.id.popup_items)
    }

    override fun getArrowColor(isArrowAttachedToBottom: Boolean): Int {
        return Utilities.resolveAttributeData(context, R.attr.popupColorPrimary)
    }

    override fun addView(child: View?) {
        if (child is PopupItemView)
            itemContainer.addView(child)
        else
            super.addView(child)
    }

    override fun removeAllViews() {
        itemContainer.removeAllViews()
    }

    fun animateHeightRemoval(heightToRemove: Int, reverse: Boolean): Animator {
        val newHeight = height - heightToRemove
        return PillHeightRevealOutlineProvider(mPillRect,
                backgroundRadius, newHeight, reverse).createRevealAnimator(this, true, false, true)
    }

    override fun getBackgroundRadius(): Float {
        return resources.getDimensionPixelSize(mTheme.backgroundRadius).toFloat()
    }
}