package app.lawnchair.fold

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.android.launcher3.util.DisplayController
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Posture of a foldable, from androidx.window's [FoldingFeature].
 *
 * The cover screen has no [FoldingFeature], and androidx.window does not say which half of the
 * inner screen carries the cover screen. So the hinge orientation at the natural display rotation
 * is learned from the first [FoldingFeature] reported, and kept while folded. Every book-style
 * foldable has its hinge on the left of the cover screen when held upright, so at the natural
 * rotation the fixed half is the right half ([HingeSide.LEFT]); upside down it is the left half.
 *
 * Values are only updated while a launcher activity is started; reads are safe from any thread.
 */
object FoldPosture {

    private const val TAG = "LC-FoldPosture"

    /** Whether the hinge is vertical at the natural display rotation, or null if never reported. */
    @Volatile
    private var naturalHingeVertical: Boolean? = null

    @Volatile
    private var rotation = Surface.ROTATION_0

    private val listeners = CopyOnWriteArrayList<Runnable>()

    /** Whether the device is half opened with a horizontal hinge (laptop-like posture). */
    @JvmStatic
    @Volatile
    var isTabletop = false
        private set

    /** Whether a hinge was reported by androidx.window in this process. */
    @JvmStatic
    val hasHinge: Boolean
        get() = naturalHingeVertical != null

    /** Hinge side for the last known display rotation. [HingeSide.UNKNOWN] if no hinge was seen. */
    @JvmStatic
    val hingeSide: HingeSide
        get() = getHingeSide(rotation, assumeBookStyle = false)

    /**
     * Returns the hinge side for the given display [rotation] (a `Surface.ROTATION_*` value).
     *
     * With [assumeBookStyle], a device whose hinge was not reported yet is treated as a book-style
     * foldable, whose hinge is vertical at the natural rotation. Use it only when the current layout
     * already shows two panels, for example right after unfolding, before androidx.window reports.
     */
    @JvmStatic
    @JvmOverloads
    fun getHingeSide(rotation: Int, assumeBookStyle: Boolean = false): HingeSide {
        val vertical = naturalHingeVertical ?: assumeBookStyle
        if (!vertical) {
            // Never reported, or a horizontal hinge at the natural rotation (flip phones).
            return HingeSide.UNKNOWN
        }
        return when (rotation) {
            Surface.ROTATION_0 -> HingeSide.LEFT

            Surface.ROTATION_180 -> HingeSide.RIGHT

            // The hinge is horizontal: there is no left or right half.
            else -> HingeSide.UNKNOWN
        }
    }

    /** Adds a listener called on the main thread when [hingeSide] or [isTabletop] may have changed. */
    @JvmStatic
    fun addListener(listener: Runnable) {
        listeners.addIfAbsent(listener)
    }

    @JvmStatic
    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    /**
     * Collects the window layout of [activity] between its onStart and onStop.
     *
     * This follows the activity callbacks rather than the androidx Lifecycle: the launcher calls
     * onSaveInstanceState on every configuration change, which moves its Lifecycle to CREATED
     * without a real onStop, and would cancel a repeatOnLifecycle(STARTED) collection.
     */
    @JvmStatic
    fun <T> attach(activity: T) where T : Activity, T : LifecycleOwner {
        activity.application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var job: Job? = null

                override fun onActivityStarted(a: Activity) {
                    if (a !== activity || job?.isActive == true) return
                    job = activity.lifecycleScope.launch {
                        WindowInfoTracker.getOrCreate(activity)
                            .windowLayoutInfo(activity)
                            .collect { update(activity, it) }
                    }
                }

                override fun onActivityStopped(a: Activity) {
                    if (a !== activity) return
                    job?.cancel()
                    job = null
                }

                override fun onActivityDestroyed(a: Activity) {
                    if (a !== activity) return
                    job?.cancel()
                    job = null
                    activity.application.unregisterActivityLifecycleCallbacks(this)
                }

                override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
            },
        )
    }

    @MainThread
    private fun update(activity: Activity, info: WindowLayoutInfo) {
        val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        val newRotation = DisplayController.INSTANCE.get(activity).info.rotation
        var newNaturalVertical = naturalHingeVertical
        if (feature != null) {
            val isVertical = feature.orientation == FoldingFeature.Orientation.VERTICAL
            val isRotated = newRotation == Surface.ROTATION_90 || newRotation == Surface.ROTATION_270
            newNaturalVertical = isVertical != isRotated
        }
        val newTabletop = feature != null &&
            feature.state == FoldingFeature.State.HALF_OPENED &&
            feature.orientation == FoldingFeature.Orientation.HORIZONTAL

        val changed = newNaturalVertical != naturalHingeVertical ||
            newRotation != rotation ||
            newTabletop != isTabletop
        naturalHingeVertical = newNaturalVertical
        rotation = newRotation
        isTabletop = newTabletop
        Log.d(TAG, "update: hingeSide=$hingeSide, isTabletop=$newTabletop, rotation=$newRotation, feature=$feature, changed=$changed")
        if (changed) {
            listeners.forEach(Runnable::run)
        }
    }
}
