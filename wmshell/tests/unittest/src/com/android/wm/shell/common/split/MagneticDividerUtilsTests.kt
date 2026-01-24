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
package com.android.wm.shell.common.split

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.builder.MotionBuilderContext
import com.android.mechanics.testing.DirectionalMotionSpecSubject.Companion.assertThat
import com.android.mechanics.testing.FakeMotionSpecBuilderContext
import com.android.mechanics.testing.MotionSpecSubject.Companion.assertThat
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget
import com.android.wm.shell.common.split.MagneticDividerUtils.generateMotionSpec
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.google.common.truth.Truth.assertThat
import kotlin.math.max
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MagneticDividerUtilsTests : MotionBuilderContext by FakeMotionSpecBuilderContext.Default {

    @Test
    fun generateMotionSpec_worksOnThisDeviceWithoutCrashing() {
        // Retrieve long edge and motion builder context (density) from this device.
        val resources = InstrumentationRegistry.getInstrumentation().context.resources
        val longEdge =
            max(
                    resources.displayMetrics.heightPixels.toDouble(),
                    resources.displayMetrics.widthPixels.toDouble(),
                )
                .toInt()

        val targets =
            listOf(
                SnapTarget(0, SplitScreenConstants.SNAP_TO_START_AND_DISMISS),
                SnapTarget(longEdge / 10, SplitScreenConstants.SNAP_TO_2_10_90),
                SnapTarget(longEdge / 2, SplitScreenConstants.SNAP_TO_2_50_50),
                SnapTarget(longEdge - (longEdge / 10), SplitScreenConstants.SNAP_TO_2_90_10),
                SnapTarget(longEdge, SplitScreenConstants.SNAP_TO_END_AND_DISMISS),
            )

        // Check that a MotionSpec gets created without crashing. A crash can happen if the dp
        // values set MagneticDividerUtils are large enough that the snap zones overlap on smaller
        // screens.
        assertThat(generateMotionSpec(targets, resources)).isNotNull()
    }

    @Test
    fun generateMotionSpec_specMatchesExpectations() {
        val zoneHalfSizePx = MagneticDividerUtils.DEFAULT_MAGNETIC_ATTACH_THRESHOLD.toPx()

        val targets =
            listOf(
                SnapTarget(0, SplitScreenConstants.SNAP_TO_START_AND_DISMISS),
                SnapTarget(500, SplitScreenConstants.SNAP_TO_2_50_50),
                SnapTarget(1000, SplitScreenConstants.SNAP_TO_END_AND_DISMISS),
            )

        val generated = generateMotionSpec(targets)
        assertThat(generated.minDirection).isSameInstanceAs(generated.maxDirection)

        val spec = generated.minDirection

        assertThat(spec)
            .breakpoints()
            .positions()
            .containsExactly(0f, 500f - zoneHalfSizePx, 500f + zoneHalfSizePx, 1000f)
            .inOrder()

        assertThat(spec)
            .mappingsMatch(
                Mapping.Fixed(0f),
                Mapping.Identity,
                Mapping.Linear(0.5f, offset = 250f),
                Mapping.Identity,
                Mapping.Fixed(1000f),
            )

        assertThat(spec)
            .semantics()
            .withKey(MagneticDividerUtils.SNAP_POSITION_KEY)
            .containsExactly(
                SplitScreenConstants.SNAP_TO_START_AND_DISMISS,
                null,
                SplitScreenConstants.SNAP_TO_2_50_50,
                null,
                SplitScreenConstants.SNAP_TO_END_AND_DISMISS,
            )
    }
}
