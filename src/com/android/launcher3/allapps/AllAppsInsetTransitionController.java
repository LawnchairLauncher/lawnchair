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

import android.annotation.TargetApi;
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

import com.android.launcher3.Utilities;
import com.android.launcher3.util.UiThreadHelper;

/**
 * Handles IME over all apps to be synchronously transitioning along with the passed in
 * root inset.
 */
public class AllAppsInsetTransitionController {

    private static final boolean DEBUG = true;
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

    // Only purpose of these states is to keep track of fast fling transition
    enum State {
        RESET, DRAG_START_BOTTOM, FLING_END_TOP,
        DRAG_START_TOP, FLING_END_BOTTOM
    }
    private State mState;

    public AllAppsInsetTransitionController(float allAppsHeight, View appsView) {
        mAllAppsHeight = allAppsHeight;
        mApps = appsView;
    }

    public void hide() {
        if (!Utilities.ATLEAST_R) return;

        WindowInsets insets = mApps.getRootWindowInsets();
        if (insets == null) return;

        boolean imeVisible = insets.isVisible(WindowInsets.Type.ime());

        if (DEBUG) {
            Log.d(TAG, "\nhide imeVisible=" +  imeVisible);
        }
        if (insets.isVisible(WindowInsets.Type.ime())) {
            mApps.getWindowInsetsController().hide(WindowInsets.Type.ime());
        }
    }

    /**
     * Initializes member variables and requests for the {@link WindowInsetsAnimationController}
     * object.
     *
     * @param progress value between 0..1
     */
    @TargetApi(Build.VERSION_CODES.R)
    public void onDragStart(float progress) {
        if (!Utilities.ATLEAST_R) return;

        // Until getRootWindowInsets().isVisible(...) method returns correct value,
        // only support InsetController based IME transition during swipe up and
        // NOT swipe down
        if (Float.compare(progress, 0f) == 0) return;

        setState(true, false, progress);
        mDown = progress * mAllAppsHeight;

        // Below two values are sometimes incorrect. Possibly a platform bug
        // mDownInsetBottom = mApps.getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
        // mShownAtDown = mApps.getRootWindowInsets().isVisible(WindowInsets.Type.ime());

        if (DEBUG) {
            Log.d(TAG, "\nonDragStart progress=" +  progress
                    + " mDownInsets=" + mDownInsetBottom
                    + " mShownAtDown=" + mShownAtDown);
        }

        mApps.getWindowInsetsController().controlWindowInsetsAnimation(
                WindowInsets.Type.ime(), -1 /* no predetermined duration */, LINEAR, null,
                mCurrentRequest = new WindowInsetsAnimationControlListener() {

                    @Override
                    public void onReady(WindowInsetsAnimationController controller, int types) {
                        if (DEBUG) {
                            Log.d(TAG, "Listener.onReady " + (mCurrentRequest == this));
                        }
                        if (controller != null) {
                            if (mCurrentRequest == this && !handleFinishOnFling(controller)) {
                                    mAnimationController = controller;
                            } else {
                                controller.finish(false /* just don't show */);
                            }
                        }
                    }

                    @Override
                    public void onFinished(WindowInsetsAnimationController controller) {
                        // when screen lock happens, then this method get called
                        if (DEBUG) {
                            Log.d(TAG, "Listener.onFinished ctrl=" + controller
                                    + " mAnimationController=" + mAnimationController);
                        }
                        if (mAnimationController != null) {
                            mAnimationController.finish(true);
                            mAnimationController = null;
                        }
                    }

                    @Override
                    public void onCancelled(@Nullable WindowInsetsAnimationController controller) {
                        if (DEBUG) {
                            Log.d(TAG, "Listener.onCancelled ctrl=" + controller
                                    + " mAnimationController=" + mAnimationController);
                        }
                        if (mState == State.DRAG_START_BOTTOM) {
                            mApps.getWindowInsetsController().show(WindowInsets.Type.ime());
                        }
                        mAnimationController = null;
                        if (controller != null) {
                            controller.finish(true);
                        }

                    }
                });
    }

