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
import android.icu.text.MeasureFormat;
import android.icu.text.MeasureFormat.FormatWidth;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.systemui.shared.recents.model.Task;

import java.time.Duration;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.Q)
public final class DigitalWellBeingToast {
    static final Intent OPEN_APP_USAGE_SETTINGS_TEMPLATE = new Intent(ACTION_APP_USAGE_SETTINGS);
    static final int MINUTE_MS = 60000;

    private static final String TAG = DigitalWellBeingToast.class.getSimpleName();

    private final BaseDraggingActivity mActivity;
    private final TaskView mTaskView;
    private final LauncherApps mLauncherApps;

    private Task mTask;
    private boolean mHasLimit;
    private long mAppRemainingTimeMs;

    public DigitalWellBeingToast(BaseDraggingActivity activity, TaskView taskView) {
        mActivity = activity;
        mTaskView = taskView;
        mLauncherApps = activity.getSystemService(LauncherApps.class);
    }

    private void setTaskFooter(View view) {
        View oldFooter = mTaskView.setFooter(TaskView.INDEX_DIGITAL_WELLBEING_TOAST, view);
        if (oldFooter != null) {
            oldFooter.setOnClickListener(null);
            mActivity.getViewCache().recycleView(R.layout.digital_wellbeing_toast, oldFooter);
        }
    }

    private void setNoLimit() {
        mHasLimit = false;
        mTaskView.setContentDescription(mTask.titleDescription);
        setTaskFooter(null);
        mAppRemainingTimeMs = 0;
    }

    private void setLimit(long appUsageLimitTimeMs, long appRemainingTimeMs) {
        mAppRemainingTimeMs = appRemainingTimeMs;
        mHasLimit = true;
        TextView toast = mActivity.getViewCache().getView(R.layout.digital_wellbeing_toast,
                mActivity, mTaskView);
        toast.setText(prefixTextWithIcon(mActivity, R.drawable.ic_hourglass_top, getText()));
        toast.setOnClickListener(this::openAppUsageSettings);
        setTaskFooter(toast);

        mTaskView.setContentDescription(
                getContentDescriptionForTask(mTask, appUsageLimitTimeMs, appRemainingTimeMs));
        RecentsView rv = mTaskView.getRecentsView();
        if (rv != null) {
            rv.onDigitalWellbeingToastShown();
        }
    }

    public String getText() {
        return getText(mAppRemainingTimeMs);
    }

    public boolean hasLimit() {
        return mHasLimit;
    }

    public void initialize(Task task) {
        mTask = task;

        if (task.key.userId != UserHandle.myUserId()) {
            setNoLimit();
            return;
        }

        THREAD_POOL_EXECUTOR.execute(() -> {
            final AppUsageLimit usageLimit = mLauncherApps.getAppUsageLimit(
                    task.getTopComponent().getPackageName(),
                    UserHandle.of(task.key.userId));

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
            return mActivity.getString(durationLessThanOneMinuteStringId);
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
        return mActivity.getString(
                R.string.time_left_for_app,
                getRoundedUpToMinuteReadableDuration(remainingTime));
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
            activity.getUserEventDispatcher().logActionOnControl(LauncherLogProto.Action.Touch.TAP,
                    LauncherLogProto.ControlType.APP_USAGE_SETTINGS, view);
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
                        getText(appRemainingTimeMs)) :
                task.titleDescription;
    }
}
