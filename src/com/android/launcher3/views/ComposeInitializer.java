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

package com.android.launcher3.views;

import android.os.Build;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

import com.android.launcher3.Utilities;

/**
 * An initializer to use Compose for classes implementing {@code ActivityContext}. This allows
 * adding ComposeView to ViewTree outside a {@link androidx.activity.ComponentActivity}.
 */
public final class ComposeInitializer {
    /**
     * Performs the initialization to use Compose in the ViewTree of {@code target}.
     */
    public static void initCompose(ActivityContext target) {
        getContentChild(target).addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {

                    @Override
                    public void onViewAttachedToWindow(View v) {
                        ComposeInitializer.onAttachedToWindow(v);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        ComposeInitializer.onDetachedFromWindow(v);
                    }
                });
    }

    /**
     * Find the "content child" for {@code target}.
     *
     * @see "WindowRecomposer.android.kt: [View.contentChild]"
     */
    private static View getContentChild(ActivityContext target) {
        View self = target.getDragLayer();
        ViewParent parent = self.getParent();
        while (parent instanceof View parentView) {
            if (parentView.getId() == android.R.id.content) return self;
            self = parentView;
            parent = self.getParent();
        }
        return self;
    }

    /**
     * Function to be called on your window root view's [View.onAttachedToWindow] function.
     */
    private static void onAttachedToWindow(View root) {
        if (ViewTreeLifecycleOwner.get(root) != null) {
            throw new IllegalStateException(
                    "View " + root + " already has a LifecycleOwner");
        }

        ViewParent parent = root.getParent();
        if (parent instanceof View && ((View) parent).getId() != android.R.id.content) {
            throw new IllegalStateException(
                    "ComposeInitializer.onContentChildAttachedToWindow(View) must be called on "
                            + "the content child. Outside of activities and dialogs, this is "
                            + "usually the top-most View of a window.");
        }

        // The lifecycle owner, which is STARTED when [root] is visible and RESUMED when [root]
        // is both visible and focused.
        ViewLifecycleOwner lifecycleOwner = new ViewLifecycleOwner(root);

        // We must call [ViewLifecycleOwner.onCreate] after creating the
        // [SavedStateRegistryOwner] because `onCreate` might move the lifecycle state to STARTED
        // which will make [SavedStateRegistryController.performRestore] throw.
        lifecycleOwner.onCreate();

        // Set the owners on the root. They will be reused by any ComposeView inside the root
        // hierarchy.
        ViewTreeLifecycleOwner.set(root, lifecycleOwner);
        ViewTreeSavedStateRegistryOwner.set(root, lifecycleOwner);
    }

    /**
     * Function to be called on your window root view's [View.onDetachedFromWindow] function.
     */
    private static void onDetachedFromWindow(View root) {
        final LifecycleOwner lifecycleOwner = ViewTreeLifecycleOwner.get(root);
        if (lifecycleOwner != null) {
            ((ViewLifecycleOwner) lifecycleOwner).onDestroy();
        }
        ViewTreeLifecycleOwner.set(root, null);
        ViewTreeSavedStateRegistryOwner.set(root, null);
    }

    /**
     * A [LifecycleOwner] for a [View] that updates lifecycle state based on window state.
     *
     * Also a trivial implementation of [SavedStateRegistryOwner] that does not do any save or
     * restore. This works for processes similar to the SystemUI process, which is always running
     * and top-level windows using this initialization are created once, when the process is
     * started.
     *
     * The implementation requires the caller to call [onCreate] and [onDestroy] when the view is
     * attached to or detached from a view hierarchy. After [onCreate] and before [onDestroy] is
     * called, the implementation monitors window state in the following way
     * * If the window is not visible, we are in the [Lifecycle.State.CREATED] state
     * * If the window is visible but not focused, we are in the [Lifecycle.State.STARTED] state
     * * If the window is visible and focused, we are in the [Lifecycle.State.RESUMED] state
     *
     * Or in table format:
     * ```
     * ┌───────────────┬───────────────────┬──────────────┬─────────────────┐
     * │ View attached │ Window Visibility │ Window Focus │ Lifecycle State │
     * ├───────────────┼───────────────────┴──────────────┼─────────────────┤
     * │ Not attached  │                 Any              │       N/A       │
     * ├───────────────┼───────────────────┬──────────────┼─────────────────┤
     * │               │    Not visible    │     Any      │     CREATED     │
     * │               ├───────────────────┼──────────────┼─────────────────┤
     * │   Attached    │                   │   No focus   │     STARTED     │
     * │               │      Visible      ├──────────────┼─────────────────┤
     * │               │                   │  Has focus   │     RESUMED     │
     * └───────────────┴───────────────────┴──────────────┴─────────────────┘
     * ```
     */
    private static class ViewLifecycleOwner implements SavedStateRegistryOwner {
        private final ViewTreeObserver.OnWindowFocusChangeListener mWindowFocusListener =
                hasFocus -> updateState();
        private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

        private final SavedStateRegistryController mSavedStateRegistryController =
                SavedStateRegistryController.create(this);

        private final View mView;
        private final Api34Impl mApi34Impl;

        ViewLifecycleOwner(View view) {
            mView = view;
            if (Utilities.ATLEAST_U) {
                mApi34Impl = new Api34Impl();
            } else {
                mApi34Impl = null;
            }

            mSavedStateRegistryController.performRestore(null);
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return mLifecycleRegistry;
        }

        @NonNull
        @Override
        public SavedStateRegistry getSavedStateRegistry() {
            return mSavedStateRegistryController.getSavedStateRegistry();
        }

        void onCreate() {
            mLifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
            if (Utilities.ATLEAST_U) {
                mApi34Impl.addOnWindowVisibilityChangeListener();
            }
            mView.getViewTreeObserver().addOnWindowFocusChangeListener(
                    mWindowFocusListener);
            updateState();
        }

        void onDestroy() {
            if (Utilities.ATLEAST_U) {
                mApi34Impl.removeOnWindowVisibilityChangeListener();
            }
            mView.getViewTreeObserver().removeOnWindowFocusChangeListener(
                    mWindowFocusListener);
            mLifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
        }

        private void updateState() {
            Lifecycle.State state =
                    mView.getWindowVisibility() != View.VISIBLE ? Lifecycle.State.CREATED
                            : (!mView.hasWindowFocus() ? Lifecycle.State.STARTED
                                    : Lifecycle.State.RESUMED);
            mLifecycleRegistry.setCurrentState(state);
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private class Api34Impl {
            private final ViewTreeObserver.OnWindowVisibilityChangeListener
                    mWindowVisibilityListener =
                    visibility -> updateState();

            void addOnWindowVisibilityChangeListener() {
                mView.getViewTreeObserver().addOnWindowVisibilityChangeListener(
                        mWindowVisibilityListener);
            }

            void removeOnWindowVisibilityChangeListener() {
                mView.getViewTreeObserver().removeOnWindowVisibilityChangeListener(
                        mWindowVisibilityListener);
            }
        }
    }
}
