/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import androidx.annotation.Nullable;

import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.SystemUiProxy;
import com.android.wm.shell.shared.IHomeTransitionListener;

/**
 * Controls launcher response to home activity visibility changing.
 */
public class HomeTransitionController {

    @Nullable private QuickstepLauncher mLauncher;
    @Nullable private IHomeTransitionListener mHomeTransitionListener;

    public void registerHomeTransitionListener(QuickstepLauncher launcher) {
        mLauncher = launcher;
        mHomeTransitionListener = new IHomeTransitionListener.Stub() {
            @Override
            public void onHomeVisibilityChanged(boolean isVisible) {
                MAIN_EXECUTOR.execute(() -> {
                    if (mLauncher != null && mLauncher.getTaskbarUIController() != null) {
                        mLauncher.getTaskbarUIController().onLauncherVisibilityChanged(isVisible);
                    }
                });
            }
        };

        SystemUiProxy.INSTANCE.get(mLauncher).setHomeTransitionListener(mHomeTransitionListener);
    }

    public void unregisterHomeTransitionListener() {
        SystemUiProxy.INSTANCE.get(mLauncher).setHomeTransitionListener(null);
        mHomeTransitionListener = null;
        mLauncher = null;
    }
}
