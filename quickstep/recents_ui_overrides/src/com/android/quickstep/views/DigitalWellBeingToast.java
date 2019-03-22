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

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.AppUsageLimit;
import android.icu.text.MeasureFormat;
import android.icu.text.MeasureFormat.FormatWidth;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.systemui.shared.recents.model.Task;

import java.time.Duration;
import java.util.Locale;

public final class DigitalWellBeingToast extends LinearLayout {
    static final Intent OPEN_APP_USAGE_SETTINGS_TEMPLATE = new Intent(ACTION_APP_USAGE_SETTINGS);
    static final int MINUTE_MS = 60000;
    private final LauncherApps mLauncherApps;

    public interface InitializeCallback {
        void call(String contentDescription);
    }

    private static final String TAG = DigitalWellBeingToast.class.getSimpleName();

    private Task mTask;
    private ImageView mImage;
    private TextView mText;

    public DigitalWellBeingToast(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutDirection(Utilities.isRtl(getResources()) ?
                View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        setOnClickListener((view) -> openAppUsageSettings());
        mLauncherApps = context.getSystemService(LauncherApps.class);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mText = findViewById(R.id.digital_well_being_remaining_time);
        mImage = findViewById(R.id.digital_well_being_hourglass);
    }

    public void initialize(Task task, InitializeCallback callback) {
        mTask = task;

        if (task.key.userId != UserHandle.myUserId()) {
            setVisibility(GONE);
            callback.call(task.titleDescription);
            return;
        }

        Utilities.THREAD_POOL_EXECUTOR.execute(() -> {
            final AppUsageLimit usageLimit = mLauncherApps.getAppUsageLimit(
                    task.getTopComponent().getPackageName(),
                    UserHandle.of(task.key.userId));

            final long appUsageLimitTimeMs =
                    usageLimit != null ? usageLimit.getTotalUsageLimit() : -1;
            final long appRemainingTimeMs =
                    usageLimit != null ? usageLimit.getUsageRemaining() : -1;

            post(() -> {
                if (appUsageLimitTimeMs < 0 || appRemainingTimeMs < 0) {
                    setVisibility(GONE);
                } else {
                    setVisibility(VISIBLE);
                    mText.setText(getText(appRemainingTimeMs));
                    mImage.setImageResource(appRemainingTimeMs > 0 ?
                            R.drawable.hourglass_top : R.drawable.hourglass_bottom);
                }

                callback.call(getContentDescriptionForTask(
                        task, appUsageLimitTimeMs, appRemainingTimeMs));
            });
        });
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
            return getResources().getString(durationLessThanOneMinuteStringId);
        }

        // Otherwise, return 0-minute string.
        return MeasureFormat.getInstance(
                Locale.getDefault(), forceFormatWidth ? formatWidthHourAndMinute : FormatWidth.WIDE)
                .formatMeasures(new Measure(0, MeasureUnit.MINUTE));
    }

    private String getReadableDuration(
            Duration duration,
            FormatWidth formatWidthHourAndMinute,
            @StringRes int durationLessThanOneMinuteStringId) {
        return getReadableDuration(
                duration,
                formatWidthHourAndMinute,
                durationLessThanOneMinuteStringId,
                /* forceFormatWidth= */ false);
    }

    private String getRoundedUpToMinuteReadableDuration(long remainingTime) {
        final Duration duration = Duration.ofMillis(
                remainingTime > MINUTE_MS ?
                        (remainingTime + MINUTE_MS - 1) / MINUTE_MS * MINUTE_MS :
                        remainingTime);
        return getReadableDuration(
                duration, FormatWidth.NARROW, R.string.shorter_duration_less_than_one_minute);
    }

    private String getText(long remainingTime) {
        return getResources().getString(
                R.string.time_left_for_app,
                getRoundedUpToMinuteReadableDuration(remainingTime));
    }

    public void openAppUsageSettings() {
        final Intent intent = new Intent(OPEN_APP_USAGE_SETTINGS_TEMPLATE)
                .putExtra(Intent.EXTRA_PACKAGE_NAME,
                        mTask.getTopComponent().getPackageName()).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            final Launcher launcher = Launcher.getLauncher(getContext());
            final ActivityOptions options = ActivityOptions.makeScaleUpAnimation(
                    this, 0, 0,
                    getWidth(), getHeight());
            launcher.startActivity(intent, options.toBundle());
            launcher.getUserEventDispatcher().logActionOnControl(LauncherLogProto.Action.Touch.TAP,
                    LauncherLogProto.ControlType.APP_USAGE_SETTINGS, this);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to open app usage settings for task "
                    + mTask.getTopComponent().getPackageName(), e);
        }
    }

    private String getContentDescriptionForTask(
            Task task, long appUsageLimitTimeMs, long appRemainingTimeMs) {
        return appUsageLimitTimeMs >= 0 && appRemainingTimeMs >= 0 ?
                getResources().getString(
                        R.string.task_contents_description_with_remaining_time,
                        task.titleDescription,
                        getText(appRemainingTimeMs)) :
                task.titleDescription;
    }
}
