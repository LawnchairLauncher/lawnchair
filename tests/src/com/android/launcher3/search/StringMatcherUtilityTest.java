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

import static com.android.launcher3.search.StringMatcherUtility.getListOfBreakpoints;
import static com.android.launcher3.search.StringMatcherUtility.matches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.search.StringMatcherUtility.StringMatcher;
import com.android.launcher3.search.StringMatcherUtility.StringMatcherSpace;
import com.android.launcher3.util.IntArray;

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

    @Test
    public void testStringWithProperBreaks() {
        // empty string
        assertEquals(IntArray.wrap(), getListOfBreakpoints("", MATCHER));

        // should be "D Dz" that's why breakpoint is at 0
        assertEquals(IntArray.wrap(0), getListOfBreakpoints("DDz", MATCHER));

        // test all caps and all lower-case
        assertEquals(IntArray.wrap(), getListOfBreakpoints("SNKRS", MATCHER));
        assertEquals(IntArray.wrap(), getListOfBreakpoints("flutterappflorafy", MATCHER));
        assertEquals(IntArray.wrap(), getListOfBreakpoints("LEGO®", MATCHER));

        // test camel case
        // breakpoint at 9 to be "flutterapp Florafy"
        assertEquals(IntArray.wrap(9), getListOfBreakpoints("flutterappFlorafy", MATCHER));
        // breakpoint at 4 to be "Metro Zone"
        assertEquals(IntArray.wrap(4), getListOfBreakpoints("MetroZone", MATCHER));
        // breakpoint at 4,5 to be "metro X Zone"
        assertEquals(IntArray.wrap(4,5), getListOfBreakpoints("metroXZone", MATCHER));
        // breakpoint at 0 to be "G Pay"
        assertEquals(IntArray.wrap(0), getListOfBreakpoints("GPay", MATCHER));
        // breakpoint at 4 to be "Whats App"
        assertEquals(IntArray.wrap(4), getListOfBreakpoints("WhatsApp", MATCHER));
        // breakpoint at 2 to be "aaa A"
        assertEquals(IntArray.wrap(2), getListOfBreakpoints("aaaA", MATCHER));
        // breakpoint at 4,12,16 to be "Other Launcher Test App"
        assertEquals(IntArray.wrap(4,12,16),
                getListOfBreakpoints("OtherLauncherTestApp", MATCHER));

        // test with TITLECASE_LETTER
        // should be "Dǲ" that's why there are no break points
        assertEquals(IntArray.wrap(), getListOfBreakpoints("Dǲ", MATCHER));
        // breakpoint at 0 to be "D Dǅ"
        assertEquals(IntArray.wrap(0), getListOfBreakpoints("DDǅ", MATCHER));
        // breakpoint at 0 because there is a space to be "ǅ DD"
        assertEquals(IntArray.wrap(0), getListOfBreakpoints("ǅ DD", MATCHER));
        // breakpoint at 1 to be "Dw ǲ"
        assertEquals(IntArray.wrap(1), getListOfBreakpoints("Dwǲ", MATCHER));
        // breakpoint at 0,2 to be "Dw ǲ"
        assertEquals(IntArray.wrap(0,2), getListOfBreakpoints("wDwǲ", MATCHER));
        // breakpoint at 1,3 to be "ᾋw Dw ǲ"
        assertEquals(IntArray.wrap(1,3), getListOfBreakpoints("ᾋwDwǲ", MATCHER));
        // breakpoint at 0,2,4 to be "ᾋ ᾋw Dw ǲ"
        assertEquals(IntArray.wrap(0,2,4), getListOfBreakpoints("ᾋᾋwDwǲ", MATCHER));
        // breakpoint at 0,2,4 to be "ᾋ ᾋw Dw ǲ®"
        assertEquals(IntArray.wrap(0,2,4), getListOfBreakpoints("ᾋᾋwDwǲ®", MATCHER));

        // test with numbers and symbols
        // breakpoint at 3,11 to be "Test Activity 13"
        assertEquals(IntArray.wrap(3,11), getListOfBreakpoints("TestActivity13", MATCHER));
        // breakpoint at 3, 4, 12, 13 as the breakpoints are at the dashes
        assertEquals(IntArray.wrap(3,4,12,13),
                getListOfBreakpoints("Test-Activity-12", MATCHER));
        // breakpoint at 1 to be "AA 2"
        assertEquals(IntArray.wrap(1), getListOfBreakpoints("AA2", MATCHER));
        // breakpoint at 1 to be "AAA 2"
        assertEquals(IntArray.wrap(2), getListOfBreakpoints("AAA2", MATCHER));
        // breakpoint at 1 to be "ab 2"
        assertEquals(IntArray.wrap(1), getListOfBreakpoints("ab2", MATCHER));
        // breakpoint at 1,2 to be "el 3 suhwee"
        assertEquals(IntArray.wrap(1,2), getListOfBreakpoints("el3suhwee", MATCHER));
        // breakpoint at 0,1 as the breakpoints are at '-'
        assertEquals(IntArray.wrap(0,1), getListOfBreakpoints("t-mobile", MATCHER));
        assertEquals(IntArray.wrap(0,1), getListOfBreakpoints("t-Mobile", MATCHER));
        // breakpoint at 0,1,2 as the breakpoints are at '-'
        assertEquals(IntArray.wrap(0,1,2), getListOfBreakpoints("t--Mobile", MATCHER));
        // breakpoint at 1,2,3 as the breakpoints are at '-'
        assertEquals(IntArray.wrap(1,2,3), getListOfBreakpoints("tr--Mobile", MATCHER));
        // breakpoint at 3,4 as the breakpoints are at '.'
        assertEquals(IntArray.wrap(3,4), getListOfBreakpoints("Agar.io", MATCHER));
        assertEquals(IntArray.wrap(3,4), getListOfBreakpoints("Hole.Io", MATCHER));

        // breakpoint at 0 to be "µ Torrent®"
        assertEquals(IntArray.wrap(0), getListOfBreakpoints("µTorrent®", MATCHER));
        // breakpoint at 4 to be "LEGO® Builder"
        assertEquals(IntArray.wrap(4), getListOfBreakpoints("LEGO®Builder", MATCHER));
        // breakpoint at 4 to be "LEGO® builder"
        assertEquals(IntArray.wrap(4), getListOfBreakpoints("LEGO®builder", MATCHER));
        // breakpoint at 4 to be "lego® builder"
        assertEquals(IntArray.wrap(4), getListOfBreakpoints("lego®builder", MATCHER));

        // test string with spaces - where the breakpoints are right before where the spaces are at
        assertEquals(IntArray.wrap(3,8), getListOfBreakpoints("HEAD BALL 2", MATCHER));
        assertEquals(IntArray.wrap(2,8),
                getListOfBreakpoints("OFL Agent Application", MATCHER));
        assertEquals(IntArray.wrap(0,2), getListOfBreakpoints("D D z", MATCHER));
        assertEquals(IntArray.wrap(6), getListOfBreakpoints("Battery Stats", MATCHER));
        assertEquals(IntArray.wrap(5,9,15),
                getListOfBreakpoints("System UWB Field Test", MATCHER));
    }
}
