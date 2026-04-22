/*
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

package com.android.launcher3.widget.picker.search;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.Context;
import android.view.View;
import android.widget.ImageButton;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WidgetsSearchBarControllerTest {

    private WidgetsSearchBarController mController;
    private ExtendedEditText mEditText;
    private ImageButton mCancelButton;
    @Mock
    private SearchModeListener mSearchModeListener;
    @Mock
    private SearchAlgorithm<WidgetsListBaseEntry> mSearchAlgorithm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();

        mEditText = new ExtendedEditText(context);
        mCancelButton = new ImageButton(context);
        mController = new WidgetsSearchBarController(
                mSearchAlgorithm, mEditText, mCancelButton, mSearchModeListener);
    }

    @Test
    public void onSearchResult_shouldInformSearchModeListener() {
        ArrayList<WidgetsListBaseEntry> entries = new ArrayList<>();
        mController.onSearchResult("abc", entries);

        verify(mSearchModeListener).onSearchResults(entries);
    }

    @Test
    public void afterTextChanged_shouldInformSearchModeListenerToEnterSearch() {
        mEditText.setText("abc");

        verify(mSearchModeListener).enterSearchMode(true);
        verifyNoMoreInteractions(mSearchModeListener);
    }

    @Test
    public void afterTextChanged_shouldDoSearch() {
        mEditText.setText("abc");

        verify(mSearchAlgorithm).doSearch(eq("abc"), any());
    }

    @Test
    public void afterTextChanged_shouldShowCancelButton() {
        mEditText.setText("abc");

        assertEquals(mCancelButton.getVisibility(), View.VISIBLE);
    }

    @Test
    public void afterTextChanged_empty_shouldInformSearchModeListenerToExitSearch() {
        mEditText.setText("");

        verify(mSearchModeListener).exitSearchMode();
        verifyNoMoreInteractions(mSearchModeListener);
    }

    @Test
    public void afterTextChanged_empty_shouldCancelSearch() {
        mEditText.setText("");

        verify(mSearchAlgorithm).cancel(true);
        verifyNoMoreInteractions(mSearchAlgorithm);
    }

    @Test
    public void afterTextChanged_empty_shouldHideCancelButton() {
        mEditText.setText("");

        assertEquals(mCancelButton.getVisibility(), View.GONE);
    }

    @Test
    public void cancelSearch_shouldInformSearchModeListenerToClearResultsAndExitSearch() {
        mCancelButton.performClick();

        verify(mSearchModeListener).exitSearchMode();
    }

    @Test
    public void cancelSearch_shouldCancelSearch() {
        mCancelButton.performClick();

        verify(mSearchAlgorithm).cancel(true);
        verifyNoMoreInteractions(mSearchAlgorithm);
    }

    @Test
    public void cancelSearch_shouldClearSearchBar() {
        mCancelButton.performClick();

        assertEquals(mEditText.getText().toString(), "");
    }
}
