/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.wm.shell.bubbles.animation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.bubbles.BubbleExpandedView;
import com.android.wm.shell.bubbles.TestableBubblePositioner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class ExpandedViewAnimationControllerTest extends ShellTestCase {

    private ExpandedViewAnimationController mController;

    @Mock
    private BubbleExpandedView mMockExpandedView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        TestableBubblePositioner positioner = new TestableBubblePositioner(getContext(),
                getContext().getSystemService(WindowManager.class));
        mController = new ExpandedViewAnimationControllerImpl(getContext(), positioner);

        mController.setExpandedView(mMockExpandedView);
        when(mMockExpandedView.getContentHeight()).thenReturn(1000);
    }

    @Test
    public void testUpdateDrag_expandedViewMovesUpAndClipped() {
        // Drag by 50 pixels which corresponds to 10 pixels with overscroll
        int dragDistance = 50;
        int dampenedDistance = 10;

        mController.updateDrag(dragDistance);

        verify(mMockExpandedView).setTopClip(dampenedDistance);
        verify(mMockExpandedView).setContentTranslationY(-dampenedDistance);
        verify(mMockExpandedView).setManageButtonTranslationY(-dampenedDistance);
    }

    @Test
    public void testUpdateDrag_zOrderUpdates() {
        mController.updateDrag(10);
        mController.updateDrag(20);

        verify(mMockExpandedView, times(1)).setSurfaceZOrderedOnTop(true);
        verify(mMockExpandedView, times(1)).setAnimating(true);
    }

    @Test
    public void testUpdateDrag_moveBackToZero_zOrderRestored() {
        mController.updateDrag(50);
        reset(mMockExpandedView);
        mController.updateDrag(0);
        mController.updateDrag(0);

        verify(mMockExpandedView, times(1)).setSurfaceZOrderedOnTop(false);
        verify(mMockExpandedView, times(1)).setAnimating(false);
    }

    @Test
    public void testUpdateDrag_hapticFeedbackOnlyOnce() {
        // Drag by 10 which is below the collapse threshold - no feedback
        mController.updateDrag(10);
        verify(mMockExpandedView, times(0)).performHapticFeedback(anyInt());
        // 150 takes it over the threshold - perform feedback
        mController.updateDrag(150);
        verify(mMockExpandedView, times(1)).performHapticFeedback(anyInt());
        // Continue dragging, no more feedback
        mController.updateDrag(200);
        verify(mMockExpandedView, times(1)).performHapticFeedback(anyInt());
        // Drag below threshold and over again - no more feedback
        mController.updateDrag(10);
        mController.updateDrag(150);
        verify(mMockExpandedView, times(1)).performHapticFeedback(anyInt());
    }

    @Test
    public void testShouldCollapse_doNotCollapseIfNotDragged() {
        assertThat(mController.shouldCollapse()).isFalse();
    }

    @Test
    public void testShouldCollapse_doNotCollapseIfVelocityDown() {
        assumeTrue("Min fling velocity should be > 1 for this test", getMinFlingVelocity() > 1);
        mController.setSwipeVelocity(getVelocityAboveMinFling());
        assertThat(mController.shouldCollapse()).isFalse();
    }

    @Test
    public void tesShouldCollapse_doNotCollapseIfVelocityUpIsSmall() {
        assumeTrue("Min fling velocity should be > 1 for this test", getMinFlingVelocity() > 1);
        mController.setSwipeVelocity(-getVelocityBelowMinFling());
        assertThat(mController.shouldCollapse()).isFalse();
    }

    @Test
    public void testShouldCollapse_collapseIfVelocityUpIsLarge() {
        assumeTrue("Min fling velocity should be > 1 for this test", getMinFlingVelocity() > 1);
        mController.setSwipeVelocity(-getVelocityAboveMinFling());
        assertThat(mController.shouldCollapse()).isTrue();
    }

    @Test
    public void testShouldCollapse_collapseIfPastThreshold() {
        mController.updateDrag(500);
        assertThat(mController.shouldCollapse()).isTrue();
    }

    @Test
    public void testReset() {
        mController.updateDrag(100);
        reset(mMockExpandedView);
        mController.reset();
        verify(mMockExpandedView, atLeastOnce()).setAnimating(false);
        verify(mMockExpandedView).setContentAlpha(1);
        verify(mMockExpandedView).setBackgroundAlpha(1);
        verify(mMockExpandedView).setManageButtonAlpha(1);
        verify(mMockExpandedView).setManageButtonAlpha(1);
        verify(mMockExpandedView).setTopClip(0);
        verify(mMockExpandedView).setContentTranslationY(-0f);
        verify(mMockExpandedView).setManageButtonTranslationY(-0f);
        verify(mMockExpandedView).setBottomClip(0);
        verify(mMockExpandedView).movePointerBy(0, 0);
        assertThat(mController.shouldCollapse()).isFalse();
    }

    private int getVelocityBelowMinFling() {
        return getMinFlingVelocity() - 1;
    }

    private int getVelocityAboveMinFling() {
        return getMinFlingVelocity() + 1;
    }

    private int getMinFlingVelocity() {
        return ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();
    }
}