    /**
     * If IME bounds after touch sequence finishes, call finish.
     */
    private boolean handleFinishOnFling(WindowInsetsAnimationController controller) {
        if (!Utilities.ATLEAST_R) return false;

        if (mState == State.FLING_END_TOP) {
            controller.finish(true);
            return true;
        } else if (mState == State.FLING_END_BOTTOM) {
            controller.finish(false);
            return true;
        }
        return false;
    }

    /**
     * Handles the translation using the progress.
     *
     * @param progress value between 0..1
     */
    @TargetApi(Build.VERSION_CODES.R)
    public void setProgress(float progress) {
        if (!Utilities.ATLEAST_R) return;
        // progress that equals to 0 or 1 is error prone. Do not use them.
        // Instead use onDragStart and onAnimationEnd
        if (mAnimationController == null || progress <= 0f || progress >= 1f) return;

        mCurrent = progress * mAllAppsHeight;
        mHiddenInsetBottom = mAnimationController.getHiddenStateInsets().bottom; // 0
        mShownInsetBottom = mAnimationController.getShownStateInsets().bottom; // 1155

        int shift = mShownAtDown ? 0 : (int) (mAllAppsHeight - mShownInsetBottom);

        int inset = (int) (mDownInsetBottom + (mDown - mCurrent) - shift);

        final int start = mShownAtDown ? mShownInsetBottom : mHiddenInsetBottom;
        final int end = mShownAtDown ? mHiddenInsetBottom : mShownInsetBottom;
        inset = Math.max(inset, mHiddenInsetBottom);
        inset = Math.min(inset, mShownInsetBottom);
        if (DEBUG || false) {
            Log.d(TAG, "updateInset mCurrent=" + mCurrent + " mDown="
                    + mDown + " hidden=" + mHiddenInsetBottom
                    + " shown=" + mShownInsetBottom
                    + " mDownInsets.bottom=" + mDownInsetBottom + " inset=" + inset
                    + " shift= " + shift);
        }

        mAnimationController.setInsetsAndAlpha(
                Insets.of(0, 0, 0, inset),
                1f, (inset - start) / (float) (end - start));
    }

    /**
     * Report to the animation controller that we no longer plan to translate anymore.
     *
     * @param progress value between 0..1
     */
    @TargetApi(Build.VERSION_CODES.R)
    public void onAnimationEnd(float progress) {
        if (DEBUG) {
            Log.d(TAG, "onAnimationEnd progress=" + progress
                    + " mAnimationController=" + mAnimationController);
        }
        if (mState == null) {
            // only called when launcher restarting.
            UiThreadHelper.hideKeyboardAsync(mApps.getContext(), mApps.getWindowToken());
        }
        setState(false, true, progress);
        if (mAnimationController == null) {
            return;
        }

        /* handle finish */
        if (mState == State.FLING_END_TOP) {
            mAnimationController.finish(true /* show */);
        } else {
            if (Float.compare(progress, 1f) == 0 /* bottom */) {
                mAnimationController.finish(false /* gone */);
            } else {
                mAnimationController.finish(mShownAtDown);
            }
        }
        /* handle finish */

        if (DEBUG) {
            Log.d(TAG, "endTranslation progress=" + progress
                    + " mAnimationController=" + mAnimationController);
        }
        mAnimationController = null;
        mCurrentRequest = null;
        setState(false, false, progress);
    }

    private void setState(boolean start, boolean end, float progress) {
        State state = State.RESET;
        if (start && end) {
            throw new IllegalStateException("drag start and end cannot happen in same call");
        }
        if (start) {
            if (Float.compare(progress, 1f) == 0) {
                state = State.DRAG_START_BOTTOM;
            } else if (Float.compare(progress, 0f) == 0) {
                state = State.DRAG_START_TOP;
            }
        } else if (end) {
            if (Float.compare(progress, 1f) == 0 && mState == State.DRAG_START_TOP) {
                state = State.FLING_END_BOTTOM;
            } else if (Float.compare(progress, 0f) == 0 && mState == State.DRAG_START_BOTTOM) {
                state = State.FLING_END_TOP;
            }
        }
        if (DEBUG) {
            Log.d(TAG, "setState " + mState + " -> " + state);
        }
        mState = state;
    }
}
