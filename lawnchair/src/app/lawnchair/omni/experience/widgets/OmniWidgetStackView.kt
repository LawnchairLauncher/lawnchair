package app.lawnchair.omni.experience.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.lawnchair.omni.experience.ExperienceStore
import app.lawnchair.omni.experience.widgetSwipeDelta
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One real hosted widget is inflated at a time. Swiping the toolbar never steals widget gestures. */
class OmniWidgetStackView(context: Context) : LinearLayout(context) {
    private val content = FrameLayout(context)
    private val position = TextView(context).apply { gravity = Gravity.CENTER }
    private var previousButton: Button? = null
    private var nextButton: Button? = null
    private var outerId = -1
    private var host: StackHost? = null
    private var scope: CoroutineScope? = null
    private var collection: Job? = null
    private var currentIds = emptyList<Int>()
    private var currentIndex = 0

    init {
        orientation = VERTICAL
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(
            Button(context).apply {
                previousButton = this
                text = if (layoutDirection == LAYOUT_DIRECTION_RTL) "›" else "‹"
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                layoutParams = LayoutParams((48 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt())
                contentDescription = context.getString(R.string.omni_previous_widget)
                setOnClickListener { move(-1) }
            },
        )
        bar.addView(position, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(
            Button(context).apply {
                nextButton = this
                text = if (layoutDirection == LAYOUT_DIRECTION_RTL) "‹" else "›"
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                layoutParams = LayoutParams((48 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt())
                contentDescription = context.getString(R.string.omni_next_widget)
                setOnClickListener { move(1) }
            },
        )
        bar.addView(
            Button(context).apply {
                text = context.getString(R.string.omni_edit)
                minWidth = 0
                minimumWidth = 0
                setPadding(4, 0, 4, 0)
                setOnClickListener { configure() }
            },
        )
        var startX = 0f
        position.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    startX = event.x
                    true
                }

                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val distance = event.x - startX
                    if (kotlin.math.abs(distance) > 48 * resources.displayMetrics.density) move(widgetSwipeDelta(distance > 0, layoutDirection == LAYOUT_DIRECTION_RTL))
                    if (kotlin.math.abs(distance) <= 48 * resources.displayMetrics.density) position.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> true
            }
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        showEmpty()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        previousButton?.text = if (layoutDirection == LAYOUT_DIRECTION_RTL) "›" else "‹"
        nextButton?.text = if (layoutDirection == LAYOUT_DIRECTION_RTL) "‹" else "›"
    }

    fun bind(id: Int) {
        if (id == outerId) return
        stop()
        outerId = id
        if (isAttachedToWindow) start()
    }
    private fun configure() {
        if (outerId > 0) context.startActivity(WidgetStackActivity.intent(context, outerId))
    }
    private fun move(delta: Int) {
        val count = currentIds.size
        if (count < 2) return
        val index = Math.floorMod(currentIndex + delta, count)
        scope?.launch(Dispatchers.IO) {
            ExperienceStore.get(context).update { state -> state.copy(stacks = state.stacks.map { if (it.outerId == outerId) it.copy(selected = index) else it }) }
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }
    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (outerId <= 0 || scope != null) return
        val owner = findViewTreeLifecycleOwner() ?: context as? LifecycleOwner ?: return
        val work = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate + kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
                android.util.Log.w("OmniWidgetStack", "Widget stack unavailable", error)
                post { showEmpty() }
            },
        )
        scope = work
        collection = work.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val widgetHost = StackHost(context, outerId)
                host = widgetHost
                try {
                    widgetHost.startListening()
                    ExperienceStore.get(context).state.filterNotNull().map { state -> state.stacks.firstOrNull { it.outerId == outerId } }.distinctUntilChanged().collectLatest { stack ->
                        currentIds = stack?.widgetIds.orEmpty()
                        currentIndex = (stack?.selected ?: 0).coerceIn(0, (currentIds.size - 1).coerceAtLeast(0))
                        val childId = currentIds.getOrNull(currentIndex)
                        widgetHost.releaseViews()
                        content.removeAllViews()
                        val info = childId?.let { id -> withContext(Dispatchers.IO) { AppWidgetManager.getInstance(context).getAppWidgetInfo(id) } }
                        if (info == null || info.provider == OmniWidgetStack.component(context)) {
                            showEmpty()
                        } else {
                            val view = widgetHost.createView(context, childId, info)
                            content.addView(view, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                            val density = resources.displayMetrics.density
                            view.post {
                                if (view.parent === content) {
                                    view.updateAppWidgetSize(null, (content.width / density).toInt(), (content.height / density).toInt(), (content.width / density).toInt(), (content.height / density).toInt())
                                }
                            }
                            position.text = context.getString(R.string.omni_stack_position, currentIndex + 1, currentIds.size)
                        }
                    }
                } finally {
                    runCatching { widgetHost.stopListening() }
                    widgetHost.releaseViews()
                    content.removeAllViews()
                    host = null
                }
            }
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        content.post {
            val view = content.getChildAt(0) as? android.appwidget.AppWidgetHostView ?: return@post
            val density = resources.displayMetrics.density
            val width = (content.width / density).toInt()
            val height = (content.height / density).toInt()
            view.updateAppWidgetSize(null, width, height, width, height)
        }
    }

    private fun showEmpty() {
        content.removeAllViews()
        content.addView(
            Button(context).apply {
                text = context.getString(R.string.omni_stack_add)
                setOnClickListener { configure() }
            },
        )
        position.text = context.getString(R.string.omni_widget_stacks)
    }
    private fun stop() {
        collection?.cancel()
        scope?.cancel()
        scope = null
        host = null
    }
}
