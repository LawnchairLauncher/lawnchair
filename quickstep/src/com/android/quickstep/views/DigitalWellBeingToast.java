/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep.views;

import static android.provider.Settings.ACTION_APP_USAGE_SETTINGS;

import static com.android.launcher3.Utilities.prefixTextWithIcon;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.AppUsageLimit;
import android.graphics.Outline;
import android.graphics.Paint;
import android.icu.text.MeasureFormat;
import android.icu.text.MeasureFormat.FormatWidth;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.systemui.shared.recents.model.Task;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.Q)
public final class DigitalWellBeingToast {

    private static final float THRESHOLD_LEFT_ICON_ONLY = 0.4f;
    private static final float THRESHOLD_RIGHT_ICON_ONLY = 0.6f;

    /** Will span entire width of taskView with full text */
    private static final int SPLIT_BANNER_FULLSCREEN = 0;
    /** Used for grid task view, only showing icon and time */
    private static final int SPLIT_GRID_BANNER_LARGE = 1;
    /** Used for grid task view, only showing icon */
    private static final int SPLIT_GRID_BANNER_SMALL = 2;
    @IntDef(value = {
            SPLIT_BANNER_FULLSCREEN,
            SPLIT_GRID_BANNER_LARGE,
            SPLIT_GRID_BANNER_SMALL,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface SPLIT_BANNER_CONFIG{}

    static final Intent OPEN_APP_USAGE_SETTINGS_TEMPLATE = new Intent(ACTION_APP_USAGE_SETTINGS);
    static final int MINUTE_MS = 60000;

    private static final String TAG = DigitalWellBeingToast.class.getSimpleName();

    private final BaseDraggingActivity mActivity;
    private final TaskView mTaskView;
    private final LauncherApps mLauncherApps;

    private Task mTask;
    private boolean mHasLimit;

    private long mAppUsageLimitTimeMs;
    private long mAppRemainingTimeMs;
    @Nullable
    private View mBanner;
    private ViewOutlineProvider mOldBannerOutlineProvider;
    private float mBannerOffsetPercentage;
    @Nullable
    private SplitBounds mSplitBounds;
    private int mSplitBannerConfig = SPLIT_BANNER_FULLSCREEN;
    private float mSplitOffsetTranslationY;
    private float mSplitOffsetTranslationX;

    public DigitalWellBeingToast(BaseDraggingActivity activity, TaskView taskView) {
        mActivity = activity;
        mTaskView = taskView;
        mLauncherApps = activity.getSystemService(LauncherApps.class);
    }

    private void setNoLimit() {
        mHasLimit = false;
        mTaskView.setContentDescription(mTask.titleDescription);
        replaceBanner(null);
        mAppUsageLimitTimeMs = -1;
        mAppRemainingTimeMs = -1;
    }

    private void setLimit(long appUsageLimitTimeMs, long appRemainingTimeMs) {
        mAppUsageLimitTimeMs = appUsageLimitTimeMs;
        mAppRemainingTimeMs = appRemainingTimeMs;
        mHasLimit = true;
        TextView toast = mActivity.getViewCache().getView(R.layout.digital_wellbeing_toast,
                mActivity, mTaskView);
        toast.setText(prefixTextWithIcon(mActivity, R.drawable.ic_hourglass_top, getText()));
        toast.setOnClickListener(this::openAppUsageSettings);
        replaceBanner(toast);

        mTaskView.setContentDescription(
                getContentDescriptionForTask(mTask, appUsageLimitTimeMs, appRemainingTimeMs));
    }

    public String getText() {
        return getText(mAppRemainingTimeMs, false /* forContentDesc */);
    }

    public boolean hasLimit() {
        return mHasLimit;
    }

    public void initialize(Task task) {
        mAppUsageLimitTimeMs = mAppRemainingTimeMs = -1;
        mTask = task;
        THREAD_POOL_EXECUTOR.execute(() -> {
                    AppUsageLimit usageLimit = null;
                    try {
                        usageLimit = mLauncherApps.getAppUsageLimit(
                                mTask.getTopComponent().getPackageName(),
                                UserHandle.of(mTask.key.userId));
                    } catch (Exception e) {
                        Log.e(TAG, "Error initializing digital well being toast", e);
                    }
                    final long appUsageLimitTimeMs =
                            usageLimit != null ? usageLimit.getTotalUsageLimit() : -1;
                    final long appRemainingTimeMs =
                            usageLimit != null ? usageLimit.getUsageRemaining() : -1;

                    mTaskView.post(() -> {
                        if (appUsageLimitTimeMs < 0 || appRemainingTimeMs < 0) {
                            setNoLimit();
                        } else {
                            setLimit(appUsageLimitTimeMs, appRemainingTimeMs);
                        }
                    });

                }
        );
    }

    public void setSplitConfiguration(SplitBounds splitBounds) {
        mSplitBounds = splitBounds;
        if (mSplitBounds == null
                || !mActivity.getDeviceProfile().isTablet
                || mTaskView.isFocusedTask()) {
            mSplitBannerConfig = SPLIT_BANNER_FULLSCREEN;
            return;
        }

        // For portrait grid only height of task changes, not width. So we keep the text the same
        if (!mActivity.getDeviceProfile().isLeftRightSplit) {
            mSplitBannerConfig = SPLIT_GRID_BANNER_LARGE;
            return;
        }

        // For landscape grid, for 30% width we only show icon, otherwise show icon and time
        if (mTask.key.id == mSplitBounds.leftTopTaskId) {
            mSplitBannerConfig = mSplitBounds.leftTaskPercent < THRESHOLD_LEFT_ICON_ONLY ?
                    SPLIT_GRID_BANNER_SMALL : SPLIT_GRID_BANNER_LARGE;
        } else {
            mSplitBannerConfig = mSplitBounds.leftTaskPercent > THRESHOLD_RIGHT_ICON_ONLY ?
                    SPLIT_GRID_BANNER_SMALL : SPLIT_GRID_BANNER_LARGE;
        }
    }

    private String getReadableDuration(
            Duration duration,
            FormatWidth formatWidthHourAndMinute,
            @StringRes int durationLessThanOneMinuteStringId,
            boolean forceFormatWidth) {
        int hours = Math.toIntExact(duration.toHours());
        int minutes = Math.toIntExact(duration.minusHours(hours).toMinutes());

        // Apply formatWidthHourAndMinute if both the hour part and the minute part are non-zero.
        if (hours > 0 && minutes > 0) {
            return MeasureFormat.getInstance(Locale.getDefault(), formatWidthHourAndMinute)
                    .formatMeasures(
                            new Measure(hours, MeasureUnit.HOUR),
                            new Measure(minutes, MeasureUnit.MINUTE));
        }

        // Apply formatWidthHourOrMinute if only the hour part is non-zero (unless forced).
        if (hours > 0) {
            return MeasureFormat.getInstance(
                    Locale.getDefault(),
                    forceFormatWidth ? formatWidthHourAndMinute : FormatWidth.WIDE)
                    .formatMeasures(new Measure(hours, MeasureUnit.HOUR));
        }

        // Apply formatWidthHourOrMinute if only the minute part is non-zero (unless forced).
        if (minutes > 0) {
            return MeasureFormat.getInstance(
                    Locale.getDefault()
                    , forceFormatWidth ? formatWidthHourAndMinute : FormatWidth.WIDE)
                    .formatMeasures(new Measure(minutes, MeasureUnit.MINUTE));
        }

        // Use a specific string for usage less than one minute but non-zero.
        if (duration.compareTo(Duration.ZERO) > 0) {
            return mActivity.getString(durationLessThanOneMinuteStringId);
        }

        // Otherwise, return 0-minute string.
        return MeasureFormat.getInstance(
                Locale.getDefault(), forceFormatWidth ? formatWidthHourAndMinute : FormatWidth.WIDE)
                .formatMeasures(new Measure(0, MeasureUnit.MINUTE));
    }

    /**
     * Returns text to show for the banner depending on {@link #mSplitBannerConfig}
     * If {@param forContentDesc} is {@code true}, this will always return the full
     * string corresponding to {@link #SPLIT_BANNER_FULLSCREEN}
     */
    private String getText(long remainingTime, boolean forContentDesc) {
        final Duration duration = Duration.ofMillis(
                remainingTime > MINUTE_MS ?
                        (remainingTime + MINUTE_MS - 1) / MINUTE_MS * MINUTE_MS :
                        remainingTime);
        String readableDuration = getReadableDuration(duration,
                FormatWidth.NARROW,
                R.string.shorter_duration_less_than_one_minute,
                false /* forceFormatWidth */);
        if (forContentDesc || mSplitBannerConfig == SPLIT_BANNER_FULLSCREEN) {
            return mActivity.getString(
                    R.string.time_left_for_app,
                    readableDuration);
        }

        if (mSplitBannerConfig == SPLIT_GRID_BANNER_SMALL) {
            // show no text
            return "";
        } else { // SPLIT_GRID_BANNER_LARGE
            // only show time
            return readableDuration;
        }
    }

    public void openAppUsageSettings(View view) {
        final Intent intent = new Intent(OPEN_APP_USAGE_SETTINGS_TEMPLATE)
                .putExtra(Intent.EXTRA_PACKAGE_NAME,
                        mTask.getTopComponent().getPackageName()).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            final BaseActivity activity = BaseActivity.fromContext(view.getContext());
            final ActivityOptions options = ActivityOptions.makeScaleUpAnimation(
                    view, 0, 0,
                    view.getWidth(), view.getHeight());
            activity.startActivity(intent, options.toBundle());

            // TODO: add WW logging on the app usage settings click.
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to open app usage settings for task "
                    + mTask.getTopComponent().getPackageName(), e);
        }
    }

