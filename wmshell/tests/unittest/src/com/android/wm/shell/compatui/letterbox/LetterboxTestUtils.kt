/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.obtain
import android.view.SurfaceControl
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.verification.VerificationMode

/**
 * @return A [SurfaceControl.Transaction] mock supporting chaining for some operations. Please
 *         add other operations if needed.
 */
fun getTransactionMock(): SurfaceControl.Transaction = mock<SurfaceControl.Transaction>().apply {
    doReturn(this).`when`(this).setLayer(anyOrNull(), anyOrNull())
    doReturn(this).`when`(this).setColorSpaceAgnostic(anyOrNull(), anyOrNull())
    doReturn(this).`when`(this).setPosition(anyOrNull(), any(), any())
    doReturn(this).`when`(this).setWindowCrop(anyOrNull(), any(), any())
}

/**
 * @return A [LetterboxInputSurfaceBuilder] mock to use in tests.
 */
fun getLetterboxInputSurfaceBuilderMock() = mock<LetterboxInputSurfaceBuilder>().apply {
    doReturn(SurfaceControl()).`when`(this).createInputSurface(
        any(),
        any(),
        any(),
        any()
    )
}

// Utility to make verification mode depending on a [Boolean].
fun Boolean.asMode(): VerificationMode = if (this) times(1) else never()

// Utility matchers to use for the main types as Mockito [VerificationMode].
object LetterboxMatchers {
    fun Int.asAnyMode() = asAnyMode { this < 0 }
    fun Float.asAnyMode() = asAnyMode { this < 0f }
    fun String.asAnyMode() = asAnyMode { this.isEmpty() }
}

object LetterboxEvents {
    fun motionEventAt(x: Float, y: Float) =
        obtain(0, 10, ACTION_DOWN, x, y, 0)
}

private inline fun <reified T : Any> T.asAnyMode(condition: () -> Boolean) =
    (if (condition()) any() else eq(this))
