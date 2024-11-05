/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.taskbar

import android.app.KeyguardManager
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BACK_DISABLED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DEVICE_DOZING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TaskbarKeyguardControllerTest : TaskbarBaseTestCase() {

    private val baseDragLayer: TaskbarDragLayer = mock()
    private val keyguardManager: KeyguardManager = mock()

    @Before
    override fun setup() {
        super.setup()
        whenever(taskbarActivityContext.getSystemService(KeyguardManager::class.java))
            .thenReturn(keyguardManager)
        whenever(baseDragLayer.childCount).thenReturn(0)
        whenever(taskbarActivityContext.dragLayer).thenReturn(baseDragLayer)

        taskbarKeyguardController = TaskbarKeyguardController(taskbarActivityContext)
        taskbarKeyguardController.init(navbarButtonsViewController)
    }

    @Test
    fun uninterestingFlags_noActions() {
        setFlags(0)
        verify(navbarButtonsViewController, never()).setKeyguardVisible(any(), any())
    }

    @Test
    fun keyguardShowing() {
        setFlags(SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING)
        verify(navbarButtonsViewController, times(1))
            .setKeyguardVisible(true /*isKeyguardVisible*/, false /*isKeyguardOccluded*/)
    }

    @Test
    fun dozingShowing() {
        setFlags(SYSUI_STATE_DEVICE_DOZING)
        verify(navbarButtonsViewController, times(1))
            .setKeyguardVisible(true /*isKeyguardVisible*/, false /*isKeyguardOccluded*/)
    }

    @Test
    fun keyguardOccluded() {
        setFlags(SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED)
        verify(navbarButtonsViewController, times(1))
            .setKeyguardVisible(false /*isKeyguardVisible*/, true /*isKeyguardOccluded*/)
    }

    @Test
    fun keyguardOccludedAndDozing() {
        setFlags(SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED.or(SYSUI_STATE_DEVICE_DOZING))
        verify(navbarButtonsViewController, times(1))
            .setKeyguardVisible(true /*isKeyguardVisible*/, true /*isKeyguardOccluded*/)
    }

    @Test
    fun deviceInsecure_hideBackForBouncer() {
        whenever(keyguardManager.isDeviceSecure).thenReturn(false)
        setFlags(SYSUI_STATE_BOUNCER_SHOWING)

        verify(navbarButtonsViewController, times(1)).setBackForBouncer(false)
    }

    @Test
    fun deviceSecure_showBackForBouncer() {
        whenever(keyguardManager.isDeviceSecure).thenReturn(true)
        setFlags(SYSUI_STATE_BOUNCER_SHOWING)

        verify(navbarButtonsViewController, times(1)).setBackForBouncer(true)
    }

    @Test
    fun backDisabled_hideBackForBouncer() {
        whenever(keyguardManager.isDeviceSecure).thenReturn(true)
        setFlags(SYSUI_STATE_BACK_DISABLED.or(SYSUI_STATE_BOUNCER_SHOWING))

        verify(navbarButtonsViewController, times(1)).setBackForBouncer(false)
    }

    private fun setFlags(flags: Long) {
        taskbarKeyguardController.updateStateForSysuiFlags(flags)
    }
}
