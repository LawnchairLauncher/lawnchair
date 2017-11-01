/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.ComponentName;
import android.test.InstrumentationTestCase;

import com.android.launcher3.AppInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link DefaultAppSearchAlgorithm}
 */
public class DefaultAppSearchAlgorithmTest extends InstrumentationTestCase {

    private List<AppInfo> mAppsList;
    private DefaultAppSearchAlgorithm mAlgorithm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAppsList = new ArrayList<>();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mAlgorithm = new DefaultAppSearchAlgorithm(mAppsList);
            }
        });
    }

    public void testMatches() {
        assertTrue(mAlgorithm.matches(getInfo("white cow"), "cow"));
        assertTrue(mAlgorithm.matches(getInfo("whiteCow"), "cow"));
        assertTrue(mAlgorithm.matches(getInfo("whiteCOW"), "cow"));
        assertTrue(mAlgorithm.matches(getInfo("whitecowCOW"), "cow"));
        assertTrue(mAlgorithm.matches(getInfo("white2cow"), "cow"));

        assertFalse(mAlgorithm.matches(getInfo("whitecow"), "cow"));
        assertFalse(mAlgorithm.matches(getInfo("whitEcow"), "cow"));

        assertTrue(mAlgorithm.matches(getInfo("whitecowCow"), "cow"));
        assertTrue(mAlgorithm.matches(getInfo("whitecow cow"), "cow"));
        assertFalse(mAlgorithm.matches(getInfo("whitecowcow"), "cow"));
        assertFalse(mAlgorithm.matches(getInfo("whit ecowcow"), "cow"));

        assertTrue(mAlgorithm.matches(getInfo("cats&dogs"), "dog"));
        assertTrue(mAlgorithm.matches(getInfo("cats&Dogs"), "dog"));
        assertTrue(mAlgorithm.matches(getInfo("cats&Dogs"), "&"));

        assertTrue(mAlgorithm.matches(getInfo("2+43"), "43"));
        assertFalse(mAlgorithm.matches(getInfo("2+43"), "3"));

        assertTrue(mAlgorithm.matches(getInfo("Q"), "q"));
        assertTrue(mAlgorithm.matches(getInfo("  Q"), "q"));

        // match lower case words
        assertTrue(mAlgorithm.matches(getInfo("elephant"), "e"));

        assertTrue(mAlgorithm.matches(getInfo("电子邮件"), "电"));
        assertTrue(mAlgorithm.matches(getInfo("电子邮件"), "电子"));
        assertFalse(mAlgorithm.matches(getInfo("电子邮件"), "子"));
        assertFalse(mAlgorithm.matches(getInfo("电子邮件"), "邮件"));
    }

    private AppInfo getInfo(String title) {
        AppInfo info = new AppInfo();
        info.title = title;
        info.componentName = new ComponentName("Test", title);
        return info;
    }
}
