/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wm.shell.bubbles.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.FloatProperty;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.wm.shell.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Layout that constructs physics-based animations for each of its children, which behave according
 * to settings provided by a {@link PhysicsAnimationController} instance.
 *
 * See physics-animation-layout.md.
 */
public class PhysicsAnimationLayout extends FrameLayout {
    private static final String TAG = "Bubbs.PAL";

    /**
     * Controls the construction, configuration, and use of the physics animations supplied by this
     * layout.
     */
    abstract static class PhysicsAnimationController {

        /** Configures a given {@link PhysicsPropertyAnimator} for a view at the given index. */
        interface ChildAnimationConfigurator {

            /**
             * Called to configure the animator for the view at the given index.
             *
             * This method should make use of methods such as
             * {@link PhysicsPropertyAnimator#translationX} and
             * {@link PhysicsPropertyAnimator#withStartDelay} to configure the animation.
             *
             * Implementations should not call {@link PhysicsPropertyAnimator#start}, this will
             * happen elsewhere after configuration is complete.
             */
            void configureAnimationForChildAtIndex(int index, PhysicsPropertyAnimator animation);
        }

        /**
         * Returned by {@link #animationsForChildrenFromIndex} to allow starting multiple animations
         * on multiple child views at the same time.
         */
        interface MultiAnimationStarter {

            /**
             * Start all animations and call the given end actions once all animations have
             * completed.
             */
            void startAll(Runnable... endActions);
        }

        /**
         * Constant to return from {@link #getNextAnimationInChain} if the animation should not be
         * chained at all.
         */
        protected static final int NONE = -1;

        /** Set of properties for which the layout should construct physics animations. */
        abstract Set<DynamicAnimation.ViewProperty> getAnimatedProperties();

        /**
         * Returns the index of the next animation after the given index in the animation chain, or
         * {@link #NONE} if it should not be chained, or if the chain should end at the given index.
         *
         * If a next index is returned, an update listener will be added to the animation at the
         * given index that dispatches value updates to the animation at the next index. This
         * creates a 'following' effect.
         *
         * Typical implementations of this method will return either index + 1, or index - 1, to
         * create forward or backward chains between adjacent child views, but this is not required.
         */
        abstract int getNextAnimationInChain(DynamicAnimation.ViewProperty property, int index);

        /**
         * Offsets to be added to the value that chained animations of the given property dispatch
         * to subsequent child animations.
         *
         * This is used for things like maintaining the 'stack' effect in Bubbles, where bubbles
         * stack off to the left or right side slightly.
         */
        abstract float getOffsetForChainedPropertyAnimation(
                DynamicAnimation.ViewProperty property, int index);

        /**
         * Returns the SpringForce to be used for the given child view's property animation. Despite
         * these usually being similar or identical across properties and views, {@link SpringForce}
         * also contains the SpringAnimation's final position, so we have to construct a new one for
         * each animation rather than using a constant.
         */
        abstract SpringForce getSpringForce(DynamicAnimation.ViewProperty property, View view);

        /**
         * Called when a new child is added at the specified index. Controllers can use this
         * opportunity to animate in the new view.
         */
        abstract void onChildAdded(View child, int index);

        /**
         * Called with a child view that has been removed from the layout, from the given index. The
         * passed view has been removed from the layout and added back as a transient view, which
         * renders normally, but is not part of the normal view hierarchy and will not be considered
         * by getChildAt() and getChildCount().
         *
         * The controller can perform animations on the child (either manually, or by using
         * {@link #animationForChild(View)}), and then call finishRemoval when complete.
         *
         * finishRemoval must be called by implementations of this method, or transient views will
         * never be removed.
         */
        abstract void onChildRemoved(View child, int index, Runnable finishRemoval);

        /** Called when a child view has been reordered in the view hierachy. */
        abstract void onChildReordered(View child, int oldIndex, int newIndex);

        /**
         * Called when the controller is set as the active animation controller for the given
         * layout. Once active, the controller can start animations using the animator instances
         * returned by {@link #animationForChild}.
         *
         * While all animations started by the previous controller will be cancelled, the new
         * controller should not make any assumptions about the state of the layout or its children.
         * Their translation, alpha, scale, etc. values may have been changed by the previous
         * controller and should be reset here if relevant.
         */
        abstract void onActiveControllerForLayout(PhysicsAnimationLayout layout);

        protected PhysicsAnimationLayout mLayout;

        PhysicsAnimationController() { }

        /** Whether this controller is the currently active controller for its associated layout. */
        protected boolean isActiveController() {
            return mLayout != null && this == mLayout.mController;
        }

        protected void setLayout(PhysicsAnimationLayout layout) {
            this.mLayout = layout;
            onActiveControllerForLayout(layout);
        }

        protected PhysicsAnimationLayout getLayout() {
            return mLayout;
        }

        /**
         * Returns a {@link PhysicsPropertyAnimator} instance for the given child view.
         */
        protected PhysicsPropertyAnimator animationForChild(View child) {
            PhysicsPropertyAnimator animator =
                    (PhysicsPropertyAnimator) child.getTag(R.id.physics_animator_tag);

            if (animator == null) {
                animator = mLayout.new PhysicsPropertyAnimator(child);
                child.setTag(R.id.physics_animator_tag, animator);
            }

            animator.clearAnimator();
            animator.setAssociatedController(this);

            return animator;
        }

