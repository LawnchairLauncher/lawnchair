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

package com.android.wm.shell.common.bubbles

import android.os.Parcel
import android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class BubbleInfoTest : ShellTestCase() {

    @Test
    fun bubbleInfo() {
        val bubbleInfo =
            BubbleInfo(
                "key",
                0,
                "shortcut id",
                null,
                6,
                "com.some.package",
                "title",
                "Some app",
                true
            )
        val parcel = Parcel.obtain()
        bubbleInfo.writeToParcel(parcel, PARCELABLE_WRITE_RETURN_VALUE)
        parcel.setDataPosition(0)

        val bubbleInfoFromParcel = BubbleInfo.CREATOR.createFromParcel(parcel)

        assertThat(bubbleInfo.key).isEqualTo(bubbleInfoFromParcel.key)
        assertThat(bubbleInfo.flags).isEqualTo(bubbleInfoFromParcel.flags)
        assertThat(bubbleInfo.shortcutId).isEqualTo(bubbleInfoFromParcel.shortcutId)
        assertThat(bubbleInfo.icon).isEqualTo(bubbleInfoFromParcel.icon)
        assertThat(bubbleInfo.userId).isEqualTo(bubbleInfoFromParcel.userId)
        assertThat(bubbleInfo.packageName).isEqualTo(bubbleInfoFromParcel.packageName)
        assertThat(bubbleInfo.title).isEqualTo(bubbleInfoFromParcel.title)
        assertThat(bubbleInfo.appName).isEqualTo(bubbleInfoFromParcel.appName)
        assertThat(bubbleInfo.isImportantConversation)
            .isEqualTo(bubbleInfoFromParcel.isImportantConversation)
    }
}
