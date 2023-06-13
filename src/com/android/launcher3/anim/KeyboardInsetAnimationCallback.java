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
package com.android.launcher3.anim;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;

import androidx.annotation.RequiresApi;

import com.android.launcher3.Utilities;

import java.util.List;

/**
 * Callback that animates views above the IME.
 * <p>
 * The expected stages of a keyboard transition are:
 * <p>
 * <ul>
 *   <li>PREPARING: Keyboard insets haven't changed yet, but are about to.</li>
 *   <li>STARTED: Keyboard insets have temporarily changed to the end state, but not drawn.</li>
 *   <li>PROGRESSING: At least one frame of the animation has been drawn.</li>
 *   <li>FINISHED: Keyboard has reached its end state, and animation is complete.</li>
 * </ul>
 */
@RequiresApi(api = Build.VERSION_CODES.R)
public class KeyboardInsetAnimationCallback extends WindowInsetsAnimation.Callback {
    private final View mView;

    private float mInitialTranslation;
    private float mTerminalTranslation;
    private KeyboardTranslationState mKeyboardTranslationState = KeyboardTranslationState.SYSTEM;

    /** Current state of the keyboard. */
    public enum KeyboardTranslationState {
        // We are not controlling the keyboard, and it may or may not be translating.
        SYSTEM,
        // We are about to gain control of the keyboard, but the current state may be transient.
        MANUAL_PREPARED,
        // We are manually translating the keyboard.
        MANUAL_ONGOING
    }

    public KeyboardInsetAnimationCallback(View view) {
        super(DISPATCH_MODE_STOP);
        mView = view;
    }

    public KeyboardTranslationState getKeyboardTranslationState() {
        return mKeyboardTranslationState;
    }

    @Override
    public void onPrepare(WindowInsetsAnimation animation) {
        mKeyboardTranslationState = KeyboardTranslationState.MANUAL_PREPARED;
        mInitialTranslation = mView.getTranslationY();
    }

    @Override
    public WindowInsetsAnimation.Bounds onStart(WindowInsetsAnimation animation,
            WindowInsetsAnimation.Bounds bounds) {
        // Final insets have temporarily been applied, so store the current translation as final.
        mTerminalTranslation = mView.getTranslationY();
        // Reset the translation in case the view is drawn before onProgress gets called.
        mView.setTranslationY(mInitialTranslation);
        mKeyboardTranslationState = KeyboardTranslationState.MANUAL_ONGOING;
        if (mView instanceof KeyboardInsetListener) {
            ((KeyboardInsetListener) mView).onTranslationStart();
        }
        return super.onStart(animation, bounds);
    }

    @Override
    public WindowInsets onProgress(WindowInsets windowInsets, List<WindowInsetsAnimation> list) {
        if (list.size() == 0) {
            mView.setTranslationY(mInitialTranslation);
            return windowInsets;
        }
        WindowInsetsAnimation animation = list.get(0);

        if (animation.getDurationMillis() > -1) {
            float progress = animation.getInterpolatedFraction();
            mView.setTranslationY(
                    Utilities.mapRange(progress, mInitialTranslation, mTerminalTranslation));
        } else {
            // Manually controlled animation: Set translation to keyboard height.
            int translationY = -windowInsets.getInsets(WindowInsets.Type.ime()).bottom;
            if (mView.getParent() instanceof View) {
                // Offset any translation of the parent (e.g. All Apps parallax).
                translationY -= ((View) mView.getParent()).getTranslationY();
            }
            mView.setTranslationY(translationY);
        }

        if (mView instanceof KeyboardInsetListener) {
            ((KeyboardInsetListener) mView).onKeyboardAlphaChanged(animation.getAlpha());
        }

        return windowInsets;
    }

    @Override
    public void onEnd(WindowInsetsAnimation animation) {
        if (mView instanceof KeyboardInsetListener) {
            ((KeyboardInsetListener) mView).onTranslationEnd();
        }
        mKeyboardTranslationState = KeyboardTranslationState.SYSTEM;
    }

    /**
     * Interface Allowing views to listen for keyboard translation events
     */
    public interface KeyboardInsetListener {
        /**
         * Called from {@link KeyboardInsetAnimationCallback#onStart}
         */
        void onTranslationStart();

        /**
         * Called from {@link KeyboardInsetAnimationCallback#onProgress}
         *
         * @param alpha the current IME alpha
         */
        default void onKeyboardAlphaChanged(float alpha) {}

        /**
         * Called from {@link KeyboardInsetAnimationCallback#onEnd}
         */
        void onTranslationEnd();
    }
}
