package com.android.launcher3.model

import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.app.prediction.AppTargetEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WALLPAPERS
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION
import com.android.launcher3.util.LauncherModelHelper
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations

/** Unit tests for [QuickstepModelDelegate]. */
@RunWith(AndroidJUnit4::class)
class QuickstepModelDelegateTest {

    private lateinit var underTest: QuickstepModelDelegate
    private lateinit var modelHelper: LauncherModelHelper

    @Mock private lateinit var target: AppTarget
    @Mock private lateinit var mockedAppTargetEvent: AppTargetEvent
    @Mock private lateinit var allAppsPredictor: AppPredictor
    @Mock private lateinit var hotseatPredictor: AppPredictor
    @Mock private lateinit var widgetRecommendationPredictor: AppPredictor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        modelHelper = LauncherModelHelper()
        underTest = QuickstepModelDelegate(modelHelper.sandboxContext)
        underTest.mAllAppsState.predictor = allAppsPredictor
        underTest.mHotseatState.predictor = hotseatPredictor
        underTest.mWidgetsRecommendationState.predictor = widgetRecommendationPredictor
        underTest.mApp = LauncherAppState.getInstance(modelHelper.sandboxContext)
        underTest.mDataModel = BgDataModel()
    }

    @After
    fun tearDown() {
        modelHelper.destroy()
    }

    @Test
    fun onAppTargetEvent_notifyTarget() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_PREDICTION)

        verify(allAppsPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
        verifyZeroInteractions(hotseatPredictor)
        verifyZeroInteractions(widgetRecommendationPredictor)
    }

    @Test
    fun onWidgetPrediction_notifyWidgetRecommendationPredictor() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_WIDGETS_PREDICTION)

        verifyZeroInteractions(allAppsPredictor)
        verify(widgetRecommendationPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
        verifyZeroInteractions(hotseatPredictor)
    }

    @Test
    fun onHotseatPrediction_notifyHotseatPredictor() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_HOTSEAT_PREDICTION)

        verifyZeroInteractions(allAppsPredictor)
        verifyZeroInteractions(widgetRecommendationPredictor)
        verify(hotseatPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
    }

    @Test
    fun onOtherClient_notifyHotseatPredictor() {
        underTest.onAppTargetEvent(mockedAppTargetEvent, CONTAINER_WALLPAPERS)

        verifyZeroInteractions(allAppsPredictor)
        verifyZeroInteractions(widgetRecommendationPredictor)
        verify(hotseatPredictor).notifyAppTargetEvent(mockedAppTargetEvent)
    }

    @Test
    fun hotseatActionPin_recreateHotSeat() {
        assertSame(underTest.mHotseatState.predictor, hotseatPredictor)
        val appTargetEvent = AppTargetEvent.Builder(target, AppTargetEvent.ACTION_PIN).build()
        underTest.markActive()

        underTest.onAppTargetEvent(appTargetEvent, CONTAINER_HOTSEAT_PREDICTION)

        verify(hotseatPredictor).destroy()
        assertNotSame(underTest.mHotseatState.predictor, hotseatPredictor)
    }

    @Test
    fun hotseatActionUnpin_recreateHotSeat() {
        assertSame(underTest.mHotseatState.predictor, hotseatPredictor)
        underTest.markActive()
        val appTargetEvent = AppTargetEvent.Builder(target, AppTargetEvent.ACTION_UNPIN).build()

        underTest.onAppTargetEvent(appTargetEvent, CONTAINER_HOTSEAT_PREDICTION)

        verify(hotseatPredictor).destroy()
        assertNotSame(underTest.mHotseatState.predictor, hotseatPredictor)
    }

    @Test
    fun container_actionPin_notRecreateHotSeat() {
        assertSame(underTest.mHotseatState.predictor, hotseatPredictor)
        val appTargetEvent = AppTargetEvent.Builder(target, AppTargetEvent.ACTION_UNPIN).build()
        underTest.markActive()

        underTest.onAppTargetEvent(appTargetEvent, CONTAINER_PREDICTION)

        verify(allAppsPredictor, never()).destroy()
        verify(hotseatPredictor, never()).destroy()
        assertSame(underTest.mHotseatState.predictor, hotseatPredictor)
    }
}
