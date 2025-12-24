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

package com.android.wm.shell.common.split;

import static android.view.WindowManager.DOCKED_INVALID;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;

import static com.android.wm.shell.shared.split.SplitScreenConstants.DEFAULT_OFFSCREEN_DIM;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class FlexParallaxSpecTests {
    ParallaxSpec mFlexSpec = new FlexParallaxSpec();

    Rect mDisplayBounds = new Rect(0, 0, 1000, 1000);
    Rect mRetreatingSurface = new Rect(0, 0, 1000, 1000);
    Rect mRetreatingContent = new Rect(0, 0, 1000, 1000);
    Rect mAdvancingSurface = new Rect(0, 0, 1000, 1000);
    Rect mAdvancingContent = new Rect(0, 0, 1000, 1000);
    boolean mIsLeftRightSplit;
    boolean mTopLeftShrink;

    int mDimmingSide;
    float mDimValue;
    Point mRetreatingParallax = new Point(0, 0);
    Point mAdvancingParallax = new Point(0, 0);

    @Mock DividerSnapAlgorithm mockSnapAlgorithm;
    @Mock SplitState mockSplitState;
    @Mock SnapTarget mockStartEdge;
    @Mock SnapTarget mockFirstTarget;
    @Mock SnapTarget mockMiddleTarget;
    @Mock SnapTarget mockLastTarget;
    @Mock SnapTarget mockEndEdge;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockSnapAlgorithm.getDismissStartTarget()).thenReturn(mockStartEdge);
        when(mockSnapAlgorithm.getFirstSplitTarget()).thenReturn(mockFirstTarget);
        when(mockSnapAlgorithm.getMiddleTarget()).thenReturn(mockMiddleTarget);
        when(mockSnapAlgorithm.getLastSplitTarget()).thenReturn(mockLastTarget);
        when(mockSnapAlgorithm.getDismissEndTarget()).thenReturn(mockEndEdge);
        when(mockSnapAlgorithm.areOffscreenRatiosSupported()).thenReturn(true);
        when(mockSplitState.get()).thenReturn(SNAP_TO_2_50_50);

        when(mockStartEdge.getPosition()).thenReturn(0);
        when(mockFirstTarget.getPosition()).thenReturn(250);
        when(mockMiddleTarget.getPosition()).thenReturn(500);
        when(mockLastTarget.getPosition()).thenReturn(750);
        when(mockEndEdge.getPosition()).thenReturn(1000);
    }

    @Test
    public void testHorizontalDragFromCenter() {
        mIsLeftRightSplit = true;
        simulateDragFromCenterToLeft(125);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToLeft(250);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToLeft(375);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(500);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(625);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(750);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(875);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);
    }

    @Test
    public void testHorizontalDragFromLeft() {
        mIsLeftRightSplit = true;
        simulateDragFromLeftToLeft(125);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromLeftToLeft(250);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromLeftToCenter(375);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromLeftToCenter(500);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromLeftToRight(625);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromLeftToRight(750);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromLeftToRight(875);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isGreaterThan(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);
    }

    @Test
    public void testHorizontalDragFromRight() {
        mIsLeftRightSplit = true;

        simulateDragFromRightToLeft(125);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromRightToLeft(250);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromRightToLeft(375);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromRightToCenter(500);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromRightToCenter(625);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromRightToRight(750);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromRightToRight(875);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);
    }

    private void simulateDragFromCenterToLeft(int to) {
        int from = 500;

        mRetreatingSurface = flexOffscreenAppLeft(to);
        mRetreatingContent = onscreenAppLeft(from);
        mAdvancingSurface = onscreenAppRight(to);
        mAdvancingContent = onscreenAppRight(from);

        calculateDimAndParallax(from, to);
    }

    private void simulateDragFromCenterToRight(int to) {
        int from = 500;

        mRetreatingSurface = flexOffscreenAppRight(to);
        mRetreatingContent = onscreenAppRight(from);
        mAdvancingSurface = onscreenAppLeft(to);
        mAdvancingContent = onscreenAppLeft(from);

        calculateDimAndParallax(from, to);
    }

    private void simulateDragFromLeftToLeft(int to) {
        int from = 250;

        mRetreatingSurface = flexOffscreenAppLeft(to);
        mRetreatingContent = fullOffscreenAppLeft(from);
        mAdvancingSurface = onscreenAppRight(to);
        mAdvancingContent = onscreenAppRight(from);

        calculateDimAndParallax(from, to);
    }

    private void simulateDragFromLeftToCenter(int to) {
        int from = 250;

        mRetreatingSurface = onscreenAppRight(to);
        mRetreatingContent = onscreenAppRight(from);
        mAdvancingSurface = fullOffscreenAppLeft(to);
        mAdvancingContent = fullOffscreenAppLeft(from);

        calculateDimAndParallax(from, to);
    }

    private void simulateDragFromLeftToRight(int to) {
        int from = 250;

        mRetreatingSurface = flexOffscreenAppRight(to);
        mRetreatingContent = onscreenAppRight(from);
        mAdvancingSurface = fullOffscreenAppLeft(to);
        mAdvancingContent = fullOffscreenAppLeft(from);

        calculateDimAndParallax(from, to);
    }

    private void simulateDragFromRightToLeft(int to) {
        int from = 750;

        mRetreatingSurface = flexOffscreenAppLeft(to);
        mRetreatingContent = onscreenAppLeft(from);
        mAdvancingSurface = fullOffscreenAppRight(to);
        mAdvancingContent = fullOffscreenAppRight(from);

        calculateDimAndParallax(from, to);
    }

    private void simulateDragFromRightToCenter(int to) {
        int from = 750;

        mRetreatingSurface = onscreenAppLeft(to);
        mRetreatingContent = onscreenAppLeft(from);
        mAdvancingSurface = fullOffscreenAppRight(to);
        mAdvancingContent = fullOffscreenAppRight(from);

        calculateDimAndParallax(from, to);
    }

    private void simulateDragFromRightToRight(int to) {
        int from = 750;

        mRetreatingSurface = flexOffscreenAppRight(to);
        mRetreatingContent = fullOffscreenAppRight(from);
        mAdvancingSurface = onscreenAppLeft(to);
        mAdvancingContent = onscreenAppLeft(from);

        calculateDimAndParallax(from, to);
    }

    private Rect flexOffscreenAppLeft(int pos) {
        return new Rect(pos - (1000 - pos), 0, pos, 1000);
    }

    private Rect onscreenAppLeft(int pos) {
        return new Rect(0, 0, pos, 1000);
    }

    private Rect fullOffscreenAppLeft(int pos) {
        return new Rect(Math.min(0, pos - 750), 0, pos, 1000);
    }

    private Rect flexOffscreenAppRight(int pos) {
        return new Rect(pos, 0, pos * 2, 1000);
    }

    private Rect onscreenAppRight(int pos) {
        return new Rect(pos, 0, 1000, 1000);
    }

    private Rect fullOffscreenAppRight(int pos) {
        return new Rect(pos, 0, Math.max(pos + 750, 1000), 1000);
    }

    private void calculateDimAndParallax(int from, int to) {
        resetParallax();
        mTopLeftShrink = to < from;
        mDimmingSide = mFlexSpec.getDimmingSide(to, mockSnapAlgorithm, mIsLeftRightSplit);
        mDimValue = mFlexSpec.getDimValue(to, mockSnapAlgorithm);
        mFlexSpec.getParallax(mRetreatingParallax, mAdvancingParallax, to, mockSnapAlgorithm,
                mIsLeftRightSplit, mDisplayBounds, mRetreatingSurface, mRetreatingContent,
                mAdvancingSurface, mAdvancingContent, mDimmingSide, mTopLeftShrink, mockSplitState);
    }

    private void resetParallax() {
        mRetreatingParallax.set(0, 0);
        mAdvancingParallax.set(0, 0);
    }
}
