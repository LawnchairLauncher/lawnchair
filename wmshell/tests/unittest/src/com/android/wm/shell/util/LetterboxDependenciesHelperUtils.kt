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

import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper

@DslMarker
annotation class LetterboxDependenciesHelperTagMarker

@LetterboxDependenciesHelperTagMarker
class LetterboxDependenciesHelperTestContext(
    private val testSubjectFactory: () -> LetterboxDependenciesHelper
) : BaseChangeTestContext() {

    val deskId: Int = -1

    fun validateShouldSupportInputSurface(verifier: (Boolean) -> Unit) {
        // We execute the test subject using the input
        verifier(testSubjectFactory().shouldSupportInputSurface(inputObject))
    }
}

/**
 * Function to run tests for the different [LetterboxDependenciesHelper] implementations.
 */
fun testLetterboxDependenciesHelper(
    testSubjectFactory: () -> LetterboxDependenciesHelper,
    init: LetterboxDependenciesHelperTestContext.() -> Unit
): LetterboxDependenciesHelperTestContext {
    val testContext = LetterboxDependenciesHelperTestContext(testSubjectFactory)
    testContext.init()
    return testContext
}
