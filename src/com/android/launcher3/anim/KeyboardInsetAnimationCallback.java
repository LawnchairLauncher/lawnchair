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
 * Callback that animates views above the IME
 */
@RequiresApi(api = Build.VERSION_CODES.R)
public class KeyboardInsetAnimationCallback extends WindowInsetsAnimation.Callback {
    private final View mView;

    private float mInitialTranslation;
    private float mTerminalTranslation;

    public KeyboardInsetAnimationCallback(View view) {
        super(DISPATCH_MODE_STOP);
        mView = view;
    }

    @Override
    public void onPrepare(WindowInsetsAnimation animation) {
        mInitialTranslation = mView.getTranslationY();
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

        return windowInsets;
    }

    @Override
    public WindowInsetsAnimation.Bounds onStart(WindowInsetsAnimation animation,
            WindowInsetsAnimation.Bounds bounds) {
        mTerminalTranslation = mView.getTranslationY();
        if (mView instanceof KeyboardInsetListener) {
            ((KeyboardInsetListener) mView).onTranslationStart();
        }
        return super.onStart(animation, bounds);
    }

    @Override
    public void onEnd(WindowInsetsAnimation animation) {
        mView.setTranslationY(mTerminalTranslation);
        if (mView instanceof KeyboardInsetListener) {
            ((KeyboardInsetListener) mView).onTranslationEnd();
        }
        super.onEnd(animation);
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
         * Called from {@link KeyboardInsetAnimationCallback#onEnd}
         */
        void onTranslationEnd();
    }
}
