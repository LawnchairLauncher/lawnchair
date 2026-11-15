package app.lawnchair.smartspace

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.ViewPager
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BcSmartspaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    var previewMode: Boolean = false,
) : FrameLayout(context, attrs) {

    private val provider = SmartspaceProvider.INSTANCE.get(context)

    private lateinit var viewPager: ViewPager
    private lateinit var indicator: PageIndicator
    private val adapter = CardPagerAdapter(context)
    private var scrollState = ViewPager.SCROLL_STATE_IDLE
    private var pendingTargets: List<SmartspaceTarget>? = null
    private var runningAnimation: Animator? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        viewPager = findViewById<ViewPager>(R.id.smartspace_card_pager)!!
        viewPager.isSaveEnabled = false
        indicator = findViewById<PageIndicator>(R.id.smartspace_page_indicator)!!

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                indicator.setPageOffset(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
            }

            override fun onPageScrollStateChanged(state: Int) {
                scrollState = state
                if (state == 0) {
                    pendingTargets?.let {
                        pendingTargets = null
                        onSmartspaceTargetsUpdate(it)
                    }
                }
            }
        })

        val targets = if (previewMode) provider.previewTargets else provider.targets
        repeatOnAttached {
            viewPager.adapter = adapter
            targets
                .onEach(::onSmartspaceTargetsUpdate)
                .launchIn(this)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val res = context.resources
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val smartspaceHeight = res.getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
        val designTopPadding = res.getDimensionPixelSize(R.dimen.enhanced_smartspace_padding_top)
        if (height <= 0 || height >= smartspaceHeight) {
            setCardSize(smartspaceHeight, designTopPadding)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            scaleX = 1f
            scaleY = 1f
            return
        }

        // The row is shorter than the design height (dense workspace grids). Give up the
        // spare padding around the text first, and only scale the whole view down once the
        // text itself no longer fits.
        val minHeight = getMinCardHeight()
        val cardHeight = max(height, minHeight)
        setCardSize(cardHeight, ((cardHeight - minHeight) / 2).coerceAtMost(designTopPadding))

        val scale = height.toFloat() / cardHeight.toFloat()
        val width = (MeasureSpec.getSize(widthMeasureSpec).toFloat() / scale).roundToInt()
        super.onMeasure(
            makeMeasureSpec(width, EXACTLY),
            makeMeasureSpec(cardHeight, EXACTLY),
        )
        scaleX = scale
        scaleY = scale
        pivotX = 0f
        pivotY = cardHeight.toFloat() / 2f
    }

    /** Height of the title and subtitle lines plus the room that has to stay free below them. */
    private fun getMinCardHeight(): Int {
        val res = context.resources
        val textHeight = res.getDimensionPixelSize(R.dimen.enhanced_smartspace_title_line_height) +
            res.getDimensionPixelSize(R.dimen.enhanced_smartspace_subtitle_margin_top) +
            res.getDimensionPixelSize(R.dimen.enhanced_smartspace_subtitle_line_height)
        val bottomReserve = if (adapter.count > 1) {
            // Keep the text clear of the page indicator dots.
            res.getDimensionPixelSize(R.dimen.page_indicator_dot_size) +
                res.getDimensionPixelSize(R.dimen.page_indicator_padding_top_bottom)
        } else {
            res.getDimensionPixelSize(R.dimen.enhanced_smartspace_extras_margin_top)
        }
        return textHeight + bottomReserve
    }

    private fun setCardSize(height: Int, topPadding: Int) {
        // Picked up by the measure pass that follows, so no requestLayout is needed.
        viewPager.layoutParams.height = height
        adapter.cardTopPadding = topPadding
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        viewPager.setOnLongClickListener(l)
    }

    private fun onSmartspaceTargetsUpdate(targets: List<SmartspaceTarget>) {
        if (adapter.count > 1 && scrollState != ViewPager.SCROLL_STATE_IDLE) {
            pendingTargets = targets
            return
        }

        val sortedTargets = targets.sortedByDescending { it.score }.toMutableList()
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val currentItem = viewPager.currentItem
        val index = if (isRtl) adapter.count - currentItem else currentItem
        if (isRtl) {
            sortedTargets.reverse()
        }

        val oldCard = adapter.getCardAtPosition(currentItem)
        adapter.setTargets(sortedTargets)
        val count = adapter.count
        if (isRtl) {
            viewPager.setCurrentItem((count - index).coerceIn(0 until count), false)
        }
        indicator.setNumPages(targets.size)
        oldCard?.let { animateSmartspaceUpdate(it) }
        adapter.notifyDataSetChanged()
    }

    private fun animateSmartspaceUpdate(oldCard: BcSmartspaceCard) {
        if (runningAnimation != null || oldCard.parent != null) return

        val animParent = viewPager.parent as ViewGroup
        oldCard.measure(makeMeasureSpec(viewPager.width, EXACTLY), makeMeasureSpec(viewPager.height, EXACTLY))
        oldCard.layout(viewPager.left, viewPager.top, viewPager.right, viewPager.bottom)
        val shift = resources.getDimension(R.dimen.enhanced_smartspace_dismiss_margin)
        val animator = AnimatorSet()
        animator.play(
            ObjectAnimator.ofFloat(
                oldCard,
                View.TRANSLATION_Y,
                0f,
                (-height).toFloat() - shift,
            ),
        )
        animator.play(ObjectAnimator.ofFloat(oldCard, View.ALPHA, 1f, 0f))
        animator.play(
            ObjectAnimator.ofFloat(
                viewPager,
                View.TRANSLATION_Y,
                height.toFloat() + shift,
                0f,
            ),
        )
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                animParent.overlay.add(oldCard)
            }

            override fun onAnimationEnd(animation: Animator) {
                animParent.overlay.remove(oldCard)
                runningAnimation = null
            }
        })
        runningAnimation = animator
        animator.start()
    }
}
