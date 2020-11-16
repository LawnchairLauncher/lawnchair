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
package com.android.quickstep.util;

import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_90;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;

import com.android.quickstep.FallbackActivityInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

/**
 * Tests for {@link RecentsOrientedState}
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class RecentsOrientedStateTest {

    private RecentsOrientedState mR1, mR2;

    @Before
    public void setup() {
        Context context = RuntimeEnvironment.application;
        mR1 = new RecentsOrientedState(context, FallbackActivityInterface.INSTANCE, i -> { });
        mR2 = new RecentsOrientedState(context, FallbackActivityInterface.INSTANCE, i -> { });
        assertEquals(mR1.getStateId(), mR2.getStateId());
    }

    @Test
    public void stateId_changesWithFlags() {
        mR1.setGestureActive(true);
        mR2.setGestureActive(false);
        assertNotEquals(mR1.getStateId(), mR2.getStateId());

        mR2.setGestureActive(true);
        assertEquals(mR1.getStateId(), mR2.getStateId());
    }

    @Test
    public void stateId_changesWithRecentsRotation() {
        mR1.setRecentsRotation(ROTATION_90);
        mR2.setRecentsRotation(ROTATION_180);
        assertNotEquals(mR1.getStateId(), mR2.getStateId());

        mR2.setRecentsRotation(ROTATION_90);
        assertEquals(mR1.getStateId(), mR2.getStateId());
    }

    @Test
    public void stateId_changesWithDisplayRotation() {
        mR1.update(ROTATION_0, ROTATION_90);
        mR2.update(ROTATION_0, ROTATION_180);
        assertNotEquals(mR1.getStateId(), mR2.getStateId());

        mR2.update(ROTATION_90, ROTATION_90);
        assertNotEquals(mR1.getStateId(), mR2.getStateId());

        mR2.update(ROTATION_90, ROTATION_0);
        assertNotEquals(mR1.getStateId(), mR2.getStateId());

        mR2.update(ROTATION_0, ROTATION_90);
        assertEquals(mR1.getStateId(), mR2.getStateId());
    }
}
