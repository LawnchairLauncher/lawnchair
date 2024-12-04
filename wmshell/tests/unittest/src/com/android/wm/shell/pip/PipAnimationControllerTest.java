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

package com.android.wm.shell.pip;

import static android.util.RotationUtils.rotateBounds;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.wm.shell.MockSurfaceControlHelper.createMockSurfaceControlTransaction;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_LEAVE_PIP;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_TO_PIP;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.app.TaskInfo;
import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.MockSurfaceControlHelper;
import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests against {@link PipAnimationController} to ensure that it sends the right callbacks
 * depending on the various interactions.
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class PipAnimationControllerTest extends ShellTestCase {

    private PipAnimationController mPipAnimationController;

    private SurfaceControl mLeash;

    @Mock
    private TaskInfo mTaskInfo;
    @Mock
    private PipAnimationController.PipAnimationCallback mPipAnimationCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mPipAnimationController = new PipAnimationController(new PipSurfaceTransactionHelper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        mLeash = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("FakeLeash")
                .build();
    }

    @Test
    public void getAnimator_withAlpha_returnFloatAnimator() {
        final PipAnimationController.PipTransitionAnimator animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, new Rect(), 0f, 1f);

        assertEquals("Expect ANIM_TYPE_ALPHA animation",
                animator.getAnimationType(), PipAnimationController.ANIM_TYPE_ALPHA);
    }

    @Test
    public void getAnimator_withBounds_returnBoundsAnimator() {
        final PipAnimationController.PipTransitionAnimator animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, new Rect(), new Rect(), new Rect(), null,
                        TRANSITION_DIRECTION_TO_PIP, 0, ROTATION_0);

        assertEquals("Expect ANIM_TYPE_BOUNDS animation",
                animator.getAnimationType(), PipAnimationController.ANIM_TYPE_BOUNDS);
    }

    @Test
    public void getAnimator_whenSameTypeRunning_updateExistingAnimator() {
        final Rect baseValue = new Rect(0, 0, 100, 100);
        final Rect startValue = new Rect(0, 0, 100, 100);
        final Rect endValue1 = new Rect(100, 100, 200, 200);
        final Rect endValue2 = new Rect(200, 200, 300, 300);
        final PipAnimationController.PipTransitionAnimator oldAnimator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, baseValue, startValue, endValue1, null,
                        TRANSITION_DIRECTION_TO_PIP, 0, ROTATION_0);
        oldAnimator.setSurfaceControlTransactionFactory(
                MockSurfaceControlHelper::createMockSurfaceControlTransaction);
        oldAnimator.start();

        final PipAnimationController.PipTransitionAnimator newAnimator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, baseValue, startValue, endValue2, null,
                        TRANSITION_DIRECTION_TO_PIP, 0, ROTATION_0);

        assertEquals("getAnimator with same type returns same animator",
                oldAnimator, newAnimator);
        assertEquals("getAnimator with same type updates end value",
                endValue2, newAnimator.getEndValue());
    }

    @Test
    public void getAnimator_setTransitionDirection() {
        PipAnimationController.PipTransitionAnimator animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, new Rect(), 0f, 1f)
                .setTransitionDirection(TRANSITION_DIRECTION_TO_PIP);
        assertEquals("Transition to PiP mode",
                animator.getTransitionDirection(), TRANSITION_DIRECTION_TO_PIP);

        animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, new Rect(), 0f, 1f)
                .setTransitionDirection(TRANSITION_DIRECTION_LEAVE_PIP);
        assertEquals("Transition to fullscreen mode",
                animator.getTransitionDirection(), TRANSITION_DIRECTION_LEAVE_PIP);
    }

    @Test
    public void pipTransitionAnimator_rotatedEndValue() {
        final SurfaceControl.Transaction tx = createMockSurfaceControlTransaction();
        final Rect startBounds = new Rect(200, 700, 400, 800);
        final Rect endBounds = new Rect(0, 0, 500, 1000);
        // Fullscreen to PiP.
        PipAnimationController.PipTransitionAnimator<?> animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, null, startBounds, endBounds, null,
                        TRANSITION_DIRECTION_LEAVE_PIP, 0, ROTATION_90);
        // Apply fraction 1 to compute the end value.
        animator.applySurfaceControlTransaction(mLeash, tx, 1);
        final Rect rotatedEndBounds = new Rect(endBounds);
        rotateBounds(rotatedEndBounds, endBounds, ROTATION_90);

        assertEquals("Expect 90 degree rotated bounds", rotatedEndBounds, animator.mCurrentValue);

        // PiP to fullscreen.
        startBounds.set(0, 0, 1000, 500);
        endBounds.set(200, 100, 400, 500);
        animator = mPipAnimationController.getAnimator(mTaskInfo, mLeash, startBounds, startBounds,
                endBounds, null, TRANSITION_DIRECTION_TO_PIP, 0, ROTATION_270);
        animator.applySurfaceControlTransaction(mLeash, tx, 1);
        rotatedEndBounds.set(endBounds);
        rotateBounds(rotatedEndBounds, startBounds, ROTATION_270);

        assertEquals("Expect 270 degree rotated bounds", rotatedEndBounds, animator.mCurrentValue);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pipTransitionAnimator_updateEndValue() {
        final Rect baseValue = new Rect(0, 0, 100, 100);
        final Rect startValue = new Rect(0, 0, 100, 100);
        final Rect endValue1 = new Rect(100, 100, 200, 200);
        final Rect endValue2 = new Rect(200, 200, 300, 300);
        final PipAnimationController.PipTransitionAnimator animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, baseValue, startValue, endValue1, null,
                        TRANSITION_DIRECTION_TO_PIP, 0, ROTATION_0);

        animator.updateEndValue(endValue2);

        assertEquals("updateEndValue updates end value", animator.getEndValue(), endValue2);
    }

    @Test
    public void pipTransitionAnimator_setPipAnimationCallback() {
        final Rect baseValue = new Rect(0, 0, 100, 100);
        final Rect startValue = new Rect(0, 0, 100, 100);
        final Rect endValue = new Rect(100, 100, 200, 200);
        final PipAnimationController.PipTransitionAnimator animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, baseValue, startValue, endValue, null,
                        TRANSITION_DIRECTION_TO_PIP, 0, ROTATION_0);
        animator.setSurfaceControlTransactionFactory(
                MockSurfaceControlHelper::createMockSurfaceControlTransaction);

        animator.setPipAnimationCallback(mPipAnimationCallback);

        // onAnimationStart triggers onPipAnimationStart
        animator.onAnimationStart(animator);
        verify(mPipAnimationCallback).onPipAnimationStart(mTaskInfo, animator);

        // onAnimationCancel triggers onPipAnimationCancel
        animator.onAnimationCancel(animator);
        verify(mPipAnimationCallback).onPipAnimationCancel(mTaskInfo, animator);

        // onAnimationEnd triggers onPipAnimationEnd
        animator.onAnimationEnd(animator);
        verify(mPipAnimationCallback).onPipAnimationEnd(eq(mTaskInfo),
                any(SurfaceControl.Transaction.class), eq(animator));
    }
}
