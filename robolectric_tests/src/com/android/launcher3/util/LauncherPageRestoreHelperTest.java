/**
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Bundle;

import com.android.launcher3.LauncherPageRestoreHelper;
import com.android.launcher3.Workspace;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class LauncherPageRestoreHelperTest {

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN_COUNT =
            "launcher.current_screen_count";

    private LauncherPageRestoreHelper mPageRestoreHelper;
    private Bundle mState;

    @Mock
    private Workspace mWorkspace;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPageRestoreHelper = new LauncherPageRestoreHelper(mWorkspace);
        mState = new Bundle();
    }

    @Test
    public void givenNoChildrenInWorkspace_whenSavePages_thenNothingSaved() {
        when(mWorkspace.getChildCount()).thenReturn(0);

        mPageRestoreHelper.savePagesToRestore(mState);

        assertFalse(mState.containsKey(RUNTIME_STATE_CURRENT_SCREEN_COUNT));
        assertFalse(mState.containsKey(RUNTIME_STATE_CURRENT_SCREEN));
    }

    @Test
    public void givenMultipleCurrentPages_whenSavePages_thenSavedCorrectly() {
        when(mWorkspace.getChildCount()).thenReturn(5);
        when(mWorkspace.getCurrentPage()).thenReturn(2);
        givenPanelCount(2);

        mPageRestoreHelper.savePagesToRestore(mState);

        assertEquals(5, mState.getInt(RUNTIME_STATE_CURRENT_SCREEN_COUNT));
        assertEquals(2, mState.getInt(RUNTIME_STATE_CURRENT_SCREEN));
    }

    @Test
    public void givenNullSavedState_whenRestorePages_thenReturnEmptyIntSet() {
        IntSet result = mPageRestoreHelper.getPagesToRestore(null);

        assertTrue(result.isEmpty());
    }

    @Test
    public void givenTotalPageCountMissing_whenRestorePages_thenReturnEmptyIntSet() {
        givenSavedCurrentPage(1);
        givenPanelCount(1);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertTrue(result.isEmpty());
    }

    @Test
    public void givenCurrentPageMissing_whenRestorePages_thenReturnEmptyIntSet() {
        givenSavedPageCount(3);
        givenPanelCount(2);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertTrue(result.isEmpty());
    }

    @Test
    public void givenOnePanel_whenRestorePages_thenReturnThatPage() {
        givenSavedCurrentPage(2);
        givenSavedPageCount(5);
        givenPanelCount(1);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(1, result.size());
        assertEquals(2, result.getArray().get(0));
    }

    @Test
    public void givenTwoPanelOnFirstPages_whenRestorePages_thenReturnThosePages() {
        givenSavedCurrentPage(0, 1);
        givenSavedPageCount(2);
        givenPanelCount(2);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(0, 1), result);
    }

    @Test
    public void givenTwoPanelOnMiddlePages_whenRestorePages_thenReturnThosePages() {
        givenSavedCurrentPage(2, 3);
        givenSavedPageCount(5);
        givenPanelCount(2);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(2, 3), result);
    }

    @Test
    public void givenTwoPanelOnLastPage_whenRestorePages_thenReturnOnlyLastPage() {
        // The device has two panel home but the current page is the last page, so we don't have
        // a right panel, only the left one.
        givenSavedCurrentPage(2);
        givenSavedPageCount(3);
        givenPanelCount(2);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(2), result);
    }

    @Test
    public void givenOnlyOnePageAndPhoneFolding_whenRestorePages_thenReturnOnlyOnePage() {
        givenSavedCurrentPage(0);
        givenSavedPageCount(1);
        givenPanelCount(1);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(0), result);
    }

    @Test
    public void givenPhoneFolding_whenRestorePages_thenReturnOnlyTheFirstCurrentPage() {
        givenSavedCurrentPage(2, 3);
        givenSavedPageCount(4);
        givenPanelCount(1);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(2), result);
    }

    @Test
    public void givenPhoneUnfolding_whenRestorePages_thenReturnCurrentPagePlusTheNextOne() {
        givenSavedCurrentPage(2);
        givenSavedPageCount(4);
        givenPanelCount(2);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(2, 3), result);
    }

    @Test
    public void givenPhoneUnfoldingOnLastPage_whenRestorePages_thenReturnOnlyLastPage() {
        givenSavedCurrentPage(4);
        givenSavedPageCount(5);
        givenPanelCount(2);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(4), result);
    }

    @Test
    public void givenOnlyOnePageAndPhoneUnfolding_whenRestorePages_thenReturnOnlyOnePage() {
        givenSavedCurrentPage(0);
        givenSavedPageCount(1);
        givenPanelCount(2);

        IntSet result = mPageRestoreHelper.getPagesToRestore(mState);

        assertEquals(IntSet.wrap(0), result);
    }

    private void givenPanelCount(int panelCount) {
        when(mWorkspace.getPanelCount()).thenReturn(panelCount);
        when(mWorkspace.getLeftmostVisiblePageForIndex(anyInt())).thenAnswer(invocation -> {
            int pageIndex = invocation.getArgument(0);
            return pageIndex * panelCount / panelCount;
        });
    }

    private void givenSavedPageCount(int pageCount) {
        mState.putInt(RUNTIME_STATE_CURRENT_SCREEN_COUNT, pageCount);
    }

    private void givenSavedCurrentPage(int... pages) {
        mState.putInt(RUNTIME_STATE_CURRENT_SCREEN, pages[0]);
    }
}
