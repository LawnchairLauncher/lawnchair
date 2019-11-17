package com.android.launcher3.allapps;

import static com.android.launcher3.LauncherState.ALL_APPS_CONTENT;
import static com.android.launcher3.LauncherState.ALL_APPS_HEADER_EXTRA;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.VERTICAL_SWIPE_INDICATOR;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_ALL_APPS_HEADER_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.util.SystemUiController.UI_STATE_ALL_APPS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.FloatProperty;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.anim.SpringObjectAnimator;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ScrimView;

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
public class AllAppsTransitionController implements StateHandler, OnDeviceProfileChangeListener {

    private static final float SPRING_DAMPING_RATIO = 0.9f;
    private static final float SPRING_STIFFNESS = 600f;

    public static final FloatProperty<AllAppsTransitionController> ALL_APPS_PROGRESS =
            new FloatProperty<AllAppsTransitionController>("allAppsProgress") {

        @Override
        public Float get(AllAppsTransitionController controller) {
            return controller.mProgress;
        }

        @Override
        public void setValue(AllAppsTransitionController controller, float progress) {
            controller.setProgress(progress);
        }
    };

    private static final int APPS_VIEW_ALPHA_CHANNEL_INDEX = 0;

    private AllAppsContainerView mAppsView;
    private ScrimView mScrimView;

    private final Launcher mLauncher;
    private final boolean mIsDarkTheme;
    private boolean mIsVerticalLayout;

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    private float mShiftRange;      // changes depending on the orientation
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent

    private float mScrollRangeDelta = 0;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        mShiftRange = mLauncher.getDeviceProfile().heightPx;
        mProgress = 1f;

        mIsDarkTheme = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
        mIsVerticalLayout = mLauncher.getDeviceProfile().isVerticalBarLayout();
        mLauncher.addOnDeviceProfileChangeListener(this);
    }

    public float getShiftRange() {
        return mShiftRange;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mIsVerticalLayout = dp.isVerticalBarLayout();
        setScrollRangeDelta(mScrollRangeDelta);

        if (mIsVerticalLayout) {
            mAppsView.getAlphaProperty(APPS_VIEW_ALPHA_CHANNEL_INDEX).setValue(1);
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
        mScrimView.setProgress(progress);
        float shiftCurrent = progress * mShiftRange;

        mAppsView.setTranslationY(shiftCurrent);

        // Use a light system UI (dark icons) if all apps is behind at least half of the
        // status bar.
        boolean forceChange = shiftCurrent - mScrimView.getDragHandleSize()
                <= mLauncher.getDeviceProfile().getInsets().top / 2;
        if (forceChange) {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_ALL_APPS, !mIsDarkTheme);
        } else {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_ALL_APPS, 0);
        }

        if ((OVERVIEW.getVisibleElements(mLauncher) & HOTSEAT_ICONS) != 0) {
            // Translate hotseat with the shelf until reaching overview.
            float overviewProgress = OVERVIEW.getVerticalProgress(mLauncher);
            if (progress >= overviewProgress || mLauncher.isInState(BACKGROUND_APP)) {
                float hotseatShift = (progress - overviewProgress) * mShiftRange;
                mLauncher.getHotseat().setTranslationY(hotseatShift);
            }
        }
    }

    public float getProgress() {
        return mProgress;
    }

    /**
     * Sets the vertical transition progress to {@param state} and updates all the dependent UI
     * accordingly.
     */
    @Override
    public void setState(LauncherState state) {
        setProgress(state.getVerticalProgress(mLauncher));
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
        if (Float.compare(mProgress, targetProgress) == 0) {
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
        Animator anim = createSpringAnimation(mProgress, targetProgress);
        anim.setDuration(config.duration);
        anim.setInterpolator(builder.getInterpolator(ANIM_VERTICAL_PROGRESS, interpolator));
        anim.addListener(getProgressAnimatorListener());

        builder.play(anim);

        setAlphas(toState, config, builder);
    }

    public Animator createSpringAnimation(float... progressValues) {
        return new SpringObjectAnimator<>(this, ALL_APPS_PROGRESS, 1f / mShiftRange,
                SPRING_DAMPING_RATIO, SPRING_STIFFNESS, progressValues);
    }

    private void setAlphas(LauncherState toState, AnimationConfig config,
            AnimatorSetBuilder builder) {
        setAlphas(toState.getVisibleElements(mLauncher), config, builder);
    }

    public void setAlphas(int visibleElements, AnimationConfig config, AnimatorSetBuilder builder) {
        PropertySetter setter = config == null ? NO_ANIM_PROPERTY_SETTER
                : config.getPropertySetter(builder);
        boolean hasHeaderExtra = (visibleElements & ALL_APPS_HEADER_EXTRA) != 0;
        boolean hasAllAppsContent = (visibleElements & ALL_APPS_CONTENT) != 0;

        Interpolator allAppsFade = builder.getInterpolator(ANIM_ALL_APPS_FADE, LINEAR);
        Interpolator headerFade = builder.getInterpolator(ANIM_ALL_APPS_HEADER_FADE, allAppsFade);
        setter.setViewAlpha(mAppsView.getContentView(), hasAllAppsContent ? 1 : 0, allAppsFade);
        setter.setViewAlpha(mAppsView.getScrollBar(), hasAllAppsContent ? 1 : 0, allAppsFade);
        mAppsView.getFloatingHeaderView().setContentVisibility(hasHeaderExtra, hasAllAppsContent,
                setter, headerFade, allAppsFade);
        mAppsView.getSearchUiManager().setContentVisibility(visibleElements, setter, allAppsFade);

        setter.setInt(mScrimView, ScrimView.DRAG_HANDLE_ALPHA,
                (visibleElements & VERTICAL_SWIPE_INDICATOR) != 0 ? 255 : 0, allAppsFade);
    }

    public AnimatorListenerAdapter getProgressAnimatorListener() {
        return new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                onProgressAnimationEnd();
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
    void setScrollRangeDelta(float delta) {
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
            mAppsView.reset(false /* animate */);
        } else if (isAllAppsExpanded()) {
            mAppsView.onScrollUpEnd();
        }
    }

    private boolean isAllAppsExpanded() {
        return Float.compare(mProgress, 0f) == 0;
    }

    public void highlightWorkTabIfNecessary() {
        if (isAllAppsExpanded()) {
            mAppsView.highlightWorkTabIfNecessary();
        }
    }
}
