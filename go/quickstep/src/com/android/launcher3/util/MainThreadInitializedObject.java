/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Looper;

import java.util.concurrent.ExecutionException;

/**
 * Utility class for defining singletons which are initiated on main thread.
 */
public class MainThreadInitializedObject<T extends SafeCloseable> {

    private final ObjectProvider<T> mProvider;
    private T mValue;

    public MainThreadInitializedObject(ObjectProvider<T> provider) {
        mProvider = provider;
    }

    public T get(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof ObjectSandbox sc) {
            return sc.getObject(this);
        }

        if (mValue == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                mValue = TraceHelper.allowIpcs("main.thread.object", () -> mProvider.get(app));
            } else {
                try {
                    return MAIN_EXECUTOR.submit(() -> get(context)).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return mValue;
    }

    public interface ObjectProvider<T> {

        T get(Context context);
    }

    /** Sandbox for isolating {@link MainThreadInitializedObject} instances from Launcher. */
    public interface ObjectSandbox {

        /**
         * Find a cached object from mObjectMap if we have already created one. If not, generate
         * an object using the provider.
         */
        <T extends SafeCloseable> T getObject(MainThreadInitializedObject<T> object);
    }
}