        /** Returns a {@link PhysicsPropertyAnimator} instance for child at the given index. */
        protected PhysicsPropertyAnimator animationForChildAtIndex(int index) {
            return animationForChild(mLayout.getChildAt(index));
        }


        protected MultiAnimationStarter animationsForChildrenFromIndex(
                int startIndex, ChildAnimationConfigurator configurator) {
            return animationsForChildrenFromIndex(startIndex, /* fadeChildren= */ false,
                    configurator);
        }

        /**
         * Returns a {@link MultiAnimationStarter} whose startAll method will start the physics
         * animations for all children from startIndex onward. The provided configurator will be
         * called with each child's {@link PhysicsPropertyAnimator}, where it can set up each
         * animation appropriately.
         */
        protected MultiAnimationStarter animationsForChildrenFromIndex(
                int startIndex, boolean fadeChildren, ChildAnimationConfigurator configurator) {
            final Set<DynamicAnimation.ViewProperty> allAnimatedProperties = new HashSet<>();
            final List<PhysicsPropertyAnimator> allChildAnims = new ArrayList<>();

            // Retrieve the animator for each child, ask the configurator to configure it, then save
            // it and the properties it chose to animate.
            for (int i = startIndex; i < mLayout.getChildCount(); i++) {
                final PhysicsPropertyAnimator anim = fadeChildren
                        ? animationForChildAtIndex(i).alpha(0)
                        : animationForChildAtIndex(i);
                configurator.configureAnimationForChildAtIndex(i, anim);
                allAnimatedProperties.addAll(anim.getAnimatedProperties());
                allChildAnims.add(anim);
            }

            // Return a MultiAnimationStarter that will start all of the child animations, and also
            // add a multiple property end listener to the layout that will call the end action
            // provided to startAll() once all animations on the animated properties complete.
            return (endActions) -> {
                final Runnable runAllEndActions = () -> {
                    for (Runnable action : endActions) {
                        action.run();
                    }
                };

                // If there aren't any children to animate, just run the end actions.
                if (mLayout.getChildCount() == 0) {
                    runAllEndActions.run();
                    return;
                }

                if (endActions != null) {
                    setEndActionForMultipleProperties(
                            runAllEndActions,
                            allAnimatedProperties.toArray(
                                    new DynamicAnimation.ViewProperty[0]));
                }

                for (PhysicsPropertyAnimator childAnim : allChildAnims) {
                    childAnim.start();
                }
            };
        }

        /**
         * Sets an end action that will be run when all child animations for a given property have
         * stopped running.
         */
        protected void setEndActionForProperty(
                Runnable action, DynamicAnimation.ViewProperty property) {
            mLayout.mEndActionForProperty.put(property, action);
        }

        /**
         * Sets an end action that will be run when all child animations for all of the given
         * properties have stopped running.
         */
        protected void setEndActionForMultipleProperties(
                Runnable action, DynamicAnimation.ViewProperty... properties) {
            final Runnable checkIfAllFinished = () -> {
                if (!mLayout.arePropertiesAnimating(properties)) {
                    action.run();

                    for (DynamicAnimation.ViewProperty property : properties) {
                        removeEndActionForProperty(property);
                    }
                }
            };

            for (DynamicAnimation.ViewProperty property : properties) {
                setEndActionForProperty(checkIfAllFinished, property);
            }
        }

        /**
         * Removes the end listener that would have been called when all child animations for a
         * given property stopped running.
         */
        protected void removeEndActionForProperty(DynamicAnimation.ViewProperty property) {
            mLayout.mEndActionForProperty.remove(property);
        }
    }

    /**
     * End actions that are called when every child's animation of the given property has finished.
     */
    protected final HashMap<DynamicAnimation.ViewProperty, Runnable> mEndActionForProperty =
            new HashMap<>();

    /** The currently active animation controller. */
    @Nullable protected PhysicsAnimationController mController;

    public PhysicsAnimationLayout(Context context) {
        super(context);
    }

