package com.android.quickstep

import android.content.Context
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.IWindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.launcher3.util.DisplayController.CHANGE_DENSITY
import com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE
import com.android.launcher3.util.DisplayController.CHANGE_ROTATION
import com.android.launcher3.util.DisplayController.Info
import com.android.launcher3.util.Executors
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.window.WindowManagerProxy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever

/** Unit test for [RecentsAnimationDeviceState]. */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class RecentsAnimationDeviceStateTest {

    @Mock private lateinit var windowManager: IWindowManager
    @Mock private lateinit var windowManagerProxy: WindowManagerProxy
    @Mock private lateinit var info: Info

    private val context = ApplicationProvider.getApplicationContext() as Context
    private lateinit var underTest: RecentsAnimationDeviceState

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = RecentsAnimationDeviceState(context, windowManager)
    }

    @Test
    fun registerExclusionListener_success() {
        underTest.registerExclusionListener()

        awaitTasksCompleted()
        verify(windowManager)
            .registerSystemGestureExclusionListener(
                underTest.mGestureExclusionListener,
                Display.DEFAULT_DISPLAY
            )
    }

    @Test
    fun registerExclusionListener_again_fail() {
        underTest.registerExclusionListener()
        awaitTasksCompleted()
        reset(windowManager)

        underTest.registerExclusionListener()

        awaitTasksCompleted()
        verifyZeroInteractions(windowManager)
    }

    @Test
    fun unregisterExclusionListener_success() {
        underTest.registerExclusionListener()
        awaitTasksCompleted()
        reset(windowManager)

        underTest.unregisterExclusionListener()

        awaitTasksCompleted()
        verify(windowManager)
            .unregisterSystemGestureExclusionListener(
                underTest.mGestureExclusionListener,
                Display.DEFAULT_DISPLAY
            )
    }

    @Test
    fun unregisterExclusionListener_again_fail() {
        underTest.registerExclusionListener()
        underTest.unregisterExclusionListener()
        awaitTasksCompleted()
        reset(windowManager)

        underTest.unregisterExclusionListener()

        awaitTasksCompleted()
        verifyZeroInteractions(windowManager)
    }

    @Test
    fun onDisplayInfoChanged_noButton_registerExclusionListener() {
        whenever(windowManagerProxy.getNavigationMode(context)).thenReturn(NavigationMode.NO_BUTTON)

        underTest.onDisplayInfoChanged(context, info, CHANGE_ROTATION or CHANGE_NAVIGATION_MODE)

        awaitTasksCompleted()
        verify(windowManager)
            .registerSystemGestureExclusionListener(
                underTest.mGestureExclusionListener,
                Display.DEFAULT_DISPLAY
            )
    }

    @Test
    fun onDisplayInfoChanged_twoButton_unregisterExclusionListener() {
        underTest.registerExclusionListener()
        awaitTasksCompleted()
        whenever(info.getNavigationMode()).thenReturn(NavigationMode.TWO_BUTTONS)
        reset(windowManager)

        underTest.onDisplayInfoChanged(context, info, CHANGE_ROTATION or CHANGE_NAVIGATION_MODE)

        awaitTasksCompleted()
        verify(windowManager)
            .unregisterSystemGestureExclusionListener(
                underTest.mGestureExclusionListener,
                Display.DEFAULT_DISPLAY
            )
    }

    @Test
    fun onDisplayInfoChanged_changeDensity_noOp() {
        underTest.registerExclusionListener()
        awaitTasksCompleted()
        whenever(info.getNavigationMode()).thenReturn(NavigationMode.NO_BUTTON)
        reset(windowManager)

        underTest.onDisplayInfoChanged(context, info, CHANGE_DENSITY)

        awaitTasksCompleted()
        verifyZeroInteractions(windowManager)
    }

    private fun awaitTasksCompleted() {
        Executors.UI_HELPER_EXECUTOR.submit<Any> { null }.get()
    }
}
