package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.support.annotation.Keep
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.gestures.ui.SelectAppActivity
import com.android.launcher3.R
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.util.ComponentKey
import org.json.JSONObject

@Keep
open class OpenDrawerGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

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
class OpenSettingsGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_settings)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.startActivity(Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
            `package` = controller.launcher.packageName
        })
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
class StartAppSearchGestureHandler(context: Context, config: JSONObject?) : OpenDrawerGestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_app_search)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.showAppsView(true, true, true)
    }
}

@Keep
class StartAppGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val hasConfig = true
    override val configIntent = Intent(context, SelectAppActivity::class.java)
    override val displayName get() = if (target != null)
        String.format(displayNameWithTarget, appName) else displayNameWithoutTarget

    private val displayNameWithoutTarget = context.getString(R.string.action_open_app)!!
    private val displayNameWithTarget = context.getString(R.string.action_open_app_with_target)!!

    var appName: String? = null
    var target: ComponentKey? = null

    init {
        if (config?.has("target") == true) {
            appName = config.getString("appName")
            target = ComponentKey(context, config.getString("target"))
        }
    }

    override fun saveConfig(config: JSONObject) {
        super.saveConfig(config)
        config.put("appName", appName)
        config.put("target", target?.toString())
    }

    override fun onConfigResult(data: Intent?) {
        super.onConfigResult(data)
        if (data != null) {
            appName = data.getStringExtra("appName")
            target = ComponentKey(context, data.getStringExtra("target"))
        }
    }

    override fun onGestureTrigger(controller: GestureController) {
        if (target != null) {
            LauncherAppsCompat.getInstance(context)
                    .startActivityForProfile(target!!.componentName, target!!.user, null, null)
        }
    }
}
