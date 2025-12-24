/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.airplane.data.repository.impl

import android.net.ConnectivityManager
import android.provider.Settings.Global
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.statusbar.pipeline.airplane.data.repository.AirplaneModeRepository
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.whenever
import com.android.systemui.util.settings.FakeGlobalSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class AirplaneModeRepositoryImplTest : SysuiTestCase() {

    private lateinit var underTest: AirplaneModeRepository

    @Mock private lateinit var logger: TableLogBuffer
    @Mock private lateinit var telephonyManager: TelephonyManager
    @Mock private lateinit var subscriptionManager: SubscriptionManager
    @Mock private lateinit var connectivityManager: ConnectivityManager

    private val testContext = StandardTestDispatcher()
    private val scope = TestScope(testContext)

    private lateinit var settings: FakeGlobalSettings

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        settings = FakeGlobalSettings(testContext)

        whenever(telephonyManager.emergencyCallbackMode).thenReturn(false)
        whenever(subscriptionManager.activeSubscriptionIdList).thenReturn(intArrayOf())

        underTest =
            AirplaneModeRepositoryImpl(
                connectivityManager,
                null,
                scope.backgroundScope.coroutineContext,
                settings,
                logger,
                scope.backgroundScope,
            )
    }

    @Test
    fun isAirplaneMode_initiallyGetsSettingsValue() =
        scope.runTest {
            settings.putInt(Global.AIRPLANE_MODE_ON, 1)

            underTest =
                AirplaneModeRepositoryImpl(
                    connectivityManager,
                    null,
                    scope.backgroundScope.coroutineContext,
                    settings,
                    logger,
                    scope.backgroundScope,
                )

            underTest.isAirplaneMode.launchIn(backgroundScope)
            runCurrent()
            assertThat(underTest.isAirplaneMode.value).isTrue()
        }

    @Test
    fun isAirplaneMode_settingUpdated_valueUpdated() =
        scope.runTest {
            underTest.isAirplaneMode.launchIn(backgroundScope)

            settings.putInt(Global.AIRPLANE_MODE_ON, 0)
            runCurrent()
            assertThat(underTest.isAirplaneMode.value).isFalse()

            settings.putInt(Global.AIRPLANE_MODE_ON, 1)
            runCurrent()
            assertThat(underTest.isAirplaneMode.value).isTrue()

            settings.putInt(Global.AIRPLANE_MODE_ON, 0)
            runCurrent()
            assertThat(underTest.isAirplaneMode.value).isFalse()
        }

    @Test
    fun setIsAirplaneMode() =
        scope.runTest {
            underTest.setIsAirplaneMode(true)
            runCurrent()

            verify(connectivityManager).setAirplaneMode(eq(true))
        }
}
