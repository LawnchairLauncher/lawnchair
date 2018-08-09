/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.support.annotation.Keep
import android.widget.Toast
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.gestures.ui.SelectAppActivity
import com.android.launcher3.LauncherState
import ch.deletescape.lawnchair.globalsearch.SearchProviderController
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.widget.WidgetsFullSheet
import org.json.JSONObject

@Keep
open class OpenDrawerGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_drawer)!!

    override fun onGestureTrigger(controller: GestureController) {
        controller.launcher.stateManager.goToState(LauncherState.ALL_APPS, true, getOnCompleteRunnable(controller))
    }

    open fun getOnCompleteRunnable(controller: GestureController): Runnable? = null
}

@Keep
class OpenWidgetsGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_widgets)!!

    override fun onGestureTrigger(controller: GestureController) {
        WidgetsFullSheet.show(controller.launcher, true)
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
        OptionsPopupView.showDefaultOptions(controller.launcher, controller.touchDownPoint.x, controller.touchDownPoint.y)
    }
}

@Keep
class StartGlobalSearchGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_global_search)!!

    override fun onGestureTrigger(controller: GestureController) {
        SearchProviderController.getInstance(context).searchProvider.startSearch {
            context.startActivity(it)
        }
    }
}

@Keep
class StartAppSearchGestureHandler(context: Context, config: JSONObject?) : OpenDrawerGestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_app_search)!!

    override fun getOnCompleteRunnable(controller: GestureController): Runnable? {
        return Runnable { controller.launcher.appsView.searchUiManager.startSearch() }
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


@Keep
class StartAssistantGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_assistant)!!

    override fun onGestureTrigger(controller: GestureController) {
        Utilities.startAssistant(context)
    }
}
