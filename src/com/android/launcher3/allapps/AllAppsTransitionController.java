package com.android.launcher3.allapps;

import static com.android.launcher3.LauncherState.ALL_APPS_CONTENT;
import static com.android.launcher3.LauncherState.ALL_APPS_HEADER_EXTRA;
import static com.android.launcher3.LauncherState.APPS_VIEW_ITEM_MASK;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.VERTICAL_SWIPE_INDICATOR;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.FINAL_FRAME;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_HEADER_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.EditText;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.views.ScrimView;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.PluginListener;

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
public class AllAppsTransitionController implements StateHandler<LauncherState>,
        OnDeviceProfileChangeListener, PluginListener<AllAppsSearchPlugin> {

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
    private boolean mIsVerticalLayout;

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    private float mShiftRange;      // changes depending on the orientation
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent

    private float mScrollRangeDelta = 0;

    // plugin related variables
    private AllAppsSearchPlugin mPlugin;
    private View mPluginContent;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        mShiftRange = mLauncher.getDeviceProfile().heightPx;
        mProgress = 1f;

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
     * @see #setStateWithAnimation(LauncherState, StateAnimationConfig, PendingAnimation)
     */
    public void setProgress(float progress) {
        mProgress = progress;
        mScrimView.setProgress(progress);
        float shiftCurrent = progress * mShiftRange;

        mAppsView.setTranslationY(shiftCurrent);
        if (mPlugin != null) {
            mPlugin.setProgress(progress);
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
        setAlphas(state, new StateAnimationConfig(), NO_ANIM_PROPERTY_SETTER);
        onProgressAnimationEnd();
    }

    /**
     * Creates an animation which updates the vertical transition progress and updates all the
     * dependent UI using various animation events
     */
    @Override
    public void setStateWithAnimation(LauncherState toState,
            StateAnimationConfig config, PendingAnimation builder) {
        float targetProgress = toState.getVerticalProgress(mLauncher);
        if (Float.compare(mProgress, targetProgress) == 0) {
            if (!config.onlyPlayAtomicComponent()) {
                setAlphas(toState, config, builder);
            }
            // Fail fast
            onProgressAnimationEnd();
            return;
        }

        if (config.onlyPlayAtomicComponent()) {
            // There is no atomic component for the all apps transition, so just return early.
            return;
        }

        Interpolator interpolator = config.userControlled ? LINEAR : toState == OVERVIEW
                ? config.getInterpolator(ANIM_OVERVIEW_SCALE, FAST_OUT_SLOW_IN)
                : FAST_OUT_SLOW_IN;

        Animator anim = createSpringAnimation(mProgress, targetProgress);
        anim.setInterpolator(config.getInterpolator(ANIM_VERTICAL_PROGRESS, interpolator));
        anim.addListener(getProgressAnimatorListener());
        builder.add(anim);

        setAlphas(toState, config, builder);
    }

    public Animator createSpringAnimation(float... progressValues) {
        return ObjectAnimator.ofFloat(this, ALL_APPS_PROGRESS, progressValues);
    }

    /**
     * Updates the property for the provided state
     */
    public void setAlphas(LauncherState state, StateAnimationConfig config, PropertySetter setter) {
        int visibleElements = state.getVisibleElements(mLauncher);
        boolean hasHeaderExtra = (visibleElements & ALL_APPS_HEADER_EXTRA) != 0;
        boolean hasAllAppsContent = (visibleElements & ALL_APPS_CONTENT) != 0;

        boolean hasAnyVisibleItem = (visibleElements & APPS_VIEW_ITEM_MASK) != 0;

        Interpolator allAppsFade = config.getInterpolator(ANIM_ALL_APPS_FADE, LINEAR);
        Interpolator headerFade = config.getInterpolator(ANIM_ALL_APPS_HEADER_FADE, allAppsFade);

        if (mPlugin == null) {
            setter.setViewAlpha(mAppsView.getContentView(), hasAllAppsContent ? 1 : 0, allAppsFade);
            setter.setViewAlpha(mAppsView.getScrollBar(), hasAllAppsContent ? 1 : 0, allAppsFade);
            mAppsView.getFloatingHeaderView().setContentVisibility(hasHeaderExtra,
                    hasAllAppsContent, setter, headerFade, allAppsFade);
        } else {
            setter.setViewAlpha(mPluginContent, hasAllAppsContent ? 1 : 0, allAppsFade);
            setter.setViewAlpha(mAppsView.getContentView(), 0, allAppsFade);
            setter.setViewAlpha(mAppsView.getScrollBar(), 0, allAppsFade);
        }
        mAppsView.getSearchUiManager().setContentVisibility(visibleElements, setter, allAppsFade);

        setter.setInt(mScrimView, ScrimView.DRAG_HANDLE_ALPHA,
                (visibleElements & VERTICAL_SWIPE_INDICATOR) != 0 ? 255 : 0, allAppsFade);

        // Set visibility of the container at the very beginning or end of the transition.
        setter.setViewAlpha(mAppsView, hasAnyVisibleItem ? 1 : 0,
                hasAnyVisibleItem ? INSTANT : FINAL_FRAME);
    }

    public AnimatorListenerAdapter getProgressAnimatorListener() {
        return AnimationSuccessListener.forRunnable(this::onProgressAnimationEnd);
    }

    public void setupViews(AllAppsContainerView appsView, ScrimView scrimView) {
        mAppsView = appsView;
        mScrimView = scrimView;
        PluginManagerWrapper.INSTANCE.get(mLauncher)
                .addPluginListener(this, AllAppsSearchPlugin.class, false);
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
        }
        updatePluginAnimationEnd();
    }

    @Override
    public void onPluginConnected(AllAppsSearchPlugin plugin, Context context) {
        mPlugin = plugin;
        mPluginContent = mLauncher.getLayoutInflater().inflate(
                R.layout.all_apps_content_layout, mAppsView, false);
        mAppsView.addView(mPluginContent);
        mPluginContent.setAlpha(0f);
        mPlugin.setup((ViewGroup) mPluginContent, mLauncher, mShiftRange);
    }

    @Override
    public void onPluginDisconnected(AllAppsSearchPlugin plugin) {
        mPlugin = null;
        mAppsView.removeView(mPluginContent);
    }

    public void onActivityDestroyed() {
        PluginManagerWrapper.INSTANCE.get(mLauncher).removePluginListener(this);
    }

    /** Used for the plugin to signal when drag starts happens
     * @param toAllApps*/
    public void onDragStart(boolean toAllApps) {
        if (mPlugin == null) return;

        if (toAllApps) {
            EditText editText = mAppsView.getSearchUiManager().setTextSearchEnabled(true);
            mPlugin.setEditText(editText);
        }
        mPlugin.onDragStart(toAllApps ? 1f : 0f);
    }

    private void updatePluginAnimationEnd() {
        if (mPlugin == null) return;
        mPlugin.onAnimationEnd(mProgress);
        if (Float.compare(mProgress, 1f) == 0) {
            mAppsView.getSearchUiManager().setTextSearchEnabled(false);
            mPlugin.setEditText(null);
        }
    }
}
