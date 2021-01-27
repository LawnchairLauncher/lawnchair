/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.util.ViewPool.Reusable;

/**
 * Utility class to maintain a pool of reusable views.
 * During initialization, views are inflated on the background thread.
 */
public class ViewPool<T extends View & Reusable> {

    private final Object[] mPool;

    private final LayoutInflater mInflater;
    private final ViewGroup mParent;
    private final int mLayoutId;

    private int mCurrentSize = 0;

    public ViewPool(Context context, @Nullable ViewGroup parent,
            int layoutId, int maxSize, int initialSize) {
        mLayoutId = layoutId;
        mParent = parent;
        mInflater = LayoutInflater.from(context);
        mPool = new Object[maxSize];

        if (initialSize > 0) {
            initPool(initialSize);
        }
    }

    @UiThread
    private void initPool(int initialSize) {
        Preconditions.assertUIThread();
        Handler handler = new Handler();

        // LayoutInflater is not thread save as it maintains a global variable 'mConstructorArgs'.
        // Create a different copy to use on the background thread.
        LayoutInflater inflater = mInflater.cloneInContext(mInflater.getContext());

        // Inflate views on a non looper thread. This allows us to catch errors like calling
        // "new Handler()" in constructor easily.
        new Thread(() -> {
            for (int i = 0; i < initialSize; i++) {
                T view = inflateNewView(inflater);
                handler.post(() -> addToPool(view));
            }
        }, "ViewPool-init").start();
    }

    @UiThread
    public void recycle(T view) {
        Preconditions.assertUIThread();
        view.onRecycle();
        addToPool(view);
    }

    @UiThread
    private void addToPool(T view) {
        Preconditions.assertUIThread();
        if (mCurrentSize >= mPool.length) {
            // pool is full
            return;
        }

        mPool[mCurrentSize] = view;
        mCurrentSize++;
    }

    @UiThread
    public T getView() {
        Preconditions.assertUIThread();
        if (mCurrentSize > 0) {
            mCurrentSize--;
            return (T) mPool[mCurrentSize];
        }
        return inflateNewView(mInflater);
    }

    @AnyThread
    private T inflateNewView(LayoutInflater inflater) {
        return (T) inflater.inflate(mLayoutId, mParent, false);
    }

    /**
     * Interface to indicate that a view is reusable
     */
    public interface Reusable {

        /**
         * Called when a view is recycled / added back to the pool
         */
        void onRecycle();
    }
}
