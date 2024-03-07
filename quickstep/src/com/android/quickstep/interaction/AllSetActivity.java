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

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import static com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Utilities.mapBoundToRange;
import static com.android.launcher3.Utilities.mapRange;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.util.Executors;
import com.android.quickstep.GestureState;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.util.LottieAnimationColorUtils;
import com.android.quickstep.util.TISBindHelper;

import com.airbnb.lottie.LottieAnimationView;

import java.net.URISyntaxException;
import java.util.Map;

/**
 * A page shows after SUW flow to hint users to swipe up from the bottom of the screen to go home
 * for the gestural system navigation.
 */
public class AllSetActivity extends Activity {
    private static final String TAG = "AllSetActivity";

    private static final String LOG_TAG = "AllSetActivity";
    private static final String URI_SYSTEM_NAVIGATION_SETTING =
            "#Intent;action=com.android.settings.SEARCH_RESULT_TRAMPOLINE;S.:settings:fragment_args_key=gesture_system_navigation_input_summary;S.:settings:show_fragment=com.android.settings.gestures.SystemNavigationGestureSettings;end";
    private static final String EXTRA_ACCENT_COLOR_DARK_MODE = "suwColorAccentDark";
    private static final String EXTRA_ACCENT_COLOR_LIGHT_MODE = "suwColorAccentLight";
    private static final String EXTRA_DEVICE_NAME = "suwDeviceName";

    private static final String LOTTIE_PRIMARY_COLOR_TOKEN = ".primary";
    private static final String LOTTIE_TERTIARY_COLOR_TOKEN = ".tertiary";

    private static final float HINT_BOTTOM_FACTOR = 1 - .94f;

    private static final int MAX_SWIPE_DURATION = 350;

    private static final float ANIMATION_PAUSE_ALPHA_THRESHOLD = 0.1f;

    private final AnimatedFloat mSwipeProgress = new AnimatedFloat(this::onSwipeProgressUpdate);

    private TISBindHelper mTISBindHelper;

    private BgDrawable mBackground;
    private View mRootView;
    private float mSwipeUpShift;

    @Nullable private Vibrator mVibrator;
    private LottieAnimationView mAnimatedBackground;
    private Animator.AnimatorListener mBackgroundAnimatorListener;

