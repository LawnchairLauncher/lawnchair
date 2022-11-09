package app.lawnchair.gestures.config

import android.app.Activity
import android.content.Context
import app.lawnchair.BlankActivity
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.Routes
import com.android.launcher3.R
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed class GestureHandlerOption(
    val labelRes: Int,
    val configClass: Class<*>
) {

    fun getLabel(context: Context) = context.getString(labelRes)

    abstract suspend fun buildConfig(activity: Activity): GestureHandlerConfig?

    open class Simple(labelRes: Int, val obj: GestureHandlerConfig) : GestureHandlerOption(labelRes, obj::class.java) {
        constructor(obj: GestureHandlerConfig.Simple) : this(
            obj.labelRes, obj
        )

        override suspend fun buildConfig(activity: Activity) = obj
    }

    object NoOp : Simple(GestureHandlerConfig.NoOp)
    object Sleep : Simple(GestureHandlerConfig.Sleep)
    object OpenNotifications : Simple(GestureHandlerConfig.OpenNotifications)
    object OpenAppDrawer : Simple(GestureHandlerConfig.OpenAppDrawer)
    object OpenAppSearch : Simple(GestureHandlerConfig.OpenAppSearch)
    object OpenSearch : Simple(GestureHandlerConfig.OpenSearch)

    object OpenApp : GestureHandlerOption(
        R.string.gesture_handler_open_app_option,
        GestureHandlerConfig.OpenApp::class.java
    ) {
        override suspend fun buildConfig(activity: Activity): GestureHandlerConfig? {
            val intent = PreferenceActivity.createIntent(activity, "/${Routes.PICK_APP_FOR_GESTURE}/")
            val result = BlankActivity.startBlankActivityForResult(activity, intent)
            val configString = result.data?.getStringExtra("config") ?: return null
            return Json.decodeFromString(configString)
        }
    }
}
