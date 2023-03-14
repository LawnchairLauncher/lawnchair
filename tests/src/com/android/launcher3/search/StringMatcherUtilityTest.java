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
package com.android.launcher3.search;

import static com.android.launcher3.search.StringMatcherUtility.matches;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.search.StringMatcherUtility.StringMatcher;
import com.android.launcher3.search.StringMatcherUtility.StringMatcherSpace;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link StringMatcherUtility}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class StringMatcherUtilityTest {
    private static final StringMatcher MATCHER = StringMatcher.getInstance();
    private static final StringMatcherSpace MATCHER_SPACE = StringMatcherSpace.getInstance();

    @Test
    public void testMatches() {
        assertTrue(matches("white", "white cow", MATCHER));
        assertTrue(matches("white ", "white cow", MATCHER));
        assertTrue(matches("white c", "white cow", MATCHER));
        assertTrue(matches("cow", "white cow", MATCHER));
        assertTrue(matches("cow", "whiteCow", MATCHER));
        assertTrue(matches("cow", "whiteCOW", MATCHER));
        assertTrue(matches("cow", "whitecowCOW", MATCHER));
        assertTrue(matches("cow", "white2cow", MATCHER));

        assertFalse(matches("cow", "whitecow", MATCHER));
        assertFalse(matches("cow", "whitEcow", MATCHER));

        assertTrue(matches("cow", "whitecowCow", MATCHER));
        assertTrue(matches("cow", "whitecow cow", MATCHER));
        assertFalse(matches("cow", "whitecowcow", MATCHER));
        assertFalse(matches("cow", "whit ecowcow", MATCHER));

        assertTrue(matches("dog", "cats&dogs", MATCHER));
        assertTrue(matches("dog", "cats&Dogs", MATCHER));
        assertTrue(matches("&", "cats&Dogs", MATCHER));

        assertTrue(matches("43", "2+43", MATCHER));
        assertFalse(matches("3", "2+43", MATCHER));

        assertTrue(matches("q", "Q", MATCHER));
        assertTrue(matches("q", "  Q", MATCHER));

        // match lower case words
        assertTrue(matches("e", "elephant", MATCHER));
        assertTrue(matches("eL", "Elephant", MATCHER));

        assertTrue(matches("电", "电子邮件", MATCHER));
        assertTrue(matches("电子", "电子邮件", MATCHER));
        assertTrue(matches("子", "电子邮件", MATCHER));
        assertTrue(matches("邮件", "电子邮件", MATCHER));

        assertFalse(matches("ba", "Bot", MATCHER));
        assertFalse(matches("ba", "bot", MATCHER));
        assertFalse(matches("phant", "elephant", MATCHER));
        assertFalse(matches("elephants", "elephant", MATCHER));
    }

    @Test
    public void testMatchesVN() {
        assertTrue(matches("다", "다운로드", MATCHER));
        assertTrue(matches("드", "드라이브", MATCHER));
        assertTrue(matches("ㄷ", "다운로드 드라이브", MATCHER));
        assertTrue(matches("ㄷ", "운로 드라이브", MATCHER));
        assertTrue(matches("åbç", "abc", MATCHER));
        assertTrue(matches("ål", "Alpha", MATCHER));

        assertFalse(matches("ㄷㄷ", "다운로드 드라이브", MATCHER));
        assertFalse(matches("ㄷ", "로드라이브", MATCHER));
        assertFalse(matches("åç", "abc", MATCHER));
    }

    @Test
    public void testMatchesWithSpaceBreakOnly() {
        assertTrue(matches("white", "white cow", MATCHER_SPACE));
        assertTrue(matches("white ", "white cow", MATCHER_SPACE));
        assertTrue(matches("white c", "white cow", MATCHER_SPACE));
        assertTrue(matches("cow", "white cow", MATCHER_SPACE));
        assertTrue(matches("cow", "whitecow cow", MATCHER_SPACE));

        assertFalse(matches("cow", "whiteCow", MATCHER_SPACE));
        assertFalse(matches("cow", "whiteCOW", MATCHER_SPACE));
        assertFalse(matches("cow", "whitecowCOW", MATCHER_SPACE));
        assertFalse(matches("cow", "white2cow", MATCHER_SPACE));
        assertFalse(matches("cow", "whitecow", MATCHER_SPACE));
        assertFalse(matches("cow", "whitEcow", MATCHER_SPACE));
        assertFalse(matches("cow", "whitecowCow", MATCHER_SPACE));
        assertFalse(matches("cow", "whitecowcow", MATCHER_SPACE));
        assertFalse(matches("cow", "whit ecowcow", MATCHER_SPACE));

        assertFalse(matches("dog", "cats&dogs", MATCHER_SPACE));
        assertFalse(matches("dog", "cats&Dogs", MATCHER_SPACE));
        assertFalse(matches("&", "cats&Dogs", MATCHER_SPACE));

        assertFalse(matches("43", "2+43", MATCHER_SPACE));
        assertFalse(matches("3", "2+43", MATCHER_SPACE));

        assertTrue(matches("q", "Q", MATCHER_SPACE));
        assertTrue(matches("q", "  Q", MATCHER_SPACE));

        // match lower case words
        assertTrue(matches("e", "elephant", MATCHER_SPACE));
        assertTrue(matches("eL", "Elephant", MATCHER_SPACE));

        assertTrue(matches("电", "电子邮件", MATCHER_SPACE));
        assertTrue(matches("电子", "电子邮件", MATCHER_SPACE));
        assertTrue(matches("子", "电子邮件", MATCHER_SPACE));
        assertTrue(matches("邮件", "电子邮件", MATCHER_SPACE));

        assertFalse(matches("ba", "Bot", MATCHER_SPACE));
        assertFalse(matches("ba", "bot", MATCHER_SPACE));
        assertFalse(matches("phant", "elephant", MATCHER_SPACE));
        assertFalse(matches("elephants", "elephant", MATCHER_SPACE));
    }
}
