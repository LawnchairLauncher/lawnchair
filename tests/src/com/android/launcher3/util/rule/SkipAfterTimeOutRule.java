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

package com.android.launcher3.util.rule;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

/** Makes sure that tests are skipped after a timed-out test to avoid test execution overlap. */
public class SkipAfterTimeOutRule implements TestRule {
    private static boolean sHadTimeoutException = false;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    if (sHadTimeoutException) {
                        throw new AssumptionViolatedException(
                                "An earlier test had TestTimedOutException");
                    }
                    base.evaluate();
                } catch (TestTimedOutException e) {
                    sHadTimeoutException = true;
                    throw e;
                }
            }
        };
    }
}
