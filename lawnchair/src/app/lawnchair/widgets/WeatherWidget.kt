package app.lawnchair.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

class WeatherWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CardView(context, attrs, defStyleAttr) {

    private val tempText: TextView
    private val humidityText: TextView
    private val dewPointText: TextView

    init {
        radius = 48f
        setCardBackgroundColor(0xFF143823.toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(32, 28, 32, 28)
        }

        val title = TextView(context).apply {
            text = "Humidity"
            textSize = 14f
            setTextColor(0xFF86EFAC.toInt())
        }

        dewPointText = TextView(context).apply {
            text = "2.4 Dew Point"
            textSize = 12f
            setTextColor(0xFFA7F3D0.toInt())
        }

        humidityText = TextView(context).apply {
            text = "62%"
            textSize = 36f
            setTextColor(0xFFFFFFFF.toInt())
        }

        tempText = TextView(context).apply {
            text = "28°"
            textSize = 24f
            setTextColor(0xFFFDE047.toInt())
        }

        container.addView(title)
        container.addView(dewPointText)
        container.addView(humidityText)
        container.addView(tempText)

        addView(container)
    }

    fun updateWeatherData(temp: String, humidity: String, dewPoint: String) {
        tempText.text = temp
        humidityText.text = humidity
        dewPointText.text = dewPoint
    }
}
