/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.animation;

import android.annotation.FloatRange;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.view.SurfaceControl;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A composite {@link UIComponent.Transaction} that combines multiple other transactions for each ui
 * type.
 * @hide
 */
public class Transactions implements UIComponent.Transaction<UIComponent> {
    private final Map<Class, UIComponent.Transaction> mTransactions = new ArrayMap<>();

    /** Register a transaction object for updating a certain {@link UIComponent} type. */
    public <T extends UIComponent> Transactions registerTransactionForClass(
            Class<T> clazz, UIComponent.Transaction transaction) {
        mTransactions.put(clazz, transaction);
        return this;
    }

    private UIComponent.Transaction getTransactionFor(UIComponent ui) {
        UIComponent.Transaction transaction = mTransactions.get(ui.getClass());
        if (transaction == null) {
            transaction = ui.newTransaction();
            mTransactions.put(ui.getClass(), transaction);
        }
        return transaction;
    }

    @Override
    public Transactions setAlpha(UIComponent ui, @FloatRange(from = 0.0, to = 1.0) float alpha) {
        getTransactionFor(ui).setAlpha(ui, alpha);
        return this;
    }

    @Override
    public Transactions setVisible(UIComponent ui, boolean visible) {
        getTransactionFor(ui).setVisible(ui, visible);
        return this;
    }

    @Override
    public Transactions setBounds(UIComponent ui, Rect bounds) {
        getTransactionFor(ui).setBounds(ui, bounds);
        return this;
    }

    @Override
    public Transactions attachToTransitionLeash(
            UIComponent ui, SurfaceControl transitionLeash, int w, int h) {
        getTransactionFor(ui).attachToTransitionLeash(ui, transitionLeash, w, h);
        return this;
    }

    @Override
    public Transactions detachFromTransitionLeash(
            UIComponent ui, Executor executor, Runnable onDone) {
        getTransactionFor(ui).detachFromTransitionLeash(ui, executor, onDone);
        return this;
    }

    @Override
    public void commit() {
        mTransactions.values().forEach(UIComponent.Transaction::commit);
    }
}
