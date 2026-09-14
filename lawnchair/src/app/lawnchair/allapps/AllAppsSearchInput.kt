package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.Crossfade
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
import app.lawnchair.ui.liquid.LiquidGlassPanel
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.DRAWER_GLASS_WASH
import app.lawnchair.util.ProvideLifecycleState
import app.lawnchair.views.LawnchairScrimView
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
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.SpringRelativeLayout
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

    /**
     * The glass capsule the search bar sits on.
     *
     * It lives in the drag layer rather than in this view so the image it
     * refracts can stay fixed to the screen while the drawer slides across it.
     * Moving the glass with the bar would drag its reflection along, which is
     * precisely what stops a still image reading as glass.
     *
     * It is slotted in directly above the scrim: above, so it has the frosted
     * home screen to refract; below the drawer, so the label and the input draw
     * on top of it rather than behind it.
     */
    private var searchGlass: LiquidGlassPanel? = null

    /**
     * Whether the capsule below is the search bar's background.
     *
     * The bar had two backgrounds and only one of them was glass. The Compose
     * shell paints its own capsule from [ColorTokens.SearchboxHighlight] at
     * alpha 100, and that capsule is inside the drawer, which the drag layer
     * draws after the pane -- so the flat fill landed on top of the glass and
     * the lens, the rim and the refraction underneath it were simply not
     * visible. The one moment they showed was a dark/light switch, when the
     * shell is disposed and rebuilt and the fill is briefly absent.
     *
     * State rather than a read of [searchGlass], because the shell has to
     * recompose when it changes and a plain field would not tell it to.
     */
    private var searchGlassActive by mutableStateOf(false)
    private var searchGlassScene: android.graphics.Bitmap? = null
    private var searchGlassPending = false
    private val glassPaneLocation = IntArray(2)
    private val glassOverlayLocation = IntArray(2)

    private val glassPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        updateSearchGlass()
        true
    }

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
                    if (isFocused || !queryEmpty || searchGlassActive) 0 else 100,
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
                        // Two different things share this bar: a label naming what
                        // is below it, and a search field. Crossfading rather than
                        // swapping keeps the changeover from reading as a flicker
                        // as the keyboard comes up.
                        Crossfade(
                            targetState = !isFocused && queryEmpty,
                            label = "sora_search_bar",
                        ) { atRest ->
                            if (atRest) {
                                SoraAppLibraryLabel(onClick = actions.onQsbClick)
                            } else {
                                LawnQsbUi(
                                    state = state,
                                    style = style,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Stop Compose QSB from disappearing
        // https://stackoverflow.com/questions/72781705/jetpack-compose-view-not-drawing-when-coming-back-to-fragment/77496737#77496737
        qsbShell.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                requestLayout()
                qsbShell.disposeComposition()
            }
            override fun onViewDetachedFromWindow(v: View) {
                qsbShell.disposeComposition()
            }
        })

        val currentPaddingLeft = initialPaddingLeft
        val currentPaddingRight = initialPaddingRight

        // Activate zero search on tap
        @SuppressLint("ClickableViewAccessibility")
        input.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && !input.hasFocus()) {
                setDirectFocus(true)
            }
            false
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

                val isEmpty = it.isNullOrEmpty()
                if (isEmpty && !input.hasFocus()) {
                    animatePadding(currentPaddingLeft, currentPaddingRight)
                }
                queryEmpty = isEmpty
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
        viewTreeObserver.addOnPreDrawListener(glassPreDrawListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        launcher.deviceProfile.inv.removeOnChangeListener(this)
        if (::appsView.isInitialized) {
            appsView.appsStore?.removeUpdateListener(this)
        }
        input.viewTreeObserver.removeOnGlobalLayoutListener(this)
        viewTreeObserver.removeOnPreDrawListener(glassPreDrawListener)
        releaseSearchGlass()
        setDirectFocus(false)
    }

    /**
     * How far the drawer has travelled: 0 on the workspace, 1 fully open.
     *
     * The glass has nothing to show until the drawer is on its way, because the
     * frosted backdrop it refracts is not being drawn before then.
     */
    private fun drawerOpenness(): Float {
        if (launcher.isMergeAppDrawerToWorkspace()) {
            val scrim = launcher.scrimView as? LawnchairScrimView
            return scrim?.drawerOpenness() ?: 0f
        }
        val controller = launcher.allAppsController ?: return 0f
        return (1f - controller.progress).coerceIn(0f, 1f)
    }

    /** Creates or tears down the glass as the drawer comes and goes. */
    private fun updateSearchGlass() {
        if (hideSearchBar || drawerOpenness() <= 0f) {
            releaseSearchGlass()
            return
        }

        val panel = searchGlass ?: run {
            // Never created straight from here: this runs in the pre-draw pass,
            // and adding a view to the drag layer there forces another layout
            // before the frame is drawn -- which can reach a drawer
            // RecyclerView that is not ready to be laid out. Queued instead, and
            // picked up on the next frame.
            if (!searchGlassPending) {
                searchGlassPending = true
                post {
                    searchGlassPending = false
                    if (isAttachedToWindow && drawerOpenness() > 0f) createSearchGlass()
                }
            }
            return
        }

        // The same frosted scene the drawer itself is drawn on. The pill is a
        // pane set into that surface, not a window cut through it, so what shows
        // inside has to look like what surrounds it -- the glass comes from the
        // lens bending it at the rim and the light running along the edge, not
        // from showing something different.
        //
        // Never swapped for null. The wallpaper cache is dropped whenever the
        // wallpaper is replaced -- which a dark/light switch also reports, with
        // the wallpaper unchanged -- and a panel with no scene falls back to a
        // flat colour of its own, which is what turned the pill into a solid
        // grey capsule the moment the theme was switched.
        val scene = (launcher.scrimView as? LawnchairScrimView)?.drawerBackdrop
        if (scene != null && scene !== searchGlassScene) {
            searchGlassScene = scene
            // Mapped onto the screen, not onto the drag layer.
            //
            // The drag layer is inset by the system bars, so laying the scene
            // into its rect stretched the same bitmap over a shorter box than
            // the folder tiles lay it over -- and the pill ended up showing a
            // different part of the wallpaper from everything around it. Both
            // now state the screen, so both sample the same pixel at the same
            // place.
            val display = context.resources.displayMetrics
            panel.setScene(scene, 0, 0, display.widthPixels, display.heightPixels)
        }
    }

    private fun createSearchGlass(): LiquidGlassPanel? {
        val dragLayer = launcher.dragLayer ?: return null
        val panel = LiquidGlassPanel(context)
        // BaseDragLayer accepts only its own LayoutParams. Handed the
        // InsettableFrameLayout ones it inherits from, it quietly converts them,
        // and the conversion drops ignoreInsets -- after which onViewAdded lays
        // the system-bar inset on as a margin. The overlay then sits lower and
        // shorter than the layer it is addressed in, and what it draws near the
        // top falls outside its own bounds.
        val params = BaseDragLayer.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        params.ignoreInsets = true
        panel.layoutParams = params
        panel.touchThrough = true
        // The wash the folder tiles wear, so the pill and the tiles below it are
        // finally the one material. Without it the pill kept the full stretch
        // the pane's vibrancy and lens put on the scene, and sat there as a warm
        // copper capsule in a drawer that is mostly grey.
        panel.setTintColor(DRAWER_GLASS_WASH)
        panel.onSyncFrame = Runnable { syncSearchGlass() }

        // Handed its scene before it is added, the way a folder tile's pane is.
        //
        // Not a tidiness point. A panel with no scene of its own draws the bare
        // wallpaper over a rectangle of nothing, which is the sharp, misplaced
        // image the pill was showing; the frosted backdrop was being set a frame
        // later, on the first pre-draw, and by then the pane had been recorded
        // into a graphics layer that nothing was asking to record again. Setting
        // it first means the very first composition already has the right scene.
        val frosted = (launcher.scrimView as? LawnchairScrimView)?.drawerBackdrop
        if (frosted != null) {
            val display = context.resources.displayMetrics
            panel.setScene(frosted, 0, 0, display.widthPixels, display.heightPixels)
            searchGlassScene = frosted
        }

        // Straight above the scrim, so the home screen and workspace icons are
        // behind the glass and the drawer -- search bar included -- is in front of it.
        val scrimView = launcher.scrimView
        val index = dragLayer.indexOfChild(scrimView)
        dragLayer.addView(panel, if (index >= 0) index + 1 else -1)

        searchGlass = panel
        searchGlassActive = true
        syncSearchGlass()
        return panel
    }

    private fun releaseSearchGlass() {
        val panel = searchGlass ?: return
        searchGlass = null
        searchGlassScene = null
        searchGlassActive = false
        panel.onSyncFrame = null
        panel.visibility = GONE

        // Posted rather than done here: this can run while the hierarchy is
        // being torn down, and removing a view from a parent that is midway
        // through walking its own children leaves a hole in the array it is
        // iterating over.
        panel.post {
            (panel.parent as? ViewGroup)?.removeView(panel)
        }
    }

    /**
     * Puts the capsule where the search bar currently is, once per frame.
     *
     * The bar travels with the drawer, so this is the only part that moves: the
     * scene behind stays pinned to the screen and the capsule slides over it.
     */
    private fun syncSearchGlass() {
        val panel = searchGlass ?: return
        val openness = drawerOpenness()
        val height = qsbShell.height.toFloat()
        if (openness <= 0f || height <= 0f) {
            panel.visibility = GONE
            return
        }

        panel.visibility = VISIBLE
        panel.screenLocation(glassOverlayLocation)
        qsbShell.getLocationOnScreen(glassPaneLocation)

        val overScrollY = (launcher.appsView as? SpringRelativeLayout)?.overScrollShift ?: 0

        panel.setCornerRadius(height / 2f)
        // No tint, the same as the folder tiles beside it. The pill refracts the
        // frosted home screen and so does the drawer around it; there is no flat
        // colour between the two for the pill to wear. What separates it from
        // its surroundings is the lens bending the image at the rim and the
        // light running along the edge, not a different shade.
        panel.setTinted(false)
        panel.setPaneBounds(
            (glassPaneLocation[0] - glassOverlayLocation[0]).toFloat(),
            (glassPaneLocation[1] - glassOverlayLocation[1] + overScrollY).toFloat(),
            qsbShell.width.toFloat(),
            height,
            openness,
        )
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
                hideSearchBar || launcher.isMergeAppDrawerToWorkspace() -> 0

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
