/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link IntSet}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class IntSetTest {

    @Test
    public void shouldBeEmptyInitially() {
        IntSet set = new IntSet();
        assertThat(set.size()).isEqualTo(0);
    }

    @Test
    public void oneElementSet() {
        IntSet set = new IntSet();
        set.add(2);
        assertThat(set.size()).isEqualTo(1);
        assertTrue(set.contains(2));
        assertFalse(set.contains(1));
    }


    @Test
    public void twoElementSet() {
        IntSet set = new IntSet();
        set.add(2);
        set.add(1);
        assertThat(set.size()).isEqualTo(2);
        assertTrue(set.contains(2));
        assertTrue(set.contains(1));
    }

    @Test
    public void threeElementSet() {
        IntSet set = new IntSet();
        set.add(2);
        set.add(1);
        set.add(10);
        assertThat(set.size()).isEqualTo(3);
        assertEquals("1, 2, 10", set.mArray.toConcatString());
    }


    @Test
    public void duplicateEntries() {
        IntSet set = new IntSet();
        set.add(2);
        set.add(2);
        assertEquals(1, set.size());
    }
}
