/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PhonePipKeepClearAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Set;

/**
 * Unit tests against {@link PhonePipKeepClearAlgorithm}.
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class PhonePipKeepClearAlgorithmTest extends ShellTestCase {

    private PhonePipKeepClearAlgorithm mPipKeepClearAlgorithm;

    @Mock private PipBoundsAlgorithm mMockPipBoundsAlgorithm;
    @Mock private PipBoundsState mMockPipBoundsState;

    private static final Rect DISPLAY_BOUNDS = new Rect(0, 0, 1000, 1000);

    @Before
    public void setUp() throws Exception {
        mPipKeepClearAlgorithm = new PhonePipKeepClearAlgorithm(mContext);
    }

    @Test
    public void findUnoccludedPosition_withCollidingRestrictedKeepClearArea_movesBounds() {
        final Rect inBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(50, 50, 150, 150);

        final Rect outBounds = mPipKeepClearAlgorithm.findUnoccludedPosition(inBounds,
                Set.of(keepClearRect), Set.of(), DISPLAY_BOUNDS);

        assertFalse(outBounds.contains(keepClearRect));
    }

    @Test
    public void findUnoccludedPosition_withNonCollidingRestrictedKeepClearArea_boundsUnchanged() {
        final Rect inBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(100, 100, 150, 150);

        final Rect outBounds = mPipKeepClearAlgorithm.findUnoccludedPosition(inBounds,
                Set.of(keepClearRect), Set.of(), DISPLAY_BOUNDS);

        assertEquals(inBounds, outBounds);
    }

    @Test
    public void findUnoccludedPosition_withCollidingUnrestrictedKeepClearArea_moveBounds() {
        final Rect inBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(50, 50, 150, 150);

        final Rect outBounds = mPipKeepClearAlgorithm.findUnoccludedPosition(inBounds, Set.of(),
                Set.of(keepClearRect), DISPLAY_BOUNDS);

        assertFalse(outBounds.contains(keepClearRect));
    }

    @Test
    public void findUnoccludedPosition_withNonCollidingUnrestrictedKeepClearArea_boundsUnchanged() {
        final Rect inBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(100, 100, 150, 150);

        final Rect outBounds = mPipKeepClearAlgorithm.findUnoccludedPosition(inBounds, Set.of(),
                Set.of(keepClearRect), DISPLAY_BOUNDS);

        assertEquals(inBounds, outBounds);
    }

    @Test
    public void adjust_withCollidingRestrictedKeepClearArea_moveBounds() {
        final Rect pipBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(50, 50, 150, 150);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        when(mMockPipBoundsState.getRestrictedKeepClearAreas()).thenReturn(Set.of(keepClearRect));
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertFalse(outBounds.contains(keepClearRect));
    }

    @Test
    public void adjust_withNonCollidingRestrictedKeepClearArea_boundsUnchanged() {
        final Rect pipBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(100, 100, 150, 150);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        when(mMockPipBoundsState.getRestrictedKeepClearAreas()).thenReturn(Set.of(keepClearRect));
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertFalse(outBounds.contains(keepClearRect));
    }

    @Test
    public void adjust_withCollidingRestrictedKeepClearArea_whileStashed_boundsUnchanged() {
        final Rect pipBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(50, 50, 150, 150);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        when(mMockPipBoundsState.isStashed()).thenReturn(true);
        when(mMockPipBoundsState.getRestrictedKeepClearAreas()).thenReturn(Set.of(keepClearRect));
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(pipBounds, outBounds);
    }

    @Test
    public void adjust_withNonCollidingRestrictedKeepClearArea_whileStashed_boundsUnchanged() {
        final Rect pipBounds = new Rect(0, 0, 100, 100);
        final Rect keepClearRect = new Rect(100, 100, 150, 150);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        when(mMockPipBoundsState.isStashed()).thenReturn(true);
        when(mMockPipBoundsState.getRestrictedKeepClearAreas()).thenReturn(Set.of(keepClearRect));
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(pipBounds, outBounds);
    }

    @Test
    public void adjust_aboveDisplayBounds_onLeftEdge_appliesBottomLeftGravity() {
        final Rect pipBounds = new Rect(
                0, DISPLAY_BOUNDS.top - 50, 100, DISPLAY_BOUNDS.top + 50);
        final Rect expected = new Rect(
                0, DISPLAY_BOUNDS.bottom - 100, 100, DISPLAY_BOUNDS.bottom);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));
        when(mMockPipBoundsAlgorithm.getSnapFraction(any(Rect.class))).thenReturn(0f);

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(expected, outBounds);
    }

    @Test
    public void adjust_belowDisplayBounds_onLeftEdge_appliesBottomLeftGravity() {
        final Rect pipBounds = new Rect(
                0, DISPLAY_BOUNDS.bottom - 50, 100, DISPLAY_BOUNDS.bottom + 50);
        final Rect expected = new Rect(
                0, DISPLAY_BOUNDS.bottom - 100, 100, DISPLAY_BOUNDS.bottom);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));
        when(mMockPipBoundsAlgorithm.getSnapFraction(any(Rect.class))).thenReturn(3f);

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(expected, outBounds);
    }

    @Test
    public void adjust_aboveDisplayBounds_onRightEdge_appliesBottomRightGravity() {
        final Rect pipBounds = new Rect(
                DISPLAY_BOUNDS.right - 100, DISPLAY_BOUNDS.top - 50,
                DISPLAY_BOUNDS.right, DISPLAY_BOUNDS.top + 50);
        final Rect expected = new Rect(
                DISPLAY_BOUNDS.right - 100, DISPLAY_BOUNDS.bottom - 100,
                DISPLAY_BOUNDS.right, DISPLAY_BOUNDS.bottom);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));
        when(mMockPipBoundsAlgorithm.getSnapFraction(any(Rect.class))).thenReturn(1f);

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(expected, outBounds);
    }

    @Test
    public void adjust_belowDisplayBounds_onRightEdge_appliesBottomRightGravity() {
        final Rect pipBounds = new Rect(
                DISPLAY_BOUNDS.right - 100, DISPLAY_BOUNDS.bottom - 50,
                DISPLAY_BOUNDS.right, DISPLAY_BOUNDS.bottom + 50);
        final Rect expected = new Rect(
                DISPLAY_BOUNDS.right - 100, DISPLAY_BOUNDS.bottom - 100,
                DISPLAY_BOUNDS.right, DISPLAY_BOUNDS.bottom);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));
        when(mMockPipBoundsAlgorithm.getSnapFraction(any(Rect.class))).thenReturn(2f);

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(expected, outBounds);
    }

    @Test
    public void adjust_whileStashed_aboveDisplayBounds_alignsToBottomInset() {
        final Rect pipBounds = new Rect(
                0, DISPLAY_BOUNDS.top - 50, 100, DISPLAY_BOUNDS.top + 50);
        final Rect expected = new Rect(
                0, DISPLAY_BOUNDS.bottom - 100, 100, DISPLAY_BOUNDS.bottom);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        when(mMockPipBoundsState.isStashed()).thenReturn(true);
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(expected, outBounds);
    }

    @Test
    public void adjust_whileStashed_belowDisplayBounds_alignsToBottomInset() {
        final Rect pipBounds = new Rect(
                0, DISPLAY_BOUNDS.bottom - 50, 100, DISPLAY_BOUNDS.bottom + 50);
        final Rect expected = new Rect(
                0, DISPLAY_BOUNDS.bottom - 100, 100, DISPLAY_BOUNDS.bottom);
        when(mMockPipBoundsState.getBounds()).thenReturn(pipBounds);
        when(mMockPipBoundsState.isStashed()).thenReturn(true);
        doAnswer(invocation -> {
            Rect arg0 = invocation.getArgument(0);
            arg0.set(DISPLAY_BOUNDS);
            return null;
        }).when(mMockPipBoundsAlgorithm).getInsetBounds(any(Rect.class));

        final Rect outBounds = mPipKeepClearAlgorithm.adjust(
                mMockPipBoundsState, mMockPipBoundsAlgorithm);

        assertEquals(expected, outBounds);
    }
}
