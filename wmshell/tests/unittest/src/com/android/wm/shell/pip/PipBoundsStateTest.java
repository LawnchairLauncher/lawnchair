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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.graphics.Point;
import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.Size;

import androidx.test.filters.SmallTest;

import com.android.internal.util.function.TriConsumer;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PhoneSizeSpecSource;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.SizeSpecSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Tests for {@link PipBoundsState}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class PipBoundsStateTest extends ShellTestCase {

    private static final Size DEFAULT_SIZE = new Size(10, 10);
    private static final float DEFAULT_SNAP_FRACTION = 1.0f;

    /** The minimum possible size of the override min size's width or height */
    private static final int OVERRIDABLE_MIN_SIZE = 40;

    /** The margin of error for floating point results. */
    private static final float MARGIN_OF_ERROR = 0.05f;

    private PipBoundsState mPipBoundsState;
    private SizeSpecSource mSizeSpecSource;
    private ComponentName mTestComponentName1;
    private ComponentName mTestComponentName2;

    @Before
    public void setUp() {
        final TestableResources res = mContext.getOrCreateTestableResources();
        res.addOverride(
                R.dimen.overridable_minimal_size_pip_resizable_task,
                OVERRIDABLE_MIN_SIZE);

        PipDisplayLayoutState pipDisplayLayoutState = new PipDisplayLayoutState(mContext);
        mSizeSpecSource = new PhoneSizeSpecSource(mContext, pipDisplayLayoutState);
        mPipBoundsState = new PipBoundsState(mContext, mSizeSpecSource, pipDisplayLayoutState);
        mTestComponentName1 = new ComponentName(mContext, "component1");
        mTestComponentName2 = new ComponentName(mContext, "component2");
    }

    @Test
    public void testSetBounds() {
        final Rect bounds = new Rect(0, 0, 100, 100);
        mPipBoundsState.setBounds(bounds);

        assertEquals(bounds, mPipBoundsState.getBounds());
    }

    @Test
    public void testBoundsScale() {
        mPipBoundsState.setMaxSize(300, 300);
        mPipBoundsState.setBounds(new Rect(0, 0, 100, 100));

        final int currentWidth = mPipBoundsState.getBounds().width();
        final Point maxSize = mPipBoundsState.getMaxSize();
        final float expectedBoundsScale = Math.min((float) currentWidth / maxSize.x, 1.0f);

        // test for currentWidth < maxWidth
        assertEquals(expectedBoundsScale, mPipBoundsState.getBoundsScale(), MARGIN_OF_ERROR);

        // reset the bounds to be at the maximum size spec
        mPipBoundsState.setBounds(new Rect(0, 0, maxSize.x, maxSize.y));
        assertEquals(1.0f, mPipBoundsState.getBoundsScale(), /* delta */ 0f);

        // reset the bounds to be over the maximum size spec
        mPipBoundsState.setBounds(new Rect(0, 0, maxSize.x * 2, maxSize.y * 2));
        assertEquals(1.0f, mPipBoundsState.getBoundsScale(), /* delta */ 0f);
    }

    @Test
    public void testSetReentryState() {
        final float snapFraction = 0.5f;

        mPipBoundsState.saveReentryState(snapFraction);

        final PipBoundsState.PipReentryState state = mPipBoundsState.getReentryState();
        assertEquals(snapFraction, state.getSnapFraction(), 0.01);
    }

    @Test
    public void testClearReentryState() {
        final float snapFraction = 0.5f;

        mPipBoundsState.saveReentryState(snapFraction);
        mPipBoundsState.clearReentryState();

        assertNull(mPipBoundsState.getReentryState());
    }

    @Test
    public void testSetLastPipComponentName_notChanged_doesNotClearReentryState() {
        mPipBoundsState.setLastPipComponentName(mTestComponentName1);
        mPipBoundsState.saveReentryState(DEFAULT_SNAP_FRACTION);

        mPipBoundsState.setLastPipComponentName(mTestComponentName1);

        final PipBoundsState.PipReentryState state = mPipBoundsState.getReentryState();
        assertNotNull(state);
        assertEquals(DEFAULT_SNAP_FRACTION, state.getSnapFraction(), 0.01);
    }

    @Test
    public void testSetLastPipComponentName_changed_clearReentryState() {
        mPipBoundsState.setLastPipComponentName(mTestComponentName1);
        mPipBoundsState.saveReentryState(DEFAULT_SNAP_FRACTION);

        mPipBoundsState.setLastPipComponentName(mTestComponentName2);

        assertNull(mPipBoundsState.getReentryState());
    }

    @Test
    public void testSetShelfVisibility_changed_callbackInvoked() {
        final TriConsumer<Boolean, Integer, Boolean> callback = mock(TriConsumer.class);
        mPipBoundsState.setOnShelfVisibilityChangeCallback(callback);

        mPipBoundsState.setShelfVisibility(true, 100);

        verify(callback).accept(true, 100, true);
    }

    @Test
    public void testSetShelfVisibility_changedWithoutUpdateMovBounds_callbackInvoked() {
        final TriConsumer<Boolean, Integer, Boolean> callback = mock(TriConsumer.class);
        mPipBoundsState.setOnShelfVisibilityChangeCallback(callback);

        mPipBoundsState.setShelfVisibility(true, 100, false);

        verify(callback).accept(true, 100, false);
    }

    @Test
    public void testSetShelfVisibility_notChanged_callbackNotInvoked() {
        final TriConsumer<Boolean, Integer, Boolean> callback = mock(TriConsumer.class);
        mPipBoundsState.setShelfVisibility(true, 100);
        mPipBoundsState.setOnShelfVisibilityChangeCallback(callback);

        mPipBoundsState.setShelfVisibility(true, 100);

        verify(callback, never()).accept(true, 100, true);
    }

    @Test
    public void testSetOverrideMinSize_changed_callbackInvoked() {
        final Runnable callback = mock(Runnable.class);
        mPipBoundsState.setOverrideMinSize(new Size(5, 5));
        mPipBoundsState.setOnMinimalSizeChangeCallback(callback);

        mPipBoundsState.setOverrideMinSize(new Size(10, 10));

        verify(callback).run();
    }

    @Test
    public void testSetOverrideMinSize_notChanged_callbackNotInvoked() {
        final Runnable callback = mock(Runnable.class);
        mPipBoundsState.setOverrideMinSize(new Size(100, 150));
        mPipBoundsState.setOnMinimalSizeChangeCallback(callback);

        mPipBoundsState.setOverrideMinSize(new Size(100, 150));

        verify(callback, never()).run();
    }

    @Test
    public void testGetOverrideMinEdgeSize() {
        mPipBoundsState.setOverrideMinSize(null);
        assertEquals(0, mPipBoundsState.getOverrideMinEdgeSize());

        mPipBoundsState.setOverrideMinSize(new Size(100, 110));
        assertEquals(100, mPipBoundsState.getOverrideMinEdgeSize());

        mPipBoundsState.setOverrideMinSize(new Size(150, 200));
        assertEquals(150, mPipBoundsState.getOverrideMinEdgeSize());
    }

    @Test
    public void testSetBounds_updatesPipExclusionBounds() {
        final Consumer<Rect> callback = mock(Consumer.class);
        final Rect currentBounds = new Rect(10, 10, 20, 15);
        final Rect newBounds = new Rect(50, 50, 100, 75);
        mPipBoundsState.setBounds(currentBounds);

        mPipBoundsState.addPipExclusionBoundsChangeCallback(callback);
        // Setting the listener immediately calls back with the current bounds.
        verify(callback).accept(currentBounds);

        mPipBoundsState.setBounds(newBounds);
        // Updating the bounds makes the listener call back back with the new rect.
        verify(callback).accept(newBounds);
    }
}
