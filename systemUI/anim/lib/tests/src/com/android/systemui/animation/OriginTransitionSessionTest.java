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

package com.android.systemui.animation;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.Nullable;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.systemui.animation.shared.IOriginTransitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;
import java.util.function.Predicate;

/** Unit tests for {@link OriginTransitionSession}. */
@SmallTest
@RunWith(JUnit4.class)
public final class OriginTransitionSessionTest {
    private static final ComponentName TEST_ACTIVITY_1 = new ComponentName("test", "Activity1");
    private static final ComponentName TEST_ACTIVITY_2 = new ComponentName("test", "Activity2");
    private static final ComponentName TEST_ACTIVITY_3 = new ComponentName("test", "Activity3");

    private FakeIOriginTransitions mIOriginTransitions;
    private Instrumentation mInstrumentation;
    private FakeIntentStarter mIntentStarter;
    private Context mContext;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mIOriginTransitions = new FakeIOriginTransitions();
        mIntentStarter = new FakeIntentStarter(TEST_ACTIVITY_1, TEST_ACTIVITY_2);
    }

    @Test
    public void sessionStart_withEntryAndExitTransition_transitionsPlayed() {
        FakeRemoteTransition entry = new FakeRemoteTransition();
        FakeRemoteTransition exit = new FakeRemoteTransition();
        OriginTransitionSession session =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(mIntentStarter)
                        .withEntryTransition(entry)
                        .withExitTransition(exit)
                        .build();

        session.start();

        assertThat(mIntentStarter.hasLaunched()).isTrue();
        assertThat(entry.started()).isTrue();

        runReturnTransition(mIntentStarter);

        assertThat(exit.started()).isTrue();
    }

    @Test
    public void sessionStart_withEntryTransition_transitionPlayed() {
        FakeRemoteTransition entry = new FakeRemoteTransition();
        OriginTransitionSession session =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(mIntentStarter)
                        .withEntryTransition(entry)
                        .build();

        session.start();

        assertThat(mIntentStarter.hasLaunched()).isTrue();
        assertThat(entry.started()).isTrue();
        assertThat(mIOriginTransitions.hasPendingReturnTransitions()).isFalse();
    }

    @Test
    public void sessionStart_withoutTransition_launchedIntent() {
        OriginTransitionSession session =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(mIntentStarter)
                        .build();

        session.start();

        assertThat(mIntentStarter.hasLaunched()).isTrue();
        assertThat(mIOriginTransitions.hasPendingReturnTransitions()).isFalse();
    }

    @Test
    public void sessionStart_cancelledByIntentStarter_transitionNotPlayed() {
        FakeRemoteTransition entry = new FakeRemoteTransition();
        FakeRemoteTransition exit = new FakeRemoteTransition();
        mIntentStarter =
                new FakeIntentStarter(TEST_ACTIVITY_1, TEST_ACTIVITY_2, /* result= */ false);
        OriginTransitionSession session =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(mIntentStarter)
                        .withEntryTransition(entry)
                        .withExitTransition(exit)
                        .build();

        session.start();

        assertThat(mIntentStarter.hasLaunched()).isFalse();
        assertThat(entry.started()).isFalse();
        assertThat(exit.started()).isFalse();
        assertThat(mIOriginTransitions.hasPendingReturnTransitions()).isFalse();
    }

    @Test
    public void sessionStart_alreadyStarted_noOp() {
        FakeRemoteTransition entry = new FakeRemoteTransition();
        FakeRemoteTransition exit = new FakeRemoteTransition();
        OriginTransitionSession session =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(mIntentStarter)
                        .withEntryTransition(entry)
                        .withExitTransition(exit)
                        .build();
        session.start();
        entry.reset();
        mIntentStarter.reset();

        session.start();

        assertThat(mIntentStarter.hasLaunched()).isFalse();
        assertThat(entry.started()).isFalse();
    }

    @Test
    public void sessionStart_alreadyCancelled_noOp() {
        FakeRemoteTransition entry = new FakeRemoteTransition();
        FakeRemoteTransition exit = new FakeRemoteTransition();
        OriginTransitionSession session =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(mIntentStarter)
                        .withEntryTransition(entry)
                        .withExitTransition(exit)
                        .build();
        session.cancel();

        session.start();

        assertThat(mIntentStarter.hasLaunched()).isFalse();
        assertThat(entry.started()).isFalse();
        assertThat(mIOriginTransitions.hasPendingReturnTransitions()).isFalse();
    }

    @Test
    public void sessionCancelled_returnTransitionNotPlayed() {
        FakeRemoteTransition entry = new FakeRemoteTransition();
        FakeRemoteTransition exit = new FakeRemoteTransition();
        OriginTransitionSession session =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(mIntentStarter)
                        .withEntryTransition(entry)
                        .withExitTransition(exit)
                        .build();

        session.start();
        session.cancel();

        assertThat(mIOriginTransitions.hasPendingReturnTransitions()).isFalse();
    }

    @Test
    public void multipleSessionsStarted_allTransitionsPlayed() {
        FakeRemoteTransition entry1 = new FakeRemoteTransition();
        FakeRemoteTransition exit1 = new FakeRemoteTransition();
        FakeIntentStarter starter1 = mIntentStarter;
        OriginTransitionSession session1 =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(starter1)
                        .withEntryTransition(entry1)
                        .withExitTransition(exit1)
                        .build();
        FakeRemoteTransition entry2 = new FakeRemoteTransition();
        FakeRemoteTransition exit2 = new FakeRemoteTransition();
        FakeIntentStarter starter2 = new FakeIntentStarter(TEST_ACTIVITY_2, TEST_ACTIVITY_3);
        OriginTransitionSession session2 =
                new OriginTransitionSession.Builder(mContext, mIOriginTransitions)
                        .withIntentStarter(starter2)
                        .withEntryTransition(entry2)
                        .withExitTransition(exit2)
                        .build();

        session1.start();

        assertThat(starter1.hasLaunched()).isTrue();
        assertThat(entry1.started()).isTrue();

        session2.start();

        assertThat(starter2.hasLaunched()).isTrue();
        assertThat(entry2.started()).isTrue();

        runReturnTransition(starter2);

        assertThat(exit2.started()).isTrue();

        runReturnTransition(starter1);

        assertThat(exit1.started()).isTrue();
    }

    private void runReturnTransition(FakeIntentStarter intentStarter) {
        TransitionInfo info =
                buildTransitionInfo(intentStarter.getToActivity(), intentStarter.getFromActivity());
        mIOriginTransitions.runReturnTransition(intentStarter.getTransitionOfLastLaunch(), info);
    }

    private static TransitionInfo buildTransitionInfo(ComponentName from, ComponentName to) {
        TransitionInfo info = new TransitionInfo(WindowManager.TRANSIT_OPEN, /* flags= */ 0);
        TransitionInfo.Change c1 =
                new TransitionInfo.Change(/* container= */ null, /* leash= */ null);
        c1.setMode(WindowManager.TRANSIT_OPEN);
        c1.setActivityComponent(to);
        TransitionInfo.Change c2 =
                new TransitionInfo.Change(/* container= */ null, /* leash= */ null);
        c2.setMode(WindowManager.TRANSIT_CLOSE);
        c2.setActivityComponent(from);
        info.addChange(c2);
        info.addChange(c1);
        return info;
    }

    private static class FakeIntentStarter implements Predicate<RemoteTransition> {
        private final ComponentName mFromActivity;
        private final ComponentName mToActivity;
        private final boolean mResult;

        @Nullable private RemoteTransition mTransition;
        private boolean mLaunched;

        FakeIntentStarter(ComponentName from, ComponentName to) {
            this(from, to, /* result= */ true);
        }

        FakeIntentStarter(ComponentName from, ComponentName to, boolean result) {
            mFromActivity = from;
            mToActivity = to;
            mResult = result;
        }

        @Override
        public boolean test(RemoteTransition transition) {
            if (mResult) {
                mLaunched = true;
                mTransition = transition;
                if (mTransition != null) {
                    TransitionInfo info = buildTransitionInfo(mFromActivity, mToActivity);
                    try {
                        transition
                                .getRemoteTransition()
                                .startAnimation(
                                        new Binder(),
                                        info,
                                        new SurfaceControl.Transaction(),
                                        new FakeFinishCallback());
                    } catch (RemoteException e) {

                    }
                }
            }
            return mResult;
        }

        @Nullable
        public RemoteTransition getTransitionOfLastLaunch() {
            return mTransition;
        }

        public ComponentName getFromActivity() {
            return mFromActivity;
        }

        public ComponentName getToActivity() {
            return mToActivity;
        }

        public boolean hasLaunched() {
            return mLaunched;
        }

        public void reset() {
            mTransition = null;
            mLaunched = false;
        }
    }

    private static class FakeIOriginTransitions extends IOriginTransitions.Stub {
        private final Map<RemoteTransition, RemoteTransition> mRecords = new ArrayMap<>();

        @Override
        public RemoteTransition makeOriginTransition(
                RemoteTransition launchTransition, RemoteTransition returnTransition) {
            mRecords.put(launchTransition, returnTransition);
            return launchTransition;
        }

        @Override
        public void cancelOriginTransition(RemoteTransition originTransition) {
            mRecords.remove(originTransition);
        }

        public void runReturnTransition(RemoteTransition originTransition, TransitionInfo info) {
            RemoteTransition transition = mRecords.remove(originTransition);
            try {
                transition
                        .getRemoteTransition()
                        .startAnimation(
                                new Binder(),
                                info,
                                new SurfaceControl.Transaction(),
                                new FakeFinishCallback());
            } catch (RemoteException e) {

            }
        }

        public boolean hasPendingReturnTransitions() {
            return !mRecords.isEmpty();
        }
    }

    private static class FakeFinishCallback extends IRemoteTransitionFinishedCallback.Stub {
        @Override
        public void onTransitionFinished(
                WindowContainerTransaction wct, SurfaceControl.Transaction sct) {}
    }

    private static class FakeRemoteTransition extends IRemoteTransition.Stub {
        private boolean mStarted;

        @Override
        public void startAnimation(
                IBinder token,
                TransitionInfo info,
                SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishCallback)
                throws RemoteException {
            mStarted = true;
            finishCallback.onTransitionFinished(null, null);
        }

        @Override
        public void mergeAnimation(
                IBinder transition,
                TransitionInfo info,
                SurfaceControl.Transaction t,
                IBinder mergeTarget,
                IRemoteTransitionFinishedCallback finishCallback) {}

        @Override
        public void takeOverAnimation(
                IBinder transition,
                TransitionInfo info,
                SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishCallback,
                WindowAnimationState[] states) {}

        @Override
        public void onTransitionConsumed(IBinder transition, boolean aborted) {}

        public boolean started() {
            return mStarted;
        }

        public void reset() {
            mStarted = false;
        }
    }
}
