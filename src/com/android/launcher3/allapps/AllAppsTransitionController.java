package com.android.launcher3.allapps;

import static com.android.launcher3.LauncherState.ALL_APPS_CONTENT;
import static com.android.launcher3.LauncherState.ALL_APPS_HEADER;
import static com.android.launcher3.LauncherState.ALL_APPS_HEADER_EXTRA;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.LauncherState.VERTICAL_SWIPE_INDICATOR;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.util.SystemUiController.UI_STATE_ALL_APPS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.Interpolator;

import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.LawnchairUtilsKt;
import ch.deletescape.lawnchair.colors.ColorEngine;
import ch.deletescape.lawnchair.colors.ColorEngine.ResolveInfo;
import ch.deletescape.lawnchair.colors.ColorEngine.Resolvers;
import ch.deletescape.lawnchair.views.BlurScrimView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.uioverrides.OverviewState;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ScrimView;
import com.google.android.apps.nexuslauncher.qsb.AllAppsQsbLayout;
import org.jetbrains.annotations.NotNull;

/**
 * Handles AllApps view transition.
 * 1) Slides all apps view using direct manipulation
 * 2) When finger is released, animate to either top or bottom accordingly.
 * <p/>
 * Algorithm:
 * If release velocity > THRES1, snap according to the direction of movement.
 * If release velocity < THRES1, snap according to either top or bottom depending on whether it's
 * closer to top or closer to the page indicator.
 */
