/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget.picker;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.R;
import com.android.launcher3.util.ActivityContextWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WidgetsListHeaderAccessibilityTest {
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    @Mock
    private View.OnClickListener mOnClickListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = new ActivityContextWrapper(getApplicationContext());
        mLayoutInflater = LayoutInflater.from(
                new ContextThemeWrapper(mContext, R.style.WidgetContainerTheme));
    }

    @Test
    public void singlePaneCollapsable_hasCustomAccessibilityActions() {
        WidgetsListHeader header = (WidgetsListHeader) mLayoutInflater.inflate(
                R.layout.widgets_list_row_header,
                new FrameLayout(mContext), false);

        assertThat(header.getAccessibilityDelegate()).isNotNull();

        header.setOnClickListener(mOnClickListener);
        header.getAccessibilityDelegate().performAccessibilityAction(header,
                AccessibilityNodeInfo.ACTION_EXPAND, null);
        header.getAccessibilityDelegate().performAccessibilityAction(header,
                AccessibilityNodeInfo.ACTION_COLLAPSE, null);

        verify(mOnClickListener, times(2)).onClick(header);
    }

    @Test
    public void twoPaneNonCollapsable_noCustomAccessibilityDelegate() {
        WidgetsListHeader header = (WidgetsListHeader) mLayoutInflater.inflate(
                R.layout.widgets_list_row_header_two_pane,
                new FrameLayout(mContext), false);

        assertThat(header.getAccessibilityDelegate()).isNull();
    }
}
