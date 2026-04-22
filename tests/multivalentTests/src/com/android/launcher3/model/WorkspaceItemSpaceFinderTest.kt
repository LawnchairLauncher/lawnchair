/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.model

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ModelTestExtensions.bgDataModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [WorkspaceItemSpaceFinder] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class WorkspaceItemSpaceFinderTest : AbstractWorkspaceModelTest() {

    @Before
    override fun setup() {
        super.setup()
    }

    private fun findSpace(spanX: Int, spanY: Int): NewItemSpace =
        WorkspaceItemSpaceFinder(model.bgDataModel, mAppState.invariantDeviceProfile, model)
            .findSpaceForItem(mExistingScreens, mNewScreens, mAddedWorkspaceItems, spanX, spanY)
            .let { NewItemSpace.fromIntArray(it) }

    private fun assertRegionVacant(newItemSpace: NewItemSpace, spanX: Int, spanY: Int) {
        assertThat(
                mScreenOccupancy[newItemSpace.screenId].isRegionVacant(
                    newItemSpace.cellX,
                    newItemSpace.cellY,
                    spanX,
                    spanY,
                )
            )
            .isTrue()
    }

    @Test
    fun justEnoughSpaceOnFirstScreen_whenFindSpaceForItem_thenReturnFirstScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 space
            //  2 spaces of sizes 3x2 and 2x3
            screen2 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
        )

        val spaceFound = findSpace(1, 1)

        assertThat(spaceFound.screenId).isEqualTo(1)
        assertRegionVacant(spaceFound, 1, 1)
    }

    @Test
    fun workspaceItemsAddedButNotYetCommittedToDbShouldBeTakenIntoAccountInFindSpaceForItem() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = listOf(Rect(2, 2, 4, 4)), // 2x2 space
        )
        val itemInfo = ItemInfo()
        itemInfo.cellX = 2
        itemInfo.cellY = 2
        itemInfo.screenId = 1
        itemInfo.container = CONTAINER_DESKTOP
        mAddedWorkspaceItems.add(itemInfo)

        val itemInfo2 = ItemInfo()
        itemInfo2.cellX = 3
        itemInfo2.cellY = 2
        itemInfo2.screenId = 1
        itemInfo2.container = CONTAINER_DESKTOP
        mAddedWorkspaceItems.add(itemInfo2)

        val spaceFound = findSpace(1, 1)

        assertThat(spaceFound.screenId).isEqualTo(1)
        assertThat(spaceFound.cellX).isEqualTo(2)
        assertThat(spaceFound.cellY).isEqualTo(3)
        assertRegionVacant(spaceFound, 1, 1)

        mAddedWorkspaceItems.clear()
    }

    @Test
    fun notEnoughSpaceOnFirstScreen_whenFindSpaceForItem_thenReturnSecondScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = listOf(Rect(2, 2, 3, 3)), // 1x1 space
            //  2 spaces of sizes 3x2 and 2x3
            screen2 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
        )

        // Find a larger space
        val spaceFound = findSpace(2, 3)

        assertThat(spaceFound.screenId).isEqualTo(2)
        assertRegionVacant(spaceFound, 2, 3)
    }

    @Test
    fun notEnoughSpaceOnExistingScreens_returnNewScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            //  2 spaces of sizes 3x2 and 2x3
            screen1 = listOf(Rect(2, 0, 5, 2), Rect(0, 2, 2, 5)),
            //  2 spaces of sizes 1x2 and 2x2
            screen2 = listOf(Rect(1, 0, 2, 2), Rect(3, 2, 5, 4)),
        )

        val oldScreens = mExistingScreens.clone()
        val spaceFound = findSpace(3, 3)

        assertThat(oldScreens.contains(spaceFound.screenId)).isFalse()
        assertThat(mNewScreens.contains(spaceFound.screenId)).isTrue()
    }

    @Test
    fun firstScreenIsEmptyButSecondIsNotEmpty_returnSecondScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            // empty screens are skipped
            screen2 = listOf(Rect(2, 0, 5, 2)), // 3x2 space
        )

        val spaceFound = findSpace(2, 1)

        assertThat(spaceFound.screenId).isEqualTo(2)
        assertRegionVacant(spaceFound, 2, 1)
    }

    @Test
    fun twoEmptyMiddleScreens_returnThirdScreen() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            // empty screens are skipped
            screen3 = listOf(Rect(1, 1, 4, 4)), // 3x3 space
        )

        val spaceFound = findSpace(2, 3)

        assertThat(spaceFound.screenId).isEqualTo(3)
        assertRegionVacant(spaceFound, 2, 3)
    }

    @Test
    fun allExistingPagesAreFull_returnNewScreenId() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = fullScreenSpaces,
            screen2 = fullScreenSpaces,
        )

        val spaceFound = findSpace(2, 3)

        assertThat(spaceFound.screenId).isEqualTo(3)
        assertThat(mNewScreens.contains(spaceFound.screenId)).isTrue()
    }

    @Test
    fun firstTwoPagesAreFull_and_ThirdPageIsEmpty_returnThirdPage() {
        setupWorkspacesWithSpaces(
            // 3x2 space on screen 0, but it should be skipped
            screen0 = listOf(Rect(2, 0, 5, 2)),
            screen1 = fullScreenSpaces, // full screens are skipped
            screen2 = fullScreenSpaces, // full screens are skipped
            screen3 = emptyScreenSpaces,
        )

        val spaceFound = findSpace(3, 1)

        assertThat(spaceFound.screenId).isEqualTo(3)
        assertRegionVacant(spaceFound, 3, 1)
    }
}
