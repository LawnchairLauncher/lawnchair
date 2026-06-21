package app.lawnchair.gestures

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.type.GestureType
import app.lawnchair.launcher
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.VibratorWrapper
import kotlinx.coroutines.launch

class IconGestureListener(
    private val context: Context,
    private val prefs: PreferenceManager2,
    private val componentKey: ComponentKey?,
) : DirectionalGestureListener(context) {

    override fun onSwipeRight() = handleGesture(GestureType.SWIPE_RIGHT)
    override fun onSwipeLeft() = handleGesture(GestureType.SWIPE_LEFT)
    override fun onSwipeTop() = handleGesture(GestureType.SWIPE_UP)
    override fun onSwipeDown() = handleGesture(GestureType.SWIPE_DOWN)

    /** Check if there's any gesture configured for this entry */
    fun hasAnyGestureConfigured(): Boolean {
        return GestureType.entries.any(::hasGestureConfigured)
    }

    /** Check if there's a horizontal gesture configured for this entry. (Swipe left/right) */
    fun hasHorizontalGestureConfigured(): Boolean {
        return hasGestureConfigured(GestureType.SWIPE_LEFT) ||
            hasGestureConfigured(GestureType.SWIPE_RIGHT)
    }

    /** Check if there's a specific gesture configured for this entry
     * @param gestureType The type of gesture to check */
    private fun hasGestureConfigured(gestureType: GestureType): Boolean {
        return resolveGesture(gestureType) != null
    }

    /** Launch gesture configured operation for a specific gesture type
     * @param gestureType The type of gesture that triggered the event */
    private fun handleGesture(gestureType: GestureType): Boolean {
        val gesture = resolveGesture(gestureType) ?: return false

        Log.d("GESTURE_HANDLER", "Handling gesture: ${gestureType.name}")

        context.launcher.lifecycleScope.launch {
            Log.d("GESTURE_HANDLER", "Triggering gesture: ${gestureType.name}")
            // Lawnchair-TODO: Migrate to MSDL vibration?
            VibratorWrapper.INSTANCE.get(context.launcher).vibrate(VibratorWrapper.OVERVIEW_HAPTIC)
            gesture.createHandler(context).onTrigger(context.launcher)
        }

        return true
    }

    /** Get gesture handler for that app's component
     * @param gestureType The type of gesture to resolve for the current component */
    private fun resolveGesture(gestureType: GestureType): GestureHandlerConfig? {
        val currentComponentKey = componentKey ?: return null
        val gesture = prefs.getGestureForAppCached(currentComponentKey, gestureType)
        return gesture.takeUnless { it is GestureHandlerConfig.NoOp }
    }
}
