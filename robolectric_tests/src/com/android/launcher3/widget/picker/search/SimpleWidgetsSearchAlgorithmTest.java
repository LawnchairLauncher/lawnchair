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

import static android.os.Looper.getMainLooper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
public class SimpleWidgetsSearchAlgorithmTest {

    private SimpleWidgetsSearchAlgorithm mSimpleWidgetsSearchAlgorithm;
    @Mock
    private WidgetsPickerSearchPipeline mSearchPipeline;
    @Mock
    private SearchCallback<WidgetsListBaseEntry> mSearchCallback;
    @Captor
    private ArgumentCaptor<Consumer<List<WidgetsListBaseEntry>>> mConsumerCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSimpleWidgetsSearchAlgorithm = new SimpleWidgetsSearchAlgorithm(mSearchPipeline);
    }

    @Test
    public void doSearch_shouldQueryPipeline() {
        mSimpleWidgetsSearchAlgorithm.doSearch("abc", mSearchCallback);

        verify(mSearchPipeline).query(eq("abc"), any());
    }

    @Test
    public void doSearch_shouldInformSearchCallbackOnQueryResult() {
        ArrayList<WidgetsListBaseEntry> baseEntries = new ArrayList<>();

        mSimpleWidgetsSearchAlgorithm.doSearch("abc", mSearchCallback);

        verify(mSearchPipeline).query(eq("abc"), mConsumerCaptor.capture());
        mConsumerCaptor.getValue().accept(baseEntries);
        shadowOf(getMainLooper()).idle();
        // Verify SearchCallback#onSearchResult receives a query token along with the search
        // results. The query token is the original query string concatenated with the query
        // timestamp.
        verify(mSearchCallback).onSearchResult(matches("abc\t\\d*"), eq(baseEntries));
    }
}
