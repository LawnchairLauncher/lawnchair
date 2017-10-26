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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.Property;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.anim.AnimationLayerSet;

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
 * This interpolator emulates the rate at which the perceived scale of an object changes
 * as its distance from a camera increases. When this interpolator is applied to a scale
 * animation on a view, it evokes the sense that the object is shrinking due to moving away
 * from the camera.
 */
class ZInterpolator implements TimeInterpolator {
    private float focalLength;

    public ZInterpolator(float foc) {
        focalLength = foc;
    }

    public float getInterpolation(float input) {
        return (1.0f - focalLength / (focalLength + input)) /
                (1.0f - focalLength / (focalLength + 1.0f));
    }
}

/**
 * The exact reverse of ZInterpolator.
 */
class InverseZInterpolator implements TimeInterpolator {
    private ZInterpolator zInterpolator;
    public InverseZInterpolator(float foc) {
        zInterpolator = new ZInterpolator(foc);
    }
    public float getInterpolation(float input) {
        return 1 - zInterpolator.getInterpolation(1 - input);
    }
}

/**
 * InverseZInterpolator compounded with an ease-out.
 */
class ZoomInInterpolator implements TimeInterpolator {
    private final InverseZInterpolator inverseZInterpolator = new InverseZInterpolator(0.35f);
    private final DecelerateInterpolator decelerate = new DecelerateInterpolator(3.0f);

    public float getInterpolation(float input) {
        return decelerate.getInterpolation(inverseZInterpolator.getInterpolation(input));
    }
}

/**
 * Manages the animations between each of the workspace states.
 */
public class WorkspaceStateTransitionAnimation {

    private static final PropertySetter NO_ANIM_PROPERTY_SETTER = new PropertySetter();

    private final ZoomInInterpolator mZoomInInterpolator = new ZoomInInterpolator();

    public final int mWorkspaceScrimAlpha;

    private final Launcher mLauncher;
    private final Workspace mWorkspace;

    private final boolean mWorkspaceFadeInAdjacentScreens;

    private float mNewScale;

    public WorkspaceStateTransitionAnimation(Launcher launcher, Workspace workspace) {
        mLauncher = launcher;
        mWorkspace = workspace;

        DeviceProfile grid = mLauncher.getDeviceProfile();
        Resources res = launcher.getResources();
        mWorkspaceScrimAlpha = res.getInteger(R.integer.config_workspaceScrimAlpha);
        mWorkspaceFadeInAdjacentScreens = grid.shouldFadeAdjacentWorkspaceScreens();
    }

    public void setState(LauncherState toState) {
        setWorkspaceProperty(toState, NO_ANIM_PROPERTY_SETTER);
    }

    public void setStateWithAnimation(LauncherState toState, AnimatorSet anim,
            AnimationLayerSet layerViews, AnimationConfig config) {
        AnimatedPropertySetter propertySetter =
                new AnimatedPropertySetter(config.duration, layerViews, anim);
        setWorkspaceProperty(toState, propertySetter);
    }

    public float getFinalScale() {
        return mNewScale;
    }

    /**
     * Starts a transition animation for the workspace.
     */
    private void setWorkspaceProperty(LauncherState state, PropertySetter propertySetter) {
        float[] scaleAndTranslationY = state.getWorkspaceScaleAndTranslation(mLauncher);
        mNewScale = scaleAndTranslationY[0];
        final float finalWorkspaceTranslationY = scaleAndTranslationY[1];

        int toPage = mWorkspace.getPageNearestToCenterOfScreen();
        final int childCount = mWorkspace.getChildCount();
        for (int i = 0; i < childCount; i++) {
            applyChildState(state, (CellLayout) mWorkspace.getChildAt(i), i, toPage,
                    propertySetter);
        }

        float finalHotseatAlpha = state.hideHotseat ? 0f : 1f;

        // This is true when transitioning between:
        // - Overview <-> Workspace
        propertySetter.setViewAlpha(null, mLauncher.getOverviewPanel(), 1 - finalHotseatAlpha);
        propertySetter.setViewAlpha(mWorkspace.createHotseatAlphaAnimator(finalHotseatAlpha),
                mLauncher.getHotseat(), finalHotseatAlpha);

        propertySetter.setFloat(mWorkspace, SCALE_PROPERTY, mNewScale, mZoomInInterpolator);
        propertySetter.setFloat(mWorkspace, View.TRANSLATION_Y,
                finalWorkspaceTranslationY, mZoomInInterpolator);

        // Set scrim
        propertySetter.setInt(mLauncher.getDragLayer().getScrim(), DRAWABLE_ALPHA,
                state.hasScrim ? mWorkspaceScrimAlpha : 0, new DecelerateInterpolator(1.5f));
    }

    public void applyChildState(LauncherState state, CellLayout cl, int childIndex) {
        applyChildState(state, cl, childIndex, mWorkspace.getPageNearestToCenterOfScreen(),
                NO_ANIM_PROPERTY_SETTER);
    }

    private void applyChildState(LauncherState state, CellLayout cl, int childIndex,
            int centerPage, PropertySetter propertySetter) {
        propertySetter.setInt(cl.getScrimBackground(),
                DRAWABLE_ALPHA, state.hasScrim ? 255 : 0, mZoomInInterpolator);

        // Only animate the page alpha when we actually fade pages
        if (mWorkspaceFadeInAdjacentScreens) {
            float finalAlpha = state == LauncherState.NORMAL && childIndex != centerPage ? 0 : 1f;
            propertySetter.setFloat(cl.getShortcutsAndWidgets(), View.ALPHA,
                    finalAlpha, mZoomInInterpolator);
        }
    }

    private static class PropertySetter {

        public void setViewAlpha(Animator anim, View view, float alpha) {
            if (anim != null) {
                anim.end();
                return;
            }
            view.setAlpha(alpha);
            AlphaUpdateListener.updateVisibility(view, isAccessibilityEnabled(view));
        }

        public <T> void setFloat(T target, Property<T, Float> property, float value,
                TimeInterpolator interpolator) {
            property.set(target, value);
        }

        public <T> void setInt(T target, Property<T, Integer> property, int value,
                TimeInterpolator interpolator) {
            property.set(target, value);
        }

        protected boolean isAccessibilityEnabled(View v) {
            AccessibilityManager am = (AccessibilityManager)
                    v.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            return am.isEnabled();
        }
    }

    private static class AnimatedPropertySetter extends PropertySetter {

        private final long mDuration;
        private final AnimationLayerSet mLayerViews;
        private final AnimatorSet mStateAnimator;

        AnimatedPropertySetter(long duration, AnimationLayerSet layerView, AnimatorSet anim) {
            mDuration = duration;
            mLayerViews = layerView;
            mStateAnimator = anim;
        }

        @Override
        public void setViewAlpha(Animator anim, View view, float alpha) {
            if (anim == null) {
                if (view.getAlpha() == alpha) {
                    return;
                }
                anim = ObjectAnimator.ofFloat(view, View.ALPHA, alpha);
                anim.addListener(new AlphaUpdateListener(view, isAccessibilityEnabled(view)));
            }

            anim.setDuration(mDuration).setInterpolator(getFadeInterpolator(alpha));
            mLayerViews.addView(view);
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
            return finalAlpha == 0 ? new DecelerateInterpolator(2) : null;
        }
    }
}