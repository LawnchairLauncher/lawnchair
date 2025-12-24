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
package com.android.quickstep.interaction;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.Utilities.mapBoundToRange;
import static com.android.launcher3.Utilities.mapRange;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.taskbar.StashedHandleViewController.ALPHA_INDEX_ALL_SET_TRANSITION;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;
import static com.android.quickstep.RecentsAnimationDeviceState.RESET_TO_DEFAULT_GESTURAL_HEIGHT;
import static com.android.quickstep.views.WallpaperScreenshotClipView.CLIP_ANIM_DURATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnWindowVisibilityChangeListener;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.RemoveAnimationSettingsTracker;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.taskbar.StashedHandleViewController;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarActivityContext.UIControllerChangeListener;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.Executors;
import com.android.quickstep.GestureState;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.OverviewComponentObserver.OverviewChangeListener;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.util.ActivityPreloadUtil;
import com.android.quickstep.util.LottieAnimationColorUtils;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.WallpaperScreenshotClipView;
import com.android.wm.shell.shared.TypefaceUtils.FontFamily;

import com.airbnb.lottie.LottieAnimationView;

import java.net.URISyntaxException;
import java.util.Map;

/**
 * A page shows after SUW flow to hint users to swipe up from the bottom of the screen to go home
 * for the gestural system navigation.
 */
public class AllSetActivity extends Activity implements UIControllerChangeListener {

    public static final float ALL_SET_SWIPE_THRESHOLD_FOR_WORKSPACE_ANIM = 0.95f;
    // The fade-out happens in the last 65% of the animation.
    private static final float CONTENT_FADE_OUT_START_PROGRESS = 0.35f;
    // We allow the swipe up to start in the bottom third of the screen.
    private static final float GESTURE_HEIGHT_RATIO_OF_WINDOW_HEIGHT = 0.33f;

    private static final String TAG = "AllSetActivity";

    private static final String LOG_TAG = "AllSetActivity";
    private static final String URI_SYSTEM_NAVIGATION_SETTING =
            "#Intent;action=com.android.settings.SEARCH_RESULT_TRAMPOLINE;S.:settings:fragment_args_key=gesture_system_navigation_input_summary;S.:settings:show_fragment=com.android.settings.gestures.SystemNavigationGestureSettings;end";
    private static final String INTENT_ACTION_ACTIVITY_CLOSED =
            "com.android.quickstep.interaction.ACTION_ALL_SET_ACTIVITY_CLOSED";
    private static final String EXTRA_ACCENT_COLOR_DARK_MODE = "suwColorAccentDark";
    private static final String EXTRA_ACCENT_COLOR_LIGHT_MODE = "suwColorAccentLight";
    private static final String EXTRA_DEVICE_NAME = "suwDeviceName";

    private static final String LOTTIE_PRIMARY_COLOR_TOKEN = ".primary";
    private static final String LOTTIE_TERTIARY_COLOR_TOKEN = ".tertiary";

    private static final String SUW_THEME_SYSTEM_PROPERTY = "setupwizard.theme";
    private static final String GLIF_EXPRESSIVE_THEME = "glif_expressive";
    private static final String GLIF_EXPRESSIVE_LIGHT_THEME = "glif_expressive_light";

    private boolean mIsExpressiveThemeEnabledInSUW = false;

    private static final float HINT_BOTTOM_FACTOR = 1 - .94f;

    private static final int MAX_SWIPE_DURATION = 350;

    private static final int WALLPAPER_BLUR_RADIUS = 30;

    private static final float ANIMATION_PAUSE_ALPHA_THRESHOLD = 0.1f;

    private static final String KEY_BACKGROUND_ANIMATION_TOGGLED_ON =
            "background_animation_toggled_on";

    private boolean mIsTablet;

    private final AnimatedFloat mSwipeProgress = new AnimatedFloat(this::onSwipeProgressUpdate);

    private final InvariantDeviceProfile.OnIDPChangeListener mOnIDPChangeListener =
            modelPropertiesChanged -> updateTextForNavigationMode();

    private TISBindHelper mTISBindHelper;

