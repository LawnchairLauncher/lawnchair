/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.util;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;
import org.mockito.Mockito;

public class GlobalTestRunListener extends RunListener {
    /**
     * See {@link RunListener#testFinished} which executes per atomic test.
     * {@link RunListener#testSuiteFinished} which executes per test suite. Test suite = test class
     * in this context.
     * {@link RunListener#testRunFinished} which executes after everything (all test suites) is
     * completed.
     */
    @Override
    public void testSuiteFinished(Description description) throws Exception {
        // This method runs after every test class and will clear mocks after every test class
        // execution is completed.
        Mockito.framework().clearInlineMocks();
        super.testSuiteFinished(description);
    }
}
