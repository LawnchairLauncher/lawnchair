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

package com.android.wm.shell.util

import android.window.TransitionInfo.Change
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEvent
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventFactory

@DslMarker
annotation class LetterboxLifecycleEventFactoryTagMarker

@LetterboxLifecycleEventFactoryTagMarker
class LetterboxLifecycleEventTestContext(
    private val testSubjectFactory: () -> LetterboxLifecycleEventFactory
) {

    private lateinit var inputObject: Change

    fun inputChange(builder: ChangeTestInputBuilder.() -> Unit): Change {
        val inputFactoryObj = ChangeTestInputBuilder()
        inputFactoryObj.builder()
        return inputFactoryObj.build().apply {
            inputObject = this
        }
    }

    fun validateCreateLifecycleEvent(verifier: (LetterboxLifecycleEvent?) -> Unit) {
        // We execute the test subject using the input
        verifier(testSubjectFactory().createLifecycleEvent(inputObject))
    }

    fun validateCanHandle(verifier: (Boolean) -> Unit) {
        // We execute the test subject using the input
        verifier(testSubjectFactory().canHandle(inputObject))
    }
}

/**
 * Function to run tests for the different [LetterboxLifecycleEventFactory] implementations.
 */
fun testLetterboxLifecycleEventFactory(
    testSubjectFactory: () -> LetterboxLifecycleEventFactory,
    init: LetterboxLifecycleEventTestContext.() -> Unit
): LetterboxLifecycleEventTestContext {
    val testContext = LetterboxLifecycleEventTestContext(testSubjectFactory)
    testContext.init()
    return testContext
}