    private BgDrawable mBackground;
    private View mRootView;
    private float mSwipeUpShift;

    @Nullable private Vibrator mVibrator;
    private LottieAnimationView mAnimatedBackground;
    private Animator.AnimatorListener mBackgroundAnimatorListener;

    private AnimatorPlaybackController mLauncherStartAnim = null;

    // Auto play background animation by default
    private boolean mBackgroundAnimationToggledOn = true;

    private TextView mHintView;
    private final OverviewChangeListener mOverviewChangeListener = this::onOverviewTargetChange;

    @Nullable private AnimatorSet mExpressiveAnimSet;
    @Nullable private WallpaperScreenshotClipView mWallpaperClipPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        String SUWTheme = SystemProperties.get(SUW_THEME_SYSTEM_PROPERTY, "");
        mIsExpressiveThemeEnabledInSUW = SUWTheme.equals(GLIF_EXPRESSIVE_THEME)
                || SUWTheme.equals(GLIF_EXPRESSIVE_LIGHT_THEME);
        if (mIsExpressiveThemeEnabledInSUW) setTheme(R.style.AllSetTheme_Expressive);

        super.onCreate(savedInstanceState);
        mIsTablet = getDP().getDeviceProperties().isTablet()
                    && !getDP().getDeviceProperties().isTwoPanels();
        boolean isDarkTheme =
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        if (mIsExpressiveThemeEnabledInSUW) {
            setupExpressiveTheme();
        } else {
            setupDefaultTheme(savedInstanceState, isDarkTheme);
        }
        initializeCommonViewsAndListeners();
        configureSystemUI(isDarkTheme);

        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);
        mVibrator = getSystemService(Vibrator.class);
        getIDP().addOnChangeListener(mOnIDPChangeListener);
        OverviewComponentObserver.INSTANCE.get(this)
                .addOverviewChangeListener(mOverviewChangeListener);
        ActivityPreloadUtil.preloadOverviewForSUWAllSet(this);
    }

    private void configureSystemUI(boolean isDarkTheme) {
        int systemBarsMask = APPEARANCE_LIGHT_STATUS_BARS | APPEARANCE_LIGHT_NAVIGATION_BARS;
        int systemBarsAppearance = isDarkTheme ? 0 : systemBarsMask;
        Window window = getWindow();
        WindowInsetsController insetsController = window == null
                ? null
                : window.getInsetsController();

        if (insetsController != null) {
            insetsController.setSystemBarsAppearance(systemBarsAppearance, systemBarsMask);
        }
        if (mIsExpressiveThemeEnabledInSUW && window != null) {
            window.setBackgroundBlurRadius(WALLPAPER_BLUR_RADIUS);
        }
        mRootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void initializeCommonViewsAndListeners() {
        mHintView = findViewById(R.id.hint);
        mHintView.setAccessibilityDelegate(new SkipButtonAccessibilityDelegate());
        updateTextForNavigationMode();

        mSwipeUpShift = getResources().getDimension(R.dimen.allset_swipe_up_shift);

        View navigationSettings = findViewById(R.id.navigation_settings);
        navigationSettings.setOnClickListener(v -> {
            try {
                // This is the action that starts the system navigation settings page
                startActivityForResult(
                        Intent.parseUri(URI_SYSTEM_NAVIGATION_SETTING, 0), 0);
            } catch (URISyntaxException e) {
                Log.e(LOG_TAG, "Failed to parse system nav settings intent", e);
            }
        });
    }

    private void setupDefaultTheme(@Nullable Bundle savedInstanceState, boolean isDarkTheme) {
        setContentView(R.layout.activity_allset);
        mRootView = findViewById(R.id.root_view);

        mBackground = new BgDrawable(this);
        mRootView.setBackground(mBackground);

        int accentColor = getIntent().getIntExtra(
                isDarkTheme ? EXTRA_ACCENT_COLOR_DARK_MODE : EXTRA_ACCENT_COLOR_LIGHT_MODE,
                isDarkTheme ? Color.WHITE : Color.BLACK);

        ((ImageView) findViewById(R.id.icon)).getDrawable().mutate().setTint(accentColor);
        TextView navigationSettings = findViewById(R.id.navigation_settings);
        navigationSettings.setTextColor(accentColor);

        String suwDeviceName = getIntent().getStringExtra(EXTRA_DEVICE_NAME);
        TextView subtitle = findViewById(R.id.subtitle);
        subtitle.setText(TextUtils.isEmpty(suwDeviceName)
                ? getString(R.string.allset_description_fallback)
                : getString(R.string.allset_description_generic, suwDeviceName));

        mAnimatedBackground = findViewById(R.id.animated_background);
        // There's a bug in the currently used external Lottie library (v5.2.0), and it doesn't load
        // the correct animation from the raw resources when configuration changes, so we need to
        // manually load the resource and pass it to Lottie.
        mAnimatedBackground.setAnimation(getResources().openRawResource(R.raw.all_set_page_bg),
                null);

        LottieAnimationColorUtils.updateToColorResources(
                mAnimatedBackground,
                Map.of(LOTTIE_PRIMARY_COLOR_TOKEN, R.color.all_set_bg_primary,
                        LOTTIE_TERTIARY_COLOR_TOKEN, R.color.all_set_bg_tertiary),
                getTheme());
        mAnimatedBackground.setScaleX(Utilities.isRtl(getResources()) ? -1f : 1f);

        mBackgroundAnimationToggledOn = savedInstanceState == null
                || savedInstanceState.getBoolean(KEY_BACKGROUND_ANIMATION_TOGGLED_ON, true);
        // The animated background is behind a scroll view, which intercepts all input.
        // However, the content view also covers the full screen
        requireViewById(R.id.content).setOnClickListener(v -> {
            mBackgroundAnimationToggledOn = !mBackgroundAnimationToggledOn;
            maybeResumeOrPauseBackgroundAnimation();
        });
        setUpBackgroundAnimation(getDP().getDeviceProperties().isTablet());
    }

    private void setupExpressiveTheme() {
        setContentView(R.layout.activity_allset_expressive);
        mRootView = findViewById(R.id.root_view);

        TextView title = findViewById(R.id.title);
        TextView subtitle = findViewById(R.id.subtitle);
        mHintView = findViewById(R.id.hint);
        TextView navigationSettings = findViewById(R.id.navigation_settings);
        title.setText(R.string.allset_title_expressive_fixed);
        title.setTypeface(
                Typeface.create(FontFamily.GSF_HEADLINE_LARGE_EMPHASIZED.getValue(),
                        Typeface.NORMAL));
        subtitle.setTypeface(
                Typeface.create(FontFamily.GSF_BODY_MEDIUM.getValue(), Typeface.NORMAL));
        mHintView.setTypeface(
                Typeface.create(FontFamily.GSF_HEADLINE_SMALL_EMPHASIZED.getValue(),
                        Typeface.NORMAL));
        navigationSettings.setTypeface(
                Typeface.create(FontFamily.GSF_HEADLINE_SMALL_EMPHASIZED.getValue(),
                        Typeface.NORMAL));

        if (mIsExpressiveThemeEnabledInSUW && Flags.enableNewAllSetAnimation()) {
            mWallpaperClipPath = findViewById(R.id.wallpaper_clip_path);
            mWallpaperClipPath.setVisibility(VISIBLE);

            // Attempt to pre-load screenshot.
            ViewTreeObserver observer = mWallpaperClipPath.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mWallpaperClipPath.getViewTreeObserver().removeOnGlobalLayoutListener(
                            this);

                    tryCaptureWallpaperScreenshot();
                }
            });

            // If wallpaper is not ready for pre-load, we try one more time.
            observer.addOnWindowVisibilityChangeListener(new OnWindowVisibilityChangeListener() {
                @Override
                public void onWindowVisibilityChanged(int visibility) {
                    if (visibility != VISIBLE) {
                        return;
                    }
                    mWallpaperClipPath.getViewTreeObserver()
                            .removeOnWindowVisibilityChangeListener(this);
                    tryCaptureWallpaperScreenshot();

                }
            });
            mExpressiveAnimSet = buildExpressiveAnimatorSet();
        }
    }

    private void tryCaptureWallpaperScreenshot() {
        if (mWallpaperClipPath != null) {
            View wallpaperScrim = findViewById(R.id.wallpaper_scrim);
            wallpaperScrim.setVisibility(GONE);
            Runnable resetScrim = () -> {
                wallpaperScrim.setVisibility(VISIBLE);
            };
            mWallpaperClipPath.tryCaptureWallpaperScreenshot(
                    getWindow(), getDisplayId(), mRootView, WALLPAPER_BLUR_RADIUS, resetScrim);
        }
    }

    private AnimatorSet buildExpressiveAnimatorSet() {
        if (!mIsExpressiveThemeEnabledInSUW || !Flags.enableNewAllSetAnimation()) {
            return null;
        }

        View content = findViewById(R.id.content);
        int height = getWindowManager().getCurrentWindowMetrics().getBounds().height();

        ValueAnimator transYAnimator = ValueAnimator.ofFloat(0, -height);
        transYAnimator.setDuration(CLIP_ANIM_DURATION);
        transYAnimator.setInterpolator(LINEAR);
        transYAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float transY = (float) animation.getAnimatedValue();
                mWallpaperClipPath.setClipTranslationY(transY, animation.getAnimatedFraction());
                StashedHandleViewController controller = getStashedHandleViewController();
                if (controller != null) {
                    controller.setTranslationYForSwipe(transY);
                }
            }
        });

        ValueAnimator contentAlpha = ValueAnimator.ofFloat(1, 0);
        contentAlpha.setInterpolator(LINEAR);
        contentAlpha.setDuration(CLIP_ANIM_DURATION);
        contentAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float progress = valueAnimator.getAnimatedFraction();
                float alpha = 1f - clampToProgress(progress, CONTENT_FADE_OUT_START_PROGRESS, 1f);
                content.setAlpha(alpha);
            }
        });

        ValueAnimator hintAndHandleAlpha = ValueAnimator.ofFloat(1, 0);
        hintAndHandleAlpha.setDuration(CLIP_ANIM_DURATION / 10);
        hintAndHandleAlpha.setInterpolator(LINEAR);
        hintAndHandleAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float alpha = (float) valueAnimator.getAnimatedValue();
                mHintView.setAlpha(alpha);
                StashedHandleViewController controller = getStashedHandleViewController();
                if (controller != null) {
                    controller.getStashedHandleAlpha()
                            .get(ALPHA_INDEX_ALL_SET_TRANSITION)
                            .setValue(alpha);
                }
            }
        });

        AnimatorSet as = new AnimatorSet();
        mWallpaperClipPath.addClipAnimation(as);
        as.play(transYAnimator);
        as.play(contentAlpha);
        as.play(hintAndHandleAlpha);
        as.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                StashedHandleViewController controller = getStashedHandleViewController();
                if (controller != null) {
                    controller.setTranslationYForSwipe(0);
                    controller.getStashedHandleAlpha()
                            .get(ALPHA_INDEX_ALL_SET_TRANSITION)
                            .setValue(1f);
                }
            }
        });
        return as;
    }

    private @Nullable StashedHandleViewController getStashedHandleViewController() {
        if (mTISBindHelper != null) {
            TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
            if (taskbarManager != null) {
                return taskbarManager.getCurrentActivityContext()
                        .getControllers().stashedHandleViewController;
            }
        }
        return null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mIsExpressiveThemeEnabledInSUW) {
            outState.putBoolean(KEY_BACKGROUND_ANIMATION_TOGGLED_ON, mBackgroundAnimationToggledOn);
        }
    }

    private InvariantDeviceProfile getIDP() {
        return LauncherAppState.getInstance(this).getInvariantDeviceProfile();
    }

    private DeviceProfile getDP() {
        return getIDP().getDeviceProfile(this);
    }

    private void updateTextForNavigationMode() {
        boolean isGestureMode = getDP().getDeviceProperties().isGestureMode();
        int hintTextResId;
        String subtitleText = null;

        if (mIsExpressiveThemeEnabledInSUW) {
            hintTextResId = isGestureMode
                    ? R.string.allset_hint_expressive
                    : R.string.allset_button_hint_expressive;
            String deviceName = getString(mIsTablet
                    ? R.string.allset_device_type_tablet
                    : R.string.allset_device_type_phone);
            int subtitleFormatResId = isGestureMode
                    ? R.string.allset_subtitle_expressive_gesture_navigation
                    : R.string.allset_subtitle_expressive_button_navigation;

            subtitleText = getString(subtitleFormatResId, deviceName);
        } else {
            hintTextResId = isGestureMode
                    ? R.string.allset_hint
                    : R.string.allset_button_hint;
        }

        mHintView.setText(hintTextResId);

        TextView subtitle = findViewById(R.id.subtitle);
        if (subtitleText != null) {
            subtitle.setText(subtitleText);
        }
    }

    private void runOnUiHelperThread(Runnable runnable) {
        if (!isResumed()
                || getContentViewAlphaForSwipeProgress() <= ANIMATION_PAUSE_ALPHA_THRESHOLD) {
            return;
        }
        Executors.UI_HELPER_EXECUTOR.execute(runnable);
    }

    private void setUpBackgroundAnimation(boolean forTablet) {
        if (mVibrator == null || mIsExpressiveThemeEnabledInSUW) {
            return;
        }
        boolean supportsThud = mVibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD);

        if (!supportsThud && !mVibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK)) {
            return;
        }
        if (mBackgroundAnimatorListener == null) {
            VibrationEffect vibrationEffect = VibrationEffect.startComposition()
                    .addPrimitive(supportsThud
                                    ? VibrationEffect.Composition.PRIMITIVE_THUD
                                    : VibrationEffect.Composition.PRIMITIVE_TICK,
                            /* scale= */ forTablet ? 1.0f : 0.3f,
                            /* delay= */ 50)
                    .compose();

            mBackgroundAnimatorListener =
                    new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            runOnUiHelperThread(() -> mVibrator.vibrate(vibrationEffect));
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {
                            runOnUiHelperThread(() -> mVibrator.vibrate(vibrationEffect));
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            runOnUiHelperThread(mVibrator::cancel);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            runOnUiHelperThread(mVibrator::cancel);
                        }
                    };
        }
        mAnimatedBackground.addAnimatorListener(mBackgroundAnimatorListener);
    }

    private void setSetupUIVisible(boolean visible) {
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager == null) return;
        taskbarManager.setSetupUIVisible(visible);
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeResumeOrPauseBackgroundAnimation();
        TISBinder binder = mTISBindHelper.getBinder();
        if (binder != null) {
            setSetupUIVisible(true);
            binder.setSwipeUpProxy(this::createSwipeUpProxy);
        }
        if (mIsExpressiveThemeEnabledInSUW) {
            getWindow().setBackgroundBlurRadius(WALLPAPER_BLUR_RADIUS);
            if (Flags.enableNewAllSetAnimation() && binder != null) {
                int height = getWindowManager().getCurrentWindowMetrics().getBounds().height();
                binder.setGesturalHeight((int) (height * GESTURE_HEIGHT_RATIO_OF_WINDOW_HEIGHT));
            }
        }
        setUIControllerChangeListener(this);
    }

    private void onTISConnected(TISBinder binder) {
        setSetupUIVisible(isResumed());
        binder.setSwipeUpProxy(isResumed() ? this::createSwipeUpProxy : null);
        if (mIsExpressiveThemeEnabledInSUW && Flags.enableNewAllSetAnimation() && isResumed()) {
            int height = getWindowManager().getCurrentWindowMetrics().getBounds().height();
            binder.setGesturalHeight((int) (height * GESTURE_HEIGHT_RATIO_OF_WINDOW_HEIGHT));
        }

        setUIControllerChangeListener(this);
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager != null) {
            // Initial call
            onUIControllerChanged(
                    taskbarManager.getUIControllerForDisplay(taskbarManager.getPrimaryDisplayId()));
        }
    }

    private void setUIControllerChangeListener(UIControllerChangeListener listener) {
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager != null) {
            TaskbarActivityContext context = taskbarManager.getCurrentActivityContext();
            if (context != null) {
                context.setUIControllerChangeListener(listener);
            }
        }
    }

    @Override
    public void onUIControllerChanged(TaskbarUIController uiController) {
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager != null) {
            mLauncherStartAnim = taskbarManager.createLauncherStartFromSuwAnim(MAX_SWIPE_DURATION);
            if (mWallpaperClipPath != null) {
                mWallpaperClipPath.setForceFallbackAnimation(
                        taskbarManager.shouldForceAllSetFallbackAnimation());
            }
        }
    }

    private void onOverviewTargetChange(boolean isHomeAndOverviewSame) {
        ActivityPreloadUtil.preloadOverviewForSUWAllSet(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearBinderOverride();
        maybeResumeOrPauseBackgroundAnimation();
        if (mSwipeProgress.value >= 1) {
            finishAndRemoveTask();
            dispatchLauncherAnimStartEnd();
        }
        setUIControllerChangeListener(null);
    }

    private void clearBinderOverride() {
        TISBinder binder = mTISBindHelper.getBinder();
        if (binder != null) {
            setSetupUIVisible(false);
            binder.setSwipeUpProxy(null);
            if (mIsExpressiveThemeEnabledInSUW && Flags.enableNewAllSetAnimation()) {
                binder.setGesturalHeight(RESET_TO_DEFAULT_GESTURAL_HEIGHT);
            }
        }
    }

    /**
     * Should be called when we have successfully reached Launcher, so we dispatch to animation
     * listeners to ensure the state matches the visual animation that just occurred.
      */
    private void dispatchLauncherAnimStartEnd() {
        if (mLauncherStartAnim != null) {
            mLauncherStartAnim.dispatchOnStart();
            mLauncherStartAnim.dispatchOnEnd();
            mLauncherStartAnim = null;
        }
        sendBroadcast(new Intent(INTENT_ACTION_ACTIVITY_CLOSED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getIDP().removeOnChangeListener(mOnIDPChangeListener);
        setUIControllerChangeListener(null);
        mTISBindHelper.onDestroy();
        clearBinderOverride();
        if (mBackgroundAnimatorListener != null) {
            mAnimatedBackground.removeAnimatorListener(mBackgroundAnimatorListener);
        }
        if (!isChangingConfigurations()) {
            dispatchLauncherAnimStartEnd();
        }
        OverviewComponentObserver.INSTANCE.get(this)
                .removeOverviewChangeListener(mOverviewChangeListener);
    }

    private AnimatedFloat createSwipeUpProxy(GestureState state) {
        if (state.getTopRunningTaskId() != getTaskId()) {
            return null;
        }
        mSwipeProgress.updateValue(0);
        return mSwipeProgress;
    }

    private float getContentViewAlphaForSwipeProgress() {
        return Utilities.mapBoundToRange(
                mSwipeProgress.value, 0, HINT_BOTTOM_FACTOR, 1, 0, LINEAR);
    }

    private void maybeResumeOrPauseBackgroundAnimation() {
        if (mIsExpressiveThemeEnabledInSUW) {
            return;
        }
        boolean shouldPlayAnimation =
                !RemoveAnimationSettingsTracker.INSTANCE.get(this).isRemoveAnimationEnabled()
                        && getContentViewAlphaForSwipeProgress() > ANIMATION_PAUSE_ALPHA_THRESHOLD
                        && isResumed()
                        && mBackgroundAnimationToggledOn;
        if (mAnimatedBackground.isAnimating() && !shouldPlayAnimation) {
            mAnimatedBackground.pauseAnimation();
        } else if (!mAnimatedBackground.isAnimating() && shouldPlayAnimation) {
            mAnimatedBackground.resumeAnimation();
        }
    }

    private void onSwipeProgressUpdate() {
        if (mIsExpressiveThemeEnabledInSUW) {
            getWindow().setBackgroundBlurRadius((int) mapBoundToRange(
                    mSwipeProgress.value, 0, HINT_BOTTOM_FACTOR, WALLPAPER_BLUR_RADIUS, 0,
                    ACCELERATE));
            if (mExpressiveAnimSet != null) {
                long progress = (long) mapToRange(
                        mSwipeProgress.value, 0, 1, 0, CLIP_ANIM_DURATION, LINEAR);
                mExpressiveAnimSet.setCurrentPlayTime(Math.min(CLIP_ANIM_DURATION, progress));
            }
        } else {
            mBackground.setProgress(mSwipeProgress.value);

            float alpha = getContentViewAlphaForSwipeProgress();
            mRootView.setAlpha(alpha);
            mRootView.setTranslationY((alpha - 1) * mSwipeUpShift);
        }
        if (mLauncherStartAnim != null) {
            mLauncherStartAnim.setPlayFraction(
                    FAST_OUT_SLOW_IN.getInterpolation(mSwipeProgress.value));
        }
        maybeResumeOrPauseBackgroundAnimation();
    }

    /**
     * Accessibility delegate which exposes a click event without making the view
     * clickable in touch mode
     */
    private class SkipButtonAccessibilityDelegate extends AccessibilityDelegate {

        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(View host) {
            AccessibilityNodeInfo info = super.createAccessibilityNodeInfo(host);
            info.addAction(AccessibilityAction.ACTION_CLICK);
            info.setClickable(true);
            return info;
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            if (action == AccessibilityAction.ACTION_CLICK.getId()) {
                startHomeIntentSafely(AllSetActivity.this, null, TAG, getDisplayId());
                finish();
                return true;
            }
            return super.performAccessibilityAction(host, action, args);
        }
    }

    private static class BgDrawable extends Drawable {

        private static final float START_SIZE_FACTOR = .5f;
        private static final float END_SIZE_FACTOR = 2;
        private static final float GRADIENT_END_PROGRESS = .5f;

        private final Paint mPaint = new Paint();
        private final RadialGradient mMaskGrad;
        private final Matrix mMatrix = new Matrix();

        private final ColorMatrix mColorMatrix = new ColorMatrix();
        private final ColorMatrixColorFilter mColorFilter =
                new ColorMatrixColorFilter(mColorMatrix);

        private final int mColor;
        private float mProgress = 0;

        BgDrawable(Context context) {
            mColor = context.getColor(R.color.all_set_page_background);
            mMaskGrad = new RadialGradient(0, 0, 1,
                    new int[] {ColorUtils.setAlphaComponent(mColor, 0), mColor},
                    new float[]{0, 1}, TileMode.CLAMP);

            mPaint.setShader(mMaskGrad);
            mPaint.setColorFilter(mColorFilter);
        }

        @Override
        public void draw(Canvas canvas) {
            if (mProgress <= 0) {
                canvas.drawColor(mColor);
                return;
            }

            // Update the progress to half the size only.
            float progress = mapBoundToRange(mProgress,
                    0, GRADIENT_END_PROGRESS, 0, 1, LINEAR);
            Rect bounds = getBounds();
            float x = bounds.exactCenterX();
            float height = bounds.height();

            float size = PointF.length(x, height);
            float radius = size * mapRange(progress, START_SIZE_FACTOR, END_SIZE_FACTOR);
            float y = mapRange(progress, height + radius , height / 2);
            mMatrix.setTranslate(x, y);
            mMatrix.postScale(radius, radius, x, y);
            mMaskGrad.setLocalMatrix(mMatrix);

            // Change the alpha-addition-component (index 19) so that every pixel is updated
            // accordingly
            mColorMatrix.getArray()[19] = mapBoundToRange(mProgress, 0, 1, 0, -255, LINEAR);
            mColorFilter.setColorMatrix(mColorMatrix);

            canvas.drawPaint(mPaint);
        }

        public void setProgress(float progress) {
            if (mProgress != progress) {
                mProgress = progress;
                invalidateSelf();
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setAlpha(int i) { }

        @Override
        public void setColorFilter(ColorFilter colorFilter) { }
    }
}
