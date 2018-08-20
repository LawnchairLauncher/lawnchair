package ch.deletescape.lawnchair.gestures.handlers

import android.app.ActivityOptions
import android.content.Context
import android.support.annotation.Keep
import android.util.Log
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.mainHandler
import ch.deletescape.lawnchair.mostRecentTask
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.quickstep.RecentsModel
import com.android.quickstep.TouchInteractionService
import com.android.systemui.shared.system.ActivityManagerWrapper
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

@Keep
open class LaunchMostRecentTaskGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_last_task)!!
    override val isAvailable: Boolean
        get() = TouchInteractionService.isConnected()

    override fun onGestureTrigger(controller: GestureController) {
        RecentsModel.getInstance(context).loadTasks(-1, {
            val opts = ActivityOptions.makeBasic()
            if (it.taskStack.mostRecentTask != null) {
                ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(it.taskStack.mostRecentTask?.key, opts, {
                    if (!it) {
                        Log.e(this::class.java.simpleName, "Failed to start task")
                    }
                }, mainHandler)
            }
        })
    }
}
