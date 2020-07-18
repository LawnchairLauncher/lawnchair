/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.graphics.Insets;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimationControlListener;
import android.view.WindowInsetsAnimationController;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.os.BuildCompat;

/**
 * Handles IME over all apps to be synchronously transitioning along with the passed in
 * root inset.
 */
public class AllAppsInsetTransitionController {

    private static final boolean DEBUG = false;
    private static final String TAG = "AllAppsInsetTransitionController";
    private static final Interpolator LINEAR = new LinearInterpolator();

    private WindowInsetsAnimationController mAnimationController;
    private WindowInsetsAnimationControlListener mCurrentRequest;

    private float mAllAppsHeight;

    private int mDownInsetBottom;
    private boolean mShownAtDown;

    private int mHiddenInsetBottom;
    private int mShownInsetBottom;

    private float mDown, mCurrent;
    private View mApps;

    public AllAppsInsetTransitionController(float allAppsHeight, View appsView) {
        mAllAppsHeight = allAppsHeight;
        mApps = appsView;
    }

    /**
     * Initializes member variables and requests for the {@link WindowInsetsAnimationController}
     * object.
     *
     * @param progress value between 0..1
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public void onDragStart(float progress) {
        if (!BuildCompat.isAtLeastR()) return;
        onAnimationEnd(progress);

        mDown = progress * mAllAppsHeight;

        // Below two values are sometimes incorrect. Possibly a platform bug
        mDownInsetBottom = mApps.getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
        mShownAtDown = mApps.getRootWindowInsets().isVisible(WindowInsets.Type.ime());

        // override this value based on what it should actually be.
        mShownAtDown = Float.compare(progress, 1f) == 0 ? false : true;
        mDownInsetBottom = mShownAtDown ? mShownInsetBottom : mHiddenInsetBottom;
        if (DEBUG) {
            Log.d(TAG, "\nonDragStart mDownInsets=" + mDownInsetBottom
                    + " mShownAtDown =" + mShownAtDown);
        }

        mApps.getWindowInsetsController().controlWindowInsetsAnimation(
                WindowInsets.Type.ime(), -1 /* no predetermined duration */, LINEAR, null,
                mCurrentRequest = new WindowInsetsAnimationControlListener() {

                    @Override
                    public void onReady(WindowInsetsAnimationController controller, int types) {
                        if (DEBUG) {
                            Log.d(TAG, "Listener.onReady " + (mCurrentRequest == this));
                        }
                        if (mCurrentRequest == this) {
                            mAnimationController = controller;
                        } else {
                            controller.finish(mShownAtDown);
                        }
                    }

                    @Override
                    public void onFinished(WindowInsetsAnimationController controller) {
                        // when screen lock happens, then this method get called
                        mAnimationController.finish(false);
                        mAnimationController = null;
                        if (DEBUG) {
                            Log.d(TAG, "Listener.onFinished ctrl=" + controller);
                        }
                    }

                    @Override
                    public void onCancelled(@Nullable WindowInsetsAnimationController controller) {
                        mAnimationController = null;
                        if (controller != null) {
                            controller.finish(mShownAtDown);
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Listener.onCancelled ctrl=" + controller);
                        }
                    }
                });
    }

    /**
     * Handles the translation using the progress.
     *
     * @param progress value between 0..1
     */
    @RequiresApi(api = 30)
    public void setProgress(float progress) {
        if (!BuildCompat.isAtLeastR()) return;
        // progress that equals to 0 or 1 is error prone. Do not use them.
        // Instead use onDragStart and onAnimationEnd
        if (mAnimationController == null || progress <= 0f || progress >= 1f) return;

        mCurrent = progress * mAllAppsHeight;
        mHiddenInsetBottom = mAnimationController.getHiddenStateInsets().bottom; // 0
        mShownInsetBottom = mAnimationController.getShownStateInsets().bottom; // 1155

        int shift = mShownAtDown ? 0 : (int) (mAllAppsHeight - mShownInsetBottom);

        int inset = (int) (mDownInsetBottom + (mDown - mCurrent) - shift);

        if (DEBUG) {
            Log.d(TAG, "updateInset mCurrent=" + mCurrent + " mDown="
                    + mDown + " hidden=" + mHiddenInsetBottom
                    + " shown=" + mShownInsetBottom
                    + " mDownInsets.bottom=" + mDownInsetBottom + " inset:" + inset
                    + " shift: " + shift);
        }
        final int start = mShownAtDown ? mShownInsetBottom : mHiddenInsetBottom;
        final int end = mShownAtDown ? mHiddenInsetBottom : mShownInsetBottom;
        inset = Math.max(inset, mHiddenInsetBottom);
        inset = Math.min(inset, mShownInsetBottom);
        Log.d(TAG, "updateInset inset:" + inset);

        mAnimationController.setInsetsAndAlpha(
                Insets.of(0, 0, 0, inset),
                1f, (inset - start) / (float) (end - start));
    }

    /**
     * Report to the animation controller that we no longer plan to translate anymore.
     *
     * @param progress value between 0..1
     */
    @RequiresApi(api = 30)
    public void onAnimationEnd(float progress) {
        if (DEBUG) {
            Log.d(TAG, "endTranslation progress=" + progress
                    + " mAnimationController=" + mAnimationController);
        }

        if (mAnimationController == null) return;

        if (Float.compare(progress, 1f) == 0 /* bottom */) {
            mAnimationController.finish(false /* gone */);
        }
        if (Float.compare(progress, 0f) == 0 /* top */) {
            mAnimationController.finish(true /* show */);
        }
        mAnimationController = null;
        mCurrentRequest = null;
    }
}
