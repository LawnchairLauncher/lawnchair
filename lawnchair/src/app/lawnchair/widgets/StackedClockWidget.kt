package app.lawnchair.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StackedClockWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private val hourText: TextView
    private val minuteText: TextView
    private val amPmText: TextView

    init {
        radius = 48f
        setCardBackgroundColor(0xFF1B2E21.toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        hourText = TextView(context).apply {
            textSize = 64f
            setTextColor(0xFF86EFAC.toInt())
            text = "12"
        }

        val rightStack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(16, 0, 0, 0)
        }

        minuteText = TextView(context).apply {
            textSize = 32f
            setTextColor(0xFF86EFAC.toInt())
            text = "21"
        }

        amPmText = TextView(context).apply {
            textSize = 16f
            setTextColor(0xFF4ADE80.toInt())
            text = "PM"
        }

        rightStack.addView(minuteText)
        rightStack.addView(amPmText)

        container.addView(hourText)
        container.addView(rightStack)

        addView(container)
        updateTime()
    }

    fun updateTime() {
        val now = Date()
        hourText.text = SimpleDateFormat("hh", Locale.getDefault()).format(now)
        minuteText.text = SimpleDateFormat("mm", Locale.getDefault()).format(now)
        amPmText.text = SimpleDateFormat("a", Locale.getDefault()).format(now)
    }
}
