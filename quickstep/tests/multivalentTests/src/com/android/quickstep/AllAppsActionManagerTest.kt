/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep

import android.app.PendingIntent
import android.content.IIntentSender
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 5L

@RunWith(AndroidJUnit4::class)
class AllAppsActionManagerTest {
    private val callbackSemaphore = Semaphore(0)
    private val bgExecutor = UI_HELPER_EXECUTOR

    private val allAppsActionManager =
        AllAppsActionManager(
            InstrumentationRegistry.getInstrumentation().targetContext,
            bgExecutor,
        ) {
            callbackSemaphore.release()
            PendingIntent(IIntentSender.Default())
        }

    @Test
    fun taskbarPresent_actionRegistered() {
        allAppsActionManager.isTaskbarPresent = true
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
    }

    @Test
    fun homeAndOverviewSame_actionRegistered() {
        allAppsActionManager.isHomeAndOverviewSame = true
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
    }

    @Test
    fun toggleTaskbar_destroyedAfterActionRegistered_actionUnregistered() {
        allAppsActionManager.isTaskbarPresent = true
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()

        allAppsActionManager.isTaskbarPresent = false
        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to unregister.
        assertThat(allAppsActionManager.isActionRegistered).isFalse()
    }

    @Test
    fun toggleTaskbar_destroyedBeforeActionRegistered_pendingActionUnregistered() {
        allAppsActionManager.isTaskbarPresent = true
        allAppsActionManager.isTaskbarPresent = false

        TestUtil.runOnExecutorSync(bgExecutor) {} // Force system action to unregister.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isFalse()
    }

    @Test
    fun changeHome_sameAsOverviewBeforeActionUnregistered_actionRegisteredAgain() {
        allAppsActionManager.isHomeAndOverviewSame = true // Initialize to same.
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()

        allAppsActionManager.isHomeAndOverviewSame = false
        allAppsActionManager.isHomeAndOverviewSame = true
        assertThat(callbackSemaphore.tryAcquire(TIMEOUT, SECONDS)).isTrue()
        assertThat(allAppsActionManager.isActionRegistered).isTrue()
    }
}
