package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.support.annotation.Keep
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R
import org.json.JSONObject

@Keep
class OpenDrawerGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_drawer)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.showAppsView(true, true)
    }
}

@Keep
class OpenWidgetsGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_widgets)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.showWidgetsView(true, true)
    }
}

@Keep
class OpenOverviewGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_overview)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.showOverviewPopup(false)
    }
}

@Keep
class StartGlobalSearchGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_global_search)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.startGlobalSearch(null,  false, null, null)
    }
}

@Keep
class StartAppSearchGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_app_search)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.showAppsView(true, true, true)
    }
}
