package app.lawnchair.gestures.config

import android.app.Activity
import android.content.Context
import android.os.ResultReceiver
import app.lawnchair.BlankActivity
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.GesturesPickApp
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R

sealed class GestureHandlerOption(
    private val labelRes: Int,
    val iconRes: Int,
    val configClass: Class<*>,
) {
    fun getLabel(context: Context) = context.getString(labelRes)

    open fun isSelected(config: GestureHandlerConfig) = config::class.java == configClass

    abstract suspend fun buildConfig(
        activity: Activity,
        resultReceiver: ResultReceiver? = null,
    ): GestureHandlerConfig?

    sealed class Simple(labelRes: Int, iconRes: Int, val obj: GestureHandlerConfig) : GestureHandlerOption(labelRes, iconRes, obj::class.java) {
        constructor(obj: GestureHandlerConfig.Simple) : this(obj.labelRes, obj.iconRes, obj)
        override suspend fun buildConfig(
            activity: Activity,
            resultReceiver: ResultReceiver?,
        ) = obj
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
        R.drawable.ic_launcher_home,
        GestureHandlerConfig.OpenApp::class.java,
    ) {
        override fun isSelected(config: GestureHandlerConfig) = config is GestureHandlerConfig.OpenApp || config is GestureHandlerConfig.OpenShortcut

        override suspend fun buildConfig(
            activity: Activity,
            resultReceiver: ResultReceiver?,
        ): GestureHandlerConfig? {
            val intent = PreferenceActivity.createIntent(activity, GesturesPickApp)
            if (resultReceiver != null) {
                intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver)
            }
            val result = BlankActivity.startBlankActivityForResult(activity, intent)
            if (resultReceiver != null) return null

            val configString = result.data?.getStringExtra("config") ?: return null
            return kotlinxJson.decodeFromString(configString)
        }
    }

    companion object {
        const val EXTRA_CONFIG = "app.lawnchair.gestures.config.CONFIG"
        const val EXTRA_RESULT_RECEIVER = "app.lawnchair.gestures.config.RESULT_RECEIVER"
    }
}
