package app.lawnchair.gestures.config

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import app.lawnchair.gestures.handlers.GestureHandler
import app.lawnchair.gestures.handlers.NoOpGestureHandler
import app.lawnchair.gestures.handlers.OpenAppDrawerGestureHandler
import app.lawnchair.gestures.handlers.OpenAppGestureHandler
import app.lawnchair.gestures.handlers.OpenAppSearchGestureHandler
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.gestures.handlers.OpenAssistantHandler
import app.lawnchair.gestures.handlers.OpenNotificationsHandler
import app.lawnchair.gestures.handlers.OpenQuickSettingsHandler
import app.lawnchair.gestures.handlers.OpenSearchGestureHandler
import app.lawnchair.gestures.handlers.RecentsGestureHandler
import app.lawnchair.gestures.handlers.SleepGestureHandler
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.AppFilter
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class GestureHandlerConfig {

    @get:DrawableRes
    open val iconRes: Int = R.drawable.ic_launcher_home

    abstract fun getIcon(context: Context): Icon
    abstract fun getLabel(context: Context): String
    abstract fun createHandler(context: Context): GestureHandler

    open fun getDisplayLabel(context: Context) = getLabel(context)
    open fun isExternallyInvokable(): Boolean = false

    @Serializable
    sealed class Simple(
        val labelRes: Int,
        @Transient private val creator: (Context) -> GestureHandler = {
            throw IllegalArgumentException("default creator not supported")
        },
    ) : GestureHandlerConfig() {
        override fun getIcon(context: Context): Icon {
            val drawable = AppCompatResources.getDrawable(context, iconRes)!!
            drawable.setTint(ColorTokens.ColorAccent.resolveColor(context))

            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            return Icon.createWithBitmap(bitmap)
        }

        override fun getLabel(context: Context) = context.getString(labelRes)
        override fun createHandler(context: Context) = creator(context)
    }

    @Serializable
    @SerialName("noOp")
    data object NoOp : Simple(R.string.gesture_handler_no_op, ::NoOpGestureHandler)

    @Serializable
    @SerialName("sleep")
    data object Sleep : Simple(R.string.gesture_handler_sleep, ::SleepGestureHandler)

    @Serializable
    @SerialName("recents")
    data object Recents : Simple(R.string.gesture_handler_recents, ::RecentsGestureHandler) {
        override val iconRes = R.drawable.ic_quickstep
    }

    @Serializable
    @SerialName("openNotificationdata")
    data object OpenNotifications :
        Simple(R.string.gesture_handler_open_notifications, ::OpenNotificationsHandler) {
        override val iconRes = R.drawable.ic_sysbar_notifications
    }

    @Serializable
    @SerialName("openQuickSettings")
    data object OpenQuickSettings :
        Simple(R.string.gesture_handler_open_quick_settings, ::OpenQuickSettingsHandler) {
        override val iconRes = R.drawable.ic_setting
    }

    @Serializable
    @SerialName("openAppDrawer")
    data object OpenAppDrawer :
        Simple(R.string.gesture_handler_open_app_drawer, ::OpenAppDrawerGestureHandler) {
        override val iconRes = R.drawable.ic_apps

        override fun isExternallyInvokable() = true
    }

    @Serializable
    @SerialName("openAppSearch")
    data object OpenAppSearch :
        Simple(R.string.gesture_handler_open_app_search, ::OpenAppSearchGestureHandler) {
        override val iconRes = R.drawable.ic_search

        override fun isExternallyInvokable() = true
    }

    @Serializable
    @SerialName("openSearch")
    data object OpenSearch :
        Simple(R.string.gesture_handler_open_search, ::OpenSearchGestureHandler) {
        override val iconRes = R.drawable.ic_search
    }

    @Serializable
    @SerialName("openAssistant")
    data object OpenAssistant :
        Simple(R.string.gesture_handler_open_assistant, ::OpenAssistantHandler) {
        override val iconRes = R.drawable.ic_mic_flat
    }

    @Serializable
    @SerialName("openApp")
    data class OpenApp(val appName: String, val target: OpenAppTarget) : GestureHandlerConfig() {
        override fun getIcon(context: Context): Icon {
            when (target) {
                is OpenAppTarget.Shortcut -> {
                    // fallback
                    return Icon.createWithResource(context, iconRes)
                }

                is OpenAppTarget.App -> {
                    val fallback = Icon.createWithResource(context, iconRes)

                    val filter = AppFilter(context)
                    val componentName = target.key.componentName
                    val user = target.key.user

                    if (!filter.shouldShowApp(componentName)) {
                        // Fallback to default icon
                        return fallback
                    }

                    val launcherApps = context.getSystemService(LauncherApps::class.java)

                    return runBlocking(MODEL_EXECUTOR.asCoroutineDispatcher()) {
                        val activityInfo = launcherApps.resolveActivity(
                            AppInfo.makeLaunchIntent(componentName),
                            target.key.user,
                        ) ?: return@runBlocking fallback

                        val appInfo = AppInfo(
                            context,
                            activityInfo,
                            user,
                        )

                        LauncherAppState.getInstance(context).iconCache.getTitleAndIcon(
                            appInfo,
                            DEFAULT_LOOKUP_FLAG,
                        )

                        Icon.createWithBitmap(appInfo.bitmap.icon)
                    }
                }
            }
        }

        override fun isExternallyInvokable() = target is OpenAppTarget.App

        override fun getDisplayLabel(context: Context) = appName
        override fun getLabel(context: Context) = context.getString(R.string.gesture_handler_open_app_config, appName)
        override fun createHandler(context: Context) = OpenAppGestureHandler(context, target)
    }

    companion object {
        fun toString(config: GestureHandlerConfig): String {
            return kotlinxJson.encodeToString(config)
        }

        fun fromString(string: String): GestureHandlerConfig {
            return try {
                kotlinxJson.decodeFromString<GestureHandlerConfig>(string)
            } catch (_: Exception) {
                NoOp
            }
        }
    }
}
