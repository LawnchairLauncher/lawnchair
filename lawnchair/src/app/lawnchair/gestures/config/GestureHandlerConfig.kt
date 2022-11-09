package app.lawnchair.gestures.config

import android.content.Context
import app.lawnchair.gestures.handlers.GestureHandler
import app.lawnchair.gestures.handlers.NoOpGestureHandler
import app.lawnchair.gestures.handlers.*
import com.android.launcher3.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class GestureHandlerConfig {

    abstract fun getLabel(context: Context): String
    abstract fun createHandler(context: Context): GestureHandler

    @Serializable
    sealed class Simple(
        val labelRes: Int,
        @Transient private val creator: (Context) -> GestureHandler = {
            throw IllegalArgumentException("default creator not supported")
        }
    ) : GestureHandlerConfig() {
        override fun getLabel(context: Context) = context.getString(labelRes)
        override fun createHandler(context: Context) = creator(context)
    }

    @Serializable
    @SerialName("noOp")
    object NoOp : Simple(R.string.gesture_handler_no_op, ::NoOpGestureHandler)

    @Serializable
    @SerialName("sleep")
    object Sleep : Simple(R.string.gesture_handler_sleep, ::SleepGestureHandler)

    @Serializable
    @SerialName("openNotifications")
    object OpenNotifications : Simple(R.string.gesture_handler_open_notifications, ::OpenNotificationsHandler)

    @Serializable
    @SerialName("openAppDrawer")
    object OpenAppDrawer : Simple(R.string.gesture_handler_open_app_drawer, ::OpenAppDrawerGestureHandler)

    @Serializable
    @SerialName("openAppSearch")
    object OpenAppSearch : Simple(R.string.gesture_handler_open_app_search, ::OpenAppSearchGestureHandler)

    @Serializable
    @SerialName("openSearch")
    object OpenSearch : Simple(R.string.gesture_handler_open_search, ::OpenSearchGestureHandler)

    @Serializable
    @SerialName("openApp")
    data class OpenApp(val appName: String, val target: OpenAppTarget) : GestureHandlerConfig() {
        override fun getLabel(context: Context) = context.getString(R.string.gesture_handler_open_app_config, appName)
        override fun createHandler(context: Context) = OpenAppGestureHandler(context, target)
    }
}
