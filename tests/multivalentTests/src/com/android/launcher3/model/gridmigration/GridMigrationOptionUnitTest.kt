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

package com.android.launcher3.model.gridmigration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.model.GridMigrationOption
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [GridMigrationOption] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class GridMigrationOptionUnitTest {

    /* Here we're going from a phone grid to another phone grid and isAfterRestore is false */
    @Test
    fun canMigrateFrom4x5To5x5NotAfterRestoreShouldReturnTrue() {
        val sourceGridMigrationOption = GridMigrationOption.from(4, 5)
        val destGridMigrationOption = GridMigrationOption.from(5, 5)
        assert(
            sourceGridMigrationOption != null &&
                destGridMigrationOption != null &&
                sourceGridMigrationOption.canMigrate(
                    destGridMigrationOption,
                    isAfterRestore = false,
                )
        ) {
            "Going from 4x5 to 5x5 with isAfterRestore as false caused canMigrate() to return false"
        }
    }

    /* Here we're going from a phone grid to another phone grid and isAfterRestore is false */
    @Test
    fun canMigrateFrom4x5To4x6NotAfterRestoreShouldReturnTrue() {
        val sourceGridMigrationOption = GridMigrationOption.from(4, 5)
        val destGridMigrationOption = GridMigrationOption.from(4, 6)
        assert(
            sourceGridMigrationOption != null &&
                destGridMigrationOption != null &&
                sourceGridMigrationOption.canMigrate(
                    destGridMigrationOption,
                    isAfterRestore = false,
                )
        ) {
            "Going from 4x5 to 4x6 with isAfterRestore as false caused canMigrate() to return false"
        }
    }

    /* Here we're going from a phone grid to a non-existent grid and isAfterRestore is false */
    @Test
    fun canMigrateFrom4x5To7x7NotAfterRestoreShouldReturnFalse() {
        val sourceGridMigrationOption = GridMigrationOption.from(4, 5)
        val destGridMigrationOption = GridMigrationOption.from(7, 7)
        assert(destGridMigrationOption == null) {
            "We create a GridMigrationOption for an invalid 7x7 grid"
        }
    }

    /* Here we're going from a non-existent grid to a phone grid and isAfterRestore is false */
    @Test
    fun canMigrateFrom8x8To4x5NotAfterRestoreShouldReturnFalse() {
        val sourceGridMigrationOption = GridMigrationOption.from(8, 8)
        assert(sourceGridMigrationOption == null) {
            "We create a GridMigrationOption for an invalid 8x8 grid"
        }
    }

    /* Here we're going from a tablet grid to a phone grid and isAfterRestore is false */
    @Test
    fun canMigrateFrom6x5To2x2NotAfterRestoreShouldReturnFalse() {
        val sourceGridMigrationOption = GridMigrationOption.from(6, 5)
        val destGridMigrationOption = GridMigrationOption.from(2, 2)
        assert(
            sourceGridMigrationOption != null &&
                destGridMigrationOption != null &&
                !sourceGridMigrationOption.canMigrate(
                    destGridMigrationOption,
                    isAfterRestore = false,
                )
        ) {
            "Going from 6x5 to 2x2 with isAfterRestore as false caused canMigrate() to return true"
        }
    }

    /* Here we're going from a tablet grid to itself and isAfterRestore is false */
    @Test
    fun canMigrateFrom6x5To6x5NotAfterRestoreShouldReturnTrue() {
        val sourceGridMigrationOption = GridMigrationOption.from(6, 5)
        val destGridMigrationOption = GridMigrationOption.from(6, 5)

        assert(
            sourceGridMigrationOption != null &&
                destGridMigrationOption != null &&
                sourceGridMigrationOption.canMigrate(
                    destGridMigrationOption,
                    isAfterRestore = false,
                )
        ) {
            "Going from 6x5 to 6x5 with isAfterRestore as false caused canMigrate() to return false"
        }
    }

    /* Here we're going from a phone grid to another phone grid and isAfterRestore is true */
    @Test
    fun canMigrateFrom4x5To4x6AfterRestoreShouldReturnTrue() {
        val sourceGridMigrationOption = GridMigrationOption.from(4, 5)
        val destGridMigrationOption = GridMigrationOption.from(4, 6)
        assert(
            sourceGridMigrationOption != null &&
                destGridMigrationOption != null &&
                sourceGridMigrationOption.canMigrate(destGridMigrationOption, isAfterRestore = true)
        ) {
            "Going from 4x5 to 4x6 with isAfterRestore as true caused canMigrate() to return false"
        }
    }

    /**
     * Here we're going from a phone grid to another phone grid that is fixed landscape and
     * isAfterRestore is true.
     */
    @Test
    fun canMigrateFrom4x5To8x3NotAfterRestoreShouldReturnTrue() {
        val sourceGridMigrationOption = GridMigrationOption.from(4, 5)
        val destGridMigrationOption = GridMigrationOption.from(8, 3)
        assert(
            sourceGridMigrationOption != null &&
                destGridMigrationOption != null &&
                sourceGridMigrationOption.canMigrate(
                    destGridMigrationOption,
                    isAfterRestore = false,
                )
        ) {
            "Going from 4x5 to 8x3 with isAfterRestore as false caused canMigrate() to return false"
        }
    }

    /* Here we're going from a phone grid to a tablet grid and isAfterRestore is true */
    @Test
    fun canMigrateFrom4x5To6x5NotAfterRestoreShouldReturnFalse() {
        val sourceGridMigrationOption = GridMigrationOption.from(4, 5)
        val destGridMigrationOption = GridMigrationOption.from(6, 5)
        assert(
            sourceGridMigrationOption != null &&
                destGridMigrationOption != null &&
                sourceGridMigrationOption.canMigrate(destGridMigrationOption, isAfterRestore = true)
        ) {
            "Going from 4x5 to 6x5 with isAfterRestore as true caused canMigrate() to return false"
        }
    }
}
