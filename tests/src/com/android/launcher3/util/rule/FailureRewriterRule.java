/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class FailureRewriterRule implements TestRule {
    private static final String TAG = "FailureRewriter";

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } catch (Throwable e) {
                    final int bug = FailureInvestigator.getBugForFailure(e.toString());
                    if (bug == 0) throw e;

                    Log.e(TAG, "Known bug found for the original failure "
                            + android.util.Log.getStackTraceString(e));
                    throw new AssertionError(
                            "Detected a failure that matches a known bug b/" + bug);
                }
            }
        };
    }
}
