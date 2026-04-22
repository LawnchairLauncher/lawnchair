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

package com.android.wm.shell.desktopmode.education

import android.content.Context
import android.testing.AndroidTestingRunner
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.desktopmode.education.data.AppHandleEducationDatastoreRepository
import com.android.wm.shell.desktopmode.education.data.WindowingEducationProto
import com.android.wm.shell.util.GMAIL_PACKAGE_NAME
import com.android.wm.shell.util.createWindowingEducationProto
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class AppHandleEducationDatastoreRepositoryTest {
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDatastore: DataStore<WindowingEducationProto>
    private lateinit var datastoreRepository: AppHandleEducationDatastoreRepository
    private lateinit var datastoreScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        testDatastore =
            DataStoreFactory.create(
                serializer =
                    AppHandleEducationDatastoreRepository.Companion
                        .WindowingEducationProtoSerializer,
                scope = datastoreScope,
            ) {
                testContext.dataStoreFile(APP_HANDLE_EDUCATION_DATASTORE_TEST_FILE)
            }
        datastoreRepository = AppHandleEducationDatastoreRepository(testDatastore)
    }

    @After
    fun tearDown() {
        File(ApplicationProvider.getApplicationContext<Context>().filesDir, "datastore")
            .deleteRecursively()

        datastoreScope.cancel()
    }

    @Test
    fun getWindowingEducationProto_returnsCorrectProto() =
        runTest(StandardTestDispatcher()) {
            val windowingEducationProto =
                createWindowingEducationProto(
                    appHandleHintViewedTimestampMillis = 123L,
                    appHandleHintUsedTimestampMillis = 124L,
                    appUsageStats = mapOf(GMAIL_PACKAGE_NAME to 2),
                    appUsageStatsLastUpdateTimestampMillis = 125L,
                )
            testDatastore.updateData { windowingEducationProto }

            val resultProto = datastoreRepository.windowingEducationProto()

            assertThat(resultProto).isEqualTo(windowingEducationProto)
        }

    @Test
    fun updateAppUsageStats_updatesDatastoreProto() =
        runTest(StandardTestDispatcher()) {
            val appUsageStats = mapOf(GMAIL_PACKAGE_NAME to 3)
            val appUsageStatsLastUpdateTimestamp = Duration.ofMillis(123L)
            val windowingEducationProto =
                createWindowingEducationProto(
                    appUsageStats = appUsageStats,
                    appUsageStatsLastUpdateTimestampMillis =
                        appUsageStatsLastUpdateTimestamp.toMillis(),
                )

            datastoreRepository.updateAppUsageStats(appUsageStats, appUsageStatsLastUpdateTimestamp)

            val result = testDatastore.data.first()
            assertThat(result).isEqualTo(windowingEducationProto)
        }

    @Test
    fun updateAppHandleHintViewedTimestampMillis_updatesDatastoreProto() =
        runTest(StandardTestDispatcher()) {
            datastoreRepository.updateAppHandleHintViewedTimestampMillis(true)

            val result = testDatastore.data.first().hasAppHandleHintViewedTimestampMillis()
            assertThat(result).isEqualTo(true)
        }

    @Test
    fun updateEnterDesktopModeHintViewedTimestampMillis_updatesDatastoreProto() =
        runTest(StandardTestDispatcher()) {
            datastoreRepository.updateEnterDesktopModeHintViewedTimestampMillis(true)

            val result = testDatastore.data.first().hasEnterDesktopModeHintViewedTimestampMillis()
            assertThat(result).isEqualTo(true)
        }

    @Test
    fun updateExitDesktopModeHintViewedTimestampMillis_updatesDatastoreProto() =
        runTest(StandardTestDispatcher()) {
            datastoreRepository.updateExitDesktopModeHintViewedTimestampMillis(true)

            val result = testDatastore.data.first().hasExitDesktopModeHintViewedTimestampMillis()
            assertThat(result).isEqualTo(true)
        }

    @Test
    fun updateAppHandleHintUsedTimestampMillis_updatesDatastoreProto() =
        runTest(StandardTestDispatcher()) {
            datastoreRepository.updateAppHandleHintUsedTimestampMillis(true)

            val result = testDatastore.data.first().hasAppHandleHintUsedTimestampMillis()
            assertThat(result).isEqualTo(true)
        }

    companion object {
        private const val APP_HANDLE_EDUCATION_DATASTORE_TEST_FILE = "app_handle_education_test.pb"
    }
}
