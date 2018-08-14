package ch.deletescape.lawnchair.gestures

import android.content.Context
import android.graphics.PointF
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.gestures.*
import ch.deletescape.lawnchair.gestures.handlers.*
import com.android.launcher3.Utilities
import com.android.launcher3.util.TouchController
import org.json.JSONException
import org.json.JSONObject

class GestureController(val launcher: LawnchairLauncher) : TouchController {

    private val prefs = Utilities.getLawnchairPrefs(launcher)
    private val blankGestureHandler = BlankGestureHandler(launcher, null)
    private val doubleTapGesture = DoubleTapGesture(this)
    private val pressHomeGesture = PressHomeGesture(this)
    private val pressBackGesture = PressBackGesture(this)
    private val longPressGesture = LongPressGesture(this)

    val verticalSwipeGesture = VerticalSwipeGesture(this)

    var touchDownPoint = PointF()

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    fun onBlankAreaTouch(ev: MotionEvent): Boolean {
        return doubleTapGesture.isEnabled && doubleTapGesture.onTouchEvent(ev)
    }

    fun onLongPress() {
        longPressGesture.isEnabled && longPressGesture.onEvent()
    }

    fun onPressHome() {
        pressHomeGesture.isEnabled && pressHomeGesture.onEvent()
    }

    fun onPressBack() {
        pressBackGesture.isEnabled && pressBackGesture.onEvent()
    }

    fun createHandlerPref(key: String, defaultValue: GestureHandler = blankGestureHandler) = prefs.StringBasedPref(
            key, defaultValue, prefs.doNothing, ::createGestureHandler, GestureHandler::toString, GestureHandler::onDestroy)

    private fun createGestureHandler(jsonString: String) = createGestureHandler(launcher, jsonString, blankGestureHandler)

    companion object {

        private const val TAG = "GestureController"

        fun createGestureHandler(context: Context, jsonString: String, fallback: GestureHandler): GestureHandler {
            if (!TextUtils.isEmpty(jsonString)) {
                val config: JSONObject? = try {
                    JSONObject(jsonString)
                } catch (e: JSONException) {
                    null
                }
                val className = config?.getString("class") ?: jsonString
                val configValue = if (config?.has("config") == true) config.getJSONObject("config") else null
                // Log.d(TAG, "creating handler $className with config ${configValue?.toString(2)}")
                try {
                    return Class.forName(className).getConstructor(Context::class.java, JSONObject::class.java)
                            .newInstance(context, configValue) as GestureHandler
                } catch (t: Throwable) {
                    Log.e(TAG, "can't create gesture handler", t)
                }
            }
            return fallback
        }

        fun getClassName(jsonString: String): String {
            val config: JSONObject? = try {
                JSONObject(jsonString)
            } catch (e: JSONException) {
                null
            }
            return config?.getString("class") ?: jsonString
        }

        fun getGestureHandlers(context: Context) = listOf(
                BlankGestureHandler(context, null),
                SleepGestureHandlerTimeout(context, null),
                SleepGestureHandlerRoot(context, null),
                SleepGestureHandlerDeviceAdmin(context, null),
                OpenDrawerGestureHandler(context, null),
                OpenWidgetsGestureHandler(context, null),
                OpenSettingsGestureHandler(context, null),
                OpenOverviewGestureHandler(context, null),
                StartGlobalSearchGestureHandler(context, null),
                StartAppSearchGestureHandler(context, null),
                NotificationsOpenGestureHandler(context, null),
                OpenOverlayGestureHandler(context, null),
                StartAppGestureHandler(context, null)
        )
    }
}
