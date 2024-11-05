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

package com.android.wm.shell.pip.phone;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.util.Size;
import android.view.DisplayInfo;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.pip.PhoneSizeSpecSource;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.SizeSpecSource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Unit test against {@link PhoneSizeSpecSource}
 */
@RunWith(AndroidTestingRunner.class)
public class PhoneSizeSpecSourceTest extends ShellTestCase {
    /** A sample overridden min edge size. */
    private static final int OVERRIDE_MIN_EDGE_SIZE = 40;
    /** A sample default min edge size */
    private static final int DEFAULT_MIN_EDGE_SIZE = 40;
    /** Display edge size */
    private static final int DISPLAY_EDGE_SIZE = 1000;
    /** Default sizing percentage */
    private static final float DEFAULT_PERCENT = 0.6f;
    /** Minimum sizing percentage */
    private static final float MIN_PERCENT = 0.5f;
    /** Threshold to determine if a Display is square-ish. */
    private static final float SQUARE_DISPLAY_THRESHOLD = 0.95f;
    /** Default sizing percentage for square-ish Display. */
    private static final float SQUARE_DISPLAY_DEFAULT_PERCENT = 0.5f;
    /** Minimum sizing percentage for square-ish Display. */
    private static final float SQUARE_DISPLAY_MIN_PERCENT = 0.4f;
    /** Aspect ratio that the new PIP size spec logic optimizes for. */
    private static final float OPTIMIZED_ASPECT_RATIO = 9f / 16;

    /** Maps of aspect ratios to be tested to expected sizes on non-square Display. */
    private static Map<Float, Size> sNonSquareDisplayExpectedMaxSizes;
    private static Map<Float, Size> sNonSquareDisplayExpectedDefaultSizes;
    private static Map<Float, Size> sNonSquareDisplayExpectedMinSizes;

    /** Maps of aspect ratios to be tested to expected sizes on square Display. */
    private static Map<Float, Size> sSquareDisplayExpectedMaxSizes;
    private static Map<Float, Size> sSquareDisplayExpectedDefaultSizes;
    private static Map<Float, Size> sSquareDisplayExpectedMinSizes;

    @Mock private Context mContext;
    @Mock private Resources mResources;

    private PipDisplayLayoutState mPipDisplayLayoutState;
    private SizeSpecSource mSizeSpecSource;

    /**
     * Initializes the map with the aspect ratios to be tested and corresponding expected max sizes.
     * This is to initialize the expectations on non-square Display only.
     */
    private static void initNonSquareDisplayExpectedSizes() {
        sNonSquareDisplayExpectedMaxSizes = new HashMap<>();
        sNonSquareDisplayExpectedDefaultSizes = new HashMap<>();
        sNonSquareDisplayExpectedMinSizes = new HashMap<>();

        sNonSquareDisplayExpectedMaxSizes.put(16f / 9, new Size(1000, 563));
        sNonSquareDisplayExpectedDefaultSizes.put(16f / 9, new Size(600, 338));
        sNonSquareDisplayExpectedMinSizes.put(16f / 9, new Size(501, 282));

        sNonSquareDisplayExpectedMaxSizes.put(4f / 3, new Size(893, 670));
        sNonSquareDisplayExpectedDefaultSizes.put(4f / 3, new Size(536, 402));
        sNonSquareDisplayExpectedMinSizes.put(4f / 3, new Size(447, 335));

        sNonSquareDisplayExpectedMaxSizes.put(3f / 4, new Size(670, 893));
        sNonSquareDisplayExpectedDefaultSizes.put(3f / 4, new Size(402, 536));
        sNonSquareDisplayExpectedMinSizes.put(3f / 4, new Size(335, 447));

        sNonSquareDisplayExpectedMaxSizes.put(9f / 16, new Size(563, 1001));
        sNonSquareDisplayExpectedDefaultSizes.put(9f / 16, new Size(338, 601));
        sNonSquareDisplayExpectedMinSizes.put(9f / 16, new Size(282, 501));
    }

    /**
     * Initializes the map with the aspect ratios to be tested and corresponding expected max sizes.
     * This is to initialize the expectations on square Display only.
     */
    private static void initSquareDisplayExpectedSizes() {
        sSquareDisplayExpectedMaxSizes = new HashMap<>();
        sSquareDisplayExpectedDefaultSizes = new HashMap<>();
        sSquareDisplayExpectedMinSizes = new HashMap<>();

        sSquareDisplayExpectedMaxSizes.put(16f / 9, new Size(1000, 563));
        sSquareDisplayExpectedDefaultSizes.put(16f / 9, new Size(500, 281));
        sSquareDisplayExpectedMinSizes.put(16f / 9, new Size(400, 225));

        sSquareDisplayExpectedMaxSizes.put(4f / 3, new Size(893, 670));
        sSquareDisplayExpectedDefaultSizes.put(4f / 3, new Size(447, 335));
        sSquareDisplayExpectedMinSizes.put(4f / 3, new Size(357, 268));

        sSquareDisplayExpectedMaxSizes.put(3f / 4, new Size(670, 893));
        sSquareDisplayExpectedDefaultSizes.put(3f / 4, new Size(335, 447));
        sSquareDisplayExpectedMinSizes.put(3f / 4, new Size(268, 357));

        sSquareDisplayExpectedMaxSizes.put(9f / 16, new Size(563, 1001));
        sSquareDisplayExpectedDefaultSizes.put(9f / 16, new Size(282, 501));
        sSquareDisplayExpectedMinSizes.put(9f / 16, new Size(225, 400));
    }

