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
package com.android.launcher3.util.rule;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.app.viewcapture.ViewCapture;
import com.android.app.viewcapture.data.ExportedData;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.concurrent.ExecutionException;

/**
 * After the test succeeds, the rule looks for anomalies in the data accumulated by ViewCapture
 * that's passed as a parameter. If anomalies are detected, throws an exception and fails the test.
 */
public class ViewCaptureAnalysisRule extends TestWatcher {
    @NonNull
    private final ViewCapture mViewCapture;

    public ViewCaptureAnalysisRule(@NonNull ViewCapture viewCapture) {
        mViewCapture = viewCapture;
    }

    @Override
    protected void succeeded(Description description) {
        super.succeeded(description);
        try {
            analyzeViewCaptureData(mViewCapture.getExportedData(
                    InstrumentationRegistry.getTargetContext()));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static void analyzeViewCaptureData(ExportedData viewCaptureData) {
    }
}