public class AllAppsTransitionController implements StateHandler, OnDeviceProfileChangeListener,
        ColorEngine.OnColorChangeListener {

    public static final Property<AllAppsTransitionController, Float> ALL_APPS_PROGRESS =
            new Property<AllAppsTransitionController, Float>(Float.class, "allAppsProgress") {

                @Override
                public Float get(AllAppsTransitionController controller) {
                    return controller.mProgress;
                }

                @Override
                public void set(AllAppsTransitionController controller, Float progress) {
                    controller.setProgress(progress);
                }
            };

    public static final Property<AllAppsTransitionController, Float> SCRIM_PROGRESS =
            new Property<AllAppsTransitionController, Float>(Float.class, "allAppsProgress") {

                @Override
                public Float get(AllAppsTransitionController controller) {
                    return controller.mScrimProgress;
                }

                @Override
                public void set(AllAppsTransitionController controller, Float progress) {
                    controller.setScrimProgress(progress);
                }
            };

    private AllAppsContainerView mAppsView;
    private ScrimView mScrimView;

    private final Launcher mLauncher;
    private boolean mIsDarkTheme;
    private boolean mIsVerticalLayout;

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    private float mShiftRange;      // changes depending on the orientation
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent
    private float mScrimProgress;

    private float mScrollRangeDelta = 0;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        mShiftRange = mLauncher.getDeviceProfile().heightPx;
        mProgress = mScrimProgress = 1f;

        mIsDarkTheme = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
        mIsVerticalLayout = mLauncher.getDeviceProfile().isVerticalBarLayout();
        mLauncher.addOnDeviceProfileChangeListener(this);
        ColorEngine.getInstance(l).addColorChangeListeners(this, Resolvers.ALLAPPS_BACKGROUND);
    }

    public float getShiftRange() {
        return mShiftRange;
    }

    private void onProgressAnimationStart() {
        // Initialize values that should not change until #onDragEnd
        mAppsView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mIsVerticalLayout = dp.isVerticalBarLayout();
        setScrollRangeDelta(mScrollRangeDelta);

        if (mIsVerticalLayout) {
            mAppsView.setAlpha(1);
            mLauncher.getHotseat().setTranslationY(0);
            mLauncher.getWorkspace().getPageIndicator().setTranslationY(0);
        }
    }

    /**
     * Note this method should not be called outside this class. This is public because it is used
     * in xml-based animations which also handle updating the appropriate UI.
     *
     * @param progress value between 0 and 1, 0 shows all apps and 1 shows workspace
     *
     * @see #setState(LauncherState)
     * @see #setStateWithAnimation(LauncherState, AnimatorSetBuilder, AnimationConfig)
     */
    public void setProgress(float progress) {
        mProgress = progress;
        float shiftCurrent = getShiftApps(progress, true);

        mAppsView.setTranslationY(getShiftApps(progress, false));
        float hotseatTranslation = -mShiftRange + shiftCurrent;

        if (!mIsVerticalLayout) {
            mLauncher.getHotseat().setTranslationY(hotseatTranslation);
            mLauncher.getWorkspace().getPageIndicator().setTranslationY(hotseatTranslation);
        }

        // Use a light system UI (dark icons) if all apps is behind at least half of the
        // status bar.
        boolean forceChange = shiftCurrent - mScrimView.getDragHandleSize()
                <= mLauncher.getDeviceProfile().getInsets().top / 2;
        if (forceChange) {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_ALL_APPS, !mIsDarkTheme);
        } else {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_ALL_APPS, 0);
        }
    }

    private float getShiftApps(float progress, boolean inverted) {
        float normalShift = progress * mShiftRange;
        LawnchairPreferences prefs = LawnchairPreferences.Companion.getInstanceNoCreate();
        if (mAppsView.getFloatingHeaderView().hasVisibleContent()
                && prefs.getAllAppsSearch() != prefs.getDockSearchBar()) {
            float overviewProgress = OVERVIEW.getVerticalProgress(mLauncher);
            float overviewShift = getQsbHeight();
            if (prefs.getAllAppsSearch()) {
                overviewShift = -overviewShift;
            }
            if (progress < overviewProgress) {
                overviewShift = Utilities.mapToRange(progress, 0, overviewProgress,
                        inverted ? prefs.getDockSearchBar() ? -overviewShift : 0 : 0,
                        inverted ? 0 : overviewShift,
                        Interpolators.LINEAR);
            } else if (inverted) {
                overviewShift = 0;
            }
            return normalShift + overviewShift;
        } else {
            return normalShift;
        }
    }

    private int getQsbHeight() {
        MarginLayoutParams mlp = (MarginLayoutParams) mAppsView.getSearchView().getLayoutParams();
        return mlp.topMargin + mlp.height;
    }

    public float getProgress() {
        return mProgress;
    }

    public void setScrimProgress(float progress) {
        mScrimProgress = progress;
        mScrimView.setProgress(progress);
    }

    public float getScrimProgress() {
        return mScrimProgress;
    }

    /**
     * Sets the vertical transition progress to {@param state} and updates all the dependent UI
     * accordingly.
     */
    @Override
    public void setState(LauncherState state) {
        float targetProgress = state.getVerticalProgress(mLauncher);
        setProgress(targetProgress);
        setScrimProgress(state.getScrimProgress(mLauncher));
        setAlphas(state, null, new AnimatorSetBuilder());
        onProgressAnimationEnd();
    }

    /**
     * Creates an animation which updates the vertical transition progress and updates all the
     * dependent UI using various animation events
     */
    @Override
    public void setStateWithAnimation(LauncherState toState,
                                      AnimatorSetBuilder builder, AnimationConfig config) {
        float targetProgress = toState.getVerticalProgress(mLauncher);
        float targetScrimProgress = toState.getScrimProgress(mLauncher);
        if (Float.compare(mProgress, targetProgress) == 0
                && Float.compare(mScrimProgress, targetScrimProgress) == 0) {
            setAlphas(toState, config, builder);
            // Fail fast
            onProgressAnimationEnd();
            return;
        }

        if (!config.playNonAtomicComponent()) {
            // There is no atomic component for the all apps transition, so just return early.
            return;
        }

        Interpolator interpolator = config.userControlled ? LINEAR : toState == OVERVIEW
                ? builder.getInterpolator(ANIM_OVERVIEW_SCALE, FAST_OUT_SLOW_IN)
                : FAST_OUT_SLOW_IN;
        ObjectAnimator anim =
                ObjectAnimator.ofFloat(this, ALL_APPS_PROGRESS, mProgress, targetProgress);
        anim.setDuration(config.duration);
        anim.setInterpolator(builder.getInterpolator(ANIM_VERTICAL_PROGRESS, interpolator));
        anim.addListener(getProgressAnimatorListener());

        builder.play(anim);

        ObjectAnimator scrimAnim =
                ObjectAnimator.ofFloat(this, SCRIM_PROGRESS, mScrimProgress, targetScrimProgress);
        scrimAnim.setDuration(config.duration);
        scrimAnim.setInterpolator(builder.getInterpolator(ANIM_VERTICAL_PROGRESS, interpolator));
        scrimAnim.addListener(getProgressAnimatorListener());

        builder.play(scrimAnim);

        setAlphas(toState, config, builder);
    }

    private void setAlphas(LauncherState toState, AnimationConfig config,
            AnimatorSetBuilder builder) {
        PropertySetter setter = config == null ? NO_ANIM_PROPERTY_SETTER
                : config.getPropertySetter(builder);
        int visibleElements = toState.getVisibleElements(mLauncher);
        LawnchairPreferences prefs = LawnchairPreferences.Companion.getInstanceNoCreate();
        boolean hasHeader = (visibleElements & ALL_APPS_HEADER) != 0 && prefs.getAllAppsSearch();
        boolean hasHeaderExtra = (visibleElements & ALL_APPS_HEADER_EXTRA) != 0;
        boolean hasContent = (visibleElements & ALL_APPS_CONTENT) != 0;

        Interpolator allAppsFade = builder.getInterpolator(ANIM_ALL_APPS_FADE, LINEAR);
        setter.setViewAlpha(mAppsView.getSearchView(), hasHeader ? 1 : 0, allAppsFade);
        setter.setViewAlpha(mAppsView.getContentView(), hasContent ? 1 : 0, allAppsFade);
        setter.setViewAlpha(mAppsView.getScrollBar(), hasContent ? 1 : 0, allAppsFade);
        mAppsView.getFloatingHeaderView().setContentVisibility(hasHeaderExtra, hasContent, setter,
                allAppsFade);

        setter.setInt(mScrimView, ScrimView.DRAG_HANDLE_ALPHA,
                (visibleElements & VERTICAL_SWIPE_INDICATOR) != 0 ? 255 : 0, LINEAR);
    }

    public AnimatorListenerAdapter getProgressAnimatorListener() {
        return new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                onProgressAnimationEnd();
            }

            @Override
            public void onAnimationStart(Animator animation) {
                onProgressAnimationStart();
            }
        };
    }

    public void setupViews(AllAppsContainerView appsView) {
        mAppsView = appsView;
        mScrimView = mLauncher.findViewById(R.id.scrim_view);
    }

    /**
     * Updates the total scroll range but does not update the UI.
     */
    public void setScrollRangeDelta(float delta) {
        mScrollRangeDelta = delta;
        mShiftRange = mLauncher.getDeviceProfile().heightPx - mScrollRangeDelta;

        if (mScrimView != null) {
            mScrimView.reInitUi();
        }
    }

    /**
     * Set the final view states based on the progress.
     * TODO: This logic should go in {@link LauncherState}
     */
    private void onProgressAnimationEnd() {
        if (Float.compare(mProgress, 1f) == 0) {
            mAppsView.setVisibility(View.INVISIBLE);
            mAppsView.reset(false /* animate */);
        } else if (Float.compare(mProgress, 0f) == 0) {
            mAppsView.setVisibility(View.VISIBLE);
            mAppsView.onScrollUpEnd();
        } else {
            mAppsView.setVisibility(View.VISIBLE);
        }
    }

    public void reset() {
        setProgress(1f);
    }

    public AllAppsContainerView getAppsView() {
        return mAppsView;
    }

    @Override
    public void onColorChange(@NotNull ResolveInfo resolveInfo) {
        mIsDarkTheme = LawnchairUtilsKt.isDark(resolveInfo.getColor());
    }

    public void setOverlayScroll(float scroll) {
        if (mScrimView instanceof BlurScrimView) {
            ((BlurScrimView) mScrimView).setOverlayScroll(scroll);
        }
    }
}
