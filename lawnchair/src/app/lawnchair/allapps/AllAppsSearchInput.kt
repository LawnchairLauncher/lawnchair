package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.provider.SearchRecentSuggestions
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_POINT_MARK
import android.text.method.TextKeyListener
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.subscribeBlocking
import app.lawnchair.qsb.AssistantIconView
import app.lawnchair.qsb.LawnQsbLayout.Companion.getLensIntent
import app.lawnchair.qsb.LawnQsbLayout.Companion.getSearchProvider
import app.lawnchair.qsb.ThemingMethod
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.setThemedIconResource
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.theme.drawable.DrawableTokens
import app.lawnchair.util.viewAttachedScope
import com.android.launcher3.Insettable
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.SearchUiManager
import com.android.launcher3.allapps.search.AllAppsSearchBarController
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Themes
import com.patrykmichalik.opto.core.firstBlocking
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.launch

class AllAppsSearchInput(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs),
    Insettable,
    SearchUiManager,
    SearchCallback<AdapterItem>,
    AllAppsStore.OnUpdateListener,
    ViewTreeObserver.OnGlobalLayoutListener {

    private lateinit var hint: TextView
    private lateinit var input: FallbackSearchInputView
    private lateinit var actionButton: ImageButton
    private lateinit var searchIcon: ImageButton

    private lateinit var micIcon: AssistantIconView
    private lateinit var lensIcon: ImageButton

    private val qsbMarginTopAdjusting = resources.getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting)
    private val allAppsSearchVerticalOffset = resources.getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset)

    private val launcher = context.launcher
    private val searchBarController = AllAppsSearchBarController()
    private val searchQueryBuilder = SpannableStringBuilder().apply {
        Selection.setSelection(this, 0)
    }

    private lateinit var apps: LawnchairAlphabeticalAppsList<*>
    private lateinit var appsView: ActivityAllAppsContainerView<*>

    private var focusedResultTitle = ""
    private var canShowHint = false

    private val bg = DrawableTokens.SearchInputFg.resolve(context)
    private val bgAlphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 300 }
    private var bgVisible = true
    private var bgAlpha = 1f
    private val suggestionsRecent = SearchRecentSuggestions(launcher, LawnchairRecentSuggestionProvider.AUTHORITY, LawnchairRecentSuggestionProvider.MODE)
    private val prefs = PreferenceManager.getInstance(launcher)
    private val prefs2 = PreferenceManager2.getInstance(launcher)

    override fun onFinishInflate() {
        super.onFinishInflate()

        val wrapper = ViewCompat.requireViewById<View>(this, R.id.search_wrapper)
        wrapper.background = bg
        bgAlphaAnimator.addUpdateListener { updateBgAlpha() }

        hint = ViewCompat.requireViewById(this, R.id.hint)

        input = ViewCompat.requireViewById(this, R.id.input)

        searchIcon = ViewCompat.requireViewById(this, R.id.search_icon)
        micIcon = ViewCompat.requireViewById(this, R.id.mic_btn)
        lensIcon = ViewCompat.requireViewById(this, R.id.lens_btn)

        val shouldShowIcons = prefs2.matchHotseatQsbStyle.firstBlocking()

        val searchProvider = getSearchProvider(context, prefs2)
        val isGoogle = searchProvider == Google || searchProvider == GoogleGo || searchProvider == PixelSearch
        val supportsLens = searchProvider == Google || searchProvider == PixelSearch

        val lensIntent = getLensIntent(context)
        val voiceIntent = AssistantIconView.getVoiceIntent(searchProvider, context)

        micIcon.isVisible = shouldShowIcons && voiceIntent != null
        lensIcon.isVisible = shouldShowIcons && supportsLens && lensIntent != null

        with(input) {
            if (prefs2.searchAlgorithm.firstBlocking() != LawnchairSearchAlgorithm.APP_SEARCH) {
                setHint(R.string.all_apps_device_search_hint)
            } else {
                setHint(R.string.all_apps_search_bar_hint)
            }
            addTextChangedListener {
                actionButton.isVisible = !it.isNullOrEmpty()
                micIcon.isVisible = shouldShowIcons && voiceIntent != null && it.isNullOrEmpty()
                lensIcon.isVisible = shouldShowIcons && supportsLens && lensIntent != null && it.isNullOrEmpty()
            }
        }

        actionButton = ViewCompat.requireViewById(this, R.id.action_btn)
        with(actionButton) {
            isVisible = false
            setOnClickListener {
                input.reset()
            }
        }

        prefs2.themedHotseatQsb.subscribeBlocking(scope = viewAttachedScope) { themed ->
            with(searchIcon) {
                isVisible = true

                val iconRes = if (themed) searchProvider.themedIcon else searchProvider.icon
                if (shouldShowIcons) {
                    setThemedIconResource(
                        resId = iconRes,
                        themed = themed || iconRes == R.drawable.ic_qsb_search,
                        method = searchProvider.themingMethod,
                    )
                } else {
                    // Always theme default search icon
                    setThemedIconResource(
                        resId = R.drawable.ic_qsb_search,
                        themed = true,
                        method = ThemingMethod.TINT,
                    )
                }

                setOnClickListener {
                    val launcher = context.launcher
                    launcher.lifecycleScope.launch {
                        searchProvider.launch(launcher)
                    }
                }
            }
            with(micIcon) {
                setIcon(isGoogle, themed)
                setOnClickListener {
                    context.startActivity(voiceIntent)
                }
            }
            with(lensIcon) {
                if (lensIntent != null) {
                    setThemedIconResource(R.drawable.ic_lens_color, themed)
                    setOnClickListener {
                        runCatching { context.startActivity(lensIntent) }
                    }
                }
            }
        }

        if (prefs.searchResulRecentSuggestion.get()) {
            input.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val query = editText.text.toString()
                    suggestionsRecent.saveRecentQuery(query, null)
                }
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
            },
        )

        val hide = PreferenceManager2.getInstance(context).hideAppDrawerSearchBar.firstBlocking()
        if (hide) {
            isInvisible = true
            layoutParams.height = 0
        }
    }

    override fun setFocusedResultTitle(title: CharSequence?, sub: CharSequence?, showArrow: Boolean) {
        focusedResultTitle = title?.toString().orEmpty()
        updateHint()
    }

    override fun refreshResults() {
        onAppsUpdated()
    }

    private fun updateHint() {
        val inputString = input.text.toString()
        val inputLowerCase = inputString.lowercase(Locale.getDefault())
        val focusedLowerCase = focusedResultTitle.lowercase(Locale.getDefault())
        if (canShowHint &&
            inputLowerCase.isNotEmpty() && focusedLowerCase.isNotEmpty() &&
            focusedLowerCase.matches(Regex("^[\\x00-\\x7F]*$")) &&
            focusedLowerCase.startsWith(inputLowerCase)
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
        appsView.appsStore?.addUpdateListener(this)
        input.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        appsView.appsStore?.removeUpdateListener(this)
        input.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onAppsUpdated() {
        searchBarController.refreshSearchResult()
    }

    override fun initializeSearch(appsView: ActivityAllAppsContainerView<*>) {
        apps = appsView.searchResultList as LawnchairAlphabeticalAppsList<*>
        this.appsView = appsView
        searchBarController.initialize(
            LawnchairSearchAlgorithm.create(context),
            input,
            launcher,
            this,
        )
        input.initialize(appsView)
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

    override fun onSearchResult(query: String, items: ArrayList<AdapterItem>?) {
        if (items != null) {
            apps.setSearchResults(items)
            notifyResultChanged()
            appsView.setSearchResults(items)
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
        appsView.floatingHeaderView?.setFloatingRowsCollapsed(false)
    }

    private fun notifyResultChanged() {
        appsView.mSearchRecyclerView.onSearchResultsChanged()
    }

    override fun setInsets(insets: Rect) {
        val lp = layoutParams as MarginLayoutParams
        if (isInvisible) {
            lp.topMargin = insets.top - allAppsSearchVerticalOffset
            return
        }
        lp.topMargin = max(-allAppsSearchVerticalOffset, insets.top - qsbMarginTopAdjusting)

        val dp = launcher.deviceProfile
        val horizontalPadding = dp.desiredWorkspaceHorizontalMarginPx + dp.desiredWorkspaceHorizontalMarginPx
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
