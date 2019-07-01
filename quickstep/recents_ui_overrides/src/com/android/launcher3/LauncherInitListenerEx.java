/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.appprediction.PredictionUiStateManager.Client;

import java.util.function.BiPredicate;

public class LauncherInitListenerEx extends LauncherInitListener {

    public LauncherInitListenerEx(BiPredicate<Launcher, Boolean> onInitListener) {
        super(onInitListener);
    }

    @Override
    protected boolean init(Launcher launcher, boolean alreadyOnHome) {
        PredictionUiStateManager.INSTANCE.get(launcher).switchClient(Client.OVERVIEW);
        return super.init(launcher, alreadyOnHome);
    }
}
