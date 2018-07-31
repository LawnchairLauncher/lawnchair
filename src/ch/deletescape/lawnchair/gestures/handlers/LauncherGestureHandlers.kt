package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.support.annotation.Keep
import android.widget.Toast
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.gestures.ui.SelectAppActivity
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
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
class OpenOverlayGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_overlay)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.googleNow.showOverlay(true)
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

    var type: String? = null
    var appName: String? = null
    var target: ComponentKey? = null
    var intent: Intent? = null
    var user: UserHandle? = null
    var packageName: String? = null
    var id: String? = null

    init {
        if (config?.has("appName") == true) {
            appName = config.getString("appName")
            type = if (config.has("type")) config.getString("type") else "app"
            if (type == "app") {
                target = ComponentKey(context, config.getString("target"))
            } else {
                intent = Intent.parseUri(config.getString("intent"), 0)
                user = UserManagerCompat.getInstance(context).getUserForSerialNumber(config.getLong("user"))
                packageName = config.getString("packageName")
                id = config.getString("id")
            }
        }
    }

    override fun saveConfig(config: JSONObject) {
        super.saveConfig(config)
        config.put("appName", appName)
        config.put("type", type)
        when (type) {
            "app" -> {
                config.put("target", target.toString())
            }
            "shortcut" -> {
                config.put("intent", intent!!.toUri(0))
                config.put("user", UserManagerCompat.getInstance(context).getSerialNumberForUser(user))
                config.put("packageName", packageName)
                config.put("id", id)
            }
        }
    }

    override fun onConfigResult(data: Intent?) {
        super.onConfigResult(data)
        if (data != null) {
            appName = data.getStringExtra("appName")
            type = data.getStringExtra("type")
            when (type) {
                "app" -> {
                    target = ComponentKey(context, data.getStringExtra("target"))
                }
                "shortcut" -> {
                    intent = Intent.parseUri(data.getStringExtra("intent"), 0)
                    user = data.getParcelableExtra("user")
                    packageName = data.getStringExtra("packageName")
                    id = data.getStringExtra("id")
                }
            }
        }
    }

    override fun onGestureTrigger(controller: GestureController) {
        when (type) {
            "app" -> {
                try {
                    LauncherAppsCompat.getInstance(context)
                            .startActivityForProfile(target!!.componentName, target!!.user, null, null)
                } catch (e: NullPointerException){
                    // App is probably not installed anymore, show a Toast
                    Toast.makeText(context, R.string.app_gesture_failed_toast, Toast.LENGTH_LONG).show()
                }
            }
            "shortcut" -> {
                DeepShortcutManager.getInstance(context)
                        .startShortcut(packageName, id, intent, null, user)
            }
        }
    }
}
