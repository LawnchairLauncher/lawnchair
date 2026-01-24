/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.common.pip;

import static com.android.wm.shell.common.pip.PipDoubleTapHelper.SIZE_SPEC_CUSTOM;
import static com.android.wm.shell.common.pip.PipDoubleTapHelper.SIZE_SPEC_DEFAULT;
import static com.android.wm.shell.common.pip.PipDoubleTapHelper.SIZE_SPEC_MAX;
import static com.android.wm.shell.common.pip.PipDoubleTapHelper.nextSizeSpec;

import static org.mockito.Mockito.when;

import android.graphics.Point;
import android.graphics.Rect;
import android.testing.AndroidTestingRunner;

import com.android.wm.shell.ShellTestCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * Unit test against {@link com.android.wm.shell.common.pip.PipDoubleTapHelper}.
 */
@RunWith(AndroidTestingRunner.class)
public class PipDoubleTapHelperTest extends ShellTestCase {
    @Mock private PipBoundsState mMockPipBoundsState;

    // Actual dimension guidelines of the PiP bounds.
    private static final int MAX_EDGE_SIZE = 100;
    private static final int DEFAULT_EDGE_SIZE = 60;
    private static final int MIN_EDGE_SIZE = 50;
    private static final int AVERAGE_EDGE_SIZE = (MAX_EDGE_SIZE + MIN_EDGE_SIZE) / 2;

    @Before
    public void setUp() {
        // define pip bounds
        when(mMockPipBoundsState.getMaxSize()).thenReturn(new Point(MAX_EDGE_SIZE, MAX_EDGE_SIZE));
        when(mMockPipBoundsState.getMinSize()).thenReturn(new Point(MIN_EDGE_SIZE, MIN_EDGE_SIZE));

        final Rect normalBounds = new Rect(0, 0, DEFAULT_EDGE_SIZE, DEFAULT_EDGE_SIZE);
        when(mMockPipBoundsState.getNormalBounds()).thenReturn(normalBounds);
    }

    @Test
    public void nextSizeSpec_resizedWiderThanAverage_returnDefaultThenCustom() {
        final int resizeEdgeSize = (MAX_EDGE_SIZE + AVERAGE_EDGE_SIZE) / 2;
        final Rect userResizeBounds = new Rect(0, 0, resizeEdgeSize, resizeEdgeSize);
        when(mMockPipBoundsState.getBounds()).thenReturn(userResizeBounds);
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_DEFAULT);

        // once we toggle to DEFAULT only PiP bounds state gets updated - not the user resize bounds
        when(mMockPipBoundsState.getBounds()).thenReturn(
                new Rect(0, 0, DEFAULT_EDGE_SIZE, DEFAULT_EDGE_SIZE));
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_CUSTOM);
    }

    @Test
    public void nextSizeSpec_resizedSmallerThanAverage_returnMaxThenCustom() {
        final int resizeEdgeSize = (AVERAGE_EDGE_SIZE + MIN_EDGE_SIZE) / 2;
        final Rect userResizeBounds = new Rect(0, 0, resizeEdgeSize, resizeEdgeSize);
        when(mMockPipBoundsState.getBounds()).thenReturn(userResizeBounds);
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_MAX);

        // Once we toggle to MAX our screen size gets updated but not the user resize bounds
        when(mMockPipBoundsState.getBounds()).thenReturn(
                new Rect(0, 0, MAX_EDGE_SIZE, MAX_EDGE_SIZE));
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_CUSTOM);
    }

    @Test
    public void nextSizeSpec_resizedToMax_returnDefault() {
        final Rect userResizeBounds = new Rect(0, 0, MAX_EDGE_SIZE, MAX_EDGE_SIZE);
        when(mMockPipBoundsState.getBounds()).thenReturn(userResizeBounds);
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_DEFAULT);
    }

    @Test
    public void nextSizeSpec_resizedToDefault_returnMax() {
        final Rect userResizeBounds = new Rect(0, 0, DEFAULT_EDGE_SIZE, DEFAULT_EDGE_SIZE);
        when(mMockPipBoundsState.getBounds()).thenReturn(userResizeBounds);
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_MAX);
    }

    @Test
    public void nextSizeSpec_resizedToAlmostMax_returnDefault() {
        final Rect userResizeBounds = new Rect(0, 0, MAX_EDGE_SIZE, MAX_EDGE_SIZE - 1);
        when(mMockPipBoundsState.getBounds()).thenReturn(userResizeBounds);
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_DEFAULT);
    }

    @Test
    public void nextSizeSpec_resizedToAlmostMin_returnMax() {
        final Rect userResizeBounds = new Rect(0, 0, MIN_EDGE_SIZE, MIN_EDGE_SIZE + 1);
        when(mMockPipBoundsState.getBounds()).thenReturn(userResizeBounds);
        Assert.assertEquals(nextSizeSpec(mMockPipBoundsState, userResizeBounds), SIZE_SPEC_MAX);
    }
}
