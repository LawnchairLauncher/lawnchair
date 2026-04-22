package app.lawnchair.views.component

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.android.launcher3.R
import com.android.launcher3.util.Themes

class IconFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val imageView: ImageView

    init {
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        )

        imageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            )
            setPadding(12.dpToPx(context), 12.dpToPx(context), 12.dpToPx(context), 12.dpToPx(context))
        }
        addView(imageView)

        setBackgroundWithRadius(
            bgColor = ContextCompat.getColor(context, R.color.accent_primary_device_default),
            cornerRadius = Themes.getDialogCornerRadius(context),
        )
    }

    /**
     * Convert dp to pixels for consistent padding across devices.
     */
    private fun Int.dpToPx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (this * density).toInt()
    }

    /**
     * Set the vector drawable for the ImageView.
     *
     * @param drawableRes The resource ID of the vector drawable.
     */
    fun setIcon(@DrawableRes drawableRes: Int) {
        imageView.setImageResource(drawableRes)
    }

    /**
     * Set the background color and corner radius of the FrameLayout.
     *
     * @param bgColor The background color.
     * @param cornerRadius The corner radius in pixels.
     */
    fun setBackgroundWithRadius(bgColor: Int, cornerRadius: Float) {
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            this.cornerRadius = cornerRadius
        }
        background = backgroundDrawable
    }
}
