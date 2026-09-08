package app.lawnchair.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

class QuoteWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CardView(context, attrs, defStyleAttr) {

    private val quoteText: TextView

    init {
        radius = 48f
        setCardBackgroundColor(0xFF1B2E21.toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val quoteMarkTop = TextView(context).apply {
            text = "“"
            textSize = 28f
            setTextColor(0xFF4ADE80.toInt())
        }

        quoteText = TextView(context).apply {
            text = "Act boldly. Small steps every day lead to big results."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFE2E8F0.toInt())
        }

        val quoteMarkBottom = TextView(context).apply {
            text = "”"
            textSize = 28f
            gravity = Gravity.END
            setTextColor(0xFF4ADE80.toInt())
        }

        container.addView(quoteMarkTop)
        container.addView(quoteText)
        container.addView(quoteMarkBottom)

        addView(container)
    }

    fun setQuote(quote: String) {
        quoteText.text = quote
    }
}
