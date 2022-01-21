/*
 * Copyright 2021, Lawnchair
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
import android.content.IntentSender
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import app.lawnchair.gestures.GestureController
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import app.lawnchair.search.LawnchairSearchAdapterProvider
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.color.ColorTokens
import app.lawnchair.ui.popup.LawnchairShortcut
import com.android.launcher3.*
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.search.SearchAdapterProvider
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.uioverrides.states.OverviewState
import com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.RoundedCornerEnforcement
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.shared.system.QuickStepContract
import dev.kdrag0n.monet.theme.ColorScheme
import kotlinx.coroutines.launch
import java.util.stream.Stream
import kotlin.math.roundToInt

class LawnchairLauncher : QuickstepLauncher(), LifecycleOwner,
    SavedStateRegistryOwner, ActivityResultRegistryOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            val activity = this@LawnchairLauncher

            // Immediate result path
            val synchronousResult = contract.getSynchronousResult(activity, input)
            if (synchronousResult != null) {
                Handler(Looper.getMainLooper()).post {
                    dispatchResult(
                        requestCode,
                        synchronousResult.value
                    )
                }
                return
            }

            // Start activity path
            val intent = contract.createIntent(activity, input)
            var optionsBundle: Bundle? = null
            // If there are any extras, we should defensively set the classLoader
            if (intent.extras != null && intent.extras!!.classLoader == null) {
                intent.setExtrasClassLoader(activity.classLoader)
            }
            if (intent.hasExtra(StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE)) {
                optionsBundle =
                    intent.getBundleExtra(StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE)
                intent.removeExtra(StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE)
            } else if (options != null) {
                optionsBundle = options.toBundle()
            }
            if (RequestMultiplePermissions.ACTION_REQUEST_PERMISSIONS == intent.action) {
                // requestPermissions path
                var permissions =
                    intent.getStringArrayExtra(RequestMultiplePermissions.EXTRA_PERMISSIONS)
                if (permissions == null) {
                    permissions = arrayOfNulls(0)
                }
                ActivityCompat.requestPermissions(activity, permissions, requestCode)
            } else if (StartIntentSenderForResult.ACTION_INTENT_SENDER_REQUEST == intent.action) {
                val request: IntentSenderRequest =
                    intent.getParcelableExtra(StartIntentSenderForResult.EXTRA_INTENT_SENDER_REQUEST)!!
                try {
                    // startIntentSenderForResult path
                    ActivityCompat.startIntentSenderForResult(
                        activity, request.intentSender,
                        requestCode, request.fillInIntent, request.flagsMask,
                        request.flagsValues, 0, optionsBundle
                    )
                } catch (e: IntentSender.SendIntentException) {
                    Handler(Looper.getMainLooper()).post {
                        dispatchResult(
                            requestCode, RESULT_CANCELED,
                            Intent()
                                .setAction(StartIntentSenderForResult.ACTION_INTENT_SENDER_REQUEST)
                                .putExtra(StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION, e)
                        )
                    }
                }
            } else {
                // startActivityForResult path
                ActivityCompat.startActivityForResult(activity, intent, requestCode, optionsBundle)
            }
        }
    }
    private val _onBackPressedDispatcher = OnBackPressedDispatcher {
        super.onBackPressed()
    }
    val gestureController by lazy { GestureController(this) }
    private val defaultOverlay by lazy { OverlayCallbackImpl(this) }
    private val prefs by lazy { PreferenceManager.getInstance(this) }
    var allAppsScrimColor = 0

    private val themeProvider by lazy { ThemeProvider.INSTANCE.get(this) }
    private lateinit var colorScheme: ColorScheme

    override fun onCreate(savedInstanceState: Bundle?) {
        layoutInflater.factory2 = LawnchairLayoutFactory(this)
        savedStateRegistryController.performRestore(savedInstanceState)
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)
        prefs.drawerOpacity.subscribeValues(this) { opacity ->
            val scrimColor = ColorTokens.AllAppsScrimColor.resolveColor(this)
            val alpha = (opacity * 255).roundToInt()
            allAppsScrimColor = ColorUtils.setAlphaComponent(scrimColor, alpha)
        }

        if (prefs.autoLaunchRoot.get()) {
            lifecycleScope.launch {
                try {
                    RootHelperManager.INSTANCE.get(this@LawnchairLauncher).getService()
                } catch (e: RootNotAvailableException) {
                    // do nothing
                }
            }
        }
        if (!prefs.showStatusBar.get()) {
            val insetsController = WindowInsetsControllerCompat(launcher.window, rootView)
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            launcher.stateManager.addStateListener(object : StateManager.StateListener<LauncherState> {
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
            })
        }
        prefs.overrideWindowCornerRadius.subscribeValues(this) {
            QuickStepContract.sHasCustomCornerRadius = it
        }
        prefs.windowCornerRadius.subscribeValues(this) {
            QuickStepContract.sCustomCornerRadius = it.toFloat()
        }
        prefs.roundedWidgets.subscribeValues(this) {
            RoundedCornerEnforcement.sRoundedCornerEnabled = it
        }
        val isWorkspaceDarkText = Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText)
        prefs.darkStatusBar.subscribeValues(this) { darkStatusBar ->
            systemUiController.updateUiState(UI_STATE_BASE_WINDOW, isWorkspaceDarkText || darkStatusBar)
        }

        colorScheme = themeProvider.colorScheme
    }

    override fun setupViews() {
        super.setupViews()
        val launcherRootView = findViewById<LauncherRootView>(R.id.launcher)
        ViewTreeLifecycleOwner.set(launcherRootView, this)
        ViewTreeSavedStateRegistryOwner.set(launcherRootView, this)
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

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
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

    override fun onPause() {
        super.onPause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onStop() {
        super.onStop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onBackPressed() {
        _onBackPressedDispatcher.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savedStateRegistryController.performSave(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!activityResultRegistry.dispatchResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    override fun getSavedStateRegistry(): SavedStateRegistry {
        return savedStateRegistryController.savedStateRegistry
    }

    override fun getActivityResultRegistry(): ActivityResultRegistry {
        return activityResultRegistry
    }

    override fun getOnBackPressedDispatcher(): OnBackPressedDispatcher {
        return _onBackPressedDispatcher
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

    companion object {
        private const val FLAG_RECREATE = 1 shl 0
        private const val FLAG_RESTART = 1 shl 1

        var sRestartFlags = 0

        val instance get() = LauncherAppState.getInstanceNoCreate()?.launcher as? LawnchairLauncher
    }
}

val Context.launcher: LawnchairLauncher
    get() = BaseActivity.fromContext(this)

val Context.launcherNullable: LawnchairLauncher? get() {
    return try {
        launcher
    } catch (e: IllegalArgumentException) {
        null
    }
}
