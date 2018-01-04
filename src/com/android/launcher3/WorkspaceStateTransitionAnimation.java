/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.LauncherAnimUtils.DRAWABLE_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.util.Property;
import android.view.View;

import com.android.launcher3.LauncherState.PageAlphaProvider;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;

/**
 * A convenience class to update a view's visibility state after an alpha animation.
 */
class AlphaUpdateListener extends AnimatorListenerAdapter implements ValueAnimator.AnimatorUpdateListener {
    private static final float ALPHA_CUTOFF_THRESHOLD = 0.01f;

    private View mView;
    private boolean mAccessibilityEnabled;
    private boolean mCanceled = false;

    public AlphaUpdateListener(View v, boolean accessibilityEnabled) {
        mView = v;
        mAccessibilityEnabled = accessibilityEnabled;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator arg0) {
        updateVisibility(mView, mAccessibilityEnabled);
    }

    public static void updateVisibility(View view, boolean accessibilityEnabled) {
        // We want to avoid the extra layout pass by setting the views to GONE unless
        // accessibility is on, in which case not setting them to GONE causes a glitch.
        int invisibleState = accessibilityEnabled ? View.GONE : View.INVISIBLE;
        if (view.getAlpha() < ALPHA_CUTOFF_THRESHOLD && view.getVisibility() != invisibleState) {
            view.setVisibility(invisibleState);
        } else if (view.getAlpha() > ALPHA_CUTOFF_THRESHOLD
                && view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        mCanceled = true;
    }

    @Override
    public void onAnimationEnd(Animator arg0) {
        if (mCanceled) return;
        updateVisibility(mView, mAccessibilityEnabled);
    }

    @Override
    public void onAnimationStart(Animator arg0) {
        // We want the views to be visible for animation, so fade-in/out is visible
        mView.setVisibility(View.VISIBLE);
    }
}

/**
 * Manages the animations between each of the workspace states.
 */
public class WorkspaceStateTransitionAnimation {

    public static final PropertySetter NO_ANIM_PROPERTY_SETTER = new PropertySetter();

    public final int mWorkspaceScrimAlpha;

    private final Launcher mLauncher;
    private final Workspace mWorkspace;

    private float mNewScale;

    public WorkspaceStateTransitionAnimation(Launcher launcher, Workspace workspace) {
        mLauncher = launcher;
        mWorkspace = workspace;
        mWorkspaceScrimAlpha = launcher.getResources()
                .getInteger(R.integer.config_workspaceScrimAlpha);
    }

    public void setState(LauncherState toState) {
        setWorkspaceProperty(toState, NO_ANIM_PROPERTY_SETTER);
    }

    public void setStateWithAnimation(LauncherState toState, AnimatorSetBuilder builder,
            AnimationConfig config) {
        AnimatedPropertySetter propertySetter =
                new AnimatedPropertySetter(config.duration, builder);
        setWorkspaceProperty(toState, propertySetter);
    }

    public float getFinalScale() {
        return mNewScale;
    }

    /**
     * Starts a transition animation for the workspace.
     */
    private void setWorkspaceProperty(LauncherState state, PropertySetter propertySetter) {
        float[] scaleAndTranslation = state.getWorkspaceScaleAndTranslation(mLauncher);
        mNewScale = scaleAndTranslation[0];
        PageAlphaProvider pageAlphaProvider = state.getWorkspacePageAlphaProvider(mLauncher);
        final int childCount = mWorkspace.getChildCount();
        for (int i = 0; i < childCount; i++) {
            applyChildState(state, (CellLayout) mWorkspace.getChildAt(i), i, pageAlphaProvider,
                    propertySetter);
        }

        propertySetter.setFloat(mWorkspace, SCALE_PROPERTY, mNewScale, Interpolators.ZOOM_IN);
        propertySetter.setFloat(mWorkspace, View.TRANSLATION_X,
                scaleAndTranslation[1], Interpolators.ZOOM_IN);
        propertySetter.setFloat(mWorkspace, View.TRANSLATION_Y,
                scaleAndTranslation[2], Interpolators.ZOOM_IN);

        float hotseatAlpha = state.getHoseatAlpha(mLauncher);
        propertySetter.setViewAlpha(mWorkspace.createHotseatAlphaAnimator(hotseatAlpha),
                mLauncher.getHotseat(), hotseatAlpha);

        // Set scrim
        propertySetter.setInt(mLauncher.getDragLayer().getScrim(), DRAWABLE_ALPHA,
                state.hasScrim ? mWorkspaceScrimAlpha : 0, Interpolators.DEACCEL_1_5);
    }

