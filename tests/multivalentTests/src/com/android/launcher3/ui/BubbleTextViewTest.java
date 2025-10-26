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

import static android.graphics.fonts.FontStyle.FONT_WEIGHT_BOLD;
import static android.graphics.fonts.FontStyle.FONT_WEIGHT_NORMAL;
import static android.text.style.DynamicDrawableSpan.ALIGN_CENTER;

import static com.android.launcher3.BubbleTextView.DISPLAY_ALL_APPS;
import static com.android.launcher3.BubbleTextView.DISPLAY_PREDICTION_ROW;
import static com.android.launcher3.BubbleTextView.DISPLAY_SEARCH_RESULT;
import static com.android.launcher3.BubbleTextView.DISPLAY_SEARCH_RESULT_SMALL;
import static com.android.launcher3.Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING;
import static com.android.launcher3.Flags.FLAG_USE_NEW_ICON_FOR_ARCHIVED_APPS;
import static com.android.launcher3.LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_ARCHIVED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Typeface;
import android.os.Build;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.SpannedString;
import android.text.style.ImageSpan;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.PreloadIconDrawable;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.views.BaseDragLayer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for testing modifyTitleToSupportMultiLine() in BubbleTextView.java
 * This class tests a couple of strings and uses the getMaxLines() to determine if the test passes.
 * Verifying with getMaxLines() is sufficient since BubbleTextView can only be in one line or
 * two lines, and this is enough to ensure whether the string should be specifically wrapped onto
 * the second line and to ensure truncation.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BubbleTextViewTest {

    @Rule public final SetFlagsRule mSetFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);
    private static final StringMatcherUtility.StringMatcher
            MATCHER = StringMatcherUtility.StringMatcher.getInstance();
    private static final UserHandle WORK_HANDLE = new UserHandle(13);
    private static final int WORK_FLAG = 1;
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

    private SandboxModelContext mModelContext;

    private BubbleTextView mBubbleTextView;
    private ItemInfoWithIcon mItemInfoWithIcon;
    private Context mContext;
    private int mLimitedWidth;
    private AppInfo mGmailAppInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Utilities.enableRunningInTestHarnessForTests();
        mModelContext = new SandboxModelContext();
        LauncherPrefs.get(mModelContext).put(ENABLE_TWOLINE_ALLAPPS_TOGGLE, true);

        mContext = new ActivityContextWrapper(mModelContext);
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
        ComponentName componentName = new ComponentName(mContext,
                "com.android.launcher3.tests.Activity" + "Gmail");
        mGmailAppInfo = new AppInfo(componentName, "Gmail", WORK_HANDLE, new Intent());
    }

    @After
    public void tearDown() {
        mModelContext.onDestroy();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testEmptyString_flagOn() {
        mItemInfoWithIcon.title = EMPTY_STRING;
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertNotEquals(TWO_LINE, mBubbleTextView.getMaxLines());
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testEmptyString_flagOff() {
        mItemInfoWithIcon.title = EMPTY_STRING;
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testStringWithSpaceLongerThanCharLimit_flagOn() {
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
    @DisableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testStringWithSpaceLongerThanCharLimit_flagOff() {
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
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testLongStringNoSpaceLongerThanCharLimit_flagOn() {
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
    @DisableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testLongStringNoSpaceLongerThanCharLimit_flagOff() {
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
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testLongStringWithSpaceLongerThanCharLimit_flagOn() {
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
    @DisableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testLongStringWithSpaceLongerThanCharLimit_flagOff() {
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
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testLongStringSymbolLongerThanCharLimit_flagOn() {
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
    @DisableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testLongStringSymbolLongerThanCharLimit_flagOff() {
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
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void testEnsurePredictionRowIsTwoLine() {
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
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void modifyTitleToSupportMultiLine_whenLimitedHeight_shouldBeOneLine() {
        // test string: "LEGO®Builder"
        mItemInfoWithIcon.title = TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, LIMITED_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(ONE_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TWOLINE_TOGGLE)
    public void modifyTitleToSupportMultiLine_whenUnlimitedHeight_shouldBeTwoLine() {
        // test string: "LEGO®Builder"
        mItemInfoWithIcon.title = TEST_LONG_STRING_SYMBOL_LONGER_THAN_CHAR_LIMIT;
        mBubbleTextView.setDisplay(DISPLAY_ALL_APPS);
        mBubbleTextView.applyLabel(mItemInfoWithIcon);
        mBubbleTextView.setTypeface(Typeface.MONOSPACE);
        mBubbleTextView.measure(mLimitedWidth, MAX_HEIGHT);

        mBubbleTextView.onPreDraw();

        assertEquals(TWO_LINE, mBubbleTextView.getLineCount());
    }

    @Test
    public void applyIconAndLabel_whenDisplay_DISPLAY_SEARCH_RESULT_SMALL_noBadge() {
        FlagOp op = FlagOp.NO_OP;
        // apply the WORK bitmap flag to show work badge
        mGmailAppInfo.bitmap.flags = op.apply(WORK_FLAG);
        mBubbleTextView.setDisplay(DISPLAY_SEARCH_RESULT_SMALL);

        mBubbleTextView.applyIconAndLabel(mGmailAppInfo);

        assertThat(mBubbleTextView.getIcon().hasBadge()).isEqualTo(false);
    }

    @EnableFlags({FLAG_ENABLE_SUPPORT_FOR_ARCHIVING, FLAG_USE_NEW_ICON_FOR_ARCHIVED_APPS})
    @Test
    public void applyIconAndLabel_setsImageSpan_whenInactiveArchivedApp() {
        // Given
        BubbleTextView spyTextView = spy(mBubbleTextView);
        mGmailAppInfo.runtimeStatusFlags |= FLAG_ARCHIVED;
        BubbleTextView expectedTextView = new BubbleTextView(mContext);
        mContext.getResources().getConfiguration().fontWeightAdjustment = 0;
        int expectedDrawableId = mContext.getResources().getIdentifier(
                "cloud_download_24px", /* name */
                "drawable", /* defType */
                mContext.getPackageName()
        );
        expectedTextView.setTextWithStartIcon(mGmailAppInfo.title, expectedDrawableId);
        // When
        spyTextView.applyIconAndLabel(mGmailAppInfo);
        // Then
        SpannedString expectedText = (SpannedString) expectedTextView.getText();
        SpannedString actualText = (SpannedString) spyTextView.getText();
        ImageSpan actualSpan = actualText.getSpans(
                0, /* queryStart */
                1, /* queryEnd */
                ImageSpan.class
        )[0];
        ImageSpan expectedSpan = expectedText.getSpans(
                0, /* queryStart */
                1, /* queryEnd */
                ImageSpan.class
        )[0];
        verify(spyTextView).setTextWithStartIcon(mGmailAppInfo.title, expectedDrawableId);
        assertThat(actualText.toString()).isEqualTo(expectedText.toString());
        assertThat(actualSpan.getDrawable().getBounds())
                .isEqualTo(expectedSpan.getDrawable().getBounds());
        assertThat(actualSpan.getVerticalAlignment()).isEqualTo(ALIGN_CENTER);
    }

    @EnableFlags({FLAG_ENABLE_SUPPORT_FOR_ARCHIVING, FLAG_USE_NEW_ICON_FOR_ARCHIVED_APPS})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    public void applyIconAndLabel_setsBoldDrawable_whenBoldedTextForArchivedApp() {
        // Given
        int expectedDrawableId = mContext.getResources().getIdentifier(
                "cloud_download_semibold_24px", /* name */
                "drawable", /* defType */
                mContext.getPackageName()
        );
        mContext.getResources().getConfiguration().fontWeightAdjustment =
                FONT_WEIGHT_BOLD - FONT_WEIGHT_NORMAL;
        BubbleTextView spyTextView = spy(mBubbleTextView);
        mGmailAppInfo.runtimeStatusFlags |= FLAG_ARCHIVED;
        // When
        spyTextView.applyIconAndLabel(mGmailAppInfo);
        // Then
        verify(spyTextView).setTextWithStartIcon(mGmailAppInfo.title, expectedDrawableId);
    }

    @Test
    public void applyIconAndLabel_whenDisplay_DISPLAY_SEARCH_RESULT_hasBadge() {
        FlagOp op = FlagOp.NO_OP;
        // apply the WORK bitmap flag to show work badge
        mGmailAppInfo.bitmap.flags = op.apply(WORK_FLAG);
        mBubbleTextView.setDisplay(DISPLAY_SEARCH_RESULT);

        mBubbleTextView.applyIconAndLabel(mGmailAppInfo);

        assertThat(mBubbleTextView.getIcon().hasBadge()).isEqualTo(true);
    }

    @Test
    public void applyingPendingIcon_preserves_last_icon() throws Exception {
        mItemInfoWithIcon.bitmap =
                BitmapInfo.fromBitmap(Bitmap.createBitmap(100, 100, Config.ARGB_8888));
        mItemInfoWithIcon.setProgressLevel(30, PackageInstallInfo.STATUS_INSTALLING);

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR,
                () -> mBubbleTextView.applyIconAndLabel(mItemInfoWithIcon));
        assertThat(mBubbleTextView.getIcon()).isInstanceOf(PreloadIconDrawable.class);
        assertThat(mBubbleTextView.getIcon().getLevel()).isEqualTo(30);
        PreloadIconDrawable oldIcon = (PreloadIconDrawable) mBubbleTextView.getIcon();

        // Same icon is used when progress changes
        mItemInfoWithIcon.setProgressLevel(50, PackageInstallInfo.STATUS_INSTALLING);
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR,
                () -> mBubbleTextView.applyIconAndLabel(mItemInfoWithIcon));
        assertThat(mBubbleTextView.getIcon()).isSameInstanceAs(oldIcon);
        assertThat(mBubbleTextView.getIcon().getLevel()).isEqualTo(50);

        // Icon is replaced with a non pending icon when download finishes
        mItemInfoWithIcon.setProgressLevel(100, PackageInstallInfo.STATUS_INSTALLED);

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR, () -> {
            mBubbleTextView.applyIconAndLabel(mItemInfoWithIcon);
            assertThat(mBubbleTextView.getIcon()).isSameInstanceAs(oldIcon);
            assertThat(oldIcon.getActiveAnimation()).isNotNull();
            oldIcon.getActiveAnimation().end();
        });

        // Assert that the icon is replaced with a non-pending icon
        assertThat(mBubbleTextView.getIcon()).isNotInstanceOf(PreloadIconDrawable.class);
    }

}
