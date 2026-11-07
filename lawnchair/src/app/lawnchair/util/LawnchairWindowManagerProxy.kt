package app.lawnchair.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.ArrayMap
import android.util.Log
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayCutout
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.Keep
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.RotationUtils.deltaRotation
import com.android.launcher3.util.RotationUtils.rotateRect
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Keep
@LauncherAppSingleton
class LawnchairWindowManagerProxy @Inject constructor() : WindowManagerProxy(Utilities.ATLEAST_T) {

    @Suppress("PropertyName")
    val TAG = "LC-WindowManagerProxy"

    override fun estimateInternalDisplayBounds(displayInfoContext: Context): ArrayMap<CachedDisplayInfo, List<WindowBounds>> {
        val result = ArrayMap<CachedDisplayInfo, List<WindowBounds>>()
        val displayManager = displayInfoContext.getSystemService(DisplayManager::class.java) ?: return result

        val displays = displayManager.displays

        for (display in displays) {
            try {
                val contextForDisplay = displayInfoContext.createDisplayContext(display)
                val wm = contextForDisplay.getSystemService(WindowManager::class.java)
                val metrics = if (Utilities.ATLEAST_R) wm.maximumWindowMetrics else null

                if (metrics != null) {
                    val info = getDisplayInfo(metrics, display.rotation).normalize(this)
                    val bounds = estimateWindowBounds(contextForDisplay, info)

                    result[info] = bounds
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error estimating bounds for display ${display.displayId}", e)
            }
        }

        // LC-Note: DisplayManager only reports the panel in use, so a foldable looked like a phone
        // when folded and like a tablet when unfolded, and every fold reloaded the grid. On devices
        // that can fold, add the other states of the internal display, like
        // SystemWindowManagerProxy does, so the device type is the same in all of them.
        for (info in getInternalDisplayStates(displayInfoContext)) {
            if (info !in result) {
                result[info] = estimateWindowBounds(displayInfoContext, info)
            }
        }

        return result
    }

    /**
     * Returns the known states of the internal display of a foldable (e.g. both panels), as the
     * keys that DisplayController.Info looks up for them.
     */
    private fun getInternalDisplayStates(context: Context): List<CachedDisplayInfo> {
        if (!isFoldable(context) || getDisplay(context)?.displayId != DEFAULT_DISPLAY) return emptyList()
        // Built like DisplayController.Info#normalizedDisplayInfo, so that it is found in the cache
        val current = getDisplayInfo(context).normalize(this)

        // The system knows all of them, but only tells the recents provider. They come from the
        // physical displays, so they can differ from the keys in use (e.g. with a display size
        // override): only use them when the current state is one of them.
        val possibleStates = getPossibleMaximumWindowMetrics(context)
            .map { getDisplayInfo(it, Surface.ROTATION_0) }
        if (possibleStates.size > 1 && current in possibleStates) return possibleStates

        // Otherwise remember the ones seen so far.
        return rememberInternalDisplayState(context, current)
    }

    private fun getPossibleMaximumWindowMetrics(context: Context): Set<WindowMetrics> {
        if (!Utilities.ATLEAST_T) return emptySet()
        return try {
            context.getSystemService(WindowManager::class.java)
                ?.getPossibleMaximumWindowMetrics(DEFAULT_DISPLAY)
                .orEmpty()
        } catch (t: Throwable) {
            // Hidden API, not always reachable
            Log.w(TAG, "getPossibleMaximumWindowMetrics unavailable: $t")
            emptySet()
        }
    }

    /**
     * Adds [current] to the states of the internal display seen on this device, and returns them all,
     * most recent first. They are kept in device protected storage, as this runs before the user
     * unlocks the device when Lawnchair is the recents provider, and are not backed up.
     */
    @Synchronized
    private fun rememberInternalDisplayState(context: Context, current: CachedDisplayInfo): List<CachedDisplayInfo> {
        // Its own file: LauncherPrefs listeners (DisplayController registers one in its constructor and
        // reads its Info from it) would fire on a write made while DisplayController is being built.
        val prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(DISPLAY_STATES_PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_INTERNAL_DISPLAY_STATES, null)
        val savedStates = saved?.split(STATE_SEPARATOR)?.mapNotNull { parseDisplayState(it) }.orEmpty()

        // A state with the same aspect ratio is the same panel at another resolution (or the same
        // state), so it is replaced.
        val states = (listOf(current) + savedStates.filterNot { hasSameAspectRatio(it, current) })
            .take(MAX_INTERNAL_DISPLAY_STATES)
        val serialized = states.joinToString(STATE_SEPARATOR) { serializeDisplayState(it) }
        if (serialized != saved) {
            prefs.edit().putString(KEY_INTERNAL_DISPLAY_STATES, serialized).apply()
        }
        return states
    }

    private fun hasSameAspectRatio(a: CachedDisplayInfo, b: CachedDisplayInfo): Boolean {
        fun CachedDisplayInfo.ratio() = min(size.x, size.y).toFloat() / max(size.x, size.y)
        return abs(a.ratio() - b.ratio()) < ASPECT_RATIO_TOLERANCE
    }

    private fun serializeDisplayState(info: CachedDisplayInfo): String {
        val cutout = getSafeInsets(info.cutout)
        return "${info.size.x},${info.size.y},${cutout.left},${cutout.top},${cutout.right},${cutout.bottom}"
    }

    private fun parseDisplayState(value: String): CachedDisplayInfo? {
        val values = value.split(",").map { it.toIntOrNull() ?: return null }
        if (values.size != 6 || values[0] <= 0 || values[1] <= 0) return null
        // Same cutout as getDisplayInfo(WindowMetrics, Int) and rotateCutout() produce
        val insets = Insets.of(values[2], values[3], values[4], values[5])
        val cutout = if (insets == Insets.NONE) null else DisplayCutout(insets, null, null, null, null)
        return CachedDisplayInfo(Point(values[0], values[1]), Surface.ROTATION_0, cutout)
    }

    override fun getRealBounds(displayInfoContext: Context, info: CachedDisplayInfo): WindowBounds {
        if (Utilities.ATLEAST_R) {
            val windowMetrics = displayInfoContext.getSystemService(WindowManager::class.java)?.maximumWindowMetrics
            if (windowMetrics != null) {
                val insets = Rect()
                normalizeWindowInsets(displayInfoContext, windowMetrics.windowInsets, insets)
                return WindowBounds(windowMetrics.bounds, insets, info.rotation)
            }
        }

        val estimateBounds = estimateWindowBounds(displayInfoContext, info)
        return estimateBounds.getOrNull(info.rotation)
            ?: estimateBounds.first()
    }

    override fun normalizeWindowInsets(context: Context, oldInsets: WindowInsets, outInsets: Rect): WindowInsets {
        if (!Utilities.ATLEAST_R || !mTaskbarDrawnInProcess) {
            outInsets.set(
                oldInsets.systemWindowInsetLeft,
                oldInsets.systemWindowInsetTop,
                oldInsets.systemWindowInsetRight,
                oldInsets.systemWindowInsetBottom,
            )
            return oldInsets
        }

        val insetsBuilder = WindowInsets.Builder(oldInsets)
        val navInsets = oldInsets.getInsets(WindowInsets.Type.navigationBars())

        val systemRes = context.resources
        val config = systemRes.configuration

        val isLargeScreen = config.smallestScreenWidthDp > MIN_TABLET_WIDTH
        val isGesture = isGestureNav(context)
        val isPortrait = config.screenHeightDp > config.screenWidthDp

        val bottomNav = if (isLargeScreen) {
            0
        } else if (isPortrait) {
            getDimenByName(systemRes, ResourceUtils.NAVBAR_HEIGHT)
        } else if (isGesture) {
            getDimenByName(systemRes, ResourceUtils.NAVBAR_HEIGHT_LANDSCAPE)
        } else {
            0
        }
        var leftNav = navInsets.left
        var rightNav = navInsets.right
        if (!isLargeScreen && !isGesture && !isPortrait) {
            val navBarWidth = getDimenByName(systemRes, ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE)
            when (getRotation(context)) {
                Surface.ROTATION_90 -> rightNav = navBarWidth
                Surface.ROTATION_270 -> leftNav = navBarWidth
            }
        }
        val newNavInsets = Insets.of(leftNav, navInsets.top, rightNav, bottomNav)
        insetsBuilder.setInsets(WindowInsets.Type.navigationBars(), newNavInsets)
        insetsBuilder.setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), newNavInsets)

