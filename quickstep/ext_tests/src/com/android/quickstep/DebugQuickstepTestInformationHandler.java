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
package com.android.quickstep;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.launcher3.testing.DebugTestInformationHandler;
import com.android.launcher3.testing.shared.TestProtocol;

/**
 * Class to handle requests from tests, including debug ones, to Quickstep Launcher builds.
 */
public abstract class DebugQuickstepTestInformationHandler extends QuickstepTestInformationHandler {

    private final DebugTestInformationHandler mDebugTestInformationHandler;

    public DebugQuickstepTestInformationHandler(Context context) {
        super(context);
        mDebugTestInformationHandler = new DebugTestInformationHandler(context);
    }

    @Override
    public Bundle call(String method, String arg, @Nullable Bundle extras) {
        Bundle response = new Bundle();
        if (TestProtocol.REQUEST_RECREATE_TASKBAR.equals(method)) {
            // Allow null-pointer to catch illegal states.
            runOnTISBinder(tisBinder -> tisBinder.getTaskbarManager().recreateTaskbar());
            return response;
        }
        response = super.call(method, arg, extras);
        if (response != null) return response;
        return mDebugTestInformationHandler.call(method, arg, extras);
    }
}