    /**
     * Sets the animation controller and constructs or reconfigures the layout's physics animations
     * to meet the controller's specifications.
     */
    public void setActiveController(PhysicsAnimationController controller) {
        cancelAllAnimations();
        mEndActionForProperty.clear();

        this.mController = controller;
        mController.setLayout(this);

        // Set up animations for this controller's animated properties.
        for (DynamicAnimation.ViewProperty property : mController.getAnimatedProperties()) {
            setUpAnimationsForProperty(property);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        addViewInternal(child, index, params, false /* isReorder */);
    }

    /** Removes the child view immediately. */
    public void removeViewNoAnimation(View view) {
        super.removeView(view);
        view.setTag(R.id.physics_animator_tag, null);
    }

    @Override
    public void removeView(View view) {
        if (mController != null) {
            final int index = indexOfChild(view);

            // Remove the view and add it back as a transient view so we can animate it out.
            super.removeView(view);
            addTransientView(view, index);

            // Tell the controller to animate this view out, and call the callback when it's
            // finished.
            mController.onChildRemoved(view, index, () -> {
                // The controller says it's done with the transient view, cancel animations in case
                // any are still running and then remove it.
                cancelAnimationsOnView(view);
                removeTransientView(view);
            });
        } else {
            // Without a controller, nobody will animate this view out, so it gets an unceremonious
            // departure.
            super.removeView(view);
        }
    }

    @Override
    public void removeViewAt(int index) {
        removeView(getChildAt(index));
    }

    /** Immediately re-orders the view to the given index. */
    public void reorderView(View view, int index) {
        if (view == null) {
            return;
        }
        final int oldIndex = indexOfChild(view);

        if (oldIndex == index) return;

        super.removeView(view);
        if (view.getParent() != null) {
            // View still has a parent. This could have been added as a transient view.
            // Remove it from transient views.
            super.removeTransientView(view);
        }
        addViewInternal(view, index, view.getLayoutParams(), true /* isReorder */);

        if (mController != null) {
            mController.onChildReordered(view, oldIndex, index);
        }
    }

    /** Checks whether any animations of the given properties are still running. */
    public boolean arePropertiesAnimating(DynamicAnimation.ViewProperty... properties) {
        for (int i = 0; i < getChildCount(); i++) {
            if (arePropertiesAnimatingOnView(getChildAt(i), properties)) {
                return true;
            }
        }

        return false;
    }

    /** Checks whether any animations of the given properties are running on the given view. */
    public boolean arePropertiesAnimatingOnView(
            View view, DynamicAnimation.ViewProperty... properties) {
        final ObjectAnimator targetAnimator = getTargetAnimatorFromView(view);
        for (DynamicAnimation.ViewProperty property : properties) {
            final SpringAnimation animation = getSpringAnimationFromView(property, view);
            if (animation != null && animation.isRunning()) {
                return true;
            }

            // If the target animator is running, its update listener will trigger the translation
            // physics animations at some point. We should consider the translation properties to be
            // be animating in this case, even if the physics animations haven't been started yet.
            final boolean isTranslation =
                    property.equals(DynamicAnimation.TRANSLATION_X)
                            || property.equals(DynamicAnimation.TRANSLATION_Y)
                            || property.equals(DynamicAnimation.TRANSLATION_Z);
            if (isTranslation && targetAnimator != null && targetAnimator.isRunning()) {
                return true;
            }
        }

        return false;
    }

    /** Cancels all animations that are running on all child views, for all properties. */
    public void cancelAllAnimations() {
        if (mController == null) {
            return;
        }

        cancelAllAnimationsOfProperties(
                mController.getAnimatedProperties().toArray(new DynamicAnimation.ViewProperty[]{}));
    }

    /** Cancels all animations that are running on all child views, for the given properties. */
    public void cancelAllAnimationsOfProperties(DynamicAnimation.ViewProperty... properties) {
        if (mController == null) {
            return;
        }

        for (int i = 0; i < getChildCount(); i++) {
            for (DynamicAnimation.ViewProperty property : properties) {
                final DynamicAnimation anim = getSpringAnimationAtIndex(property, i);
                if (anim != null) {
                    anim.cancel();
                }
            }
            final ViewPropertyAnimator anim = getViewPropertyAnimatorFromView(getChildAt(i));
            if (anim != null) {
                anim.cancel();
            }
        }
    }

    /** Cancels all of the physics animations running on the given view. */
    public void cancelAnimationsOnView(View view) {
        // If present, cancel the target animator so it doesn't restart the translation physics
        // animations.
        final ObjectAnimator targetAnimator = getTargetAnimatorFromView(view);
        if (targetAnimator != null) {
            targetAnimator.cancel();
        }

        // Cancel physics animations on the view.
        for (DynamicAnimation.ViewProperty property : mController.getAnimatedProperties()) {
            final DynamicAnimation animationFromView = getSpringAnimationFromView(property, view);
            if (animationFromView != null) {
                animationFromView.cancel();
            }
        }
    }

    protected boolean isActiveController(PhysicsAnimationController controller) {
        return mController == controller;
    }

    /** Whether the first child would be left of center if translated to the given x value. */
    protected boolean isFirstChildXLeftOfCenter(float x) {
        if (getChildCount() > 0) {
            return x + (getChildAt(0).getWidth() / 2) < getWidth() / 2;
        } else {
            return false; // If there's no first child, really anything is correct, right?
        }
    }

    /** ViewProperty's toString is useless, this returns a readable name for debug logging. */
    protected static String getReadablePropertyName(DynamicAnimation.ViewProperty property) {
        if (property.equals(DynamicAnimation.TRANSLATION_X)) {
            return "TRANSLATION_X";
        } else if (property.equals(DynamicAnimation.TRANSLATION_Y)) {
            return "TRANSLATION_Y";
        } else if (property.equals(DynamicAnimation.TRANSLATION_Z)) {
            return "TRANSLATION_Z";
        } else if (property.equals(DynamicAnimation.SCALE_X)) {
            return "SCALE_X";
        } else if (property.equals(DynamicAnimation.SCALE_Y)) {
            return "SCALE_Y";
        } else if (property.equals(DynamicAnimation.ALPHA)) {
            return "ALPHA";
        } else {
            return "Unknown animation property.";
        }
    }

    /**
     * Adds a view to the layout. If this addition is not the result of a call to
     * {@link #reorderView}, this will also notify the controller via
     * {@link PhysicsAnimationController#onChildAdded} and set up animations for the view.
     */
    private void addViewInternal(
            View child, int index, ViewGroup.LayoutParams params, boolean isReorder) {
        super.addView(child, index, params);

        // Set up animations for the new view, if the controller is set. If it isn't set, we'll be
        // setting up animations for all children when setActiveController is called.
        if (mController != null && !isReorder) {
            for (DynamicAnimation.ViewProperty property : mController.getAnimatedProperties()) {
                setUpAnimationForChild(property, child);
            }

            mController.onChildAdded(child, index);
        }
    }

    /**
     * Retrieves the animation of the given property from the view at the given index via the view
     * tag system.
     */
    @Nullable private SpringAnimation getSpringAnimationAtIndex(
            DynamicAnimation.ViewProperty property, int index) {
        return getSpringAnimationFromView(property, getChildAt(index));
    }

    /**
     * Retrieves the spring animation of the given property from the view via the view tag system.
     */
    @Nullable private SpringAnimation getSpringAnimationFromView(
            DynamicAnimation.ViewProperty property, View view) {
        if (view == null) return null;
        return (SpringAnimation) view.getTag(getTagIdForProperty(property));
    }

    /**
     * Retrieves the view property animation of the given property from the view via the view tag
     * system.
     */
    @Nullable private ViewPropertyAnimator getViewPropertyAnimatorFromView(View view) {
        if (view == null) return null;
        return (ViewPropertyAnimator) view.getTag(R.id.reorder_animator_tag);
    }

    /** Retrieves the target animator from the view via the view tag system. */
    @Nullable private ObjectAnimator getTargetAnimatorFromView(View view) {
        if (view == null) return null;
        return (ObjectAnimator) view.getTag(R.id.target_animator_tag);
    }

    /** Sets up SpringAnimations of the given property for each child view in the layout. */
    private void setUpAnimationsForProperty(DynamicAnimation.ViewProperty property) {
        for (int i = 0; i < getChildCount(); i++) {
            setUpAnimationForChild(property, getChildAt(i));
        }
    }

    /** Constructs a SpringAnimation of the given property for a child view. */
    private void setUpAnimationForChild(DynamicAnimation.ViewProperty property, View child) {
        SpringAnimation newAnim = new SpringAnimation(child, property);
        newAnim.addUpdateListener((animation, value, velocity) -> {
            final int indexOfChild = indexOfChild(child);
            final int nextAnimInChain = mController.getNextAnimationInChain(property, indexOfChild);
            if (nextAnimInChain == PhysicsAnimationController.NONE || indexOfChild < 0) {
                return;
            }

            final float offset = mController.getOffsetForChainedPropertyAnimation(property,
                    nextAnimInChain);
            if (nextAnimInChain < getChildCount()) {
                final SpringAnimation nextAnim = getSpringAnimationAtIndex(
                        property, nextAnimInChain);
                if (nextAnim != null) {
                    nextAnim.animateToFinalPosition(value + offset);
                }
            }
        });

        newAnim.setSpring(mController.getSpringForce(property, child));
        newAnim.addEndListener(new AllAnimationsForPropertyFinishedEndListener(property));
        child.setTag(getTagIdForProperty(property), newAnim);
    }

    /** Return a stable ID to use as a tag key for the given property's animations. */
    private int getTagIdForProperty(DynamicAnimation.ViewProperty property) {
        if (property.equals(DynamicAnimation.TRANSLATION_X)) {
            return R.id.translation_x_dynamicanimation_tag;
        } else if (property.equals(DynamicAnimation.TRANSLATION_Y)) {
            return R.id.translation_y_dynamicanimation_tag;
        } else if (property.equals(DynamicAnimation.TRANSLATION_Z)) {
            return R.id.translation_z_dynamicanimation_tag;
        } else if (property.equals(DynamicAnimation.SCALE_X)) {
            return R.id.scale_x_dynamicanimation_tag;
        } else if (property.equals(DynamicAnimation.SCALE_Y)) {
            return R.id.scale_y_dynamicanimation_tag;
        } else if (property.equals(DynamicAnimation.ALPHA)) {
            return R.id.alpha_dynamicanimation_tag;
        }

        return -1;
    }

    /**
     * End listener that is added to each individual DynamicAnimation, which dispatches to a single
     * listener when every other animation of the given property is no longer running.
     *
     * This is required since chained DynamicAnimations can stop and start again due to changes in
     * upstream animations. This means that adding an end listener to just the last animation is not
     * sufficient. By firing only when every other animation on the property has stopped running, we
     * ensure that no animation will be restarted after the single end listener is called.
     */
    protected class AllAnimationsForPropertyFinishedEndListener
            implements DynamicAnimation.OnAnimationEndListener {
        private DynamicAnimation.ViewProperty mProperty;

        AllAnimationsForPropertyFinishedEndListener(DynamicAnimation.ViewProperty property) {
            this.mProperty = property;
        }

        @Override
        public void onAnimationEnd(
                DynamicAnimation anim, boolean canceled, float value, float velocity) {
            if (!arePropertiesAnimating(mProperty)) {
                if (mEndActionForProperty.containsKey(mProperty)) {
                    final Runnable callback = mEndActionForProperty.get(mProperty);

                    if (callback != null) {
                        callback.run();
                    }
                }
            }
        }
    }

    /**
     * Animator class returned by {@link PhysicsAnimationController#animationForChild}, to allow
     * controllers to animate child views using physics animations.
     *
     * See docs/physics-animation-layout.md for documentation and examples.
     */
    protected class PhysicsPropertyAnimator {
        /** The view whose properties this animator animates. */
        private View mView;

        /** Start velocity to use for all property animations. */
        private float mDefaultStartVelocity = -Float.MAX_VALUE;

        /** Start delay to use when start is called. */
        private long mStartDelay = 0;

        /** Damping ratio to use for the animations. */
        private float mDampingRatio = -1;

        /** Stiffness to use for the animations. */
        private float mStiffness = -1;

        /** End actions to call when animations for the given property complete. */
        private Map<DynamicAnimation.ViewProperty, Runnable[]> mEndActionsForProperty =
                new HashMap<>();

        /**
         * Start velocities to use for TRANSLATION_X and TRANSLATION_Y, since these are often
         * provided by VelocityTrackers and differ from each other.
         */
        private Map<DynamicAnimation.ViewProperty, Float> mPositionStartVelocities =
                new HashMap<>();

        /**
         * End actions to call when both TRANSLATION_X and TRANSLATION_Y animations have completed,
         * if {@link #position} was used to animate TRANSLATION_X and TRANSLATION_Y simultaneously.
         */
        @Nullable private Runnable[] mPositionEndActions;

        /**
         * All of the properties that have been set and will animate when {@link #start} is called.
         */
        private Map<DynamicAnimation.ViewProperty, Float> mAnimatedProperties = new HashMap<>();

        /**
         * All of the initial property values that have been set. These values will be instantly set
         * when {@link #start} is called, just before the animation begins.
         */
        private Map<DynamicAnimation.ViewProperty, Float> mInitialPropertyValues = new HashMap<>();

        /** The animation controller that last retrieved this animator instance. */
        private PhysicsAnimationController mAssociatedController;

        /**
         * Animator used to traverse the path provided to {@link #followAnimatedTargetAlongPath}. As
         * the path is traversed, the view's translation spring animation final positions are
         * updated such that the view 'follows' the current position on the path.
         */
        @Nullable private ObjectAnimator mPathAnimator;

        /** Current position on the path. This is animated by {@link #mPathAnimator}. */
        private PointF mCurrentPointOnPath = new PointF();

        /**
         * FloatProperty instances that can be passed to {@link ObjectAnimator} to animate the value
         * of {@link #mCurrentPointOnPath}.
         */
        private final FloatProperty<PhysicsPropertyAnimator> mCurrentPointOnPathXProperty =
                new FloatProperty<PhysicsPropertyAnimator>("PathX") {
            @Override
            public void setValue(PhysicsPropertyAnimator object, float value) {
                mCurrentPointOnPath.x = value;
            }

            @Override
            public Float get(PhysicsPropertyAnimator object) {
                return mCurrentPointOnPath.x;
            }
        };

        private final FloatProperty<PhysicsPropertyAnimator> mCurrentPointOnPathYProperty =
                new FloatProperty<PhysicsPropertyAnimator>("PathY") {
            @Override
            public void setValue(PhysicsPropertyAnimator object, float value) {
                mCurrentPointOnPath.y = value;
            }

            @Override
            public Float get(PhysicsPropertyAnimator object) {
                return mCurrentPointOnPath.y;
            }
        };

        protected PhysicsPropertyAnimator(View view) {
            this.mView = view;
        }

        /** Animate a property to the given value, then call the optional end actions. */
        public PhysicsPropertyAnimator property(
                DynamicAnimation.ViewProperty property, float value, Runnable... endActions) {
            mAnimatedProperties.put(property, value);
            mEndActionsForProperty.put(property, endActions);
            return this;
        }

        /** Animate the view's alpha value to the provided value. */
        public PhysicsPropertyAnimator alpha(float alpha, Runnable... endActions) {
            return property(DynamicAnimation.ALPHA, alpha, endActions);
        }

        /** Set the view's alpha value to 'from', then animate it to the given value. */
        public PhysicsPropertyAnimator alpha(float from, float to, Runnable... endActions) {
            mInitialPropertyValues.put(DynamicAnimation.ALPHA, from);
            return alpha(to, endActions);
        }

        /** Animate the view's translationX value to the provided value. */
        public PhysicsPropertyAnimator translationX(float translationX, Runnable... endActions) {
            mPathAnimator = null; // We aren't using the path anymore if we're translating.
            return property(DynamicAnimation.TRANSLATION_X, translationX, endActions);
        }

        /** Animate the view's translationZ value to the provided value. */
        public PhysicsPropertyAnimator translationZ(float translationZ, Runnable... endActions) {
            mPathAnimator = null; // We aren't using the path anymore if we're translating.
            return property(DynamicAnimation.TRANSLATION_Z, translationZ, endActions);
        }

        /** Set the view's translationX value to 'from', then animate it to the given value. */
        public PhysicsPropertyAnimator translationX(
                float from, float to, Runnable... endActions) {
            mInitialPropertyValues.put(DynamicAnimation.TRANSLATION_X, from);
            return translationX(to, endActions);
        }

        /** Animate the view's translationY value to the provided value. */
        public PhysicsPropertyAnimator translationY(float translationY, Runnable... endActions) {
            mPathAnimator = null; // We aren't using the path anymore if we're translating.
            return property(DynamicAnimation.TRANSLATION_Y, translationY, endActions);
        }

        /** Set the view's translationY value to 'from', then animate it to the given value. */
        public PhysicsPropertyAnimator translationY(
                float from, float to, Runnable... endActions) {
            mInitialPropertyValues.put(DynamicAnimation.TRANSLATION_Y, from);
            return translationY(to, endActions);
        }

        /**
         * Animate the view's translationX and translationY values, and call the end actions only
         * once both TRANSLATION_X, TRANSLATION_Y and TRANSLATION_Z animations have completed.
         */
        public PhysicsPropertyAnimator position(float translationX, float translationY,
                float translationZ, Runnable... endActions) {
            mPositionEndActions = endActions;
            translationX(translationX);
            translationY(translationY);
            return translationZ(translationZ);
        }

        /**
         * Animates a 'target' point that moves along the given path, using the provided duration
         * and interpolator to animate the target. The view itself is animated using physics-based
         * animations, whose final positions are updated to the target position as it animates. This
         * results in the view 'following' the target in a realistic way.
         *
         * This method will override earlier calls to {@link #translationX}, {@link #translationY},
         * or {@link #position}, ultimately animating the view's position to the final point on the
         * given path.
         *
         * @param pathAnimEndActions End actions to run after the animator that moves the target
         *                           along the path ends. The views following the target may still
         *                           be moving.
         */
        public PhysicsPropertyAnimator followAnimatedTargetAlongPath(
                Path path,
                int targetAnimDuration,
                TimeInterpolator targetAnimInterpolator,
                Runnable... pathAnimEndActions) {
            if (mPathAnimator != null) {
                mPathAnimator.cancel();
            }

            mPathAnimator = ObjectAnimator.ofFloat(
                    this, mCurrentPointOnPathXProperty, mCurrentPointOnPathYProperty, path);

            if (pathAnimEndActions != null) {
                mPathAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        for (Runnable action : pathAnimEndActions) {
                            if (action != null) {
                                action.run();
                            }
                        }
                    }
                });
            }

