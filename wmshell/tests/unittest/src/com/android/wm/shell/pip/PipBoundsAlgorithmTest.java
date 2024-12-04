/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.pip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.Size;
import android.view.DisplayInfo;
import android.view.Gravity;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.pip.PhoneSizeSpecSource;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipKeepClearAlgorithmInterface;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.SizeSpecSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests against {@link PipBoundsAlgorithm}, including but not limited to:
 * - default/movement bounds
 * - save/restore PiP position on application lifecycle
 * - save/restore PiP position on screen rotation
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class PipBoundsAlgorithmTest extends ShellTestCase {
    private static final int ROUNDING_ERROR_MARGIN = 16;
    private static final float ASPECT_RATIO_ERROR_MARGIN = 0.01f;
    private static final float DEFAULT_ASPECT_RATIO = 1f;
    private static final float MIN_ASPECT_RATIO = 0.5f;
    private static final float MAX_ASPECT_RATIO = 2f;
    private static final int DEFAULT_MIN_EDGE_SIZE = 100;

    /** The minimum possible size of the override min size's width or height */
    private static final int OVERRIDABLE_MIN_SIZE = 40;

    private PipBoundsAlgorithm mPipBoundsAlgorithm;
    private DisplayInfo mDefaultDisplayInfo;
    private PipBoundsState mPipBoundsState;
    private SizeSpecSource mSizeSpecSource;
    private PipDisplayLayoutState mPipDisplayLayoutState;


    @Before
    public void setUp() throws Exception {
        initializeMockResources();
        mPipDisplayLayoutState = new PipDisplayLayoutState(mContext);

        mSizeSpecSource = new PhoneSizeSpecSource(mContext, mPipDisplayLayoutState);
        mPipBoundsState = new PipBoundsState(mContext, mSizeSpecSource, mPipDisplayLayoutState);
        mPipBoundsAlgorithm = new PipBoundsAlgorithm(mContext, mPipBoundsState,
                new PipSnapAlgorithm(), new PipKeepClearAlgorithmInterface() {},
                mPipDisplayLayoutState, mSizeSpecSource);

        DisplayLayout layout =
                new DisplayLayout(mDefaultDisplayInfo, mContext.getResources(), true, true);
        mPipDisplayLayoutState.setDisplayLayout(layout);
    }

    private void initializeMockResources() {
        final TestableResources res = mContext.getOrCreateTestableResources();
        res.addOverride(
                R.dimen.config_pictureInPictureDefaultAspectRatio,
                DEFAULT_ASPECT_RATIO);
        res.addOverride(
                R.integer.config_defaultPictureInPictureGravity,
                Gravity.END | Gravity.BOTTOM);
        res.addOverride(
                R.dimen.default_minimal_size_pip_resizable_task,
                DEFAULT_MIN_EDGE_SIZE);
        res.addOverride(
                R.dimen.overridable_minimal_size_pip_resizable_task,
                OVERRIDABLE_MIN_SIZE);
        res.addOverride(
                R.string.config_defaultPictureInPictureScreenEdgeInsets,
                "16x16");
        res.addOverride(
                com.android.internal.R.dimen.config_pictureInPictureMinAspectRatio,
                MIN_ASPECT_RATIO);
        res.addOverride(
                com.android.internal.R.dimen.config_pictureInPictureMaxAspectRatio,
                MAX_ASPECT_RATIO);

        mDefaultDisplayInfo = new DisplayInfo();
        mDefaultDisplayInfo.displayId = 1;
        mDefaultDisplayInfo.logicalWidth = 1000;
        mDefaultDisplayInfo.logicalHeight = 1500;
    }

    @Test
    public void getDefaultAspectRatio() {
        assertEquals("Default aspect ratio matches resources",
                DEFAULT_ASPECT_RATIO, mPipBoundsAlgorithm.getDefaultAspectRatio(),
                ASPECT_RATIO_ERROR_MARGIN);
    }

    @Test
    public void onConfigurationChanged_reloadResources() {
        final float newDefaultAspectRatio = (DEFAULT_ASPECT_RATIO + MAX_ASPECT_RATIO) / 2;
        final TestableResources res = mContext.getOrCreateTestableResources();
        res.addOverride(R.dimen.config_pictureInPictureDefaultAspectRatio,
                newDefaultAspectRatio);

        mPipBoundsAlgorithm.onConfigurationChanged(mContext);

        assertEquals("Default aspect ratio should be reloaded",
                mPipBoundsAlgorithm.getDefaultAspectRatio(), newDefaultAspectRatio,
                ASPECT_RATIO_ERROR_MARGIN);
    }

    @Test
    public void getDefaultBounds_noOverrideMinSize_matchesDefaultSizeAndAspectRatio() {
        final Size defaultSize = mSizeSpecSource.getDefaultSize(DEFAULT_ASPECT_RATIO);

        mPipBoundsState.setOverrideMinSize(null);
        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        assertEquals(defaultSize, new Size(defaultBounds.width(), defaultBounds.height()));
        assertEquals(DEFAULT_ASPECT_RATIO, getRectAspectRatio(defaultBounds),
                ASPECT_RATIO_ERROR_MARGIN);
    }

    @Test
    public void getDefaultBounds_widerOverrideMinSize_matchesMinSizeWidthAndDefaultAspectRatio() {
        overrideDefaultAspectRatio(1.0f);
        // The min size's aspect ratio is greater than the default aspect ratio.
        final Size overrideMinSize = new Size(150, 120);

        mPipBoundsState.setOverrideMinSize(overrideMinSize);
        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        // The default aspect ratio should trump the min size aspect ratio.
        assertEquals(DEFAULT_ASPECT_RATIO, getRectAspectRatio(defaultBounds),
                ASPECT_RATIO_ERROR_MARGIN);
        // The width of the min size is still used with the default aspect ratio.
        assertEquals(overrideMinSize.getWidth(), defaultBounds.width());
    }

    @Test
    public void getDefaultBounds_tallerOverrideMinSize_matchesMinSizeHeightAndDefaultAspectRatio() {
        overrideDefaultAspectRatio(1.0f);
        // The min size's aspect ratio is greater than the default aspect ratio.
        final Size overrideMinSize = new Size(120, 150);

        mPipBoundsState.setOverrideMinSize(overrideMinSize);
        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        // The default aspect ratio should trump the min size aspect ratio.
        assertEquals(DEFAULT_ASPECT_RATIO, getRectAspectRatio(defaultBounds),
                ASPECT_RATIO_ERROR_MARGIN);
        // The height of the min size is still used with the default aspect ratio.
        assertEquals(overrideMinSize.getHeight(), defaultBounds.height());
    }

    @Test
    public void getDefaultBounds_imeShowing_offsetByImeHeight() {
        final int imeHeight = 30;
        mPipBoundsState.setImeVisibility(false, 0);
        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        mPipBoundsState.setImeVisibility(true, imeHeight);
        final Rect defaultBoundsWithIme = mPipBoundsAlgorithm.getDefaultBounds();

        assertEquals(imeHeight, defaultBounds.top - defaultBoundsWithIme.top);
    }

    @Test
    public void getDefaultBounds_shelfShowing_offsetByShelfHeight() {
        final int shelfHeight = 30;
        mPipBoundsState.setShelfVisibility(false, 0);
        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        mPipBoundsState.setShelfVisibility(true, shelfHeight);
        final Rect defaultBoundsWithShelf = mPipBoundsAlgorithm.getDefaultBounds();

        assertEquals(shelfHeight, defaultBounds.top - defaultBoundsWithShelf.top);
    }

    @Test
    public void getDefaultBounds_imeAndShelfShowing_offsetByTallest() {
        final int imeHeight = 30;
        final int shelfHeight = 40;
        mPipBoundsState.setImeVisibility(false, 0);
        mPipBoundsState.setShelfVisibility(false, 0);
        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        mPipBoundsState.setImeVisibility(true, imeHeight);
        mPipBoundsState.setShelfVisibility(true, shelfHeight);
        final Rect defaultBoundsWithIme = mPipBoundsAlgorithm.getDefaultBounds();

        assertEquals(shelfHeight, defaultBounds.top - defaultBoundsWithIme.top);
    }

    @Test
    public void getDefaultBounds_boundsAtDefaultGravity() {
        final Rect insetBounds = new Rect();
        mPipBoundsAlgorithm.getInsetBounds(insetBounds);
        overrideDefaultStackGravity(Gravity.END | Gravity.BOTTOM);

        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        assertEquals(insetBounds.bottom, defaultBounds.bottom);
        assertEquals(insetBounds.right, defaultBounds.right);
    }

    @Test
    public void getNormalBounds_invalidAspectRatio_returnsDefaultBounds() {
        final Rect defaultBounds = mPipBoundsAlgorithm.getDefaultBounds();

        // Set an invalid current aspect ratio.
        mPipBoundsState.setAspectRatio(MIN_ASPECT_RATIO / 2);
        final Rect normalBounds = mPipBoundsAlgorithm.getNormalBounds();

        assertEquals(defaultBounds, normalBounds);
    }

    @Test
    public void getNormalBounds_validAspectRatio_returnsAdjustedDefaultBounds() {
        final Rect defaultBoundsAdjustedToAspectRatio = mPipBoundsAlgorithm.getDefaultBounds();
        mPipBoundsAlgorithm.transformBoundsToAspectRatio(defaultBoundsAdjustedToAspectRatio,
                MIN_ASPECT_RATIO, false /* useCurrentMinEdgeSize */, false /* useCurrentSize */);

        // Set a valid current aspect ratio different that the default.
        mPipBoundsState.setAspectRatio(MIN_ASPECT_RATIO);
        final Rect normalBounds = mPipBoundsAlgorithm.getNormalBounds();

        assertEquals(defaultBoundsAdjustedToAspectRatio, normalBounds);
    }

    @Test
    public void getEntryDestinationBounds_returnBoundsMatchesAspectRatio() {
        final float[] aspectRatios = new float[] {
                (MIN_ASPECT_RATIO + DEFAULT_ASPECT_RATIO) / 2,
                DEFAULT_ASPECT_RATIO,
                (MAX_ASPECT_RATIO + DEFAULT_ASPECT_RATIO) / 2
        };
        for (float aspectRatio : aspectRatios) {
            mPipBoundsState.setAspectRatio(aspectRatio);
            final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
            final float actualAspectRatio = getRectAspectRatio(destinationBounds);
            assertEquals("Destination bounds matches the given aspect ratio",
                    aspectRatio, actualAspectRatio, ASPECT_RATIO_ERROR_MARGIN);
        }
    }

    @Test
    public void getEntryDestinationBounds_invalidAspectRatio_returnsDefaultAspectRatio() {
        final float[] invalidAspectRatios = new float[] {
                MIN_ASPECT_RATIO / 2,
                MAX_ASPECT_RATIO * 2
        };
        for (float aspectRatio : invalidAspectRatios) {
            mPipBoundsState.setAspectRatio(aspectRatio);
            final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
            final float actualAspectRatio =
                    destinationBounds.width() / (destinationBounds.height() * 1f);
            assertEquals("Destination bounds fallbacks to default aspect ratio",
                    mPipBoundsAlgorithm.getDefaultAspectRatio(), actualAspectRatio,
                    ASPECT_RATIO_ERROR_MARGIN);
        }
    }

    @Test
    public void  getAdjustedDestinationBounds_returnBoundsMatchesAspectRatio() {
        final float aspectRatio = (DEFAULT_ASPECT_RATIO + MAX_ASPECT_RATIO) / 2;
        final Rect currentBounds = new Rect(0, 0, 0, 100);
        currentBounds.right = (int) (currentBounds.height() * aspectRatio) + currentBounds.left;

        mPipBoundsState.setAspectRatio(aspectRatio);
        final Rect destinationBounds = mPipBoundsAlgorithm.getAdjustedDestinationBounds(
                currentBounds, aspectRatio);

        final float actualAspectRatio =
                destinationBounds.width() / (destinationBounds.height() * 1f);
        assertEquals("Destination bounds matches the given aspect ratio",
                aspectRatio, actualAspectRatio, ASPECT_RATIO_ERROR_MARGIN);
    }

    @Test
    public void getEntryDestinationBounds_withMinSize_returnMinBounds() {
        final float[] aspectRatios = new float[] {
                (MIN_ASPECT_RATIO + DEFAULT_ASPECT_RATIO) / 2,
                DEFAULT_ASPECT_RATIO,
                (MAX_ASPECT_RATIO + DEFAULT_ASPECT_RATIO) / 2
        };
        final Size[] minimalSizes = new Size[] {
                new Size((int) (200 * aspectRatios[0]), 200),
                new Size((int) (200 * aspectRatios[1]), 200),
                new Size((int) (200 * aspectRatios[2]), 200)
        };
        for (int i = 0; i < aspectRatios.length; i++) {
            final float aspectRatio = aspectRatios[i];
            final Size minimalSize = minimalSizes[i];
            mPipBoundsState.setAspectRatio(aspectRatio);
            mPipBoundsState.setOverrideMinSize(minimalSize);
            final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
            assertTrue("Destination bounds is no smaller than minimal requirement",
                    (destinationBounds.width() == minimalSize.getWidth()
                            && destinationBounds.height() >= minimalSize.getHeight())
                            || (destinationBounds.height() == minimalSize.getHeight()
                            && destinationBounds.width() >= minimalSize.getWidth()));
            final float actualAspectRatio =
                    destinationBounds.width() / (destinationBounds.height() * 1f);
            assertEquals("Destination bounds matches the given aspect ratio",
                    aspectRatio, actualAspectRatio, ASPECT_RATIO_ERROR_MARGIN);
        }
    }

    @Test
    public void getAdjustedDestinationBounds_ignoreMinBounds() {
        final float aspectRatio = (DEFAULT_ASPECT_RATIO + MAX_ASPECT_RATIO) / 2;
        final Rect currentBounds = new Rect(0, 0, 0, 100);
        currentBounds.right = (int) (currentBounds.height() * aspectRatio) + currentBounds.left;
        final Size minSize = new Size(currentBounds.width() / 2, currentBounds.height() / 2);

        mPipBoundsState.setAspectRatio(aspectRatio);
        mPipBoundsState.setOverrideMinSize(minSize);
        final Rect destinationBounds = mPipBoundsAlgorithm.getAdjustedDestinationBounds(
                currentBounds, aspectRatio);

        assertTrue("Destination bounds ignores minimal size",
                destinationBounds.width() > minSize.getWidth()
                        && destinationBounds.height() > minSize.getHeight());
    }

    @Test
    public void getEntryDestinationBounds_reentryStateExists_restoreProportionalSize() {
        mPipBoundsState.setAspectRatio(DEFAULT_ASPECT_RATIO);
        final Size maxSize = mSizeSpecSource.getMaxSize(DEFAULT_ASPECT_RATIO);
        mPipBoundsState.setMaxSize(maxSize.getWidth(), maxSize.getHeight());
        final Rect reentryBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
        reentryBounds.scale(1.25f);
        mPipBoundsState.setBounds(reentryBounds); // this updates the bounds scale used in reentry

        final float reentrySnapFraction = mPipBoundsAlgorithm.getSnapFraction(reentryBounds);

        mPipBoundsState.saveReentryState(reentrySnapFraction);
        final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();

        assertEquals(reentryBounds.width(), destinationBounds.width());
        assertEquals(reentryBounds.height(), destinationBounds.height());
    }

    @Test
    public void getEntryDestinationBounds_reentryStateExists_restoreLastPosition() {
        mPipBoundsState.setAspectRatio(DEFAULT_ASPECT_RATIO);
        final Rect reentryBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
        reentryBounds.offset(0, -100);
        final float reentrySnapFraction = mPipBoundsAlgorithm.getSnapFraction(reentryBounds);

        mPipBoundsState.saveReentryState(reentrySnapFraction);

        final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();

        assertBoundsInclusionWithMargin("restoreLastPosition", reentryBounds, destinationBounds);
    }

    @Test
    public void setShelfHeight_offsetBounds() {
        final int shelfHeight = 100;
        mPipBoundsState.setAspectRatio(DEFAULT_ASPECT_RATIO);
        final Rect oldPosition = mPipBoundsAlgorithm.getEntryDestinationBounds();

        mPipBoundsState.setShelfVisibility(true, shelfHeight);
        final Rect newPosition = mPipBoundsAlgorithm.getEntryDestinationBounds();

        oldPosition.offset(0, -shelfHeight);
        assertBoundsInclusionWithMargin("offsetBounds by shelf", oldPosition, newPosition);
    }

    @Test
    public void onImeVisibilityChanged_offsetBounds() {
        final int imeHeight = 100;
        mPipBoundsState.setAspectRatio(DEFAULT_ASPECT_RATIO);
        final Rect oldPosition = mPipBoundsAlgorithm.getEntryDestinationBounds();

        mPipBoundsState.setImeVisibility(true, imeHeight);
        final Rect newPosition = mPipBoundsAlgorithm.getEntryDestinationBounds();

        oldPosition.offset(0, -imeHeight);
        assertBoundsInclusionWithMargin("offsetBounds by IME", oldPosition, newPosition);
    }

    @Test
    public void getEntryDestinationBounds_noReentryState_useDefaultBounds() {
        mPipBoundsState.setAspectRatio(DEFAULT_ASPECT_RATIO);
        final Rect defaultBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();

        mPipBoundsState.clearReentryState();

        final Rect actualBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();

        assertBoundsInclusionWithMargin("useDefaultBounds", defaultBounds, actualBounds);
    }

    @Test
    public void adjustNormalBoundsToFitMenu_alreadyFits() {
        final Rect normalBounds = new Rect(0, 0, 400, 711);
        final Size minMenuSize = new Size(396, 292);
        mPipBoundsState.setAspectRatio(
                ((float) normalBounds.width()) / ((float) normalBounds.height()));

        final Rect bounds =
                mPipBoundsAlgorithm.adjustNormalBoundsToFitMenu(normalBounds, minMenuSize);

        assertEquals(normalBounds, bounds);
    }

    @Test
    public void adjustNormalBoundsToFitMenu_widthTooSmall() {
        final Rect normalBounds = new Rect(0, 0, 297, 528);
        final Size minMenuSize = new Size(396, 292);
        mPipBoundsState.setAspectRatio(
                ((float) normalBounds.width()) / ((float) normalBounds.height()));

        final Rect bounds =
                mPipBoundsAlgorithm.adjustNormalBoundsToFitMenu(normalBounds, minMenuSize);

        assertEquals(minMenuSize.getWidth(), bounds.width());
        assertEquals(minMenuSize.getWidth() / mPipBoundsState.getAspectRatio(),
                bounds.height(), 0.3f);
    }

    @Test
    public void adjustNormalBoundsToFitMenu_heightTooSmall() {
        final Rect normalBounds = new Rect(0, 0, 400, 280);
        final Size minMenuSize = new Size(396, 292);
        mPipBoundsState.setAspectRatio(
                ((float) normalBounds.width()) / ((float) normalBounds.height()));

        final Rect bounds =
                mPipBoundsAlgorithm.adjustNormalBoundsToFitMenu(normalBounds, minMenuSize);

        assertEquals(minMenuSize.getHeight(), bounds.height());
        assertEquals(minMenuSize.getHeight() * mPipBoundsState.getAspectRatio(),
                bounds.width(), 0.3f);
    }

    @Test
    public void adjustNormalBoundsToFitMenu_widthAndHeightTooSmall() {
        final Rect normalBounds = new Rect(0, 0, 350, 280);
        final Size minMenuSize = new Size(396, 292);
        mPipBoundsState.setAspectRatio(
                ((float) normalBounds.width()) / ((float) normalBounds.height()));

        final Rect bounds =
                mPipBoundsAlgorithm.adjustNormalBoundsToFitMenu(normalBounds, minMenuSize);

        assertEquals(minMenuSize.getWidth(), bounds.width());
        assertEquals(minMenuSize.getWidth() / mPipBoundsState.getAspectRatio(),
                bounds.height(), 0.3f);
    }

    private void overrideDefaultAspectRatio(float aspectRatio) {
        final TestableResources res = mContext.getOrCreateTestableResources();
        res.addOverride(
                R.dimen.config_pictureInPictureDefaultAspectRatio,
                aspectRatio);
        mPipBoundsAlgorithm.onConfigurationChanged(mContext);
    }

    private void overrideDefaultStackGravity(int stackGravity) {
        final TestableResources res = mContext.getOrCreateTestableResources();
        res.addOverride(
                R.integer.config_defaultPictureInPictureGravity,
                stackGravity);
        mPipBoundsAlgorithm.onConfigurationChanged(mContext);
    }

    private void assertBoundsInclusionWithMargin(String from, Rect expected, Rect actual) {
        final Rect expectedWithMargin = new Rect(expected);
        expectedWithMargin.inset(-ROUNDING_ERROR_MARGIN, -ROUNDING_ERROR_MARGIN);
        assertTrue(from + ": expect " + expected
                + " contains " + actual
                + " with error margin " + ROUNDING_ERROR_MARGIN,
                expectedWithMargin.contains(actual));
    }

    private static float getRectAspectRatio(Rect rect) {
        return rect.width() / (rect.height() * 1f);
    }
}
