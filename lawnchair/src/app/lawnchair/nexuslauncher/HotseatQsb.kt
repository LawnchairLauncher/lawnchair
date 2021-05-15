package app.lawnchair.nexuslauncher

import android.content.Context
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
import app.lawnchair.util.preferences.PreferenceManager.Companion.getInstance
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

class HotseatQsb @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        QsbContainerView(context, attrs, defStyleAttr), Insettable, SearchUiManager, AllAppsSearchBarController.Callbacks, AllAppsStore.OnUpdateListener {
    private val mActivity: ActivityContext = ActivityContext.lookupContext(context)
    private val mFixedTranslationY: Int = resources.getDimensionPixelSize(R.dimen.search_widget_hotseat_height) / 2
    private val mMarginTopAdjusting: Int = resources.getDimensionPixelSize(R.dimen.search_widget_top_shift)
    private val mSearchBarController: AllAppsSearchBarController = AllAppsSearchBarController()
    private val mSearchQueryBuilder: SpannableStringBuilder = SpannableStringBuilder()
    private var mApps: AlphabeticalAppsList? = null
    private var mAppsView: AllAppsContainerView? = null
    private var mSearchWrapperView: View? = null
    private var mFallbackSearchView: ExtendedEditText? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        mFallbackSearchView = findViewById(R.id.fallback_search_view)
        mSearchWrapperView = findViewById(R.id.search_wrapper_view)
        val spanned = SpannableString("  " + mFallbackSearchView?.hint)
        spanned.setSpan(TintedDrawableSpan(context, R.drawable.ic_allapps_search),
                0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        mFallbackSearchView?.hint = spanned

        val prefs = getInstance(context)
        // TODO: Recolour cursor.
        mFallbackSearchView?.setHintTextColor(prefs.accentColor.get())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mAppsView?.appsStore?.addUpdateListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mAppsView?.appsStore?.removeUpdateListener(this)
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
        mSearchBarController.initialize(DefaultAppSearchAlgorithm(mApps?.apps),
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
        mApps?.setOrderedFilter(apps)
        notifyResultChanged()
        mAppsView?.setLastSearchQuery(query)
    }

    override fun clearSearchResult() {
        if (mApps?.setOrderedFilter(null) == true) {
            notifyResultChanged()
        }
        mSearchQueryBuilder.clear()
        mSearchQueryBuilder.clearSpans()
        Selection.setSelection(mSearchQueryBuilder, 0)
        mAppsView?.onClearSearchResult()
    }

    private fun notifyResultChanged() {
        mAppsView?.onSearchResultsChanged()
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
        offsetTopAndBottom(mFixedTranslationY)
    }

    override fun setContentVisibility(visibleElements: Int, setter: PropertySetter,
                                      interpolator: Interpolator) {
        val showAllAppsMode = visibleElements and LauncherState.ALL_APPS_CONTENT != 0
        setter.setViewAlpha(mSearchWrapperView, if (showAllAppsMode) 0f else 1f, Interpolators.LINEAR)
        setter.setViewAlpha(mFallbackSearchView, if (showAllAppsMode) 1f else 0f, Interpolators.LINEAR)
    }

    override fun setTextSearchEnabled(isEnabled: Boolean): EditText? {
        return mFallbackSearchView
    }

    class HotseatQsbFragment : QsbFragment() {
        override fun isQsbEnabled(): Boolean {
            return true
        }
    }

    init {
        Selection.setSelection(mSearchQueryBuilder, 0)
    }
}
