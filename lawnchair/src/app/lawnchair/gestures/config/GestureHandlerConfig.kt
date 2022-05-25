package app.lawnchair.gestures.config

import android.content.Context
import app.lawnchair.gestures.GestureHandler
import app.lawnchair.gestures.NoOpGestureHandler
import app.lawnchair.gestures.handlers.OpenAppGestureHandler
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.gestures.handlers.OpenNotificationsHandler
import app.lawnchair.gestures.handlers.SleepGestureHandler
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
        private val labelRes: Int,
        @Transient private val creator: (Context) -> GestureHandler = {
            throw IllegalArgumentException("default creator not supported")
        }
    ) : GestureHandlerConfig() {
        override fun getLabel(context: Context) = context.getString(labelRes)
        override fun createHandler(context: Context) = creator(context)
    }

    @Serializable
    @SerialName("noOp")
    object NoOp : Simple(R.string.gesture_handler_no_op, { NoOpGestureHandler(it) })

    @Serializable
    @SerialName("sleep")
    object Sleep : Simple(R.string.gesture_handler_sleep, { SleepGestureHandler(it) })

    @Serializable
    @SerialName("openNotifications")
    object OpenNotifications : Simple(R.string.gesture_handler_open_notifications, { OpenNotificationsHandler(it) })

    @Serializable
    @SerialName("openApp")
    data class OpenApp(val appName: String, val target: OpenAppTarget) : GestureHandlerConfig() {
        override fun getLabel(context: Context) = context.getString(R.string.gesture_handler_open_app_config, appName)
        override fun createHandler(context: Context) = OpenAppGestureHandler(context, target)
    }
}
