package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_POINT_MARK
import android.text.TextUtils
import android.text.method.TextKeyListener
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.LawnchairSearchAlgorithm
import app.lawnchair.theme.drawable.DrawableTokens
import com.android.launcher3.Insettable
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.*
import com.android.launcher3.allapps.search.AllAppsSearchBarController
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Themes
import com.patrykmichalik.opto.core.firstBlocking
import java.util.*
import kotlin.math.max

class AllAppsSearchInput(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs),
    Insettable, SearchUiManager,
    SearchCallback<AllAppsGridAdapter.AdapterItem>,
    AllAppsStore.OnUpdateListener, ViewTreeObserver.OnGlobalLayoutListener {

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

    private var focusedResultTitle = ""
    private var canShowHint = false

    private val bg = DrawableTokens.SearchInputFg.resolve(context)
    private val bgAlphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 300 }
    private var bgVisible = true
    private var bgAlpha = 1f

    override fun onFinishInflate() {
        super.onFinishInflate()

        val wrapper = ViewCompat.requireViewById<View>(this, R.id.search_wrapper)
        wrapper.background = bg
        bgAlphaAnimator.addUpdateListener { updateBgAlpha() }

        hint = ViewCompat.requireViewById(this, R.id.hint)

        input = ViewCompat.requireViewById(this, R.id.input)
        with(input) {
            if (LawnchairSearchAlgorithm.isDeviceSearchEnabled(context)) {
                setHint(R.string.all_apps_device_search_hint)
            } else {
                setHint(R.string.all_apps_search_bar_hint)
            }
            addTextChangedListener {
                actionButton.isVisible = !it.isNullOrEmpty()
            }
        }

        actionButton = ViewCompat.requireViewById(this, R.id.action_btn)
        with(actionButton) {
            isVisible = false
            setOnClickListener {
                input.reset()
            }
        }

        input.addTextChangedListener(
            beforeTextChanged = { _, _, _, _ ->
                hint.isInvisible = true
            },
            afterTextChanged = {
                updateHint()
                if (input.text.toString() == "/lawnchairdebug") {
                    val enableDebugMenu = PreferenceManager.getInstance(context).enableDebugMenu
                    enableDebugMenu.set(!enableDebugMenu.get())
                    launcher.stateManager.goToState(LauncherState.NORMAL)
                }
            }
        )

        val hide = PreferenceManager2.getInstance(context).hideAppDrawerSearchBar.firstBlocking()
        if (hide) {
            isInvisible = true
            layoutParams.height = 0
        }
    }

    override fun setFocusedResultTitle(title: CharSequence?) {
        focusedResultTitle = title?.toString().orEmpty()
        updateHint()
    }

    private fun updateHint() {
        val inputString = input.text.toString()
        val inputLowerCase = inputString.lowercase(Locale.getDefault())
        val focusedLowerCase = focusedResultTitle.lowercase(Locale.getDefault())
        if (canShowHint
            && !TextUtils.isEmpty(inputLowerCase) && !TextUtils.isEmpty(focusedLowerCase)
            && focusedLowerCase.matches(Regex("^[\\x00-\\x7F]*$"))
            && focusedLowerCase.startsWith(inputLowerCase)
        ) {
            val hintColor = Themes.getAttrColor(context, android.R.attr.textColorTertiary)
            val hintText = SpannableStringBuilder(inputString)
                .append(focusedLowerCase.substring(inputLowerCase.length))
            hintText.setSpan(ForegroundColorSpan(Color.TRANSPARENT), 0, inputLowerCase.length, SPAN_POINT_MARK)
            hintText.setSpan(ForegroundColorSpan(hintColor), inputLowerCase.length, hintText.length, SPAN_POINT_MARK)
            hint.text = hintText
            hint.isVisible = true
        }
    }

    override fun onGlobalLayout() {
        canShowHint = input.layout?.getEllipsisCount(0) == 0
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        appsView.appsStore.addUpdateListener(this)
        input.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        appsView.appsStore.removeUpdateListener(this)
        input.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun initializeSearch(appsView: AllAppsContainerView) {
        apps = appsView.apps
        this.appsView = appsView
        searchBarController.initialize(
            LawnchairSearchAlgorithm.create(context),
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
        items: ArrayList<AllAppsGridAdapter.AdapterItem>?
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
        appsView.floatingHeaderView?.setCollapsed(false)
    }

    private fun notifyResultChanged() {
        appsView.onSearchResultsChanged()
    }

    override fun setInsets(insets: Rect) {
        val lp = layoutParams as MarginLayoutParams
        if (isInvisible) {
            lp.topMargin = insets.top - allAppsSearchVerticalOffset
            return
        }
        lp.topMargin = max(-allAppsSearchVerticalOffset, insets.top - qsbMarginTopAdjusting)

        val dp = launcher.deviceProfile
        val horizontalPadding = dp.desiredWorkspaceHorizontalMarginPx + dp.cellLayoutPaddingLeftRightPx
        setPadding(horizontalPadding, paddingTop, horizontalPadding, paddingBottom)
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        offsetTopAndBottom(allAppsSearchVerticalOffset)
    }

    override fun getEditText() = input

    override fun setBackgroundVisibility(visible: Boolean, maxAlpha: Float) {
        if (bgVisible != visible) {
            bgVisible = visible
            bgAlpha = maxAlpha
            if (visible) {
                bgAlphaAnimator.start()
            } else {
                bgAlphaAnimator.reverse()
            }
        } else if (bgAlpha != maxAlpha && !bgAlphaAnimator.isRunning && visible) {
            bgAlpha = maxAlpha
            bgAlphaAnimator.setCurrentFraction(maxAlpha)
            updateBgAlpha()
        }
    }

    override fun getBackgroundVisibility(): Boolean {
        return bgVisible
    }

    private fun updateBgAlpha() {
        val fraction = bgAlphaAnimator.animatedFraction
        bg.alpha = (Utilities.mapRange(fraction, 0f, bgAlpha) * 255).toInt()
    }
}
