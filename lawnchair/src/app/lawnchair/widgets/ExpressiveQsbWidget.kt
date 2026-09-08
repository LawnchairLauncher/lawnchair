package app.lawnchair.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

class ExpressiveQsbWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CardView(context, attrs, defStyleAttr) {

    init {
        radius = 64f
        setCardBackgroundColor(0xFF1F3A2B.toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 20, 32, 20)
        }

        val googleLogo = TextView(context).apply {
            text = "G"
            textSize = 22f
            setTextColor(0xFF86EFAC.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val rightActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val incognitoBtn = TextView(context).apply {
            text = "🕶️"
            textSize = 18f
            setPadding(16, 0, 16, 0)
        }

        val aiBtn = TextView(context).apply {
            text = "✦"
            textSize = 18f
            setTextColor(0xFF86EFAC.toInt())
            setPadding(16, 0, 16, 0)
        }

        val lensBtn = TextView(context).apply {
            text = "📷"
            textSize = 18f
            setPadding(16, 0, 16, 0)
        }

        rightActions.addView(incognitoBtn)
        rightActions.addView(aiBtn)
        rightActions.addView(lensBtn)

        container.addView(googleLogo)
        container.addView(rightActions)

        addView(container)
    }
}
