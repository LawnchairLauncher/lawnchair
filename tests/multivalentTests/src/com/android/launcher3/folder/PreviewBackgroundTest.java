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
package com.android.launcher3.folder;

import static com.android.launcher3.folder.PreviewBackground.ACCEPT_SCALE_FACTOR;
import static com.android.launcher3.folder.PreviewBackground.CONSUMPTION_ANIMATION_DURATION;
import static com.android.launcher3.folder.PreviewBackground.HOVER_ANIMATION_DURATION;
import static com.android.launcher3.folder.PreviewBackground.HOVER_SCALE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.PathInterpolator;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.CellLayout;
import com.android.launcher3.util.LauncherMultivalentJUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@UiThreadTest
@RunWith(LauncherMultivalentJUnit.class)
public class PreviewBackgroundTest {

    private static final float REST_SCALE = 1f;
    private static final float EPSILON = 0.00001f;

    @Mock
    CellLayout mCellLayout;

    private final PreviewBackground mPreviewBackground =
            new PreviewBackground(InstrumentationRegistry.getInstrumentation().getContext());

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPreviewBackground.mScale = REST_SCALE;
        mPreviewBackground.mIsAccepting = false;
        mPreviewBackground.mIsHovered = false;
        mPreviewBackground.mIsHoveredOrAnimating = false;
        mPreviewBackground.invalidate();
    }

    @Test
    public void testAnimateScale_restToHovered() {
        mPreviewBackground.setHovered(true);
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, HOVER_SCALE, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                HOVER_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator() instanceof PathInterpolator);
        endAnimation();
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_restToNotHovered() {
        mPreviewBackground.setHovered(false);

        assertEquals("Scale changed.", mPreviewBackground.mScale, REST_SCALE, EPSILON);
        assertNull("Animator not null.", mPreviewBackground.mScaleAnimator);
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_hoveredToHovered() {
        mPreviewBackground.mScale = HOVER_SCALE;
        mPreviewBackground.mIsHovered = true;
        mPreviewBackground.mIsHoveredOrAnimating = true;
        mPreviewBackground.invalidate();

        mPreviewBackground.setHovered(true);

        assertEquals("Scale changed.", mPreviewBackground.mScale, HOVER_SCALE, EPSILON);
        assertNull("Animator not null.", mPreviewBackground.mScaleAnimator);
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_hoveredToRest() {
        mPreviewBackground.mScale = HOVER_SCALE;
        mPreviewBackground.mIsHovered = true;
        mPreviewBackground.mIsHoveredOrAnimating = true;
        mPreviewBackground.invalidate();

        mPreviewBackground.setHovered(false);
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, REST_SCALE, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                HOVER_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator() instanceof PathInterpolator);
        endAnimation();
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_restToAccept() {
        mPreviewBackground.animateToAccept(mCellLayout, 0, 0);
        runAnimationToFraction(1f);

        assertEquals("Scale changed.", mPreviewBackground.mScale, ACCEPT_SCALE_FACTOR, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                CONSUMPTION_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator()
                        instanceof AccelerateDecelerateInterpolator);
        endAnimation();
        assertEquals("Scale progress not 1.", mPreviewBackground.getAcceptScaleProgress(), 1,
                EPSILON);
    }

    @Test
    public void testAnimateScale_restToRest() {
        mPreviewBackground.animateToRest();

        assertEquals("Scale changed.", mPreviewBackground.mScale, REST_SCALE, EPSILON);
        assertNull("Animator not null.", mPreviewBackground.mScaleAnimator);
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_acceptToRest() {
        mPreviewBackground.mScale = ACCEPT_SCALE_FACTOR;
        mPreviewBackground.mIsAccepting = true;
        mPreviewBackground.invalidate();

        mPreviewBackground.animateToRest();
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, REST_SCALE, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                CONSUMPTION_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator()
                        instanceof AccelerateDecelerateInterpolator);
        endAnimation();
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_acceptToHover() {
        mPreviewBackground.mScale = ACCEPT_SCALE_FACTOR;
        mPreviewBackground.mIsAccepting = true;
        mPreviewBackground.invalidate();

        mPreviewBackground.mIsAccepting = false;
        mPreviewBackground.setHovered(true);
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, HOVER_SCALE, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                HOVER_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator() instanceof PathInterpolator);
        endAnimation();
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_hoverToAccept() {
        mPreviewBackground.mScale = HOVER_SCALE;
        mPreviewBackground.mIsHovered = true;
        mPreviewBackground.mIsHoveredOrAnimating = true;
        mPreviewBackground.invalidate();

        mPreviewBackground.animateToAccept(mCellLayout, 0, 0);
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, ACCEPT_SCALE_FACTOR, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                CONSUMPTION_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator()
                        instanceof AccelerateDecelerateInterpolator);
        mPreviewBackground.mIsHovered = false;
        endAnimation();
        assertEquals("Scale progress not 1.", mPreviewBackground.getAcceptScaleProgress(), 1,
                EPSILON);
    }

    @Test
    public void testAnimateScale_midwayToHoverToAccept() {
        mPreviewBackground.setHovered(true);
        runAnimationToFraction(0.5f);
        assertTrue("Scale not changed.",
                mPreviewBackground.mScale > REST_SCALE && mPreviewBackground.mScale < HOVER_SCALE);
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);

        mPreviewBackground.animateToAccept(mCellLayout, 0, 0);
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, ACCEPT_SCALE_FACTOR, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                CONSUMPTION_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator()
                        instanceof AccelerateDecelerateInterpolator);
        mPreviewBackground.mIsHovered = false;
        endAnimation();
        assertEquals("Scale progress not 1.", mPreviewBackground.getAcceptScaleProgress(), 1,
                EPSILON);
        assertNull("Animator not null.", mPreviewBackground.mScaleAnimator);
    }

    @Test
    public void testAnimateScale_partWayToAcceptToHover() {
        mPreviewBackground.animateToAccept(mCellLayout, 0, 0);
        runAnimationToFraction(0.25f);
        assertTrue("Scale not changed part way.", mPreviewBackground.mScale > REST_SCALE
                && mPreviewBackground.mScale < ACCEPT_SCALE_FACTOR);

        mPreviewBackground.mIsAccepting = false;
        mPreviewBackground.setHovered(true);
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, HOVER_SCALE, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                HOVER_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator() instanceof PathInterpolator);
        endAnimation();
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_midwayToAcceptEqualsHover() {
        mPreviewBackground.animateToAccept(mCellLayout, 0, 0);
        runAnimationToFraction(0.5f);
        assertEquals("Scale not changed.", mPreviewBackground.mScale, HOVER_SCALE, EPSILON);
        mPreviewBackground.mIsAccepting = false;

        mPreviewBackground.setHovered(true);

        assertEquals("Scale changed.", mPreviewBackground.mScale, HOVER_SCALE, EPSILON);
        assertNull("Animator not null.", mPreviewBackground.mScaleAnimator);
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_midwayToHoverToRest() {
        mPreviewBackground.setHovered(true);
        runAnimationToFraction(0.5f);
        assertTrue("Scale not changed midway.",
                mPreviewBackground.mScale > REST_SCALE && mPreviewBackground.mScale < HOVER_SCALE);

        mPreviewBackground.mIsHovered = false;
        mPreviewBackground.animateToRest();
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, REST_SCALE, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                HOVER_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator() instanceof PathInterpolator);
        endAnimation();
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    @Test
    public void testAnimateScale_midwayToAcceptToRest() {
        mPreviewBackground.animateToAccept(mCellLayout, 0, 0);
        runAnimationToFraction(0.5f);
        assertTrue("Scale not changed.", mPreviewBackground.mScale > REST_SCALE
                && mPreviewBackground.mScale < ACCEPT_SCALE_FACTOR);

        mPreviewBackground.animateToRest();
        runAnimationToFraction(1f);

        assertEquals("Scale not changed.", mPreviewBackground.mScale, REST_SCALE, EPSILON);
        assertEquals("Duration not correct.", mPreviewBackground.mScaleAnimator.getDuration(),
                CONSUMPTION_ANIMATION_DURATION);
        assertTrue("Wrong interpolator used.",
                mPreviewBackground.mScaleAnimator.getInterpolator()
                        instanceof AccelerateDecelerateInterpolator);
        endAnimation();
        assertEquals("Scale progress not 0.", mPreviewBackground.getAcceptScaleProgress(), 0,
                EPSILON);
    }

    private void runAnimationToFraction(float animationFraction) {
        mPreviewBackground.mScaleAnimator.setCurrentFraction(animationFraction);
    }

    private void endAnimation() {
        mPreviewBackground.mScaleAnimator.end();
    }
}
