package app.lawnchair.studyplanner

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import app.lawnchair.StudyPlannerActivity
import app.lawnchair.studyplanner.data.QuotesDataSource
import com.android.launcher3.R
import com.android.systemui.dagger.qualifiers.Main
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Calendar.DATE
import java.util.Calendar.DAY_OF_WEEK
import java.util.Calendar.MONTH
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Created by shubhampandey
 */
class StudyPlannerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, ): ConstraintLayout(context, attrs) {

    private var view: View

    private val calendar by lazy { Calendar.getInstance() }
    private val quotesDataSource by lazy { QuotesDataSource(context) }

    private var job: Job? = null
    companion object {
        private val WEEKS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
//        private const val ONE_HR_IN_MS = 60 * 60 * 1000
          private const val ONE_HR_IN_MS = 1000
    }

    private var quoteTextView: AppCompatTextView
    private var enterButton: AppCompatButton
    init {
        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.layout_study_planner, this, false)
        quoteTextView = view.findViewById(R.id.tv_quote)
        enterButton = view.findViewById(R.id.bt_enter)
        addView(view)
        configureDate()
        configureMySpaceView()
        configureQuotes()
        setupListener()
    }

    private fun setupListener() {
        enterButton.setOnClickListener {
            context.startActivity(Intent(context, StudyPlannerActivity::class.java))
        }
    }

    private fun configureQuotes() {
        quoteTextView.text = quotesDataSource.quotes[0]
        job = CoroutineScope(Dispatchers.IO).launchPeriodicAsync(1)
    }

    private fun configureMySpaceView() {
        val weekIdx = calendar.get(DAY_OF_WEEK)
        WEEKS.forEachIndexed { idx, week ->
            val childView = LayoutInflater.from(context).inflate(R.layout.item_week_view, null, false)
            val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.marginEnd = 60.toDp
            val textView = childView.findViewById<AppCompatTextView>(R.id.tv_week)
            textView.text = week.substring(0, 1)
            val dotView = childView.findViewById<AppCompatImageView>(R.id.iv_dot)
            if (idx == weekIdx - 1) {
                textView.backgroundTintList = ContextCompat.getColorStateList(context, R.color.green_500)
                textView.setTextColor(ContextCompat.getColor(context, R.color.white_50))
            } else {
                if (idx == weekIdx) {
                    dotView.visibility = View.VISIBLE
                }
                textView.backgroundTintList = ContextCompat.getColorStateList(context, R.color.lottie_grey200)
            }
            view.findViewById<ViewGroup>(R.id.ll_weeks).addView(childView, layoutParams)
        }
    }

    private fun CoroutineScope.launchPeriodicAsync(repeatHr: Int) = this.async {
        if (repeatHr > 0) {
            while (isActive) {
                withContext(Dispatchers.Main) {
                    updateQuote()
                }
                delay(repeatHr.minutes)
            }
        } else {
            withContext(Dispatchers.Main) {
                updateQuote()
            }
        }
    }

    private fun updateQuote() {
        val currentTimeStamp = System.currentTimeMillis()
        val lastShownTimestamp = quotesDataSource.lastShownTimestamp
        val deltaTime = currentTimeStamp - lastShownTimestamp
        Log.d("StudyPlannerView", "This is repeating..with delta time: $deltaTime")
        if (deltaTime > ONE_HR_IN_MS) {
            var index = quotesDataSource.lastShownQuote
            val quoteList = quotesDataSource.quotes
            if (index >= quoteList.size) {
                index = 0
            }
            val quote = quoteList[index++]
            quoteTextView.text = quote
            quotesDataSource.setLastTimeShown(currentTimeStamp)
            quotesDataSource.setLastQuoteShown(index)
        }
    }

    private val Int.toDp get() = (this / Resources.getSystem().displayMetrics.density).toInt()
    private fun configureDate() {
        val dayOfTheWeek = WEEKS[calendar.get(DAY_OF_WEEK)]
        val month = MONTHS[calendar.get(MONTH)]
        view.findViewById<AppCompatTextView>(R.id.tv_date).text = "${dayOfTheWeek}, ${calendar.get(DATE)} $month"
    }

    fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat(format, locale)
        return formatter.format(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        configureQuotes()
    }
}
