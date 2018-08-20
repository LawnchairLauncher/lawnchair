package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.support.annotation.Keep
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.quickstep.TouchInteractionService
import org.json.JSONObject

@Keep
open class OpenRecentsGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_recents)!!
    override val isAvailable: Boolean
        get() = TouchInteractionService.isConnected()

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.stateManager.goToState(LauncherState.OVERVIEW)
    }
}
