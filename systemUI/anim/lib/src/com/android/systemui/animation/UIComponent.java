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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Rect;
import android.view.SurfaceControl;

import java.util.concurrent.Executor;

/**
 * An interface representing an UI component on the display.
 * @hide
 */
public interface UIComponent {

    /** Get the current alpha of this UI. */
    float getAlpha();

    /** Check if this UI is visible. */
    boolean isVisible();

    /** Get the bounds of this UI in its display. */
    @NonNull
    Rect getBounds();

    /** Create a new {@link Transaction} that can update this UI. */
    @NonNull
    Transaction newTransaction();

    /**
     * A transaction class for updating {@link UIComponent}.
     *
     * @param <T> the subtype of {@link UIComponent} that this {@link Transaction} can handle.
     * @hide
     */
    interface Transaction<T extends UIComponent> {
        /** Update alpha of an UI. Execution will be delayed until {@link #commit()} is called. */
        Transaction setAlpha(@NonNull T ui, @FloatRange(from = 0.0, to = 1.0) float alpha);

        /**
         * Update visibility of an UI. Execution will be delayed until {@link #commit()} is called.
         */
        @NonNull
        Transaction setVisible(@NonNull T ui, boolean visible);

        /** Update bounds of an UI. Execution will be delayed until {@link #commit()} is called. */
        @NonNull
        Transaction setBounds(@NonNull T ui, @NonNull Rect bounds);

        /**
         * Attach a ui to the transition leash. Execution will be delayed until {@link #commit()} is
         * called.
         */
        @NonNull
        Transaction attachToTransitionLeash(
                @NonNull T ui, @NonNull SurfaceControl transitionLeash, int w, int h);

        /**
         * Detach a ui from the transition leash. Execution will be delayed until {@link #commit} is
         * called.
         */
        @NonNull
        Transaction detachFromTransitionLeash(
                @NonNull T ui, @NonNull Executor executor, @Nullable Runnable onDone);

        /** Commit any pending changes added to this transaction. */
        void commit();
    }
}