        val statusBarInsets = oldInsets.getInsets(WindowInsets.Type.statusBars())
        val newStatusBarInsets = Insets.of(
            statusBarInsets.left,
            getStatusBarHeight(context, isPortrait, statusBarInsets.top),
            statusBarInsets.right,
            statusBarInsets.bottom,
        )
        insetsBuilder.setInsets(WindowInsets.Type.statusBars(), newStatusBarInsets)
        insetsBuilder.setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), newStatusBarInsets)

        if (isGesture) {
            val oldTappableInsets = oldInsets.getInsets(WindowInsets.Type.tappableElement())
            val newTappableInsets = Insets.of(oldTappableInsets.left, oldTappableInsets.top, oldTappableInsets.right, 0)
            insetsBuilder.setInsets(WindowInsets.Type.tappableElement(), newTappableInsets)
        }

        applyDisplayCutoutBottomInsetOverrideOnLargeScreen(
            context,
            isLargeScreen,
            Utilities.dpToPx(
                config.screenWidthDp.toFloat(),
            ),
            oldInsets,
            insetsBuilder,
        )

        val result = insetsBuilder.build()
        val systemWindowInsets = result.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        outInsets.set(systemWindowInsets.left, systemWindowInsets.top, systemWindowInsets.right, systemWindowInsets.bottom)
        return result
    }

    override fun rotateCutout(
        original: DisplayCutout?,
        startWidth: Int,
        startHeight: Int,
        fromRotation: Int,
        toRotation: Int,
    ): DisplayCutout? {
        val safeCutout = getSafeInsets(original)
        rotateRect(safeCutout, deltaRotation(fromRotation, toRotation))

        return if (Utilities.ATLEAST_Q) {
            DisplayCutout(Insets.of(safeCutout), null, null, null, null)
        } else {
            null
        }
    }

    override fun applyDisplayCutoutBottomInsetOverrideOnLargeScreen(
        context: Context,
        isLargeScreen: Boolean,
        screenWidthPx: Int,
        windowInsets: WindowInsets,
        insetsBuilder: WindowInsets.Builder,
    ) {
        if (!isLargeScreen || !Utilities.ATLEAST_S) return

        val displayCutout = windowInsets.displayCutout ?: return
        if (!areBottomDisplayCutoutsSmallAndAtCorners(displayCutout.boundingRectBottom, screenWidthPx, context.resources)) return

        val oldDisplayCutoutInset = windowInsets.getInsets(WindowInsets.Type.displayCutout())
        val newDisplayCutoutInset = Insets.of(oldDisplayCutoutInset.left, oldDisplayCutoutInset.top, oldDisplayCutoutInset.right, 0)
        insetsBuilder.setInsetsIgnoringVisibility(WindowInsets.Type.displayCutout(), newDisplayCutoutInset)
    }

    @VisibleForTesting
    fun areBottomDisplayCutoutsSmallAndAtCorners(
        cutoutRectBottom: Rect,
        screenWidthPx: Int,
        resources: Resources,
    ): Boolean {
        if (cutoutRectBottom.isEmpty) return false
        val maxCutoutSizePx = dpToPx(resources, 32)
        return cutoutRectBottom.right <= maxCutoutSizePx || cutoutRectBottom.left >= (screenWidthPx - maxCutoutSizePx)
    }

    override fun getStatusBarHeight(context: Context, isPortrait: Boolean, statusBarInset: Int): Int {
        val systemRes = context.resources
        val statusBarHeight = getDimenByName(systemRes, if (isPortrait) ResourceUtils.STATUS_BAR_HEIGHT_PORTRAIT else ResourceUtils.STATUS_BAR_HEIGHT_LANDSCAPE, ResourceUtils.STATUS_BAR_HEIGHT)
        return max(statusBarInset, statusBarHeight)
    }

    private fun getDimenByName(resources: Resources, vararg dimenNames: String): Int {
        for (name in dimenNames) {
            val resId = resources.getIdentifier(name, "dimen", "android")
            if (resId > 0) return resources.getDimensionPixelSize(resId)
        }
        return 0
    }

    override fun getDisplay(displayInfoContext: Context): Display? {
        return try {
            if (Utilities.ATLEAST_R) {
                displayInfoContext.display
            } else {
                displayInfoContext.getSystemService(DisplayManager::class.java)?.getDisplay(
                    DEFAULT_DISPLAY,
                )
            }
        } catch (e: UnsupportedOperationException) {
            displayInfoContext.getSystemService(DisplayManager::class.java)?.getDisplay(
                DEFAULT_DISPLAY,
            )
        }
    }

    override fun getRotation(context: Context): Int {
        val display = context.getSystemService(WindowManager::class.java)?.defaultDisplay
        return display?.rotation ?: Surface.ROTATION_0
    }

    private fun dpToPx(resources: Resources, dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        /** Whether this device folds: the single check, for DisplayController too. */
        @JvmStatic
        fun isFoldable(context: Context): Boolean = Utilities.ATLEAST_R &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

        private const val DISPLAY_STATES_PREFS = "lc_display_states"
        private const val KEY_INTERNAL_DISPLAY_STATES = "lc_internal_display_states"
        private const val STATE_SEPARATOR = ";"
        private const val MAX_INTERNAL_DISPLAY_STATES = 4
        private const val ASPECT_RATIO_TOLERANCE = 0.01f
    }
}
