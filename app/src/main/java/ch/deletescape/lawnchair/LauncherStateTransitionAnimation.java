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

package ch.deletescape.lawnchair;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import java.util.HashMap;

import ch.deletescape.lawnchair.allapps.AllAppsContainerView;
import ch.deletescape.lawnchair.allapps.AllAppsTransitionController;
import ch.deletescape.lawnchair.util.CircleRevealOutlineProvider;
import ch.deletescape.lawnchair.util.Thunk;
import ch.deletescape.lawnchair.widget.WidgetsContainerView;

/**
 * TODO: figure out what kind of tests we can write for this
 * <p>
 * Things to test when changing the following class.
 * - Home from workspace
 * - from center screen
 * - from other screens
 * - Home from all apps
 * - from center screen
 * - from other screens
 * - Back from all apps
 * - from center screen
 * - from other screens
 * - Launch app from workspace and quit
 * - with back
 * - with home
 * - Launch app from all apps and quit
 * - with back
 * - with home
 * - Go to a screen that's not the default, then all
 * apps, and launch and app, and go back
 * - with back
 * -with home
 * - On workspace, long press power and go back
 * - with back
 * - with home
 * - On all apps, long press power and go back
 * - with back
 * - with home
 * - On workspace, power off
 * - On all apps, power off
 * - Launch an app and turn off the screen while in that app
 * - Go back with home key
 * - Go back with back key  TODO: make this not go to workspace
 * - From all apps
 * - From workspace
 * - Enter and exit car mode (becuase it causes an extra configuration changed)
 * - From all apps
 * - From the center workspace
 * - From another workspace
 */
public class LauncherStateTransitionAnimation {

    /**
     * animation used for widget tray
     */
    public static final int CIRCULAR_REVEAL = 0;
    /**
     * animation used for all apps
     */
    public static final int PULLUP = 1;

    private static final float FINAL_REVEAL_ALPHA_FOR_WIDGETS = 0.3f;

    /**
     * Private callbacks made during transition setup.
     */
    private static class PrivateTransitionCallbacks {
        private final float materialRevealViewFinalAlpha;

        PrivateTransitionCallbacks(float revealAlpha) {
            materialRevealViewFinalAlpha = revealAlpha;
        }

        float getMaterialRevealViewStartFinalRadius() {
            return 0;
        }

        AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(View revealView,
                                                                      View buttonView) {
            return null;
        }

        void onTransitionComplete() {
        }
    }

    public static final String TAG = "LSTAnimation";

    // Flags to determine how to set the layers on views before the transition animation
    //public static final int BUILD_LAYER = 0;
    public static final int BUILD_AND_SET_LAYER = 1;
    public static final int SINGLE_FRAME_DELAY = 16;

    @Thunk
    Launcher mLauncher;
    @Thunk
    AnimatorSet mCurrentAnimation;
    AllAppsTransitionController mAllAppsController;

    public LauncherStateTransitionAnimation(Launcher l, AllAppsTransitionController allAppsController) {
        mLauncher = l;
        mAllAppsController = allAppsController;
    }

    /**
     * Starts an animation to the apps view.
     *
     * @param startSearchAfterTransition Immediately starts app search after the transition to
     *                                   All Apps is completed.
     */
    public void startAnimationToAllApps(final boolean animated, final boolean startSearchAfterTransition) {
        final AllAppsContainerView toView = mLauncher.getAppsView();
        final View buttonView = mLauncher.getStartViewForAllAppsRevealAnimation();
        PrivateTransitionCallbacks cb = new PrivateTransitionCallbacks(1f) {
            @Override
            public float getMaterialRevealViewStartFinalRadius() {
                int allAppsButtonSize = mLauncher.getDeviceProfile().allAppsButtonVisualSize;
                return allAppsButtonSize / 2;
            }

            @Override
            public AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(
                    final View revealView, final View allAppsButtonView) {
                return new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        allAppsButtonView.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        allAppsButtonView.setVisibility(View.VISIBLE);
                    }
                };
            }

