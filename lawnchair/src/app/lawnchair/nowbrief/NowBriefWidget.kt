package app.lawnchair.nowbrief

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

enum class NowBriefState {
    WEATHER_AND_CALENDAR,
    MEDIA_PLAYING,
    QUICK_SHORTCUTS,
    SMART_NOTIFICATION,
}

class NowBriefWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CardView(context, attrs, defStyleAttr) {

    private val titleText: TextView
    private val subtextText: TextView
    private val actionIcon: TextView
    private var currentState: NowBriefState = NowBriefState.WEATHER_AND_CALENDAR

    init {
        radius = 56f
        setCardBackgroundColor(0xFF132A1C.toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 24, 36, 24)
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleText = TextView(context).apply {
            textSize = 15f
            setTextColor(0xFF86EFAC.toInt())
            text = "Good Morning · 24° Sunny"
        }

        subtextText = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFCBD5E1.toInt())
            text = "Next: Design Sync @ 10:00 AM"
        }

        textContainer.addView(titleText)
        textContainer.addView(subtextText)

        actionIcon = TextView(context).apply {
            textSize = 20f
            text = "▶️"
            setPadding(16, 0, 0, 0)
        }

        container.addView(textContainer)
        container.addView(actionIcon)

        addView(container)

        setOnClickListener {
            cycleState()
        }
    }

    fun setState(state: NowBriefState) {
        currentState = state
        when (state) {
            NowBriefState.WEATHER_AND_CALENDAR -> {
                titleText.text = "Good Morning · 24° Sunny"
                subtextText.text = "Next: Design Sync @ 10:00 AM"
                actionIcon.text = "📅"
            }
            NowBriefState.MEDIA_PLAYING -> {
                titleText.text = "Now Playing"
                subtextText.text = "Starboy — The Weeknd"
                actionIcon.text = "⏸️"
            }
            NowBriefState.QUICK_SHORTCUTS -> {
                titleText.text = "Quick Brief"
                subtextText.text = "3 Alarms active · Battery 85%"
                actionIcon.text = "⚡"
            }
            NowBriefState.SMART_NOTIFICATION -> {
                titleText.text = "Important Alert"
                subtextText.text = "Meeting starting in 5 minutes"
                actionIcon.text = "🔔"
            }
        }
    }

    private fun cycleState() {
        val nextOrdinal = (currentState.ordinal + 1) % NowBriefState.values().size
        setState(NowBriefState.values()[nextOrdinal])
    }
}
