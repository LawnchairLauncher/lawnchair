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

package com.android.wm.shell

import android.app.Instrumentation
import android.os.Build
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.shared.desktopmode.DesktopState
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Rule that allow tests to be executed only on [ProjectedOnly] or [ExtendedOnly].
 */
class DesktopDeviceTypeRule : TestRule {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val desktopState = DesktopState.fromContext(context)
    private val canEnterExtended = desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)

    override fun apply(base: Statement, description: Description): Statement {
        val projectedOnly = description.hasAnnotationInClassOrMethod(ProjectedOnly::class.java)
        val extendedOnly = description.hasAnnotationInClassOrMethod(ExtendedOnly::class.java)
        return when {
            projectedOnly && extendedOnly -> throw RuntimeException(
                "Test cannot be annotated with both @ProjectedOnly and @ExtendedOnly")
            projectedOnly && canEnterExtended -> wrongDeviceTypeStatement(
                "Skipping test on ${Build.PRODUCT} as it doesn't support projected mode.")
            extendedOnly && !canEnterExtended -> wrongDeviceTypeStatement(
                "Skipping test on ${Build.PRODUCT} as it doesn't support extended mode.")
            else -> base
        }
    }
}

private fun <T : Annotation> Description.hasAnnotationInClassOrMethod(
    annotationType: Class<T>
): Boolean = (annotations + testClass.annotations).any { annotationType.isInstance(it) }

private fun wrongDeviceTypeStatement(message: String) =
    object : Statement() {
        override fun evaluate() {
            throw AssumptionViolatedException(message)
        }
    }

/** The test will run only on devices that support projected mode. */
@Retention(RUNTIME)
@Target(CLASS, FUNCTION)
annotation class ProjectedOnly

/** The test will run only on devices that support extended mode. */
@Retention(RUNTIME)
@Target(CLASS, FUNCTION)
annotation class ExtendedOnly