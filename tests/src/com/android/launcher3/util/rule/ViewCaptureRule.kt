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
import com.android.app.viewcapture.SimpleViewCapture
import com.android.app.viewcapture.ViewCapture.MAIN_EXECUTOR
import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * This JUnit TestRule registers a listener for activity lifecycle events to attach a ViewCapture
 * instance that other test rules use to dump the timelapse hierarchy upon an error during a test.
 *
 * This rule will not work in OOP tests that don't have access to the activity under test.
 */
class ViewCaptureRule : TestRule {
    val viewCapture = SimpleViewCapture("test-view-capture")

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val windowListenerCloseables = mutableListOf<SafeCloseable>()

                val lifecycleCallbacks =
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

                val application = ApplicationProvider.getApplicationContext<Application>()
                application.registerActivityLifecycleCallbacks(lifecycleCallbacks)

                try {
                    base.evaluate()
                } finally {
                    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)

                    // Clean up ViewCapture references here rather than in onActivityDestroyed so
                    // test code can access view hierarchy capture. onActivityDestroyed would delete
                    // view capture data before FailureWatcher could output it as a test artifact.
                    // This is on the main thread to avoid a race condition where the onDrawListener
                    // is removed while onDraw is running, resulting in an IllegalStateException.
                    MAIN_EXECUTOR.execute { windowListenerCloseables.onEach(SafeCloseable::close) }
                }
            }
        }
    }
}
