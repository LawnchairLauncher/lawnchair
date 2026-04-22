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

import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link PipSnapAlgorithm}. **/
@RunWith(AndroidTestingRunner.class)
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class PipSnapAlgorithmTest extends ShellTestCase {
    private static final int DEFAULT_STASH_OFFSET = 32;
    private static final Rect DISPLAY_BOUNDS = new Rect(0, 0, 2000, 2000);
    private static final Rect STACK_BOUNDS_CENTERED = new Rect(900, 900, 1100, 1100);
    private static final Rect INSET_BOUNDS_EMPTY = new Rect(0, 0, 0, 0);
    private static final Rect INSET_BOUNDS_RIGHT = new Rect(0, 0, 200, 0);
    private static final Rect MOVEMENT_BOUNDS = new Rect(0, 0,
            DISPLAY_BOUNDS.width() - STACK_BOUNDS_CENTERED.width(),
            DISPLAY_BOUNDS.width() - STACK_BOUNDS_CENTERED.width());

    private PipSnapAlgorithm mPipSnapAlgorithm;

    @Before
    public void setUp() {
        mPipSnapAlgorithm = new PipSnapAlgorithm();
    }

    @Test
    public void testApplySnapFraction_topEdge() {
        final float snapFraction = 0.25f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction);

        assertEquals(MOVEMENT_BOUNDS.width() / 4, bounds.left);
        assertEquals(MOVEMENT_BOUNDS.top, bounds.top);
    }

    @Test
    public void testApplySnapFraction_rightEdge() {
        final float snapFraction = 1.5f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction);

        assertEquals(MOVEMENT_BOUNDS.right, bounds.left);
        assertEquals(MOVEMENT_BOUNDS.height() / 2, bounds.top);
    }

    @Test
    public void testApplySnapFraction_bottomEdge() {
        final float snapFraction = 2.25f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction);

        assertEquals((int) (MOVEMENT_BOUNDS.width() * 0.75f), bounds.left);
        assertEquals(MOVEMENT_BOUNDS.bottom, bounds.top);
    }

    @Test
    public void testApplySnapFraction_leftEdge() {
        final float snapFraction = 3.75f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction);

        assertEquals(MOVEMENT_BOUNDS.left, bounds.left);
        assertEquals((int) (MOVEMENT_BOUNDS.height() * 0.25f), bounds.top);
    }

    @Test
    public void testApplySnapFraction_notStashed_isNotOffBounds() {
        final float snapFraction = 2f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction,
                PipBoundsState.STASH_TYPE_NONE, DEFAULT_STASH_OFFSET, DISPLAY_BOUNDS,
                INSET_BOUNDS_EMPTY);

        assertEquals(MOVEMENT_BOUNDS.right, bounds.left);
        assertEquals(MOVEMENT_BOUNDS.bottom, bounds.top);
    }

    @Test
    public void testApplySnapFraction_stashedLeft() {
        final float snapFraction = 3f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction,
                PipBoundsState.STASH_TYPE_LEFT, DEFAULT_STASH_OFFSET, DISPLAY_BOUNDS,
                INSET_BOUNDS_EMPTY);

        final int offBoundsWidth = bounds.width() - DEFAULT_STASH_OFFSET;
        assertEquals(MOVEMENT_BOUNDS.left - offBoundsWidth, bounds.left);
        assertEquals(MOVEMENT_BOUNDS.bottom, bounds.top);
    }

    @Test
    public void testApplySnapFraction_stashedRight() {
        final float snapFraction = 2f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction,
                PipBoundsState.STASH_TYPE_RIGHT, DEFAULT_STASH_OFFSET, DISPLAY_BOUNDS,
                INSET_BOUNDS_EMPTY);

        assertEquals(DISPLAY_BOUNDS.right - DEFAULT_STASH_OFFSET, bounds.left);
        assertEquals(MOVEMENT_BOUNDS.bottom, bounds.top);
    }

    @Test
    public void testApplySnapFraction_stashedRight_withInset() {
        final float snapFraction = 2f;
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);

        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, snapFraction,
                PipBoundsState.STASH_TYPE_RIGHT, DEFAULT_STASH_OFFSET, DISPLAY_BOUNDS,
                INSET_BOUNDS_RIGHT);

        assertEquals(DISPLAY_BOUNDS.right - DEFAULT_STASH_OFFSET - INSET_BOUNDS_RIGHT.right,
                bounds.left);
        assertEquals(MOVEMENT_BOUNDS.bottom, bounds.top);
    }

    @Test
    public void testSnapRectToClosestEdge_rightEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move the centered rect slightly to the right side.
        bounds.offset(10, 0);

        mPipSnapAlgorithm.snapRectToClosestEdge(bounds, MOVEMENT_BOUNDS, bounds,
                PipBoundsState.STASH_TYPE_NONE);

        assertEquals(MOVEMENT_BOUNDS.right, bounds.left);
    }

    @Test
    public void testSnapRectToClosestEdge_leftEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move the centered rect slightly to the left side.
        bounds.offset(-10, 0);

        mPipSnapAlgorithm.snapRectToClosestEdge(bounds, MOVEMENT_BOUNDS, bounds,
                PipBoundsState.STASH_TYPE_NONE);

        assertEquals(MOVEMENT_BOUNDS.left, bounds.left);
    }

    @Test
    public void testSnapRectToClosestEdge_topEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move the centered rect slightly to the top half.
        bounds.offset(0, -10);

        mPipSnapAlgorithm.snapRectToClosestEdge(bounds, MOVEMENT_BOUNDS, bounds,
                PipBoundsState.STASH_TYPE_NONE);

        assertEquals(MOVEMENT_BOUNDS.top, bounds.top);
    }

    @Test
    public void testSnapRectToClosestEdge_bottomEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move the centered rect slightly to the bottom half.
        bounds.offset(0, 10);

        mPipSnapAlgorithm.snapRectToClosestEdge(bounds, MOVEMENT_BOUNDS, bounds,
                PipBoundsState.STASH_TYPE_NONE);

        assertEquals(MOVEMENT_BOUNDS.bottom, bounds.top);
    }

    @Test
    public void testSnapRectToClosestEdge_stashed_unStahesBounds() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Stash it on the left side.
        mPipSnapAlgorithm.applySnapFraction(bounds, MOVEMENT_BOUNDS, 3.5f,
                PipBoundsState.STASH_TYPE_LEFT, DEFAULT_STASH_OFFSET, DISPLAY_BOUNDS,
                INSET_BOUNDS_EMPTY);

        mPipSnapAlgorithm.snapRectToClosestEdge(bounds, MOVEMENT_BOUNDS, bounds,
                PipBoundsState.STASH_TYPE_LEFT);

        assertEquals(MOVEMENT_BOUNDS.left, bounds.left);
    }

    @Test
    public void testGetSnapFraction_leftEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move it slightly to the left side.
        bounds.offset(-10, 0);

        final float snapFraction = mPipSnapAlgorithm.getSnapFraction(bounds, MOVEMENT_BOUNDS);

        assertEquals(3.5f, snapFraction, 0.1f);
    }

    @Test
    public void testGetSnapFraction_rightEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move it slightly to the right side.
        bounds.offset(10, 0);

        final float snapFraction = mPipSnapAlgorithm.getSnapFraction(bounds, MOVEMENT_BOUNDS);

        assertEquals(1.5f, snapFraction, 0.1f);
    }

    @Test
    public void testGetSnapFraction_topEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move it slightly to the top half.
        bounds.offset(0, -10);

        final float snapFraction = mPipSnapAlgorithm.getSnapFraction(bounds, MOVEMENT_BOUNDS);

        assertEquals(0.5f, snapFraction, 0.1f);
    }

    @Test
    public void testGetSnapFraction_bottomEdge() {
        final Rect bounds = new Rect(STACK_BOUNDS_CENTERED);
        // Move it slightly to the bottom half.
        bounds.offset(0, 10);

        final float snapFraction = mPipSnapAlgorithm.getSnapFraction(bounds, MOVEMENT_BOUNDS);

        assertEquals(2.5f, snapFraction, 0.1f);
    }
}
