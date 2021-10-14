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
        float progress = list.get(0).getInterpolatedFraction();

        mView.setTranslationY(
                Utilities.mapRange(progress, mInitialTranslation, mTerminalTranslation));

        return windowInsets;
    }

    @Override
    public WindowInsetsAnimation.Bounds onStart(WindowInsetsAnimation animation,
            WindowInsetsAnimation.Bounds bounds) {
        mTerminalTranslation = mView.getTranslationY();
        mView.setTranslationY(mInitialTranslation);
        return super.onStart(animation, bounds);
    }
}
