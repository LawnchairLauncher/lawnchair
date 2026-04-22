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

import CustomDialogDetector
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

/** Test for [CustomDialogDetector]. */
class CustomDialogDetectorTest : Launcher3LintDetectorTest() {
    override fun getDetector(): Detector = CustomDialogDetector()

    override fun getIssues(): List<Issue> = listOf(CustomDialogDetector.ISSUE)

    @Test
    fun classDoesNotExtendDialog_noViolation() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                    package test.pkg

                    class SomeClass
                """
                        .trimIndent()
                ),
                *androidStubs,
            )
            .issues(CustomDialogDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun classDoesExtendDialog_violation() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                    package test.pkg

                    import android.app.Dialog

                    class SomeClass(context: Context) : Dialog(context)
                """
                        .trimIndent()
                ),
                *androidStubs,
            )
            .issues(CustomDialogDetector.ISSUE)
            .run()
            .expect(
                ("""
                src/test/pkg/SomeClass.kt:5: Error: Class implements Dialog [IllegalUseOfCustomDialog]
                class SomeClass(context: Context) : Dialog(context)
                      ~~~~~~~~~
                1 errors, 0 warnings
                """)
                    .trimIndent()
            )
    }
}
