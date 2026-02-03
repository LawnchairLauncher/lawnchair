/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.bubbles.bar;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.graphics.Color;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.core.content.ContextCompat;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class BubbleBarHandleViewTest extends ShellTestCase {
    private BubbleBarHandleView mHandleView;

    @Before
    public void setup() {
        mHandleView = new BubbleBarHandleView(mContext);
    }

    @Test
    public void testUpdateHandleColor_lightBg() {
        mHandleView.updateHandleColor(false /* isRegionDark */, false /* animated */);

        assertFalse(mHandleView.getClipToOutline());
        int handleColor = mHandleView.mHandlePaint.getColor();
        assertEquals(handleColor,
                ContextCompat.getColor(mContext, R.color.bubble_bar_expanded_view_handle_dark));
    }

    @Test
    public void testUpdateHandleColor_darkBg() {
        mHandleView.updateHandleColor(true /* isRegionDark */, false /* animated */);

        assertFalse(mHandleView.getClipToOutline());
        int handleColor = mHandleView.mHandlePaint.getColor();
        assertEquals(handleColor,
                ContextCompat.getColor(mContext, R.color.bubble_bar_expanded_view_handle_light));
    }

    @Test
    public void testSetHandleInitialColor_beforeUpdateHandleColor_updatesColor() {
        mHandleView.setHandleInitialColor(Color.RED);
        assertThat(mHandleView.getHandleColor()).isEqualTo(Color.RED);
    }

    @Test
    public void testSetHandleInitialColor_afterUpdateHandleColor_doesNotUpdateColor() {
        mHandleView.updateHandleColor(/* isRegionDark= */ true, /* animated= */ false);
        mHandleView.setHandleInitialColor(Color.RED);
        assertThat(mHandleView.getHandleColor()).isEqualTo(
                ContextCompat.getColor(mContext, R.color.bubble_bar_expanded_view_handle_light));
    }
}
