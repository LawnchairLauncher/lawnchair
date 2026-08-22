/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair

import android.animation.AnimatorSet
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Pair
import android.view.Display
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.window.SplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairApp.Companion.showQuickstepWarningIfNecessary
import app.lawnchair.compat.LawnchairQuickstepCompat
import app.lawnchair.data.AppDatabase
import app.lawnchair.allapps.AllAppsPagedGridView
import app.lawnchair.data.wallpaper.service.WallpaperService
import app.lawnchair.drivingmode.DrivingModeController
import app.lawnchair.gestures.GestureController
import app.lawnchair.gestures.VerticalSwipeTouchController
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.ui.LawnchairShortcutActivity
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.LawnchairShortcut
import app.lawnchair.util.DrawerBackgroundImageStore
import app.lawnchair.util.applyRecentsExclusion
import app.lawnchair.util.getThemedIconPacksInstalled
import app.lawnchair.util.unsafeLazy
import app.lawnchair.views.LawnchairFloatingSurfaceView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.GestureNavContract
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.PredictedContainerInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StateManager.StateHandler
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.uioverrides.states.AllAppsState
import com.android.launcher3.uioverrides.states.BackgroundAppState
import com.android.launcher3.uioverrides.states.OverviewState
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW
import com.android.launcher3.util.Themes
import com.android.launcher3.util.TouchController
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.RoundedCornerEnforcement
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.shared.system.QuickStepContract
import com.kieronquinn.app.smartspacer.sdk.client.SmartspacerClient
import com.patrykmichalik.opto.core.onEach
import dev.kdrag0n.monet.theme.ColorScheme
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LawnchairLauncher : QuickstepLauncher() {
    private val defaultOverlay by unsafeLazy { OverlayCallbackImpl(this) }
    private val prefs by unsafeLazy { PreferenceManager.getInstance(this) }
    private val preferenceManager2 by unsafeLazy { PreferenceManager2.getInstance(this) }
    private val insetsController: WindowInsetsControllerCompat by lazy {
        val window = launcher.window
            ?: throw Exception("WindowInsetsControllerCompat not available.")
        WindowInsetsControllerCompat(window, rootView)
    }
    private val themeProvider by unsafeLazy { ThemeProvider.INSTANCE.get(this) }
    private val appDrawerWallpaperBackgroundView by unsafeLazy {
        findViewById<ImageView>(R.id.app_drawer_wallpaper_background)
    }
    private val noStatusBarStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState is OverviewState) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState !is OverviewState) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    private val rememberPositionStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState is AllAppsState) {
                mAppsView.activeRecyclerView.restoreScrollPosition()
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {}
    }
    private val statusBarClockListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            when (toState) {
                is BackgroundAppState,
                is OverviewState,
                is AllAppsState,
                -> {
                    LawnchairApp.instance.restoreClockInStatusBar()
                }

                else -> {
                    workspace.updateStatusbarClock()
                }
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {}
    }
    private val clearSearchStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState == LauncherState.NORMAL && mAppsView != null && mAppsView.isSearching) {
                mAppsView?.post {
                    mAppsView.reset(false, true)
                }
            }
        }
    }

    // Mirrors rememberPositionStateListener but for the paged drawer: when
    // "Remember scroll position" is off, jump back to the first page every
    // time the drawer opens, same as the normal drawer resets to top.
    private val pagedDrawerResetStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState is AllAppsState) {
                // Self-heals a cold-start race: the paged grid's first refresh can run before
                // the model finishes loading apps (most noticeable right after a fresh
                // install), leaving it empty until something else happens to refresh it. This
                // is a cheap no-op otherwise (also no-ops when paged mode isn't active).
                mAppsView?.refreshPagedGridView()
                if (!preferenceManager2.rememberPosition.firstCached()) {
                    mAppsView?.findViewById<AllAppsPagedGridView>(R.id.apps_paged_grid_view)?.setCurrentPage(0)
                }
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState !is AllAppsState) {
                // Closing the app drawer is exactly the point where a "Clear all" in Recents can
                // leave this task's exclude-from-recents state reset - reapply defensively.
                applyRecentsExclusion(this@LawnchairLauncher, preferenceManager2.hideLawnchairActivities.firstCached())
            }
        }
    }

    // How much wider than the screen the background image is scaled, reserving room to pan -
    // matches the subtle parallax range typical of a home screen wallpaper (not a literal
    // separate crop per drawer page).
    private val backgroundParallaxWidthFactor = 1.2f

    /**
     * Positions the (screen-sized) background ImageView's MATRIX-scaled bitmap so it covers the
     * view while reserving [backgroundParallaxWidthFactor] extra width to pan across, then shifts
     * it horizontally by [progress] (0..1). Centered vertically like a normal centerCrop.
     */
    private fun updateDrawerBackgroundMatrix(bitmap: Bitmap, progress: Float) {
        // The view's own measured size, not the display's - they differ in split-screen/
        // multi-window, where using the full display size would over-scale and mis-pan the
        // image relative to the actually-visible (smaller) view.
        val view = appDrawerWallpaperBackgroundView
        val viewWidth = (if (view.width > 0) view.width else resources.displayMetrics.widthPixels).toFloat()
        val viewHeight = (if (view.height > 0) view.height else resources.displayMetrics.heightPixels).toFloat()
        val scale = maxOf(viewHeight / bitmap.height, (viewWidth * backgroundParallaxWidthFactor) / bitmap.width)
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(-progress * (scaledWidth - viewWidth), -(scaledHeight - viewHeight) / 2f)
        }
        appDrawerWallpaperBackgroundView.imageMatrix = matrix
    }

    // Shows the user's chosen background image behind the drawer's scrim. The workspace sits
    // directly behind scrim_view in the layout, so a translucent scrim alone would reveal the
    // home screen through the drawer rather than a clean image - this layer occludes the
    // workspace while showing the picked image instead. Reading the live wallpaper directly via
    // WallpaperManager was tried first but proved unreliable (silently returns null on this
    // device/Android version with no exception to catch), hence storing our own copy.
    //
    // In paged drawer mode, the image pans horizontally as the user swipes between pages,
    // mirroring how the home screen parallaxes the system wallpaper across workspace pages.
    // Filename this bitmap was decoded from, so a later call can tell whether the preference
    // still points at the same file (reuse the decode) or a different/cleared one (reload).
    private var cachedBackgroundImage: kotlin.Pair<String, Bitmap>? = null

    // Tracks the in-flight decode below so it can be cancelled if the user leaves All Apps (or
    // the background image preference changes) before it finishes - otherwise a slow decode can
    // land after the fact and show a stale/wrong image.
    private var backgroundLoadJob: Job? = null

    private val drawerBackgroundImageStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState !is AllAppsState) return
            val fileName = preferenceManager2.appDrawerBackgroundImage.firstCached()
            if (fileName.isEmpty()) {
                // The user removed their background image since it was last shown - without
                // this, a previously-shown bitmap would stay visible/stale indefinitely.
                backgroundLoadJob?.cancel()
                hideDrawerBackground()
                return
            }
            val cached = cachedBackgroundImage
            if (cached != null && cached.first == fileName) {
                backgroundLoadJob?.cancel()
                showDrawerBackground(cached.second)
                return
            }
            // BitmapFactory.decodeFile is a synchronous disk read + decode - too slow to run on
            // the main thread on every AllApps transition, and unnecessary to repeat at all once
            // cached above for as long as the preference keeps pointing at the same file.
            backgroundLoadJob?.cancel()
            backgroundLoadJob = lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    DrawerBackgroundImageStore.loadBitmap(this@LawnchairLauncher, fileName)
                }
                // The state may have moved on, or the preference may point elsewhere, while this
                // decode was in flight - applying it now would show the wrong image.
                if (launcher.stateManager.state !is AllAppsState ||
                    preferenceManager2.appDrawerBackgroundImage.firstCached() != fileName
                ) {
                    return@launch
                }
                if (bitmap == null) {
                    hideDrawerBackground()
                    return@launch
                }
                cachedBackgroundImage = fileName to bitmap
                showDrawerBackground(bitmap)
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState !is AllAppsState) {
                backgroundLoadJob?.cancel()
                hideDrawerBackground()
            }
        }
    }

    private fun hideDrawerBackground() {
        appDrawerWallpaperBackgroundView.visibility = View.GONE
        appDrawerWallpaperBackgroundView.setImageBitmap(null)
        mAppsView?.findViewById<AllAppsPagedGridView>(R.id.apps_paged_grid_view)?.onScrollProgressChanged = null
    }

    private fun showDrawerBackground(bitmap: Bitmap) {
        val pagedGridView = mAppsView?.findViewById<AllAppsPagedGridView>(R.id.apps_paged_grid_view)
        if (pagedGridView != null) {
            appDrawerWallpaperBackgroundView.scaleType = ImageView.ScaleType.MATRIX
            updateDrawerBackgroundMatrix(bitmap, pagedGridView.currentScrollProgress())
            pagedGridView.onScrollProgressChanged = { progress -> updateDrawerBackgroundMatrix(bitmap, progress) }
        } else {
            appDrawerWallpaperBackgroundView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        appDrawerWallpaperBackgroundView.setImageBitmap(bitmap)
        appDrawerWallpaperBackgroundView.visibility = View.VISIBLE
    }

    private lateinit var colorScheme: ColorScheme
    private var hasBackGesture = false

    val gestureController by unsafeLazy { GestureController(this) }
    private val drivingModeController by unsafeLazy { DrivingModeController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        layoutInflater.factory2 = LawnchairLayoutFactory(this)
        super.onCreate(savedInstanceState)

        drivingModeController.start()
        if (intent?.getBooleanExtra(EXTRA_START_DRIVING_MODE, false) == true) {
            drivingModeController.show()
        }

        // A freshly created task doesn't inherit exclude-from-recents state from a previous
        // session - subscribe (rather than reading the cache once) since the in-memory
        // preference cache isn't guaranteed to be populated from disk yet this early in a cold
        // start, and firstCached() would silently fall back to the compile-time default.
        preferenceManager2.hideLawnchairActivities.get().distinctUntilChanged().onEach { exclude ->
            applyRecentsExclusion(this, exclude)
        }.launchIn(scope = lifecycleScope)

        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)
        prefs.feedProvider.subscribeChanges(this, defaultOverlay::reconnect)
        preferenceManager2.enableFeed.get().distinctUntilChanged().onEach { enable ->
            defaultOverlay.setEnableFeed(enable)
        }.launchIn(scope = lifecycleScope)
        launcher.stateManager.addStateListener(clearSearchStateListener)
        launcher.stateManager.addStateListener(pagedDrawerResetStateListener)
        launcher.stateManager.addStateListener(drawerBackgroundImageStateListener)

        if (prefs.autoLaunchRoot.get()) {
            lifecycleScope.launch {
                try {
                    RootHelperManager.INSTANCE.get(this@LawnchairLauncher)
                } catch (_: RootNotAvailableException) {
                }
            }
        }

        preferenceManager2.showStatusBar.get().distinctUntilChanged().onEach {
            with(insetsController) {
                if (it) {
                    show(WindowInsetsCompat.Type.statusBars())
                } else {
                    hide(WindowInsetsCompat.Type.statusBars())
                }
            }
            with(launcher.stateManager) {
                if (it) {
                    removeStateListener(noStatusBarStateListener)
                } else {
                    addStateListener(noStatusBarStateListener)
                }
            }
        }.launchIn(scope = lifecycleScope)

        preferenceManager2.statusBarClock.get().onEach {
            with(launcher.stateManager) {
                if (it) {
                    addStateListener(statusBarClockListener)
                } else {
                    removeStateListener(statusBarClockListener)
                    // Make sure status bar clock is restored when the preference is toggled off
                    LawnchairApp.instance.restoreClockInStatusBar()
                }
            }
        }
        preferenceManager2.rememberPosition.get().onEach {
            with(launcher.stateManager) {
                if (it) {
                    addStateListener(rememberPositionStateListener)
                } else {
                    removeStateListener(rememberPositionStateListener)
                }
            }
        }.launchIn(scope = lifecycleScope)

        prefs.overrideWindowCornerRadius.subscribeValues(this) {
            QuickStepContract.sHasCustomCornerRadius = it
        }
        prefs.windowCornerRadius.subscribeValues(this) {
            QuickStepContract.sCustomCornerRadius = it.toFloat()
        }
        preferenceManager2.roundedWidgets.onEach(launchIn = lifecycleScope) {
            RoundedCornerEnforcement.sUseSystemRadius = it
        }
        preferenceManager2.customRoundedWidgetsRadius.onEach(launchIn = lifecycleScope) {
            RoundedCornerEnforcement.sCustomRadiusDp = it.toFloat()
        }
        val isWorkspaceDarkText = Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText)
        preferenceManager2.darkStatusBar.onEach(launchIn = lifecycleScope) { darkStatusBar ->
            systemUiController?.updateUiState(UI_STATE_BASE_WINDOW, isWorkspaceDarkText || darkStatusBar)
        }
        preferenceManager2.backPressGestureHandler.onEach(launchIn = lifecycleScope) { handler ->
            hasBackGesture = handler !is GestureHandlerConfig.NoOp
        }

        LauncherOptionsPopup.restoreMissingPopupOptions(launcher)
        LauncherOptionsPopup.migrateLegacyPreferences(launcher)

        // Handle update from version 12 Alpha 4 to version 12 Alpha 5.
        if (
            prefs.themedIcons.get() &&
            packageManager.getThemedIconPacksInstalled(this).isEmpty()
        ) {
            prefs.themedIcons.set(newValue = false)
        }

        colorScheme = themeProvider.colorScheme

        showQuickstepWarningIfNecessary()

        reloadIconsIfNeeded()

        AppDatabase.INSTANCE.get(this).checkpointSync()
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent != null && intent.action == LawnchairShortcutActivity.START_ACTION) {
            val handlerString = intent.getStringExtra(LawnchairShortcutActivity.EXTRA_HANDLER)
            val config = handlerString?.let { GestureHandlerConfig.fromString(it) }
            if (config != null && config.isExternallyInvokable()) {
                gestureController.handle(config)
            }
        }

        // The Home button/gesture re-delivers here as ACTION_MAIN since it's the same activity -
        // while driving mode is up, reset its grid instead of letting Launcher3's own handling
        // below open search (or move the hidden workspace to its default page).
        if (drivingModeController.isShowing && intent?.action == Intent.ACTION_MAIN) {
            drivingModeController.requestGoHome()
            return
        }

        if (intent?.getBooleanExtra(EXTRA_START_DRIVING_MODE, false) == true) {
            drivingModeController.show()
        }

        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rotation reliably leaves the driving-mode overlay's touch dispatch broken (dead taps,
        // no page snapping) afterward - nothing tried in-process (Compose state resets, replaying
        // dragLayer.recreateControllers(), forcing a real pager scroll) has fixed it, only killing
        // and relaunching the whole app has. Handing it a genuinely new ComposeView, the one thing
        // relaunching does that nothing else here can, is the reliable fix.
        if (drivingModeController.isShowing) {
            drivingModeController.recreateOverlayForConfigChange()
        }
    }

    override fun collectStateHandlers(out: MutableList<StateHandler<LauncherState>>) {
        super.collectStateHandlers(out)
        out.add(SearchBarStateHandler(this))
    }

    override fun getAllAppsItemLongClickListener(): View.OnLongClickListener {
        return View.OnLongClickListener { view ->
            if (view is FolderIcon && view.mInfo.id != ItemInfo.NO_ID) {
                LawnchairShortcut.showAppDrawerFolderPopup(this, view)
            } else {
                super.getAllAppsItemLongClickListener().onLongClick(view)
            }
        }
    }

    override fun getSupportedShortcuts(container: Int): Stream<SystemShortcut.Factory<*>> = Stream.concat(
        super.getSupportedShortcuts(container),
        Stream.concat(
            Stream.of(
                LawnchairShortcut.UNINSTALL,
                LawnchairShortcut.CUSTOMIZE,
                LawnchairShortcut.CUSTOMIZE_SHORTCUT,
                LawnchairShortcut.OPEN_IN_STORE,
            ),
            if (LawnchairApp.isRecentsEnabled) Stream.of(LawnchairShortcut.PAUSE_APPS) else Stream.empty(),
        ),
    )

    fun updateTheme() {
        if (themeProvider.colorScheme != colorScheme) {
            recreate()
        } else {
            mWallpaperThemeManager.updateTheme()
        }
    }

    override fun onStateBack() {
        val searchInput = mAppsView?.searchUiManager?.editText
        val isSearching = mAppsView?.isSearching == true || searchInput?.hasFocus() == true
        if (isSearching) {
            mAppsView?.searchUiManager?.resetSearch()
            allAppsController.animateAllAppsToNoScale()
        } else {
            super.onStateBack()
        }
    }

    override fun createTouchControllers(): Array<TouchController> {
        // Suppress all of Launcher3's own swipe gestures (open all apps,
        // recents, etc.) while the driving-mode overlay is showing — a view
        // added on top of dragLayer doesn't stop these on its own, since
        // they're TouchControllers checked before normal child dispatch.
        if (drivingModeController.isShowing) return emptyArray()
        val verticalSwipeController = VerticalSwipeTouchController(this, gestureController)
        return arrayOf<TouchController>(verticalSwipeController) + super.createTouchControllers()
    }

    override fun handleHomeTap() {
        gestureController.onHomePressed()
    }

    fun bindItems(items: List<ItemInfo>, forceAnimateIcons: Boolean) {
        // pE-TODO(QPR1): Note: null is modelWriter + bindItems override something
        val inflatedItems = items.map { i ->
            Pair.create(
                i,
                itemInflater?.inflateItem(
                    i,
                    null,
                ),
            )
        }.toList()
        bindInflatedItems(inflatedItems, if (forceAnimateIcons) AnimatorSet() else null)
    }

    override fun handleGestureContract(intent: Intent) {
        if (!LawnchairApp.isRecentsEnabled && prefs.enableGnc.get()) {
            val gnc = GestureNavContract.fromIntent(intent)
            if (gnc != null) {
                AbstractFloatingView.closeOpenViews(
                    this,
                    false,
                    AbstractFloatingView.TYPE_ICON_SURFACE,
                )
                LawnchairFloatingSurfaceView.show(this, gnc)
            }
        }
    }

    override fun onUiChangedWhileSleeping() {
        if (Utilities.ATLEAST_S) {
            super.onUiChangedWhileSleeping()
        }
    }

    override fun showDefaultOptions(x: Float, y: Float) {
        val showWallpaperCarousel = "+carousel" in preferenceManager2.launcherPopupOrder.firstCached()

        if (showWallpaperCarousel) {
            show<LawnchairLauncher>(
                this,
                getPopupTarget(x, y),
                OptionsPopupView.getOptions(this),
            )
        } else {
            super.showDefaultOptions(x, y)
        }
    }

    private fun <T> show(
        activityContext: ActivityContext?,
        targetRect: RectF,
        items: List<OptionItem>,
        shouldAddArrow: Boolean = false,
        width: Int = 0,
    ): OptionsPopupView<T>? where T : Context?, T : ActivityContext? {
        if (activityContext == null) return null

        val isEmpty = WallpaperService.INSTANCE.get(this).getTopWallpapers().isEmpty()
        val layout = if (isEmpty) R.layout.longpress_options_menu else R.layout.wallpaper_options_popup

        val popup = activityContext.layoutInflater.inflate(layout, activityContext.dragLayer, false) as OptionsPopupView<T>
        popup.setTargetRect(targetRect)
        popup.setShouldAddArrow(shouldAddArrow)

        for (item in items) {
            val deepLayout = if (isEmpty) R.layout.system_shortcut else R.layout.wallpaper_options_popup_item

            val view = popup.inflateAndAdd<DeepShortcutView>(deepLayout, popup)
            if (width > 0) view.layoutParams.width = width
            view.iconView.setBackgroundDrawable(item.icon)
            view.bubbleText.text = item.label
            view.setOnClickListener(popup)
            view.setOnLongClickListener(popup)
            popup.mItemMap[view] = item
        }

        popup.show()
        return popup
    }

    fun createAppWidgetHolder(): LauncherWidgetHolder {
        val holder = LauncherWidgetHolder.newInstance(this)
        holder.setAppWidgetRemovedCallback { appWidgetId ->
            workspace.removeWidget(appWidgetId)
        }
        return holder
    }

    override fun makeDefaultActivityOptions(splashScreenStyle: Int): ActivityOptionsWrapper {
        val callbacks = RunnableList()
        val options = if (Utilities.ATLEAST_Q) {
            LawnchairQuickstepCompat.activityOptionsCompat.makeCustomAnimation(
                this,
                0,
                0,
                Executors.MAIN_EXECUTOR.handler,
                null,
            ) {
                callbacks.executeAllAndDestroy()
            }
        } else {
            ActivityOptions.makeBasic()
        }
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = splashScreenStyle
        }

        Utilities.allowBGLaunch(options)
        return ActivityOptionsWrapper(options, callbacks)
    }

    override fun getActivityLaunchOptions(v: View?, item: ItemInfo?): ActivityOptionsWrapper {
        return runCatching {
            super.getActivityLaunchOptions(v, item)
        }.getOrElse {
            getActivityLaunchOptionsDefault(v)
        }
    }

    private fun getActivityLaunchOptionsDefault(v: View?): ActivityOptionsWrapper {
        var left = 0
        var top = 0
        var width = v!!.measuredWidth
        var height = v.measuredHeight
        if (v is BubbleTextView) {
            // Launch from center of icon, not entire view
            val icon: Drawable? = v.icon
            if (icon != null) {
                val bounds = icon.bounds
                left = (width - bounds.width()) / 2
                top = v.paddingTop
                width = bounds.width()
                height = bounds.height()
            }
        }
        val options = Utilities.allowBGLaunch(
            ActivityOptions.makeClipRevealAnimation(
                v,
                left,
                top,
                width,
                height,
            ),
        )
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId = if (v.display != null) v.display.displayId else Display.DEFAULT_DISPLAY
        val callback = RunnableList()
        return ActivityOptionsWrapper(options, callback)
    }

    override fun onResume() {
        super.onResume()
        restartIfPending()
        refreshPredictionContainersFromModel()

        // Re-apply on every resume, not just at onCreate - "Clear all" in Recents can leave this
        // task's exclude-from-recents state reset, and this task's own onCreate doesn't run again
        // when merely returning from the app drawer or another app.
        applyRecentsExclusion(this, preferenceManager2.hideLawnchairActivities.firstCached())

        dragLayer.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                private var handled = false

                override fun onDraw() {
                    if (handled) {
                        return
                    }
                    handled = true

                    dragLayer.post {
                        dragLayer.viewTreeObserver.removeOnDrawListener(this)
                        // Drop stuck All Apps RenderEffect on icons after returning home.
                        depthController.clearStuckBlurOnResumeIfHome()
                    }
                }
            },
        )
    }

    override fun onStateSetEnd(state: LauncherState) {
        super.onStateSetEnd(state)
        refreshPredictionContainersFromModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        drivingModeController.stop()
        // Only actually closes if required, safe to call if not enabled
        SmartspacerClient.close()
    }

    override fun getDefaultOverlay(): LauncherOverlayManager = defaultOverlay

    fun recreateIfNotScheduled() {
        if (sRestartFlags == 0) {
            recreate()
        }
    }

    private fun restartIfPending() {
        when {
            sRestartFlags and FLAG_RESTART != 0 -> lawnchairApp.restart(false)

            sRestartFlags and FLAG_RECREATE != 0 -> {
                sRestartFlags = 0
                recreate()
            }
        }
    }

    private fun refreshPredictionContainersFromModel() {
        LauncherAppState.getInstance(this).model.loadAsync { dataModel ->
            if (dataModel == null || isDestroyed) return@loadAsync

            val predictedContainers = synchronized(dataModel) {
                listOf(
                    dataModel.itemsIdMap[CONTAINER_ALL_APPS_PREDICTION] as? PredictedContainerInfo,
                    dataModel.itemsIdMap[CONTAINER_HOTSEAT_PREDICTION] as? PredictedContainerInfo,
                    dataModel.itemsIdMap[CONTAINER_WIDGETS_PREDICTION] as? PredictedContainerInfo,
                ).filterNotNull()
            }

            Executors.MAIN_EXECUTOR.execute {
                if (isDestroyed) return@execute
                predictedContainers.forEach(::bindPredictedContainerInfo)
            }
        }
    }

    /**
     * Reloads app icons if there is an active icon pack & [PreferenceManager2.alwaysReloadIcons] is enabled.
     */
    private fun reloadIconsIfNeeded() {
        if (
            preferenceManager2.alwaysReloadIcons.firstCached()
        ) {
            LauncherAppState.getInstance(this).model.reloadIfActive()
        }
    }

    companion object {
        private const val FLAG_RECREATE = 1 shl 0
        private const val FLAG_RESTART = 1 shl 1

        var sRestartFlags = 0

        val instance get() = LawnchairApp.launcher

        // Set on the intent DrivingModeTileService uses to launch this activity when no instance
        // (and so no live DrivingModeController) exists yet to show the overlay directly.
        const val EXTRA_START_DRIVING_MODE = "app.lawnchair.START_DRIVING_MODE"
    }
}

val Context.launcher: LawnchairLauncher
    get() = BaseActivity.fromContext(this)

val Context.launcherNullable: LawnchairLauncher? get() = try {
    launcher
} catch (_: IllegalArgumentException) {
    null
}
