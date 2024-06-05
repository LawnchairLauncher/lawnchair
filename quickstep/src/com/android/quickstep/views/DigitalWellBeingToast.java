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
import static com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.AppUsageLimit;
import android.graphics.Outline;
import android.graphics.Paint;
import android.icu.text.MeasureFormat;
import android.icu.text.MeasureFormat.FormatWidth;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.systemui.shared.recents.model.Task;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.Locale;

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
    @interface SplitBannerConfig {
    }

    static final Intent OPEN_APP_USAGE_SETTINGS_TEMPLATE = new Intent(ACTION_APP_USAGE_SETTINGS);
    static final int MINUTE_MS = 60000;

    private static final String TAG = "DigitalWellBeingToast";

    private final RecentsViewContainer mContainer;
    private final TaskView mTaskView;
    private final LauncherApps mLauncherApps;

    private final int mBannerHeight;

    private Task mTask;
    private boolean mHasLimit;

    private long mAppRemainingTimeMs;
    @Nullable
    private View mBanner;
    private ViewOutlineProvider mOldBannerOutlineProvider;
    private float mBannerOffsetPercentage;
    @Nullable
    private SplitBounds mSplitBounds;
    private float mSplitOffsetTranslationY;
    private float mSplitOffsetTranslationX;

    private boolean mIsDestroyed = false;

    public DigitalWellBeingToast(RecentsViewContainer container, TaskView taskView) {
        mContainer = container;
        mTaskView = taskView;
        mLauncherApps = container.asContext().getSystemService(LauncherApps.class);
        mBannerHeight = container.asContext().getResources().getDimensionPixelSize(
                R.dimen.digital_wellbeing_toast_height);
    }

    private void setNoLimit() {
        mHasLimit = false;
        mTaskView.setContentDescription(mTask.titleDescription);
        replaceBanner(null);
        mAppRemainingTimeMs = -1;
    }

    private void setLimit(long appUsageLimitTimeMs, long appRemainingTimeMs) {
        mAppRemainingTimeMs = appRemainingTimeMs;
        mHasLimit = true;
        TextView toast = mContainer.getViewCache().getView(R.layout.digital_wellbeing_toast,
                mContainer.asContext(), mTaskView);
        toast.setText(prefixTextWithIcon(mContainer.asContext(), R.drawable.ic_hourglass_top,
                getText()));
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
        if (mIsDestroyed) {
            throw new IllegalStateException("Cannot re-initialize a destroyed toast");
        }
        mTask = task;
        ORDERED_BG_EXECUTOR.execute(() -> {
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
                if (mIsDestroyed) {
                    return;
                }
                if (appUsageLimitTimeMs < 0 || appRemainingTimeMs < 0) {
                    setNoLimit();
                } else {
                    setLimit(appUsageLimitTimeMs, appRemainingTimeMs);
                }
            });
        });
    }

    /**
     * Mark the DWB toast as destroyed and remove banner from TaskView.
     */
    public void destroy() {
        mIsDestroyed = true;
        mTaskView.post(() -> replaceBanner(null));
    }

    public void setSplitBounds(@Nullable SplitBounds splitBounds) {
        mSplitBounds = splitBounds;
    }

    private @SplitBannerConfig int getSplitBannerConfig() {
        if (mSplitBounds == null
                || !mContainer.getDeviceProfile().isTablet
                || mTaskView.isFocusedTask()) {
            return SPLIT_BANNER_FULLSCREEN;
        }

        // For portrait grid only height of task changes, not width. So we keep the text the same
        if (!mContainer.getDeviceProfile().isLeftRightSplit) {
            return SPLIT_GRID_BANNER_LARGE;
        }

        // For landscape grid, for 30% width we only show icon, otherwise show icon and time
        if (mTask.key.id == mSplitBounds.leftTopTaskId) {
            return mSplitBounds.leftTaskPercent < THRESHOLD_LEFT_ICON_ONLY
                    ? SPLIT_GRID_BANNER_SMALL : SPLIT_GRID_BANNER_LARGE;
        } else {
            return mSplitBounds.leftTaskPercent > THRESHOLD_RIGHT_ICON_ONLY
                    ? SPLIT_GRID_BANNER_SMALL : SPLIT_GRID_BANNER_LARGE;
        }
    }

    private String getReadableDuration(
            Duration duration,
            @StringRes int durationLessThanOneMinuteStringId) {
        int hours = Math.toIntExact(duration.toHours());
        int minutes = Math.toIntExact(duration.minusHours(hours).toMinutes());

        // Apply FormatWidth.WIDE if both the hour part and the minute part are non-zero.
        if (hours > 0 && minutes > 0) {
            return MeasureFormat.getInstance(Locale.getDefault(), FormatWidth.NARROW)
                    .formatMeasures(
                            new Measure(hours, MeasureUnit.HOUR),
                            new Measure(minutes, MeasureUnit.MINUTE));
        }

        // Apply FormatWidth.WIDE if only the hour part is non-zero (unless forced).
        if (hours > 0) {
            return MeasureFormat.getInstance(Locale.getDefault(), FormatWidth.WIDE).formatMeasures(
                    new Measure(hours, MeasureUnit.HOUR));
        }

        // Apply FormatWidth.WIDE if only the minute part is non-zero (unless forced).
        if (minutes > 0) {
            return MeasureFormat.getInstance(Locale.getDefault(), FormatWidth.WIDE).formatMeasures(
                    new Measure(minutes, MeasureUnit.MINUTE));
        }

        // Use a specific string for usage less than one minute but non-zero.
        if (duration.compareTo(Duration.ZERO) > 0) {
            return mContainer.asContext().getString(durationLessThanOneMinuteStringId);
        }

        // Otherwise, return 0-minute string.
        return MeasureFormat.getInstance(Locale.getDefault(), FormatWidth.WIDE).formatMeasures(
                new Measure(0, MeasureUnit.MINUTE));
    }

    /**
     * Returns text to show for the banner depending on {@link #getSplitBannerConfig()}
     * If {@param forContentDesc} is {@code true}, this will always return the full
     * string corresponding to {@link #SPLIT_BANNER_FULLSCREEN}
     */
    private String getText(long remainingTime, boolean forContentDesc) {
        final Duration duration = Duration.ofMillis(
                remainingTime > MINUTE_MS ?
                        (remainingTime + MINUTE_MS - 1) / MINUTE_MS * MINUTE_MS :
                        remainingTime);
        String readableDuration = getReadableDuration(duration,
                R.string.shorter_duration_less_than_one_minute
                /* forceFormatWidth */);
        @SplitBannerConfig int splitBannerConfig = getSplitBannerConfig();
        if (forContentDesc || splitBannerConfig == SPLIT_BANNER_FULLSCREEN) {
            return mContainer.asContext().getString(
                    R.string.time_left_for_app,
                    readableDuration);
        }

        if (splitBannerConfig == SPLIT_GRID_BANNER_SMALL) {
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
            final RecentsViewContainer container =
                    RecentsViewContainer.containerFromContext(view.getContext());
            final ActivityOptions options = ActivityOptions.makeScaleUpAnimation(
                    view, 0, 0,
                    view.getWidth(), view.getHeight());
            container.asContext().startActivity(intent, options.toBundle());

            // TODO: add WW logging on the app usage settings click.
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to open app usage settings for task "
                    + mTask.getTopComponent().getPackageName(), e);
        }
    }

    private String getContentDescriptionForTask(
            Task task, long appUsageLimitTimeMs, long appRemainingTimeMs) {
        return appUsageLimitTimeMs >= 0 && appRemainingTimeMs >= 0 ?
                mContainer.asContext().getString(
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
            mContainer.getViewCache().recycleView(R.layout.digital_wellbeing_toast, mBanner);
        }
    }

    private void setBanner(@Nullable View view) {
        mBanner = view;
        if (mBanner != null && mTaskView.getRecentsView() != null) {
            setupAndAddBanner();
            setBannerOutline();
        }
    }

    private void setupAndAddBanner() {
        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) mBanner.getLayoutParams();
        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        layoutParams.bottomMargin = ((ViewGroup.MarginLayoutParams)
                mTaskView.getFirstThumbnailViewDeprecated().getLayoutParams()).bottomMargin;
        RecentsPagedOrientationHandler orientationHandler = mTaskView.getPagedOrientationHandler();
        Pair<Float, Float> translations = orientationHandler
                .getDwbLayoutTranslations(mTaskView.getMeasuredWidth(),
                        mTaskView.getMeasuredHeight(), mSplitBounds, deviceProfile,
                        mTaskView.getThumbnailViews(), mTask.key.id, mBanner);
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
        if (mBannerOffsetPercentage != offsetPercentage) {
            mBannerOffsetPercentage = offsetPercentage;
            if (mBanner != null) {
                updateTranslationY();
                mBanner.invalidateOutline();
            }
        }
    }

    private void updateTranslationY() {
        if (mBanner == null) {
            return;
        }

        mBanner.setTranslationY(
                (mBannerOffsetPercentage * mBannerHeight) + mSplitOffsetTranslationY);
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

    private int getAccessibilityActionId() {
        return (mSplitBounds != null
                && mSplitBounds.rightBottomTaskId == mTask.key.id)
                ? R.id.action_digital_wellbeing_bottom_right
                : R.id.action_digital_wellbeing_top_left;
    }

    @Nullable
    public AccessibilityNodeInfo.AccessibilityAction getDWBAccessibilityAction() {
        if (!hasLimit()) {
            return null;
        }

        Context context = mContainer.asContext();
        String label =
                (mTaskView.containsMultipleTasks())
                        ? context.getString(
                        R.string.split_app_usage_settings,
                        TaskUtils.getTitle(context, mTask)
                ) : context.getString(R.string.accessibility_app_usage_settings);
        return new AccessibilityNodeInfo.AccessibilityAction(getAccessibilityActionId(), label);
    }

    public boolean handleAccessibilityAction(int action) {
        if (getAccessibilityActionId() == action) {
            openAppUsageSettings(mTaskView);
            return true;
        } else {
            return false;
        }
    }
}
