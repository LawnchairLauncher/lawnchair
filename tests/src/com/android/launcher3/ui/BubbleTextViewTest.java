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

package com.android.launcher3.ui;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.BubbleTextView.DISPLAY_ALL_APPS;
import static com.android.launcher3.BubbleTextView.DISPLAY_PREDICTION_ROW;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.graphics.Typeface;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Flags;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.views.BaseDragLayer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for testing modifyTitleToSupportMultiLine() in BubbleTextView.java
 * This class tests a couple of strings and uses the getMaxLines() to determine if the test passes.
 * Verifying with getMaxLines() is sufficient since BubbleTextView can only be in one line or
 * two lines, and this is enough to ensure whether the string should be specifically wrapped onto
 * the second line and to ensure truncation.
 */
public class BubbleTextViewTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    private static final StringMatcherUtility.StringMatcher
            MATCHER = StringMatcherUtility.StringMatcher.getInstance();
    private static final int ONE_LINE = 1;
    private static final int TWO_LINE = 2;
    private static final String TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT = "Battery Stats";
    private static final String TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT_RESULT =
            "Battery\nStats";
    private static final String TEST_LONG_STRING_NO_SPACE_LONGER_THAN_CHAR_LIMIT =
            "flutterappflorafy";
    private static final String TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT =
            "System UWB Field Test";
    private static final String TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT_RESULT =
            "System\nUWB Field Test";
    private static final String TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT =
            "LEGO®Builder";
    private static final String TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT_RESULT =
            "LEGO®\nBuilder";
    private static final String EMPTY_STRING = "";
    private static final int CHAR_CNT = 7;
    private static final int MAX_HEIGHT = Integer.MAX_VALUE;
    private static final int LIMITED_HEIGHT = 357; /* allowedHeight in Pixel6 */
    private static final float SPACE_MULTIPLIER = 1;
    private static final float SPACE_EXTRA = 0;

    private BubbleTextView mBubbleTextView;
    private ItemInfoWithIcon mItemInfoWithIcon;
    private Context mContext;
    private int mLimitedWidth;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_CURSOR_HOVER_STATES);
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU);
        Utilities.enableRunningInTestHarnessForTests();
        mContext = new ActivityContextWrapper(getApplicationContext());
        mBubbleTextView = new BubbleTextView(mContext);
        mBubbleTextView.reset();

        BubbleTextView testView = new BubbleTextView(mContext);
        testView.setTypeface(Typeface.MONOSPACE);
        testView.setText("B");
        // calculate the maxWidth of the textView by calculating the width of one monospace
        // character * CHAR_CNT
        mLimitedWidth =
                (int) (testView.getPaint().measureText(testView.getText().toString()) * CHAR_CNT);
        // needed otherwise there is a NPE during setText() on checkForRelayout()
        mBubbleTextView.setLayoutParams(
                new ViewGroup.LayoutParams(mLimitedWidth,
                BaseDragLayer.LayoutParams.WRAP_CONTENT));
        mItemInfoWithIcon = new ItemInfoWithIcon() {
            @Override
            public ItemInfoWithIcon clone() {
                return null;
            }
        };
    }

    @Test
    public void testEmptyString_flagOn() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        mItemInfoWithIcon.title = EMPTY_STRING;
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertNotEquals(TWO_LINE, mBubbleTextView.getMaxLines());
    }

    @Test
    public void testEmptyString_flagOff() {
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        mItemInfoWithIcon.title = EMPTY_STRING;
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testStringWithSpaceLongerThanCharLimit_flagOn() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "Battery Stats"
        mItemInfoWithIcon.title = TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, MAX_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(TWO_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testStringWithSpaceLongerThanCharLimit_flagOff() {
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "Battery Stats"
        mItemInfoWithIcon.title = TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testLongStringNoSpaceLongerThanCharLimit_flagOn() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "flutterappflorafy"
        mItemInfoWithIcon.title = TEST_LONG_STRING_NO_SPACE_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testLongStringNoSpaceLongerThanCharLimit_flagOff() {
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "flutterappflorafy"
        mItemInfoWithIcon.title = TEST_LONG_STRING_NO_SPACE_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testLongStringWithSpaceLongerThanCharLimit_flagOn() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "System UWB Field Test"
        mItemInfoWithIcon.title = TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, MAX_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(TWO_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testLongStringWithSpaceLongerThanCharLimit_flagOff() {
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "System UWB Field Test"
        mItemInfoWithIcon.title = TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testLongStringSymbolLongerThanCharLimit_flagOn() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "LEGO®Builder"
        mItemInfoWithIcon.title = TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, MAX_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(TWO_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void testLongStringSymbolLongerThanCharLimit_flagOff() {
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "LEGO®Builder"
        mItemInfoWithIcon.title = TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void modifyTitleToSupportMultiLine_TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT() {
        // test string: "Battery Stats"
        IntArray breakPoints = StringMatcherUtility.getListOfBreakpoints(
                TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT, MATCHER);
        CharSequence newString = BubbleTextView.modifyTitleToSupportMultiLine(mLimitedWidth,
                MAX_HEIGHT,
                TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT, mBubbleTextView.getPaint(),
                breakPoints,
                SPACE_MULTIPLIER,
                SPACE_EXTRA);
        assertEquals(TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT_RESULT, newString);
    }

    @Test
    public void modifyTitleToSupportMultiLine_TEST_LONG_STRING_NO_SPACE_LONGER_THAN_CHAR_LIMIT() {
        // test string: "flutterappflorafy"
        IntArray breakPoints = StringMatcherUtility.getListOfBreakpoints(
                TEST_LONG_STRING_NO_SPACE_LONGER_THAN_CHAR_LIMIT, MATCHER);
        CharSequence newString = BubbleTextView.modifyTitleToSupportMultiLine(mLimitedWidth, 0,
                TEST_LONG_STRING_NO_SPACE_LONGER_THAN_CHAR_LIMIT, mBubbleTextView.getPaint(),
                breakPoints,
                SPACE_MULTIPLIER,
                SPACE_EXTRA);
        assertEquals(TEST_LONG_STRING_NO_SPACE_LONGER_THAN_CHAR_LIMIT, newString);
    }

    @Test
    public void modifyTitleToSupportMultiLine_TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT() {
        // test string: "System UWB Field Test"
        IntArray breakPoints = StringMatcherUtility.getListOfBreakpoints(
                TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT, MATCHER);
        CharSequence newString = BubbleTextView.modifyTitleToSupportMultiLine(mLimitedWidth,
                MAX_HEIGHT,
                TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT, mBubbleTextView.getPaint(),
                breakPoints,
                SPACE_MULTIPLIER,
                SPACE_EXTRA);
        assertEquals(TEST_LONG_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT_RESULT, newString);
    }

    @Test
    public void modifyTitleToSupportMultiLine_TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT() {
        // test string: "LEGO®Builder"
        IntArray breakPoints = StringMatcherUtility.getListOfBreakpoints(
                TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT, MATCHER);
        CharSequence newString = BubbleTextView.modifyTitleToSupportMultiLine(
                mLimitedWidth,
                MAX_HEIGHT,
                TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT, mBubbleTextView.getPaint(),
                breakPoints,
                SPACE_MULTIPLIER,
                SPACE_EXTRA);
        assertEquals(TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT_RESULT, newString);
    }

    @Test
    public void testEnsurePredictionRowIsTwoLine() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "Battery Stats"
        mItemInfoWithIcon.title = TEST_STRING_WITH_SPACE_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.setDisplay(DISPLAY_PREDICTION_ROW);
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, MAX_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(TWO_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void modifyTitleToSupportMultiLine_whenLimitedHeight_shouldBeOneLine() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "LEGO®Builder"
        mItemInfoWithIcon.title = TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void modifyTitleToSupportMultiLine_whenUnlimitedHeight_shouldBeTwoLine() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS);
        // test string: "LEGO®Builder"
        mItemInfoWithIcon.title = TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, MAX_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(TWO_LINE, mBubbleTextView.getLineCount());
    }
}
