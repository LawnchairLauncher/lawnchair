package ch.deletescape.lawnchair.gestures

import android.content.Context
import android.text.TextUtils
import android.view.MotionEvent
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.gestures.DoubleTapGesture
import ch.deletescape.lawnchair.gestures.gestures.PressHomeGesture
import ch.deletescape.lawnchair.gestures.gestures.SwipeDownGesture
import ch.deletescape.lawnchair.gestures.gestures.SwipeUpGesture
import com.android.launcher3.Utilities
import com.android.launcher3.util.TouchController
import org.json.JSONException
import org.json.JSONObject

class GestureController(val launcher: LawnchairLauncher) : TouchController {

    private val prefs = Utilities.getLawnchairPrefs(launcher)
    private val blankGestureHandler = BlankGestureHandler(launcher)
    private val doubleTapGesture = DoubleTapGesture(this)
    private val pressHomeGesture = PressHomeGesture(this)
    private val swipeDownGesture = SwipeDownGesture(this)
    private val swipeUpGesture = SwipeUpGesture(this)

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    fun onBlankAreaTouch(ev: MotionEvent) {
        doubleTapGesture.isEnabled && doubleTapGesture.onTouchEvent(ev)
    }

    fun onPressHome() {
        pressHomeGesture.isEnabled && pressHomeGesture.onEvent()
    }

    fun onSwipeDown() {
        swipeDownGesture.isEnabled && swipeDownGesture.onEvent()
    }

    fun onSwipeUp(): Boolean {
        return swipeUpGesture.isEnabled && swipeUpGesture.onEvent()
    }

    fun hasCustomSwipeUp():Boolean {
        return swipeUpGesture.isEnabled && swipeUpGesture.isCustom
    }

    fun createHandlerPref(key: String, defaultValue: GestureHandler = blankGestureHandler) = prefs.StringBasedPref(
            key, defaultValue, prefs.doNothing, ::createGestureHandler, GestureHandler::toString)

    private fun createGestureHandler(jsonString: String) = createGestureHandler(launcher, jsonString, blankGestureHandler)

    companion object {

        private fun createGestureHandler(context: Context, jsonString: String, fallback: GestureHandler): GestureHandler {
            if (!TextUtils.isEmpty(jsonString)) {
                val config: JSONObject? = try {
                    JSONObject(jsonString)
                } catch (e: JSONException) {
                    null
                }
                val className = config?.getString("class") ?: jsonString
                try {
                    return Class.forName(className).getConstructor(Context::class.java, JSONObject::class.java)
                            .newInstance(context, config?.getJSONObject("config")) as GestureHandler
                } catch (t: Throwable) {

                }
            }
            return fallback
        }
    }
}
