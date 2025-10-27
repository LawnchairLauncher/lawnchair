package com.android.quickstep

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

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_ENABLE_LAUNCHER_BR_METRICS_FIXED)
class LauncherRestoreEventLoggerImplTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val mLauncherModelHelper = LauncherModelHelper()
    private val mSandboxContext: SandboxModelContext = mLauncherModelHelper.sandboxContext
    private lateinit var loggerUnderTest: LauncherRestoreEventLoggerImpl

    @Before
    fun setup() {
        loggerUnderTest = LauncherRestoreEventLoggerImpl(mSandboxContext)
    }

    @After
    fun teardown() {
        loggerUnderTest.restoreEventLogger.clearData()
        mLauncherModelHelper.destroy()
    }

    @Test
    fun `logLauncherItemsRestoreFailed logs multiple items as failing restore`() {
        // Given
        val expectedDataType = "application"
        val expectedError = "test_failure"
        // When
        loggerUnderTest.logLauncherItemsRestoreFailed(
            dataType = expectedDataType,
            count = 5,
            error = expectedError
        )
        // Then
        val actualResult = loggerUnderTest.restoreEventLogger.loggingResults.first()
        assertThat(actualResult.dataType).isEqualTo(expectedDataType)
        assertThat(actualResult.successCount).isEqualTo(0)
        assertThat(actualResult.failCount).isEqualTo(5)
        assertThat(actualResult.errors.keys).containsExactly(expectedError)
    }

    @Test
    fun `logLauncherItemsRestored logs multiple items as restored`() {
        // Given
        val expectedDataType = "application"
        // When
        loggerUnderTest.logLauncherItemsRestored(dataType = expectedDataType, count = 5)
        // Then
        val actualResult = loggerUnderTest.restoreEventLogger.loggingResults.first()
        assertThat(actualResult.dataType).isEqualTo(expectedDataType)
        assertThat(actualResult.successCount).isEqualTo(5)
        assertThat(actualResult.failCount).isEqualTo(0)
        assertThat(actualResult.errors.keys).isEmpty()
    }

    @Test
    fun `logSingleFavoritesItemRestored logs a single Favorites Item as restored`() {
        // Given
        val expectedDataType = "widget"
        // When
        loggerUnderTest.logSingleFavoritesItemRestored(favoritesId = Favorites.ITEM_TYPE_APPWIDGET)
        // Then
        val actualResult = loggerUnderTest.restoreEventLogger.loggingResults.first()
        assertThat(actualResult.dataType).isEqualTo(expectedDataType)
        assertThat(actualResult.successCount).isEqualTo(1)
        assertThat(actualResult.failCount).isEqualTo(0)
        assertThat(actualResult.errors.keys).isEmpty()
    }

    @Test
    fun `logSingleFavoritesItemRestoreFailed logs a single Favorites Item as failing restore`() {
        // Given
        val expectedDataType = "widget"
        val expectedError = "test_failure"
        // When
        loggerUnderTest.logSingleFavoritesItemRestoreFailed(
            favoritesId = Favorites.ITEM_TYPE_APPWIDGET,
            error = expectedError
        )
        // Then
        val actualResult = loggerUnderTest.restoreEventLogger.loggingResults.first()
        assertThat(actualResult.dataType).isEqualTo(expectedDataType)
        assertThat(actualResult.successCount).isEqualTo(0)
        assertThat(actualResult.failCount).isEqualTo(1)
        assertThat(actualResult.errors.keys).containsExactly(expectedError)
    }

    @Test
    fun `logFavoritesItemsRestoreFailed logs multiple Favorites Items as failing restore`() {
        // Given
        val expectedDataType = "deep_shortcut"
        val expectedError = "test_failure"
        // When
        loggerUnderTest.logFavoritesItemsRestoreFailed(
            favoritesId = Favorites.ITEM_TYPE_DEEP_SHORTCUT,
            count = 5,
            error = expectedError
        )
        // Then
        val actualResult = loggerUnderTest.restoreEventLogger.loggingResults.first()
        assertThat(actualResult.dataType).isEqualTo(expectedDataType)
        assertThat(actualResult.successCount).isEqualTo(0)
        assertThat(actualResult.failCount).isEqualTo(5)
        assertThat(actualResult.errors.keys).containsExactly(expectedError)
    }
}
