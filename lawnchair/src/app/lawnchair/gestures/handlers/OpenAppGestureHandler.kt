package app.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairLauncher
import app.lawnchair.util.ComponentKeySerializer
import app.lawnchair.util.IntentSerializer
import app.lawnchair.util.UserHandlerSerializer
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OpenAppGestureHandler(
    context: Context,
    private val target: OpenAppTarget,
) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        when (target) {
            is OpenAppTarget.App -> {
                val key = target.key
                launcher.getSystemService<LauncherApps>()?.startMainActivity(
                    key.componentName,
                    key.user,
                    null,
                    null,
                )
            }

            is OpenAppTarget.Shortcut -> {
                // LC: start the chosen app shortcut (e.g. a specific chat). Only the
                // default launcher may do this, which Lawnchair is while a gesture fires.
                try {
                    launcher.getSystemService<LauncherApps>()?.startShortcut(
                        target.packageName,
                        target.id,
                        null,
                        null,
                        target.user,
                    )
                } catch (e: Exception) {
                    Log.w("OpenAppGestureHandler", "Could not start shortcut ${target.id}", e)
                    Toast.makeText(launcher, R.string.gesture_shortcut_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Serializable
sealed class OpenAppTarget {
    @Serializable
    @SerialName("app")
    data class App(
        @Serializable(ComponentKeySerializer::class) val key: ComponentKey,
    ) : OpenAppTarget()

    @Serializable
    @SerialName("shortcut")
    data class Shortcut(
        @Serializable(IntentSerializer::class) val intent: Intent,
        @Serializable(UserHandlerSerializer::class) val user: UserHandle,
        val packageName: String,
        val id: String,
    ) : OpenAppTarget()
}
