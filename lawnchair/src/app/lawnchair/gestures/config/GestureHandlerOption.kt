package app.lawnchair.gestures.config

import android.content.Context
import com.android.launcher3.R

abstract class GestureHandlerOption(
    val labelRes: Int,
    val configClass: Class<*>
) {

    fun getLabel(context: Context) = context.getString(labelRes)

    abstract suspend fun buildConfig(): GestureHandlerConfig

    open class Simple(labelRes: Int, val obj: GestureHandlerConfig) : GestureHandlerOption(labelRes, obj::class.java) {
        override suspend fun buildConfig() = obj
    }

    object NoOp : Simple(R.string.gesture_handler_no_op, GestureHandlerConfig.NoOp)
    object Sleep : Simple(R.string.gesture_handler_sleep, GestureHandlerConfig.Sleep)
    object OpenNotifications : Simple(R.string.gesture_handler_open_notifications, GestureHandlerConfig.OpenNotifications)
}
