package ch.deletescape.lawnchair.gestures.handlers

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.support.annotation.Keep
import android.view.KeyEvent
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.Utilities
import java.io.DataOutputStream
import java.io.IOException

@Keep
class SleepGestureHandlerRoot(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    override fun onGestureTrigger() {
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
class SleepGestureHandlerTimeout(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    override fun onGestureTrigger() {
        if (!Utilities.ATLEAST_MARSHMALLOW || Settings.System.canWrite(launcher)) {
            launcher.startActivity(Intent(launcher, SleepTimeoutActivity::class.java))
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${launcher.packageName}")
            launcher.startActivity(intent)
        }
    }
}
