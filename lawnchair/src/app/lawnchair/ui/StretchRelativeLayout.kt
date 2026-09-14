package app.lawnchair.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import com.android.launcher3.views.SpringRelativeLayout

@Suppress("LeakingThis")
sealed class StretchRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SpringRelativeLayout(context, attrs, defStyleAttr) {

    init {
        setWillNotDraw(false)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
    }
}
