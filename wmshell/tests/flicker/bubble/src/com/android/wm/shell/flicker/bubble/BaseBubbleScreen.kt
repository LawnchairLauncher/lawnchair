/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.flicker.bubble

import android.app.INotificationManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.ServiceManager
import android.tools.Rotation
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.FlickerTestData
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.SYSTEMUI_PACKAGE
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.helpers.LaunchBubbleHelper
import com.android.server.wm.flicker.helpers.MultiWindowUtils
import com.android.wm.shell.flicker.BaseTest
import org.junit.runners.Parameterized

/** Base configurations for Bubble flicker tests */
abstract class BaseBubbleScreen(flicker: LegacyFlickerTest) : BaseTest(flicker) {

    protected val context: Context = instrumentation.context
    protected val testApp = LaunchBubbleHelper(instrumentation)

    private val notifyManager =
        INotificationManager.Stub.asInterface(
            ServiceManager.getService(Context.NOTIFICATION_SERVICE)
        )

    private val uid =
        context.packageManager
            .getApplicationInfo(testApp.packageName, PackageManager.ApplicationInfoFlags.of(0))
            .uid

    @JvmOverloads
    protected open fun buildTransition(
        extraSpec: FlickerBuilder.() -> Unit = {}
    ): FlickerBuilder.() -> Unit {
        return {
            setup {
                MultiWindowUtils.executeShellCommand(
                    instrumentation,
                    "settings put secure force_hide_bubbles_user_education 1"
                )
                notifyManager.setBubblesAllowed(
                    testApp.packageName,
                    uid,
                    NotificationManager.BUBBLE_PREFERENCE_ALL
                )
                testApp.launchViaIntent(wmHelper)
                waitAndGetAddBubbleBtn()
                waitAndGetCancelAllBtn()
            }

            teardown {
                MultiWindowUtils.executeShellCommand(
                    instrumentation,
                    "settings put secure force_hide_bubbles_user_education 0"
                )
                notifyManager.setBubblesAllowed(
                    testApp.packageName,
                    uid,
                    NotificationManager.BUBBLE_PREFERENCE_NONE
                )
                device.wait(
                    Until.gone(By.res(SYSTEM_UI_PACKAGE, BUBBLE_RES_NAME)),
                    FIND_OBJECT_TIMEOUT
                )
                testApp.exit(wmHelper)
            }

            extraSpec(this)
        }
    }

    protected fun FlickerTestData.waitAndGetAddBubbleBtn(): UiObject2? =
        device.wait(Until.findObject(By.text("Add Bubble")), FIND_OBJECT_TIMEOUT)
    protected fun FlickerTestData.waitAndGetCancelAllBtn(): UiObject2? =
        device.wait(Until.findObject(By.text("Cancel All Bubble")), FIND_OBJECT_TIMEOUT)

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )

        const val FIND_OBJECT_TIMEOUT = 4000L
        const val SYSTEM_UI_PACKAGE = SYSTEMUI_PACKAGE
        const val BUBBLE_RES_NAME = "bubble_view"
    }
}
