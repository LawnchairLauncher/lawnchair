/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.allapps;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.Flags.FLAG_WORK_SCHEDULER_IN_WORK_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.util.ActivityContextWrapper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WorkUtilityViewTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    private WorkUtilityView mVut;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = new ActivityContextWrapper(getApplicationContext(),
                com.android.launcher3.R.style.DynamicColorsBaseLauncherTheme);
        mVut = (WorkUtilityView) ViewGroup.inflate(context,
                com.android.launcher3.R.layout.work_mode_utility_view, null);
    }

    @Test
    @EnableFlags(FLAG_WORK_SCHEDULER_IN_WORK_PROFILE)
    public void testInflateFlagOn_visible() {
        WorkUtilityView workUtilityView = Mockito.spy(mVut);
        doReturn(true).when(workUtilityView).shouldUseScheduler();

        workUtilityView.onFinishInflate();

        assertThat(workUtilityView.getSchedulerButton().getVisibility()).isEqualTo(VISIBLE);
        assertThat(workUtilityView.getSchedulerButton().hasOnClickListeners()).isEqualTo(true);
    }

    @Test
    @DisableFlags(FLAG_WORK_SCHEDULER_IN_WORK_PROFILE)
    public void testInflateFlagOff_gone() {
        mVut.onFinishInflate();

        assertThat(mVut.getSchedulerButton().getVisibility()).isEqualTo(GONE);
        assertThat(mVut.getSchedulerButton().hasOnClickListeners()).isEqualTo(false);
    }
}
