/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.apps

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.platform.test.annotations.Postsubmit
import android.tools.device.apphelpers.MapsAppHelper
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import androidx.test.filters.RequiresDevice
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from Maps app by interacting with the app UI
 *
 * To run this test: `atest WMShellFlickerTests:MapsEnterPipTest`
 *
 * Actions:
 * ```
 *     Launch Maps and start navigation mode
 *     Go home to enter PiP
 * ```
 *
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited from [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.legacy.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class MapsEnterPipTest(flicker: LegacyFlickerTest) : AppsEnterPipTransition(flicker) {
    override val standardAppHelper: MapsAppHelper = MapsAppHelper(instrumentation)

    override val permissions: Array<String> =
        arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION)

    val locationManager: LocationManager =
        instrumentation.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val mainHandler = Handler(Looper.getMainLooper())
    var mockLocationEnabled = false

    val updateLocation =
        object : Runnable {
            override fun run() {
                // early bail out if mocking location is not enabled
                if (!mockLocationEnabled) return
                val location = Location("Googleplex")
                location.latitude = 37.42243438411294
                location.longitude = -122.08426281892311
                location.time = System.currentTimeMillis()
                location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                location.accuracy = 100f
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
                mainHandler.postDelayed(this, 5)
            }
        }

    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            mockLocationEnabled = true
            // postpone first location update to make sure GPS is set as test provider
            mainHandler.postDelayed(updateLocation, 200)

            // normal app open through the Launcher All Apps
            // var mapsAddressOption = "Golden Gate Bridge"
            // standardAppHelper.open()
            // standardAppHelper.doSearch(mapsAddressOption)
            // standardAppHelper.getDirections()
            // standardAppHelper.startNavigation();

            standardAppHelper.launchViaIntent(
                wmHelper,
                MapsAppHelper.getMapIntent(MapsAppHelper.INTENT_NAVIGATION)
            )

            standardAppHelper.waitForNavigationToStart()
        }
    }

    override val defaultTeardown: FlickerBuilder.() -> Unit = {
        teardown {
            standardAppHelper.exit(wmHelper)
            mainHandler.removeCallbacks(updateLocation)
            // the main looper callback might have tried to provide a new location after the
            // provider is no longer in test mode, causing a crash, this prevents it from happening
            mockLocationEnabled = false
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        }
    }

    override val thisTransition: FlickerBuilder.() -> Unit = { transitions { tapl.goHome() } }

    /** Checks [standardAppHelper] layer remains visible throughout the animation */
    @Postsubmit
    @Test
    override fun pipAppLayerAlwaysVisible() {
        // For Maps the transition goes through the UI mode change that adds a snapshot overlay so
        // we assert only start/end layers matching the app instead.
        flicker.assertLayersStart { this.isVisible(standardAppHelper.packageNameMatcher) }
        flicker.assertLayersEnd { this.isVisible(standardAppHelper.packageNameMatcher) }
    }

    @Postsubmit
    @Test
    override fun focusChanges() {
        // in gestural nav the focus goes to different activity on swipe up with auto enter PiP
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.focusChanges()
    }
}
