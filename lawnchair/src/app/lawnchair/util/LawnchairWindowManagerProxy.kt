package app.lawnchair.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.ArrayMap
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayCutout
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.Keep
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Utilities
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.RotationUtils.deltaRotation
import com.android.launcher3.util.RotationUtils.rotateRect
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy
import kotlin.math.max

@Keep
class LawnchairWindowManagerProxy(context: Context) : WindowManagerProxy(Utilities.ATLEAST_T) {

    override fun estimateInternalDisplayBounds(displayInfoContext: Context): ArrayMap<CachedDisplayInfo, List<WindowBounds>> {
        val info = getDisplayInfo(displayInfoContext).normalize(this)
        val bounds = estimateWindowBounds(displayInfoContext, info)
        return ArrayMap<CachedDisplayInfo, List<WindowBounds>>().apply { put(info, bounds) }
    }

    override fun getRealBounds(displayInfoContext: Context, info: CachedDisplayInfo): WindowBounds {
        val windowMetrics = if (Utilities.ATLEAST_R) {
            displayInfoContext.getSystemService(WindowManager::class.java)?.maximumWindowMetrics
        } else {
            null
        }
        return if (windowMetrics != null) {
            val insets = Rect()
            normalizeWindowInsets(displayInfoContext, windowMetrics.windowInsets, insets)
            WindowBounds(windowMetrics.bounds, insets, info.rotation)
        } else {
            WindowBounds(Rect(), Rect(), info.rotation)
        }
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
}
