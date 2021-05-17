package app.lawnchair.nexuslauncher

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.TextKeyListener
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.animation.Interpolator
import android.widget.EditText
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.*
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.SearchUiManager
import com.android.launcher3.allapps.search.AllAppsSearchBarController
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.anim.PropertySetter
import com.android.launcher3.graphics.TintedDrawableSpan
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import java.util.*
import kotlin.math.round

class AllAppsHotseatQsb @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        QsbContainerView(context, attrs, defStyleAttr), Insettable, SearchUiManager, AllAppsSearchBarController.Callbacks, AllAppsStore.OnUpdateListener {
    private val mActivity: ActivityContext = ActivityContext.lookupContext(context)
    private val mFixedTranslationY: Int = resources.getDimensionPixelSize(R.dimen.search_widget_hotseat_height) / 2
    private val mMarginTopAdjusting: Int = resources.getDimensionPixelSize(R.dimen.search_widget_top_shift)
    private val mSearchBarController: AllAppsSearchBarController = AllAppsSearchBarController()
    private val mSearchQueryBuilder: SpannableStringBuilder = SpannableStringBuilder()
    private lateinit var mApps: AlphabeticalAppsList
    private lateinit var mAppsView: AllAppsContainerView
    private lateinit var mSearchWrapperView: View
    private lateinit var mFallbackSearchView: ExtendedEditText

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        mFallbackSearchView = findViewById(R.id.fallback_search_view)
        mSearchWrapperView = findViewById(R.id.search_wrapper_view)
        val spanned = SpannableString("  " + mFallbackSearchView.hint)
        spanned.setSpan(TintedDrawableSpan(context, R.drawable.ic_allapps_search),
                0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        mFallbackSearchView.hint = spanned
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mAppsView.appsStore?.addUpdateListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mAppsView.appsStore?.removeUpdateListener(this)
    }

    override fun setInsets(insets: Rect) {
        visibility = if (mActivity.deviceProfile.isVerticalBarLayout) View.GONE else View.VISIBLE
        val mlp: MarginLayoutParams = layoutParams as MarginLayoutParams
        mlp.topMargin = (-mFixedTranslationY).coerceAtLeast(insets.top - mMarginTopAdjusting)
        val padding: Rect = mActivity.deviceProfile.hotseatLayoutPadding
        setPaddingUnchecked(padding.left, 0, padding.right, 0)
        requestLayout()
    }

