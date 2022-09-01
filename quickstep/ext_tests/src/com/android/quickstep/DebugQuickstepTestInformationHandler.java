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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.testing.DebugTestInformationHandler;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.util.TISBindHelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

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
        switch (method) {
            case TestProtocol.REQUEST_ENABLE_MANUAL_TASKBAR_STASHING:
                runOnTISBinder(tisBinder -> {
                    enableManualTaskbarStashing(tisBinder, true);
                });
                return response;

            case TestProtocol.REQUEST_DISABLE_MANUAL_TASKBAR_STASHING:
                runOnTISBinder(tisBinder -> {
                    enableManualTaskbarStashing(tisBinder, false);
                });
                return response;

            case TestProtocol.REQUEST_UNSTASH_TASKBAR_IF_STASHED:
                runOnTISBinder(tisBinder -> {
                    enableManualTaskbarStashing(tisBinder, true);

                    // Allow null-pointer to catch illegal states.
                    tisBinder.getTaskbarManager().getCurrentActivityContext()
                            .unstashTaskbarIfStashed();

                    enableManualTaskbarStashing(tisBinder, false);
                });
                return response;

            case TestProtocol.REQUEST_STASHED_TASKBAR_HEIGHT: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(R.dimen.taskbar_stashed_size));
                return response;
            }

            case TestProtocol.REQUEST_RECREATE_TASKBAR:
                // Allow null-pointer to catch illegal states.
                runOnTISBinder(tisBinder -> tisBinder.getTaskbarManager().recreateTaskbar());
                return response;

            default:
                response = super.call(method, arg, extras);
                if (response != null) return response;
                return mDebugTestInformationHandler.call(method, arg, extras);
        }
    }

    private void enableManualTaskbarStashing(TISBinder tisBinder, boolean enable) {
        // Allow null-pointer to catch illegal states.
        tisBinder.getTaskbarManager().getCurrentActivityContext().enableManualStashingForTests(
                enable);
    }

    /**
     * Runs the given command on the UI thread, after ensuring we are connected to
     * TouchInteractionService.
     */
    private void runOnTISBinder(Consumer<TISBinder> connectionCallback) {
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            TISBindHelper helper = MAIN_EXECUTOR.submit(() ->
                    new TISBindHelper(mContext, tisBinder -> {
                        connectionCallback.accept(tisBinder);
                        countDownLatch.countDown();
                    })).get();
            countDownLatch.await();
            MAIN_EXECUTOR.submit(helper::onDestroy);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private interface UIThreadCommand {

        void execute(Launcher launcher);
    }
}

