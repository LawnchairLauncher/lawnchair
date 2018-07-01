package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.support.annotation.Keep
import android.view.KeyEvent
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R
import com.android.launcher3.Utilities
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException

@Keep
class SleepGestureHandlerRoot(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_sleep_root)!!

    override fun onGestureTrigger(controller: GestureController) {
        try {
            val p = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(p.outputStream)
            outputStream.writeBytes("input keyevent ${KeyEvent.KEYCODE_POWER}\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            outputStream.close()
            p.waitFor()
            p.destroy()
        } catch (e: IOException) {
        } catch (e: InterruptedException) {
        }
    }
}

@Keep
class SleepGestureHandlerTimeout(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_sleep_timeout)!!

    override fun onGestureTrigger(controller: GestureController) {
        val launcher = controller.launcher
        if (!Utilities.ATLEAST_MARSHMALLOW || Settings.System.canWrite(launcher)) {
            launcher.startActivity(Intent(launcher, SleepTimeoutActivity::class.java))
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${launcher.packageName}")
            launcher.startActivity(intent)
        }
    }
}
