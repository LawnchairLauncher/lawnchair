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
package com.android.launcher3.accessibility;

import static com.android.launcher3.WorkspaceLayoutManager.EXTRA_EMPTY_SCREEN_ID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.Workspace;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link LauncherAccessibilityDelegate#findSpaceOnWorkspace}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class LauncherAccessibilityDelegateTest {

    @Mock
    private Launcher mLauncher;
    @Mock
    private Workspace<?> mWorkspace;
    @Mock
    private CellLayout mFullLayout;
    @Mock
    private CellLayout mFreeLayout;
    @Mock
    private CellLayout mCommittedLayout;

    private final IntArray mScreenOrder = new IntArray();
    private LauncherAccessibilityDelegate mDelegate;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mLauncher.getString(anyInt())).thenReturn("");
        when(mLauncher.getWorkspace()).thenReturn(mWorkspace);
        when(mWorkspace.getScreenOrder()).thenReturn(mScreenOrder);
        mDelegate = new LauncherAccessibilityDelegate(mLauncher);
    }

    @Test
    public void extraEmptyScreen_isSkippedWhenLookingForSpace() {
        // Screen 0 is full and the only other page is the transient extra empty screen,
        // which must not be returned as a persistent drop target.
        mScreenOrder.add(0);
        mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);
        when(mWorkspace.getCurrentPage()).thenReturn(0);
        when(mWorkspace.getPageAt(0)).thenReturn(mFullLayout);
        when(mWorkspace.getPageAt(1)).thenReturn(mFreeLayout);
        when(mFullLayout.findCellForSpan(any(int[].class), anyInt(), anyInt()))
                .thenReturn(false);
        when(mFreeLayout.findCellForSpan(any(int[].class), anyInt(), anyInt()))
                .thenReturn(true);

        IntSet committed = new IntSet();
        committed.add(5);
        when(mWorkspace.commitExtraEmptyScreens()).thenReturn(committed);
        when(mWorkspace.getScreenWithId(5)).thenReturn(mCommittedLayout);
        when(mCommittedLayout.findCellForSpan(any(int[].class), anyInt(), anyInt()))
                .thenReturn(true);

        int screenId = mDelegate.findSpaceOnWorkspace(new ItemInfo(), new int[2]);

        // The transient screen id must never be returned; the committed persistent id is.
        assertEquals(5, screenId);
        verify(mWorkspace).addExtraEmptyScreens();
        verify(mWorkspace).commitExtraEmptyScreens();
    }

    @Test
    public void extraEmptyScreenAsCurrentPage_isNotSelected() {
        // The user is on the extra empty screen and it has room; a real screen must still be
        // preferred over the transient one.
        mScreenOrder.add(0);
        mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);
        when(mWorkspace.getCurrentPage()).thenReturn(1);
        when(mWorkspace.getPageAt(0)).thenReturn(mFreeLayout);
        when(mWorkspace.getPageAt(1)).thenReturn(mFreeLayout);
        when(mFreeLayout.findCellForSpan(any(int[].class), anyInt(), anyInt()))
                .thenReturn(true);

        int screenId = mDelegate.findSpaceOnWorkspace(new ItemInfo(), new int[2]);

        assertEquals(0, screenId);
        verify(mWorkspace, never()).commitExtraEmptyScreens();
    }

    @Test
    public void workspaceCannotCommitNewScreen_returnsInvalidScreenId() {
        mScreenOrder.add(0);
        when(mWorkspace.getCurrentPage()).thenReturn(0);
        when(mWorkspace.getPageAt(0)).thenReturn(mFullLayout);
        when(mFullLayout.findCellForSpan(any(int[].class), anyInt(), anyInt()))
                .thenReturn(false);
        when(mWorkspace.commitExtraEmptyScreens()).thenReturn(new IntSet());

        int screenId = mDelegate.findSpaceOnWorkspace(new ItemInfo(), new int[2]);

        assertEquals(-1, screenId);
    }
}
