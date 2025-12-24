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
public class FlexHybridParallaxSpecTests {
    ParallaxSpec mFlexHybridSpec = new FlexHybridParallaxSpec();

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

    @Mock DividerSnapAlgorithm mSnapAlgorithm;
    @Mock SplitState mSplitState;
    @Mock SnapTarget mStartEdge;
    @Mock SnapTarget mFirstTarget;
    @Mock SnapTarget mSecondTarget;
    @Mock SnapTarget mMiddleTarget;
    @Mock SnapTarget mSecondLastTarget;
    @Mock SnapTarget mLastTarget;
    @Mock SnapTarget mEndEdge;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mSnapAlgorithm.getDismissStartTarget()).thenReturn(mStartEdge);
        when(mSnapAlgorithm.getFirstSplitTarget()).thenReturn(mFirstTarget);
        when(mSnapAlgorithm.getSecondSplitTarget()).thenReturn(mSecondTarget);
        when(mSnapAlgorithm.getMiddleTarget()).thenReturn(mMiddleTarget);
        when(mSnapAlgorithm.getSecondLastSplitTarget()).thenReturn(mSecondLastTarget);
        when(mSnapAlgorithm.getLastSplitTarget()).thenReturn(mLastTarget);
        when(mSnapAlgorithm.getDismissEndTarget()).thenReturn(mEndEdge);
        when(mSnapAlgorithm.areOffscreenRatiosSupported()).thenReturn(true);
        when(mSplitState.get()).thenReturn(SNAP_TO_2_50_50);

        when(mStartEdge.getPosition()).thenReturn(0);
        when(mFirstTarget.getPosition()).thenReturn(100);
        when(mSecondTarget.getPosition()).thenReturn(333);
        when(mMiddleTarget.getPosition()).thenReturn(500);
        when(mSecondLastTarget.getPosition()).thenReturn(667);
        when(mLastTarget.getPosition()).thenReturn(900);
        when(mEndEdge.getPosition()).thenReturn(1000);
    }

    @Test
    public void testHorizontalDragFromCenter() {
        mIsLeftRightSplit = true;

        simulateDragFromCenterToLeft(50);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToLeft(100);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isGreaterThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToLeft(250);
        assertThat(mDimmingSide).isEqualTo(DOCKED_LEFT);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToLeft(333);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToLeft(400);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToLeft(500);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isEqualTo(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(600);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(667);
        assertThat(mDimmingSide).isEqualTo(DOCKED_INVALID);
        assertThat(mDimValue).isEqualTo(0f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(750);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(0f);
        assertThat(mDimValue).isLessThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(900);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isEqualTo(DEFAULT_OFFSCREEN_DIM);
        assertThat(mRetreatingParallax.x).isLessThan(0);
        assertThat(mRetreatingParallax.y).isEqualTo(0);
        assertThat(mAdvancingParallax.x).isEqualTo(0);
        assertThat(mAdvancingParallax.y).isEqualTo(0);

        simulateDragFromCenterToRight(950);
        assertThat(mDimmingSide).isEqualTo(DOCKED_RIGHT);
        assertThat(mDimValue).isGreaterThan(DEFAULT_OFFSCREEN_DIM);
        assertThat(mDimValue).isLessThan(1f);
        assertThat(mRetreatingParallax.x).isLessThan(0);
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

    private Rect flexOffscreenAppLeft(int pos) {
        if (pos <= 100) {
            return new Rect(-900, 0, pos, 1000);
        } else {
            return onscreenAppLeft(pos);
        }
    }

    private Rect onscreenAppLeft(int pos) {
        return new Rect(0, 0, pos, 1000);
    }

    private Rect flexOffscreenAppRight(int pos) {
        if (pos >= 900) {
            return new Rect(pos, 0, 1800, 1000);
        } else {
            return onscreenAppRight(pos);
        }
    }

    private Rect onscreenAppRight(int pos) {
        return new Rect(pos, 0, 1000, 1000);
    }

    private void calculateDimAndParallax(int from, int to) {
        resetParallax();
        mTopLeftShrink = to < from;
        mDimmingSide = mFlexHybridSpec.getDimmingSide(to, mSnapAlgorithm, mIsLeftRightSplit);
        mDimValue = mFlexHybridSpec.getDimValue(to, mSnapAlgorithm);
        mFlexHybridSpec.getParallax(mRetreatingParallax, mAdvancingParallax, to, mSnapAlgorithm,
                mIsLeftRightSplit, mDisplayBounds, mRetreatingSurface, mRetreatingContent,
                mAdvancingSurface, mAdvancingContent, mDimmingSide, mTopLeftShrink, mSplitState);
    }

    private void resetParallax() {
        mRetreatingParallax.set(0, 0);
        mAdvancingParallax.set(0, 0);
    }
}
