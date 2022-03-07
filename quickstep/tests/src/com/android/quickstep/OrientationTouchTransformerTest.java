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

import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.util.DisplayController.NavigationMode.NO_BUTTON;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.ResourceUtils;
import com.android.launcher3.util.DisplayController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OrientationTouchTransformerTest {
    static class ScreenSize {
        int mHeight;
        int mWidth;

        ScreenSize(int height, int width) {
            mHeight = height;
            mWidth = width;
        }
    }

    private static final ScreenSize NORMAL_SCREEN_SIZE = new ScreenSize(2280, 1080);
    private static final ScreenSize LARGE_SCREEN_SIZE = new ScreenSize(3280, 1080);
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

    private DisplayController.Info createDisplayInfo(ScreenSize screenSize, int rotation) {
        Context context = getApplicationContext();
        Display display = spy(context.getSystemService(DisplayManager.class)
                .getDisplay(DEFAULT_DISPLAY));

        Point p = new Point(screenSize.mWidth, screenSize.mHeight);
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            p.set(screenSize.mHeight, screenSize.mWidth);
        }

        doReturn(rotation).when(display).getRotation();
        doAnswer(i -> {
            ((Point) i.getArgument(0)).set(p.x, p.y);
            return null;
        }).when(display).getRealSize(any(Point.class));
        doAnswer(i -> {
            ((Point) i.getArgument(0)).set(p.x, p.y);
            ((Point) i.getArgument(1)).set(p.x, p.y);
            return null;
        }).when(display).getCurrentSizeRange(any(Point.class), any(Point.class));
        return new DisplayController.Info(context, display);
    }

    private float generateTouchRegionHeight(ScreenSize screenSize, int rotation) {
        float height = screenSize.mHeight;
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            height = screenSize.mWidth;
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
