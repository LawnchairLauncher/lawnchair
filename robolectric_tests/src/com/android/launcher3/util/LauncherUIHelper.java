/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.util;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.view.View;
import android.view.WindowManager;

import com.android.launcher3.Launcher;

import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;

/**
 * Utility class to help manage Launcher UI and related objects for test.
 */
public class LauncherUIHelper {

    /**
     * Returns the class name for the Launcher activity as defined in the manifest
     */
    public static String getLauncherClassName() {
        Context context = RuntimeEnvironment.application;
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        List<ResolveInfo> launchers = context.getPackageManager()
                .queryIntentActivities(homeIntent, 0);
        if (launchers.size() != 1) {
            return null;
        }
        return launchers.get(0).activityInfo.name;
    }

    /**
     * Returns an activity controller for Launcher activity defined in the manifest
     */
    public static <T extends Launcher> ActivityController<T> buildLauncher() {
        try {
            Class<T> tClass = (Class<T>) Class.forName(getLauncherClassName());
            return Robolectric.buildActivity(tClass);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates and binds a Launcher activity defined in the manifest.
     * Note that the model must be bound before calling this
     */
    public static <T extends Launcher> T buildAndBindLauncher() {
        ActivityController<T> controller = buildLauncher();

        T launcher = controller.setup().get();
        doLayout(launcher);
        ViewOnDrawExecutor executor = ReflectionHelpers.getField(launcher, "mPendingExecutor");
        if (executor != null) {
            executor.runAllTasks();
        }
        return launcher;
    }

    /**
     * Performs a measure and layout pass for the given activity
     */
    public static void doLayout(Activity activity) {
        Point size = new Point();
        RuntimeEnvironment.application.getSystemService(WindowManager.class)
                .getDefaultDisplay().getSize(size);
        View view = activity.getWindow().getDecorView();
        view.measure(makeMeasureSpec(size.x, EXACTLY), makeMeasureSpec(size.y, EXACTLY));
        view.layout(0, 0, size.x, size.y);
        ShadowLooper.idleMainLooper();
    }
}
