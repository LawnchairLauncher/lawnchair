package app.lawnchair

import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.runOnEnd
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.LauncherState
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.states.StateAnimationConfig

class SearchBarStateHandler(private val launcher: LawnchairLauncher) : StateManager.StateHandler<LauncherState> {

    private val autoShowKeyboard = PreferenceManager.getInstance(launcher).searchAutoShowKeyboard

    override fun setState(state: LauncherState) {

    }

    override fun setStateWithAnimation(
        toState: LauncherState,
        config: StateAnimationConfig,
        animation: PendingAnimation
    ) {
        if (launcher.isInState(LauncherState.NORMAL) && toState == LauncherState.ALL_APPS) {
            val editText = launcher.appsView.searchUiManager.setTextSearchEnabled(true) as? ExtendedEditText ?: return
            if (autoShowKeyboard.get()) {
                animation.runOnEnd {
                    editText.showKeyboard()
                }
            }
        }
    }
}
