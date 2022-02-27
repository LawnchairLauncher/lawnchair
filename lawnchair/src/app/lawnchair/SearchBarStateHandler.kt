package app.lawnchair

import android.os.CancellationSignal
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.anim.AnimatorListeners.forEndCallback
import com.android.launcher3.anim.AnimatorListeners.forSuccessCallback
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.states.StateAnimationConfig
import com.android.quickstep.AnimatedFloat
import com.patrykmichalik.preferencemanager.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SearchBarStateHandler(private val launcher: LawnchairLauncher) :
    StateManager.StateHandler<LauncherState> {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val preferenceManager2 = PreferenceManager2.getInstance(launcher)
    private var autoShowKeyboard = false

    init {
        preferenceManager2.autoShowKeyboardInDrawer.onEach(launchIn = coroutineScope) {
            autoShowKeyboard = it
        }
    }

    override fun setState(state: LauncherState) {
        if (launcher.isInState(LauncherState.NORMAL) && state == LauncherState.ALL_APPS && autoShowKeyboard) {
            showKeyboard()
        }
    }

    override fun setStateWithAnimation(
        toState: LauncherState,
        config: StateAnimationConfig,
        animation: PendingAnimation
    ) {
        if (shouldAnimateKeyboard(toState)) {
            if (Utilities.ATLEAST_R) {
                val handler = SearchBarInsetsHandler(launcher.allAppsController.shiftRange)
                val cancellationSignal = CancellationSignal()
                val windowInsetsController = launcher.appsView.windowInsetsController
                windowInsetsController?.controlWindowInsetsAnimation(
                    WindowInsets.Type.ime(),
                    -1,
                    Interpolators.LINEAR,
                    cancellationSignal,
                    handler
                )
                animation.setFloat(
                    handler.progress,
                    AnimatedFloat.VALUE,
                    1f,
                    Interpolators.DEACCEL_1_7
                )
                animation.addListener(forEndCallback(Runnable {
                    handler.onAnimationEnd()
                    cancellationSignal.cancel()
                }))
            } else {
                animation.addListener(forSuccessCallback {
                    launcher.appsView.searchUiManager.editText?.hideKeyboard()
                })
            }
        }
        if (launcher.isInState(LauncherState.NORMAL) && toState == LauncherState.ALL_APPS) {
            if (autoShowKeyboard) {
                val progress = AnimatedFloat()
                animation.setFloat(progress, AnimatedFloat.VALUE, 1f, Interpolators.LINEAR)
                animation.addListener(forSuccessCallback {
                    if (progress.value > 0.5f) {
                        showKeyboard()
                    }
                })
            }
        }
    }

    private fun shouldAnimateKeyboard(toState: LauncherState): Boolean {
        val windowInsets = launcher.rootView.rootWindowInsets ?: return false
        val rootWindowInsets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        val keyboardVisible = rootWindowInsets.isVisible(WindowInsetsCompat.Type.ime())
        return keyboardVisible && launcher.isInState(LauncherState.ALL_APPS) && toState != LauncherState.ALL_APPS
    }

    private fun showKeyboard() {
        val editText = launcher.appsView.searchUiManager.editText ?: return
        editText.showKeyboard()
    }
}
