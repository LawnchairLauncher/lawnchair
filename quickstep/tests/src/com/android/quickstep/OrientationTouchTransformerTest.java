/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */

package com.android.quickstep;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.util.NavigationMode.NO_BUTTON;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.RotationUtils;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OrientationTouchTransformerTest {

    private static final Size NORMAL_SCREEN_SIZE = new Size(1080, 2280);
    private static final Size LARGE_SCREEN_SIZE = new Size(1080, 3280);
    private static final float DENSITY_DISPLAY_METRICS = 3.0f;

    private OrientationTouchTransformer mTouchTransformer;

    Resources mResources;
    private DisplayController.Info mInfo;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mResources = mock(Resources.class);
        when(mResources.getBoolean(anyInt())).thenReturn(true);
        when(mResources.getDimension(anyInt())).thenReturn(10.0f);
        when(mResources.getDimensionPixelSize(anyInt())).thenReturn(10);
        DisplayMetrics mockDisplayMetrics = new DisplayMetrics();
        mockDisplayMetrics.density = DENSITY_DISPLAY_METRICS;
        when(mResources.getDisplayMetrics()).thenReturn(mockDisplayMetrics);
        mInfo = createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_0);
        mTouchTransformer = new OrientationTouchTransformer(mResources, NO_BUTTON, () -> 0);
    }

    @Test
    public void disabledMultipleRegions_shouldOverrideFirstRegion() {
        float portraitRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0) + 1;
        float landscapeRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_90) + 1;

        mTouchTransformer.createOrAddTouchRegion(mInfo);
        tapAndAssertTrue(100, portraitRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertFalse(100, landscapeRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertTrue(0, portraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertFalse(0, landscapeRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));

        // Override region
        mTouchTransformer
            .createOrAddTouchRegion(createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_90));
        tapAndAssertFalse(100, portraitRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertTrue(100, landscapeRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertFalse(0, portraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertTrue(0, landscapeRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));

        // Override region again
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        tapAndAssertTrue(100, portraitRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertFalse(100, landscapeRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertTrue(0, portraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertFalse(0, landscapeRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
    }

    @Test
    public void enableMultipleRegions_shouldOverrideFirstRegion() {
        float portraitRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0) + 1;
        float landscapeRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_90) + 1;

        mTouchTransformer
            .createOrAddTouchRegion(createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_90));
        tapAndAssertFalse(100, portraitRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertTrue(100, landscapeRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertFalse(0, portraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertTrue(0, landscapeRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        // We have to add 0 rotation second so that gets set as the current rotation, otherwise
        // matrix transform will fail (tests only work in Portrait at the moment)
        mTouchTransformer.enableMultipleRegions(true, mInfo);
        mTouchTransformer.createOrAddTouchRegion(mInfo);

        tapAndAssertTrue(100, portraitRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertFalse(100, landscapeRegionY,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
        tapAndAssertTrue(0, portraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertFalse(0, landscapeRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
    }

    @Test
    public void enableMultipleRegions_assistantTriggersInMostRecent() {
        float portraitRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0) + 1;
        float landscapeRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_90) + 1;

        mTouchTransformer.enableMultipleRegions(true, mInfo);
        mTouchTransformer
            .createOrAddTouchRegion(createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_90));
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        tapAndAssertTrue(0, portraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertFalse(0, landscapeRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
    }

    @Test
    public void enableMultipleRegions_assistantTriggersInCurrentOrientationAfterDisable() {
        float portraitRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0) + 1;
        float landscapeRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_90) + 1;

        mTouchTransformer.enableMultipleRegions(true, mInfo);
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        mTouchTransformer
            .createOrAddTouchRegion(createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_90));
        mTouchTransformer.enableMultipleRegions(false, mInfo);
        tapAndAssertTrue(0, portraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertFalse(0, landscapeRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
    }

    @Test
    public void assistantTriggersInCurrentScreenAfterScreenSizeChange() {
        float smallerScreenPortraitRegionY =
                generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0) + 1;
        float largerScreenPortraitRegionY =
                generateTouchRegionHeight(LARGE_SCREEN_SIZE, Surface.ROTATION_0) + 1;

        mTouchTransformer.enableMultipleRegions(false,
                createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_0));
        tapAndAssertTrue(0, smallerScreenPortraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));

        mTouchTransformer
            .enableMultipleRegions(false, createDisplayInfo(LARGE_SCREEN_SIZE, Surface.ROTATION_0));
        tapAndAssertTrue(0, largerScreenPortraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
        tapAndAssertFalse(0, smallerScreenPortraitRegionY,
                event -> mTouchTransformer.touchInAssistantRegion(event));
    }

    @Test
    public void applyTransform_taskNotFrozen_notInRegion() {
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        tapAndAssertFalse(100, 100,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
    }

    @Test
    public void applyTransform_taskFrozen_noRotate_outOfRegion() {
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        mTouchTransformer.enableMultipleRegions(true, mInfo);
        tapAndAssertFalse(100, 100,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
    }

    @Test
    public void applyTransform_taskFrozen_noRotate_inRegion() {
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        mTouchTransformer.enableMultipleRegions(true, mInfo);
        float y = generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0) + 1;
        tapAndAssertTrue(100, y,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
    }

    @Test
    public void applyTransform_taskNotFrozen_noRotate_inDefaultRegion() {
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        float y = generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0) + 1;
        tapAndAssertTrue(100, y,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
    }

    @Test
    public void applyTransform_taskNotFrozen_90Rotate_inRegion() {
        mTouchTransformer
            .createOrAddTouchRegion(createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_90));
        float y = generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_90) + 1;
        tapAndAssertTrue(100, y,
                event -> mTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY()));
    }

    @Test
    public void applyTransform_taskNotFrozen_90Rotate_withTwoRegions() {
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        mTouchTransformer.enableMultipleRegions(true, mInfo);
        mTouchTransformer
            .createOrAddTouchRegion(createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_90));
        // Landscape point
        float y1 = generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_90) + 1;
        MotionEvent inRegion1_down = generateMotionEvent(MotionEvent.ACTION_DOWN, 10, y1);
        MotionEvent inRegion1_up = generateMotionEvent(MotionEvent.ACTION_UP, 10, y1);
        // Portrait point in landscape orientation axis
        MotionEvent inRegion2 = generateMotionEvent(MotionEvent.ACTION_DOWN, 10, 10);
        mTouchTransformer.transform(inRegion1_down);
        // no-op
        mTouchTransformer.transform(inRegion2);
        assertTrue(mTouchTransformer.touchInValidSwipeRegions(
                inRegion1_down.getX(), inRegion1_down.getY()));
        // We only process one gesture region until we see a MotionEvent.ACTION_UP
        assertFalse(mTouchTransformer.touchInValidSwipeRegions(inRegion2.getX(), inRegion2.getY()));

        mTouchTransformer.transform(inRegion1_up);
    }

    @Test
    public void applyTransform_90Rotate_inRotatedRegion() {
        // Create regions for both 0 Rotation and 90 Rotation
        mTouchTransformer.createOrAddTouchRegion(mInfo);
        mTouchTransformer.enableMultipleRegions(true, mInfo);
        mTouchTransformer
                .createOrAddTouchRegion(createDisplayInfo(NORMAL_SCREEN_SIZE, Surface.ROTATION_90));
        // Portrait point in landscape orientation axis
        float x1 = generateTouchRegionHeight(NORMAL_SCREEN_SIZE, Surface.ROTATION_0);
        // bottom of screen, from landscape perspective right side of screen
        MotionEvent inRegion2 = generateAndTransformMotionEvent(MotionEvent.ACTION_DOWN, x1, 370);
        assertTrue(mTouchTransformer.touchInValidSwipeRegions(inRegion2.getX(), inRegion2.getY()));
    }

    private DisplayController.Info createDisplayInfo(Size screenSize, int rotation) {
        Point displaySize = new Point(screenSize.getWidth(), screenSize.getHeight());
        RotationUtils.rotateSize(displaySize, rotation);
        CachedDisplayInfo cachedDisplayInfo = new CachedDisplayInfo(displaySize, rotation);
        WindowBounds windowBounds = new WindowBounds(
                new Rect(0, 0, displaySize.x, displaySize.y),
                new Rect());
        WindowManagerProxy wmProxy = mock(WindowManagerProxy.class);
        doReturn(cachedDisplayInfo).when(wmProxy).getDisplayInfo(any());
        doReturn(windowBounds).when(wmProxy).getRealBounds(any(), any());
        ArrayMap<CachedDisplayInfo, List<WindowBounds>> internalDisplayBounds = new ArrayMap<>();
        doReturn(internalDisplayBounds).when(wmProxy).estimateInternalDisplayBounds(any());
        return new DisplayController.Info(
                getApplicationContext(), wmProxy, new ArrayMap<>());
    }

    private float generateTouchRegionHeight(Size screenSize, int rotation) {
        float height = screenSize.getHeight();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            height = screenSize.getWidth();
        }
        return height - ResourceUtils.DEFAULT_NAVBAR_VALUE * DENSITY_DISPLAY_METRICS;
    }

    private MotionEvent generateMotionEvent(int motionAction, float x, float y) {
        return MotionEvent.obtain(0, 0, motionAction, x, y, 0);
    }

    private MotionEvent generateAndTransformMotionEvent(int motionAction, float x, float y) {
        MotionEvent motionEvent = generateMotionEvent(motionAction, x, y);
        mTouchTransformer.transform(motionEvent);
        return motionEvent;
    }

    private void tapAndAssertTrue(float x, float y, MotionEventAssertion assertion) {
        MotionEvent motionEvent = generateAndTransformMotionEvent(MotionEvent.ACTION_DOWN, x, y);
        assertTrue(assertion.getCondition(motionEvent));
        generateAndTransformMotionEvent(MotionEvent.ACTION_UP, x, y);
    }

    private void tapAndAssertFalse(float x, float y, MotionEventAssertion assertion) {
        MotionEvent motionEvent = generateAndTransformMotionEvent(MotionEvent.ACTION_DOWN, x, y);
        assertFalse(assertion.getCondition(motionEvent));
        generateAndTransformMotionEvent(MotionEvent.ACTION_UP, x, y);
    }

    private interface MotionEventAssertion {
        boolean getCondition(MotionEvent motionEvent);
    }
}
