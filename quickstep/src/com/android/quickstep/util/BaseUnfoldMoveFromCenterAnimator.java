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
package com.android.quickstep.util;

import android.annotation.CallSuper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.systemui.shared.animation.UnfoldMoveFromCenterAnimator;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Animation that moves launcher icons and widgets from center to the sides (final position)
 */
public abstract class BaseUnfoldMoveFromCenterAnimator implements TransitionProgressListener {

    private final UnfoldMoveFromCenterAnimator mMoveFromCenterAnimation;

    private final Map<ViewGroup, Boolean> mOriginalClipToPadding = new HashMap<>();
    private final Map<ViewGroup, Boolean> mOriginalClipChildren = new HashMap<>();

    public BaseUnfoldMoveFromCenterAnimator(WindowManager windowManager) {
        mMoveFromCenterAnimation = new UnfoldMoveFromCenterAnimator(windowManager,
                new LauncherViewsMoveFromCenterTranslationApplier());
    }

    @CallSuper
    @Override
    public void onTransitionStarted() {
        mMoveFromCenterAnimation.updateDisplayProperties();
        onPrepareViewsForAnimation();
        onTransitionProgress(0f);
    }

    @CallSuper
    @Override
    public void onTransitionProgress(float progress) {
        mMoveFromCenterAnimation.onTransitionProgress(progress);
    }

    @CallSuper
    @Override
    public void onTransitionFinished() {
        mMoveFromCenterAnimation.onTransitionFinished();
        mMoveFromCenterAnimation.clearRegisteredViews();

        mOriginalClipChildren.clear();
        mOriginalClipToPadding.clear();
    }

    protected void onPrepareViewsForAnimation() {

    }

    protected void registerViewForAnimation(View view) {
        mMoveFromCenterAnimation.registerViewForAnimation(view);
    }

    protected void disableClipping(ViewGroup view) {
        mOriginalClipToPadding.put(view, view.getClipToPadding());
        mOriginalClipChildren.put(view, view.getClipChildren());
        view.setClipToPadding(false);
        view.setClipChildren(false);
    }

    protected void restoreClipping(ViewGroup view) {
        final Boolean originalClipToPadding = mOriginalClipToPadding.get(view);
        if (originalClipToPadding != null) {
            view.setClipToPadding(originalClipToPadding);
        }
        final Boolean originalClipChildren = mOriginalClipChildren.get(view);
        if (originalClipChildren != null) {
            view.setClipChildren(originalClipChildren);
        }
    }
}
