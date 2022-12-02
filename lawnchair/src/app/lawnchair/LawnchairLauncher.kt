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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairApp.Companion.showQuickstepWarningIfNecessary
import app.lawnchair.gestures.GestureController
import app.lawnchair.gestures.VerticalSwipeTouchController
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.ui.popup.LawnchairShortcut
import app.lawnchair.util.getThemedIconPacksInstalled
import com.android.launcher3.BaseActivity
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.search.SearchAdapterProvider
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.uioverrides.states.OverviewState
import com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW
import com.android.launcher3.util.Themes
import com.android.launcher3.util.TouchController
import com.android.launcher3.widget.RoundedCornerEnforcement
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.shared.system.QuickStepContract
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.onEach
import dev.kdrag0n.monet.theme.ColorScheme
import java.util.stream.Stream
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LawnchairLauncher : QuickstepLauncher() {

    val gestureController by lazy { GestureController(this) }
    private val defaultOverlay by lazy { OverlayCallbackImpl(this) }
    private val prefs by lazy { PreferenceManager.getInstance(this) }
    private val preferenceManager2 by lazy { PreferenceManager2.getInstance(this) }
    private val insetsController by lazy { WindowInsetsControllerCompat(launcher.window, rootView) }

    private val themeProvider by lazy { ThemeProvider.INSTANCE.get(this) }
    private lateinit var colorScheme: ColorScheme

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

    private var hasBackGesture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        layoutInflater.factory2 = LawnchairLayoutFactory(this)
        super.onCreate(savedInstanceState)

        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)

        if (prefs.autoLaunchRoot.get()) {
            lifecycleScope.launch {
                try {
                    RootHelperManager.INSTANCE.get(this@LawnchairLauncher).getService()
                } catch (_: RootNotAvailableException) {
                }
            }
        }

        preferenceManager2.showStatusBar.get().distinctUntilChanged().onEach {
            with (insetsController) {
                if (it) show(WindowInsetsCompat.Type.statusBars())
                else hide(WindowInsetsCompat.Type.statusBars())
            }
            with (launcher.stateManager) {
                if (it) removeStateListener(noStatusBarStateListener)
                else addStateListener(noStatusBarStateListener)
            }
        }.launchIn(scope = lifecycleScope)

        prefs.overrideWindowCornerRadius.subscribeValues(this) {
            QuickStepContract.sHasCustomCornerRadius = it
        }
        prefs.windowCornerRadius.subscribeValues(this) {
            QuickStepContract.sCustomCornerRadius = it.toFloat()
        }
        preferenceManager2.roundedWidgets.onEach(launchIn = lifecycleScope) {
            RoundedCornerEnforcement.sRoundedCornerEnabled = it
        }
        val isWorkspaceDarkText = Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText)
        preferenceManager2.darkStatusBar.onEach(launchIn = lifecycleScope) { darkStatusBar ->
            systemUiController.updateUiState(UI_STATE_BASE_WINDOW, isWorkspaceDarkText || darkStatusBar)
        }
        preferenceManager2.backPressGestureHandler.onEach(launchIn = lifecycleScope) { handler ->
            hasBackGesture = handler !is GestureHandlerConfig.NoOp
        }

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
    }

    override fun collectStateHandlers(out: MutableList<StateManager.StateHandler<*>>) {
        super.collectStateHandlers(out)
        out.add(SearchBarStateHandler(this))
    }

    override fun getSupportedShortcuts(): Stream<SystemShortcut.Factory<*>> {
        return Stream.concat(
            super.getSupportedShortcuts(),
            Stream.of(LawnchairShortcut.CUSTOMIZE)
        )
    }

    override fun createSearchAdapterProvider(allapps: AllAppsContainerView): SearchAdapterProvider {
        return LawnchairSearchAdapterProvider(this, allapps)
    }

    override fun updateTheme() {
        if (themeProvider.colorScheme != colorScheme) {
            recreate()
        } else {
            super.updateTheme()
        }
    }

    override fun createTouchControllers(): Array<TouchController> {
        val verticalSwipeController = VerticalSwipeTouchController(this, gestureController)
        return arrayOf<TouchController>(verticalSwipeController) + super.createTouchControllers()
    }

    override fun handleHomeTap() {
        gestureController.onHomePressed()
    }

    override fun shouldBackButtonBeHidden(toState: LauncherState): Boolean {
        if (toState == LauncherState.NORMAL && hasBackGesture) {
            return false
        }
        return super.shouldBackButtonBeHidden(toState)
    }

    override fun onResume() {
        super.onResume()
        restartIfPending()

        dragLayer.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            var handled = false

            override fun onDraw() {
                if (handled) {
                    return
                }
                handled = true

                dragLayer.post {
                    dragLayer.viewTreeObserver.removeOnDrawListener(this)
                }
                depthController.reapplyDepth()
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (activityResultRegistry.dispatchResult(requestCode, resultCode, data)) {
            mPendingActivityRequestCode = -1
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun getDefaultOverlay(): LauncherOverlayManager {
        return defaultOverlay
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

    private fun scheduleFlag(flag: Int) {
        sRestartFlags = sRestartFlags or flag
        if (lifecycle.currentState === Lifecycle.State.RESUMED) {
            restartIfPending()
        }
    }

    fun scheduleRecreate() {
        scheduleFlag(FLAG_RECREATE)
    }

    fun scheduleRestart() {
        scheduleFlag(FLAG_RESTART)
    }

    fun recreateIfNotScheduled() {
        if (sRestartFlags == 0) {
            recreate()
        }
    }

    /**
     * Reloads app icons if there is an active icon pack & [PreferenceManager2.alwaysReloadIcons] is enabled.
     */
    private fun reloadIconsIfNeeded() {
        if (
            preferenceManager2.alwaysReloadIcons.firstBlocking() &&
            prefs.iconPackPackage.get().isNotEmpty()
        ) {
            LauncherAppState.getInstance(this).reloadIcons()
        }
    }

    companion object {
        private const val FLAG_RECREATE = 1 shl 0
        private const val FLAG_RESTART = 1 shl 1

        var sRestartFlags = 0

        val instance get() = LauncherAppState.getInstanceNoCreate()?.launcher as? LawnchairLauncher
    }
}

val Context.launcher: LawnchairLauncher
    get() = BaseActivity.fromContext(this)

val Context.launcherNullable: LawnchairLauncher? get() = try {
    launcher
} catch (_: IllegalArgumentException) {
    null
}
