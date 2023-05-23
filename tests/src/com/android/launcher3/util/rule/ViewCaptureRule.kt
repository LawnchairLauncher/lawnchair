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
import android.util.Log
import androidx.annotation.AnyThread
import androidx.test.core.app.ApplicationProvider
import com.android.app.viewcapture.SimpleViewCapture
import com.android.app.viewcapture.ViewCapture.MAIN_EXECUTOR
import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

private const val TAG = "ViewCaptureRule"

/**
 * This JUnit TestRule registers a listener for activity lifecycle events to attach a ViewCapture
 * instance that other test rules use to dump the timelapse hierarchy upon an error during a test.
 *
 * This rule will not work in OOP tests that don't have access to the activity under test.
 */
class ViewCaptureRule : TestWatcher() {
    private val viewCapture = SimpleViewCapture("test-view-capture")
    private val windowListenerCloseables = mutableListOf<SafeCloseable>()

    override fun apply(base: Statement, description: Description): Statement {
        val testWatcherStatement = super.apply(base, description)

        return object : Statement() {
            override fun evaluate() {
                val lifecycleCallbacks = createLifecycleCallbacks(description)
                with(ApplicationProvider.getApplicationContext<Application>()) {
                    registerActivityLifecycleCallbacks(lifecycleCallbacks)
                    try {
                        testWatcherStatement.evaluate()
                    } finally {
                        unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
                    }
                }
            }
        }
    }

    private fun createLifecycleCallbacks(description: Description) =
        object : ActivityLifecycleCallbacksAdapter {
            override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
                super.onActivityCreated(activity, bundle)
                windowListenerCloseables.add(
                    viewCapture.startCapture(
                        activity.window.decorView,
                        "${description.testClass?.simpleName}.${description.methodName}"
                    )
                )
            }

            override fun onActivityDestroyed(activity: Activity) {
                super.onActivityDestroyed(activity)
                viewCapture.stopCapture(activity.window.decorView)
            }
        }

    override fun succeeded(description: Description) = cleanup()

    /** If the test fails, this function will output the ViewCapture information. */
    override fun failed(e: Throwable, description: Description) {
        super.failed(e, description)

        val testName = "${description.testClass.simpleName}.${description.methodName}"
        val application: Application = ApplicationProvider.getApplicationContext()
        val zip = File(application.filesDir, "ViewCapture-$testName.zip")

        ZipOutputStream(FileOutputStream(zip)).use {
            it.putNextEntry(ZipEntry("FS/data/misc/wmtrace/failed_test.vc"))
            viewCapture.dumpTo(it, ApplicationProvider.getApplicationContext())
            it.closeEntry()
        }
        cleanup()

        Log.d(
            TAG,
            "Failed $testName due to ${e::class.java.simpleName}.\n" +
                "\tUse go/web-hv to open dump file: \n\t\t${zip.absolutePath}"
        )
    }

    /**
     * Clean up ViewCapture references can't happen in onActivityDestroyed otherwise view
     * hierarchies would be erased before they could be outputted.
     *
     * This is on the main thread to avoid a race condition where the onDrawListener is removed
     * while onDraw is running, resulting in an IllegalStateException.
     */
    @AnyThread
    private fun cleanup() {
        MAIN_EXECUTOR.execute { windowListenerCloseables.onEach(SafeCloseable::close) }
    }
}
