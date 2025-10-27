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

package com.android.internal.launcher3.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import java.io.File
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Abstract class that should be used by any test for launcher 3 lint detectors.
 *
 * When you write your test, ensure that you pass [androidStubs] as part of your [TestFiles]
 * definition.
 */
@RunWith(JUnit4::class)
abstract class Launcher3LintDetectorTest : LintDetectorTest() {

    /**
     * Customize the lint task to disable SDK usage completely. This ensures that running the tests
     * in Android Studio has the same result as running the tests in atest
     */
    override fun lint(): TestLintTask =
        super.lint().allowMissingSdk(true).sdkHome(File("/dev/null"))

    companion object {
        private val libraryNames =
            arrayOf(
                "androidx.annotation_annotation.jar",
                "dagger2.jar",
                "framework.jar",
                "kotlinx-coroutines-core.jar",
            )

        /**
         * This file contains stubs of framework APIs and System UI classes for testing purposes
         * only. The stubs are not used in the lint detectors themselves.
         */
        val androidStubs =
            libraryNames
                .map { TestFiles.LibraryReferenceTestFile(File(it).canonicalFile) }
                .toTypedArray()
    }
}
