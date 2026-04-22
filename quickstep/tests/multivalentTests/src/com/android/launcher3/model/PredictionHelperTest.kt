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

package com.android.launcher3.model

import android.app.prediction.AppTargetEvent
import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PIN_WIDGETS
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.model.PredictionHelper.BUNDLE_KEY_ADDED_APP_WIDGETS
import com.android.launcher3.model.PredictionHelper.BUNDLE_KEY_PIN_EVENTS
import com.android.launcher3.model.PredictionHelper.getBundleForHotseatPredictions
import com.android.launcher3.model.PredictionHelper.getBundleForWidgetPredictions
import com.android.launcher3.model.PredictionHelper.isTrackedForHotseatPrediction
import com.android.launcher3.model.PredictionHelper.isTrackedForWidgetPrediction
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.ModelTestExtensions.initItems
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class PredictionHelperTest {

    @get:Rule val context = SandboxApplication()
    private var itemIdCounter: Int = 0

    @Test
    fun isTrackedForHotseatPrediction_true_for_valid_items() {
        createAppInfo("test").let {
            assertTrue(it.isTrackedForHotseatPrediction())
            assertTrue(it.buildProto(context).isTrackedForHotseatPrediction())
        }

        createAppInfo("test", CONTAINER_HOTSEAT).let {
            assertTrue(it.isTrackedForHotseatPrediction())
            assertTrue(it.buildProto(context).isTrackedForHotseatPrediction())
        }
    }

    @Test
    fun isTrackedForHotseatPrediction_false_for_invalid_items() {
        createAppInfo("test", CONTAINER_DESKTOP, 3).let {
            assertFalse(it.isTrackedForHotseatPrediction())
            assertFalse(it.buildProto(context).isTrackedForHotseatPrediction())
        }

        createAppInfo("test", 2 /* folder */).let {
            assertFalse(it.isTrackedForHotseatPrediction())
            assertFalse(it.buildProto(context).isTrackedForHotseatPrediction())
        }

        createAppInfo("test", CONTAINER_ALL_APPS).let {
            assertFalse(it.isTrackedForHotseatPrediction())
            assertFalse(it.buildProto(context).isTrackedForHotseatPrediction())
        }
    }

    @Test
    fun isTrackedForWidgetPrediction_true_for_valid_items() {
        createWidgetInfoInfo("test").let {
            assertTrue(it.isTrackedForWidgetPrediction())
            assertTrue(it.buildProto(context).isTrackedForWidgetPrediction())
        }
    }

    @Test
    fun isTrackedForWidgetPrediction_false_for_invalid_items() {
        createAppInfo("test").let {
            assertFalse(it.isTrackedForWidgetPrediction())
            assertFalse(it.buildProto(context).isTrackedForWidgetPrediction())
        }

        createWidgetInfoInfo("test", CONTAINER_PIN_WIDGETS).let {
            assertFalse(it.isTrackedForWidgetPrediction())
            assertFalse(it.buildProto(context).isTrackedForWidgetPrediction())
        }
    }

    @Test
    fun getBundleForHotseatPredictions_contains_pin_events() {
        val dataModel = BgDataModel(mock(), mock(), mock(), mock())
        dataModel.initItems(
            createAppInfo("test1"),
            createAppInfo("test2", CONTAINER_HOTSEAT, 2),
            createAppInfo("test3", 2 /* folder */),
        )

        val items =
            getBundleForHotseatPredictions(context, dataModel)
                .getParcelableArrayList<AppTargetEvent>(BUNDLE_KEY_PIN_EVENTS)!!
        assertThat(items).hasSize(2)
        assertThat(items[0].launchLocation).isEqualTo("workspace/0/[0,0]/[1,1]")
        assertThat(items[0].target!!.packageName).isEqualTo("test1")

        assertThat(items[1].launchLocation).isEqualTo("hotseat/2/[2,0]/[1,1]")
        assertThat(items[1].target!!.packageName).isEqualTo("test2")
    }

    @Test
    fun getBundleForWidgetPredictions_contains_pin_events() {
        val dataModel = BgDataModel(mock(), mock(), mock(), mock())
        dataModel.initItems(
            createAppInfo("test1"),
            createAppInfo("test2", CONTAINER_HOTSEAT, 2),
            createWidgetInfoInfo("test3"),
        )

        val items =
            getBundleForWidgetPredictions(context, dataModel)
                .getParcelableArrayList<AppTargetEvent>(BUNDLE_KEY_ADDED_APP_WIDGETS)!!
        assertThat(items).hasSize(1)
        assertThat(items[0].launchLocation).isEqualTo("workspace/0/[0,0]/[1,1]")
        assertThat(items[0].target!!.packageName).isEqualTo("test3")
    }

    private fun createAppInfo(
        pkg: String,
        container: Int = CONTAINER_DESKTOP,
        screenId: Int = FIRST_SCREEN_ID,
    ) =
        AppInfo().apply {
            this.id = itemIdCounter
            this.container = container
            this.screenId = screenId
            this.intent = Intent().setComponent(ComponentName(pkg, pkg))
            this.componentName = ComponentName(pkg, pkg)
            this.cellX = 0
            this.cellY = 0

            itemIdCounter++
        }

    private fun createWidgetInfoInfo(
        pkg: String,
        container: Int = CONTAINER_DESKTOP,
        screenId: Int = FIRST_SCREEN_ID,
    ) =
        LauncherAppWidgetInfo().apply {
            this.id = itemIdCounter
            this.container = container
            this.screenId = screenId
            this.providerName = ComponentName(pkg, pkg)
            this.cellX = 0
            this.cellY = 0

            itemIdCounter++
        }
}
