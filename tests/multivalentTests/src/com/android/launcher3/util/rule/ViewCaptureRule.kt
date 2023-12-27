/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.util.rule

import android.app.Activity
import android.app.Application
import android.media.permission.SafeCloseable
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.viewcapture.SimpleViewCapture
import com.android.app.viewcapture.ViewCapture.MAIN_EXECUTOR
import com.android.app.viewcapture.data.ExportedData
import com.android.launcher3.tapl.TestHelpers
import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter
import com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT
import com.android.launcher3.util.viewcapture_analysis.ViewCaptureAnalyzer
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.function.Supplier
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * This JUnit TestRule registers a listener for activity lifecycle events to attach a ViewCapture
 * instance that other test rules use to dump the timelapse hierarchy upon an error during a test.
 *
 * This rule will not work in OOP tests that don't have access to the activity under test.
 */
class ViewCaptureRule(var alreadyOpenActivitySupplier: Supplier<Activity?>) : TestRule {
    private val viewCapture = SimpleViewCapture("test-view-capture")
    var viewCaptureData: ExportedData? = null
        private set

    override fun apply(base: Statement, description: Description): Statement {
        // Skip view capture collection in Launcher3 tests to avoid hidden API check exception.
        if (
            "com.android.launcher3.tests" ==
                InstrumentationRegistry.getInstrumentation().context.packageName
        )
            return base

        return object : Statement() {
            override fun evaluate() {
                viewCaptureData = null
                val windowListenerCloseables = mutableListOf<SafeCloseable>()

                startCapturingExistingActivity(windowListenerCloseables)

                val lifecycleCallbacks =
                    object : ActivityLifecycleCallbacksAdapter {
                        override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
                            startCapture(windowListenerCloseables, activity)
                        }

                        override fun onActivityDestroyed(activity: Activity) {
                            viewCapture.stopCapture(activity.window.decorView)
                        }
                    }

                val application = ApplicationProvider.getApplicationContext<Application>()
                application.registerActivityLifecycleCallbacks(lifecycleCallbacks)

                try {
                    base.evaluate()
                } finally {
                    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)

                    viewCaptureData =
                        viewCapture.getExportedData(ApplicationProvider.getApplicationContext())

                    // Clean up ViewCapture references here rather than in onActivityDestroyed so
                    // test code can access view hierarchy capture. onActivityDestroyed would delete
                    // view capture data before FailureWatcher could output it as a test artifact.
                    // This is on the main thread to avoid a race condition where the onDrawListener
                    // is removed while onDraw is running, resulting in an IllegalStateException.
                    MAIN_EXECUTOR.execute { windowListenerCloseables.onEach(SafeCloseable::close) }
                }

                analyzeViewCapture(description)
            }

            private fun startCapturingExistingActivity(
                windowListenerCloseables: MutableCollection<SafeCloseable>
            ) {
                val alreadyOpenActivity = alreadyOpenActivitySupplier.get()
                if (alreadyOpenActivity != null) {
                    startCapture(windowListenerCloseables, alreadyOpenActivity)
                }
            }

            private fun startCapture(
                windowListenerCloseables: MutableCollection<SafeCloseable>,
                activity: Activity
            ) {
                windowListenerCloseables.add(
                    viewCapture.startCapture(
                        activity.window.decorView,
                        "${description.testClass?.simpleName}.${description.methodName}"
                    )
                )
            }
        }
    }

    private fun analyzeViewCapture(description: Description) {
        // OOP tests don't produce ViewCapture data
        if (!TestHelpers.isInLauncherProcess()) return

        // Due to flakiness of ViewCapture verifier, don't run the check in presubmit
        if (TestStabilityRule.getRunFlavor() != PLATFORM_POSTSUBMIT) return

        var frameCount = 0
        for (i in 0 until viewCaptureData!!.windowDataCount) {
            frameCount += viewCaptureData!!.getWindowData(i).frameDataCount
        }

        val mayProduceNoFrames = description.getAnnotation(MayProduceNoFrames::class.java) != null
        assertTrue("Empty ViewCapture data", mayProduceNoFrames || frameCount > 0)

        val anomalies: Map<String, String> = ViewCaptureAnalyzer.getAnomalies(viewCaptureData)
        if (!anomalies.isEmpty()) {
            val diagFile = FailureWatcher.diagFile(description, "ViewAnomalies", "txt")
            try {
                OutputStreamWriter(BufferedOutputStream(FileOutputStream(diagFile))).use { writer ->
                    writer.write("View animation anomalies detected.\r\n")
                    writer.write(
                        "To suppress an anomaly for a view, add its full path to the PATHS_TO_IGNORE list in the corresponding AnomalyDetector.\r\n"
                    )
                    writer.write("List of views with animation anomalies:\r\n")

                    for ((viewPath, message) in anomalies) {
                        writer.write("View: $viewPath\r\n        $message\r\n")
                    }
                }
            } catch (ex: IOException) {
                throw RuntimeException(ex)
            }

            val (viewPath, message) = anomalies.entries.first()
            fail(
                "${anomalies.size} view(s) had animation anomalies during the test, including view: $viewPath: $message\r\nSee ${diagFile.name} for details."
            )
        }
    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    annotation class MayProduceNoFrames
}
