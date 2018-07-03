package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.support.annotation.Keep
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R
import com.android.launcher3.Utilities
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException

private val suThread = HandlerThread("su").apply { start() }
private val suHandler = Handler(suThread.looper)

@Keep
class SleepGestureHandlerRoot(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_sleep_root)!!

    private var currentSession: Session? = null
    private val destroy = Runnable { currentSession?.destroy() }
    private val sleep = Runnable {
        getSuSession().run {
            write("sendevent /dev/input/event1 1 116 1\n")
            write("sendevent /dev/input/event1 0 0 0\n")
            write("sendevent /dev/input/event1 1 116 0\n")
            write("sendevent /dev/input/event1 0 0 0\n")
        }
    }

    private fun getSuSession(): Session {
        if (currentSession == null || !currentSession!!.isAlive) {
            currentSession = Session()
        }
        return currentSession!!
    }

    override fun onDestroy() {
        super.onDestroy()
        suHandler.post(destroy)
    }

    override fun onGestureTrigger(controller: GestureController) {
        suHandler.post(sleep)
    }

    class Session {

        private val process = Runtime.getRuntime().exec("su")!!
        private val outputStream = DataOutputStream(process.outputStream)

        val isAlive get() = isAlive(process)

        fun write(string: String) {
            try {
                outputStream.writeBytes(string)
                outputStream.flush()
            } catch (e: IOException) {
            } catch (e: InterruptedException) {
            }
        }

        fun destroy() {
            try {
                if (isAlive) {
                    outputStream.writeBytes("exit\n")
                    outputStream.flush()
                    outputStream.close()
                }
                process.waitFor()
                process.destroy()
            } catch (e: IOException) {
            } catch (e: InterruptedException) {
            }
        }

        private fun isAlive(process: Process?): Boolean {
            if (process == null) return false
            return try {
                process.exitValue()
                false
            } catch (e: IllegalThreadStateException) {
                true
            }
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
