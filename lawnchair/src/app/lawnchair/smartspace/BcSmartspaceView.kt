package app.lawnchair.smartspace

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.viewpager.widget.ViewPager
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.SmartspaceProvider
import com.android.launcher3.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

class BcSmartspaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val scope = MainScope()
    private var currentJob: Job? = null
    private val provider = SmartspaceProvider.INSTANCE.get(context)

    private lateinit var viewPager: ViewPager
    private val adapter = CardPagerAdapter(context)

    override fun onFinishInflate() {
        super.onFinishInflate()
        viewPager = findViewById(R.id.smartspace_card_pager)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val smartspaceHeight =
            context.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
        if (height <= 0 || height >= smartspaceHeight) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            scaleX = 1f
            scaleY = 1f
            return
        }

        val scale = height.toFloat() / smartspaceHeight.toFloat()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec((height.toFloat() / scale).roundToInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(smartspaceHeight, MeasureSpec.EXACTLY)
        )
        scaleX = scale
        scaleY = scale
        pivotX = 0f
        pivotY = smartspaceHeight.toFloat() / 2f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewPager.adapter = adapter

        currentJob = provider.targets
            .onEach(this::onSmartspaceTargetsUpdate)
            .launchIn(scope)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentJob?.cancel()
        currentJob = null
    }

    private fun onSmartspaceTargetsUpdate(targets: List<SmartspaceTarget>) {
        adapter.setTargets(targets.sortedByDescending { it.score })
    }
}