    private void forEveryTestCaseCheck(Map<Float, Size> expectedSizes,
            Function<Float, Size> callback) {
        for (Map.Entry<Float, Size> expectedSizesEntry : expectedSizes.entrySet()) {
            float aspectRatio = expectedSizesEntry.getKey();
            Size expectedSize = expectedSizesEntry.getValue();

            Assert.assertEquals(expectedSize, callback.apply(aspectRatio));
        }
    }

    @Before
    public void setUp() {
        initNonSquareDisplayExpectedSizes();
        initSquareDisplayExpectedSizes();

        when(mResources.getFloat(R.dimen.config_pipSystemPreferredDefaultSizePercent))
                .thenReturn(DEFAULT_PERCENT);
        when(mResources.getFloat(R.dimen.config_pipSystemPreferredMinimumSizePercent))
                .thenReturn(MIN_PERCENT);
        when(mResources.getDimensionPixelSize(R.dimen.default_minimal_size_pip_resizable_task))
                .thenReturn(DEFAULT_MIN_EDGE_SIZE);
        when(mResources.getFloat(R.dimen.config_pipLargeScreenOptimizedAspectRatio))
                .thenReturn(OPTIMIZED_ASPECT_RATIO);
        when(mResources.getString(R.string.config_defaultPictureInPictureScreenEdgeInsets))
                .thenReturn("0x0");
        when(mResources.getDisplayMetrics())
                .thenReturn(getContext().getResources().getDisplayMetrics());
        when(mResources.getFloat(R.dimen.config_pipSquareDisplayThresholdForSystemPreferredSize))
                .thenReturn(SQUARE_DISPLAY_THRESHOLD);
        when(mResources.getFloat(
                R.dimen.config_pipSystemPreferredDefaultSizePercentForSquareDisplay))
                .thenReturn(SQUARE_DISPLAY_DEFAULT_PERCENT);
        when(mResources.getFloat(
                R.dimen.config_pipSystemPreferredMinimumSizePercentForSquareDisplay))
                .thenReturn(SQUARE_DISPLAY_MIN_PERCENT);

        // set up the mock context for spec handler specifically
        when(mContext.getResources()).thenReturn(mResources);
    }

    private void setupSizeSpecWithDisplayDimension(int width, int height) {
        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = width;
        displayInfo.logicalHeight = height;

        // use the parent context (not the mocked one) to obtain the display layout
        // this is done to avoid unnecessary mocking while allowing for custom display dimensions
        DisplayLayout displayLayout = new DisplayLayout(displayInfo, getContext().getResources(),
                false, false);
        mPipDisplayLayoutState = new PipDisplayLayoutState(mContext);
        mPipDisplayLayoutState.setDisplayLayout(displayLayout);

        mSizeSpecSource = new PhoneSizeSpecSource(mContext, mPipDisplayLayoutState);

        // no overridden min edge size by default
        mSizeSpecSource.setOverrideMinSize(null);
    }

    @Test
    public void testGetMaxSize_nonSquareDisplay() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE * 2, DISPLAY_EDGE_SIZE);
        forEveryTestCaseCheck(sNonSquareDisplayExpectedMaxSizes,
                (aspectRatio) -> mSizeSpecSource.getMaxSize(aspectRatio));
    }

    @Test
    public void testGetDefaultSize_nonSquareDisplay() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE * 2, DISPLAY_EDGE_SIZE);
        forEveryTestCaseCheck(sNonSquareDisplayExpectedDefaultSizes,
                (aspectRatio) -> mSizeSpecSource.getDefaultSize(aspectRatio));
    }

    @Test
    public void testGetMinSize_nonSquareDisplay() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE * 2, DISPLAY_EDGE_SIZE);
        forEveryTestCaseCheck(sNonSquareDisplayExpectedMinSizes,
                (aspectRatio) -> mSizeSpecSource.getMinSize(aspectRatio));
    }

    @Test
    public void testGetMaxSize_squareDisplay() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE, DISPLAY_EDGE_SIZE);
        forEveryTestCaseCheck(sSquareDisplayExpectedMaxSizes,
                (aspectRatio) -> mSizeSpecSource.getMaxSize(aspectRatio));
    }

    @Test
    public void testGetDefaultSize_squareDisplay() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE, DISPLAY_EDGE_SIZE);
        forEveryTestCaseCheck(sSquareDisplayExpectedDefaultSizes,
                (aspectRatio) -> mSizeSpecSource.getDefaultSize(aspectRatio));
    }

    @Test
    public void testGetMinSize_squareDisplay() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE, DISPLAY_EDGE_SIZE);
        forEveryTestCaseCheck(sSquareDisplayExpectedMinSizes,
                (aspectRatio) -> mSizeSpecSource.getMinSize(aspectRatio));
    }

    @Test
    public void testGetSizeForAspectRatio_noOverrideMinSize() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE * 2, DISPLAY_EDGE_SIZE);
        // an initial size with 16:9 aspect ratio
        Size initSize = new Size(600, 337);

        Size expectedSize = new Size(338, 601);
        Size actualSize = mSizeSpecSource.getSizeForAspectRatio(initSize, 9f / 16);

        Assert.assertEquals(expectedSize, actualSize);
    }

    @Test
    public void testGetSizeForAspectRatio_withOverrideMinSize() {
        setupSizeSpecWithDisplayDimension(DISPLAY_EDGE_SIZE * 2, DISPLAY_EDGE_SIZE);
        // an initial size with a 1:1 aspect ratio
        Size initSize = new Size(OVERRIDE_MIN_EDGE_SIZE, OVERRIDE_MIN_EDGE_SIZE);
        mSizeSpecSource.setOverrideMinSize(initSize);

        Size expectedSize = new Size(40, 71);
        Size actualSize = mSizeSpecSource.getSizeForAspectRatio(initSize, 9f / 16);

        Assert.assertEquals(expectedSize, actualSize);
    }
}
