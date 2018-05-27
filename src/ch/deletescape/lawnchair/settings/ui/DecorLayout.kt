package ch.deletescape.lawnchair.settings.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.getColorAttr
import ch.deletescape.lawnchair.getDimenAttr
import com.android.launcher3.R

@SuppressLint("ViewConstructor")
class DecorLayout(context: Context, private val window: Window) : FrameLayout(context) {

    private val contentFrame: View
    private val actionBarContainer: View
    private val toolbar: View
    private val largeTitle: View

    var actionBarElevation: Float
        get() = actionBarContainer.elevation
        set(value) {
            actionBarContainer.elevation = value
            if (value.compareTo(0f) == 0) {
                actionBarContainer.background = null
                window.statusBarColor = 0
            } else {
                val backgroundColor = context.getColorAttr(android.R.attr.windowBackground)
                actionBarContainer.background = ColorDrawable(backgroundColor)
                window.statusBarColor = backgroundColor
            }
        }

    var useLargeTitle: Boolean = false
        set(value) {
            largeTitle.visibility = if (value) View.VISIBLE else View.GONE
            toolbar.visibility = if (value) View.GONE else View.VISIBLE
            updateContentTopMargin(value)
        }

    private fun updateContentTopMargin(value: Boolean) {
        val layoutParams = contentFrame.layoutParams as LayoutParams
        if (value) {
            layoutParams.topMargin = context.resources.getDimensionPixelSize(R.dimen.large_title_height)
        } else {
            layoutParams.topMargin = context.getDimenAttr(R.attr.actionBarSize)
        }
    }

    init {
        fitsSystemWindows = false
        LayoutInflater.from(context).inflate(R.layout.decor_layout, this)

        contentFrame = findViewById(android.R.id.content)
        actionBarContainer = findViewById(R.id.action_bar_container)
        toolbar = findViewById(R.id.toolbar)
        largeTitle = findViewById(R.id.large_title)

        updateContentTopMargin(false)

        if (BlurWallpaperProvider.isEnabled) {
            findViewById<View>(R.id.blur_tint).visibility = View.VISIBLE
            background = BlurWallpaperProvider.getInstance(context).createDrawable()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        (background as BlurDrawable?)?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        (background as BlurDrawable?)?.stopListening()
    }
}
