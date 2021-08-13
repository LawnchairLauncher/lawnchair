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

package com.android.launcher3.model;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.android.launcher3.R;

/**
 * A job to request AppShareabilityManager to update its shareability data
 * The shareability status of an app is not expected to change often, so this job is only
 * run periodically.
 */
public final class AppShareabilityJobService extends JobService {

    private static final String TAG = "AppShareabilityJobService";
    // Run this job once a week
    private static final int RECURRENCE_INTERVAL_MILLIS = 604800000;

    @Override
    public boolean onStartJob(final JobParameters params) {
        Context context = getApplicationContext();
        AppShareabilityManager.INSTANCE.get(context).requestFullUpdate();
        return false; // Job is finished
    }

    @Override
    public boolean onStopJob(final JobParameters params) {
        Log.d(TAG, "App shareability data update job stopped; id=" + params.getJobId()
                + ", reason="
                + JobParameters.getInternalReasonCodeDescription(params.getStopReason()));
        return true; // Reschedule the job
    }

    /**
     * Creates and schedules the job.
     * Does not schedule a duplicate job if one is already pending.
     * @param context The application context
     */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        final JobInfo pendingJob = jobScheduler.getPendingJob(R.integer.app_shareability_job_id);
        if (pendingJob != null) {
            // Don't schedule duplicate jobs
            return;
        }

        final JobInfo newJob = new JobInfo.Builder(R.integer.app_shareability_job_id,
                new ComponentName(context, AppShareabilityJobService.class))
                .setPeriodic(RECURRENCE_INTERVAL_MILLIS)
                .setPersisted(true)
                .setRequiresBatteryNotLow(true)
                .build();
        jobScheduler.schedule(newJob);
    }
}
