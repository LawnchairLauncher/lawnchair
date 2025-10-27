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

package com.android.quickstep.inputconsumers;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.NavHandle;
import com.android.quickstep.util.TestExtensions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NavHandleLongPressHandlerTest {

    private NavHandleLongPressHandler mLongPressHandler;
    @Mock private NavHandle mNavHandle;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLongPressHandler = new NavHandleLongPressHandler(context);
    }

    @Test
    public void testStartNavBarAnimation_flagDisabled() {
        try (AutoCloseable flag = overrideAnimateLPNHFlag(false)) {
            mLongPressHandler.startNavBarAnimation(mNavHandle);
            verify(mNavHandle, never())
                    .animateNavBarLongPress(anyBoolean(), anyBoolean(), anyLong());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testStartNavBarAnimation_flagEnabled() {
        try (AutoCloseable flag = overrideAnimateLPNHFlag(true)) {
            mLongPressHandler.startNavBarAnimation(mNavHandle);
            verify(mNavHandle).animateNavBarLongPress(anyBoolean(), anyBoolean(), anyLong());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AutoCloseable overrideAnimateLPNHFlag(boolean value) {
        return TestExtensions.overrideNavConfigFlag(
                "ANIMATE_LPNH", value, () -> DeviceConfigWrapper.get().getAnimateLpnh());
    }
}