            @Override
            void onTransitionComplete() {
                if (startSearchAfterTransition) {
                    toView.startAppsSearch();
                }
            }
        };
        // Only animate the search bar if animating from spring loaded mode back to all apps
        startAnimationToOverlay(
                Workspace.State.NORMAL_HIDDEN, buttonView, toView, animated, PULLUP, cb);
    }

    /**
     * Starts an animation to the widgets view.
     */
    public void startAnimationToWidgets(final boolean animated) {
        final WidgetsContainerView toView = mLauncher.getWidgetsView();
        final View buttonView = mLauncher.getWidgetsButton();
        startAnimationToOverlay(
                Workspace.State.OVERVIEW_HIDDEN, buttonView, toView, animated, CIRCULAR_REVEAL, new PrivateTransitionCallbacks(FINAL_REVEAL_ALPHA_FOR_WIDGETS));
    }

    /**
     * Starts an animation to the workspace from the current overlay view.
     */
    public void startAnimationToWorkspace(final Launcher.State fromState,
                                          final Workspace.State fromWorkspaceState, final Workspace.State toWorkspaceState,
                                          final boolean animated, final Runnable onCompleteRunnable) {
        if (toWorkspaceState != Workspace.State.NORMAL &&
                toWorkspaceState != Workspace.State.SPRING_LOADED &&
                toWorkspaceState != Workspace.State.OVERVIEW) {
            Log.e(TAG, "Unexpected call to startAnimationToWorkspace");
        }

        if (fromState == Launcher.State.APPS || fromState == Launcher.State.APPS_SPRING_LOADED
                || mAllAppsController.isTransitioning()) {
            startAnimationToWorkspaceFromAllApps(fromWorkspaceState, toWorkspaceState,
                    animated, PULLUP, onCompleteRunnable);
        } else if (fromState == Launcher.State.WIDGETS ||
                fromState == Launcher.State.WIDGETS_SPRING_LOADED) {
            startAnimationToWorkspaceFromWidgets(fromWorkspaceState, toWorkspaceState,
                    animated, onCompleteRunnable);
        } else {
            startAnimationToNewWorkspaceState(toWorkspaceState,
                    animated, onCompleteRunnable);
        }
    }

    /**
     * Creates and starts a new animation to a particular overlay view.
     */
    @SuppressLint("NewApi")
    private void startAnimationToOverlay(
            final Workspace.State toWorkspaceState,
            final View buttonView, final BaseContainerView toView,
            final boolean animated, int animType, final PrivateTransitionCallbacks pCb) {
        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final Resources res = mLauncher.getResources();
        final int revealDuration = res.getInteger(R.integer.config_overlayRevealTime);
        final int revealDurationSlide = res.getInteger(R.integer.config_overlaySlideRevealTime);

        final int itemsAlphaStagger = res.getInteger(R.integer.config_overlayItemsAlphaStagger);

        final View fromView = mLauncher.getWorkspace();

        final HashMap<View, Integer> layerViews = new HashMap<>();

        // If for some reason our views aren't initialized, don't animate
        boolean initialized = buttonView != null;

        // Cancel the current animation
        cancelAnimation();

        final View contentView = toView.getContentView();
        playCommonTransitionAnimations(toWorkspaceState, fromView, toView,
                animated, initialized, animation, layerViews);
        if (!animated || !initialized) {
            if (toWorkspaceState == Workspace.State.NORMAL_HIDDEN) {
                mAllAppsController.finishPullUp();
            }
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setAlpha(1.0f);
            toView.setVisibility(View.VISIBLE);

            // Show the content view
            contentView.setVisibility(View.VISIBLE);

            dispatchOnLauncherTransitionPrepare(fromView, false);
            dispatchOnLauncherTransitionStart(fromView);
            dispatchOnLauncherTransitionEnd(fromView, false);
            dispatchOnLauncherTransitionPrepare(toView, false);
            dispatchOnLauncherTransitionStart(toView);
            dispatchOnLauncherTransitionEnd(toView, false);
            pCb.onTransitionComplete();
            return;
        }
        if (animType == CIRCULAR_REVEAL) {
            // Setup the reveal view animation
            final View revealView = toView.getRevealView();

            int width = revealView.getMeasuredWidth();
            int height = revealView.getMeasuredHeight();
            float revealRadius = (float) Math.hypot(width / 2, height / 2);
            revealView.setVisibility(View.VISIBLE);
            revealView.setAlpha(0f);
            revealView.setTranslationY(0f);
            revealView.setTranslationX(0f);

            // Calculate the final animation values
            final float revealViewToAlpha;
            final float revealViewToXDrift;
            final float revealViewToYDrift;
            int[] buttonViewToPanelDelta = Utilities.getCenterDeltaInScreenSpace(
                    revealView, buttonView, null);
            revealViewToAlpha = pCb.materialRevealViewFinalAlpha;
            revealViewToYDrift = buttonViewToPanelDelta[1];
            revealViewToXDrift = buttonViewToPanelDelta[0];

            // Create the animators
            PropertyValuesHolder panelAlpha =
                    PropertyValuesHolder.ofFloat(View.ALPHA, revealViewToAlpha, 1f);
            PropertyValuesHolder panelDriftY =
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, revealViewToYDrift, 0);
            PropertyValuesHolder panelDriftX =
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, revealViewToXDrift, 0);
            ObjectAnimator panelAlphaAndDrift = ObjectAnimator.ofPropertyValuesHolder(revealView,
                    panelAlpha, panelDriftY, panelDriftX);
            panelAlphaAndDrift.setDuration(revealDuration);
            panelAlphaAndDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));

            // Play the animation
            layerViews.put(revealView, BUILD_AND_SET_LAYER);
            animation.play(panelAlphaAndDrift);

            // Setup the animation for the content view
            contentView.setVisibility(View.VISIBLE);
            contentView.setAlpha(0f);
            contentView.setTranslationY(revealViewToYDrift);
            layerViews.put(contentView, BUILD_AND_SET_LAYER);

            // Create the individual animators
            ObjectAnimator pageDrift = ObjectAnimator.ofFloat(contentView, "translationY",
                    revealViewToYDrift, 0);
            pageDrift.setDuration(revealDuration);
            pageDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));
            pageDrift.setStartDelay(itemsAlphaStagger);
            animation.play(pageDrift);

            ObjectAnimator itemsAlpha = ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f);
            itemsAlpha.setDuration(revealDuration);
            itemsAlpha.setInterpolator(new AccelerateInterpolator(1.5f));
            itemsAlpha.setStartDelay(itemsAlphaStagger);
            animation.play(itemsAlpha);

            float startRadius = pCb.getMaterialRevealViewStartFinalRadius();
            AnimatorListenerAdapter listener = pCb.getMaterialRevealViewAnimatorListener(
                    revealView, buttonView);
            Animator reveal = new CircleRevealOutlineProvider(width / 2, height / 2,
                    startRadius, revealRadius).createRevealAnimator(revealView);
            reveal.setDuration(revealDuration);
            reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));
            if (listener != null) {
                reveal.addListener(listener);
            }
            animation.play(reveal);

            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromView, false);
                    dispatchOnLauncherTransitionEnd(toView, false);

                    // Hide the reveal view
                    revealView.setVisibility(View.INVISIBLE);

                    // Disable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    }

                    // This can hold unnecessary references to views.
                    cleanupAnimation();
                    pCb.onTransitionComplete();
                }

            });

            // Dispatch the prepare transition signal
            dispatchOnLauncherTransitionPrepare(fromView, false);
            dispatchOnLauncherTransitionPrepare(toView, false);

            final AnimatorSet stateAnimation = animation;
            final Runnable startAnimRunnable = new Runnable() {
                @Override
                public void run() {
                    // Check that mCurrentAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mCurrentAnimation != stateAnimation)
                        return;
                    dispatchOnLauncherTransitionStart(fromView);
                    dispatchOnLauncherTransitionStart(toView);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (v.isAttachedToWindow() && v.getVisibility() == View.VISIBLE) {
                            v.buildLayer();
                        }
                    }

                    // Focus the new view
                    toView.requestFocus();

                    stateAnimation.start();
                }
            };
            toView.bringToFront();
            toView.setVisibility(View.VISIBLE);
            toView.post(startAnimRunnable);
            mCurrentAnimation = animation;
        } else if (animType == PULLUP) {
            // We are animating the content view alpha, so ensure we have a layer for it
            layerViews.put(contentView, BUILD_AND_SET_LAYER);

            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromView, false);
                    dispatchOnLauncherTransitionEnd(toView, false);

                    // Disable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    }

                    cleanupAnimation();
                    pCb.onTransitionComplete();
                }
            });
            boolean shouldPost = mAllAppsController.animateToAllApps(animation, revealDurationSlide);

            dispatchOnLauncherTransitionPrepare(fromView, false);
            dispatchOnLauncherTransitionPrepare(toView, false);

            final AnimatorSet stateAnimation = animation;
            final Runnable startAnimRunnable = new Runnable() {
                @Override
                public void run() {
                    // Check that mCurrentAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mCurrentAnimation != stateAnimation)
                        return;

                    dispatchOnLauncherTransitionStart(fromView);
                    dispatchOnLauncherTransitionStart(toView);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (v.isAttachedToWindow() && v.getVisibility() == View.VISIBLE) {
                            v.buildLayer();
                        }
                    }

                    toView.requestFocus();
                    stateAnimation.start();
                }
            };
            mCurrentAnimation = animation;
            if (shouldPost) {
                toView.post(startAnimRunnable);
            } else {
                startAnimRunnable.run();
            }
        }
    }

    /**
     * Plays animations used by various transitions.
     */
    private void playCommonTransitionAnimations(
            Workspace.State toWorkspaceState, View fromView, View toView,
            boolean animated, boolean initialized, AnimatorSet animation,
            HashMap<View, Integer> layerViews) {
        // Create the workspace animation.
        // NOTE: this call apparently also sets the state for the workspace if !animated
        Animator workspaceAnim = mLauncher.startWorkspaceStateChangeAnimation(toWorkspaceState,
                animated, layerViews);

        if (animated && initialized) {
            // Play the workspace animation
            if (workspaceAnim != null) {
                animation.play(workspaceAnim);
            }
            // Dispatch onLauncherTransitionStep() as the animation interpolates.
            animation.play(dispatchOnLauncherTransitionStepAnim(fromView, toView));
        }
    }

    /**
     * Returns an Animator that calls {@link #dispatchOnLauncherTransitionStep(View, float)} on
     * {@param fromView} and {@param toView} as the animation interpolates.
     * <p>
     * This is a bit hacky: we create a dummy ValueAnimator just for the AnimatorUpdateListener.
     */
    private Animator dispatchOnLauncherTransitionStepAnim(final View fromView, final View toView) {
        ValueAnimator updateAnimator = ValueAnimator.ofFloat(0, 1);
        updateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                dispatchOnLauncherTransitionStep(fromView, animation.getAnimatedFraction());
                dispatchOnLauncherTransitionStep(toView, animation.getAnimatedFraction());
            }
        });
        return updateAnimator;
    }

    /**
     * Starts an animation to the workspace from the apps view.
     */
    private void startAnimationToWorkspaceFromAllApps(final Workspace.State fromWorkspaceState,
                                                      final Workspace.State toWorkspaceState, final boolean animated, int type,
                                                      final Runnable onCompleteRunnable) {
        AllAppsContainerView appsView = mLauncher.getAppsView();
        // No alpha anim from all apps
        PrivateTransitionCallbacks cb = new PrivateTransitionCallbacks(1f) {
            @Override
            float getMaterialRevealViewStartFinalRadius() {
                int allAppsButtonSize = mLauncher.getDeviceProfile().allAppsButtonVisualSize;
                return allAppsButtonSize / 2;
            }

            @Override
            public AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(
                    final View revealView, final View allAppsButtonView) {
                return new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        // We set the alpha instead of visibility to ensure that the focus does not
                        // get taken from the all apps view
                        allAppsButtonView.setVisibility(View.VISIBLE);
                        allAppsButtonView.setAlpha(0f);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Hide the reveal view
                        revealView.setVisibility(View.INVISIBLE);

                        // Show the all apps button, and focus it
                        allAppsButtonView.setAlpha(1f);
                    }
                };
            }
        };
        // Only animate the search bar if animating to spring loaded mode from all apps
        startAnimationToWorkspaceFromOverlay(fromWorkspaceState, toWorkspaceState,
                mLauncher.getStartViewForAllAppsRevealAnimation(), appsView,
                animated, type, onCompleteRunnable, cb);
    }

    /**
     * Starts an animation to the workspace from the widgets view.
     */
    private void startAnimationToWorkspaceFromWidgets(final Workspace.State fromWorkspaceState,
                                                      final Workspace.State toWorkspaceState, final boolean animated,
                                                      final Runnable onCompleteRunnable) {
        final WidgetsContainerView widgetsView = mLauncher.getWidgetsView();
        PrivateTransitionCallbacks cb =
                new PrivateTransitionCallbacks(FINAL_REVEAL_ALPHA_FOR_WIDGETS) {
                    @Override
                    public AnimatorListenerAdapter getMaterialRevealViewAnimatorListener(
                            final View revealView, final View widgetsButtonView) {
                        return new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                // Hide the reveal view
                                revealView.setVisibility(View.INVISIBLE);
                            }
                        };
                    }
                };
        startAnimationToWorkspaceFromOverlay(
                fromWorkspaceState, toWorkspaceState,
                mLauncher.getWidgetsButton(), widgetsView,
                animated, CIRCULAR_REVEAL, onCompleteRunnable, cb);
    }

    /**
     * Starts an animation to the workspace from another workspace state, e.g. normal to overview.
     */
    private void startAnimationToNewWorkspaceState(final Workspace.State toWorkspaceState, final boolean animated,
                                                   final Runnable onCompleteRunnable) {
        final View fromWorkspace = mLauncher.getWorkspace();
        final HashMap<View, Integer> layerViews = new HashMap<>();
        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();

        // Cancel the current animation
        cancelAnimation();

        boolean multiplePagesVisible = toWorkspaceState.hasMultipleVisiblePages;

        playCommonTransitionAnimations(toWorkspaceState, fromWorkspace, null,
                animated, animated, animation, layerViews);

        if (animated) {
            dispatchOnLauncherTransitionPrepare(fromWorkspace, multiplePagesVisible);

            final AnimatorSet stateAnimation = animation;
            final Runnable startAnimRunnable = new Runnable() {
                @Override
                public void run() {
                    // Check that mCurrentAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mCurrentAnimation != stateAnimation)
                        return;

                    dispatchOnLauncherTransitionStart(fromWorkspace);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (v.isAttachedToWindow() && v.getVisibility() == View.VISIBLE) {
                            v.buildLayer();
                        }
                    }
                    stateAnimation.start();
                }
            };
            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromWorkspace, true);

                    // Run any queued runnables
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }

                    // Disable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    }

                    // This can hold unnecessary references to views.
                    cleanupAnimation();
                }
            });
            fromWorkspace.post(startAnimRunnable);
            mCurrentAnimation = animation;
        } else /* if (!animated) */ {
            dispatchOnLauncherTransitionPrepare(fromWorkspace, multiplePagesVisible);
            dispatchOnLauncherTransitionStart(fromWorkspace);
            dispatchOnLauncherTransitionEnd(fromWorkspace, true);

            // Run any queued runnables
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }

            mCurrentAnimation = null;
        }
    }

    /**
     * Creates and starts a new animation to the workspace.
     */
    private void startAnimationToWorkspaceFromOverlay(
            final Workspace.State fromWorkspaceState, final Workspace.State toWorkspaceState,
            final View buttonView, final BaseContainerView fromView,
            final boolean animated, int animType, final Runnable onCompleteRunnable,
            final PrivateTransitionCallbacks pCb) {
        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final Resources res = mLauncher.getResources();
        final int revealDuration = res.getInteger(R.integer.config_overlayRevealTime);
        final int revealDurationSlide = res.getInteger(R.integer.config_overlaySlideRevealTime);
        final int itemsAlphaStagger =
                res.getInteger(R.integer.config_overlayItemsAlphaStagger);

        final View toView = mLauncher.getWorkspace();
        final View revealView = fromView.getRevealView();
        final View contentView = fromView.getContentView();

        final HashMap<View, Integer> layerViews = new HashMap<>();

        // If for some reason our views aren't initialized, don't animate
        boolean initialized = buttonView != null;

        // Cancel the current animation
        cancelAnimation();

        boolean multiplePagesVisible = toWorkspaceState.hasMultipleVisiblePages;

        playCommonTransitionAnimations(toWorkspaceState, fromView, toView,
                animated, initialized, animation, layerViews);
        if (!animated || !initialized) {
            if (fromWorkspaceState == Workspace.State.NORMAL_HIDDEN) {
                mAllAppsController.finishPullDown();
            }
            fromView.setVisibility(View.GONE);
            dispatchOnLauncherTransitionPrepare(fromView, multiplePagesVisible);
            dispatchOnLauncherTransitionStart(fromView);
            dispatchOnLauncherTransitionEnd(fromView, true);
            dispatchOnLauncherTransitionPrepare(toView, multiplePagesVisible);
            dispatchOnLauncherTransitionStart(toView);
            dispatchOnLauncherTransitionEnd(toView, true);
            pCb.onTransitionComplete();

            // Run any queued runnables
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            return;
        }
        if (animType == CIRCULAR_REVEAL) {
            // hideAppsCustomizeHelper is called in some cases when it is already hidden
            // don't perform all these no-op animations. In particularly, this was causing
            // the all-apps button to pop in and out.
            if (fromView.getVisibility() == View.VISIBLE) {
                int width = revealView.getMeasuredWidth();
                int height = revealView.getMeasuredHeight();
                float revealRadius = (float) Math.hypot(width / 2, height / 2);
                revealView.setVisibility(View.VISIBLE);
                revealView.setAlpha(1f);
                revealView.setTranslationY(0);
                layerViews.put(revealView, BUILD_AND_SET_LAYER);

                // Calculate the final animation values
                final float revealViewToXDrift;
                final float revealViewToYDrift;
                int[] buttonViewToPanelDelta = Utilities.getCenterDeltaInScreenSpace(revealView,
                        buttonView, null);
                revealViewToYDrift = buttonViewToPanelDelta[1];
                revealViewToXDrift = buttonViewToPanelDelta[0];

                // The vertical motion of the apps panel should be delayed by one frame
                // from the conceal animation in order to give the right feel. We correspondingly
                // shorten the duration so that the slide and conceal end at the same time.
                TimeInterpolator decelerateInterpolator = new LogDecelerateInterpolator(100, 0);
                ObjectAnimator panelDriftY = ObjectAnimator.ofFloat(revealView, "translationY",
                        0, revealViewToYDrift);
                panelDriftY.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftY.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftY.setInterpolator(decelerateInterpolator);
                animation.play(panelDriftY);

                ObjectAnimator panelDriftX = ObjectAnimator.ofFloat(revealView, "translationX",
                        0, revealViewToXDrift);
                panelDriftX.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftX.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftX.setInterpolator(decelerateInterpolator);
                animation.play(panelDriftX);

                // Setup animation for the reveal panel alpha
                final float revealViewToAlpha = pCb.materialRevealViewFinalAlpha;
                if (revealViewToAlpha != 1f) {
                    ObjectAnimator panelAlpha = ObjectAnimator.ofFloat(revealView, "alpha",
                            1f, revealViewToAlpha);
                    panelAlpha.setDuration(revealDuration);
                    panelAlpha.setStartDelay(0);
                    panelAlpha.setInterpolator(decelerateInterpolator);
                    animation.play(panelAlpha);
                }

                // Setup the animation for the content view
                layerViews.put(contentView, BUILD_AND_SET_LAYER);

                // Create the individual animators
                ObjectAnimator pageDrift = ObjectAnimator.ofFloat(contentView, "translationY",
                        0, revealViewToYDrift);
                contentView.setTranslationY(0);
                pageDrift.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                pageDrift.setInterpolator(decelerateInterpolator);
                pageDrift.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                animation.play(pageDrift);

                contentView.setAlpha(1f);
                ObjectAnimator itemsAlpha = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f);
                itemsAlpha.setDuration(100);
                itemsAlpha.setInterpolator(decelerateInterpolator);
                animation.play(itemsAlpha);

                // Invalidate the scrim throughout the animation to ensure the highlight
                // cutout is correct throughout.
                ValueAnimator invalidateScrim = ValueAnimator.ofFloat(0f, 1f);
                invalidateScrim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        mLauncher.getDragLayer().invalidateScrim();
                    }
                });
                animation.play(invalidateScrim);

                // Animate the all apps button
                float finalRadius = pCb.getMaterialRevealViewStartFinalRadius();
                AnimatorListenerAdapter listener =
                        pCb.getMaterialRevealViewAnimatorListener(revealView, buttonView);
                Animator reveal = new CircleRevealOutlineProvider(width / 2, height / 2,
                        revealRadius, finalRadius).createRevealAnimator(revealView);
                reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));
                reveal.setDuration(revealDuration);
                reveal.setStartDelay(itemsAlphaStagger);
                if (listener != null) {
                    reveal.addListener(listener);
                }
                animation.play(reveal);
            }

            dispatchOnLauncherTransitionPrepare(fromView, multiplePagesVisible);
            dispatchOnLauncherTransitionPrepare(toView, multiplePagesVisible);

            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fromView.setVisibility(View.GONE);
                    dispatchOnLauncherTransitionEnd(fromView, true);
                    dispatchOnLauncherTransitionEnd(toView, true);

                    // Run any queued runnables
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }

                    // Disable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    }

                    // Reset page transforms
                    if (contentView != null) {
                        contentView.setTranslationX(0);
                        contentView.setTranslationY(0);
                        contentView.setAlpha(1);
                    }

                    // This can hold unnecessary references to views.
                    cleanupAnimation();
                    pCb.onTransitionComplete();
                }
            });

            final AnimatorSet stateAnimation = animation;
            final Runnable startAnimRunnable = new Runnable() {
                @Override
                public void run() {
                    // Check that mCurrentAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mCurrentAnimation != stateAnimation)
                        return;

                    dispatchOnLauncherTransitionStart(fromView);
                    dispatchOnLauncherTransitionStart(toView);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (v.isAttachedToWindow() && v.getVisibility() == View.VISIBLE) {
                            v.buildLayer();
                        }
                    }
                    stateAnimation.start();
                }
            };
            mCurrentAnimation = animation;
            fromView.post(startAnimRunnable);
        } else if (animType == PULLUP) {
            // We are animating the content view alpha, so ensure we have a layer for it
            layerViews.put(contentView, BUILD_AND_SET_LAYER);

            animation.addListener(new AnimatorListenerAdapter() {
                boolean canceled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (canceled) return;
                    dispatchOnLauncherTransitionEnd(fromView, true);
                    dispatchOnLauncherTransitionEnd(toView, true);
                    // Run any queued runnables
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }

                    // Disable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    }

                    cleanupAnimation();
                    pCb.onTransitionComplete();
                }

            });
            boolean shouldPost = mAllAppsController.animateToWorkspace(animation, revealDurationSlide);

            // Dispatch the prepare transition signal
            dispatchOnLauncherTransitionPrepare(fromView, multiplePagesVisible);
            dispatchOnLauncherTransitionPrepare(toView, multiplePagesVisible);

            final AnimatorSet stateAnimation = animation;
            final Runnable startAnimRunnable = new Runnable() {
                @Override
                public void run() {
                    // Check that mCurrentAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mCurrentAnimation != stateAnimation)
                        return;

                    dispatchOnLauncherTransitionStart(fromView);
                    dispatchOnLauncherTransitionStart(toView);

                    // Enable all necessary layers
                    for (View v : layerViews.keySet()) {
                        if (layerViews.get(v) == BUILD_AND_SET_LAYER) {
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                        if (v.isAttachedToWindow() && v.getVisibility() == View.VISIBLE) {
                            v.buildLayer();
                        }
                    }

                    // Focus the new view
                    toView.requestFocus();
                    stateAnimation.start();
                }
            };
            mCurrentAnimation = animation;
            if (shouldPost) {
                fromView.post(startAnimRunnable);
            } else {
                startAnimRunnable.run();
            }
        }
    }

    /**
     * Dispatches the prepare-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionPrepare(View v,
                                             boolean multiplePagesVisible) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionPrepare(
                    multiplePagesVisible);
        }
    }

    /**
     * Dispatches the start-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionStart(View v) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStart(
            );
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 0f);
    }

    /**
     * Dispatches the step-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionStep(View v, float t) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStep(t);
        }
    }

    /**
     * Dispatches the end-transition event to suitable views.
     */
    void dispatchOnLauncherTransitionEnd(View v, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionEnd(
                    toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 1f);
    }

    /**
     * Cancels the current animation.
     */
    private void cancelAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setDuration(0);
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }
    }

    @Thunk
    void cleanupAnimation() {
        mCurrentAnimation = null;
    }
}
