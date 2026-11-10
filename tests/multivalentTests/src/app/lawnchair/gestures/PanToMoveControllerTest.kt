/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package app.lawnchair.gestures

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PanToMoveControllerTest {

    @Test
    fun tiltThreshold_decreasesAsSensitivityIncreases() {
        assertThat(thresholdForSensitivity(0f, isGyroscope = false)).isEqualTo(0.5f)
        assertThat(thresholdForSensitivity(0.5f, isGyroscope = false)).isEqualTo(0.35f)
        assertThat(thresholdForSensitivity(1f, isGyroscope = false)).isEqualTo(0.2f)
    }

    @Test
    fun tiltThreshold_clampsSensitivityToSupportedRange() {
        assertThat(thresholdForSensitivity(-1f, isGyroscope = false)).isEqualTo(0.5f)
        assertThat(thresholdForSensitivity(2f, isGyroscope = false)).isEqualTo(0.2f)
    }
}
