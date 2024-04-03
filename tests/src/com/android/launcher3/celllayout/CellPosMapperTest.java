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
package com.android.launcher3.celllayout;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.celllayout.CellPosMapper.TwoPanelCellPosMapper;
import com.android.launcher3.model.data.ItemInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link CellPosMapper}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CellPosMapperTest {

    @Test
    public void testMapModelToPresenter_default() {
        CellPosMapper mapper = CellPosMapper.DEFAULT;
        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 0, CONTAINER_DESKTOP))).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 1, CONTAINER_DESKTOP))).isEqualTo(new CellPos(0, 0, 1));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 1, CONTAINER_DESKTOP))).isEqualTo(new CellPos(5, 0, 1));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 0, CONTAINER_DESKTOP))).isEqualTo(new CellPos(5, 0, 0));

        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 0, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 1, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(0, 0, 1));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 1, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(5, 0, 1));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 0, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(5, 0, 0));
    }

    @Test
    public void testMapPresenterToModel_default() {
        CellPosMapper mapper = CellPosMapper.DEFAULT;
        assertThat(mapper.mapPresenterToModel(
                0, 0, 0, CONTAINER_DESKTOP)).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapPresenterToModel(
                0, 0, 1, CONTAINER_DESKTOP)).isEqualTo(new CellPos(0, 0, 1));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 1, CONTAINER_DESKTOP)).isEqualTo(new CellPos(5, 0, 1));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 0, CONTAINER_DESKTOP)).isEqualTo(new CellPos(5, 0, 0));

        assertThat(mapper.mapPresenterToModel(
                0, 0, 0, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapPresenterToModel(
                0, 0, 1, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 1, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(5, 0, 5));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 0, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(5, 0, 5));
    }

    @Test
    public void testMapModelToPresenter_twoPanel() {
        CellPosMapper mapper = new TwoPanelCellPosMapper(8);
        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 0, CONTAINER_DESKTOP))).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 1, CONTAINER_DESKTOP))).isEqualTo(new CellPos(8, 0, 0));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 1, CONTAINER_DESKTOP))).isEqualTo(new CellPos(13, 0, 0));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 0, CONTAINER_DESKTOP))).isEqualTo(new CellPos(5, 0, 0));

        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 0, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapModelToPresenter(
                createInfo(0, 0, 1, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(0, 0, 1));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 1, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(5, 0, 1));
        assertThat(mapper.mapModelToPresenter(
                createInfo(5, 0, 0, CONTAINER_HOTSEAT))).isEqualTo(new CellPos(5, 0, 0));
    }

    @Test
    public void testMapPresenterToModel_twoPanel() {
        CellPosMapper mapper = new TwoPanelCellPosMapper(3);
        assertThat(mapper.mapPresenterToModel(
                0, 0, 0, CONTAINER_DESKTOP)).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapPresenterToModel(
                0, 0, 1, CONTAINER_DESKTOP)).isEqualTo(new CellPos(0, 0, 1));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 1, CONTAINER_DESKTOP)).isEqualTo(new CellPos(5, 0, 1));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 0, CONTAINER_DESKTOP)).isEqualTo(new CellPos(2, 0, 1));

        assertThat(mapper.mapPresenterToModel(
                0, 0, 0, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapPresenterToModel(
                0, 0, 1, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 1, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(5, 0, 5));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 0, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(5, 0, 5));
    }

    @Test
    public void testMapPresenterToModel_VerticalHotseat() {
        CellPosMapper mapper = new CellPosMapper(true, 6);
        assertThat(mapper.mapPresenterToModel(
                0, 0, 0, CONTAINER_DESKTOP)).isEqualTo(new CellPos(0, 0, 0));
        assertThat(mapper.mapPresenterToModel(
                0, 0, 1, CONTAINER_DESKTOP)).isEqualTo(new CellPos(0, 0, 1));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 1, CONTAINER_DESKTOP)).isEqualTo(new CellPos(5, 0, 1));
        assertThat(mapper.mapPresenterToModel(
                5, 0, 0, CONTAINER_DESKTOP)).isEqualTo(new CellPos(5, 0, 0));

        assertThat(mapper.mapPresenterToModel(
                0, 0, 0, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 0, 5));
        assertThat(mapper.mapPresenterToModel(
                0, 0, 1, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 0, 5));
        assertThat(mapper.mapPresenterToModel(
                0, 5, 1, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 5, 0));
        assertThat(mapper.mapPresenterToModel(
                0, 5, 0, CONTAINER_HOTSEAT)).isEqualTo(new CellPos(0, 5, 0));
    }

    private ItemInfo createInfo(int cellX, int cellY, int screen, int container) {
        ItemInfo info = new ItemInfo();
        info.cellX = cellX;
        info.cellY = cellY;
        info.screenId = screen;
        info.container = container;
        return info;
    }
}
