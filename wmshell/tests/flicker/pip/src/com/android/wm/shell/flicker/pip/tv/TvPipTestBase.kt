/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.tv

import android.app.ActivityManager
import android.app.IActivityManager
import android.app.IProcessObserver
import android.os.SystemClock
import android.tools.helpers.wakeUpAndGoToHomeScreen
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Surface.ROTATION_0
import android.view.Surface.rotationToString
import com.android.wm.shell.flicker.utils.SYSTEM_UI_PACKAGE_NAME
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before

abstract class TvPipTestBase : PipTestBase(rotationToString(ROTATION_0), ROTATION_0) {

    private val systemUiProcessObserver = SystemUiProcessObserver()
    protected val wmHelper = WindowManagerStateHelper()

    @Before
    final override fun televisionSetUp() {
        // Should run only on TVs.
        assumeTrue(isTelevision)

        systemUiProcessObserver.start()

        uiDevice.wakeUpAndGoToHomeScreen()
    }

    @After
    open fun tearDown() {
        if (!isTelevision) return

        testApp.exit()

        // Wait for 1 second, and check if the SystemUI has been alive and well since the start.
        SystemClock.sleep(AFTER_TEXT_PROCESS_CHECK_DELAY)
        systemUiProcessObserver.stop()
        assertFalse("SystemUI has died during test execution", systemUiProcessObserver.hasDied)
    }

    protected fun fail(message: String): Nothing = throw AssertionError(message)

    inner class SystemUiProcessObserver : IProcessObserver.Stub() {
        private val activityManager: IActivityManager = ActivityManager.getService()
        private val uiAutomation = instrumentation.uiAutomation
        private val systemUiUid = packageManager.getPackageUid(SYSTEM_UI_PACKAGE_NAME, 0)
        var hasDied: Boolean = false

        fun start() {
            hasDied = false
            uiAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.SET_ACTIVITY_WATCHER
            )
            activityManager.registerProcessObserver(this)
        }

        fun stop() {
            activityManager.unregisterProcessObserver(this)
            uiAutomation.dropShellPermissionIdentity()
        }

        override fun onProcessStarted(
            pid: Int,
            processUid: Int,
            packageUid: Int,
            packageName: String,
            processName: String
        ) {}

        override fun onForegroundActivitiesChanged(pid: Int, uid: Int, foreground: Boolean) {}

        override fun onForegroundServicesChanged(pid: Int, uid: Int, serviceTypes: Int) {}

        override fun onProcessDied(pid: Int, uid: Int) {
            if (uid == systemUiUid) hasDied = true
        }
    }

    companion object {
        private const val AFTER_TEXT_PROCESS_CHECK_DELAY = 1_000L // 1 sec
    }
}
