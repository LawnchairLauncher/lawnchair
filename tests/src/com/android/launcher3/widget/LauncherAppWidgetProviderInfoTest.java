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
package com.android.launcher3.widget;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class LauncherAppWidgetProviderInfoTest {

    private static final int SPACE_SIZE = 10;
    private static final int CELL_SIZE = 50;
    private static final int NUM_OF_COLS = 4;
    private static final int NUM_OF_ROWS = 5;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = getApplicationContext();
    }

    @Test
    public void initSpans_minWidthSmallerThanCellWidth_shouldInitializeSpansToOne() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 20;
        info.minHeight = 20;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.spanX).isEqualTo(1);
        assertThat(info.spanY).isEqualTo(1);
    }

    @Test
    public void initSpans_minWidthLargerThanCellWidth_shouldInitializeSpans() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 80;
        info.minHeight = 80;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.spanX).isEqualTo(2);
        assertThat(info.spanY).isEqualTo(2);
    }

    @Test
    public void
            initSpans_minWidthLargerThanGridColumns_shouldInitializeSpansToAtMostTheGridColumns() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = CELL_SIZE * (NUM_OF_COLS + 1);
        info.minHeight = 20;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.spanX).isEqualTo(NUM_OF_COLS);
        assertThat(info.spanY).isEqualTo(1);
    }

    @Test
    public void initSpans_minHeightLargerThanGridRows_shouldInitializeSpansToAtMostTheGridRows() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 20;
        info.minHeight = 50 * (NUM_OF_ROWS + 1);
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.spanX).isEqualTo(1);
        assertThat(info.spanY).isEqualTo(NUM_OF_ROWS);
    }

    @Test
    public void initSpans_minResizeWidthUnspecified_shouldInitializeMinSpansToOne() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.minSpanX).isEqualTo(1);
        assertThat(info.minSpanY).isEqualTo(1);
    }

    @Test
    public void initSpans_minResizeWidthSmallerThanCellWidth_shouldInitializeMinSpansToOne() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 100;
        info.minHeight = 100;
        info.minResizeWidth = 20;
        info.minResizeHeight = 20;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.minSpanX).isEqualTo(1);
        assertThat(info.minSpanY).isEqualTo(1);
    }

    @Test
    public void initSpans_minResizeWidthLargerThanCellWidth_shouldInitializeMinSpans() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 100;
        info.minHeight = 100;
        info.minResizeWidth = 80;
        info.minResizeHeight = 80;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.minSpanX).isEqualTo(2);
        assertThat(info.minSpanY).isEqualTo(2);
    }

    @Test
    public void initSpans_minResizeWidthWithCellSpacingAndWidgetInset_shouldInitializeMinSpans() {
        InvariantDeviceProfile idp = createIDP();
        DeviceProfile dp = idp.supportedProfiles.get(0);
        Rect padding = new Rect();
        AppWidgetHostView.getDefaultPaddingForWidget(mContext, null, padding);
        int maxPadding = Math.max(Math.max(padding.left, padding.right),
                Math.max(padding.top, padding.bottom));
        dp.cellLayoutBorderSpacePx.x = dp.cellLayoutBorderSpacePx.y = maxPadding + 1;

        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = CELL_SIZE * 3;
        info.minHeight = CELL_SIZE * 3;
        info.minResizeWidth = CELL_SIZE * 2 + maxPadding;
        info.minResizeHeight = CELL_SIZE * 2 + maxPadding;

        info.initSpans(mContext, idp);

        assertThat(info.minSpanX).isEqualTo(2);
        assertThat(info.minSpanY).isEqualTo(2);
    }

    @Test
    public void initSpans_minResizeWidthWithCellSpacingAndNoWidgetInset_shouldInitializeMinSpans() {
        InvariantDeviceProfile idp = createIDP();
        DeviceProfile dp = idp.supportedProfiles.get(0);
        Rect padding = new Rect();
        AppWidgetHostView.getDefaultPaddingForWidget(mContext, null, padding);
        int maxPadding = Math.max(Math.max(padding.left, padding.right),
                Math.max(padding.top, padding.bottom));
        dp.cellLayoutBorderSpacePx.x = dp.cellLayoutBorderSpacePx.y = maxPadding - 1;
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = CELL_SIZE * 3;
        info.minHeight = CELL_SIZE * 3;
        info.minResizeWidth = CELL_SIZE * 2 + maxPadding;
        info.minResizeHeight = CELL_SIZE * 2 + maxPadding;

        info.initSpans(mContext, idp);

        assertThat(info.minSpanX).isEqualTo(3);
        assertThat(info.minSpanY).isEqualTo(3);
    }

    @Test
    public void
            initSpans_minResizeWidthHeightLargerThanMinWidth_shouldUseMinWidthHeightAsMinSpans() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 20;
        info.minHeight = 20;
        info.minResizeWidth = 80;
        info.minResizeHeight = 80;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.minSpanX).isEqualTo(1);
        assertThat(info.minSpanY).isEqualTo(1);
    }

    @Test
    public void isMinSizeFulfilled_minWidthAndHeightWithinGridSize_shouldReturnTrue() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 80;
        info.minHeight = 80;
        info.minResizeWidth = 50;
        info.minResizeHeight = 50;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.isMinSizeFulfilled()).isTrue();
    }

    @Test
    public void
            isMinSizeFulfilled_minWidthAndMinResizeWidthExceededGridColumns_shouldReturnFalse() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = CELL_SIZE * (NUM_OF_COLS + 2);
        info.minHeight = 80;
        info.minResizeWidth = CELL_SIZE * (NUM_OF_COLS + 1);
        info.minResizeHeight = 50;
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.isMinSizeFulfilled()).isFalse();
    }

    @Test
    public void isMinSizeFulfilled_minHeightAndMinResizeHeightExceededGridRows_shouldReturnFalse() {
        LauncherAppWidgetProviderInfo info = new LauncherAppWidgetProviderInfo();
        info.minWidth = 80;
        info.minHeight = CELL_SIZE * (NUM_OF_ROWS + 2);
        info.minResizeWidth = 50;
        info.minResizeHeight = CELL_SIZE * (NUM_OF_ROWS + 1);
        InvariantDeviceProfile idp = createIDP();

        info.initSpans(mContext, idp);

        assertThat(info.isMinSizeFulfilled()).isFalse();
    }

    private InvariantDeviceProfile createIDP() {
        DeviceProfile dp = LauncherAppState.getIDP(mContext)
                .getDeviceProfile(mContext).copy(mContext);
        DeviceProfile profile = Mockito.spy(dp);
        doAnswer(i -> {
            ((Point) i.getArgument(0)).set(CELL_SIZE, CELL_SIZE);
            return null;
        }).when(profile).getCellSize(any(Point.class));
        Mockito.when(profile.getCellSize()).thenReturn(new Point(CELL_SIZE, CELL_SIZE));
        profile.cellLayoutBorderSpacePx = new Point(SPACE_SIZE, SPACE_SIZE);
        profile.widgetPadding.setEmpty();

        InvariantDeviceProfile idp = new InvariantDeviceProfile();
        List<DeviceProfile> supportedProfiles = new ArrayList<>(idp.supportedProfiles);
        supportedProfiles.add(profile);
        idp.supportedProfiles = Collections.unmodifiableList(supportedProfiles);
        idp.numColumns = NUM_OF_COLS;
        idp.numRows = NUM_OF_ROWS;
        return idp;
    }
}
