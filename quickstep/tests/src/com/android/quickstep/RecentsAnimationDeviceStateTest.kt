package com.android.quickstep

import android.content.Context
import android.testing.AndroidTestingRunner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.launcher3.util.DisplayController.CHANGE_DENSITY
import com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE
import com.android.launcher3.util.DisplayController.CHANGE_ROTATION
import com.android.launcher3.util.DisplayController.Info
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.quickstep.util.GestureExclusionManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever

/** Unit test for [RecentsAnimationDeviceState]. */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class RecentsAnimationDeviceStateTest {

    @Mock private lateinit var exclusionManager: GestureExclusionManager
    @Mock private lateinit var windowManagerProxy: WindowManagerProxy
    @Mock private lateinit var info: Info

    private val context = ApplicationProvider.getApplicationContext() as Context
    private lateinit var underTest: RecentsAnimationDeviceState

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = RecentsAnimationDeviceState(context, exclusionManager)
    }

    @Test
    fun registerExclusionListener_success() {
        underTest.registerExclusionListener()

        verify(exclusionManager).addListener(underTest)
    }

    @Test
    fun registerExclusionListener_again_fail() {
        underTest.registerExclusionListener()
        reset(exclusionManager)

        underTest.registerExclusionListener()

        verifyZeroInteractions(exclusionManager)
    }

    @Test
    fun unregisterExclusionListener_success() {
        underTest.registerExclusionListener()
        reset(exclusionManager)

        underTest.unregisterExclusionListener()

        verify(exclusionManager).removeListener(underTest)
    }

    @Test
    fun unregisterExclusionListener_again_fail() {
        underTest.registerExclusionListener()
        underTest.unregisterExclusionListener()
        reset(exclusionManager)

        underTest.unregisterExclusionListener()

        verifyZeroInteractions(exclusionManager)
    }

    @Test
    fun onDisplayInfoChanged_noButton_registerExclusionListener() {
        doReturn(NavigationMode.NO_BUTTON).whenever(info).getNavigationMode()

        underTest.onDisplayInfoChanged(context, info, CHANGE_ROTATION or CHANGE_NAVIGATION_MODE)

        verify(exclusionManager).addListener(underTest)
    }

    @Test
    fun onDisplayInfoChanged_twoButton_unregisterExclusionListener() {
        underTest.registerExclusionListener()
        whenever(info.getNavigationMode()).thenReturn(NavigationMode.TWO_BUTTONS)
        reset(exclusionManager)

        underTest.onDisplayInfoChanged(context, info, CHANGE_ROTATION or CHANGE_NAVIGATION_MODE)

        verify(exclusionManager).removeListener(underTest)
    }

    @Test
    fun onDisplayInfoChanged_changeDensity_noOp() {
        underTest.registerExclusionListener()
        whenever(info.getNavigationMode()).thenReturn(NavigationMode.NO_BUTTON)
        reset(exclusionManager)

        underTest.onDisplayInfoChanged(context, info, CHANGE_DENSITY)

        verifyZeroInteractions(exclusionManager)
    }
}
