package app.lawnchair

import android.os.CancellationSignal
import android.view.WindowInsets
import android.view.animation.Interpolator
import androidx.core.view.WindowInsetsCompat
import app.lawnchair.preferences2.PreferenceManager2
import com.android.app.animation.Interpolators
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.AnimatorListeners.forEndCallback
import com.android.launcher3.anim.AnimatorListeners.forSuccessCallback
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.states.StateAnimationConfig
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SearchBarStateHandler(private val launcher: LawnchairLauncher) : StateManager.StateHandler<LauncherState> {

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
        animation: PendingAnimation,
    ) {
        if (shouldAnimateKeyboardShow(toState)) {
            if (Utilities.ATLEAST_R) {
                val editText = launcher.appsView.searchUiManager.editText
                editText?.requestFocus()

                val handler = SearchBarShowInsetsHandler(launcher.allAppsController.shiftRange)
                val cancellationSignal = CancellationSignal()
                val windowInsetsController = launcher.appsView.windowInsetsController
                val interpolator = getKeyboardInterpolator(
                    isUserControlled = config.isUserControlled,
                )
                windowInsetsController?.controlWindowInsetsAnimation(
                    WindowInsets.Type.ime(),
                    -1,
                    interpolator,
                    cancellationSignal,
                    handler,
                )
                animation.setFloat(
                    handler.progress,
                    AnimatedFloat.VALUE,
                    1f,
                    interpolator,
                )
                animation.addListener(
                    forEndCallback(
                        Runnable {
                            handler.onAnimationEnd()
                            cancellationSignal.cancel()
                        },
                    ),
                )
            } else {
                animation.addListener(
                    forSuccessCallback {
                        showKeyboard()
                    },
                )
            }
            return
        }

        if (shouldAnimateKeyboardHide(toState)) {
            if (Utilities.ATLEAST_R) {
                val handler = SearchBarInsetsHandler(launcher.allAppsController.shiftRange)
                val cancellationSignal = CancellationSignal()
                val windowInsetsController = launcher.appsView.windowInsetsController
                windowInsetsController?.controlWindowInsetsAnimation(
                    WindowInsets.Type.ime(),
                    -1,
                    Interpolators.LINEAR,
                    cancellationSignal,
                    handler,
                )
                animation.setFloat(
                    handler.progress,
                    AnimatedFloat.VALUE,
                    1f,
                    Interpolators.DECELERATE_1_7,
                )
                animation.addListener(
                    forEndCallback(
                        Runnable {
                            handler.onAnimationEnd()
                            cancellationSignal.cancel()
                        },
                    ),
                )
            } else {
                animation.addListener(
                    forSuccessCallback {
                        launcher.appsView.searchUiManager.editText?.hideKeyboard()
                    },
                )
            }
        }
    }
    private fun shouldAnimateKeyboardShow(toState: LauncherState): Boolean {
        if (!autoShowKeyboard || !Utilities.ATLEAST_R) return false

        // If you are in Workspace and going somewhere that isn't AllApps, then false!
        if (!launcher.isInState(LauncherState.NORMAL) || toState != LauncherState.ALL_APPS) return false

        val insets = launcher.rootView.rootWindowInsets ?: return false
        val isImeVisible = WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime())
        return !isImeVisible
    }

    private fun shouldAnimateKeyboardHide(toState: LauncherState): Boolean {
        val windowInsets = launcher.rootView.rootWindowInsets ?: return false
        val rootWindowInsets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        val keyboardVisible = rootWindowInsets.isVisible(WindowInsetsCompat.Type.ime())

        // Keyboard is visible, AND you are in AllApps and going somewhere else that isn't AllApps!
        return keyboardVisible && launcher.isInState(LauncherState.ALL_APPS) && toState != LauncherState.ALL_APPS
    }

    private fun getKeyboardInterpolator(isUserControlled: Boolean): Interpolator {
        return if (isUserControlled) {
            Interpolators.clampToProgress(Interpolators.EMPHASIZED_ACCELERATE, 0.35f, 1.0f)
        } else {
            Interpolators.LINEAR
        }
    }

    private fun showKeyboard() {
        val editText = launcher.appsView.searchUiManager.editText ?: return
        editText.showKeyboard()
    }
}
