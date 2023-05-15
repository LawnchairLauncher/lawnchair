/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.util.rule;

import android.app.Activity;

import com.android.launcher3.Launcher;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.Callable;

/**
 * Test rule to get the current Launcher activity.
 */
public class LauncherActivityRule extends SimpleActivityRule<Launcher> {

    public LauncherActivityRule() {
        super(Launcher.class);
    }

    @Override
    public Statement apply(Statement base, Description description) {

        return new MyStatement(base) {
            @Override
            public void onActivityStarted(Activity activity) {
                if (activity instanceof Launcher) {
                    ((Launcher) activity).getRotationHelper().forceAllowRotationForTesting(true);
                }
            }
        };
    }

    public Callable<Boolean> itemExists(final ItemOperator op) {
        return () -> {
            Launcher launcher = getActivity();
            if (launcher == null) {
                return false;
            }
            return launcher.getWorkspace().getFirstMatch(op) != null;
        };
    }
}