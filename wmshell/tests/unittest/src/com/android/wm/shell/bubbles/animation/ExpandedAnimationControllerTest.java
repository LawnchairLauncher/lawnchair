/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleStackView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ExpandedAnimationControllerTest extends PhysicsAnimationLayoutTestCase {

    private final Semaphore mBubbleRemovedSemaphore = new Semaphore(0);
    private final Runnable mOnBubbleAnimatedOutAction = mBubbleRemovedSemaphore::release;
    ExpandedAnimationController mExpandedController;

    private int mStackOffset;
    private PointF mExpansionPoint;
    private BubblePositioner mPositioner;
    private final BubbleStackView.StackViewState mStackViewState =
            new BubbleStackView.StackViewState();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mPositioner = new BubblePositioner(getContext(),
                getContext().getSystemService(WindowManager.class));
        mPositioner.updateInternal(Configuration.ORIENTATION_PORTRAIT,
                Insets.of(0, 0, 0, 0),
                new Rect(0, 0, 500, 1000));

        BubbleStackView stackView = mock(BubbleStackView.class);

        mExpandedController = new ExpandedAnimationController(mPositioner,
                mOnBubbleAnimatedOutAction,
                stackView);

        addOneMoreThanBubbleLimitBubbles();
        mLayout.setActiveController(mExpandedController);

        Resources res = mLayout.getResources();
        mStackOffset = res.getDimensionPixelSize(R.dimen.bubble_stack_offset);
        mExpansionPoint = new PointF(100, 100);

        getStackViewState();
        when(stackView.getState()).thenAnswer(i -> getStackViewState());
        waitForMainThread();
    }

    @After
    public void tearDown() {
        waitForMainThread();
    }

    private BubbleStackView.StackViewState getStackViewState() {
        mStackViewState.numberOfBubbles = mLayout.getChildCount();
        mStackViewState.selectedIndex = 0;
        mStackViewState.onLeft = mPositioner.isStackOnLeft(mExpansionPoint);
        return mStackViewState;
    }

    @Test
    public void testExpansionAndCollapse() throws Exception {
        expand();
        testBubblesInCorrectExpandedPositions();
        waitForMainThread();

        final Semaphore semaphore = new Semaphore(0);
        Runnable afterCollapse = semaphore::release;
        mExpandedController.collapseBackToStack(mExpansionPoint, false, afterCollapse);
        assertThat(semaphore.tryAcquire(1, 2, TimeUnit.SECONDS)).isTrue();
        waitForAnimation();
        testStackedAtPosition(mExpansionPoint.x, mExpansionPoint.y);
    }

    @Test
    public void testOnChildAdded() throws Exception {
        expand();
        waitForMainThread();

        // Add another new view and wait for its animation.
        final View newView = new FrameLayout(getContext());
        mLayout.addView(newView, 0);

        waitForAnimation();
        testBubblesInCorrectExpandedPositions();
    }

    @Test
    public void testOnChildRemoved() throws Exception {
        expand();
        waitForMainThread();

        // Remove some views and verify the remaining child views still pass the expansion test.
        mLayout.removeView(mViews.get(0));
        mLayout.removeView(mViews.get(3));

        // Removing a view will invoke onBubbleAnimatedOutAction. Block until it gets called twice.
        assertThat(mBubbleRemovedSemaphore.tryAcquire(2, 2, TimeUnit.SECONDS)).isTrue();

        waitForAnimation();
        testBubblesInCorrectExpandedPositions();
    }

    @Test
    public void testDragBubbleOutDoesntNPE() {
        mExpandedController.onGestureFinished();
        mExpandedController.dragBubbleOut(mViews.get(0), 1, 1);
    }

    /** Expand the stack and wait for animations to finish. */
    private void expand() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);
        Runnable afterExpand = semaphore::release;

        mExpandedController.expandFromStack(afterExpand);
        assertThat(semaphore.tryAcquire(1, TimeUnit.SECONDS)).isTrue();
    }

    /** Check that children are in the correct positions for being stacked. */
    private void testStackedAtPosition(float x, float y) {
        // Make sure the rest of the stack moved again, including the first bubble not moving, and
        // is stacked to the right now that we're on the right side of the screen.
        for (int i = 0; i < mLayout.getChildCount(); i++) {
            assertEquals(x, mLayout.getChildAt(i).getTranslationX(), 2f);
            assertEquals(y + Math.min(i, 1) * mStackOffset, mLayout.getChildAt(i).getTranslationY(),
                    2f);
            assertEquals(1f, mLayout.getChildAt(i).getAlpha(), .01f);
        }
    }

    /** Check that children are in the correct positions for being expanded. */
    private void testBubblesInCorrectExpandedPositions() {
        // Check all the visible bubbles to see if they're in the right place.
        for (int i = 0; i < mLayout.getChildCount(); i++) {
            PointF expectedPosition = mPositioner.getExpandedBubbleXY(i,
                    getStackViewState());
            assertEquals(expectedPosition.x,
                    mLayout.getChildAt(i).getTranslationX(),
                    2f);
            assertEquals(expectedPosition.y,
                    mLayout.getChildAt(i).getTranslationY(), 2f);
        }
    }

    private void waitForAnimation() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        boolean[] animating = new boolean[]{ true };
        for (int i = 0; i < 4; i++) {
            if (animating[0]) {
                mMainThreadHandler.post(() -> {
                    if (!mExpandedController.isAnimating()) {
                        animating[0] = false;
                        semaphore.release();
                    }
                });
                Thread.sleep(500);
            }
        }
        assertThat(semaphore.tryAcquire(1, 2, TimeUnit.SECONDS)).isTrue();
        waitForPropertyAnimations(DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y);
    }
}
