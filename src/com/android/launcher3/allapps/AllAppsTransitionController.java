package com.android.launcher3.allapps;

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.util.Property;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.AllAppsScrim;

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
public class AllAppsTransitionController
        implements SearchUiManager.OnScrollRangeChangeListener, LauncherStateManager.StateHandler {

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

    public static final float PARALLAX_COEFFICIENT = .125f;

    private AllAppsContainerView mAppsView;
    private Workspace mWorkspace;
    private Hotseat mHotseat;

    private final Launcher mLauncher;
    private final boolean mIsDarkTheme;
    private final boolean mIsVerticalLayout;

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    private float mShiftRange;      // changes depending on the orientation
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent

    private static final float DEFAULT_SHIFT_RANGE = 10;

    private AllAppsScrim mAllAppsScrim;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        mShiftRange = DEFAULT_SHIFT_RANGE;
        mProgress = 1f;

        mIsDarkTheme = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
        mIsVerticalLayout = mLauncher.getDeviceProfile().isVerticalBarLayout();
    }

    public float getShiftRange() {
        return mShiftRange;
    }

    private void onProgressAnimationStart() {
        // Initialize values that should not change until #onDragEnd
        mHotseat.setVisibility(View.VISIBLE);
        mAppsView.setVisibility(View.VISIBLE);
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
        float shiftCurrent = progress * mShiftRange;

        float workspaceHotseatAlpha = Utilities.boundToRange(progress, 0f, 1f);
        float alpha = 1 - workspaceHotseatAlpha;

        mAppsView.setTranslationY(shiftCurrent);
        if (mAllAppsScrim == null) {
            mAllAppsScrim = mLauncher.findViewById(R.id.all_apps_scrim);
        }
        float hotseatTranslation = -mShiftRange + shiftCurrent;
        mAllAppsScrim.setProgress(hotseatTranslation, alpha);

        if (!mIsVerticalLayout) {
            mAppsView.setAlpha(alpha);
            mLauncher.getHotseat().setTranslationY(hotseatTranslation);
            mLauncher.getWorkspace().getPageIndicator().setTranslationY(hotseatTranslation);

            // Use a light system UI (dark icons) if all apps is behind at least half of the
            // status bar.
            boolean forceChange = shiftCurrent <= mShiftRange / 4;
            if (forceChange) {
                mLauncher.getSystemUiController().updateUiState(
                        SystemUiController.UI_STATE_ALL_APPS, !mIsDarkTheme);
            } else {
                mLauncher.getSystemUiController().updateUiState(
                        SystemUiController.UI_STATE_ALL_APPS, 0);
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
            // Fail fast
            onProgressAnimationEnd();
            return;
        }

        Interpolator interpolator = config.userControlled ? LINEAR : FAST_OUT_SLOW_IN;
        ObjectAnimator anim =
                ObjectAnimator.ofFloat(this, ALL_APPS_PROGRESS, mProgress, targetProgress);
        anim.setDuration(config.duration);
        anim.setInterpolator(interpolator);
        anim.addListener(getProgressAnimatorListener());

        builder.play(anim);
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

    public void setupViews(AllAppsContainerView appsView, Hotseat hotseat, Workspace workspace) {
        mAppsView = appsView;
        mHotseat = hotseat;
        mWorkspace = workspace;
        mHotseat.bringToFront();
        mAppsView.getSearchUiManager().addOnScrollRangeChangeListener(this);
    }

    @Override
    public void onScrollRangeChanged(int scrollRange) {
        mShiftRange = scrollRange;
        setProgress(mProgress);
    }

    /**
     * Set the final view states based on the progress.
     * TODO: This logic should go in {@link LauncherState}
     */
    private void onProgressAnimationEnd() {
        if (Float.compare(mProgress, 1f) == 0) {
            mAppsView.setVisibility(View.INVISIBLE);
            mHotseat.setVisibility(View.VISIBLE);
            mAppsView.reset();
        } else if (Float.compare(mProgress, 0f) == 0) {
            mHotseat.setVisibility(View.INVISIBLE);
            mAppsView.setVisibility(View.VISIBLE);
            mAppsView.onScrollUpEnd();
        } else {
            mAppsView.setVisibility(View.VISIBLE);
            mHotseat.setVisibility(View.VISIBLE);
        }
    }
}
