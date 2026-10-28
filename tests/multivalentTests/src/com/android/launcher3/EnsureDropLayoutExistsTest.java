/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3;

import static com.android.launcher3.WorkspaceLayoutManager.EXTRA_EMPTY_SCREEN_ID;
import static com.android.launcher3.WorkspaceLayoutManager.EXTRA_EMPTY_SCREEN_SECOND_ID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.util.IntSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link Launcher#ensureDropLayoutExists}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class EnsureDropLayoutExistsTest {

    @Mock
    private Workspace<?> mWorkspace;
    @Mock
    private CellLayout mCellLayout;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void existingScreen_isReturnedUnchanged() {
        when(mWorkspace.getScreenWithId(3)).thenReturn(mCellLayout);

        assertEquals(3, Launcher.ensureDropLayoutExists(mWorkspace, 3));
        verify(mWorkspace, never()).commitExtraEmptyScreens();
    }

    @Test
    public void missingScreen_isReplacedByCommittedScreen() {
        when(mWorkspace.getScreenWithId(9)).thenReturn(null);
        when(mWorkspace.commitExtraEmptyScreens()).thenReturn(intSet(12));

        assertEquals(12, Launcher.ensureDropLayoutExists(mWorkspace, 9));
        verify(mWorkspace).addExtraEmptyScreens();
    }

    @Test
    public void extraEmptyScreen_isCommittedToPersistentScreen() {
        // The transient extra empty screen still exists at this point, but it must not be
        // used as a persistent drop target.
        when(mWorkspace.getScreenWithId(EXTRA_EMPTY_SCREEN_ID)).thenReturn(mCellLayout);
        when(mWorkspace.commitExtraEmptyScreens()).thenReturn(intSet(5));

        assertEquals(5, Launcher.ensureDropLayoutExists(mWorkspace, EXTRA_EMPTY_SCREEN_ID));
        verify(mWorkspace).commitExtraEmptyScreens();
    }

    @Test
    public void secondExtraEmptyScreen_mapsToSecondCommittedScreen() {
        // On two panel home both extra screens are committed; an item targeting the second
        // one must land on the second committed screen.
        when(mWorkspace.getScreenWithId(EXTRA_EMPTY_SCREEN_SECOND_ID)).thenReturn(mCellLayout);
        when(mWorkspace.commitExtraEmptyScreens()).thenReturn(intSet(5, 6));

        assertEquals(6,
                Launcher.ensureDropLayoutExists(mWorkspace, EXTRA_EMPTY_SCREEN_SECOND_ID));
    }

    @Test
    public void commitFails_returnsInvalidScreenId() {
        when(mWorkspace.getScreenWithId(EXTRA_EMPTY_SCREEN_ID)).thenReturn(mCellLayout);
        when(mWorkspace.commitExtraEmptyScreens()).thenReturn(new IntSet());

        assertEquals(-1, Launcher.ensureDropLayoutExists(mWorkspace, EXTRA_EMPTY_SCREEN_ID));
    }

    private static IntSet intSet(int... ids) {
        IntSet set = new IntSet();
        for (int id : ids) {
            set.add(id);
        }
        return set;
    }
}
