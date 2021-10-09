package app.lawnchair

import android.os.Build
import android.os.CancellationSignal
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.runOnEnd
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.states.StateAnimationConfig
import com.android.quickstep.AnimatedFloat

class SearchBarStateHandler(private val launcher: LawnchairLauncher) : StateManager.StateHandler<LauncherState> {

    private val autoShowKeyboard = PreferenceManager.getInstance(launcher).searchAutoShowKeyboard

    override fun setState(state: LauncherState) {
        if (state == LauncherState.ALL_APPS && autoShowKeyboard.get()) {
            showKeyboard()
        }
    }

    override fun setStateWithAnimation(
        toState: LauncherState,
        config: StateAnimationConfig,
        animation: PendingAnimation
    ) {
        if (Utilities.ATLEAST_R && shouldAnimateKeyboard(toState)) {
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
            animation.setFloat(handler.progress, AnimatedFloat.VALUE, 1f, Interpolators.LINEAR)
            animation.runOnEnd { isSuccess ->
                if (isSuccess) {
                    handler.onAnimationEnd()
                    cancellationSignal.cancel()
                }
            }
        }
        if (launcher.isInState(LauncherState.NORMAL) && toState == LauncherState.ALL_APPS) {
            if (autoShowKeyboard.get()) {
                val progress = AnimatedFloat()
                animation.setFloat(progress, AnimatedFloat.VALUE, 1f, Interpolators.LINEAR)
                animation.runOnEnd { isSuccess ->
                    if (isSuccess && progress.value > 0.5f) {
                        showKeyboard()
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun shouldAnimateKeyboard(toState: LauncherState): Boolean {
        val rootWindowInsets = launcher.rootView.rootWindowInsets
        val keyboardVisible = rootWindowInsets?.isVisible(WindowInsets.Type.ime()) ?: false
        return keyboardVisible && launcher.isInState(LauncherState.ALL_APPS) && toState != LauncherState.ALL_APPS
    }

    private fun showKeyboard() {
        val editText = launcher.appsView.searchUiManager.setTextSearchEnabled(true) as? ExtendedEditText ?: return
        editText.showKeyboard()
    }
}
