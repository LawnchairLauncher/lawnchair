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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import com.android.launcher3.util.Thunk;

import java.util.HashMap;

/**
 * A convenience class to update a view's visibility state after an alpha animation.
 */
class AlphaUpdateListener extends AnimatorListenerAdapter implements ValueAnimator.AnimatorUpdateListener {
    private static final float ALPHA_CUTOFF_THRESHOLD = 0.01f;

    private View mView;
    private boolean mAccessibilityEnabled;

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
    public void onAnimationEnd(Animator arg0) {
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

    public static final String TAG = "WorkspaceStateTransitionAnimation";

    public static final int SCROLL_TO_CURRENT_PAGE = -1;
    @Thunk static final int BACKGROUND_FADE_OUT_DURATION = 350;

    final @Thunk Launcher mLauncher;
    final @Thunk Workspace mWorkspace;

    @Thunk AnimatorSet mStateAnimator;
    @Thunk float[] mOldBackgroundAlphas;
    @Thunk float[] mOldAlphas;
    @Thunk float[] mNewBackgroundAlphas;
    @Thunk float[] mNewAlphas;
    @Thunk int mLastChildCount = -1;

    @Thunk float mCurrentScale;
    @Thunk float mNewScale;

    @Thunk final ZoomInInterpolator mZoomInInterpolator = new ZoomInInterpolator();

    // These properties refer to the background protection gradient used for AllApps and Customize
    @Thunk ValueAnimator mBackgroundFadeInAnimation;
    @Thunk ValueAnimator mBackgroundFadeOutAnimation;

    @Thunk float mSpringLoadedShrinkFactor;
    @Thunk float mOverviewModeShrinkFactor;
    @Thunk float mWorkspaceScrimAlpha;
    @Thunk int mAllAppsTransitionTime;
    @Thunk int mOverviewTransitionTime;
    @Thunk int mOverlayTransitionTime;
    @Thunk boolean mWorkspaceFadeInAdjacentScreens;

    public WorkspaceStateTransitionAnimation(Launcher launcher, Workspace workspace) {
        mLauncher = launcher;
        mWorkspace = workspace;

        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        Resources res = launcher.getResources();
        mAllAppsTransitionTime = res.getInteger(R.integer.config_workspaceUnshrinkTime);
        mOverviewTransitionTime = res.getInteger(R.integer.config_overviewTransitionTime);
        mOverlayTransitionTime = res.getInteger(R.integer.config_appsCustomizeWorkspaceShrinkTime);
        mSpringLoadedShrinkFactor =
                res.getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100f;
        mWorkspaceScrimAlpha = res.getInteger(R.integer.config_workspaceScrimAlpha) / 100f;
        mOverviewModeShrinkFactor = grid.getOverviewModeScale();
        mWorkspaceFadeInAdjacentScreens = grid.shouldFadeAdjacentWorkspaceScreens();
    }

    public AnimatorSet getAnimationToState(Workspace.State fromState, Workspace.State toState,
                                           int toPage, boolean animated,
                                           HashMap<View, Integer> layerViews) {
        getAnimation(fromState, toState, toPage, animated, layerViews);
        return mStateAnimator;
    }

    public float getFinalScale() {
        return mNewScale;
    }

    /**
     * Starts a transition animation for the workspace.
     */
    private void getAnimation(final Workspace.State fromState, final Workspace.State toState,
                              int toPage, final boolean animated,
                              final HashMap<View, Integer> layerViews) {
        AccessibilityManager am = (AccessibilityManager)
                mLauncher.getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean accessibilityEnabled = am.isEnabled();

        // Reinitialize animation arrays for the current workspace state
        reinitializeAnimationArrays();

        // Cancel existing workspace animations and create a new animator set if requested
        cancelAnimation();
        if (animated) {
            mStateAnimator = LauncherAnimUtils.createAnimatorSet();
        }

        // Update the workspace state
        final boolean oldStateIsNormal = (fromState == Workspace.State.NORMAL);
        final boolean oldStateIsSpringLoaded = (fromState == Workspace.State.SPRING_LOADED);
        final boolean oldStateIsNormalHidden = (fromState == Workspace.State.NORMAL_HIDDEN);
        final boolean oldStateIsOverviewHidden = (fromState == Workspace.State.OVERVIEW_HIDDEN);
        final boolean oldStateIsOverview = (fromState == Workspace.State.OVERVIEW);

        final boolean stateIsNormal = (toState == Workspace.State.NORMAL);
        final boolean stateIsSpringLoaded = (toState == Workspace.State.SPRING_LOADED);
        final boolean stateIsNormalHidden = (toState == Workspace.State.NORMAL_HIDDEN);
        final boolean stateIsOverviewHidden = (toState == Workspace.State.OVERVIEW_HIDDEN);
        final boolean stateIsOverview = (toState == Workspace.State.OVERVIEW);

        final boolean workspaceToAllApps = (oldStateIsNormal && stateIsNormalHidden);
        final boolean overviewToAllApps = (oldStateIsOverview && stateIsOverviewHidden);
        final boolean allAppsToWorkspace = (stateIsNormalHidden && stateIsNormal);
        final boolean workspaceToOverview = (oldStateIsNormal && stateIsOverview);
        final boolean overviewToWorkspace = (oldStateIsOverview && stateIsNormal);

        float finalBackgroundAlpha = (stateIsSpringLoaded || stateIsOverview) ? 1.0f : 0f;
        float finalHotseatAndPageIndicatorAlpha = (stateIsNormal || stateIsSpringLoaded) ? 1f : 0f;
        float finalOverviewPanelAlpha = stateIsOverview ? 1f : 0f;
        // We keep the search bar visible on the workspace and in AllApps now
        boolean showSearchBar = stateIsNormal ||
                (mLauncher.isAllAppsSearchOverridden() && stateIsNormalHidden);
        float finalSearchBarAlpha = showSearchBar ? 1f : 0f;
        float finalWorkspaceTranslationY = stateIsOverview || stateIsOverviewHidden ?
                mWorkspace.getOverviewModeTranslationY() : 0;

        final int childCount = mWorkspace.getChildCount();
        final int customPageCount = mWorkspace.numCustomPages();

        mNewScale = 1.0f;

        if (oldStateIsOverview) {
            mWorkspace.disableFreeScroll();
        } else if (stateIsOverview) {
            mWorkspace.enableFreeScroll();
        }

        if (!stateIsNormal) {
            if (stateIsSpringLoaded) {
                mNewScale = mSpringLoadedShrinkFactor;
            } else if (stateIsOverview || stateIsOverviewHidden) {
                mNewScale = mOverviewModeShrinkFactor;
            }
        }

        final int duration;
        if (workspaceToAllApps || overviewToAllApps) {
            duration = mAllAppsTransitionTime;
        } else if (workspaceToOverview || overviewToWorkspace) {
            duration = mOverviewTransitionTime;
        } else {
            duration = mOverlayTransitionTime;
        }

        if (toPage == SCROLL_TO_CURRENT_PAGE) {
            toPage = mWorkspace.getPageNearestToCenterOfScreen();
        }
        mWorkspace.snapToPage(toPage, duration, mZoomInInterpolator);

        for (int i = 0; i < childCount; i++) {
            final CellLayout cl = (CellLayout) mWorkspace.getChildAt(i);
            boolean isCurrentPage = (i == toPage);
            float initialAlpha = cl.getShortcutsAndWidgets().getAlpha();
            float finalAlpha;
            if (stateIsNormalHidden || stateIsOverviewHidden) {
                finalAlpha = 0f;
            } else if (stateIsNormal && mWorkspaceFadeInAdjacentScreens) {
                finalAlpha = (i == toPage || i < customPageCount) ? 1f : 0f;
            } else {
                finalAlpha = 1f;
            }

            // If we are animating to/from the small state, then hide the side pages and fade the
            // current page in
            if (!mWorkspace.isSwitchingState()) {
                if (workspaceToAllApps || allAppsToWorkspace) {
                    if (allAppsToWorkspace && isCurrentPage) {
                        initialAlpha = 0f;
                    } else if (!isCurrentPage) {
                        initialAlpha = finalAlpha = 0f;
                    }
                    cl.setShortcutAndWidgetAlpha(initialAlpha);
                }
            }

            mOldAlphas[i] = initialAlpha;
            mNewAlphas[i] = finalAlpha;
            if (animated) {
                mOldBackgroundAlphas[i] = cl.getBackgroundAlpha();
                mNewBackgroundAlphas[i] = finalBackgroundAlpha;
            } else {
                cl.setBackgroundAlpha(finalBackgroundAlpha);
                cl.setShortcutAndWidgetAlpha(finalAlpha);
            }
        }

        final View searchBar = mLauncher.getOrCreateQsbBar();
        final View overviewPanel = mLauncher.getOverviewPanel();
        final View hotseat = mLauncher.getHotseat();
        final View pageIndicator = mWorkspace.getPageIndicator();
        if (animated) {
            LauncherViewPropertyAnimator scale = new LauncherViewPropertyAnimator(mWorkspace);
            scale.scaleX(mNewScale)
                    .scaleY(mNewScale)
                    .translationY(finalWorkspaceTranslationY)
                    .setDuration(duration)
                    .setInterpolator(mZoomInInterpolator);
            mStateAnimator.play(scale);
            for (int index = 0; index < childCount; index++) {
                final int i = index;
                final CellLayout cl = (CellLayout) mWorkspace.getChildAt(i);
                float currentAlpha = cl.getShortcutsAndWidgets().getAlpha();
                if (mOldAlphas[i] == 0 && mNewAlphas[i] == 0) {
                    cl.setBackgroundAlpha(mNewBackgroundAlphas[i]);
                    cl.setShortcutAndWidgetAlpha(mNewAlphas[i]);
                } else {
                    if (layerViews != null) {
                        layerViews.put(cl, LauncherStateTransitionAnimation.BUILD_LAYER);
                    }
                    if (mOldAlphas[i] != mNewAlphas[i] || currentAlpha != mNewAlphas[i]) {
                        LauncherViewPropertyAnimator alphaAnim =
                                new LauncherViewPropertyAnimator(cl.getShortcutsAndWidgets());
                        alphaAnim.alpha(mNewAlphas[i])
                                .setDuration(duration)
                                .setInterpolator(mZoomInInterpolator);
                        mStateAnimator.play(alphaAnim);
                    }
                    if (mOldBackgroundAlphas[i] != 0 ||
                            mNewBackgroundAlphas[i] != 0) {
                        ValueAnimator bgAnim =
                                LauncherAnimUtils.ofFloat(cl, 0f, 1f);
                        bgAnim.setInterpolator(mZoomInInterpolator);
                        bgAnim.setDuration(duration);
                        bgAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                            public void onAnimationUpdate(float a, float b) {
                                cl.setBackgroundAlpha(
                                        a * mOldBackgroundAlphas[i] +
                                                b * mNewBackgroundAlphas[i]);
                            }
                        });
                        mStateAnimator.play(bgAnim);
                    }
                }
            }
            Animator pageIndicatorAlpha = null;
            if (pageIndicator != null) {
                pageIndicatorAlpha = new LauncherViewPropertyAnimator(pageIndicator)
                        .alpha(finalHotseatAndPageIndicatorAlpha).withLayer();
                pageIndicatorAlpha.addListener(new AlphaUpdateListener(pageIndicator,
                        accessibilityEnabled));
            } else {
                // create a dummy animation so we don't need to do null checks later
                pageIndicatorAlpha = ValueAnimator.ofFloat(0, 0);
            }

            LauncherViewPropertyAnimator hotseatAlpha = new LauncherViewPropertyAnimator(hotseat)
                    .alpha(finalHotseatAndPageIndicatorAlpha);
            hotseatAlpha.addListener(new AlphaUpdateListener(hotseat, accessibilityEnabled));

            LauncherViewPropertyAnimator overviewPanelAlpha =
                    new LauncherViewPropertyAnimator(overviewPanel).alpha(finalOverviewPanelAlpha);
            overviewPanelAlpha.addListener(new AlphaUpdateListener(overviewPanel,
                    accessibilityEnabled));

            // For animation optimations, we may need to provide the Launcher transition
            // with a set of views on which to force build layers in certain scenarios.
            hotseat.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            overviewPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (layerViews != null) {
                // If layerViews is not null, we add these views, and indicate that
                // the caller can manage layer state.
                layerViews.put(hotseat, LauncherStateTransitionAnimation.BUILD_AND_SET_LAYER);
                layerViews.put(overviewPanel, LauncherStateTransitionAnimation.BUILD_AND_SET_LAYER);
            } else {
                // Otherwise let the animator handle layer management.
                hotseatAlpha.withLayer();
                overviewPanelAlpha.withLayer();
            }

            if (workspaceToOverview) {
                pageIndicatorAlpha.setInterpolator(new DecelerateInterpolator(2));
                hotseatAlpha.setInterpolator(new DecelerateInterpolator(2));
                overviewPanelAlpha.setInterpolator(null);
            } else if (overviewToWorkspace) {
                pageIndicatorAlpha.setInterpolator(null);
                hotseatAlpha.setInterpolator(null);
                overviewPanelAlpha.setInterpolator(new DecelerateInterpolator(2));
            }

            overviewPanelAlpha.setDuration(duration);
            pageIndicatorAlpha.setDuration(duration);
            hotseatAlpha.setDuration(duration);

            // TODO: This should really be coordinated with the SearchDropTargetBar, otherwise the
            //       bar has no idea that it is hidden, and this has no idea what state the bar is
            //       actually in.
            if (searchBar != null) {
                LauncherViewPropertyAnimator searchBarAlpha = new LauncherViewPropertyAnimator(searchBar)
                    .alpha(finalSearchBarAlpha);
                searchBarAlpha.addListener(new AlphaUpdateListener(searchBar, accessibilityEnabled));
                searchBar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                if (layerViews != null) {
                    // If layerViews is not null, we add these views, and indicate that
                    // the caller can manage layer state.
                    layerViews.put(searchBar, LauncherStateTransitionAnimation.BUILD_AND_SET_LAYER);
                } else {
                    // Otherwise let the animator handle layer management.
                    searchBarAlpha.withLayer();
                }
                searchBarAlpha.setDuration(duration);
                mStateAnimator.play(searchBarAlpha);
            }

            mStateAnimator.play(overviewPanelAlpha);
            mStateAnimator.play(hotseatAlpha);
            mStateAnimator.play(pageIndicatorAlpha);
            mStateAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mStateAnimator = null;
                }
            });
        } else {
            overviewPanel.setAlpha(finalOverviewPanelAlpha);
            AlphaUpdateListener.updateVisibility(overviewPanel, accessibilityEnabled);
            hotseat.setAlpha(finalHotseatAndPageIndicatorAlpha);
            AlphaUpdateListener.updateVisibility(hotseat, accessibilityEnabled);
            if (pageIndicator != null) {
                pageIndicator.setAlpha(finalHotseatAndPageIndicatorAlpha);
                AlphaUpdateListener.updateVisibility(pageIndicator, accessibilityEnabled);
            }
            if (searchBar != null) {
                searchBar.setAlpha(finalSearchBarAlpha);
                AlphaUpdateListener.updateVisibility(searchBar, accessibilityEnabled);
            }
            mWorkspace.updateCustomContentVisibility();
            mWorkspace.setScaleX(mNewScale);
            mWorkspace.setScaleY(mNewScale);
            mWorkspace.setTranslationY(finalWorkspaceTranslationY);
        }

        if (stateIsNormal) {
            animateBackgroundGradient(0f, animated);
        } else {
            animateBackgroundGradient(mWorkspaceScrimAlpha, animated);
        }
    }

    /**
     * Reinitializes the arrays that we need for the animations on each page.
     */
    private void reinitializeAnimationArrays() {
        final int childCount = mWorkspace.getChildCount();
        if (mLastChildCount == childCount) return;

        mOldBackgroundAlphas = new float[childCount];
        mOldAlphas = new float[childCount];
        mNewBackgroundAlphas = new float[childCount];
        mNewAlphas = new float[childCount];
    }

    /**
     * Animates the background scrim.
     * TODO(winsonc): Is there a better place for this?
     *
     * @param finalAlpha the final alpha for the background scrim
     * @param animated whether or not to set the background alpha immediately
     */
    private void animateBackgroundGradient(float finalAlpha, boolean animated) {
        // Cancel any running background animations
        cancelAnimator(mBackgroundFadeInAnimation);
        cancelAnimator(mBackgroundFadeOutAnimation);

        final DragLayer dragLayer = mLauncher.getDragLayer();
        final float startAlpha = dragLayer.getBackgroundAlpha();
        if (finalAlpha != startAlpha) {
            if (animated) {
                mBackgroundFadeOutAnimation =
                        LauncherAnimUtils.ofFloat(mWorkspace, startAlpha, finalAlpha);
                mBackgroundFadeOutAnimation.addUpdateListener(
                        new ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator animation) {
                        dragLayer.setBackgroundAlpha(
                                ((Float)animation.getAnimatedValue()).floatValue());
                    }
                });
                mBackgroundFadeOutAnimation.setInterpolator(new DecelerateInterpolator(1.5f));
                mBackgroundFadeOutAnimation.setDuration(BACKGROUND_FADE_OUT_DURATION);
                mBackgroundFadeOutAnimation.start();
            } else {
                dragLayer.setBackgroundAlpha(finalAlpha);
            }
        }
    }

    /**
     * Cancels the current animation.
     */
    private void cancelAnimation() {
        cancelAnimator(mStateAnimator);
        mStateAnimator = null;
    }

    /**
     * Cancels the specified animation.
     */
    private void cancelAnimator(Animator animator) {
        if (animator != null) {
            animator.setDuration(0);
            animator.cancel();
        }
    }
}