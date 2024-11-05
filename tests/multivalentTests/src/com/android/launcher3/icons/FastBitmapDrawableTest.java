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
package com.android.launcher3.icons;

import static com.android.launcher3.icons.FastBitmapDrawable.CLICK_FEEDBACK_DURATION;
import static com.android.launcher3.icons.FastBitmapDrawable.HOVERED_SCALE;
import static com.android.launcher3.icons.FastBitmapDrawable.HOVER_FEEDBACK_DURATION;
import static com.android.launcher3.icons.FastBitmapDrawable.PRESSED_SCALE;
import static com.android.launcher3.icons.FastBitmapDrawable.SCALE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;

import com.android.launcher3.util.LauncherMultivalentJUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Tests for FastBitmapDrawable.
 */
@SmallTest
@RunWith(LauncherMultivalentJUnit.class)
@UiThreadTest
public class FastBitmapDrawableTest {
    private static final float EPSILON = 0.00001f;

    @Spy
    FastBitmapDrawable mFastBitmapDrawable =
            spy(new FastBitmapDrawable(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)));
    @Mock Drawable mBadge;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        FastBitmapDrawable.setFlagHoverEnabled(true);
        when(mFastBitmapDrawable.isVisible()).thenReturn(true);
        mFastBitmapDrawable.mIsPressed = false;
        mFastBitmapDrawable.mIsHovered = false;
        mFastBitmapDrawable.resetScale();
    }

    @Test
    public void testOnStateChange_noState() {
        int[] state = new int[]{};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No scale changes without state change.
        assertFalse("State change handled.", isHandled);
        assertNull("Scale animation not null.", mFastBitmapDrawable.mScaleAnimation);
    }

    @Test
    public void testOnStateChange_statePressed() {
        int[] state = new int[]{android.R.attr.state_pressed};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to state pressed.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.", mFastBitmapDrawable.mScaleAnimation.getDuration(),
                CLICK_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), PRESSED_SCALE, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator()
                        instanceof AccelerateInterpolator);
    }

    @Test
    public void testOnStateChange_stateHovered() {
        int[] state = new int[]{android.R.attr.state_hovered};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to state hovered.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.", mFastBitmapDrawable.mScaleAnimation.getDuration(),
                HOVER_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), HOVERED_SCALE, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator() instanceof PathInterpolator);
    }

    @Test
    public void testOnStateChange_stateHoveredFlagDisabled() {
        FastBitmapDrawable.setFlagHoverEnabled(false);
        int[] state = new int[]{android.R.attr.state_hovered};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No state change with flag disabled.
        assertFalse("Hover state change handled with flag disabled.", isHandled);
        assertNull("Animation should not run with hover flag disabled.",
                mFastBitmapDrawable.mScaleAnimation);
    }

    @Test
    public void testOnStateChange_statePressedAndHovered() {
        int[] state = new int[]{android.R.attr.state_pressed, android.R.attr.state_hovered};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to pressed state only.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.", mFastBitmapDrawable.mScaleAnimation.getDuration(),
                CLICK_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), PRESSED_SCALE, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator()
                        instanceof AccelerateInterpolator);
    }

    @Test
    public void testOnStateChange_stateHoveredAndPressed() {
        int[] state = new int[]{android.R.attr.state_hovered, android.R.attr.state_pressed};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to pressed state only.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.", mFastBitmapDrawable.mScaleAnimation.getDuration(),
                CLICK_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), PRESSED_SCALE, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator()
                        instanceof AccelerateInterpolator);
    }

    @Test
    public void testOnStateChange_stateHoveredAndPressedToPressed() {
        mFastBitmapDrawable.mIsPressed = true;
        mFastBitmapDrawable.mIsHovered = true;
        SCALE.setValue(mFastBitmapDrawable, PRESSED_SCALE);
        int[] state = new int[]{android.R.attr.state_pressed};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No scale change from pressed state to pressed state.
        assertTrue("State not changed.", isHandled);
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), PRESSED_SCALE, EPSILON);
    }

    @Test
    public void testOnStateChange_stateHoveredAndPressedToHovered() {
        mFastBitmapDrawable.mIsPressed = true;
        mFastBitmapDrawable.mIsHovered = true;
        SCALE.setValue(mFastBitmapDrawable, PRESSED_SCALE);
        int[] state = new int[]{android.R.attr.state_hovered};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No scale change from pressed state to hovered state.
        assertTrue("State not changed.", isHandled);
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), HOVERED_SCALE, EPSILON);
    }

    @Test
    public void testOnStateChange_stateHoveredToPressed() {
        mFastBitmapDrawable.mIsHovered = true;
        SCALE.setValue(mFastBitmapDrawable, HOVERED_SCALE);
        int[] state = new int[]{android.R.attr.state_pressed};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No scale change from pressed state to hovered state.
        assertTrue("State not changed.", isHandled);
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), PRESSED_SCALE, EPSILON);
    }

    @Test
    public void testOnStateChange_statePressedToHovered() {
        mFastBitmapDrawable.mIsPressed = true;
        SCALE.setValue(mFastBitmapDrawable, PRESSED_SCALE);
        int[] state = new int[]{android.R.attr.state_hovered};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No scale change from pressed state to hovered state.
        assertTrue("State not changed.", isHandled);
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), HOVERED_SCALE, EPSILON);
    }

    @Test
    public void testOnStateChange_stateDefaultFromPressed() {
        mFastBitmapDrawable.mIsPressed = true;
        SCALE.setValue(mFastBitmapDrawable, PRESSED_SCALE);
        int[] state = new int[]{};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to default state from pressed state.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.", mFastBitmapDrawable.mScaleAnimation.getDuration(),
                CLICK_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.", (float) SCALE.get(mFastBitmapDrawable), 1f, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator()
                        instanceof DecelerateInterpolator);
    }

    @Test
    public void testOnStateChange_stateDefaultFromHovered() {
        mFastBitmapDrawable.mIsHovered = true;
        SCALE.setValue(mFastBitmapDrawable, HOVERED_SCALE);
        int[] state = new int[]{};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to default state from hovered state.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.", mFastBitmapDrawable.mScaleAnimation.getDuration(),
                HOVER_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.", (float) SCALE.get(mFastBitmapDrawable), 1f, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator() instanceof PathInterpolator);
    }

    @Test
    public void testOnStateChange_stateHoveredWhilePartiallyScaled() {
        SCALE.setValue(mFastBitmapDrawable, 0.5f);
        int[] state = new int[]{android.R.attr.state_hovered};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to hovered state from midway to pressed state.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.",
                mFastBitmapDrawable.mScaleAnimation.getDuration(), HOVER_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), HOVERED_SCALE, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator() instanceof PathInterpolator);
    }

    @Test
    public void testOnStateChange_statePressedWhilePartiallyScaled() {
        SCALE.setValue(mFastBitmapDrawable, 0.5f);
        int[] state = new int[]{android.R.attr.state_pressed};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // Animate to pressed state from midway to hovered state.
        assertTrue("State change not handled.", isHandled);
        assertEquals("Duration not correct.",
                mFastBitmapDrawable.mScaleAnimation.getDuration(), CLICK_FEEDBACK_DURATION);
        mFastBitmapDrawable.mScaleAnimation.end();
        assertEquals("End value not correct.",
                (float) SCALE.get(mFastBitmapDrawable), PRESSED_SCALE, EPSILON);
        assertTrue("Wrong interpolator used.",
                mFastBitmapDrawable.mScaleAnimation.getInterpolator()
                        instanceof AccelerateInterpolator);
    }

    @Test
    public void testOnStateChange_stateDefaultFromPressedNotVisible() {
        when(mFastBitmapDrawable.isVisible()).thenReturn(false);
        mFastBitmapDrawable.mIsPressed = true;
        SCALE.setValue(mFastBitmapDrawable, PRESSED_SCALE);
        clearInvocations(mFastBitmapDrawable);
        int[] state = new int[]{};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No animations when state was pressed but drawable no longer visible. Set values directly.
        assertTrue("State change not handled.", isHandled);
        assertNull("Scale animation not null.", mFastBitmapDrawable.mScaleAnimation);
        assertEquals("End value not correct.", (float) SCALE.get(mFastBitmapDrawable), 1f, EPSILON);
        verify(mFastBitmapDrawable).invalidateSelf();
    }

    @Test
    public void testOnStateChange_stateDefaultFromHoveredNotVisible() {
        when(mFastBitmapDrawable.isVisible()).thenReturn(false);
        mFastBitmapDrawable.mIsHovered = true;
        SCALE.setValue(mFastBitmapDrawable, HOVERED_SCALE);
        clearInvocations(mFastBitmapDrawable);
        int[] state = new int[]{};

        boolean isHandled = mFastBitmapDrawable.onStateChange(state);

        // No animations when state was hovered but drawable no longer visible. Set values directly.
        assertTrue("State change not handled.", isHandled);
        assertNull("Scale animation not null.", mFastBitmapDrawable.mScaleAnimation);
        assertEquals("End value not correct.", (float) SCALE.get(mFastBitmapDrawable), 1f, EPSILON);
        verify(mFastBitmapDrawable).invalidateSelf();
    }

    @Test
    public void testUpdateBadgeAlpha() {
        mFastBitmapDrawable.setBadge(mBadge);

        mFastBitmapDrawable.setAlpha(1);
        mFastBitmapDrawable.setAlpha(0);

        verify(mBadge).setAlpha(1);
        verify(mBadge).setAlpha(0);
    }
}
