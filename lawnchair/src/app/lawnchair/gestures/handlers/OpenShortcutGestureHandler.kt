package app.lawnchair.gestures.handlers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairLauncher
import app.lawnchair.util.ComponentKeySerializer
import app.lawnchair.util.UserHandlerSerializer
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import kotlinx.serialization.Serializable

class OpenShortcutGestureHandler(
    context: Context,
    private val target: OpenShortcutTarget,
) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val launcherApps = launcher.getSystemService<LauncherApps>()
        if (launcherApps == null) {
            showShortcutUnavailable(IllegalStateException("LauncherApps unavailable"))
            return
        }
        try {
            launcherApps.startShortcut(
                target.packageName,
                target.id,
                null,
                null,
                target.user,
            )
        } catch (e: ActivityNotFoundException) {
            showShortcutUnavailable(e)
        } catch (e: IllegalArgumentException) {
            showShortcutUnavailable(e)
        } catch (e: IllegalStateException) {
            showShortcutUnavailable(e)
        } catch (e: SecurityException) {
            showShortcutUnavailable(e)
        }
    }

    private fun showShortcutUnavailable(error: Exception) {
        Log.w(TAG, "Unable to launch app shortcut", error)
        Toast.makeText(context, R.string.shortcut_not_available, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "OpenShortcutGestureHandler"
    }
}

@Serializable
data class OpenShortcutTarget(
    @Serializable(ComponentKeySerializer::class) val app: ComponentKey? = null,
    @Serializable(UserHandlerSerializer::class) val user: UserHandle,
    val packageName: String,
    val id: String,
)