    private String getContentDescriptionForTask(
            Task task, long appUsageLimitTimeMs, long appRemainingTimeMs) {
        return appUsageLimitTimeMs >= 0 && appRemainingTimeMs >= 0 ?
                mActivity.getString(
                        R.string.task_contents_description_with_remaining_time,
                        task.titleDescription,
                        getText(appRemainingTimeMs, true /* forContentDesc */)) :
                task.titleDescription;
    }

    private void replaceBanner(@Nullable View view) {
        resetOldBanner();
        setBanner(view);
    }

    private void resetOldBanner() {
        if (mBanner != null) {
            mBanner.setOutlineProvider(mOldBannerOutlineProvider);
            mTaskView.removeView(mBanner);
            mBanner.setOnClickListener(null);
            mActivity.getViewCache().recycleView(R.layout.digital_wellbeing_toast, mBanner);
        }
    }

    private void setBanner(@Nullable View view) {
        mBanner = view;
        if (view != null && mTaskView.getRecentsView() != null) {
            setupAndAddBanner();
            setBannerOutline();
        }
    }

    private void setupAndAddBanner() {
        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) mBanner.getLayoutParams();
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        layoutParams.bottomMargin = ((ViewGroup.MarginLayoutParams)
                mTaskView.getThumbnail().getLayoutParams()).bottomMargin;
        PagedOrientationHandler orientationHandler = mTaskView.getPagedOrientationHandler();
        Pair<Float, Float> translations = orientationHandler
                .getDwbLayoutTranslations(mTaskView.getMeasuredWidth(),
                        mTaskView.getMeasuredHeight(), mSplitBounds, deviceProfile,
                        mTaskView.getThumbnails(), mTask.key.id, mBanner);
        mSplitOffsetTranslationX = translations.first;
        mSplitOffsetTranslationY = translations.second;
        updateTranslationY();
        updateTranslationX();
        mTaskView.addView(mBanner);
    }

    private void setBannerOutline() {
        // TODO(b\273367585) to investigate why mBanner.getOutlineProvider() can be null
        mOldBannerOutlineProvider = mBanner.getOutlineProvider() != null
                ? mBanner.getOutlineProvider()
                : ViewOutlineProvider.BACKGROUND;

        mBanner.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                mOldBannerOutlineProvider.getOutline(view, outline);
                float verticalTranslation = -view.getTranslationY() + mSplitOffsetTranslationY;
                outline.offset(0, Math.round(verticalTranslation));
            }
        });
        mBanner.setClipToOutline(true);
    }

    void updateBannerOffset(float offsetPercentage) {
        if (mBanner != null && mBannerOffsetPercentage != offsetPercentage) {
            mBannerOffsetPercentage = offsetPercentage;
            updateTranslationY();
            mBanner.invalidateOutline();
        }
    }

    private void updateTranslationY() {
        if (mBanner == null) {
            return;
        }

        mBanner.setTranslationY(
                (mBannerOffsetPercentage * mBanner.getHeight()) + mSplitOffsetTranslationY);
    }

    private void updateTranslationX() {
        if (mBanner == null) {
            return;
        }

        mBanner.setTranslationX(mSplitOffsetTranslationX);
    }

    void setBannerColorTint(int color, float amount) {
        if (mBanner == null) {
            return;
        }
        if (amount == 0) {
            mBanner.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        Paint layerPaint = new Paint();
        layerPaint.setColorFilter(Utilities.makeColorTintingColorFilter(color, amount));
        mBanner.setLayerType(View.LAYER_TYPE_HARDWARE, layerPaint);
        mBanner.setLayerPaint(layerPaint);
    }

    void setBannerVisibility(int visibility) {
        if (mBanner == null) {
            return;
        }

        mBanner.setVisibility(visibility);
    }
}
