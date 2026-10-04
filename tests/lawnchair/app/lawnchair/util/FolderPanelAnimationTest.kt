package app.lawnchair.util

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class FolderPanelAnimationTest {
    @Test
    fun cancellingGrowRestoresVisibilityAndClippingWithoutScalingIcons() {
        val context = RuntimeEnvironment.getApplication()
        val panel = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(600, 800)
            layout(0, 0, 600, 800)
            pivotX = 300f
            pivotY = 750f
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        val icon = View(context)
        panel.addView(icon)
        val animation = FolderPanelAnimation.create(panel, 120, 24f, true)
        animation.start()
        animation.currentPlayTime = 80
        assertTrue(panel.clipToOutline)
        assertEquals(1f, panel.scaleX, 0f)
        assertEquals(1f, icon.scaleX, 0f)
        animation.cancel()
        assertFalse(panel.clipToOutline)
        assertSame(ViewOutlineProvider.BACKGROUND, panel.outlineProvider)
        assertEquals(1f, panel.alpha, 0f)

        // Closing after an interrupted open must also leave the view reusable.
        val closing = FolderPanelAnimation.create(panel, 120, 24f, false)
        closing.start()
        closing.end()
        assertFalse(panel.clipToOutline)
        assertEquals(1f, panel.alpha, 0f)
    }
}