    private AnimatorPlaybackController mLauncherStartAnim = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_allset);
        mRootView = findViewById(R.id.root_view);
        mRootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        Resources resources = getResources();
        int mode = resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = mode == Configuration.UI_MODE_NIGHT_YES;

        int systemBarsMask = APPEARANCE_LIGHT_STATUS_BARS | APPEARANCE_LIGHT_NAVIGATION_BARS;
        int systemBarsAppearance = isDarkTheme ? 0 : systemBarsMask;
        Window window = getWindow();
        WindowInsetsController insetsController = window == null
                ? null
                : window.getInsetsController();
        if (insetsController != null) {
            insetsController.setSystemBarsAppearance(systemBarsAppearance, systemBarsMask);
        }

        Intent intent = getIntent();
        int accentColor = intent.getIntExtra(
                isDarkTheme ? EXTRA_ACCENT_COLOR_DARK_MODE : EXTRA_ACCENT_COLOR_LIGHT_MODE,
                isDarkTheme ? Color.WHITE : Color.BLACK);

        ((ImageView) findViewById(R.id.icon)).getDrawable().mutate().setTint(accentColor);

        mBackground = new BgDrawable(this);
        mRootView.setBackground(mBackground);
        mSwipeUpShift = resources.getDimension(R.dimen.allset_swipe_up_shift);

        TextView subtitle = findViewById(R.id.subtitle);
        String suwDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
        subtitle.setText(getString(
                R.string.allset_description_generic,
                !TextUtils.isEmpty(suwDeviceName)
                        ? suwDeviceName : getString(R.string.default_device_name)));

        TextView settings = findViewById(R.id.navigation_settings);
        settings.setTextColor(accentColor);
        settings.setOnClickListener(v -> {
            try {
                startActivityForResult(
                        Intent.parseUri(URI_SYSTEM_NAVIGATION_SETTING, 0), 0);
            } catch (URISyntaxException e) {
                Log.e(LOG_TAG, "Failed to parse system nav settings intent", e);
            }
        });

        TextView hint = findViewById(R.id.hint);
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(this).getDeviceProfile(this);
        if (!dp.isGestureMode) {
            hint.setText(R.string.allset_button_hint);
        }
        hint.setAccessibilityDelegate(new SkipButtonAccessibilityDelegate());

        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);

        mVibrator = getSystemService(Vibrator.class);
        mAnimatedBackground = findViewById(R.id.animated_background);
        // There's a bug in the currently used external Lottie library (v5.2.0), and it doesn't load
        // the correct animation from the raw resources when configuration changes, so we need to
        // manually load the resource and pass it to Lottie.
        mAnimatedBackground.setAnimation(resources.openRawResource(R.raw.all_set_page_bg),
                null);

        LottieAnimationColorUtils.updateToColorResources(
                mAnimatedBackground,
                Map.of(LOTTIE_PRIMARY_COLOR_TOKEN, R.color.all_set_bg_primary,
                        LOTTIE_TERTIARY_COLOR_TOKEN, R.color.all_set_bg_tertiary),
                getTheme());

        startBackgroundAnimation(dp.isTablet);
    }

    private void runOnUiHelperThread(Runnable runnable) {
        if (!isResumed()
                || getContentViewAlphaForSwipeProgress() <= ANIMATION_PAUSE_ALPHA_THRESHOLD) {
            return;
        }
        Executors.UI_HELPER_EXECUTOR.execute(runnable);
    }

    private void startBackgroundAnimation(boolean forTablet) {
        if (!Utilities.ATLEAST_S || mVibrator == null) {
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
        mAnimatedBackground.playAnimation();
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
    }

    private void onTISConnected(TISBinder binder) {
        setSetupUIVisible(isResumed());
        binder.setSwipeUpProxy(isResumed() ? this::createSwipeUpProxy : null);
        binder.setOverviewTargetChangeListener(binder::preloadOverviewForSUWAllSet);
        binder.preloadOverviewForSUWAllSet();
        TaskbarManager taskbarManager = binder.getTaskbarManager();
        if (taskbarManager != null) {
            mLauncherStartAnim = taskbarManager.createLauncherStartFromSuwAnim(MAX_SWIPE_DURATION);
        }
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
    }

    private void clearBinderOverride() {
        TISBinder binder = mTISBindHelper.getBinder();
        if (binder != null) {
            setSetupUIVisible(false);
            binder.setSwipeUpProxy(null);
            binder.setOverviewTargetChangeListener(null);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTISBindHelper.onDestroy();
        clearBinderOverride();
        if (mBackgroundAnimatorListener != null) {
            mAnimatedBackground.removeAnimatorListener(mBackgroundAnimatorListener);
        }
        if (!isChangingConfigurations()) {
            dispatchLauncherAnimStartEnd();
        }
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
        boolean shouldPlayAnimation =
                getContentViewAlphaForSwipeProgress() > ANIMATION_PAUSE_ALPHA_THRESHOLD
                        && isResumed();
        if (mAnimatedBackground.isAnimating() && !shouldPlayAnimation) {
            mAnimatedBackground.pauseAnimation();
        } else if (!mAnimatedBackground.isAnimating() && shouldPlayAnimation) {
            mAnimatedBackground.resumeAnimation();
        }
    }

    private void onSwipeProgressUpdate() {
        mBackground.setProgress(mSwipeProgress.value);
        float alpha = getContentViewAlphaForSwipeProgress();
        mRootView.setAlpha(alpha);
        mRootView.setTranslationY((alpha - 1) * mSwipeUpShift);

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
                startHomeIntentSafely(AllSetActivity.this, null, TAG);
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
