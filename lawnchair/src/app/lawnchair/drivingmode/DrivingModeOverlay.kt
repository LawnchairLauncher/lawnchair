package app.lawnchair.drivingmode

import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.views.BaseDragLayer

/**
 * Owns a single ComposeView showing DrivingModeScreen, attached directly on
 * top of the launcher's dragLayer — no separate task/activity involved, so
 * there's nothing for Android's single-launcher-owner model to fight. Back
 * naturally returns here because it's the same task as the home screen.
 */
class DrivingModeOverlay(private val launcher: Launcher) {

    private var composeView: ComposeView? = null
    private var hadNavBarContrastEnforced = true
    private var hadStatusBarContrastEnforced = true

    val isShowing: Boolean get() = composeView != null

    fun show() {
        if (isShowing) return

        // The overlay is added to this Activity's own dragLayer, so it's invisible unless this
        // Activity is actually in the foreground - e.g. triggered from the Settings screen (a
        // separate Activity), or by a real Bluetooth connect while some other app is open.
        // Bring the launcher forward first; REORDER_TO_FRONT reuses the existing instance rather
        // than recreating it, so none of the state set up below is lost.
        launcher.startActivity(
            Intent(launcher, launcher.javaClass).setFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )

        // Force a clean slate — if AllApps (or a folder/popup) was open when
        // driving mode was triggered, it would otherwise stay open and keep
        // responding to touches underneath our overlay.
        AbstractFloatingView.closeAllOpenViews(launcher, false)
        launcher.stateManager.goToState(LauncherState.NORMAL, false)

        // Phones normally lock the launcher to portrait (unless the user enabled "Allow home
        // screen rotation") - override that while driving mode is up so the grid follows the
        // device's actual orientation, same as any other rotating app. The activity already
        // declares orientation|screenSize in configChanges, so this doesn't recreate it.
        launcher.rotationHelper.setCurrentStateRequest(RotationHelper.REQUEST_ROTATE)

        val view = ComposeView(launcher).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DrivingModeScreen(
                    launcher = launcher,
                    onExit = { hide() },
                )
            }
        }
        val params = BaseDragLayer.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        launcher.dragLayer.addView(view, params)
        composeView = view

        // Hide icons/widgets so only the (dimmed) wallpaper shows behind the
        // driving UI, rather than the workspace content dimmed underneath it.
        // The hotseat is specifically hidden via .alpha, not .visibility —
        // that's the actual property WorkspaceStateTransitionAnimation uses
        // for state-driven hotseat visibility (see HOTSEAT_ICONS handling),
        // and our goToState(NORMAL) call above re-asserts alpha=1 as part of
        // NORMAL state's defaults, fighting a plain .visibility override.
        launcher.workspace.visibility = View.INVISIBLE
        launcher.hotseat.visibility = View.INVISIBLE
        launcher.hotseat.alpha = 0f
        Log.i(TAG, "show(): hotseat.visibility=${launcher.hotseat.visibility} alpha=${launcher.hotseat.alpha}")

        // Re-assert after this layout pass in case the state transition's
        // own animation/property-setters apply after we do, on this frame.
        launcher.dragLayer.post {
            launcher.workspace.visibility = View.INVISIBLE
            launcher.hotseat.visibility = View.INVISIBLE
            launcher.hotseat.alpha = 0f
            Log.i(TAG, "show()/post: hotseat.visibility=${launcher.hotseat.visibility} alpha=${launcher.hotseat.alpha}")
        }

        // Adding a view on top doesn't stop Launcher3's own swipe gestures
        // (open all apps, etc.) — those are TouchControllers registered on
        // dragLayer itself and checked before normal child dispatch. Force
        // a refresh so LawnchairLauncher.createTouchControllers() (which
        // checks isShowing) returns none while we're up.
        launcher.dragLayer.recreateControllers()

        // The OS draws its own translucent contrast scrim behind the status/
        // nav bars independent of app content — with it on, our own dimming
        // can't visibly darken that strip. Disable it while showing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            launcher.window?.let { window ->
                hadNavBarContrastEnforced = window.isNavigationBarContrastEnforced
                hadStatusBarContrastEnforced = window.isStatusBarContrastEnforced
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
    }

    fun hide() {
        composeView?.let { launcher.dragLayer.removeView(it) }
        composeView = null
        launcher.workspace.visibility = View.VISIBLE
        launcher.hotseat.visibility = View.VISIBLE
        launcher.hotseat.alpha = 1f
        launcher.dragLayer.recreateControllers()
        launcher.rotationHelper.setCurrentStateRequest(RotationHelper.REQUEST_NONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            launcher.window?.let { window ->
                window.isNavigationBarContrastEnforced = hadNavBarContrastEnforced
                window.isStatusBarContrastEnforced = hadStatusBarContrastEnforced
            }
        }
    }

    companion object {
        private const val TAG = "DrivingModeOverlay"
    }
}
