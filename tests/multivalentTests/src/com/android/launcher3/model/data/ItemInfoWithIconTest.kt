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

package com.android.launcher3.model.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.pm.PackageInstallInfo
import com.android.launcher3.util.LauncherModelHelper
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ItemInfoWithIconTest {

    private var context = LauncherModelHelper.SandboxModelContext()
    private lateinit var itemInfoWithIcon: ItemInfoWithIcon

    @Before
    fun setup() {
        itemInfoWithIcon =
            object : ItemInfoWithIcon() {
                override fun clone(): ItemInfoWithIcon? {
                    return null
                }
            }
    }

    @After
    fun tearDown() {
        context.destroy()
    }

    @Test
    fun itemInfoWithIconDefaultParamsTest() {
        Truth.assertThat(itemInfoWithIcon.isDisabled).isFalse()
        Truth.assertThat(itemInfoWithIcon.isPendingDownload).isFalse()
        Truth.assertThat(itemInfoWithIcon.isArchived).isFalse()
    }

    @Test
    fun isDisabledOrPendingTest() {
        itemInfoWithIcon.setProgressLevel(0, PackageInstallInfo.STATUS_INSTALLING)
        Truth.assertThat(itemInfoWithIcon.isDisabled).isFalse()
        Truth.assertThat(itemInfoWithIcon.isPendingDownload).isTrue()

        itemInfoWithIcon.setProgressLevel(1, PackageInstallInfo.STATUS_INSTALLING)
        Truth.assertThat(itemInfoWithIcon.isDisabled).isFalse()
        Truth.assertThat(itemInfoWithIcon.isPendingDownload).isFalse()
    }
}
