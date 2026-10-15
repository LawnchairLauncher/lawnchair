package app.lawnchair.util

import android.content.Context
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
import androidx.annotation.Keep
import androidx.annotation.VisibleForTesting
import app.lawnchair.LawnchairApp
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.dpiFromPx
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.RotationUtils.deltaRotation
import com.android.launcher3.util.RotationUtils.rotateRect
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@Keep
@LauncherAppSingleton
class LawnchairWindowManagerProxy @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : WindowManagerProxy(Utilities.ATLEAST_T) {

    @Suppress("PropertyName")
    val TAG = "LC-WindowManagerProxy"

    // LC-Note: mTaskbarDrawnInProcess stays true on Android 13+ for the inset overrides below, but
    // Lawnchair only draws a taskbar when it is the recents provider (quickstep runs). Otherwise the
    // system draws its taskbar (or navigation bar) on large screens, over our layout.
    private val taskbarDrawnInProcess by lazy {
        mTaskbarDrawnInProcess && LawnchairApp.isRecentsEnabled(appContext)
    }

    override fun isTaskbarDrawnInProcess() = taskbarDrawnInProcess

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

        return result
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
            // Leave room for the system taskbar unless ours is drawn there
            if (isTaskbarDrawnInProcess()) 0 else navInsets.bottom
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

    override fun estimateWindowBounds(context: Context, displayInfo: CachedDisplayInfo): List<WindowBounds> {
        val windowBounds = super.estimateWindowBounds(context, displayInfo)
        if (!mTaskbarDrawnInProcess || isTaskbarDrawnInProcess()) return windowBounds

        // LC-Note: super leaves no room at the bottom of large screens for a taskbar drawn in
        // process. Like normalizeWindowInsets, leave room for the one the system draws instead.
        val densityDpi = context.resources.configuration.densityDpi
        val swDp = dpiFromPx(min(displayInfo.size.x, displayInfo.size.y).toFloat(), densityDpi).toInt()
        if (swDp < MIN_TABLET_WIDTH) return windowBounds

        val taskbarHeight = getSystemTaskbarHeight(context)
        return windowBounds.map { bounds ->
            if (bounds.insets.bottom >= taskbarHeight) {
                bounds
            } else {
                WindowBounds(bounds.bounds, Rect(bounds.insets).apply { bottom = taskbarHeight }, bounds.rotationHint)
            }
        }
    }

    /**
     * Returns the bottom inset of the taskbar the system draws on large screens: the current one
     * when on a large screen (it stays at the bottom in every rotation), or else its usual height.
     */
    private fun getSystemTaskbarHeight(context: Context): Int {
        if (context.resources.configuration.smallestScreenWidthDp > MIN_TABLET_WIDTH) {
            val windowMetrics = context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics
            if (windowMetrics != null) {
                return windowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
            }
        }
        return getDimenByName(context.resources, "taskbar_frame_height")
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
}
