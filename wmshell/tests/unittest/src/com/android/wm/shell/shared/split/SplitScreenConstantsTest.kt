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

package com.android.wm.shell.common.split

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.wm.shell.shared.split.SplitScreenConstants
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitScreenConstantsTest {

    /**
     * Ensures that some important constants are not changed from their set values. These values
     * are persisted in user-defined app pairs, and changing them will break things.
     */
    @Test
    fun shouldKeepExistingConstantValues() {
        assertEquals(
            "the value of SPLIT_POSITION_TOP_OR_LEFT should be 0",
            0,
            SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT,
        )
        assertEquals(
            "the value of SPLIT_POSITION_BOTTOM_OR_RIGHT should be 1",
            1,
            SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT,
        )
        assertEquals(
            "the value of SNAP_TO_2_33_66 should be 0",
            0,
            SplitScreenConstants.SNAP_TO_2_33_66,
        )
        assertEquals(
            "the value of SNAP_TO_2_50_50 should be 1",
            1,
            SplitScreenConstants.SNAP_TO_2_50_50,
        )
        assertEquals(
            "the value of SNAP_TO_2_66_33 should be 2",
            2,
            SplitScreenConstants.SNAP_TO_2_66_33,
        )
        assertEquals(
            "the value of SNAP_TO_2_90_10 should be 3",
            3,
            SplitScreenConstants.SNAP_TO_2_90_10,
        )
        assertEquals(
            "the value of SNAP_TO_2_10_90 should be 4",
            4,
            SplitScreenConstants.SNAP_TO_2_10_90,
        )
        assertEquals(
            "the value of SNAP_TO_3_33_33_33 should be 5",
            5,
            SplitScreenConstants.SNAP_TO_3_33_33_33,
        )
        assertEquals(
            "the value of SNAP_TO_3_45_45_10 should be 6",
            6,
            SplitScreenConstants.SNAP_TO_3_45_45_10,
        )
        assertEquals(
            "the value of SNAP_TO_3_10_45_45 should be 7",
            7,
            SplitScreenConstants.SNAP_TO_3_10_45_45,
        )
    }
}