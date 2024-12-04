/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.splitscreen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;
import android.graphics.Rect;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.split.SplitScreenUtils;

import org.junit.Test;
import org.junit.runner.RunWith;


/** Tests for {@link com.android.wm.shell.common.split.SplitScreenUtils} */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SplitScreenUtilsTests extends ShellTestCase {

    @Test
    public void testIsLeftRightSplit() {
        Configuration portraitTablet = new Configuration();
        portraitTablet.smallestScreenWidthDp = 720;
        portraitTablet.windowConfiguration.setMaxBounds(new Rect(0, 0, 500, 1000));
        Configuration landscapeTablet = new Configuration();
        landscapeTablet.smallestScreenWidthDp = 720;
        landscapeTablet.windowConfiguration.setMaxBounds(new Rect(0, 0, 1000, 500));
        Configuration portraitPhone = new Configuration();
        portraitPhone.smallestScreenWidthDp = 420;
        portraitPhone.windowConfiguration.setMaxBounds(new Rect(0, 0, 500, 1000));
        Configuration landscapePhone = new Configuration();
        landscapePhone.smallestScreenWidthDp = 420;
        landscapePhone.windowConfiguration.setMaxBounds(new Rect(0, 0, 1000, 500));

        // Allow L/R split in portrait = false
        assertTrue(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                landscapeTablet));
        assertTrue(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                landscapePhone));
        assertFalse(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                portraitTablet));
        assertFalse(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                portraitPhone));

        // Allow L/R split in portrait = true, only affects large screens
        assertFalse(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                landscapeTablet));
        assertTrue(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                landscapePhone));
        assertTrue(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                portraitTablet));
        assertFalse(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                portraitPhone));
    }
}
