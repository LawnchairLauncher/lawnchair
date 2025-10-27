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

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.util.AllModulesForTest;
import com.android.launcher3.util.LauncherMultivalentJUnit;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.window.RecentsDisplayModel;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.fallback.window.RecentsWindowSwipeHandler;
import com.android.quickstep.views.RecentsViewContainer;

import dagger.BindsInstance;
import dagger.Component;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@SmallTest
@RunWith(LauncherMultivalentJUnit.class)
public class RecentsWindowSwipeHandlerTestCase extends AbsSwipeUpHandlerTestCase<
        RecentsState,
        RecentsWindowManager,
        FallbackRecentsView<RecentsWindowManager>,
        RecentsWindowSwipeHandler,
        FallbackWindowInterface> {

    @Mock private RecentsDisplayModel mRecentsDisplayModel;
    @Mock private FallbackRecentsView<RecentsWindowManager> mRecentsView;
    @Mock private RecentsWindowManager mRecentsWindowManager;

    @Before
    public void setRecentsDisplayModel() {
        mContext.initDaggerComponent(DaggerRecentsWindowSwipeHandlerTestCase_TestComponent.builder()
                .bindRecentsDisplayModel(mRecentsDisplayModel));
    }

    @NonNull
    @Override
    protected RecentsWindowSwipeHandler createSwipeHandler(long touchTimeMs,
            boolean continuingLastGesture) {
        return new RecentsWindowSwipeHandler(
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
    protected RecentsViewContainer getRecentsContainer() {
        return mRecentsWindowManager;
    }

    @NonNull
    @Override
    protected FallbackRecentsView<RecentsWindowManager> getRecentsView() {
        return mRecentsView;
    }

    @LauncherAppSingleton
    @Component(modules = {AllModulesForTest.class})
    interface TestComponent extends LauncherAppComponent {
        @Component.Builder
        interface Builder extends LauncherAppComponent.Builder {
            @BindsInstance Builder bindRecentsDisplayModel(RecentsDisplayModel model);
            @Override LauncherAppComponent build();
        }
    }
}
