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

import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Test
import org.junit.runner.RunWith

data class TestCase(val maxCountX: Int, val maxCountY: Int, val totalItems: Int)

@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderPagedViewTest {

    companion object {
        private fun makeFolderGridOrganizer(testCase: TestCase): FolderGridOrganizer {
            val folderGridOrganizer = FolderGridOrganizer(testCase.maxCountX, testCase.maxCountY)
            folderGridOrganizer.setContentSize(testCase.totalItems)
            return folderGridOrganizer
        }
    }

    @Test
    fun setContentSize() {
        assertCountXandY(
            TestCase(maxCountX = 4, maxCountY = 3, totalItems = 22),
            expectedCountX = 4,
            expectedCountY = 3
        )
        assertCountXandY(
            TestCase(maxCountX = 4, maxCountY = 3, totalItems = 8),
            expectedCountX = 3,
            expectedCountY = 3
        )
        assertCountXandY(
            TestCase(maxCountX = 4, maxCountY = 3, totalItems = 3),
            expectedCountX = 2,
            expectedCountY = 2
        )
    }

    private fun assertCountXandY(testCase: TestCase, expectedCountX: Int, expectedCountY: Int) {
        val folderGridOrganizer = makeFolderGridOrganizer(testCase)
        assert(folderGridOrganizer.countX == expectedCountX) {
            "Error on expected countX $expectedCountX got ${folderGridOrganizer.countX} using test case $testCase"
        }
        assert(folderGridOrganizer.countY == expectedCountY) {
            "Error on expected countY $expectedCountY got ${folderGridOrganizer.countY} using test case $testCase"
        }
    }

    @Test
    fun getPosForRank() {
        assertFolderRank(
            TestCase(maxCountX = 4, maxCountY = 3, totalItems = 22),
            expectedPos = Point(0, 0),
            rank = 0
        )
        assertFolderRank(
            TestCase(maxCountX = 4, maxCountY = 3, totalItems = 22),
            expectedPos = Point(1, 0),
            rank = 1
        )
        assertFolderRank(
            TestCase(maxCountX = 4, maxCountY = 3, totalItems = 22),
            expectedPos = Point(3, 0),
            rank = 3
        )
        assertFolderRank(
            TestCase(maxCountX = 4, maxCountY = 3, totalItems = 22),
            expectedPos = Point(2, 1),
            rank = 6
        )
        val testCase = TestCase(maxCountX = 4, maxCountY = 3, totalItems = 22)
        // Rank 16 and 38 should yield the same point since 38 % 12 == 2
        val folderGridOrganizer = makeFolderGridOrganizer(testCase)
        assertFolderRank(testCase, expectedPos = folderGridOrganizer.getPosForRank(2), rank = 38)
    }

    private fun assertFolderRank(testCase: TestCase, expectedPos: Point, rank: Int) {
        val folderGridOrganizer = makeFolderGridOrganizer(testCase)
        val pos = folderGridOrganizer.getPosForRank(rank)
        assert(pos == expectedPos) {
            "Expected pos = $expectedPos  doesn't match pos = $pos for the given rank $rank and the give test case $testCase"
        }
    }

    @Test
    fun isItemInPreview() {
        val folderGridOrganizer = FolderGridOrganizer(5, 8)
        folderGridOrganizer.setContentSize(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW - 1)
        // Very few items
        for (i in 0..3) {
            assertItemsInPreview(
                TestCase(
                    maxCountX = 5,
                    maxCountY = 8,
                    totalItems = ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW - 1
                ),
                expectedIsInPreview = true,
                page = 0,
                rank = i
            )
        }
        for (i in 4..40) {
            assertItemsInPreview(
                TestCase(
                    maxCountX = 5,
                    maxCountY = 8,
                    totalItems = ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW - 1
                ),
                expectedIsInPreview = false,
                page = 0,
                rank = i
            )
        }
        // Full of items
        assertItemsInPreview(
            TestCase(maxCountX = 5, maxCountY = 8, totalItems = 40),
            expectedIsInPreview = false,
            page = 0,
            rank = 2
        )
        assertItemsInPreview(
            TestCase(maxCountX = 5, maxCountY = 8, totalItems = 40),
            expectedIsInPreview = false,
            page = 0,
            rank = 2
        )
        assertItemsInPreview(
            TestCase(maxCountX = 5, maxCountY = 8, totalItems = 40),
            expectedIsInPreview = true,
            page = 0,
            rank = 5
        )
        assertItemsInPreview(
            TestCase(maxCountX = 5, maxCountY = 8, totalItems = 40),
            expectedIsInPreview = true,
            page = 0,
            rank = 6
        )
    }

    private fun assertItemsInPreview(
        testCase: TestCase,
        expectedIsInPreview: Boolean,
        page: Int,
        rank: Int
    ) {
        val folderGridOrganizer = makeFolderGridOrganizer(testCase)
        val isInPreview = folderGridOrganizer.isItemInPreview(page, rank)
        assert(isInPreview == expectedIsInPreview) {
            "Item preview state should be $expectedIsInPreview but got $isInPreview, for page $page and rank $rank, for test case $testCase"
        }
    }
}
