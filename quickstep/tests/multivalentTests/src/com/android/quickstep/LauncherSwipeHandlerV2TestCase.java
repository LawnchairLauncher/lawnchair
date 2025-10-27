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

package com.android.quickstep;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.launcher3.Hotseat;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;
import com.android.launcher3.WorkspaceStateTransitionAnimation;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.LauncherMultivalentJUnit;
import com.android.quickstep.views.RecentsView;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@SmallTest
@RunWith(LauncherMultivalentJUnit.class)
@Ignore
public class LauncherSwipeHandlerV2TestCase extends AbsSwipeUpHandlerTestCase<
        LauncherState,
        QuickstepLauncher,
        RecentsView<QuickstepLauncher, LauncherState>,
        LauncherSwipeHandlerV2,
        LauncherActivityInterface> {

    @Mock private QuickstepLauncher mQuickstepLauncher;
    @Mock private RecentsView<QuickstepLauncher, LauncherState> mRecentsView;
    @Mock private Workspace<?> mWorkspace;
    @Mock private Hotseat mHotseat;
    @Mock private WorkspaceStateTransitionAnimation mTransitionAnimation;

    @Before
    public void setUpQuickStepLauncher() {
        when(mQuickstepLauncher.createAtomicAnimationFactory())
                .thenReturn(new AtomicAnimationFactory<>(0));
        when(mQuickstepLauncher.getHotseat()).thenReturn(mHotseat);
        doReturn(mWorkspace).when(mQuickstepLauncher).getWorkspace();
        doReturn(new StateManager(mQuickstepLauncher, LauncherState.NORMAL))
                .when(mQuickstepLauncher).getStateManager();

    }

    @Before
    public void setUpWorkspace() {
        when(mWorkspace.getStateTransitionAnimation()).thenReturn(mTransitionAnimation);
    }

    @NonNull
    @Override
    protected LauncherSwipeHandlerV2 createSwipeHandler(
            long touchTimeMs, boolean continuingLastGesture) {
        return new LauncherSwipeHandlerV2(
                mContext,
                mTaskAnimationManager,
                mGestureState,
                touchTimeMs,
                continuingLastGesture,
                mInputConsumerController,
                mMSDLPlayerWrapper);
    }

    @NonNull
    @Override
    protected QuickstepLauncher getRecentsContainer() {
        return mQuickstepLauncher;
    }

    @NonNull
    @Override
    protected RecentsView<QuickstepLauncher, LauncherState> getRecentsView() {
        return mRecentsView;
    }
}
