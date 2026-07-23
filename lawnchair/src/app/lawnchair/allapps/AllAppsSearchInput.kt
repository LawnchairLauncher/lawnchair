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
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.firstCached
import app.lawnchair.qsb.LawnQsbLayout.Companion.getLensIntent
import app.lawnchair.qsb.LawnQsbLayout.Companion.getSearchProvider
import app.lawnchair.qsb.LawnQsbLayout.Companion.getVoiceIntent
import app.lawnchair.qsb.LawnQsbUi
import app.lawnchair.qsb.QsbActions
import app.lawnchair.qsb.QsbIconId
import app.lawnchair.qsb.buildQsbStyle
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.rememberAllAppsQsbState
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.ProvideLifecycleState
import com.android.launcher3.Insettable
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
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
import com.android.systemui.shared.system.BlurUtils
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.launch

class AllAppsSearchInput(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs),
    Insettable,
    OnIDPChangeListener,
    SearchUiManager,
    SearchCallback<AdapterItem>,
    AllAppsStore.OnUpdateListener,
    ViewTreeObserver.OnGlobalLayoutListener {

    private lateinit var hint: TextView
    private lateinit var input: FallbackSearchInputView
    private lateinit var qsbShell: ComposeView

    private val qsbMarginTopAdjusting = resources.getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting)
    private val allAppsSearchVerticalOffset = resources.getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset)

    private val launcher = context.launcher
    private val searchBarController = AllAppsSearchBarController()
    private val searchQueryBuilder = SpannableStringBuilder().apply {
        Selection.setSelection(this, 0)
    }

    private lateinit var apps: LawnchairAlphabeticalAppsList<*>
    private lateinit var appsView: ActivityAllAppsContainerView<*>
    private var searchAlgorithm: LawnchairSearchAlgorithm? = null

    private var isDirectFocus = false
    private var focusedResultTitle = ""
    private var canShowHint = false
    private var queryEmpty by mutableStateOf(true)

    private var bgAlphaState by mutableFloatStateOf(1f)
    private val supportBlur = BlurUtils.supportsBlursOnWindows()
    private val bgAlphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
    }
    private var bgVisible = true
    private var bgAlpha = 1f
    private val suggestionsRecent = SearchRecentSuggestions(launcher, LawnchairRecentSuggestionProvider.AUTHORITY, LawnchairRecentSuggestionProvider.MODE)
    private val prefs = PreferenceManager.getInstance(launcher)
    private val prefs2 = PreferenceManager2.getInstance(launcher)

    private var initialPaddingLeft: Int = 0
    private var initialPaddingRight: Int = 0
    private var hideSearchBar = false

    override fun onFinishInflate() {
        super.onFinishInflate()

        setupPadding()
        bgAlphaAnimator.addUpdateListener { updateBgAlpha() }

        hint = ViewCompat.requireViewById(this, R.id.hint)

        input = ViewCompat.requireViewById(this, R.id.input)

        qsbShell = ViewCompat.requireViewById(this, R.id.qsb_shell)

        qsbShell.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            setContent {
                var isFocused by remember(input) { mutableStateOf(input.hasFocus()) }

                // Yes, this is a bit hacky, but it's the only way to ensure that
                // we can check if the input has focus in Compose without wrestling
                // with multiple global variables or state changes
                DisposableEffect(input) {
                    val focusListener = OnGlobalFocusChangeListener { _, _ ->
                        isFocused = input.hasFocus()
                    }

                    val observer = input.viewTreeObserver
                    observer.addOnGlobalFocusChangeListener(focusListener)

                    onDispose {
                        if (observer.isAlive) {
                            observer.removeOnGlobalFocusChangeListener(focusListener)
                        }
                    }
                }

                val searchProviderPref by prefs2.hotseatQsbProvider.asState()
                val searchProvider = remember(searchProviderPref, context) {
                    getSearchProvider(context, searchProviderPref)
                }
                val themedQsb by prefs2.themedHotseatQsb.asState()
                val shouldShowIcons by prefs2.matchHotseatQsbStyle.asState()

                val supportsLens = searchProvider == Google || searchProvider == PixelSearch
                val voiceIntent = remember(searchProvider, context) {
                    getVoiceIntent(searchProvider, context)
                }
                val lensIntent = remember(supportsLens, context) {
                    if (supportsLens) getLensIntent(context) else null
                }

                val state = rememberAllAppsQsbState(
                    searchProvider = searchProvider,
                    themed = themedQsb,
                    shouldShowIcons = shouldShowIcons,
                    queryEmpty = queryEmpty,
                    showMic = voiceIntent != null,
                    showLens = lensIntent != null,
                )

                val backgroundColor = if (supportBlur) {
                    ColorTokens.SearchboxHighlightBlur.resolveColor(context)
                } else {
                    ColorTokens.SearchboxHighlight.resolveColor(context)
                }

                val backgroundAlpha by animateIntAsState(
                    if (isFocused || !queryEmpty) 0 else 100,
                )

                // Ignore other theme attributes to preserve existing behavior
                val style = buildQsbStyle(
                    context = context,
                    themed = themedQsb,
                    backgroundColor = backgroundColor,
                    backgroundAlpha = backgroundAlpha,
                    cornerRadius = 1f,
                    strokeColor = null,
                    strokeWidth = 0f,
                )

                val actions = QsbActions(
                    onQsbClick = {
                        if (input.text.isNullOrEmpty()) {
                            searchAlgorithm?.doZeroStateSearch(this@AllAppsSearchInput)
                        }
                        input.requestFocus()
                        input.showKeyboard()
                    },
                    onStartIconClick = if (shouldShowIcons) {
                        {
                            val launcher = context.launcher
                            launcher.lifecycleScope.launch {
                                searchProvider.launch(launcher)
                            }
                        }
                    } else {
                        null
                    },
                    onEndIconClick = { id ->
                        when (id) {
                            QsbIconId.MIC -> voiceIntent?.let { context.startActivity(it) }

                            QsbIconId.LENS -> lensIntent?.let { context.startActivity(it) }

                            QsbIconId.CLEAR -> {
                                input.reset()
                                searchAlgorithm?.doZeroStateSearch(this@AllAppsSearchInput)
                                updateHint()
                            }

                            else -> Unit
                        }
                    },
                )

                LawnchairTheme {
                    ProvideLifecycleState {
                        LawnQsbUi(
                            state = state,
                            style = style,
                            actions = actions,
                        )
                    }
                }
            }
        }

        val currentPaddingLeft = initialPaddingLeft
        val currentPaddingRight = initialPaddingRight

        // Activate zero search on click
        input.setOnClickListener {
            if (input.text.isNullOrEmpty()) {
                searchAlgorithm?.doZeroStateSearch(this)
            }
        }

        input.onFocusChangeListener = { _, hasFocus ->
            if (hasFocus) {
                if (prefs2.searchAlgorithm.firstCached() != LawnchairSearchAlgorithm.APP_SEARCH) {
                    input.setHint(R.string.all_apps_device_search_hint)
                } else {
                    input.setHint(R.string.all_apps_search_bar_hint)
                }

                if (input.text.toString().isEmpty() && isDirectFocus) {
                    searchAlgorithm?.doZeroStateSearch(this)
                    setDirectFocus(false)
                }

                setBackgroundVisibility(false, 0f)
                animateHintVisibility(true)
                animatePadding(currentPaddingLeft / 2, currentPaddingRight / 2)
            } else {
                setBackgroundVisibility(true, 1f)
                animateHintVisibility(false)
                if (prefs.searchResulRecentSuggestion.get()) {
                    val query = editText.text.toString()
                    suggestionsRecent.saveRecentQuery(query, null)
                }

                if (input.text.isNullOrEmpty()) {
                    animatePadding(currentPaddingLeft, currentPaddingRight)
                }
                focusedResultTitle = ""
                input.setHint("")
                hint.text = ""
            }

            if (::appsView.isInitialized) {
                appsView.mSearchRecyclerView.invalidate()
            }
        }

        input.addTextChangedListener(
            beforeTextChanged = { _, _, _, _ ->
                hint.isInvisible = true
            },
            afterTextChanged = {
                updateHint()
                if (input.text.isNullOrEmpty() && input.hasFocus() && !input.isResetting) {
                    searchAlgorithm?.doZeroStateSearch(this)
                }
                if (input.text.toString() == "/lawnchairdebug") {
                    val enableDebugMenu = prefs.enableDebugMenu
                    enableDebugMenu.set(!enableDebugMenu.get())
                    launcher.stateManager.goToState(LauncherState.NORMAL)
                }

                queryEmpty = it.isNullOrEmpty()
            },
        )

        hideSearchBar = prefs2.hideAppDrawerSearchBar.firstCached()
        if (hideSearchBar) {
            // GONE so top margin/height do not reserve empty space above the app list.
            isGone = true
            layoutParams.height = 0
        }
    }

    private fun setupPadding() {
        launcher.deviceProfile.let { dp ->
            val padding = dp.getAllAppsIconStartMargin(context)
            initialPaddingLeft = padding
            initialPaddingRight = padding
            setPadding(padding, paddingTop, padding, paddingBottom)
        }
    }

    private fun animateHintVisibility(visible: Boolean) {
        val targetAlpha = if (visible) 1f else 0f
        val duration = if (visible) 300L else 200L

        if (visible) {
            hint.alpha = 0f
            hint.isVisible = true
        }

        hint.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                if (!visible) hint.isVisible = false
            }
            .start()
    }

    private fun animatePadding(newPaddingLeft: Int, newPaddingRight: Int) {
        val currentPaddingLeft = paddingLeft
        val currentPaddingRight = paddingRight

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val leftPadding = currentPaddingLeft + (newPaddingLeft - currentPaddingLeft) * fraction
                val rightPadding = currentPaddingRight + (newPaddingRight - currentPaddingRight) * fraction
                setPadding(leftPadding.toInt(), paddingTop, rightPadding.toInt(), paddingBottom)
            }
            start()
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
            inputLowerCase.isNotEmpty() &&
            focusedLowerCase.isNotEmpty() &&
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
        launcher.deviceProfile.inv.addOnChangeListener(this)
        if (::appsView.isInitialized) {
            appsView.appsStore?.addUpdateListener(this)
        }
        input.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        launcher.deviceProfile.inv.removeOnChangeListener(this)
        if (::appsView.isInitialized) {
            appsView.appsStore?.removeUpdateListener(this)
        }
        input.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onAppsUpdated() {
        searchBarController.refreshSearchResult()
    }

    override fun initializeSearch(appsView: ActivityAllAppsContainerView<*>) {
        apps = appsView.searchResultList as LawnchairAlphabeticalAppsList<*>
        this.appsView = appsView
        val algorithm = LawnchairSearchAlgorithm.create(context)
        this.searchAlgorithm = algorithm
        searchBarController.initialize(
            algorithm,
            input,
            launcher,
            this,
        )
        input.initialize(appsView)
    }

    override fun resetSearch() {
        searchBarController.reset()
    }

    override fun setDirectFocus(directFocus: Boolean) {
        isDirectFocus = directFocus
    }

    override fun preDispatchKeyEvent(event: KeyEvent) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!searchBarController.isSearchFieldFocused && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                !Character.isWhitespace(unicodeChar) &&
                !Character.isSpaceChar(unicodeChar)
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
        (layoutParams as MarginLayoutParams).apply {
            topMargin = when {
                hideSearchBar -> 0

                // Sheet mode already pads the container with status-bar insets; only clear the
                // drag handle. Re-applying insets.top here created the large empty band under it.
                launcher.deviceProfile.shouldShowAllAppsOnSheet() ->
                    resources.getDimensionPixelSize(R.dimen.bottom_sheet_handle_area_height)

                else -> max(-allAppsSearchVerticalOffset, insets.top - qsbMarginTopAdjusting)
            }
        }
        requestLayout()
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
        bgAlphaState = Utilities.mapRange(fraction, 0f, bgAlpha)
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        setupPadding()
        invalidate()
        requestLayout()
    }
}
