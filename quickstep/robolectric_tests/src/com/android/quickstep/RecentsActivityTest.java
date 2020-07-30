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
package com.android.quickstep;

import static com.android.launcher3.util.LauncherUIHelper.doLayout;

import android.app.ActivityManager.RunningTaskInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;


@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class RecentsActivityTest {

    @Test
    public void testRecentsActivityCreates() {
        ActivityController<RecentsActivity> controller =
                Robolectric.buildActivity(RecentsActivity.class);

        RecentsActivity launcher = controller.setup().get();
        doLayout(launcher);

        // TODO: Ensure that LauncherAppState is not created
    }

    @Test
    public void testRecents_showCurrentTask() {
        ActivityController<RecentsActivity> controller =
                Robolectric.buildActivity(RecentsActivity.class);

        RecentsActivity activity = controller.setup().get();
        doLayout(activity);

        FallbackRecentsView frv = activity.getOverviewPanel();

        RunningTaskInfo dummyTask = new RunningTaskInfo();
        dummyTask.taskId = 22;
        frv.showCurrentTask(dummyTask);
        doLayout(activity);

        ThumbnailData thumbnailData = new ThumbnailData();
        ReflectionHelpers.setField(thumbnailData, "thumbnail",
                Bitmap.createBitmap(300, 500, Config.ARGB_8888));
        frv.switchToScreenshot(thumbnailData, () -> { });
        ShadowLooper.idleMainLooper();
    }
}
