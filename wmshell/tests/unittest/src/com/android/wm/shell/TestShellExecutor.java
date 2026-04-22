/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell;

import com.android.wm.shell.common.ShellExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * Really basic test executor. It just gathers all events in a blob. The only option is to
 * execute everything at once. If better control over delayed execution is needed, please add it.
 */
public class TestShellExecutor implements ShellExecutor {
    final ArrayList<Runnable> mRunnables = new ArrayList<>();

    @Override
    public void execute(Runnable runnable) {
        mRunnables.add(runnable);
    }

    @Override
    public void executeDelayed(Runnable r, long delayMillis) {
        mRunnables.add(r);
    }

    @Override
    public void removeCallbacks(Runnable r) {
        mRunnables.remove(r);
    }

    @Override
    public boolean hasCallback(Runnable r) {
        return mRunnables.contains(r);
    }

    public void flushAll() {
        while (!mRunnables.isEmpty()) {
            mRunnables.remove(0).run();
        }
    }

    /** Returns the list of callbacks for this executor. */
    public List<Runnable> getCallbacks() {
        return mRunnables;
    }
}
