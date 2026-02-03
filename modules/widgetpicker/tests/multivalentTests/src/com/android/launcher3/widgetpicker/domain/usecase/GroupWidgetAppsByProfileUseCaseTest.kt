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

package com.android.launcher3.widgetpicker.domain.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.WORK_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.widgetUserProfilePersonal
import com.android.launcher3.widgetpicker.TestUtils.widgetUserProfileWork
import com.android.launcher3.widgetpicker.TestUtils.workUser
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfiles
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupWidgetAppsByProfileUseCaseTest {
    private val underTest = GroupWidgetAppsByProfileUseCase()

    @Test
    fun noWorkProfile_mapsOnlyPersonalProfile() {
        val allWidgetApps = PERSONAL_TEST_APPS
        val personalProfile = widgetUserProfilePersonal
        val workProfile = null

        val result = underTest(
            workProfileUser = null,
            widgetApps = allWidgetApps,
            userProfiles = WidgetUserProfiles(personal = personalProfile, work = workProfile),
        )

        assertThat(result).hasSize(1)
        assertThat(result).containsExactlyEntriesIn(mapOf(personalProfile to allWidgetApps))
    }

    @Test
    fun workProfile_mapsBothPersonalAndWorkProfiles() {
        val allWidgetApps = PERSONAL_TEST_APPS + WORK_TEST_APPS
        val personalProfile = widgetUserProfilePersonal
        val workProfile = widgetUserProfileWork.copy(paused = false)

        val result = underTest(
            workProfileUser = workUser,
            widgetApps = allWidgetApps,
            userProfiles = WidgetUserProfiles(personal = personalProfile, work = workProfile),
        )

        assertThat(result).hasSize(2)
        assertThat(result[personalProfile]).containsExactlyElementsIn(PERSONAL_TEST_APPS)
        assertThat(result[workProfile]).containsExactlyElementsIn(WORK_TEST_APPS)
    }

    @Test
    fun workProfilePaused_setsWorkWidgetsToEmpty() {
        val allWidgetApps = PERSONAL_TEST_APPS + WORK_TEST_APPS
        val personalProfile = widgetUserProfilePersonal
        val workProfile = widgetUserProfileWork.copy(paused = true)

        val result = underTest(
            workProfileUser = workUser,
            widgetApps = allWidgetApps,
            userProfiles = WidgetUserProfiles(personal = personalProfile, work = workProfile),
        )

        assertThat(result).hasSize(2)
        assertThat(result[personalProfile]).containsExactlyElementsIn(PERSONAL_TEST_APPS)
        assertThat(result[workProfile]).isEmpty()
    }
}
