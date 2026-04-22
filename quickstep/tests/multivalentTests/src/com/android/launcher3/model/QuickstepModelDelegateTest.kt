/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.app.prediction.AppTargetEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WALLPAPERS
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION
import com.android.launcher3.util.SandboxApplication
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

/** Unit tests for [QuickstepModelDelegate]. */
@RunWith(AndroidJUnit4::class)
class QuickstepModelDelegateTest {

    @get:Rule val context = SandboxApplication().withModelDependency()

    private lateinit var underTest: QuickstepModelDelegate

    @Mock private lateinit var target: AppTarget
    @Mock private lateinit var mockedAppTargetEvent: AppTargetEvent
    @Mock private lateinit var allAppsPredictor: AppPredictor
    @Mock private lateinit var hotseatPredictor: AppPredictor
    @Mock private lateinit var widgetRecommendationPredictor: AppPredictor
    @Mock private lateinit var itemParserFactory: PredictedItemFactory.Factory

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        underTest =
            QuickstepModelDelegate(
                context,
                context.appComponent.idp,
                context.appComponent.userCache,
                itemParserFactory,
                "", /* dbFileName */
            )
        underTest.mAllPredictionAppsState.predictor = allAppsPredictor
        underTest.mHotseatPredictionState.predictor = hotseatPredictor
        underTest.mWidgetsRecommendationState.predictor = widgetRecommendationPredictor
        underTest.mModel = LauncherAppState.getInstance(context).model
        underTest.mDataModel =
            BgDataModel(
                WidgetsModel(context),
                /* homeDataProvider */ { null },
                /* dumpManager */ mock(),
                /* DaggerSingletonTracker */ mock(),
            )
    }

    @Test
    fun onAppTargetEvent_notifyTarget() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_ALL_APPS_PREDICTION)

        verify(allAppsPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
        verifyNoMoreInteractions(hotseatPredictor)
        verifyNoMoreInteractions(widgetRecommendationPredictor)
    }

    @Test
    fun onWidgetPrediction_notifyWidgetRecommendationPredictor() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_WIDGETS_PREDICTION)

        verifyNoMoreInteractions(allAppsPredictor)
        verify(widgetRecommendationPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
        verifyNoMoreInteractions(hotseatPredictor)
    }

    @Test
    fun onHotseatPrediction_notifyHotseatPredictor() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_HOTSEAT_PREDICTION)

        verifyNoMoreInteractions(allAppsPredictor)
        verifyNoMoreInteractions(widgetRecommendationPredictor)
        verify(hotseatPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
    }

    @Test
    fun onOtherClient_notifyHotseatPredictor() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_WALLPAPERS)

        verifyNoMoreInteractions(allAppsPredictor)
        verifyNoMoreInteractions(widgetRecommendationPredictor)
        verify(hotseatPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
    }

    @Test
    fun hotseatActionPin_recreateHotSeat() {
        assertSame(underTest.mHotseatPredictionState.predictor, hotseatPredictor)
        val appTargetEvent = AppTargetEvent.Builder(target, AppTargetEvent.ACTION_PIN).build()
        underTest.markActive()

        underTest.onAppTargetEvent(appTargetEvent, CONTAINER_HOTSEAT_PREDICTION)

        verify(hotseatPredictor).destroy()
        assertNotSame(underTest.mHotseatPredictionState.predictor, hotseatPredictor)
    }

    @Test
    fun hotseatActionUnpin_recreateHotSeat() {
        assertSame(underTest.mHotseatPredictionState.predictor, hotseatPredictor)
        underTest.markActive()
        val appTargetEvent = AppTargetEvent.Builder(target, AppTargetEvent.ACTION_UNPIN).build()

        underTest.onAppTargetEvent(appTargetEvent, CONTAINER_HOTSEAT_PREDICTION)

        verify(hotseatPredictor).destroy()
        assertNotSame(underTest.mHotseatPredictionState.predictor, hotseatPredictor)
    }

    @Test
    fun container_actionPin_notRecreateHotSeat() {
        assertSame(underTest.mHotseatPredictionState.predictor, hotseatPredictor)
        val appTargetEvent = AppTargetEvent.Builder(target, AppTargetEvent.ACTION_UNPIN).build()
        underTest.markActive()

        underTest.onAppTargetEvent(appTargetEvent, CONTAINER_ALL_APPS_PREDICTION)

        verify(allAppsPredictor, never()).destroy()
        verify(hotseatPredictor, never()).destroy()
        assertSame(underTest.mHotseatPredictionState.predictor, hotseatPredictor)
    }
}