            mPathAnimator.setDuration(targetAnimDuration);
            mPathAnimator.setInterpolator(targetAnimInterpolator);

            // Remove translation related values since we're going to ignore them and follow the
            // path instead.
            clearTranslationValues();
            return this;
        }

        private void clearTranslationValues() {
            mAnimatedProperties.remove(DynamicAnimation.TRANSLATION_X);
            mAnimatedProperties.remove(DynamicAnimation.TRANSLATION_Y);
            mAnimatedProperties.remove(DynamicAnimation.TRANSLATION_Z);
            mInitialPropertyValues.remove(DynamicAnimation.TRANSLATION_X);
            mInitialPropertyValues.remove(DynamicAnimation.TRANSLATION_Y);
            mInitialPropertyValues.remove(DynamicAnimation.TRANSLATION_Z);
            mEndActionForProperty.remove(DynamicAnimation.TRANSLATION_X);
            mEndActionForProperty.remove(DynamicAnimation.TRANSLATION_Y);
            mEndActionForProperty.remove(DynamicAnimation.TRANSLATION_Z);
        }

        /** Animate the view's scaleX value to the provided value. */
        public PhysicsPropertyAnimator scaleX(float scaleX, Runnable... endActions) {
            return property(DynamicAnimation.SCALE_X, scaleX, endActions);
        }

        /** Set the view's scaleX value to 'from', then animate it to the given value. */
        public PhysicsPropertyAnimator scaleX(float from, float to, Runnable... endActions) {
            mInitialPropertyValues.put(DynamicAnimation.SCALE_X, from);
            return scaleX(to, endActions);
        }

        /** Animate the view's scaleY value to the provided value. */
        public PhysicsPropertyAnimator scaleY(float scaleY, Runnable... endActions) {
            return property(DynamicAnimation.SCALE_Y, scaleY, endActions);
        }

        /** Set the view's scaleY value to 'from', then animate it to the given value. */
        public PhysicsPropertyAnimator scaleY(float from, float to, Runnable... endActions) {
            mInitialPropertyValues.put(DynamicAnimation.SCALE_Y, from);
            return scaleY(to, endActions);
        }

        /** Set the start velocity to use for all property animations. */
        public PhysicsPropertyAnimator withStartVelocity(float startVel) {
            mDefaultStartVelocity = startVel;
            return this;
        }

        /**
         * Set the damping ratio to use for this animation. If not supplied, will default to the
         * value from {@link PhysicsAnimationController#getSpringForce}.
         */
        public PhysicsPropertyAnimator withDampingRatio(float dampingRatio) {
            mDampingRatio = dampingRatio;
            return this;
        }

        /**
         * Set the stiffness to use for this animation. If not supplied, will default to the
         * value from {@link PhysicsAnimationController#getSpringForce}.
         */
        public PhysicsPropertyAnimator withStiffness(float stiffness) {
            mStiffness = stiffness;
            return this;
        }

        /**
         * Set the start velocities to use for TRANSLATION_X and TRANSLATION_Y animations. This
         * overrides any value set via {@link #withStartVelocity(float)} for those properties.
         */
        public PhysicsPropertyAnimator withPositionStartVelocities(float velX, float velY) {
            mPositionStartVelocities.put(DynamicAnimation.TRANSLATION_X, velX);
            mPositionStartVelocities.put(DynamicAnimation.TRANSLATION_Y, velY);
            return this;
        }

        /** Set a delay, in milliseconds, before kicking off the animations. */
        public PhysicsPropertyAnimator withStartDelay(long startDelay) {
            mStartDelay = startDelay;
            return this;
        }

        /**
         * Start the animations, and call the optional end actions once all animations for every
         * animated property on every child (including chained animations) have ended.
         */
        public void start(Runnable... after) {
            if (!isActiveController(mAssociatedController)) {
                Log.w(TAG, "Only the active animation controller is allowed to start animations. "
                        + "Use PhysicsAnimationLayout#setActiveController to set the active "
                        + "animation controller.");
                return;
            }

            final Set<DynamicAnimation.ViewProperty> properties = getAnimatedProperties();

            // If there are end actions, set an end listener on the layout for all the properties
            // we're about to animate.
            if (after != null && after.length > 0) {
                final DynamicAnimation.ViewProperty[] propertiesArray =
                        properties.toArray(new DynamicAnimation.ViewProperty[0]);
                mAssociatedController.setEndActionForMultipleProperties(() -> {
                    for (Runnable callback : after) {
                        callback.run();
                    }
                }, propertiesArray);
            }

            // If we used position-specific end actions, we'll need to listen for TRANSLATION_X
            // TRANSLATION_Y and TRANSLATION_Z animations ending, and call them once both have
            // finished.
            if (mPositionEndActions != null) {
                final SpringAnimation translationXAnim =
                        getSpringAnimationFromView(DynamicAnimation.TRANSLATION_X, mView);
                final SpringAnimation translationYAnim =
                        getSpringAnimationFromView(DynamicAnimation.TRANSLATION_Y, mView);
                final SpringAnimation translationZAnim =
                        getSpringAnimationFromView(DynamicAnimation.TRANSLATION_Z, mView);
                final Runnable waitForXYZ = () -> {
                    if (!translationXAnim.isRunning() && !translationYAnim.isRunning()
                            && !translationZAnim.isRunning()) {
                        if (mPositionEndActions != null) {
                            for (Runnable callback : mPositionEndActions) {
                                callback.run();
                            }
                        }

                        mPositionEndActions = null;
                    }
                };

                mEndActionsForProperty.put(DynamicAnimation.TRANSLATION_X,
                        new Runnable[]{waitForXYZ});
                mEndActionsForProperty.put(DynamicAnimation.TRANSLATION_Y,
                        new Runnable[]{waitForXYZ});
                mEndActionsForProperty.put(DynamicAnimation.TRANSLATION_Z,
                        new Runnable[]{waitForXYZ});
            }

            if (mPathAnimator != null) {
                startPathAnimation();
            }

            // Actually start the animations.
            for (DynamicAnimation.ViewProperty property : properties) {
                // Don't start translation animations if we're using a path animator, the update
                // listeners added to that animator will take care of that.
                boolean isTranslationProperty = property.equals(DynamicAnimation.TRANSLATION_X)
                        || property.equals(DynamicAnimation.TRANSLATION_Y)
                        || property.equals(DynamicAnimation.TRANSLATION_Z);
                if (mPathAnimator != null && isTranslationProperty) {
                    return;
                }

                if (mInitialPropertyValues.containsKey(property)) {
                    property.setValue(mView, mInitialPropertyValues.get(property));
                }

                final SpringForce defaultSpringForce = mController.getSpringForce(property, mView);
                animateValueForChild(
                        property,
                        mView,
                        mAnimatedProperties.get(property),
                        mPositionStartVelocities.getOrDefault(property, mDefaultStartVelocity),
                        mStartDelay,
                        mStiffness >= 0 ? mStiffness : defaultSpringForce.getStiffness(),
                        mDampingRatio >= 0 ? mDampingRatio : defaultSpringForce.getDampingRatio(),
                        mEndActionsForProperty.get(property));
            }

            clearAnimator();
        }

        /** Returns the set of properties that will animate once {@link #start} is called. */
        protected Set<DynamicAnimation.ViewProperty> getAnimatedProperties() {
            final HashSet<DynamicAnimation.ViewProperty> animatedProperties = new HashSet<>(
                    mAnimatedProperties.keySet());

            // If we're using a path animator, it'll kick off translation animations.
            if (mPathAnimator != null) {
                animatedProperties.add(DynamicAnimation.TRANSLATION_X);
                animatedProperties.add(DynamicAnimation.TRANSLATION_Y);
                animatedProperties.add(DynamicAnimation.TRANSLATION_Z);
            }

            return animatedProperties;
        }

        /**
         * Animates the property of the given child view, then runs the callback provided when the
         * animation ends.
         */
        protected void animateValueForChild(
                DynamicAnimation.ViewProperty property,
                View view,
                float value,
                float startVel,
                long startDelay,
                float stiffness,
                float dampingRatio,
                Runnable... afterCallbacks) {
            if (view != null) {
                final SpringAnimation animation =
                        (SpringAnimation) view.getTag(getTagIdForProperty(property));

                // If the animation is null, the view was probably removed from the layout before
                // the animation started.
                if (animation == null) {
                    return;
                }

                if (afterCallbacks != null) {
                    animation.addEndListener(new OneTimeEndListener() {
                        @Override
                        public void onAnimationEnd(DynamicAnimation animation, boolean canceled,
                                float value, float velocity) {
                            super.onAnimationEnd(animation, canceled, value, velocity);
                            for (Runnable runnable : afterCallbacks) {
                                runnable.run();
                            }
                        }
                    });
                }

                final SpringForce animationSpring = animation.getSpring();

                if (animationSpring == null) {
                    return;
                }

                final Runnable configureAndStartAnimation = () -> {
                    animationSpring.setStiffness(stiffness);
                    animationSpring.setDampingRatio(dampingRatio);

                    if (startVel > -Float.MAX_VALUE) {
                        animation.setStartVelocity(startVel);
                    }

                    animationSpring.setFinalPosition(value);
                    animation.start();
                };

                if (startDelay > 0) {
                    postDelayed(configureAndStartAnimation, startDelay);
                } else {
                    configureAndStartAnimation.run();
                }
            }
        }

        /**
         * Updates the final position of a view's animation, without changing any of the animation's
         * other settings. Calling this before an initial call to {@link #animateValueForChild} will
         * work, but result in unknown values for stiffness, etc. and is not recommended.
         */
        private void updateValueForChild(
                DynamicAnimation.ViewProperty property, View view, float position) {
            if (view != null) {
                final SpringAnimation animation =
                        (SpringAnimation) view.getTag(getTagIdForProperty(property));

                if (animation == null) {
                    return;
                }

                final SpringForce animationSpring = animation.getSpring();

                if (animationSpring == null) {
                    return;
                }

                animationSpring.setFinalPosition(position);
                animation.start();
            }
        }

        /**
         * Configures the path animator to respect the settings passed into the animation builder
         * and adds update listeners that update the translation physics animations. Then, starts
         * the path animation.
         */
        protected void startPathAnimation() {
            final SpringForce defaultSpringForceX = mController.getSpringForce(
                    DynamicAnimation.TRANSLATION_X, mView);
            final SpringForce defaultSpringForceY = mController.getSpringForce(
                    DynamicAnimation.TRANSLATION_Y, mView);

            if (mStartDelay > 0) {
                mPathAnimator.setStartDelay(mStartDelay);
            }

            final Runnable updatePhysicsAnims = () -> {
                updateValueForChild(
                        DynamicAnimation.TRANSLATION_X, mView, mCurrentPointOnPath.x);
                updateValueForChild(
                        DynamicAnimation.TRANSLATION_Y, mView, mCurrentPointOnPath.y);
            };

            mPathAnimator.addUpdateListener(pathAnim -> updatePhysicsAnims.run());
            mPathAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    animateValueForChild(
                            DynamicAnimation.TRANSLATION_X,
                            mView,
                            mCurrentPointOnPath.x,
                            mDefaultStartVelocity,
                            0 /* startDelay */,
                            mStiffness >= 0 ? mStiffness : defaultSpringForceX.getStiffness(),
                            mDampingRatio >= 0
                                    ? mDampingRatio
                                    : defaultSpringForceX.getDampingRatio());

                    animateValueForChild(
                            DynamicAnimation.TRANSLATION_Y,
                            mView,
                            mCurrentPointOnPath.y,
                            mDefaultStartVelocity,
                            0 /* startDelay */,
                            mStiffness >= 0 ? mStiffness : defaultSpringForceY.getStiffness(),
                            mDampingRatio >= 0
                                    ? mDampingRatio
                                    : defaultSpringForceY.getDampingRatio());
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    updatePhysicsAnims.run();
                }
            });

            // If there's a target animator saved for the view, make sure it's not running.
            final ObjectAnimator targetAnimator = getTargetAnimatorFromView(mView);
            if (targetAnimator != null) {
                targetAnimator.cancel();
            }

            mView.setTag(R.id.target_animator_tag, mPathAnimator);
            mPathAnimator.start();
        }

        private void clearAnimator() {
            mInitialPropertyValues.clear();
            mAnimatedProperties.clear();
            mPositionStartVelocities.clear();
            mDefaultStartVelocity = -Float.MAX_VALUE;
            mStartDelay = 0;
            mStiffness = -1;
            mDampingRatio = -1;
            mEndActionsForProperty.clear();
            mPathAnimator = null;
            mPositionEndActions = null;
        }

        /**
         * Sets the controller that last retrieved this animator instance, so that we can prevent
         * {@link #start} from actually starting animations if called by a non-active controller.
         */
        private void setAssociatedController(PhysicsAnimationController controller) {
            mAssociatedController = controller;
        }
    }
}
