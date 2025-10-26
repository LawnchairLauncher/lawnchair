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

package com.android.launcher3.folder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.folder.FolderNameInfos.*
import org.junit.Test
import org.junit.runner.RunWith

data class Label(val index: Int, val label: String, val score: Float)

@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderNameInfosTest {

    companion object {
        val statusList =
            listOf(
                SUCCESS,
                HAS_PRIMARY,
                HAS_SUGGESTIONS,
                ERROR_NO_PROVIDER,
                ERROR_APP_LOOKUP_FAILED,
                ERROR_ALL_APP_LOOKUP_FAILED,
                ERROR_NO_LABELS_GENERATED,
                ERROR_LABEL_LOOKUP_FAILED,
                ERROR_ALL_LABEL_LOOKUP_FAILED,
                ERROR_NO_PACKAGES,
            )
    }

    @Test
    fun status() {
        assertStatus(statusList)
        assertStatus(
            listOf(
                ERROR_NO_PROVIDER,
                ERROR_APP_LOOKUP_FAILED,
                ERROR_ALL_APP_LOOKUP_FAILED,
                ERROR_NO_LABELS_GENERATED,
                ERROR_LABEL_LOOKUP_FAILED,
                ERROR_ALL_LABEL_LOOKUP_FAILED,
                ERROR_NO_PACKAGES,
            )
        )
        assertStatus(
            listOf(
                SUCCESS,
                HAS_PRIMARY,
                HAS_SUGGESTIONS,
            )
        )
        assertStatus(
            listOf(
                SUCCESS,
                HAS_PRIMARY,
                HAS_SUGGESTIONS,
            )
        )
    }

    fun assertStatus(statusList: List<Int>) {
        var infos = FolderNameInfos()
        statusList.forEach { infos.setStatus(it) }
        assert(infos.status() == statusList.sum()) {
            "There is an overlap on the status constants!"
        }
    }

    @Test
    fun hasPrimary() {
        assertHasPrimary(
            createNameInfos(listOf(Label(0, "label", 1f)), statusList),
            hasPrimary = true
        )
        assertHasPrimary(
            createNameInfos(listOf(Label(1, "label", 1f)), statusList),
            hasPrimary = false
        )
        assertHasPrimary(
            createNameInfos(
                listOf(Label(0, "label", 1f)),
                listOf(
                    ERROR_NO_PROVIDER,
                    ERROR_APP_LOOKUP_FAILED,
                    ERROR_ALL_APP_LOOKUP_FAILED,
                    ERROR_NO_LABELS_GENERATED,
                    ERROR_LABEL_LOOKUP_FAILED,
                    ERROR_ALL_LABEL_LOOKUP_FAILED,
                    ERROR_NO_PACKAGES,
                )
            ),
            hasPrimary = false
        )
    }

    private fun assertHasPrimary(nameInfos: FolderNameInfos, hasPrimary: Boolean) =
        assert(nameInfos.hasPrimary() == hasPrimary)

    private fun createNameInfos(labels: List<Label>?, statusList: List<Int>?): FolderNameInfos {
        val infos = FolderNameInfos()
        labels?.forEach { infos.setLabel(it.index, it.label, it.score) }
        statusList?.forEach { infos.setStatus(it) }
        return infos
    }

    @Test
    fun hasSuggestions() {
        assertHasSuggestions(
            createNameInfos(listOf(Label(0, "label", 1f)), null),
            hasSuggestions = true
        )
        assertHasSuggestions(createNameInfos(null, null), hasSuggestions = false)
        // There is a max of 4 suggestions
        assertHasSuggestions(
            createNameInfos(listOf(Label(5, "label", 1f)), null),
            hasSuggestions = false
        )
        assertHasSuggestions(
            createNameInfos(
                listOf(
                    Label(0, "label", 1f),
                    Label(1, "label", 1f),
                    Label(2, "label", 1f),
                    Label(3, "label", 1f)
                ),
                null
            ),
            hasSuggestions = true
        )
    }

    private fun assertHasSuggestions(nameInfos: FolderNameInfos, hasSuggestions: Boolean) =
        assert(nameInfos.hasSuggestions() == hasSuggestions)

    @Test
    fun hasContains() {
        assertContains(
            createNameInfos(
                listOf(
                    Label(0, "label1", 1f),
                    Label(1, "label2", 1f),
                    Label(2, "label3", 1f),
                    Label(3, "label4", 1f)
                ),
                null
            ),
            label = Label(-1, "label3", -1f),
            contains = true
        )
        assertContains(
            createNameInfos(
                listOf(
                    Label(0, "label1", 1f),
                    Label(1, "label2", 1f),
                    Label(2, "label3", 1f),
                    Label(3, "label4", 1f)
                ),
                null
            ),
            label = Label(-1, "label5", -1f),
            contains = false
        )
        assertContains(
            createNameInfos(null, null),
            label = Label(-1, "label1", -1f),
            contains = false
        )
        assertContains(
            createNameInfos(
                listOf(
                    Label(0, "label1", 1f),
                    Label(1, "label2", 1f),
                    Label(2, "lAbel3", 1f),
                    Label(3, "lEbel4", 1f)
                ),
                null
            ),
            label = Label(-1, "LaBEl3", -1f),
            contains = true
        )
    }

    private fun assertContains(nameInfos: FolderNameInfos, label: Label, contains: Boolean) =
        assert(nameInfos.contains(label.label) == contains)
}