    public void applyChildState(LauncherState state, CellLayout cl, int childIndex) {
        applyChildState(state, cl, childIndex, state.getWorkspacePageAlphaProvider(mLauncher),
                NO_ANIM_PROPERTY_SETTER);
    }

    private void applyChildState(LauncherState state, CellLayout cl, int childIndex,
            PageAlphaProvider pageAlphaProvider, PropertySetter propertySetter) {
        float pageAlpha = pageAlphaProvider.getPageAlpha(childIndex);
        int drawableAlpha = Math.round(pageAlpha * (state.hasScrim ? 255 : 0));

        propertySetter.setInt(cl.getScrimBackground(),
                DRAWABLE_ALPHA, drawableAlpha, Interpolators.ZOOM_IN);
        propertySetter.setFloat(cl.getShortcutsAndWidgets(), View.ALPHA,
                pageAlpha, pageAlphaProvider.interpolator);
    }

    public static class PropertySetter {

        public void setViewAlpha(Animator anim, View view, float alpha) {
            if (anim != null) {
                anim.end();
                return;
            }
            view.setAlpha(alpha);
            AlphaUpdateListener.updateVisibility(view, isAccessibilityEnabled(view.getContext()));
        }

        public <T> void setFloat(T target, Property<T, Float> property, float value,
                TimeInterpolator interpolator) {
            property.set(target, value);
        }

        public <T> void setInt(T target, Property<T, Integer> property, int value,
                TimeInterpolator interpolator) {
            property.set(target, value);
        }
    }

    public static class AnimatedPropertySetter extends PropertySetter {

        private final long mDuration;
        private final AnimatorSetBuilder mStateAnimator;

        public AnimatedPropertySetter(long duration, AnimatorSetBuilder builder) {
            mDuration = duration;
            mStateAnimator = builder;
        }

        @Override
        public void setViewAlpha(Animator anim, View view, float alpha) {
            if (anim == null) {
                if (view.getAlpha() == alpha) {
                    return;
                }
                anim = ObjectAnimator.ofFloat(view, View.ALPHA, alpha);
                anim.addListener(new AlphaUpdateListener(view,
                        isAccessibilityEnabled(view.getContext())));
            }

            anim.setDuration(mDuration).setInterpolator(getFadeInterpolator(alpha));
            mStateAnimator.play(anim);
        }

        @Override
        public <T> void setFloat(T target, Property<T, Float> property, float value,
                TimeInterpolator interpolator) {
            if (property.get(target) == value) {
                return;
            }
            Animator anim = ObjectAnimator.ofFloat(target, property, value);
            anim.setDuration(mDuration).setInterpolator(interpolator);
            mStateAnimator.play(anim);
        }

        @Override
        public <T> void setInt(T target, Property<T, Integer> property, int value,
                TimeInterpolator interpolator) {
            if (property.get(target) == value) {
                return;
            }
            Animator anim = ObjectAnimator.ofInt(target, property, value);
            anim.setDuration(mDuration).setInterpolator(interpolator);
            mStateAnimator.play(anim);
        }

        private TimeInterpolator getFadeInterpolator(float finalAlpha) {
            return finalAlpha == 0 ? Interpolators.DEACCEL_2 : null;
        }
    }
}