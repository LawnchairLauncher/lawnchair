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
import com.android.quickstep.util.GestureExclusionManager
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DEVICE_DREAMING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DISABLE_GESTURE_SPLIT_INVOCATION
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_MAGNIFICATION_OVERLAP
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAV_BAR_HIDDEN
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_TOUCHPAD_GESTURES_DISABLED
import com.google.common.truth.Truth.assertThat
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

    @Test
    fun trackpadGesturesNotAllowedForSelectedStates() {
        val disablingStates = GESTURE_DISABLING_SYSUI_STATES +
                SYSUI_STATE_TOUCHPAD_GESTURES_DISABLED

        allSysUiStates().forEach { state ->
            val canStartGesture = !disablingStates.contains(state)
            underTest.setSystemUiFlags(state)
            assertThat(underTest.canStartTrackpadGesture()).isEqualTo(canStartGesture)
        }
    }

    @Test
    fun trackpadGesturesNotAllowedIfHomeAndOverviewIsDisabled() {
        val stateToExpectedResult = mapOf(
            SYSUI_STATE_HOME_DISABLED to true,
            SYSUI_STATE_OVERVIEW_DISABLED to true,
            DEFAULT_STATE
                .enable(SYSUI_STATE_OVERVIEW_DISABLED)
                .enable(SYSUI_STATE_HOME_DISABLED) to false
        )

        stateToExpectedResult.forEach { (state, allowed) ->
            underTest.setSystemUiFlags(state)
            assertThat(underTest.canStartTrackpadGesture()).isEqualTo(allowed)
        }
    }

    @Test
    fun systemGesturesNotAllowedForSelectedStates() {
        val disablingStates = GESTURE_DISABLING_SYSUI_STATES + SYSUI_STATE_NAV_BAR_HIDDEN

        allSysUiStates().forEach { state ->
            val canStartGesture = !disablingStates.contains(state)
            underTest.setSystemUiFlags(state)
            assertThat(underTest.canStartSystemGesture()).isEqualTo(canStartGesture)
        }
    }

    @Test
    fun systemGesturesNotAllowedWhenGestureStateDisabledAndNavBarVisible() {
        val stateToExpectedResult = mapOf(
            DEFAULT_STATE
                .enable(SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY)
                .disable(SYSUI_STATE_NAV_BAR_HIDDEN) to true,
            DEFAULT_STATE
                .enable(SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY)
                .enable(SYSUI_STATE_NAV_BAR_HIDDEN) to true,
            DEFAULT_STATE
                .disable(SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY)
                .disable(SYSUI_STATE_NAV_BAR_HIDDEN) to true,
            DEFAULT_STATE
                .disable(SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY)
                .enable(SYSUI_STATE_NAV_BAR_HIDDEN) to false,
        )

        stateToExpectedResult.forEach {(state, gestureAllowed) ->
            underTest.setSystemUiFlags(state)
            assertThat(underTest.canStartSystemGesture()).isEqualTo(gestureAllowed)
        }
    }

    private fun allSysUiStates(): List<Long> {
        // SYSUI_STATES_* are binary flags
        return (0..SYSUI_STATES_COUNT).map { 1L shl it }
    }

    companion object {
        private val GESTURE_DISABLING_SYSUI_STATES = listOf(
            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
            SYSUI_STATE_MAGNIFICATION_OVERLAP,
            SYSUI_STATE_DEVICE_DREAMING,
            SYSUI_STATE_DISABLE_GESTURE_SPLIT_INVOCATION,
        )
        private const val SYSUI_STATES_COUNT = 33
        private const val DEFAULT_STATE = 0L
    }

    private fun Long.enable(state: Long) = this or state

    private fun Long.disable(state: Long) = this and state.inv()
}
