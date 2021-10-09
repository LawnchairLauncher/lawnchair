package app.lawnchair.allapps

import android.content.Context
import android.graphics.Rect
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.method.TextKeyListener
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import app.lawnchair.launcher
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.allapps.*
import com.android.launcher3.allapps.search.AllAppsSearchBarController
import com.android.launcher3.search.SearchCallback
import kotlin.math.max

class AllAppsSearchInput(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs),
    Insettable, SearchUiManager,
    SearchCallback<AllAppsGridAdapter.AdapterItem>,
    AllAppsStore.OnUpdateListener {

    private lateinit var hint: TextView
    private lateinit var input: FallbackSearchInputView
    private lateinit var actionButton: ImageButton

    private val qsbMarginTopAdjusting = resources.getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting)
    private val allAppsSearchVerticalOffset = resources.getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset)

    private val launcher = context.launcher
    private val searchBarController = AllAppsSearchBarController()
    private val searchQueryBuilder = SpannableStringBuilder().apply {
        Selection.setSelection(this, 0)
    }

    private lateinit var apps: AlphabeticalAppsList
    private lateinit var appsView: AllAppsContainerView

    override fun onFinishInflate() {
        super.onFinishInflate()
        hint = ViewCompat.requireViewById(this, R.id.hint)
        input = ViewCompat.requireViewById(this, R.id.input)
        input.setHint(R.string.all_apps_search_bar_hint)
        input.addTextChangedListener {
            actionButton.isVisible = !it.isNullOrEmpty()
        }
        actionButton = ViewCompat.requireViewById(this, R.id.action_btn)
        actionButton.isVisible = false
        actionButton.setOnClickListener {
            input.reset()
        }
        findViewById<FrameLayout>(R.id.button_wrapper).clipToOutline = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        appsView.appsStore.addUpdateListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        appsView.appsStore.removeUpdateListener(this)
    }

    override fun initializeSearch(appsView: AllAppsContainerView) {
        apps = appsView.apps
        this.appsView = appsView
        searchBarController.initialize(
            LawnchairAppSearchAlgorithm(launcher),
            input, launcher, this
        )
        input.initialize(appsView)
    }

    override fun onAppsUpdated() {
        searchBarController.refreshSearchResult()
    }

    override fun resetSearch() {
        searchBarController.reset()
    }

    override fun preDispatchKeyEvent(event: KeyEvent) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!searchBarController.isSearchFieldFocused && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey = TextKeyListener.getInstance().onKeyDown(input, searchQueryBuilder, event.keyCode, event)
                if (gotKey && searchQueryBuilder.isNotEmpty()) {
                    searchBarController.focusSearchField()
                }
            }
        }
    }

    override fun onSearchResult(query: String, items: ArrayList<AllAppsGridAdapter.AdapterItem>?) {
        if (items != null) {
            apps.setSearchResults(items)
            notifyResultChanged()
            appsView.setLastSearchQuery(query)
        }
    }

    override fun onAppendSearchResult(
        query: String,
        items: java.util.ArrayList<AllAppsGridAdapter.AdapterItem>?
    ) {
        if (items != null) {
            apps.appendSearchResults(items)
            notifyResultChanged()
        }
    }

    override fun clearSearchResult() {
        if (apps.setSearchResults(null)) {
            notifyResultChanged()
        }

        // Clear the search query
        searchQueryBuilder.clear()
        searchQueryBuilder.clearSpans()
        Selection.setSelection(searchQueryBuilder, 0)
        appsView.onClearSearchResult()
    }

    private fun notifyResultChanged() {
        appsView.onSearchResultsChanged()
    }

    override fun setInsets(insets: Rect) {
        val lp = layoutParams as MarginLayoutParams
        lp.topMargin = max(-allAppsSearchVerticalOffset, insets.top - qsbMarginTopAdjusting)

        val dp = launcher.deviceProfile
        val horizontalPadding = dp.desiredWorkspaceLeftRightMarginPx + dp.cellLayoutPaddingLeftRightPx
        setPadding(horizontalPadding, paddingTop, horizontalPadding, paddingBottom)
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        offsetTopAndBottom(allAppsSearchVerticalOffset)
    }

    override fun getEditText(): ExtendedEditText {
        return input
    }
}
