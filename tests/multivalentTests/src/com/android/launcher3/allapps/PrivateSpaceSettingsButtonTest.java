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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PRIVATESPACE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.ActivityContextWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class PrivateSpaceSettingsButtonTest {

    private PrivateSpaceSettingsButton mVut;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = new ActivityContextWrapper(getApplicationContext());
        mVut = new PrivateSpaceSettingsButton(context);
    }

    @Test
    public void privateSpaceSettingsAppInfo_hasCorrectIdAndContainer() {
        AppInfo appInfo = mVut.createPrivateSpaceSettingsAppInfo();

        assertThat(appInfo.id).isEqualTo(CONTAINER_PRIVATESPACE);
        assertThat(appInfo.container).isEqualTo(CONTAINER_PRIVATESPACE);
    }
}
