package app.lawnchair.gestures.config

import android.app.Activity
import android.content.Context
import app.lawnchair.BlankActivity
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.GesturesPickApp
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R

sealed class GestureHandlerOption(
    private val labelRes: Int,
    val configClass: Class<*>,
) {

    fun getLabel(context: Context) = context.getString(labelRes)

    abstract suspend fun buildConfig(activity: Activity): GestureHandlerConfig?

    sealed class Simple(labelRes: Int, val obj: GestureHandlerConfig) : GestureHandlerOption(labelRes, obj::class.java) {
        constructor(obj: GestureHandlerConfig.Simple) : this(obj.labelRes, obj)

        override suspend fun buildConfig(activity: Activity) = obj
    }

    data object NoOp : Simple(GestureHandlerConfig.NoOp)
    data object Sleep : Simple(GestureHandlerConfig.Sleep)
    data object Recents : Simple(GestureHandlerConfig.Recents)
    data object OpenNotifications : Simple(GestureHandlerConfig.OpenNotifications)
    data object OpenQuickSettings : Simple(GestureHandlerConfig.OpenQuickSettings)
    data object OpenAppDrawer : Simple(GestureHandlerConfig.OpenAppDrawer)
    data object OpenAppSearch : Simple(GestureHandlerConfig.OpenAppSearch)
    data object OpenSearch : Simple(GestureHandlerConfig.OpenSearch)
    data object OpenAssistant : Simple(GestureHandlerConfig.OpenAssistant)

    data object OpenApp : GestureHandlerOption(
        R.string.gesture_handler_open_app_option,
        GestureHandlerConfig.OpenApp::class.java,
    ) {
        override suspend fun buildConfig(activity: Activity): GestureHandlerConfig? {
            val intent = PreferenceActivity.createIntent(activity, GesturesPickApp)
            val result = BlankActivity.startBlankActivityForResult(activity, intent)
            val configString = result.data?.getStringExtra("config") ?: return null
            return kotlinxJson.decodeFromString(configString)
        }
    }
}
