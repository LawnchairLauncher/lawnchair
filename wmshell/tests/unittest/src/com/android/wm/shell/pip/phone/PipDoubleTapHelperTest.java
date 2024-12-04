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

import static com.android.wm.shell.common.pip.PipDoubleTapHelper.SIZE_SPEC_CUSTOM;
import static com.android.wm.shell.common.pip.PipDoubleTapHelper.SIZE_SPEC_DEFAULT;
import static com.android.wm.shell.common.pip.PipDoubleTapHelper.SIZE_SPEC_MAX;
import static com.android.wm.shell.common.pip.PipDoubleTapHelper.nextSizeSpec;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Point;
import android.graphics.Rect;
import android.testing.AndroidTestingRunner;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDoubleTapHelper;

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
    // represents the current pip window state and has information on current
    // max, min, and normal sizes
    @Mock private PipBoundsState mBoundStateMock;
    // tied to boundsStateMock.getBounds() in setUp()
    @Mock private Rect mBoundsMock;

    // represents the most recent manually resized bounds
    // i.e. dimensions from the most recent pinch in/out
    @Mock private Rect mUserResizeBoundsMock;

    // actual dimensions of the pip screen bounds
    private static final int MAX_WIDTH = 100;
    private static final int DEFAULT_WIDTH = 40;
    private static final int MIN_WIDTH = 10;

    private static final int AVERAGE_WIDTH = (MAX_WIDTH + MIN_WIDTH) / 2;

    /**
     * Initializes mocks and assigns values for different pip screen bounds.
     */
    @Before
    public void setUp() {
        // define pip bounds
        when(mBoundStateMock.getMaxSize()).thenReturn(new Point(MAX_WIDTH, 20));
        when(mBoundStateMock.getMinSize()).thenReturn(new Point(MIN_WIDTH, 2));

        Rect rectMock = mock(Rect.class);
        when(rectMock.width()).thenReturn(DEFAULT_WIDTH);
        when(mBoundStateMock.getNormalBounds()).thenReturn(rectMock);

        when(mBoundsMock.width()).thenReturn(DEFAULT_WIDTH);
        when(mBoundStateMock.getBounds()).thenReturn(mBoundsMock);
    }

    /**
     * Tests {@link PipDoubleTapHelper#nextSizeSpec(PipBoundsState, Rect)}.
     *
     * <p>when the user resizes the screen to a larger than the average but not the maximum width,
     * then we toggle between {@code PipSizeSpec.CUSTOM} and {@code PipSizeSpec.DEFAULT}
     */
    @Test
    public void testNextScreenSize_resizedWiderThanAverage_returnDefaultThenCustom() {
        // make the user resize width in between MAX and average
        when(mUserResizeBoundsMock.width()).thenReturn((MAX_WIDTH + AVERAGE_WIDTH) / 2);
        // make current bounds same as resized bound since no double tap yet
        when(mBoundsMock.width()).thenReturn((MAX_WIDTH + AVERAGE_WIDTH) / 2);

        // then nextScreenSize() i.e. double tapping should
        // toggle to DEFAULT state
        Assert.assertSame(nextSizeSpec(mBoundStateMock, mUserResizeBoundsMock),
                SIZE_SPEC_DEFAULT);

        // once we toggle to DEFAULT our screen size gets updated
        // but not the user resize bounds
        when(mBoundsMock.width()).thenReturn(DEFAULT_WIDTH);

        // then nextScreenSize() i.e. double tapping should
        // toggle to CUSTOM state
        Assert.assertSame(nextSizeSpec(mBoundStateMock, mUserResizeBoundsMock),
                SIZE_SPEC_CUSTOM);
    }

    /**
     * Tests {@link PipDoubleTapHelper#nextSizeSpec(PipBoundsState, Rect)}.
     *
     * <p>when the user resizes the screen to a smaller than the average but not the default width,
     * then we toggle between {@code PipSizeSpec.CUSTOM} and {@code PipSizeSpec.MAX}
     */
    @Test
    public void testNextScreenSize_resizedNarrowerThanAverage_returnMaxThenCustom() {
        // make the user resize width in between MIN and average
        when(mUserResizeBoundsMock.width()).thenReturn((MIN_WIDTH + AVERAGE_WIDTH) / 2);
        // make current bounds same as resized bound since no double tap yet
        when(mBoundsMock.width()).thenReturn((MIN_WIDTH + AVERAGE_WIDTH) / 2);

        // then nextScreenSize() i.e. double tapping should
        // toggle to MAX state
        Assert.assertSame(nextSizeSpec(mBoundStateMock, mUserResizeBoundsMock),
                SIZE_SPEC_MAX);

        // once we toggle to MAX our screen size gets updated
        // but not the user resize bounds
        when(mBoundsMock.width()).thenReturn(MAX_WIDTH);

        // then nextScreenSize() i.e. double tapping should
        // toggle to CUSTOM state
        Assert.assertSame(nextSizeSpec(mBoundStateMock, mUserResizeBoundsMock),
                SIZE_SPEC_CUSTOM);
    }

    /**
     * Tests {@link PipDoubleTapHelper#nextSizeSpec(PipBoundsState, Rect)}.
     *
     * <p>when the user resizes the screen to exactly the maximum width
     * then we toggle to {@code PipSizeSpec.DEFAULT}
     */
    @Test
    public void testNextScreenSize_resizedToMax_returnDefault() {
        // the resized width is the same as MAX_WIDTH
        when(mUserResizeBoundsMock.width()).thenReturn(MAX_WIDTH);
        // the current bounds are also at MAX_WIDTH
        when(mBoundsMock.width()).thenReturn(MAX_WIDTH);

        // then nextScreenSize() i.e. double tapping should
        // toggle to DEFAULT state
        Assert.assertSame(nextSizeSpec(mBoundStateMock, mUserResizeBoundsMock),
                SIZE_SPEC_DEFAULT);
    }

    /**
     * Tests {@link PipDoubleTapHelper#nextSizeSpec(PipBoundsState, Rect)}.
     *
     * <p>when the user resizes the screen to exactly the default width
     * then we toggle to {@code PipSizeSpec.MAX}
     */
    @Test
    public void testNextScreenSize_resizedToDefault_returnMax() {
        // the resized width is the same as DEFAULT_WIDTH
        when(mUserResizeBoundsMock.width()).thenReturn(DEFAULT_WIDTH);
        // the current bounds are also at DEFAULT_WIDTH
        when(mBoundsMock.width()).thenReturn(DEFAULT_WIDTH);

        // then nextScreenSize() i.e. double tapping should
        // toggle to MAX state
        Assert.assertSame(nextSizeSpec(mBoundStateMock, mUserResizeBoundsMock),
                SIZE_SPEC_MAX);
    }
}
