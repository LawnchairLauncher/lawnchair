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

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;

import com.android.launcher3.Launcher;

import org.junit.rules.ExternalResource;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Similar to {@code ActivityScenarioRule} but it creates the activity lazily when needed
 */
public class LazyActivityRule<A extends Activity> extends ExternalResource {

    private final Supplier<ActivityScenario<A>> mScenarioSupplier;

    @Nullable private ActivityScenario<A> mScenario;

    /**
     * Constructs LazyActivityScenarioRule for a given scenario provider.
     */
    public LazyActivityRule(Supplier<ActivityScenario<A>> supplier) {
        mScenarioSupplier = supplier;
    }

    /**
     * Resets the rule, such that the activity is in closed state
     */
    public synchronized void reset() {
        if (mScenario != null) {
            try {
                mScenario.close();
            } catch (AssertionError e) {
                // Ignore errors during close
            }
        }
        mScenario = null;
    }

    @Override
    protected synchronized void after() {
        reset();
    }

    /**
     * Returns the scenario, creating one if it doesn't exist
     */
    public synchronized ActivityScenario<A> getScenario() {
        if (mScenario == null) {
            mScenario = mScenarioSupplier.get();
        }
        return mScenario;
    }

    /**
     * Executes the function {@code f} on the activities main thread and returns the result
     */
    public <T> T getFromActivity(Function<A, T> f) {
        AtomicReference<T> result = new AtomicReference<>();
        getScenario().onActivity(a -> result.set(f.apply(a)));
        return result.get();
    }

    /**
     * Runs the provided function {@code f} on the activity if the scenario is already created
     */
    public synchronized void runOnActivity(Consumer<A> f) {
        if (mScenario != null) {
            mScenario.onActivity(f::accept);
        }
    }

    /**
     * Returns a {@link LazyActivityRule} for the Launcher activity
     */
    public static <T extends Launcher> LazyActivityRule<T> forLauncher() {
        Context context = getInstrumentation().getTargetContext();
        // Create the activity after the model setup is done.
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
                        | FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | FLAG_ACTIVITY_NO_ANIMATION);
        ResolveInfo ri = context.getPackageManager().resolveActivity(
                new Intent(homeIntent).setPackage(context.getPackageName()), 0);
        homeIntent.setComponent(ri.getComponentInfo().getComponentName());
        return new LazyActivityRule<>(() -> ActivityScenario.launch(homeIntent));
    }
}
