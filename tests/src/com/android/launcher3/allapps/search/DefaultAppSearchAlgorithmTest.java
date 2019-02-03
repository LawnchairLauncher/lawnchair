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
package com.android.launcher3.allapps.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;

import com.android.launcher3.AppInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

/**
 * Unit tests for {@link DefaultAppSearchAlgorithm}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DefaultAppSearchAlgorithmTest {
    private static final DefaultAppSearchAlgorithm.StringMatcher MATCHER =
            DefaultAppSearchAlgorithm.StringMatcher.getInstance();

    @Test
    public void testMatches() {
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("white cow"), "cow", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("whiteCow"), "cow", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("whiteCOW"), "cow", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("whitecowCOW"), "cow", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("white2cow"), "cow", MATCHER));

        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("whitecow"), "cow", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("whitEcow"), "cow", MATCHER));

        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("whitecowCow"), "cow", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("whitecow cow"), "cow", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("whitecowcow"), "cow", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("whit ecowcow"), "cow", MATCHER));

        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("cats&dogs"), "dog", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("cats&Dogs"), "dog", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("cats&Dogs"), "&", MATCHER));

        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("2+43"), "43", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("2+43"), "3", MATCHER));

        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("Q"), "q", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("  Q"), "q", MATCHER));

        // match lower case words
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("elephant"), "e", MATCHER));

        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("电子邮件"), "电", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("电子邮件"), "电子", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("电子邮件"), "子", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("电子邮件"), "邮件", MATCHER));

        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("Bot"), "ba", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("bot"), "ba", MATCHER));
    }

    @Test
    public void testMatchesVN() {
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("다운로드"), "다", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("드라이브"), "드", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("다운로드 드라이브"), "ㄷ", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("운로 드라이브"), "ㄷ", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("abc"), "åbç", MATCHER));
        assertTrue(DefaultAppSearchAlgorithm.matches(getInfo("Alpha"), "ål", MATCHER));

        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("다운로드 드라이브"), "ㄷㄷ", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("로드라이브"), "ㄷ", MATCHER));
        assertFalse(DefaultAppSearchAlgorithm.matches(getInfo("abc"), "åç", MATCHER));
    }

    private AppInfo getInfo(String title) {
        AppInfo info = new AppInfo();
        info.title = title;
        info.componentName = new ComponentName("Test", title);
        return info;
    }
}