    override fun initialize(appsView: AllAppsContainerView) {
        mApps = appsView.apps
        mAppsView = appsView
        mAppsView.addElevationController(object : RecyclerView.OnScrollListener() {
            val initialElevation = mFallbackSearchView.elevation

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val currentScrollY = (recyclerView as BaseRecyclerView).currentScrollY
                val elevationScale = Utilities.boundToRange(currentScrollY / 255f, 0f, 1f)
                mFallbackSearchView.elevation = initialElevation + elevationScale * initialElevation
            }
        })
        mSearchBarController.initialize(DefaultAppSearchAlgorithm(mApps.apps),
                mFallbackSearchView, Launcher.cast(mActivity), this)
    }

    override fun onAppsUpdated() {
        mSearchBarController.refreshSearchResult()
    }

    override fun resetSearch() {
        mSearchBarController.reset()
    }

    override fun preDispatchKeyEvent(event: KeyEvent) {
        if (!mSearchBarController.isSearchFieldFocused &&
                event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey: Boolean = TextKeyListener.getInstance().onKeyDown(this, mSearchQueryBuilder,
                        event.keyCode, event)
                if (gotKey && mSearchQueryBuilder.isNotEmpty()) {
                    mSearchBarController.focusSearchField()
                }
            }
        }
    }

    override fun onSearchResult(query: String, apps: ArrayList<ComponentKey>) {
        mApps.setOrderedFilter(apps)
        notifyResultChanged()
        mAppsView.setLastSearchQuery(query)
    }

    override fun clearSearchResult() {
        if (mApps.setOrderedFilter(null)) {
            notifyResultChanged()
        }
        mSearchQueryBuilder.clear()
        mSearchQueryBuilder.clearSpans()
        Selection.setSelection(mSearchQueryBuilder, 0)
        mAppsView.onClearSearchResult()
    }

    private fun notifyResultChanged() {
        mAppsView.onSearchResultsChanged()
    }

    override fun getScrollRangeDelta(insets: Rect): Float {
        return if (mActivity.deviceProfile.isVerticalBarLayout) {
            0.0f
        } else {
            val dp: DeviceProfile = mActivity.deviceProfile
            val percentageOfAvailSpaceFromBottom = 0.45f
            val center = ((dp.hotseatBarSizePx - dp.hotseatCellHeightPx
                    - layoutParams.height - insets.bottom) * percentageOfAvailSpaceFromBottom).toInt()
            val bottomMargin = insets.bottom + center
            val topMargin = (-mFixedTranslationY).coerceAtLeast(insets.top - mMarginTopAdjusting)
            val myBot: Int = layoutParams.height + topMargin + mFixedTranslationY
            (bottomMargin + myBot).toFloat()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val parent = parent as View
        val parentWidth = parent.width - parent.paddingLeft - parent.paddingRight
        val width = right - left
        val availableSpace = parentWidth - (width)
        translationX = (parent.paddingLeft + (availableSpace / 2) - left).toFloat()
        offsetTopAndBottom(mFixedTranslationY)
    }

    private fun getWidth(width: Int): Int {
        if (mActivity.deviceProfile.isVerticalBarLayout) {
            val recyclerView = mAppsView.activeRecyclerView
            return width - recyclerView.paddingLeft - recyclerView.paddingRight
        }
        val padding = mActivity.deviceProfile.hotseatLayoutPadding
        return width - padding.left - padding.right
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val deviceProfile = mActivity.deviceProfile
        val width = getWidth((MeasureSpec.getSize(widthMeasureSpec)))
        val iconFrameWidth = width / deviceProfile.inv.numHotseatIcons
        val iconWidth = round(0.92f * (deviceProfile.iconSizePx.toFloat()))
        val iconPadding = (iconFrameWidth - iconWidth).toInt()
        setMeasuredDimension(
            width - iconPadding + paddingLeft + paddingRight,
            MeasureSpec.getSize(heightMeasureSpec)
        )
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }
    }

    override fun setContentVisibility(visibleElements: Int, setter: PropertySetter,
                                      interpolator: Interpolator) {
        val showAllAppsMode = visibleElements and LauncherState.ALL_APPS_CONTENT != 0
        setter.setViewAlpha(mSearchWrapperView, if (showAllAppsMode) 0f else 1f, Interpolators.LINEAR)
        setter.setViewAlpha(mFallbackSearchView, if (showAllAppsMode) 1f else 0f, Interpolators.LINEAR)
    }

    override fun setTextSearchEnabled(isEnabled: Boolean): EditText {
        return mFallbackSearchView
    }

    class HotseatQsbFragment : QsbFragment() {
        override fun isQsbEnabled(): Boolean {
            return true
        }

        override fun createWrapper(context: Context): FrameLayout {
            return HotseatQsbWrapper(context)
        }
    }

    class HotseatQsbWrapper @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
    ) : FrameLayout(context, attrs) {
        private val negativeMargin = (8 * Resources.getSystem().displayMetrics.density).toInt()

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            translationX = (-negativeMargin).toFloat()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val newWidth = width + negativeMargin + negativeMargin
            val newWidthSpec = MeasureSpec.makeMeasureSpec(newWidth, mode)
            super.onMeasure(newWidthSpec, heightMeasureSpec)
        }
    }

    init {
        Selection.setSelection(mSearchQueryBuilder, 0)
    }
}
