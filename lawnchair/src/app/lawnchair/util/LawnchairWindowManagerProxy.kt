package app.lawnchair.util

import android.content.Context
import android.util.ArrayMap
import android.view.Display.DEFAULT_DISPLAY
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.Keep
import app.lawnchair.LawnchairApp
import com.android.internal.policy.SystemBarUtils
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy

@Keep
class LawnchairWindowManagerProxy(context: Context) : WindowManagerProxy(true) {

    override fun getRotation(displayInfoContext: Context): Int {
        if (LawnchairApp.isAtleastT) {
            return displayInfoContext.resources.configuration.windowConfiguration.rotation
        }
        return super.getRotation(displayInfoContext)
    }

    override fun getStatusBarHeight(context: Context, isPortrait: Boolean, statusBarInset: Int): Int {
        if (LawnchairApp.isAtleastT) {
            return SystemBarUtils.getStatusBarHeight(context)
        }
        return super.getStatusBarHeight(context, isPortrait, statusBarInset)
    }

    override fun estimateInternalDisplayBounds(displayInfoContext: Context): ArrayMap<CachedDisplayInfo, Array<WindowBounds>> {
        if (LawnchairApp.isAtleastT) {
            val result = ArrayMap<CachedDisplayInfo, Array<WindowBounds>>()
            val windowManager = displayInfoContext.getSystemService(WindowManager::class.java)
            val possibleMaximumWindowMetrics =
                windowManager.getPossibleMaximumWindowMetrics(DEFAULT_DISPLAY)
            for (windowMetrics in possibleMaximumWindowMetrics) {
                val info = getDisplayInfo(windowMetrics, Surface.ROTATION_0)
                val bounds = estimateWindowBounds(displayInfoContext, info)
                result[info] = bounds
            }
            return result
        }
        return super.estimateInternalDisplayBounds(displayInfoContext)
    }
}
