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

package com.android.wm.shell.back;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.window.BackEvent;
import android.window.BackMotionEvent;
import android.window.BackProgressAnimator;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class BackProgressAnimatorTest {
    private BackProgressAnimator mProgressAnimator;
    private BackEvent mReceivedBackEvent;
    private float mTargetProgress = 0.5f;
    private CountDownLatch mTargetProgressCalled = new CountDownLatch(1);
    private Handler mMainThreadHandler;

    private BackMotionEvent backMotionEventFrom(float touchX, float progress) {
        return new BackMotionEvent(
                /* touchX = */ touchX,
                /* touchY = */ 0,
                /* progress = */ progress,
                /* velocityX = */ 0,
                /* velocityY = */ 0,
                /* triggerBack = */ false,
                /* swipeEdge = */ BackEvent.EDGE_LEFT,
                /* departingAnimationTarget = */ null);
    }

    @Before
    public void setUp() throws Exception {
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        final BackMotionEvent backEvent = backMotionEventFrom(0, 0);
        mMainThreadHandler.post(
                () -> {
                    mProgressAnimator = new BackProgressAnimator();
                    mProgressAnimator.onBackStarted(backEvent, this::onGestureProgress);
                });
    }

    @Test
    public void testBackProgressed() throws InterruptedException {
        final BackMotionEvent backEvent = backMotionEventFrom(100, mTargetProgress);
        mMainThreadHandler.post(
                () -> mProgressAnimator.onBackProgressed(backEvent));

        mTargetProgressCalled.await(1, TimeUnit.SECONDS);

        assertNotNull(mReceivedBackEvent);
        assertEquals(mReceivedBackEvent.getProgress(), mTargetProgress, 0 /* delta */);
    }

    @Test
    public void testBackCancelled() throws InterruptedException {
        // Give the animator some progress.
        final BackMotionEvent backEvent = backMotionEventFrom(100, mTargetProgress);
        mMainThreadHandler.post(
                () -> mProgressAnimator.onBackProgressed(backEvent));
        mTargetProgressCalled.await(1, TimeUnit.SECONDS);
        assertNotNull(mReceivedBackEvent);

        // Trigger animation cancel, the target progress should be 0.
        mTargetProgress = 0;
        mTargetProgressCalled = new CountDownLatch(1);
        CountDownLatch finishCallbackCalled = new CountDownLatch(1);
        mMainThreadHandler.post(
                () -> mProgressAnimator.onBackCancelled(finishCallbackCalled::countDown));
        finishCallbackCalled.await(1, TimeUnit.SECONDS);
        mTargetProgressCalled.await(1, TimeUnit.SECONDS);
        assertNotNull(mReceivedBackEvent);
        assertEquals(mReceivedBackEvent.getProgress(), mTargetProgress, 0 /* delta */);
    }

    @Test
    public void testBackInvoked() throws InterruptedException {
        // Give the animator some progress.
        final BackMotionEvent backEvent = backMotionEventFrom(100, mTargetProgress);
        mMainThreadHandler.post(
                () -> mProgressAnimator.onBackProgressed(backEvent));
        mTargetProgressCalled.await(1, TimeUnit.SECONDS);
        assertNotNull(mReceivedBackEvent);

        // Trigger back invoked animation
        CountDownLatch finishCallbackCalled = new CountDownLatch(1);
        mMainThreadHandler.post(
                () -> mProgressAnimator.onBackInvoked(finishCallbackCalled::countDown));
        assertTrue("onBackInvoked finishCallback never called",
                finishCallbackCalled.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testResetCallsCancelCallbackImmediately() throws InterruptedException {
        // Give the animator some progress.
        final BackMotionEvent backEvent = backMotionEventFrom(100, mTargetProgress);
        mMainThreadHandler.post(
                () -> mProgressAnimator.onBackProgressed(backEvent));
        mTargetProgressCalled.await(1, TimeUnit.SECONDS);
        assertNotNull(mReceivedBackEvent);

        mTargetProgress = 0;
        mReceivedBackEvent = null;
        mTargetProgressCalled = new CountDownLatch(1);

        CountDownLatch cancelCallbackCalled = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> mProgressAnimator.onBackCancelled(cancelCallbackCalled::countDown));

        // verify onBackProgressed and onBackCancelled not yet called
        assertNull(mReceivedBackEvent);
        assertEquals(1, cancelCallbackCalled.getCount());

        // call reset
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> mProgressAnimator.reset());

        // verify that back event with progress 0 is sent and cancel callback is invoked
        assertNotNull(mReceivedBackEvent);
        assertEquals(mReceivedBackEvent.getProgress(), mTargetProgress, 0 /* delta */);
        assertEquals(0, cancelCallbackCalled.getCount());
    }

    @Test
    public void testCancelFinishCallbackNotInvokedWhenRemoved() throws InterruptedException {
        // Give the animator some progress.
        final BackMotionEvent backEvent = backMotionEventFrom(100, mTargetProgress);
        mMainThreadHandler.post(
                () -> mProgressAnimator.onBackProgressed(backEvent));
        mTargetProgressCalled.await(1, TimeUnit.SECONDS);
        assertNotNull(mReceivedBackEvent);

        // call onBackCancelled (which animates progress to 0 before invoking the finishCallback)
        CountDownLatch finishCallbackCalled = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> mProgressAnimator.onBackCancelled(finishCallbackCalled::countDown));

        // remove onBackCancelled finishCallback (while progress is still animating to 0)
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> mProgressAnimator.removeOnBackCancelledFinishCallback());

        // call reset (which triggers the finishCallback invocation, if one is present)
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> mProgressAnimator.reset());

        // verify that finishCallback is not invoked
        assertEquals(1, finishCallbackCalled.getCount());
    }

    private void onGestureProgress(BackEvent backEvent) {
        if (mTargetProgress == backEvent.getProgress()) {
            mReceivedBackEvent = backEvent;
            mTargetProgressCalled.countDown();
        }
    }
}
